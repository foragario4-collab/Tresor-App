package app.glass.tresor.vault

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import app.glass.tresor.crypto.Chunked
import app.glass.tresor.crypto.Crypto
import app.glass.tresor.crypto.EncryptedReader
import app.glass.tresor.crypto.HwKeys
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher

/**
 * Ein Tresor ("a" = echter Tresor, "b" = Schein-Tresor für die Tarn-PIN).
 *
 * Schlüssel-Hierarchie:
 *  PIN ─PBKDF2-SHA512─▶ KEK ─AES-GCM─▶ Hauptschlüssel (+ Hardware-Schicht im Keystore)
 *  Hauptschlüssel ─AES-GCM─▶ Ordner-Schlüssel (ungeschützte Ordner)
 *  Ordner-Passwort/PIN/Systemsperre ─▶ Ordner-Schlüssel (geschützte Ordner)
 *  Ordner-Schlüssel ─AES-GCM─▶ Index, Dateien, Vorschaubilder, Notizen
 */
class Vault(val slot: String, val dir: File) {
    private val headerFile get() = File(dir, "header.json")
    private val rootFile get() = File(dir, "root.bin")

    @Volatile
    private var mk: ByteArray? = null
    private val fkeys = ConcurrentHashMap<String, ByteArray>()

    val folders = mutableStateListOf<Folder>()
    val items = mutableStateMapOf<String, List<Item>>()
    val unlockedFolders = mutableStateListOf<String>()

    /** Ordner, die gerade per Code oder Fingerabdruck hervorgeholt wurden (nur im Speicher). */
    val revealed = mutableStateListOf<String>()

    /** Gemeinsamer Fingerabdruck-Schlüssel aller Systemsperre-Ordner: ein Abdruck öffnet alle. */
    @Volatile
    private var sysGroup: JSONObject? = null

    private val aadVault get() = "tresor-vault-$slot".toByteArray()
    private val aadRoot = "tresor-root".toByteArray()
    private fun faad(id: String) = "folder-$id".toByteArray()
    private fun bioAlias() = "tresor_vault_$slot"
    private fun folderAlias(id: String) = "tresor_f_${slot}_$id"
    private fun groupAlias() = "tresor_fg_$slot"
    private fun gaad(id: String) = "sysg-$id".toByteArray()

    val isUnlocked get() = mk != null
    fun exists() = headerFile.exists()

    private fun header(): JSONObject = JSONObject(headerFile.readText())
    private fun saveHeader(h: JSONObject) = Crypto.writeAtomic(headerFile, h.toString().toByteArray())

    // ───────────────────────── PIN / Hauptschlüssel ─────────────────────────

    private fun wrapSecret(secret: CharArray, payload: ByteArray, aad: ByteArray): String {
        val salt = Crypto.random(16)
        val kek = Crypto.deriveKey(secret, salt)
        try {
            val blob = HwKeys.pepperSeal(Crypto.seal(kek, payload, aad))
            return JSONObject()
                .put("salt", Crypto.b64(salt))
                .put("iter", Crypto.PBKDF2_ITERATIONS)
                .put("blob", Crypto.b64(blob))
                .toString()
        } finally {
            Crypto.wipe(kek)
        }
    }

    private fun unwrapSecret(json: String, secret: CharArray, aad: ByteArray): ByteArray? = try {
        val o = JSONObject(json)
        val kek = Crypto.deriveKey(secret, Crypto.unb64(o.getString("salt")), o.getInt("iter"))
        try {
            Crypto.open(kek, HwKeys.pepperOpen(Crypto.unb64(o.getString("blob"))), aad)
        } finally {
            Crypto.wipe(kek)
        }
    } catch (e: Exception) {
        null
    }

    @Synchronized
    fun create(pin: CharArray) {
        dir.mkdirs()
        val key = Crypto.random(32)
        saveHeader(JSONObject().put("v", 1).put("pin", wrapSecret(pin, key, aadVault)))
        mk = key
        folders.clear(); items.clear(); unlockedFolders.clear(); fkeys.clear()
        newFolderInternal("Fotos", FolderKind.MEDIA)
        newFolderInternal("Dateien", FolderKind.FILES)
        newFolderInternal("Notizen", FolderKind.NOTES)
        saveRoot()
    }

    fun tryUnlock(pin: CharArray): Boolean {
        if (!exists()) return false
        val key = unwrapSecret(header().getString("pin"), pin, aadVault) ?: return false
        openWith(key)
        return true
    }

    fun verifyPin(pin: CharArray): Boolean {
        if (!exists()) return false
        val k = unwrapSecret(header().getString("pin"), pin, aadVault) ?: return false
        Crypto.wipe(k)
        return true
    }

    @Synchronized
    private fun openWith(key: ByteArray) {
        mk = key
        loadRoot()
    }

    @Synchronized
    fun changePin(old: CharArray, new: CharArray): Boolean {
        val key = unwrapSecret(header().getString("pin"), old, aadVault) ?: return false
        val h = header()
        h.put("pin", wrapSecret(new, key, aadVault))
        saveHeader(h)
        Crypto.wipe(key)
        for (f in folders.toList()) {
            if ((f.protection == Protection.PIN || f.protection == Protection.SYSTEM) && f.wrapPw != null) {
                val fk = unwrapSecret(f.wrapPw, old, faad(f.id)) ?: continue
                replaceFolder(f.copy(wrapPw = wrapSecret(new, fk, faad(f.id))))
                Crypto.wipe(fk)
            }
        }
        if (isUnlocked) saveRoot()
        return true
    }

    // ───────────────────────── Systemsperre für den Tresor ─────────────────────────

    fun bioEnabled(): Boolean = try {
        exists() && header().has("bio")
    } catch (e: Exception) {
        false
    }

    fun bioEncryptCipher(): Cipher {
        HwKeys.delete(bioAlias())
        return HwKeys.encryptCipher(bioAlias(), true)
    }

    fun enableBio(c: Cipher) {
        val key = mk ?: return
        val ct = c.doFinal(key)
        val h = header()
        h.put("bio", JSONObject().put("iv", Crypto.b64(c.iv)).put("ct", Crypto.b64(ct)))
        saveHeader(h)
    }

    fun bioDecryptCipher(): Cipher? = try {
        val b = header().getJSONObject("bio")
        HwKeys.decryptCipher(bioAlias(), Crypto.unb64(b.getString("iv")))
    } catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
        // Fingerabdrücke wurden geändert: Systemsperre abschalten, Code bleibt gültig
        disableBio()
        null
    } catch (e: Exception) {
        null
    }

    fun unlockWithBio(c: Cipher): Boolean = try {
        val b = header().getJSONObject("bio")
        val key = c.doFinal(Crypto.unb64(b.getString("ct")))
        openWith(key)
        true
    } catch (e: Exception) {
        false
    }

    fun disableBio() {
        val h = header()
        h.remove("bio")
        saveHeader(h)
        HwKeys.delete(bioAlias())
    }

    // ───────────────────────── Sperren / Löschen ─────────────────────────

    @Synchronized
    fun lock() {
        Crypto.wipe(mk)
        mk = null
        fkeys.values.forEach { Crypto.wipe(it) }
        fkeys.clear()
        folders.clear()
        items.clear()
        unlockedFolders.clear()
        revealed.clear()
        sysGroup = null
        ThumbCache.clearSlot(slot)
    }

    /** Leert den Tresor komplett (alle Ordner und Inhalte), der Code bleibt gleich. */
    @Synchronized
    fun resetContents() {
        if (mk == null) return
        folders.toList().forEach {
            HwKeys.delete(folderAlias(it.id))
        }
        File(dir, "f").deleteRecursively()
        File(dir, "blobs").deleteRecursively()
        HwKeys.delete(groupAlias())
        sysGroup = null
        fkeys.values.forEach { Crypto.wipe(it) }
        fkeys.clear(); folders.clear(); items.clear(); unlockedFolders.clear(); revealed.clear()
        ThumbCache.clearSlot(slot)
        newFolderInternal("Fotos", FolderKind.MEDIA)
        newFolderInternal("Dateien", FolderKind.FILES)
        newFolderInternal("Notizen", FolderKind.NOTES)
        saveRoot()
    }

    // ───────────────────────── Verschlüsselte Zusatzdaten (Chat, Browser) ─────────────────────────

    private fun blobFile(name: String) = File(dir, "blobs/$name.bin")

    /** Liest einen verschlüsselten Datenblock dieses Tresors (mit dem Hauptschlüssel). */
    fun readBlob(name: String): ByteArray? {
        val k = mk ?: return null
        val f = blobFile(name)
        if (!f.exists()) return null
        return try {
            Crypto.open(k, f.readBytes(), "blob-$slot-$name".toByteArray())
        } catch (e: Exception) {
            null
        }
    }

    fun writeBlob(name: String, data: ByteArray) {
        val k = mk ?: throw IllegalStateException("gesperrt")
        Crypto.writeAtomic(blobFile(name), Crypto.seal(k, data, "blob-$slot-$name".toByteArray()))
    }

    fun deleteBlob(name: String) {
        blobFile(name).delete()
    }

    // ───── Verschlüsselte Datenblöcke innerhalb eines Ordners (mit dessen eigenem Schlüssel) ─────
    // Für Chat-Konten: Ist das Konto geschützt, sind seine Daten ohne Konto-Code nicht lesbar.

    private fun folderBlobFile(fid: String, name: String) = File(folderDir(fid), "blobs/$name.bin")
    private fun folderBlobAad(fid: String, name: String) = "fblob-$slot-$fid-$name".toByteArray()

    fun readFolderBlob(fid: String, name: String): ByteArray? {
        val fk = fkeys[fid] ?: return null
        val f = folderBlobFile(fid, name)
        if (!f.exists()) return null
        return try {
            Crypto.open(fk, f.readBytes(), folderBlobAad(fid, name))
        } catch (e: Exception) {
            null
        }
    }

    fun writeFolderBlob(fid: String, name: String, data: ByteArray) {
        val fk = fkeys[fid] ?: throw IllegalStateException("Ordner gesperrt")
        Crypto.writeAtomic(folderBlobFile(fid, name), Crypto.seal(fk, data, folderBlobAad(fid, name)))
    }

    fun deleteFolderBlob(fid: String, name: String) {
        folderBlobFile(fid, name).delete()
    }

    /** Ordner für Downloads finden oder anlegen. */
    @Synchronized
    fun ensureFolder(name: String, kind: FolderKind): Folder =
        folders.firstOrNull { it.kind == kind && it.name == name && !it.isProtected } ?: createFolder(name, kind)

    fun wipeAll() {
        lock()
        dir.deleteRecursively()
        HwKeys.delete(bioAlias())
        HwKeys.delete(groupAlias())
        HwKeys.deletePrefix("tresor_f_${slot}_")
    }

    // ───────────────────────── Ordner ─────────────────────────

    private fun loadRoot() {
        val k = mk ?: return
        val root = if (rootFile.exists()) {
            JSONObject(String(Crypto.open(k, rootFile.readBytes(), aadRoot)))
        } else JSONObject().put("folders", JSONArray())
        val arr = root.getJSONArray("folders")
        sysGroup = root.optJSONObject("sysg")
        revealed.clear()
        val list = (0 until arr.length()).map { Folder.fromJson(arr.getJSONObject(it)) }
        fkeys.values.forEach { Crypto.wipe(it) }
        fkeys.clear()
        items.clear()
        unlockedFolders.clear()
        folders.clear()
        folders.addAll(list)
        for (f in list) {
            if (f.protection == Protection.NONE && f.wrapMk != null) {
                try {
                    fkeys[f.id] = Crypto.open(k, Crypto.unb64(f.wrapMk), f.id.toByteArray())
                    loadIndex(f.id)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun saveRoot() {
        val k = mk ?: return
        val arr = JSONArray()
        folders.forEach { arr.put(it.toJson()) }
        val root = JSONObject().put("folders", arr)
        sysGroup?.let { root.put("sysg", it) }
        val json = root.toString().toByteArray()
        Crypto.writeAtomic(rootFile, Crypto.seal(k, json, aadRoot))
    }

    private fun folderDir(id: String) = File(dir, "f/$id")
    private fun indexFile(id: String) = File(folderDir(id), "index.bin")
    private fun aadIndex(id: String) = "index-$id".toByteArray()

    private fun loadIndex(id: String) {
        val fk = fkeys[id] ?: return
        val f = indexFile(id)
        val list = if (f.exists()) {
            val arr = JSONObject(String(Crypto.open(fk, f.readBytes(), aadIndex(id)))).getJSONArray("items")
            (0 until arr.length()).map { Item.fromJson(arr.getJSONObject(it)) }
        } else emptyList()
        items[id] = list.sortedByDescending { it.created }
        if (id !in unlockedFolders) unlockedFolders.add(id)
    }

    private fun saveIndex(id: String) {
        val fk = fkeys[id] ?: return
        val arr = JSONArray()
        (items[id] ?: emptyList()).forEach { arr.put(it.toJson()) }
        val json = JSONObject().put("items", arr).toString().toByteArray()
        Crypto.writeAtomic(indexFile(id), Crypto.seal(fk, json, aadIndex(id)))
    }

    private fun newId() = UUID.randomUUID().toString().replace("-", "").take(16)

    private fun newFolderInternal(name: String, kind: FolderKind): Folder {
        val k = mk ?: throw IllegalStateException("gesperrt")
        val id = newId()
        val fk = Crypto.random(32)
        val f = Folder(
            id = id, name = name, kind = kind, protection = Protection.NONE,
            hidden = false, relock = true, created = System.currentTimeMillis(),
            wrapMk = Crypto.b64(Crypto.seal(k, fk, id.toByteArray()))
        )
        folderDir(id).mkdirs()
        fkeys[id] = fk
        items[id] = emptyList()
        folders.add(f)
        unlockedFolders.add(id)
        saveIndex(id)
        return f
    }

    @Synchronized
    fun createFolder(name: String, kind: FolderKind): Folder {
        val f = newFolderInternal(name, kind)
        saveRoot()
        return f
    }

    fun folder(id: String): Folder? = folders.firstOrNull { it.id == id }

    private fun replaceFolder(f: Folder) {
        val i = folders.indexOfFirst { it.id == f.id }
        if (i >= 0) folders[i] = f
    }

    @Synchronized
    fun renameFolder(id: String, name: String) {
        val f = folder(id) ?: return
        replaceFolder(f.copy(name = name))
        saveRoot()
    }

    @Synchronized
    fun setHidden(id: String, hidden: Boolean) {
        val f = folder(id) ?: return
        replaceFolder(f.copy(hidden = hidden))
        // Gerade offen → bleibt sichtbar, bis man die Ordner-Übersicht verlässt
        if (hidden && f.id !in revealed) revealed.add(f.id)
        saveRoot()
    }

    @Synchronized
    fun setRelock(id: String, relock: Boolean) {
        val f = folder(id) ?: return
        replaceFolder(f.copy(relock = relock))
        saveRoot()
    }

    @Synchronized
    fun deleteFolder(id: String) {
        folders.removeAll { it.id == id }
        items.remove(id)
        fkeys.remove(id)?.let { Crypto.wipe(it) }
        unlockedFolders.remove(id)
        revealed.remove(id)
        folderDir(id).deleteRecursively()
        HwKeys.delete(folderAlias(id))
        ThumbCache.evictFolder(slot, id)
        saveRoot()
    }

    fun isFolderUnlocked(id: String) = fkeys.containsKey(id)

    fun unlockFolderSecret(id: String, secret: CharArray): Boolean {
        val f = folder(id) ?: return false
        val w = f.wrapPw ?: return false
        val fk = unwrapSecret(w, secret, faad(id)) ?: return false
        synchronized(this) {
            fkeys[id] = fk
            loadIndex(id)
        }
        return true
    }

    /** Neues Format: Ordner-Schlüssel liegt mit dem gemeinsamen Fingerabdruck-Schlüssel verschlüsselt. */
    private fun isGroupWrapped(f: Folder) = f.wrapSys?.let {
        try { JSONObject(it).has("g") } catch (e: Exception) { false }
    } ?: false

    /** Ordner hängt am aktuellen Fingerabdruck-Schlüssel (nicht an einem alten, ungültig gewordenen). */
    private fun inCurrentGroup(f: Folder): Boolean = try {
        val g = sysGroup?.getString("id")
        g != null && JSONObject(f.wrapSys!!).optString("g") == g
    } catch (e: Exception) {
        false
    }

    /** true = der gemeinsame Fingerabdruck-Schlüssel ist eingerichtet und noch gültig. */
    fun sysGroupUsable(): Boolean = sysGroup != null && sysGroupUnlockCipher() != null

    /** Cipher zum Öffnen aller Systemsperre-Ordner (null = keiner eingerichtet oder Abdrücke geändert). */
    fun sysGroupUnlockCipher(): Cipher? = try {
        val g = sysGroup ?: throw IllegalStateException()
        HwKeys.decryptCipher(groupAlias(), Crypto.unb64(g.getString("iv")))
    } catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
        // Fingerabdrücke geändert → Systemsperre-Ordner gehen weiter mit dem Tresor-Code
        null
    } catch (e: Exception) {
        null
    }

    /**
     * Cipher für die Fingerabdruck-Abfrage. Gibt es den gemeinsamen Schlüssel schon, wird er
     * entschlüsselt, sonst neu angelegt (oder ersetzt, falls er ungültig geworden ist).
     */
    fun sysGroupCipher(): Cipher? {
        sysGroupUnlockCipher()?.let { return it }
        return try {
            HwKeys.delete(groupAlias())
            HwKeys.encryptCipher(groupAlias(), true)
        } catch (e: Exception) {
            null
        }
    }

    /** Holt den gemeinsamen Schlüssel aus dem bestätigten Cipher (legt ihn beim ersten Mal an). */
    @Synchronized
    private fun groupKeyFrom(c: Cipher, allowCreate: Boolean): ByteArray? = try {
        val g = sysGroup
        // Gleiche IV wie gespeichert = Entschlüsseln; neue zufällige IV = Schlüssel wird neu angelegt
        if (g != null && c.iv.contentEquals(Crypto.unb64(g.getString("iv")))) {
            c.doFinal(Crypto.unb64(g.getString("ct")))
        } else if (!allowCreate) {
            null
        } else {
            val gk = Crypto.random(32)
            val ct = c.doFinal(gk)
            sysGroup = JSONObject()
                .put("id", Crypto.b64(Crypto.random(8)))
                .put("iv", Crypto.b64(c.iv))
                .put("ct", Crypto.b64(ct))
            saveRoot()
            gk
        }
    } catch (e: Exception) {
        null
    }

    /** Fingerabdruck-Abfrage für einen einzelnen Ordner. */
    fun folderSysCipher(id: String): Cipher? {
        val f = folder(id) ?: return null
        if (isGroupWrapped(f)) return sysGroupUnlockCipher()
        return try {
            val o = JSONObject(f.wrapSys!!)
            HwKeys.decryptCipher(folderAlias(id), Crypto.unb64(o.getString("iv")))
        } catch (e: Exception) {
            null // z. B. Fingerabdrücke geändert → Ordner mit dem Tresor-Code öffnen
        }
    }

    private fun openFolderKey(id: String, fk: ByteArray) {
        synchronized(this) {
            fkeys[id] = fk
            loadIndex(id)
        }
    }

    /** Ordner-Schlüssel mit dem gemeinsamen Schlüssel auspacken (null = passt nicht). */
    private fun unwrapGroup(f: Folder, gk: ByteArray): ByteArray? = try {
        val o = JSONObject(f.wrapSys!!)
        if (o.getString("g") != sysGroup?.getString("id")) null
        else Crypto.open(gk, Crypto.unb64(o.getString("ct")), gaad(f.id))
    } catch (e: Exception) {
        null
    }

    fun unlockFolderSys(id: String, c: Cipher): Boolean = try {
        val f = folder(id)!!
        if (isGroupWrapped(f)) {
            val gk = groupKeyFrom(c, false) ?: throw IllegalStateException()
            try {
                val fk = unwrapGroup(f, gk) ?: throw IllegalStateException()
                openFolderKey(id, fk)
            } finally {
                Crypto.wipe(gk)
            }
        } else {
            val o = JSONObject(f.wrapSys!!)
            openFolderKey(id, c.doFinal(Crypto.unb64(o.getString("ct"))))
        }
        true
    } catch (e: Exception) {
        false
    }

    /** Ein Fingerabdruck → alle Systemsperre-Ordner dieser Art öffnen. Gibt die geöffneten IDs zurück. */
    fun unlockAllSys(c: Cipher, kind: FolderKind): List<String> {
        val gk = groupKeyFrom(c, false) ?: return emptyList()
        val opened = mutableListOf<String>()
        try {
            for (f in folders.toList()) {
                if (f.kind != kind || f.protection != Protection.SYSTEM || !isGroupWrapped(f)) continue
                if (!isFolderUnlocked(f.id)) {
                    val fk = unwrapGroup(f, gk) ?: continue
                    openFolderKey(f.id, fk)
                }
                opened += f.id
            }
        } finally {
            Crypto.wipe(gk)
        }
        markRevealed(opened)
        return opened
    }

    /**
     * Ein Code → genau die Ordner dieser Art öffnen, die mit diesem Code geschützt sind.
     * Der Tresor-Code holt außerdem ausgeblendete Ordner ohne extra Schutz hervor.
     * Systemsperre-Ordner öffnen sich nur per Code, wenn der Fingerabdruck nicht (mehr) geht.
     */
    fun unlockByCode(code: CharArray, kind: FolderKind): List<String> {
        val isVaultCode = verifyPin(code)
        val sysWorks = sysGroupUsable()
        val opened = mutableListOf<String>()
        for (f in folders.toList()) {
            if (f.kind != kind) continue
            when (f.protection) {
                Protection.NONE -> if (isVaultCode && f.hidden) opened += f.id
                Protection.PIN -> if (isVaultCode) {
                    if (isFolderUnlocked(f.id) || unlockFolderSecret(f.id, code)) opened += f.id
                }
                Protection.SYSTEM -> if (isVaultCode && !(sysWorks && inCurrentGroup(f))) {
                    if (isFolderUnlocked(f.id) || unlockFolderSecret(f.id, code)) opened += f.id
                }
                Protection.PASSWORD -> {
                    // Jeder Ordner hat sein eigenes Salz → jeder wird einzeln geprüft
                    val ok = if (isFolderUnlocked(f.id)) checkSecret(f, code) else unlockFolderSecret(f.id, code)
                    if (ok) opened += f.id
                }
            }
        }
        markRevealed(opened)
        return opened
    }

    private fun checkSecret(f: Folder, code: CharArray): Boolean {
        val w = f.wrapPw ?: return false
        val k = unwrapSecret(w, code, faad(f.id)) ?: return false
        Crypto.wipe(k)
        return true
    }

    @Synchronized
    private fun markRevealed(ids: List<String>) {
        ids.forEach { if (it !in revealed) revealed.add(it) }
    }

    /** Hervorgeholte Ordner wieder verstecken (beim Verlassen der Übersicht). */
    @Synchronized
    fun hideRevealed() {
        revealed.clear()
    }

    /** Nur bestimmte hervorgeholte Ordner wieder verstecken (z. B. nur Chat-Konten). */
    @Synchronized
    fun hideRevealed(which: (Folder) -> Boolean) {
        revealed.removeAll { id -> folder(id)?.let(which) ?: true }
    }

    @Synchronized
    fun relockFolder(id: String) {
        val f = folder(id) ?: return
        if (!f.isProtected) return
        fkeys.remove(id)?.let { Crypto.wipe(it) }
        items.remove(id)
        unlockedFolders.remove(id)
        ThumbCache.evictFolder(slot, id)
    }

    /**
     * Ändert den Schutz eines Ordners. Der Ordner muss gerade offen sein.
     * secret = Tresor-PIN (bei PIN / SYSTEM) oder neues Passwort (bei PASSWORD).
     * Bei SYSTEM bleibt die PIN immer als Notfall-Zugang erhalten.
     */
    fun setProtection(id: String, prot: Protection, secret: CharArray?, sysCipher: Cipher?): Boolean {
        val f = folder(id) ?: return false
        val fk = fkeys[id] ?: return false
        val k = mk ?: return false
        var nf = f.copy(protection = prot, wrapMk = null, wrapPw = null, wrapSys = null)
        when (prot) {
            Protection.NONE -> nf = nf.copy(wrapMk = Crypto.b64(Crypto.seal(k, fk, id.toByteArray())))
            Protection.PIN -> {
                if (secret == null || !verifyPin(secret)) return false
                nf = nf.copy(wrapPw = wrapSecret(secret, fk, faad(id)))
            }
            Protection.PASSWORD -> {
                if (secret == null || secret.isEmpty()) return false
                nf = nf.copy(wrapPw = wrapSecret(secret, fk, faad(id)))
            }
            Protection.SYSTEM -> {
                if (secret == null || sysCipher == null || !verifyPin(secret)) return false
                val gk = groupKeyFrom(sysCipher, true) ?: return false
                val gid = sysGroup?.getString("id") ?: return false
                val ct = try {
                    Crypto.seal(gk, fk, gaad(id))
                } finally {
                    Crypto.wipe(gk)
                }
                nf = nf.copy(
                    wrapPw = wrapSecret(secret, fk, faad(id)),
                    wrapSys = JSONObject().put("g", gid).put("ct", Crypto.b64(ct)).toString()
                )
            }
        }
        synchronized(this) {
            // Alter Einzel-Schlüssel des Ordners wird nicht mehr gebraucht
            HwKeys.delete(folderAlias(id))
            replaceFolder(nf)
            saveRoot()
        }
        return true
    }

    // ───────────────────────── Inhalte ─────────────────────────

    fun dataFile(fid: String, iid: String) = File(folderDir(fid), "$iid.bin")
    private fun thumbFile(fid: String, iid: String) = File(folderDir(fid), "$iid.th")
    private fun noteFile(fid: String, iid: String) = File(folderDir(fid), "$iid.note")
    private fun aadThumb(iid: String) = "thumb-$iid".toByteArray()
    /** Bindet Dateiinhalte an ihre Element-ID: vertauschte Dateien fallen beim Entschlüsseln auf. */
    private fun ctxOf(iid: String) = "data-$slot-$iid".toByteArray()
    private fun aadNote(iid: String) = "note-$iid".toByteArray()

    fun itemsOf(fid: String): List<Item> = items[fid] ?: emptyList()
    fun item(fid: String, iid: String): Item? = itemsOf(fid).firstOrNull { it.id == iid }
    fun newItemId() = newId()

    @Synchronized
    private fun putItem(fid: String, item: Item) {
        val list = itemsOf(fid).filterNot { it.id == item.id }
        items[fid] = (list + item).sortedByDescending { it.created }
        saveIndex(fid)
    }

    fun importStream(fid: String, input: InputStream, meta: Item, thumb: ByteArray?): Item {
        val fk = fkeys[fid] ?: throw IllegalStateException("Ordner gesperrt")
        val size = Chunked.encrypt(input, dataFile(fid, meta.id), fk, ctxOf(meta.id))
        var hasThumb = false
        if (thumb != null) {
            Crypto.writeAtomic(thumbFile(fid, meta.id), Crypto.seal(fk, thumb, aadThumb(meta.id)))
            hasThumb = true
        }
        val it = meta.copy(size = size, hasThumb = hasThumb)
        putItem(fid, it)
        return it
    }

    fun readThumb(fid: String, iid: String): ByteArray? = try {
        val fk = fkeys[fid] ?: return null
        Crypto.open(fk, thumbFile(fid, iid).readBytes(), aadThumb(iid))
    } catch (e: Exception) {
        null
    }

    fun reader(fid: String, iid: String): EncryptedReader {
        val fk = fkeys[fid] ?: throw IllegalStateException("Ordner gesperrt")
        return EncryptedReader(dataFile(fid, iid), fk, ctxOf(iid))
    }

    fun exportTo(fid: String, iid: String, out: OutputStream) {
        val item = item(fid, iid) ?: return
        if (item.type == ItemType.NOTE) {
            val (t, b) = readNote(fid, iid)
            out.write(("$t\n\n$b").toByteArray())
            return
        }
        reader(fid, iid).use { r -> r.inputStream().copyTo(out, 64 * 1024) }
    }

    @Synchronized
    fun deleteItems(fid: String, ids: Set<String>) {
        ids.forEach {
            dataFile(fid, it).delete()
            thumbFile(fid, it).delete()
            noteFile(fid, it).delete()
            ThumbCache.evict(slot, fid, it)
        }
        items[fid] = itemsOf(fid).filterNot { it.id in ids }
        saveIndex(fid)
    }

    fun moveItems(from: String, to: String, ids: Set<String>): Boolean {
        if (from == to) return true
        val fkFrom = fkeys[from] ?: return false
        val fkTo = fkeys[to] ?: return false
        for (id in ids) {
            val it = item(from, id) ?: continue
            if (it.type == ItemType.NOTE) {
                val plain = Crypto.open(fkFrom, noteFile(from, id).readBytes(), aadNote(id))
                Crypto.writeAtomic(noteFile(to, id), Crypto.seal(fkTo, plain, aadNote(id)))
                Crypto.wipe(plain)
            } else {
                EncryptedReader(dataFile(from, id), fkFrom, ctxOf(id)).use { r ->
                    Chunked.encrypt(r.inputStream(), dataFile(to, id), fkTo, ctxOf(id))
                }
                if (it.hasThumb) {
                    readThumb(from, id)?.let { th ->
                        Crypto.writeAtomic(thumbFile(to, id), Crypto.seal(fkTo, th, aadThumb(id)))
                    }
                }
            }
            putItem(to, it)
        }
        deleteItems(from, ids)
        return true
    }

    fun saveNote(fid: String, iid: String?, title: String, body: String): Item {
        val fk = fkeys[fid] ?: throw IllegalStateException("Ordner gesperrt")
        val id = iid ?: newId()
        val json = JSONObject().put("t", title).put("b", body).toString().toByteArray()
        Crypto.writeAtomic(noteFile(fid, id), Crypto.seal(fk, json, aadNote(id)))
        val now = System.currentTimeMillis()
        val old = item(fid, id)
        val it = old?.copy(name = title, size = body.length.toLong(), modified = now)
            ?: Item(id, title, "text/plain", ItemType.NOTE, body.length.toLong(), now, now)
        putItem(fid, it)
        return it
    }

    fun readNote(fid: String, iid: String): Pair<String, String> {
        val fk = fkeys[fid] ?: return "" to ""
        return try {
            val o = JSONObject(String(Crypto.open(fk, noteFile(fid, iid).readBytes(), aadNote(iid))))
            o.optString("t") to o.optString("b")
        } catch (e: Exception) {
            "" to ""
        }
    }

    fun recentMedia(limit: Int = 12): List<Pair<String, Item>> =
        folders.filter { !it.hidden && it.kind == FolderKind.MEDIA && it.id in unlockedFolders }
            .flatMap { f -> itemsOf(f.id).map { f.id to it } }
            .sortedByDescending { it.second.created }
            .take(limit)
}
