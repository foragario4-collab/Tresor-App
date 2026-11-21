package app.glass.tresor.music

import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import app.glass.tresor.App
import app.glass.tresor.vault.Item
import app.glass.tresor.vault.Vault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RepeatMode { OFF, ALL, ONE }

/**
 * Musik aus dem Tresor. Das laufende Lied wird entschlüsselt nur im Arbeitsspeicher gehalten,
 * nie als Datei. Beim Sperren stoppt die Musik – außer „Musik im Hintergrund“ ist an:
 * dann läuft das aktuelle Lied weiter, das nächste kann erst nach dem Entsperren geladen werden.
 */
@OptIn(UnstableApi::class)
object MusicPlayer {
    /** Lieder über dieser Größe werden nicht in den Speicher geladen. */
    private const val MAX_BYTES = 150L * 1024 * 1024

    var current by mutableStateOf<Pair<String, Item>?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeat by mutableStateOf(RepeatMode.OFF)
        private set
    /** true = Tresor ist gesperrt, es läuft nur noch das aktuelle Lied weiter. */
    var detached by mutableStateOf(false)
        private set

    private var queue: List<Pair<String, Item>> = emptyList()
    private var order: List<Int> = emptyList()
    private var pos = 0
    private var vault: Vault? = null
    private var player: ExoPlayer? = null
    private var loadJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val active get() = current != null

    private fun ensurePlayer(): ExoPlayer = player ?: ExoPlayer.Builder(App.ctx).build().also { p ->
        p.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            /* handleAudioFocus = */ true
        )
        p.setHandleAudioBecomingNoisy(true) // Kopfhörer raus → Pause
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                MusicService.update(App.ctx)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded()
            }
        })
        player = p
    }

    /** Liste abspielen, beginnend bei [start]. */
    fun play(v: Vault, list: List<Pair<String, Item>>, start: Int) {
        if (list.isEmpty()) return
        vault = v
        detached = false
        queue = list
        order = makeOrder(list.size, start.coerceIn(0, list.size - 1))
        pos = order.indexOf(start.coerceIn(0, list.size - 1)).coerceAtLeast(0)
        load()
    }

    private fun makeOrder(n: Int, first: Int): List<Int> {
        if (!shuffle) return (0 until n).toList()
        val rest = (0 until n).filter { it != first }.shuffled()
        return listOf(first) + rest
    }

    private fun load(autoPlay: Boolean = true) {
        val v = vault ?: return
        val (fid, item) = queue.getOrNull(order.getOrNull(pos) ?: return) ?: return
        current = fid to item
        loadJob?.cancel()
        loadJob = scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                try {
                    if (item.size > MAX_BYTES || !v.isFolderUnlocked(fid)) null else v.reader(fid, item.id).readAll()
                } catch (e: Exception) {
                    null
                }
            }
            if (bytes == null) {
                App.toast("Lied kann nicht abgespielt werden")
                return@launch
            }
            val p = ensurePlayer()
            val factory = DataSource.Factory { ByteArrayDataSource(bytes) }
            p.setMediaSource(ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri("tresor://music/${item.id}")))
            p.prepare()
            p.playWhenReady = autoPlay
            MusicService.start(App.ctx)
        }
    }

    private fun onEnded() {
        when {
            repeat == RepeatMode.ONE -> { player?.seekTo(0); player?.play() }
            detached -> { stop() } // gesperrt: nächstes Lied ist nicht zugänglich
            pos + 1 < order.size -> { pos++; load() }
            repeat == RepeatMode.ALL -> { pos = 0; load() }
            else -> { player?.seekTo(0); player?.pause() }
        }
    }

    fun toggle() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else {
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
            p.play()
        }
    }

    fun next() {
        if (detached || order.isEmpty()) return
        pos = if (pos + 1 < order.size) pos + 1 else 0
        load()
    }

    fun previous() {
        val p = player ?: return
        if (detached || p.currentPosition > 3000 || order.isEmpty()) { p.seekTo(0); return }
        pos = if (pos > 0) pos - 1 else order.size - 1
        load()
    }

    fun seekTo(fraction: Float) {
        val p = player ?: return
        val d = p.duration.takeIf { it > 0 } ?: return
        p.seekTo((d * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun positionMs(): Long = player?.currentPosition ?: 0
    fun durationMs(): Long = player?.duration?.takeIf { it > 0 } ?: (current?.second?.durationMs ?: 0)

    fun toggleShuffle() {
        shuffle = !shuffle
        val cur = order.getOrNull(pos) ?: return
        order = makeOrder(queue.size, cur)
        pos = order.indexOf(cur).coerceAtLeast(0)
    }

    fun cycleRepeat() {
        repeat = when (repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    /** Wird beim Sperren aufgerufen. */
    fun onVaultLocked() {
        if (!active) return
        if (App.prefs.getBoolean("musicBackground", false) && playing) {
            // Aktuelles Lied liegt schon entschlüsselt im Speicher → weiterspielen, Liste vergessen
            detached = true
            queue = listOfNotNull(current)
            order = listOf(0); pos = 0
            vault = null
        } else stop()
    }

    fun stop() {
        loadJob?.cancel()
        try { player?.stop(); player?.clearMediaItems(); player?.release() } catch (_: Exception) {}
        player = null
        current = null
        playing = false
        detached = false
        queue = emptyList(); order = emptyList(); pos = 0
        vault = null
        MusicService.stop(App.ctx)
    }

    /** Für die Steuerung aus der Mitteilung. */
    internal fun handle(action: String?) {
        when (action) {
            MusicService.ACTION_TOGGLE -> toggle()
            MusicService.ACTION_NEXT -> next()
            MusicService.ACTION_STOP -> stop()
        }
    }

}
