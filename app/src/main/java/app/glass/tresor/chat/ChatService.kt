package app.glass.tresor.chat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.glass.tresor.R
import org.torproject.jni.TorService
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Hält Tor im Hintergrund am Laufen, damit dein Handy für Kontakte erreichbar ist –
 * auch wenn der Tresor gesperrt ist. Der Dienst sieht nur verschlüsselte Pakete.
 *
 * Eingehend:  Tor-Onion-Dienst (Port 80) → 127.0.0.1:<Port des Tresors> → Posteingang
 * Ausgehend:  Postausgang → SOCKS5 (Tor) → .onion-Adresse des Kontakts
 */
class ChatService : Service() {

    companion object {
        private const val CH_SERVICE = "sync"
        private const val CH_MSG = "inbox"
        private const val ID_FG = 7001
        private const val ID_MSG = 7002
        private const val ACTION_RELOAD = "reload"
        private const val ACTION_KICK = "kick"
        private const val MAX_FRAME = 8 * 1024 * 1024

        /** Für die Anzeige im Chat: aus / verbindet / bereit. */
        @Volatile
        var state: String = "Aus"

        @Volatile
        var running = false

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, ChatService::class.java))
            } catch (_: Exception) {
            }
        }

        /** Neu starten, z. B. wenn ein Tresor neu für den Chat angemeldet wurde. */
        fun reload(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, ChatService::class.java).setAction(ACTION_RELOAD))
            } catch (_: Exception) {
            }
        }

        /** Postausgang sofort versuchen. */
        fun kick(ctx: Context) {
            if (!running) {
                start(ctx); return
            }
            try {
                ctx.startService(Intent(ctx, ChatService::class.java).setAction(ACTION_KICK))
            } catch (_: Exception) {
            }
        }

        fun stop(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, ChatService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    @Volatile
    private var tor: TorService? = null

    @Volatile
    private var bound = false
    private val listeners = ConcurrentHashMap<String, ServerSocket>()
    private val exec = Executors.newCachedThreadPool()
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val signal: java.lang.Object = java.lang.Object()
    private var loop: Thread? = null

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            tor = (binder as? TorService.LocalBinder)?.service
            wake()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            tor = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        channels()
        val n = NotificationCompat.Builder(this, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setContentTitle("Synchronisierung aktiv")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, ID_FG, n, type)
        running = true
        state = "Verbindet …"
        exec.execute { startTor() }
        loop = Thread { runLoop() }.apply { isDaemon = true; start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RELOAD -> exec.execute { restartTor() }
            ACTION_KICK -> wake()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        state = "Aus"
        wake()
        listeners.values.forEach { try { it.close() } catch (_: Exception) {} }
        listeners.clear()
        stopTor()
        exec.shutdownNow()
        super.onDestroy()
    }

    private fun channels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_SERVICE, "Synchronisierung", NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(NotificationChannel(CH_MSG, "Neue Nachrichten", NotificationManager.IMPORTANCE_DEFAULT))
    }

    // ───────────────────────── Tor ─────────────────────────

    @Synchronized
    private fun startTor() {
        val reg = ChatSpool.registry(this)
        if (reg.isEmpty()) {
            stopSelf(); return
        }
        // Onion-Dienste in die Tor-Konfiguration schreiben (eine Adresse pro Tresor)
        File(filesDir, "tor").mkdirs()
        val sb = StringBuilder()
        reg.forEach {
            sb.append("HiddenServiceDir ").append(ChatSpool.hsDir(this, it.slot).absolutePath).append('\n')
            sb.append("HiddenServicePort 80 127.0.0.1:").append(it.port).append('\n')
        }
        TorService.getTorrc(this).writeText(sb.toString())
        startListeners(reg)
        bound = bindService(Intent(this, TorService::class.java), conn, Context.BIND_AUTO_CREATE)
    }

    @Synchronized
    private fun stopTor() {
        if (bound) {
            try { unbindService(conn) } catch (_: Exception) {}
            bound = false
        }
        try { stopService(Intent(this, TorService::class.java)) } catch (_: Exception) {}
        tor = null
    }

    private fun restartTor() {
        state = "Verbindet …"
        stopTor()
        try { Thread.sleep(2500) } catch (_: InterruptedException) {}
        startTor()
    }

    private fun socksPort(): Int = try {
        tor?.socksPort ?: -1
    } catch (e: Exception) {
        -1
    }

    // ───────────────────────── Eingehend ─────────────────────────

    private fun startListeners(reg: List<ChatSpool.Entry>) {
        reg.forEach { e ->
            if (listeners.containsKey(e.slot)) return@forEach
            try {
                val ss = ServerSocket(e.port, 50, InetAddress.getByName("127.0.0.1"))
                listeners[e.slot] = ss
                exec.execute {
                    while (running && !ss.isClosed) {
                        try {
                            val s = ss.accept()
                            exec.execute { receive(s, e.slot) }
                        } catch (_: Exception) {
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun receive(s: Socket, slot: String) {
        try {
            s.use {
                it.soTimeout = 120_000
                val inp = DataInputStream(it.getInputStream())
                val len = inp.readInt()
                if (len <= 0 || len > MAX_FRAME) return
                val frame = ByteArray(len)
                inp.readFully(frame)
                ChatSpool.putInbox(this, slot, frame)
                val out = it.getOutputStream()
                out.write(1)
                out.flush()
            }
            notifyNew()
        } catch (_: Exception) {
        }
    }

    private fun notifyNew() {
        if (!app.glass.tresor.App.prefs.getBoolean("chatNotify", true)) return
        try {
            val launch = packageManager.getLaunchIntentForPackage(packageName)
            val pi = launch?.let {
                android.app.PendingIntent.getActivity(this, 0, it, android.app.PendingIntent.FLAG_IMMUTABLE)
            }
            val n: Notification = NotificationCompat.Builder(this, CH_MSG)
                .setSmallIcon(R.drawable.ic_stat_sync)
                .setContentTitle("Neue Nachricht")
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .apply { if (pi != null) setContentIntent(pi) }
                .build()
            getSystemService(NotificationManager::class.java).notify(ID_MSG, n)
        } catch (_: Exception) {
        }
    }

    // ───────────────────────── Ausgehend ─────────────────────────

    private fun wake() = synchronized(signal) { signal.notifyAll() }

    private fun runLoop() {
        while (running) {
            val port = socksPort()
            if (port > 0) {
                state = "Bereit"
                ChatSpool.outbox(this).forEach { o ->
                    if (!running) return@forEach
                    if (send(port, o.to, o.frame)) ChatSpool.markSent(this, o)
                }
            } else if (tor != null) {
                state = "Verbindet …"
            }
            synchronized(signal) {
                try { signal.wait(if (port > 0) 30_000 else 3_000) } catch (_: InterruptedException) {}
            }
        }
    }

    /** Verbindet über Tor (SOCKS5) mit der .onion-Adresse und überträgt ein Paket. */
    private fun send(socks: Int, onion: String, frame: ByteArray): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress("127.0.0.1", socks), 10_000)
                s.soTimeout = 120_000
                val inp = DataInputStream(s.getInputStream())
                val out = DataOutputStream(s.getOutputStream())
                // SOCKS5: keine Anmeldung
                out.write(byteArrayOf(5, 1, 0)); out.flush()
                val greet = ByteArray(2); inp.readFully(greet)
                if (greet[0].toInt() != 5 || greet[1].toInt() != 0) return false
                // CONNECT zu <onion>:80 (Domainname, Tor löst ihn auf)
                val host = onion.toByteArray(Charsets.US_ASCII)
                out.write(byteArrayOf(5, 1, 0, 3, host.size.toByte()))
                out.write(host)
                out.write(byteArrayOf(0, 80))
                out.flush()
                val head = ByteArray(4); inp.readFully(head)
                if (head[1].toInt() != 0) return false
                when (head[3].toInt()) {
                    1 -> inp.readFully(ByteArray(4 + 2))
                    3 -> inp.readFully(ByteArray((inp.readUnsignedByte()) + 2))
                    4 -> inp.readFully(ByteArray(16 + 2))
                    else -> return false
                }
                // Paket: Länge + Inhalt, dann Bestätigung abwarten
                out.writeInt(frame.size)
                out.write(frame)
                out.flush()
                inp.read() == 1
            }
        } catch (e: Exception) {
            false
        }
    }
}

/** Nach einem Neustart des Handys den Chat wieder erreichbar machen. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        app.glass.tresor.App.init(context)
        if (app.glass.tresor.App.prefs.getBoolean("chatBackground", true) && ChatSpool.registry(context).isNotEmpty()) {
            ChatService.start(context)
        }
    }
}
