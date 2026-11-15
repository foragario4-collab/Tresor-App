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
    NEWS("app.glass.tresor.alias.News", "News");

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

    lateinit var vaultA: Vault
    lateinit var vaultB: Vault

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

    var secure by mutableStateOf(true)
    var motionLock by mutableStateOf(false)
    var wipeAfter10 by mutableStateOf(false)

    fun init(c: Context) {
        if (::ctx.isInitialized) return
        ctx = c.applicationContext
        prefs = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)
        dprefs = ctx.getSharedPreferences("disguise_data", Context.MODE_PRIVATE)
        vaultA = Vault("a", File(ctx.filesDir, "vaults/a"))
        vaultB = Vault("b", File(ctx.filesDir, "vaults/b"))
        secure = prefs.getBoolean("secure", true)
        motionLock = prefs.getBoolean("motion", false)
        wipeAfter10 = prefs.getBoolean("wipe10", false)
        disguise = Disguise.entries.firstOrNull { it.name == prefs.getString("disguiseName", null) } ?: Disguise.TRESOR
        clearShareCache(force = true)
        emptyTrash()
        File(ctx.cacheDir, "update").deleteRecursively()
        if (!vaultA.exists()) nav.reset(Screen.Setup)
    }

    fun changeSecure(v: Boolean) { secure = v; prefs.edit().putBoolean("secure", v).apply() }
    fun setMotion(v: Boolean) { motionLock = v; prefs.edit().putBoolean("motion", v).apply() }
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
        if (lockoutRemainingMs() > 0) {
            Crypto.wipe(chars)
            return false
        }
        val ok = withContext(Dispatchers.Default) {
            try {
                when {
                    vaultA.tryUnlock(chars) -> vaultA
                    vaultB.exists() && vaultB.tryUnlock(chars) -> vaultB
                    else -> null
                }
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
        v.folders.toList()
            .filter { it.isProtected && it.relock && it.id !in used && v.isFolderUnlocked(it.id) }
            .forEach { v.relockFolder(it.id) }
    }

    fun onBioUnlocked() {
        resetFailures()
        vault = vaultA
        nav.openVault()
    }

    /** Wird vor dem Sperren aufgerufen (z. B. offene Notiz speichern). */
    val beforeLock = mutableSetOf<() -> Unit>()

    fun lock() {
        if (vault == null && nav.top == Screen.Disguise) return
        beforeLock.toList().forEach { it() }
        vaultA.lock()
        vaultB.lock()
        ThumbCache.clearAll()
        clearShareCache()
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
        vaultA.lock(); vaultB.lock()
        ThumbCache.clearAll()
        crypticShred()
        removePanicCode()
        prefs.edit().remove("wipe10").putInt("failed", 0).putLong("lockout", 0L).apply()
        wipeAfter10 = false
        vaultA.create(code)
    }

    /** Schlüssel löschen + Tresor-Ordner wegverschieben und im Hintergrund löschen. */
    private fun crypticShred() {
        HwKeys.delete(HwKeys.PEPPER_ALIAS)
        HwKeys.deletePrefix("tresor_vault_")
        HwKeys.deletePrefix("tresor_f_")
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
        vaultA.lock()
        vaultB.lock()
        crypticShred()
        removePanicCode()
        ThumbCache.clearAll()
        clearShareCache(force = true)
        prefs.edit().putInt("failed", 0).putLong("lockout", 0L).apply()
        vault = null
        nav.reset(Screen.Setup)
    }

    fun applyDisguise(d: Disguise) {
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
        disguise = d
        prefs.edit().putString("disguiseName", d.name).apply()
    }
}
