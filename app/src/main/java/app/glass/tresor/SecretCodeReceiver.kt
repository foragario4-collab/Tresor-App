package app.glass.tresor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * Geheimer Telefoncode *#*#ZAHL#*#* (Standard 8737 = T-R-E-S, in den Einstellungen änderbar).
 * Die Telefon-App meldet den Code an Apps, die ihn kennen – so lässt sich der Tresor
 * öffnen, auch wenn sein Symbol komplett versteckt ist.
 */
class SecretCodeReceiver : BroadcastReceiver() {
    companion object {
        private const val CH = "open"
        private const val ID = 7020
    }

    override fun onReceive(ctx: Context, intent: Intent?) {
        // Jeder Geheimcode kommt hier an – nur der eigene öffnet die App
        val code = intent?.data?.host ?: return
        if (!DialCode.matches(ctx, code)) return
        App.init(ctx)
        val open = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        try {
            ctx.startActivity(open)
        } catch (_: Exception) {
        }
        // Falls Android das direkte Öffnen aus dem Hintergrund blockiert: Mitteilung zum Antippen
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH, "Öffnen", NotificationManager.IMPORTANCE_HIGH))
            val pi = PendingIntent.getActivity(ctx, 2, open, PendingIntent.FLAG_IMMUTABLE)
            nm.notify(
                ID, NotificationCompat.Builder(ctx, CH)
                    .setSmallIcon(R.drawable.ic_stat_sync)
                    .setContentTitle("Tippen zum Öffnen")
                    .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setTimeoutAfter(30_000)
                    .setContentIntent(pi)
                    .build()
            )
        } catch (_: Exception) {
        }
    }
}
