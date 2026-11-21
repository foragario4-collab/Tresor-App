package app.glass.tresor

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Selbst gewählter Telefoncode *#*#ZAHL#*#* zum Öffnen der versteckten App.
 * Die App empfängt jeden Geheimcode, reagiert aber nur auf den eigenen.
 */
object DialCode {
    const val DEFAULT = "8737"
    const val ACTION = "android.provider.Telephony.SECRET_CODE"

    /** Bekannte Codes von Android, Samsung, Pixel, Xiaomi, Huawei & Co. – würden sonst ein Systemmenü öffnen. */
    private val SYSTEM = setOf(
        "4636", "225", "426", "0", "0000", "0011", "0228", "0283", "0289", "0588", "0673", "0842", "0842", "1111", "1234",
        "1472365", "1575", "197328640", "2222", "2432546", "2663", "2664", "273283", "284", "2846579", "2846579159",
        "232331", "232337", "232338", "232339", "3264", "3282", "34971539", "3424", "3646633", "37", "44336", "4777",
        "4986", "5376", "6484", "64663", "66336", "6984125", "7262626", "7378423", "7594", "759", "7780", "8255",
        "83781", "8350", "8351", "9090", "9646633", "9900", "99", "07", "2767", "4238378", "6130", "1357946",
        "7465625", "2886", "3855", "8888", "9999", "86583", "86824", "3878", "4321", "7668", "4983", "6020", "0101"
    )

    fun current(ctx: Context): String = prefs(ctx).getString("dialCode", DEFAULT) ?: DEFAULT

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    /**
     * Passt der gewählte Code? Solange der neue Code noch nicht getestet ist,
     * funktioniert der alte weiter – so sperrt man sich nie aus.
     */
    fun matches(ctx: Context, code: String): Boolean {
        val p = prefs(ctx)
        if (code == current(ctx)) {
            p.edit().putBoolean("secretCodeOk", true).remove("dialCodeOld").apply()
            return true
        }
        val old = p.getString("dialCodeOld", null)
        return old != null && code == old
    }

    /** Fehlermeldung oder null, wenn der Code frei und gültig ist. */
    fun check(ctx: Context, code: String): String? {
        val c = code.trim()
        if (c.length !in 4..12 || !c.all { it.isDigit() }) return "Bitte 4 bis 12 Ziffern."
        if (c.toSet().size == 1) return "Zu einfach – nicht nur dieselbe Ziffer."
        if ("0123456789012".contains(c) || "9876543210987".contains(c)) return "Zu einfach – keine Zahlenreihe."
        if (c in SYSTEM) return "Dieser Code ist schon vom System belegt (öffnet ein Service-Menü)."
        if (c == current(ctx)) return "Das ist schon dein aktueller Code."
        // Andere installierte Apps, die genau diesen Code verwenden
        val other = try {
            ctx.packageManager.queryBroadcastReceivers(Intent(ACTION, Uri.parse("android_secret_code://$c")), 0)
                .filter { it.activityInfo?.packageName != ctx.packageName }
        } catch (e: Exception) {
            emptyList()
        }
        if (other.isNotEmpty()) {
            val name = try { other.first().loadLabel(ctx.packageManager).toString() } catch (e: Exception) { "eine andere App" }
            return "Dieser Code ist schon belegt (von „$name“)."
        }
        return null
    }

    /** Neuen Code speichern. Er muss danach einmal getestet werden. */
    fun set(ctx: Context, code: String) {
        val p = prefs(ctx)
        val prevOk = p.getBoolean("secretCodeOk", false)
        val e = p.edit().putString("dialCode", code.trim()).putBoolean("secretCodeOk", false)
        // War der alte Code getestet, bleibt er bis zum Test des neuen gültig
        if (prevOk) e.putString("dialCodeOld", current(ctx)) else e.remove("dialCodeOld")
        e.apply()
    }

    fun display(code: String) = "*#*#$code#*#*"
}
