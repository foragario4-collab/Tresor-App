package app.glass.tresor.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.withResumed
import app.glass.tresor.App
import app.glass.tresor.Disguise
import app.glass.tresor.ui.Auth
import app.glass.tresor.ui.Blob
import app.glass.tresor.ui.G
import app.glass.tresor.ui.GlassBackground
import app.glass.tresor.ui.GlassIconButton
import app.glass.tresor.ui.Haptic
import app.glass.tresor.ui.Ic
import app.glass.tresor.ui.PillButton
import app.glass.tresor.ui.glass
import app.glass.tresor.ui.pressable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

val lockBlobs = listOf(
    Blob(Color(0xFF6D4CFF), 0.1f, 0.0f, 1.0f, 0.5f),
    Blob(Color(0xFF0EA5C6), 1.1f, 0.75f, 0.9f, 0.42f),
)

/** Ziffernblock im Glas-Look mit Punkten, Schütteln bei falscher Eingabe. */
@Composable
fun PinPad(
    title: String,
    subtitle: String,
    pin: String,
    onPin: (String) -> Unit,
    shakeKey: Int,
    busy: Boolean,
    confirmLabel: String,
    minLen: Int = 4,
    maxLen: Int = 12,
    showBio: Boolean = false,
    onBio: () -> Unit = {},
    onConfirm: () -> Unit,
) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeKey) {
        if (shakeKey == 0) return@LaunchedEffect
        for (v in listOf(18f, -16f, 12f, -8f, 4f, 0f)) shake.animateTo(v, tween(45))
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(64.dp))
        Box(Modifier.size(64.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
            Icon(Ic.lock, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = G.title2)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = G.callout.copy(color = G.dim), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(Modifier.height(28.dp))
        Row(
            Modifier.height(16.dp).offset { IntOffset(shake.value.roundToInt(), 0) },
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val shown = maxOf(minLen, pin.length)
            for (i in 0 until shown) PinDot(filled = i < pin.length)
        }
        Spacer(Modifier.weight(1f))
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("bio", "0", "del"))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            for (r in rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                    for (k in r) {
                        when (k) {
                            "bio" -> if (showBio) {
                                GlassIconButton(Ic.fingerprint, "Systemsperre", size = 78.dp, iconSize = 30.dp, fill = Color.Transparent, onClick = onBio)
                            } else Spacer(Modifier.size(78.dp))
                            "del" -> Box(
                                Modifier.size(78.dp).pressable(CircleShape, scaleTo = 0.9f, haptic = Haptic.KEY) {
                                    if (pin.isNotEmpty()) onPin(pin.dropLast(1))
                                },
                                contentAlignment = Alignment.Center
                            ) { Icon(Ic.backspace, "Löschen", tint = Color.White, modifier = Modifier.size(28.dp)) }
                            else -> Box(
                                Modifier
                                    .size(78.dp)
                                    .pressable(CircleShape, scaleTo = 0.9f, highlight = 0.2f, haptic = Haptic.KEY, enabled = !busy) {
                                        if (pin.length < maxLen) onPin(pin + k)
                                    }
                                    .glass(CircleShape),
                                contentAlignment = Alignment.Center
                            ) { Text(k, style = G.body.copy(fontSize = 32.sp, fontWeight = FontWeight.Normal)) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Box(Modifier.height(52.dp), contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
            else PillButton(confirmLabel, primary = true, enabled = pin.length >= minLen, onClick = onConfirm)
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun PinDot(filled: Boolean) {
    val s by animateFloatAsState(if (filled) 1.1f else 1f, spring(dampingRatio = 0.45f, stiffness = 600f), label = "dot")
    val bg by animateColorAsState(if (filled) Color.White else Color.Transparent, tween(150), label = "dotbg")
    Box(
        Modifier
            .size(14.dp)
            .graphicsLayer { scaleX = s; scaleY = s }
            .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
            .background(bg, CircleShape)
    )
}

@Composable
fun LockScreen(asRoot: Boolean = false) {
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as FragmentActivity
    val view = LocalView.current
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var shake by remember { mutableIntStateOf(0) }
    var lockout by remember { mutableStateOf(App.lockoutRemainingMs()) }
    val bio = remember { App.vaultA.bioEnabled() && Auth.available(activity) }

    LaunchedEffect(lockout > 0) {
        while (lockout > 0) {
            delay(1000); lockout = App.lockoutRemainingMs()
        }
    }

    fun tryBio() {
        val c = App.vaultA.bioDecryptCipher() ?: return
        Auth.prompt(activity, "Entsperren", null, c, onSuccess = { ok ->
            scope.launch {
                val success = withContext(Dispatchers.Default) { App.vaultA.unlockWithBio(ok) }
                if (success) App.onBioUnlocked()
            }
        }, onFail = {})
    }

    GlassBackground(lockBlobs) {
        val sub = when {
            lockout > 0 -> "Zu viele Versuche. Warte ${(lockout / 1000) + 1} s."
            App.failedCount() > 0 -> "Falscher Code"
            else -> "Tresor ist gesperrt"
        }
        PinPad(
            title = "Code eingeben",
            subtitle = sub,
            pin = pin,
            onPin = { pin = it },
            shakeKey = shake,
            busy = busy,
            confirmLabel = "Entsperren",
            showBio = bio,
            onBio = { tryBio() },
            onConfirm = {
                busy = true
                scope.launch {
                    val ok = App.tryUnlock(pin, silent = false)
                    busy = false
                    if (!ok) {
                        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                        pin = ""
                        shake++
                        lockout = App.lockoutRemainingMs()
                    }
                }
            }
        )
        if (!asRoot) {
            Box(Modifier.statusBarsPadding().padding(start = 20.dp, top = 12.dp)) {
                GlassIconButton(Ic.close, "Abbrechen") { App.nav.pop() }
            }
        }
    }
    // Fingerabdruck erst anbieten, wenn die App wirklich im Vordergrund ist
    LaunchedEffect(Unit) {
        activity.lifecycle.withResumed { }
        if (bio && lockout == 0L) tryBio()
    }
}

@Composable
fun SetupScreen() {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var first by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var shake by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    GlassBackground {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (fadeIn(tween(250)) + scaleIn(initialScale = 0.96f)).togetherWith(fadeOut(tween(150)) + scaleOut(targetScale = 1.02f))
            },
            label = "setup"
        ) { st ->
            when (st) {
                0 -> Column(
                    Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(84.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Ic.shield, null, tint = G.mint, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("Dein Tresor", style = G.largeTitle)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Fotos, Videos, Dateien und Notizen – mit AES-256 verschlüsselt und hinter einer unauffälligen App versteckt.",
                        style = G.callout.copy(color = G.dim), textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(28.dp))
                    Column(Modifier.fillMaxWidth().glass(app.glass.tresor.ui.sq(24)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SetupPoint(Ic.lock, "Nur mit deinem Code", "Ohne Code bleibt alles unlesbarer Datensalat.")
                        SetupPoint(Ic.calc, "Tarnung auf Wunsch", "Später als Rechner, Kalender, Checkliste oder News tarnbar.")
                        SetupPoint(Ic.shield, "Kein Zugriff von außen", "Keine Cloud, keine Werbung, keine Tracker.")
                    }
                    Spacer(Modifier.weight(1f))
                    PillButton("Code festlegen", primary = true, modifier = Modifier.fillMaxWidth()) { step = 1 }
                    Spacer(Modifier.height(12.dp))
                }
                1 -> PinPad(
                    title = "Code festlegen",
                    subtitle = error ?: "4 bis 12 Ziffern. Diesen Code brauchst du für alles.",
                    pin = pin, onPin = { pin = it; error = null }, shakeKey = shake, busy = false,
                    confirmLabel = "Weiter",
                    onConfirm = {
                        val weak = pin.toSet().size == 1 || "0123456789".contains(pin) || "9876543210".contains(pin)
                        if (weak) {
                            error = "Zu einfach. Bitte einen anderen Code."
                            pin = ""; shake++
                        } else {
                            first = pin; pin = ""; step = 2
                        }
                    }
                )
                else -> PinPad(
                    title = "Code bestätigen",
                    subtitle = error ?: "Gib den Code noch einmal ein.",
                    pin = pin, onPin = { pin = it; error = null }, shakeKey = shake, busy = busy,
                    confirmLabel = "Tresor erstellen",
                    onConfirm = {
                        if (pin != first) {
                            error = "Stimmt nicht überein."
                            pin = ""; shake++
                            return@PinPad
                        }
                        busy = true
                        scope.launch {
                            withContext(Dispatchers.Default) { App.vaultA.create(pin.toCharArray()) }
                            App.applyDisguise(Disguise.TRESOR)
                            App.vault = App.vaultA
                            busy = false
                            App.nav.openVault()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SetupPoint(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = G.headline.copy(fontSize = 16.sp))
            Text(text, style = G.footnote)
        }
    }
}
