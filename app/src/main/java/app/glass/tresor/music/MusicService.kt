package app.glass.tresor.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.glass.tresor.App
import app.glass.tresor.R

/**
 * Hält die Musik am Laufen, wenn „Musik im Hintergrund“ an ist.
 * Die Mitteilung zeigt bewusst keinen Liedtitel – nur „Wiedergabe“ mit Pause/Weiter/Stopp.
 */
class MusicService : Service() {
    companion object {
        const val ACTION_TOGGLE = "app.glass.tresor.music.TOGGLE"
        const val ACTION_NEXT = "app.glass.tresor.music.NEXT"
        const val ACTION_STOP = "app.glass.tresor.music.STOP"
        private const val CH = "music"
        private const val ID = 7010

        @Volatile
        private var running = false

        fun start(ctx: Context) {
            if (!App.prefs.getBoolean("musicBackground", false)) return
            try {
                if (!running) ctx.startForegroundService(Intent(ctx, MusicService::class.java))
                else update(ctx)
            } catch (_: Exception) {
            }
        }

        fun update(ctx: Context) {
            if (!running) return
            try { ctx.getSystemService(NotificationManager::class.java).notify(ID, build(ctx)) } catch (_: Exception) {}
        }

        fun stop(ctx: Context) {
            if (!running) return
            try { ctx.stopService(Intent(ctx, MusicService::class.java)) } catch (_: Exception) {}
        }

        private fun pi(ctx: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getService(ctx, code, Intent(ctx, MusicService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE)

        private fun build(ctx: Context): Notification {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH, "Musik", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
            val playing = MusicPlayer.playing
            return NotificationCompat.Builder(ctx, CH)
                .setSmallIcon(R.drawable.ic_stat_sync)
                .setContentTitle(if (playing) "Wiedergabe" else "Pausiert")
                .setOngoing(playing)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .addAction(0, if (playing) "Pause" else "Abspielen", pi(ctx, ACTION_TOGGLE, 1))
                .apply { if (!MusicPlayer.detached) addAction(0, "Weiter", pi(ctx, ACTION_NEXT, 2)) }
                .addAction(0, "Stopp", pi(ctx, ACTION_STOP, 3))
                .build()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            ServiceCompat.startForeground(this, ID, build(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            running = true
        } catch (e: Exception) {
            stopSelf(); return START_NOT_STICKY
        }
        intent?.action?.let { MusicPlayer.handle(it) }
        if (!MusicPlayer.active) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }
}
