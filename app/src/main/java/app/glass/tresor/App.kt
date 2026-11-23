package app.glass.tresor

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.glass.tresor.crypto.Crypto
import app.glass.tresor.crypto.HwKeys
import app.glass.tresor.vault.FolderKind
import app.glass.tresor.vault.ItemType
import app.glass.tresor.vault.ThumbCache
import app.glass.tresor.vault.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class Disguise(val alias: String, val title: String) {
    TRESOR("app.glass.tresor.alias.Tresor", "Tresor"),
    RECHNER("app.glass.tresor.alias.Rechner", "Rechner"),
    KALENDER("app.glass.tresor.alias.Kalender", "Kalender"),
    CHECKLISTE("app.glass.tresor.alias.Checkliste", "Checkliste"),
    NEWS("app.glass.tresor.alias.News", "News"),
    MUSIK("app.glass.tresor.alias.Musik", "Musik"),
    NOTIZEN("app.glass.tresor.alias.Notizen", "Notizen"),
    WETTER("app.glass.tresor.alias.Wetter", "Wetter"),
    BROWSER("app.glass.tresor.alias.Browser", "Browser");

    companion object {
        fun fromAlias(cls: String?): Disguise? = entries.firstOrNull { it.alias == cls }
    }
}

sealed interface Screen {
    data object Disguise : Screen
    data object Setup : Screen
    data object Lock : Screen
    data object Home : Screen
    data class Albums(val kind: FolderKind, val filter: ItemType?) : Screen
    data class FolderView(val fid: String, val filter: ItemType?) : Screen
    data class Viewer(val fid: String, val iid: String, val filter: ItemType?) : Screen
    data class TextView(val fid: String, val iid: String) : Screen
    data class NoteEdit(val fid: String, val iid: String?) : Screen
    data object Settings : Screen
    data object Browser : Screen
    data object Chat : Screen
    data class ChatThread(val contactId: String) : Screen
    data object ChatCode : Screen
    data object ChatTips : Screen
    data object ChatAdd : Screen
    data object Phone : Screen
    data object Calendar : Screen
    data object Passwords : Screen
    data class PasswordEntry(val id: String?) : Screen
    data object Memos : Screen
    data object Contacts : Screen
    data class ContactDetail(val contactId: String) : Screen
    data object Player : Screen
    data object Handbook : Screen
    data class CategorySettings(val kind: FolderKind) : Screen
    data class Camera(val fid: String, val video: Boolean) : Screen
    data class FolderSettings(val fid: String) : Screen
}

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Disguise)
    var forward by mutableStateOf(true)
    val top: Screen get() = stack.last()

    fun push(s: Screen) {
        forward = true
        stack.add(s)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        forward = false
        stack.removeAt(stack.lastIndex)
        App.relockUnused()
        return true
    }

    fun reset(s: Screen, fwd: Boolean = true) {
        forward = fwd
        stack.clear()
        stack.add(s)
    }

    /** Ersetzt alles bis zur Wurzel, z. B. Tarnung → Tresor. */
    fun openVault() {
        forward = true
        stack.clear()
        stack.add(Screen.Disguise)
        stack.add(Screen.Home)
    }
}

object App {
    lateinit var ctx: Context
    lateinit var prefs: SharedPreferences
    lateinit var dprefs: SharedPreferences // Daten der Tarn-Apps (Kalender-Termine, Checkliste)

    /** Erster Tresor (beim Einrichten angelegt). Nur er kann die Systemsperre nutzen. */
    lateinit var vaultA: Vault

    /** Alle Tresore. Jeder Code öffnet genau seinen Tresor – keiner weiß von den anderen. */
    val vaults = mutableListOf<Vault>()

    private fun vaultsDir() = File(ctx.filesDir, "vaults")

    private fun loadVaults() {
        vaults.clear()
        vaultA = Vault("a", File(vaultsDir(), "a"))
        vaults.add(vaultA)
        vaultsDir().listFiles()?.filter { it.isDirectory && it.name != "a" }?.sortedBy { it.name }?.forEach {
            val v = Vault(it.name, it)
            if (v.exists()) vaults.add(v) else it.deleteRecursively()
        }
    }

    var vault by mutableStateOf<Vault?>(null)
    val nav = Nav()
    var disguise by mutableStateOf(Disguise.TRESOR)

    /** Während Foto-Auswahl, Kamera oder Systemsperre nicht automatisch sperren. */
    @Volatile
    var suppressLock = false

    /** Kurze Hinweise unten am Bildschirm. */
    var toastMsg by mutableStateOf<String?>(null)
    var toastId by mutableStateOf(0)
    fun toast(msg: String) {
        toastMsg = msg
        toastId++
    }

    /** Zeitpunkt, an dem zuletzt eine Datei an eine andere App übergeben wurde. */
    @Volatile
    var lastShare = 0L

    /** false = Die App wurde mit einem fremden Schlüssel signiert (verändert / gefälschter Nachbau). */
    var genuine = true

    /** Vergleicht den Fingerabdruck des Signier-Zertifikats mit dem, der beim Bauen eingetragen wurde. */
    private fun checkSignature(): Boolean {
        val expected = BuildConfig.CERT_SHA256
        if (expected.isEmpty()) return true // Test-Build ohne Release-Schlüssel
        return try {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signers = info.signingInfo?.apkContentsSigners ?: return false
            signers.any { s ->
                java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
                    .joinToString("") { "%02x".format(it) } == expected
            }
        } catch (e: Exception) {
            false
        }
    }

    var secure by mutableStateOf(true)
    var motionLock by mutableStateOf(false)
    /** Empfindlichkeit der Bewegungssperre 0 (nur starkes Rucken) … 1 (sehr empfindlich). */
    var motionSens by mutableStateOf(0.4f)
    /**
     * Wann der Tresor sperrt: "now" = sofort beim Verlassen, "screen" = erst wenn der Bildschirm ausgeht,
     * "1"/"5"/"15" = nach so vielen Minuten im Hintergrund, "close" = erst beim Schließen der App.
     */
    var lockMode by mutableStateOf("now")
    @Volatile
    var backgroundSince = 0L

    fun changeLockMode(m: String) { lockMode = m; prefs.edit().putString("lockMode", m).apply() }

    fun lockModeLabel(m: String = lockMode) = when (m) {
        "screen" -> "Wenn der Bildschirm ausgeht"
        "1" -> "Nach 1 Minute im Hintergrund"
        "5" -> "Nach 5 Minuten im Hintergrund"
        "15" -> "Nach 15 Minuten im Hintergrund"
        "close" -> "Erst beim Schließen der App"
        else -> "Sofort beim Verlassen"
    }

    /** Beim Zurückkehren: Ist die eingestellte Zeit im Hintergrund abgelaufen? */
    fun checkTimedLock() {
        val since = backgroundSince
        backgroundSince = 0L
        val min = lockMode.toIntOrNull() ?: return
        if (since > 0 && android.os.SystemClock.elapsedRealtime() - since > min * 60_000L) lock()
    }

    /** Sperren, sobald das Handy mit dem Display nach unten liegt. */
    var flipLock by mutableStateOf(false)
    var wipeAfter10 by mutableStateOf(false)

    fun init(c: Context) {
        if (::ctx.isInitialized) return
        ctx = c.applicationContext
        prefs = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)
        dprefs = ctx.getSharedPreferences("disguise_data", Context.MODE_PRIVATE)
        genuine = checkSignature()
        loadVaults()
        secure = prefs.getBoolean("secure", true)
        motionLock = prefs.getBoolean("motion", false)
        motionSens = prefs.getFloat("motionSens", 0.4f).coerceIn(0f, 1f)
        flipLock = prefs.getBoolean("flipLock", false)
        lockMode = prefs.getString("lockMode", "now") ?: "now"
        appHidden = prefs.getBoolean("appHidden", false)
        app.glass.tresor.browser.BrowserTor.init()
        app.glass.tresor.ui.TresorKb.init()
        wipeAfter10 = prefs.getBoolean("wipe10", false)
        disguise = Disguise.entries.firstOrNull { it.name == prefs.getString("disguiseName", null) } ?: Disguise.TRESOR
        clearShareCache(force = true)
        emptyTrash()
        File(ctx.cacheDir, "update").deleteRecursively()
        if (!vaultA.exists()) nav.reset(Screen.Setup)
    }

    fun changeSecure(v: Boolean) { secure = v; prefs.edit().putBoolean("secure", v).apply() }
    fun setMotion(v: Boolean) { motionLock = v; prefs.edit().putBoolean("motion", v).apply() }
    fun setMotionSens(v: Float, persist: Boolean) { motionSens = v.coerceIn(0f, 1f); if (persist) prefs.edit().putFloat("motionSens", motionSens).apply() }
    fun changeFlipLock(v: Boolean) { flipLock = v; prefs.edit().putBoolean("flipLock", v).apply() }
    /** Schwelle in m/s²: weniger empfindlich = höhere Schwelle. */
    fun motionThreshold() = 12f - motionSens * 9.5f
    fun setWipe(v: Boolean) { wipeAfter10 = v; prefs.edit().putBoolean("wipe10", v).apply() }

    fun lockoutRemainingMs(): Long =
        (prefs.getLong("lockout", 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    fun failedCount() = prefs.getInt("failed", 0)

    private fun registerFailure(countForWipe: Boolean) {
        val n = prefs.getInt("failed", 0) + 1
        val e = prefs.edit().putInt("failed", n)
        if (n >= 5) {
            val ms = (30_000L shl (n - 5).coerceAtMost(5)).coerceAtMost(15 * 60_000L)
            e.putLong("lockout", System.currentTimeMillis() + ms)
        }
        e.apply()
        if (countForWipe && wipeAfter10 && n >= 10) wipeEverything()
    }

    private fun resetFailures() {
        prefs.edit().putInt("failed", 0).putLong("lockout", 0L).apply()
    }

    /**
     * Versucht zu entsperren: erst echter Tresor, dann Schein-Tresor.
     * silent = Versuch aus Tarnung (Rechner/Checkliste): zählt nicht fürs Löschen.
     */
    suspend fun tryUnlock(pin: String, silent: Boolean): Boolean {
        val chars = pin.toCharArray()
        // Notfall-Löschcode funktioniert immer – auch während einer Sperrzeit
        val panic = panicEnabled() && withContext(Dispatchers.Default) {
            if (isPanicCode(chars)) { panicWipe(chars); true } else false
        }
        if (panic) {
            Crypto.wipe(chars)
            vault = vaultA
            nav.openVault()
            return true
        }
        // Löschcode eines einzelnen Tresors
        val target = withContext(Dispatchers.Default) {
            vaultPanicTarget(chars)?.also { panicWipeVault(it, chars) }
        }
        if (target != null) {
            Crypto.wipe(chars)
            vault = target
            nav.openVault()
            return true
        }
        if (lockoutRemainingMs() > 0) {
            Crypto.wipe(chars)
            return false
        }
        val ok = withContext(Dispatchers.Default) {
            try {
                vaults.toList().firstOrNull { it.exists() && it.tryUnlock(chars) }
            } finally {
                Crypto.wipe(chars)
            }
        }
        return if (ok != null) {
            resetFailures()
            vault = ok
            nav.openVault()
            true
        } else {
            registerFailure(!silent)
            false
        }
    }

    /** Geschützte Ordner wieder sperren, sobald man sie verlässt. */
    fun relockUnused() {
        val v = vault ?: return
        val used = nav.stack.mapNotNull {
            when (it) {
                is Screen.FolderView -> it.fid
                is Screen.Viewer -> it.fid
                is Screen.TextView -> it.fid
                is Screen.NoteEdit -> it.fid
                is Screen.FolderSettings -> it.fid
                else -> null
            }
        }.toSet()
        // Per Code / Fingerabdruck hervorgeholte Ordner bleiben offen, solange man in der Ordner-Übersicht ist.
        // Verlässt man sie, verschwinden ausgeblendete Ordner wieder. Für Chat-Konten gilt dasselbe mit dem Chat.
        val inAlbums = nav.stack.any { it is Screen.Albums }
        val inChat = nav.stack.any {
            it == Screen.Chat || it is Screen.ChatThread || it == Screen.ChatCode || it == Screen.ChatAdd || it == Screen.ChatTips ||
                it == Screen.Phone || it == Screen.Contacts || it is Screen.ContactDetail
        }
        val inBrowser = nav.stack.any { it == Screen.Browser }
        val isChat = { f: app.glass.tresor.vault.Folder -> f.kind == app.glass.tresor.vault.FolderKind.CHAT }
        val isBrowser = { f: app.glass.tresor.vault.Folder -> f.kind == app.glass.tresor.vault.FolderKind.BROWSER }
        if (!inAlbums) v.hideRevealed { !isChat(it) && !isBrowser(it) }
        if (!inBrowser) {
            v.hideRevealed { isBrowser(it) }
            app.glass.tresor.browser.BrowserAreas.reset()
        }
        if (!inChat) {
            v.hideRevealed { isChat(it) }
            app.glass.tresor.chat.ChatAccounts.reset()
        }
        val keep = v.revealed.toMutableSet()
        if (inChat) keep += app.glass.tresor.chat.ChatAccounts.selected
        if (inBrowser) app.glass.tresor.browser.BrowserAreas.selected?.let { keep += it }
        v.folders.toList()
            .filter { it.isProtected && it.relock && it.id !in used && it.id !in keep && v.isFolderUnlocked(it.id) }
            .forEach { v.relockFolder(it.id) }
        app.glass.tresor.chat.ChatEngine.prune(v)
        // Geschützte Container schließen, sobald man ihre Kategorie verlässt
        val kindsInUse = nav.stack.mapNotNull { sc ->
            when (sc) {
                is Screen.Albums -> sc.kind
                is Screen.CategorySettings -> sc.kind
                is Screen.FolderView -> v.folder(sc.fid)?.kind
                is Screen.Viewer -> v.folder(sc.fid)?.kind
                is Screen.TextView -> v.folder(sc.fid)?.kind
                is Screen.NoteEdit -> v.folder(sc.fid)?.kind
                is Screen.FolderSettings -> v.folder(sc.fid)?.kind
                Screen.Player -> app.glass.tresor.music.MusicPlayer.current?.first?.let { v.folder(it)?.kind }
                else -> null
            }
        }.toSet() + listOfNotNull(app.glass.tresor.music.MusicPlayer.current?.first?.let { v.folder(it)?.kind })
        v.openCategories.toList().filter { it !in kindsInUse }.forEach { v.relockCategory(it) }
    }

    fun onBioUnlocked(v: Vault) {
        resetFailures()
        vault = v
        nav.openVault()
    }

    /** Der eine Tresor, der per Systemsperre öffnet (falls eingerichtet). */
    fun bioVault(): Vault? {
        val slot = prefs.getString("bioSlot", "a")
        return vaults.firstOrNull { it.slot == slot && it.bioEnabled() }
    }

    fun setBioSlot(slot: String) = prefs.edit().putString("bioSlot", slot).apply()

    /** Wird vor dem Sperren aufgerufen (z. B. offene Notiz speichern). */
    val beforeLock = mutableSetOf<() -> Unit>()

    fun lock() {
        if (vault == null && nav.top == Screen.Disguise) return
        beforeLock.toList().forEach { it() }
        app.glass.tresor.chat.Calls.onVaultLocked()
        app.glass.tresor.music.MusicPlayer.onVaultLocked()
        app.glass.tresor.chat.ChatAccounts.reset()
        app.glass.tresor.browser.BrowserAreas.reset()
        // Tor-Weiterleitung des Browsers aufheben (startet beim nächsten Öffnen neu)
        if (app.glass.tresor.browser.BrowserTor.ready || app.glass.tresor.chat.ChatService.forBrowser) app.glass.tresor.browser.BrowserTor.release()
        app.glass.tresor.chat.VoicePlayer.stop()
        app.glass.tresor.chat.ChatEngine.close()
        vaults.forEach { it.lock() }
        ThumbCache.clearAll()
        clearShareCache()
        app.glass.tresor.browser.BrowserData.clearAll()
        vault = null
        if (!vaultA.exists()) nav.reset(Screen.Setup) else nav.reset(Screen.Disguise, fwd = false)
    }

    /**
     * Entschlüsselte Kopien für "Öffnen mit" löschen. Direkt nach dem Teilen kurz warten,
     * damit die andere App die Datei noch lesen kann – spätestens beim nächsten Start weg.
     */
    fun clearShareCache(force: Boolean = false) {
        if (force || System.currentTimeMillis() - lastShare > 120_000L) {
            File(ctx.cacheDir, "share").deleteRecursively()
        }
        File(ctx.cacheDir, "cam").listFiles()?.forEach { it.delete() }
    }

    // ───────────────────────── Notfall-Löschcode ─────────────────────────

    fun panicEnabled() = prefs.contains("panic")

    /** Speichert nur einen PBKDF2-SHA512-Hash, nie den Code selbst. */
    fun setPanicCode(code: CharArray) {
        val salt = Crypto.random(16)
        val hash = Crypto.deriveKey(code, salt)
        prefs.edit().putString("panic", Crypto.b64(salt) + ":" + Crypto.b64(hash)).apply()
        Crypto.wipe(hash)
    }

    fun removePanicCode() {
        prefs.edit().remove("panic").apply()
    }

    // ── Löschcode pro Tresor: löscht nur diesen einen Tresor, die anderen bleiben ──
    private fun vaultPanicKey(slot: String) = "panic_$slot"
    fun vaultPanicEnabled(v: Vault) = prefs.contains(vaultPanicKey(v.slot))

    fun setVaultPanicCode(v: Vault, code: CharArray) {
        val salt = Crypto.random(16)
        val hash = Crypto.deriveKey(code, salt)
        prefs.edit().putString(vaultPanicKey(v.slot), Crypto.b64(salt) + ":" + Crypto.b64(hash)).apply()
        Crypto.wipe(hash)
    }

    fun removeVaultPanicCode(v: Vault) {
        prefs.edit().remove(vaultPanicKey(v.slot)).apply()
    }

    private fun removeAllVaultPanicCodes() {
        val e = prefs.edit()
        prefs.all.keys.filter { it.startsWith("panic_") }.forEach { e.remove(it) }
        e.apply()
    }

    private fun matchesHash(stored: String?, code: CharArray): Boolean {
        if (stored == null) return false
        return try {
            val (s, h) = stored.split(":")
            val test = Crypto.deriveKey(code, Crypto.unb64(s))
            java.security.MessageDigest.isEqual(test, Crypto.unb64(h)).also { Crypto.wipe(test) }
        } catch (e: Exception) {
            false
        }
    }

    /** Zu welchem Tresor gehört dieser Löschcode? null = keiner. */
    fun vaultPanicTarget(code: CharArray): Vault? =
        vaults.toList().firstOrNull { it.exists() && matchesHash(prefs.getString(vaultPanicKey(it.slot), null), code) }

    fun isAnyPanicCode(code: CharArray) = isPanicCode(code) || vaultPanicTarget(code) != null

    /**
     * Löscht nur diesen einen Tresor (Schlüssel zuerst → sofort unlesbar) und legt an seiner Stelle
     * einen leeren Tresor mit dem Löschcode an. Die anderen Tresore bleiben unberührt.
     */
    fun panicWipeVault(v: Vault, code: CharArray) {
        app.glass.tresor.music.MusicPlayer.stop()
        app.glass.tresor.chat.ChatEngine.forget(v.slot)
        ThumbCache.clearSlot(v.slot)
        v.wipeAll()
        removeVaultPanicCode(v)
        if (prefs.getString("bioSlot", null) == v.slot) prefs.edit().remove("bioSlot").apply()
        prefs.edit().putInt("failed", 0).putLong("lockout", 0L).apply()
        v.create(code)
    }

    fun isPanicCode(code: CharArray): Boolean {
        val v = prefs.getString("panic", null) ?: return false
        return try {
            val (s, h) = v.split(":")
            val test = Crypto.deriveKey(code, Crypto.unb64(s))
            java.security.MessageDigest.isEqual(test, Crypto.unb64(h)).also { Crypto.wipe(test) }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Vernichtet sofort alles: Zuerst werden die Hardware-Schlüssel gelöscht (dadurch sind
     * alle Daten augenblicklich unlesbar), dann werden die Dateien im Hintergrund entfernt.
     * Anschließend entsteht ein neuer, leerer Tresor mit dem Löschcode als Code – von außen
     * sieht es aus wie ein ganz normaler, leerer Tresor.
     */
    fun panicWipe(code: CharArray) {
        vaults.forEach { it.lock() }
        ThumbCache.clearAll()
        app.glass.tresor.chat.ChatService.stop(ctx)
        crypticShred()
        genuine = checkSignature()
        loadVaults()
        removePanicCode()
        removeAllVaultPanicCodes()
        prefs.edit().remove("wipe10").putInt("failed", 0).putLong("lockout", 0L).apply()
        wipeAfter10 = false
        vaultA.create(code)
    }

    /** Schlüssel löschen + Tresor-Ordner wegverschieben und im Hintergrund löschen. */
    private fun crypticShred() {
        HwKeys.delete(HwKeys.PEPPER_ALIAS)
        HwKeys.deletePrefix("tresor_vault_")
        HwKeys.deletePrefix("tresor_f_")
        HwKeys.deletePrefix("tresor_fg_")
        File(ctx.filesDir, "chat").let { if (it.exists()) it.renameTo(File(ctx.filesDir, "trash_chat_${System.nanoTime()}")) }
        File(ctx.filesDir, "tor").let { if (it.exists()) it.renameTo(File(ctx.filesDir, "trash_tor_${System.nanoTime()}")) }
        val dir = File(ctx.filesDir, "vaults")
        val trash = File(ctx.filesDir, "trash_${System.nanoTime()}")
        if (dir.exists() && !dir.renameTo(trash)) dir.deleteRecursively()
        clearShareCache(force = true)
        emptyTrash()
    }

    private fun emptyTrash() {
        val list = ctx.filesDir.listFiles { f -> f.name.startsWith("trash_") } ?: return
        if (list.isEmpty()) return
        Thread { list.forEach { it.deleteRecursively() } }.start()
    }

    fun wipeEverything() {
        vaults.forEach { it.lock() }
        app.glass.tresor.chat.ChatService.stop(ctx)
        crypticShred()
        genuine = checkSignature()
        loadVaults()
        removePanicCode()
        removeAllVaultPanicCodes()
        ThumbCache.clearAll()
        clearShareCache(force = true)
        prefs.edit().putInt("failed", 0).putLong("lockout", 0L).apply()
        vault = null
        nav.reset(Screen.Setup)
    }

    // ───────────────────────── Weitere Tresore ─────────────────────────

    /** Prüft, ob ein Code schon zu irgendeinem Tresor oder zum Löschcode gehört. */
    fun codeInUse(code: CharArray): Boolean =
        isAnyPanicCode(code) || vaults.any { it.exists() && it.verifyPin(code) }

    /** Legt einen neuen, unabhängigen Tresor an. Gibt null zurück, wenn der Code schon vergeben ist. */
    fun createVault(code: CharArray): Vault? {
        if (codeInUse(code)) return null
        val slot = Crypto.b64(Crypto.random(6)).replace("/", "x").replace("+", "y").replace("=", "").lowercase()
        val v = Vault(slot, File(vaultsDir(), slot))
        v.create(code)
        v.lock()
        vaults.add(v)
        return v
    }

    /** Löscht den gerade offenen Tresor (nicht den ersten). */
    fun deleteCurrentVault() {
        val v = vault ?: return
        if (v === vaultA) return
        app.glass.tresor.chat.ChatEngine.forget(v.slot)
        removeVaultPanicCode(v)
        v.wipeAll()
        vaults.remove(v)
        vault = null
        nav.reset(Screen.Disguise, fwd = false)
    }

    /** App komplett ohne Symbol – nur über den (selbst gewählten) Telefoncode *#*#ZAHL#*#* zu öffnen. */
    var appHidden by mutableStateOf(false)

    fun changeAppHidden(h: Boolean) {
        appHidden = h
        prefs.edit().putBoolean("appHidden", h).apply()
        if (h) {
            val pm = ctx.packageManager
            Disguise.entries.forEach {
                pm.setComponentEnabledSetting(
                    ComponentName(ctx.packageName, it.alias),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
                )
            }
            setWeatherWidget(false)
        } else applyDisguise(disguise)
    }

    /** Wetter-Widget nur anbieten, solange die Tarnung „Wetter“ sichtbar ist. */
    private fun setWeatherWidget(on: Boolean) {
        try {
            ctx.packageManager.setComponentEnabledSetting(
                ComponentName(ctx, app.glass.tresor.disguise.WeatherWidget::class.java),
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (_: Exception) {
        }
    }

    fun applyDisguise(d: Disguise) {
        if (appHidden) {
            // Versteckt: nur merken, welche Tarnung beim Öffnen erscheint – kein Symbol aktivieren
            disguise = d
            prefs.edit().putString("disguiseName", d.name).apply()
            return
        }
        val pm = ctx.packageManager
        // Neues Icon zuerst aktivieren, dann die anderen ausblenden
        pm.setComponentEnabledSetting(
            ComponentName(ctx.packageName, d.alias),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
        )
        Disguise.entries.filter { it != d }.forEach {
            pm.setComponentEnabledSetting(
                ComponentName(ctx.packageName, it.alias),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
            )
        }
        setWeatherWidget(d == Disguise.WETTER)
        disguise = d
        prefs.edit().putString("disguiseName", d.name).apply()
    }
}
