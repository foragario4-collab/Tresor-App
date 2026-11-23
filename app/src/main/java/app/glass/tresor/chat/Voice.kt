package app.glass.tresor.chat

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.RandomAccessFile

/**
 * Aufnahme einer Sprachnachricht (Opus, sonst AAC).
 * Die Aufnahme liegt nur kurz im privaten App-Speicher und wird danach überschrieben und gelöscht.
 */
class VoiceRecorder(private val ctx: Context, private val maxMs: Long = ChatEngine.MAX_VOICE_MS, tmpName: String = "voice.tmp") {
    private var rec: MediaRecorder? = null
    @Volatile private var maxReached = false
    private val file = File(ctx.noBackupFilesDir, tmpName)
    var startedAt = 0L
        private set

    fun start(): Boolean {
        cancel()
        for (opus in listOf(true, false)) {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
            try {
                r.setAudioSource(MediaRecorder.AudioSource.MIC)
                if (opus) {
                    r.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                    r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                    r.setAudioSamplingRate(48000)
                    r.setAudioEncodingBitRate(24000)
                } else {
                    r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    r.setAudioSamplingRate(44100)
                    r.setAudioEncodingBitRate(48000)
                }
                r.setAudioChannels(1)
                r.setMaxDuration(maxMs.toInt())
                maxReached = false
                r.setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) maxReached = true
                }
                r.setOutputFile(file.absolutePath)
                r.prepare()
                r.start()
                rec = r
                startedAt = System.currentTimeMillis()
                return true
            } catch (e: Exception) {
                try { r.release() } catch (_: Exception) {}
                wipeFile()
            }
        }
        return false
    }

    val elapsed get() = if (rec == null) 0L else System.currentTimeMillis() - startedAt

    /** Lautstärke seit der letzten Abfrage, 0..1 (für die Pegel-Anzeige). */
    fun level(): Float = try {
        ((rec?.maxAmplitude ?: 0) / 32767f).coerceIn(0f, 1f)
    } catch (e: Exception) {
        0f
    }

    /** Hat die Aufnahme die Höchstlänge erreicht? */
    val limitReached get() = maxReached

    /** Beendet die Aufnahme. Gibt (Audio, Dauer) zurück oder null, wenn sie zu kurz war. */
    fun stop(): Pair<ByteArray, Long>? {
        val r = rec ?: return null
        val dur = elapsed
        rec = null
        val ok = try { r.stop(); true } catch (e: Exception) { maxReached }
        try { r.release() } catch (_: Exception) {}
        val data = if (ok && dur >= 700 && file.exists()) try { file.readBytes() } catch (e: Exception) { null } else null
        wipeFile()
        return data?.takeIf { it.isNotEmpty() }?.let { it to dur }
    }

    fun cancel() {
        rec?.let { r ->
            try { r.stop() } catch (_: Exception) {}
            try { r.release() } catch (_: Exception) {}
        }
        rec = null
        wipeFile()
    }

    private fun wipeFile() {
        try {
            if (file.exists()) {
                RandomAccessFile(file, "rw").use { raf ->
                    val zero = ByteArray(8192)
                    var left = raf.length()
                    while (left > 0) {
                        val n = minOf(left, zero.size.toLong()).toInt()
                        raf.write(zero, 0, n); left -= n
                    }
                }
                file.delete()
            }
        } catch (_: Exception) {
            file.delete()
        }
    }
}

/** Spielt Sprachnachrichten direkt aus dem Speicher ab – ohne entschlüsselte Datei. */
object VoicePlayer {
    var playingId by mutableStateOf<String?>(null)
        private set
    private var player: MediaPlayer? = null

    private class Bytes(private val b: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= b.size) return -1
            val n = minOf(size.toLong(), b.size - position).toInt()
            System.arraycopy(b, position.toInt(), buffer, offset, n)
            return n
        }

        override fun getSize(): Long = b.size.toLong()
        override fun close() {}
    }

    fun toggle(id: String, data: () -> ByteArray?) {
        if (playingId == id) { stop(); return }
        stop()
        val bytes = data() ?: return
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            mp.setDataSource(Bytes(bytes))
            mp.setOnCompletionListener { stop() }
            mp.setOnErrorListener { _, _, _ -> stop(); true }
            mp.prepare()
            mp.start()
            player = mp
            playingId = id
        } catch (e: Exception) {
            stop()
        }
    }

    /** Aktuelle Stelle und Länge der laufenden Wiedergabe in ms. */
    fun position(): Long = try { player?.currentPosition?.toLong() ?: 0L } catch (e: Exception) { 0L }
    fun duration(): Long = try { player?.duration?.toLong()?.coerceAtLeast(0L) ?: 0L } catch (e: Exception) { 0L }

    /** Fortschritt 0..1 der laufenden Wiedergabe. */
    fun progress(): Float = try {
        player?.let { if (it.duration > 0) it.currentPosition.toFloat() / it.duration else 0f } ?: 0f
    } catch (e: Exception) {
        0f
    }

    fun stop() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        playingId = null
    }
}
