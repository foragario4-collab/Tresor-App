package app.glass.tresor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.glass.tresor.ui.Pill
import app.glass.tresor.ui.glass
import kotlinx.coroutines.delay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.glass.tresor.disguise.DisguiseRoot
import app.glass.tresor.ui.G
import app.glass.tresor.ui.screens.AlbumsScreen
import app.glass.tresor.ui.screens.FolderScreen
import app.glass.tresor.ui.screens.FolderSettingsScreen
import app.glass.tresor.ui.screens.HomeScreen
import app.glass.tresor.ui.screens.LockScreen
import app.glass.tresor.ui.screens.NoteEditScreen
import app.glass.tresor.ui.screens.SettingsScreen
import app.glass.tresor.ui.screens.SetupScreen
import app.glass.tresor.ui.screens.TextViewScreen
import app.glass.tresor.ui.screens.ViewerScreen
import kotlin.math.sqrt

class MainActivity : FragmentActivity() {

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Während eines Gesprächs schaltet der Näherungssensor den Bildschirm am Ohr ab – dann nicht sperren
            if (!app.glass.tresor.chat.Calls.busy) App.lock()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        App.init(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        applyDisguise(intent)
        ContextCompat.registerReceiver(
            this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // Schutz gegen unsichtbare Overlays über der Code-Eingabe (Android 12+ macht das selbst)
        window.decorView.filterTouchesWhenObscured = android.os.Build.VERSION.SDK_INT < 31
        setContent { Root() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyDisguise(intent)
    }

    private fun applyDisguise(i: Intent?) {
        val d = Disguise.fromAlias(i?.component?.className) ?: return
        if (d != App.disguise) {
            App.disguise = d
            App.prefs.edit().putString("disguiseName", d.name).apply()
        }
    }

    override fun onResume() {
        super.onResume()
        App.suppressLock = false
    }

    /** Sofort-Sperre: sobald die App nicht mehr sichtbar ist. */
    override fun onStop() {
        super.onStop()
        if (!App.suppressLock && !isChangingConfigurations) App.lock()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenOff)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }
}

@Composable
private fun Root() {
    val activity = LocalContext.current as FragmentActivity
    val nav = App.nav

    LaunchedEffect(App.secure) {
        if (App.secure) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    MotionLock()
    FlipLock()

    // Wartender Anruf: sobald der passende Tresor offen ist, Anrufer entschlüsseln und anzeigen
    LaunchedEffect(app.glass.tresor.chat.Calls.pendingTick, App.vault, App.vault?.unlockedFolders?.size, App.vault?.revealed?.size) {
        val v = App.vault ?: return@LaunchedEffect
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Jedes sichtbare offene Chat-Konto prüfen – der Anruf gehört zu genau einem
                app.glass.tresor.chat.ChatEngine.openEngines(v, visibleOnly = true)
                    .forEach { app.glass.tresor.chat.Calls.resolveIncoming(it) }
            } catch (_: Exception) {
            }
        }
    }

    BackHandler(enabled = nav.stack.size > 1) {
        val top = nav.top
        if (top == Screen.Home) App.lock()
        else {
            nav.pop()
            if (nav.top == Screen.Disguise && App.vault != null) App.lock()
        }
    }

    if (!App.genuine) {
        TamperedScreen()
        return
    }

    Box(Modifier.fillMaxSize().background(G.bg)) {
        AnimatedContent(
            targetState = nav.top,
            transitionSpec = {
                val spec = spring<IntOffset>(dampingRatio = 1f, stiffness = 520f)
                val toVault = targetState == Screen.Home &&
                    (initialState == Screen.Disguise || initialState == Screen.Lock || initialState == Screen.Setup)
                if (toVault || targetState == Screen.Disguise) {
                    (fadeIn(tween(260)) + scaleIn(initialScale = 0.94f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f)))
                        .togetherWith(fadeOut(tween(180)) + scaleOut(targetScale = 1.04f))
                } else if (nav.forward) {
                    (slideInHorizontally(spec) { it } + fadeIn(tween(200)))
                        .togetherWith(slideOutHorizontally(spec) { -it / 4 } + fadeOut(tween(220)))
                } else {
                    (slideInHorizontally(spec) { -it / 4 } + fadeIn(tween(200)))
                        .togetherWith(slideOutHorizontally(spec) { it } + fadeOut(tween(220)))
                }
            },
            label = "nav"
        ) { s ->
            when (s) {
                Screen.Disguise -> DisguiseRoot()
                Screen.Setup -> SetupScreen()
                Screen.Lock -> LockScreen()
                Screen.Home -> HomeScreen()
                is Screen.Albums -> AlbumsScreen(s.kind, s.filter)
                is Screen.FolderView -> FolderScreen(s.fid, s.filter)
                is Screen.Viewer -> ViewerScreen(s.fid, s.iid, s.filter)
                is Screen.TextView -> TextViewScreen(s.fid, s.iid)
                is Screen.NoteEdit -> NoteEditScreen(s.fid, s.iid)
                Screen.Settings -> SettingsScreen()
                is Screen.FolderSettings -> FolderSettingsScreen(s.fid)
                Screen.Browser -> app.glass.tresor.ui.screens.BrowserScreen()
                Screen.Chat -> app.glass.tresor.ui.screens.ChatListScreen()
                is Screen.ChatThread -> app.glass.tresor.ui.screens.ChatThreadScreen(s.contactId)
                Screen.ChatCode -> app.glass.tresor.ui.screens.ChatCodeScreen()
                Screen.ChatTips -> app.glass.tresor.ui.screens.ChatTipsScreen()
                Screen.ChatAdd -> app.glass.tresor.ui.screens.ChatAddScreen()
                Screen.Player -> app.glass.tresor.ui.screens.PlayerScreen()
            }
        }
        app.glass.tresor.ui.screens.ChatBanner(Modifier.align(Alignment.TopCenter))
        app.glass.tresor.ui.screens.MiniPlayer(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp))
        app.glass.tresor.ui.screens.CallOverlay()
        Toast(Modifier.align(Alignment.BottomCenter))
    }
}

/** Wird gezeigt, wenn die App verändert und neu signiert wurde. Der Tresor bleibt dann zu. */
@Composable
private fun TamperedScreen() {
    app.glass.tresor.ui.GlassBackground {
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            androidx.compose.material3.Icon(app.glass.tresor.ui.Ic.shield, null, tint = G.red, modifier = Modifier.padding(bottom = 16.dp))
            Text("Diese App wurde verändert", style = G.title2)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))
            Text(
                "Sie ist nicht mit dem echten Schlüssel signiert. Das kann ein gefälschter Nachbau sein. Zu deinem Schutz bleibt der Tresor gesperrt. Lösche diese App und installiere die echte Version aus deinem GitHub-Repo.",
                style = G.callout.copy(color = G.dim),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** Kleiner Glas-Hinweis unten, verschwindet nach 2,2 s. */
@Composable
private fun Toast(modifier: Modifier) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(App.toastId) {
        if (App.toastId == 0) return@LaunchedEffect
        shown = true
        delay(2200)
        shown = false
    }
    AnimatedVisibility(
        shown,
        modifier = modifier.navigationBarsPadding().padding(bottom = 100.dp),
        enter = fadeIn(tween(180)) + slideInVertically(spring(dampingRatio = 0.7f, stiffness = 500f)) { it },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 }
    ) {
        Text(
            App.toastMsg ?: "",
            style = G.callout.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.glass(Pill, G.sheet).padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}

/** Sperre beim Umdrehen: liegt das Handy mit dem Display nach unten, wird sofort gesperrt. */
@Composable
private fun FlipLock() {
    val ctx = LocalContext.current
    val active = App.vault != null && App.flipLock && !app.glass.tresor.chat.Calls.busy
    DisposableEffect(active) {
        if (!active) return@DisposableEffect onDispose { }
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val g = FloatArray(3)
        var downSince = 0L
        val startAt = System.currentTimeMillis() + 600
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                for (i in 0..2) g[i] = 0.8f * g[i] + 0.2f * e.values[i]
                if (System.currentTimeMillis() < startAt) return
                // z stark negativ = Display zeigt nach unten
                if (g[2] < -8.0f && kotlin.math.abs(g[0]) < 3f && kotlin.math.abs(g[1]) < 3f) {
                    if (downSince == 0L) downSince = System.currentTimeMillis()
                    else if (System.currentTimeMillis() - downSince > 350) App.lock()
                } else downSince = 0L
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
}

/** Sperre bei Bewegung: ruckartige Bewegung (z. B. Handy wird weggenommen) → sofort sperren. */
@Composable
private fun MotionLock() {
    val ctx = LocalContext.current
    // Beim Telefonieren nicht – das Handy wird ja ans Ohr gehoben
    val active = App.vault != null && App.motionLock && !app.glass.tresor.chat.Calls.busy
    DisposableEffect(active) {
        if (!active) return@DisposableEffect onDispose { }
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val linear = sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION
        val gravity = FloatArray(3)
        val startAt = System.currentTimeMillis() + 800
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (System.currentTimeMillis() < startAt) return
                val x: Float
                val y: Float
                val z: Float
                if (linear) {
                    x = e.values[0]; y = e.values[1]; z = e.values[2]
                } else {
                    for (i in 0..2) gravity[i] = 0.9f * gravity[i] + 0.1f * e.values[i]
                    x = e.values[0] - gravity[0]; y = e.values[1] - gravity[1]; z = e.values[2] - gravity[2]
                }
                if (sqrt(x * x + y * y + z * z) > App.motionThreshold()) App.lock()
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
}
