package app.glass.tresor.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.glass.tresor.App
import app.glass.tresor.chat.CallPhase
import app.glass.tresor.chat.Calls
import app.glass.tresor.ui.G
import app.glass.tresor.ui.GlassBackground
import app.glass.tresor.ui.Haptic
import app.glass.tresor.ui.Ic
import app.glass.tresor.ui.pressable
import kotlinx.coroutines.delay

/** Anruf-Bildschirm. Liegt über allem anderen, solange ein Anruf läuft. */
@Composable
fun CallOverlay() {
    val phase = Calls.phase
    AnimatedVisibility(
        visible = phase != CallPhase.IDLE,
        enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f)),
        exit = fadeOut(tween(260))
    ) {
        CallContent()
    }
}

@Composable
private fun CallContent() {
    val ctx = LocalContext.current
    val phase = Calls.phase
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    BackHandler(enabled = phase != CallPhase.IDLE) { }

    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        App.suppressLock = false
        if (granted) Calls.accept() else App.toast("Ohne Mikrofon kann der Anruf nicht angenommen werden")
    }

    fun acceptWithMic() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) Calls.accept()
        else {
            App.suppressLock = true
            micPerm.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Gesprächsdauer
    LaunchedEffect(phase) {
        while (phase == CallPhase.ACTIVE) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }

    // Klingeln (eingehend) bzw. Freizeichen (ausgehend)
    DisposableEffect(phase) {
        var ring: android.media.Ringtone? = null
        var tone: ToneGenerator? = null
        var vib: Vibrator? = null
        try {
            if (phase == CallPhase.INCOMING) {
                ring = RingtoneManager.getRingtone(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                ring?.isLooping = true
                ring?.play()
                vib = if (android.os.Build.VERSION.SDK_INT >= 31) {
                    (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                vib?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 900), 0))
            } else if (phase == CallPhase.RINGING_OUT) {
                tone = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70)
                tone.startTone(ToneGenerator.TONE_SUP_RINGTONE)
            }
        } catch (_: Exception) {
        }
        onDispose {
            try { ring?.stop() } catch (_: Exception) {}
            try { vib?.cancel() } catch (_: Exception) {}
            try { tone?.stopTone(); tone?.release() } catch (_: Exception) {}
        }
    }

    val status = when (phase) {
        CallPhase.CONNECTING -> "Verbinde über Tor …"
        CallPhase.RINGING_OUT -> "Klingelt …"
        CallPhase.INCOMING -> "Eingehender Anruf"
        CallPhase.ACTIVE -> Calls.duration(now - Calls.startedAt)
        CallPhase.ENDED -> Calls.endText.ifBlank { "Beendet" }
        CallPhase.IDLE -> ""
    }

    // Fängt alle Berührungen ab, damit nichts darunter bedient wird
    GlassBackground(modifier = Modifier.pointerInput(Unit) { detectTapGestures { } }) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))
            Box(
                Modifier.size(112.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(Calls.peerName.take(1).uppercase().ifBlank { "?" }, style = G.largeTitle.copy(fontSize = 46.sp))
            }
            Spacer(Modifier.height(22.dp))
            Text(Calls.peerName, style = G.largeTitle.copy(fontSize = 30.sp), textAlign = TextAlign.Center, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Text(status, style = G.callout.copy(color = G.dim, fontSize = 17.sp), textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.lock, null, tint = G.faint, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(5.dp))
                Text("Ende-zu-Ende verschlüsselt · über Tor", style = G.footnote.copy(color = G.faint))
            }
            if (phase == CallPhase.ACTIVE) {
                Spacer(Modifier.height(6.dp))
                Text("Über Tor kann die Sprache etwas verzögert ankommen.", style = G.footnote.copy(color = G.faint), textAlign = TextAlign.Center)
            }

            Spacer(Modifier.weight(1f))

            when (phase) {
                CallPhase.INCOMING -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CallButton(Ic.hangup, "Ablehnen", G.red) { Calls.hangup() }
                    CallButton(Ic.phone, "Annehmen", G.green) { acceptWithMic() }
                }
                CallPhase.ACTIVE -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CallButton(if (Calls.muted) Ic.micOff else Ic.mic, if (Calls.muted) "Stumm an" else "Stumm", Color.White.copy(alpha = 0.14f), active = Calls.muted) { Calls.toggleMute() }
                    CallButton(Ic.hangup, "Auflegen", G.red) { Calls.hangup() }
                    CallButton(Ic.speaker, "Lautsprecher", Color.White.copy(alpha = 0.14f), active = Calls.speaker) { Calls.toggleSpeaker() }
                }
                CallPhase.CONNECTING, CallPhase.RINGING_OUT -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CallButton(Ic.hangup, "Abbrechen", G.red) { Calls.hangup() }
                }
                else -> Spacer(Modifier.height(96.dp))
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun CallButton(icon: ImageVector, label: String, color: Color, active: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(72.dp)
                .pressable(CircleShape, scaleTo = 0.9f, highlight = 0.1f, haptic = Haptic.CONFIRM, onClick = onClick)
                .clip(CircleShape)
                .background(if (active) Color.White else color),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = if (active) Color(0xFF0B0D12) else Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = G.footnote.copy(color = Color.White))
    }
}
