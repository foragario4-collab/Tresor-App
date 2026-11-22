package app.glass.tresor.chat

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

/**
 * Mikrofon und Lautsprecher für Anrufe.
 * Aufnahme → 8 kHz → µ-law-Pakete à 40 ms → [onFrame]. Empfangene Pakete → Puffer → Wiedergabe.
 * Der Puffer bleibt kurz (max. ~0,6 s), damit die Verzögerung über Tor nicht immer weiter wächst.
 */
class CallAudio(private val ctx: Context, private val onFrame: (ByteArray) -> Unit) {
    companion object {
        const val RATE = 8000
        const val FRAME = 320 // 40 ms bei 8 kHz
        private const val MAX_QUEUE = 15
        private const val KEEP_QUEUE = 5
    }

    @Volatile
    var muted = false

    @Volatile
    private var running = false
    private val queue = LinkedBlockingDeque<ShortArray>()
    private var rec: AudioRecord? = null
    private var track: AudioTrack? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var recThread: Thread? = null
    private var playThread: Thread? = null
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var oldMode = AudioManager.MODE_NORMAL

    @SuppressLint("MissingPermission") // Berechtigung wird vor dem Anruf abgefragt
    fun start() {
        if (running) return
        running = true
        oldMode = am.mode
        try { am.mode = AudioManager.MODE_IN_COMMUNICATION } catch (_: Exception) {}

        // Aufnahme: bevorzugt direkt 8 kHz, sonst höher aufnehmen und herunterrechnen
        var recRate = 0
        for (r in intArrayOf(8000, 16000, 48000)) {
            val min = AudioRecord.getMinBufferSize(r, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (min <= 0) continue
            try {
                val ar = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION, r,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, r / 5 * 2)
                )
                if (ar.state == AudioRecord.STATE_INITIALIZED) {
                    rec = ar; recRate = r; break
                }
                ar.release()
            } catch (_: Exception) {
            }
        }
        rec?.let { ar ->
            try { if (AcousticEchoCanceler.isAvailable()) aec = AcousticEchoCanceler.create(ar.audioSessionId)?.apply { setEnabled(true) } } catch (_: Exception) {}
            try { if (NoiseSuppressor.isAvailable()) ns = NoiseSuppressor.create(ar.audioSessionId)?.apply { setEnabled(true) } } catch (_: Exception) {}
            val factor = recRate / RATE
            recThread = Thread({ recordLoop(ar, factor) }, "call-rec").apply { start() }
        }

        val minOut = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        try {
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minOut, FRAME * 2 * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: Exception) {
            track = null
        }
        track?.let { t ->
            playThread = Thread({ playLoop(t) }, "call-play").apply { start() }
        }
    }

    private fun recordLoop(ar: AudioRecord, factor: Int) {
        val f = maxOf(1, factor)
        val buf = ShortArray(FRAME * f)
        val out = ShortArray(FRAME)
        try {
            ar.startRecording()
            while (running) {
                var got = 0
                while (got < buf.size && running) {
                    val n = ar.read(buf, got, buf.size - got)
                    if (n <= 0) break
                    got += n
                }
                if (!running || got < buf.size) {
                    if (got <= 0) Thread.sleep(10)
                    continue
                }
                // Herunterrechnen auf 8 kHz (Mittelwert = einfacher Tiefpass)
                for (i in 0 until FRAME) {
                    var sum = 0
                    for (k in 0 until f) sum += buf[i * f + k]
                    out[i] = (sum / f).toShort()
                }
                if (!muted) onFrame(Mulaw.encode(out))
            }
        } catch (_: Exception) {
        } finally {
            try { ar.stop() } catch (_: Exception) {}
        }
    }

    /** Ein empfangenes Paket (µ-law) in die Wiedergabe geben. */
    fun play(data: ByteArray) {
        if (!running || data.isEmpty() || data.size > 4000) return
        queue.offerLast(Mulaw.decode(data))
        // Zu viel angestaut (z. B. nach einem Tor-Stocken) → Älteres verwerfen, Verzögerung klein halten
        if (queue.size > MAX_QUEUE) while (queue.size > KEEP_QUEUE) queue.pollFirst()
    }

    private fun playLoop(t: AudioTrack) {
        val silence = ShortArray(FRAME)
        try {
            t.play()
            var primed = false
            while (running) {
                if (!primed) {
                    // Kurz vorpuffern, damit es nicht abgehackt klingt
                    if (queue.size < 3) {
                        if (t.write(silence, 0, silence.size) < 0) Thread.sleep(40)
                        continue
                    }
                    primed = true
                }
                val frame = queue.pollFirst(80, TimeUnit.MILLISECONDS)
                val n = if (frame == null) {
                    primed = false
                    t.write(silence, 0, silence.size)
                } else {
                    t.write(frame, 0, frame.size)
                }
                if (n < 0) Thread.sleep(40)
            }
        } catch (_: Exception) {
        } finally {
            try { t.stop() } catch (_: Exception) {}
        }
    }

    fun setSpeaker(on: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                if (on) {
                    am.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                        ?.let { am.setCommunicationDevice(it) }
                } else am.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = on
            }
        } catch (_: Exception) {
        }
    }

    fun stop() {
        if (!running) return
        running = false
        try { recThread?.join(500) } catch (_: Exception) {}
        try { playThread?.join(500) } catch (_: Exception) {}
        try { aec?.release() } catch (_: Exception) {}
        try { ns?.release() } catch (_: Exception) {}
        try { rec?.release() } catch (_: Exception) {}
        try { track?.release() } catch (_: Exception) {}
        rec = null; track = null; aec = null; ns = null
        queue.clear()
        setSpeaker(false)
        try { am.mode = oldMode } catch (_: Exception) {}
    }
}
