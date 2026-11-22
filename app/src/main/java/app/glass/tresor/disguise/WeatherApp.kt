package app.glass.tresor.disguise

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.glass.tresor.App
import app.glass.tresor.ui.G
import app.glass.tresor.ui.GlassIconButton
import app.glass.tresor.ui.Ic
import app.glass.tresor.ui.Pill
import app.glass.tresor.ui.glass
import app.glass.tresor.ui.pressable
import app.glass.tresor.ui.sq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume

private fun wxIcon(code: Int, day: Boolean): ImageVector = when (WeatherData.kind(code)) {
    WeatherData.Kind.CLEAR -> if (day) Ic.sun else Ic.moon
    WeatherData.Kind.PARTLY -> Ic.partly
    WeatherData.Kind.CLOUD -> Ic.cloud
    WeatherData.Kind.FOG -> Ic.fog
    WeatherData.Kind.RAIN -> Ic.rain
    WeatherData.Kind.SNOW -> Ic.snow
    WeatherData.Kind.STORM -> Ic.storm
}

private fun wxColors(code: Int, day: Boolean): List<Color> = when {
    !day -> listOf(Color(0xFF0E1630), Color(0xFF1E2A55))
    else -> when (WeatherData.kind(code)) {
        WeatherData.Kind.CLEAR -> listOf(Color(0xFF2F80ED), Color(0xFF56B4F5))
        WeatherData.Kind.PARTLY -> listOf(Color(0xFF3A78C9), Color(0xFF7FA9D6))
        WeatherData.Kind.CLOUD, WeatherData.Kind.FOG -> listOf(Color(0xFF55657A), Color(0xFF8593A6))
        WeatherData.Kind.RAIN -> listOf(Color(0xFF2E4058), Color(0xFF52667F))
        WeatherData.Kind.SNOW -> listOf(Color(0xFF6E86A3), Color(0xFFA9BBD0))
        WeatherData.Kind.STORM -> listOf(Color(0xFF1F2533), Color(0xFF3E4659))
    }
}

private val weekday = SimpleDateFormat("EEE", Locale.GERMANY)
private val isoDay = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

/** Echte Wetter-App (Open-Meteo). Geheim: den Code in die Städtesuche tippen und „Suchen“ drücken. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeatherApp() {
    val ctx = LocalContext.current
    var city by remember { mutableStateOf(WeatherData.city(ctx)) }
    var now by remember { mutableStateOf(WeatherData.cached(ctx)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var choose by remember { mutableStateOf(city == null) }

    LaunchedEffect(city, reload) {
        val c = city ?: return@LaunchedEffect
        val fresh = now?.let { System.currentTimeMillis() - it.fetched < 10 * 60_000L } == true
        if (fresh && reload == 0) return@LaunchedEffect
        loading = true; error = false
        val r = withContext(Dispatchers.IO) { try { WeatherData.fetch(ctx, c) } catch (e: Exception) { null } }
        loading = false
        if (r == null) error = true else { now = r; WeatherWidget.refreshAll(ctx) }
    }

    if (choose) {
        CityChooser(canCancel = city != null, onCancel = { choose = false }) { c ->
            WeatherData.saveCity(ctx, c)
            now = null
            city = c
            choose = false
            WeatherWidget.refreshAll(ctx)
        }
        return
    }

    val n = now
    val colors = if (n != null) wxColors(n.code, n.day) else listOf(Color(0xFF2F80ED), Color(0xFF56B4F5))
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colors))) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 20.dp).padding(bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Ic.search, "Stadt wählen") { choose = true }
                Spacer(Modifier.weight(1f))
                if (loading) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                GlassIconButton(Ic.refresh, "Aktualisieren") { reload++ }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (city?.gps == true) { Icon(Ic.location, null, tint = Color.White, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
                Text(
                    city?.name ?: "", style = TextStyle(fontSize = 30.sp, color = Color.White, fontWeight = FontWeight.Medium),
                    // Geheim: Stadtnamen lange drücken → Code-Abfrage
                    modifier = Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                        onClick = { choose = true }, onLongClick = { openSecretLock() }
                    )
                )
            }
            if (n == null) {
                Spacer(Modifier.height(60.dp))
                if (error) {
                    Text("Keine Verbindung", style = G.headline)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.pressable(Pill) { reload++ }.glass(Pill).padding(horizontal = 20.dp, vertical = 10.dp)) { Text("Erneut versuchen", style = G.callout) }
                } else CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                return@Column
            }
            Text(WeatherData.deg(n.temp), style = TextStyle(fontSize = 96.sp, color = Color.White, fontWeight = FontWeight.Thin))
            Text(WeatherData.text(n.code), style = TextStyle(fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Medium))
            n.days.firstOrNull()?.let { d ->
                Text("H: ${WeatherData.deg(d.max)}   T: ${WeatherData.deg(d.min)}", style = TextStyle(fontSize = 17.sp, color = Color.White.copy(alpha = 0.9f)))
            }
            if (error) Text("Offline – letzte Daten", style = G.footnote.copy(color = Color.White.copy(alpha = 0.7f)))
            Spacer(Modifier.height(24.dp))

            // Stündlich
            Column(Modifier.fillMaxWidth().glass(sq(22), Color.White.copy(alpha = 0.14f)).padding(vertical = 14.dp)) {
                Text("STÜNDLICHE VORHERSAGE", style = G.caption, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                    n.hours.forEachIndexed { i, h ->
                        Column(Modifier.width(58.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (i == 0) "Jetzt" else h.time.take(2), style = TextStyle(fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium))
                            Spacer(Modifier.height(8.dp))
                            Icon(wxIcon(h.code, h.day), null, tint = Color.White, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(WeatherData.deg(h.temp), style = TextStyle(fontSize = 17.sp, color = Color.White, fontWeight = FontWeight.Medium))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // 7 Tage
            val gMin = n.days.minOfOrNull { it.min } ?: 0.0
            val gMax = n.days.maxOfOrNull { it.max } ?: 1.0
            Column(Modifier.fillMaxWidth().glass(sq(22), Color.White.copy(alpha = 0.14f)).padding(16.dp)) {
                Text("7-TAGE-VORHERSAGE", style = G.caption)
                n.days.forEachIndexed { i, d ->
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(Color.White.copy(alpha = 0.15f)))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val name = if (i == 0) "Heute" else try { weekday.format(isoDay.parse(d.date)!!).removeSuffix(".") } catch (e: Exception) { d.date }
                        Text(name, style = TextStyle(fontSize = 17.sp, color = Color.White, fontWeight = FontWeight.Medium), modifier = Modifier.width(62.dp))
                        Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(wxIcon(d.code, true), null, tint = Color.White, modifier = Modifier.size(22.dp))
                            if (d.rain >= 20) Text("${d.rain} %", style = TextStyle(fontSize = 11.sp, color = Color(0xFF9FD3FF), fontWeight = FontWeight.SemiBold))
                        }
                        Text(WeatherData.deg(d.min), style = TextStyle(fontSize = 17.sp, color = Color.White.copy(alpha = 0.65f)), textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
                        // Temperaturbalken
                        Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(5.dp).clip(Pill).background(Color.White.copy(alpha = 0.2f))) {
                            val span = (gMax - gMin).coerceAtLeast(1.0)
                            val a = ((d.min - gMin) / span).toFloat().coerceIn(0f, 0.95f)
                            val b = ((d.max - gMin) / span).toFloat().coerceIn(a + 0.05f, 1f)
                            Row(Modifier.fillMaxWidth()) {
                                if (a > 0f) Spacer(Modifier.weight(a))
                                Box(Modifier.weight(b - a).height(5.dp).clip(Pill).background(Brush.horizontalGradient(listOf(Color(0xFF8FD3FF), Color(0xFFFFC56B)))))
                                if (b < 1f) Spacer(Modifier.weight(1f - b))
                            }
                        }
                        Text(WeatherData.deg(d.max), style = TextStyle(fontSize = 17.sp, color = Color.White), modifier = Modifier.width(40.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Details
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Detail(Ic.sun, "GEFÜHLT", WeatherData.deg(n.feels), Modifier.weight(1f))
                Detail(Ic.drop, "FEUCHTIGKEIT", "${n.humidity} %", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Detail(Ic.wind, "WIND", "${Math.round(n.wind)} km/h", Modifier.weight(1f))
                Detail(Ic.moon, "SONNE", "${n.sunrise.take(5)} – ${n.sunset.take(5)}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Text("Daten: Open-Meteo.com", style = G.footnote.copy(color = Color.White.copy(alpha = 0.6f)))
        }
    }
}

@Composable
private fun Detail(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    Column(modifier.glass(sq(22), Color.White.copy(alpha = 0.14f)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = G.caption)
        }
        Spacer(Modifier.height(8.dp))
        Text(value, style = TextStyle(fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Stadt wählen: Suche (privat) oder optional per Standort (weniger privat). */
@Composable
private fun CityChooser(canCancel: Boolean, onCancel: () -> Unit, onPick: (WeatherData.City) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WeatherData.City>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    if (canCancel) BackHandler { onCancel() }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        focus.clearFocus()
        if (q.length in 4..12 && q.all { it.isDigit() }) {
            // Geheimer Zugang – sieht aus wie eine Suche ohne Treffer
            scope.launch {
                if (App.tryUnlock(q, silent = true)) query = "" else { results = emptyList(); info = "Keine Orte gefunden" }
            }
            return
        }
        busy = true; info = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { try { WeatherData.search(q) } catch (e: Exception) { null } }
            busy = false
            if (r == null) info = "Keine Verbindung" else { results = r; if (r.isEmpty()) info = "Keine Orte gefunden" }
        }
    }

    fun locate() {
        busy = true; info = null
        scope.launch {
            val loc = withTimeoutOrNull(20_000) { currentLocation(ctx) }
            busy = false
            if (loc == null) { info = "Standort nicht verfügbar. Ist der Standort am Handy eingeschaltet?"; return@launch }
            onPick(WeatherData.City("Mein Standort", "", loc.first, loc.second, gps = true))
        }
    }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        App.suppressLock = false
        if (ok) locate() else info = "Ohne Standort-Erlaubnis: Stadt einfach oben suchen."
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1D3B66), Color(0xFF2F6CB3))))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Wetter", style = G.largeTitle, modifier = Modifier.weight(1f))
                if (canCancel) Box(Modifier.pressable(Pill) { onCancel() }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text("Abbrechen", style = G.body.copy(fontSize = 16.sp))
                }
            }
            Text("Wähle deine Stadt", style = G.footnote)
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().height(48.dp).glass(Pill, Color.White.copy(alpha = 0.16f)).padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Ic.search, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Stadt suchen", style = G.body.copy(color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp))
                    BasicTextField(
                        value = query, onValueChange = { query = it.take(60) }, singleLine = true,
                        textStyle = G.body.copy(fontSize = 16.sp), cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search() }, onDone = { search() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp).padding(end = 4.dp))
                else Box(
                    Modifier.height(36.dp).pressable(Pill, highlight = 0f) { search() }.clip(Pill).background(Color.White.copy(alpha = 0.22f)).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Suchen", style = G.callout.copy(fontWeight = FontWeight.SemiBold)) }
            }
            Spacer(Modifier.height(10.dp))
            // Optional: Standort – ausdrücklich als weniger privat gekennzeichnet
            Row(
                Modifier.fillMaxWidth().pressable(sq(18)) {
                    val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (granted) locate() else { App.suppressLock = true; perm.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
                }.glass(sq(18), Color.White.copy(alpha = 0.10f)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Ic.location, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Mit GPS orten", style = G.headline.copy(fontSize = 15.sp))
                    Text("Weniger privat: braucht die Standort-Erlaubnis. Verwendet wird nur der ungefähre Ort für das Wetter.", style = G.footnote)
                }
            }
            info?.let { Spacer(Modifier.height(10.dp)); Text(it, style = G.footnote.copy(color = Color.White.copy(alpha = 0.85f))) }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                results.forEach { c ->
                    Column(Modifier.fillMaxWidth().pressable(sq(18)) { onPick(c) }.glass(sq(18), Color.White.copy(alpha = 0.12f)).padding(14.dp)) {
                        Text(c.name, style = G.headline.copy(fontSize = 16.sp))
                        if (c.region.isNotBlank()) Text(c.region, style = G.footnote)
                    }
                }
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

/** Ungefähren Standort einmalig holen (kein dauerhaftes Orten). */
@android.annotation.SuppressLint("MissingPermission")
private suspend fun currentLocation(ctx: android.content.Context): Pair<Double, Double>? {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
    val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
    val provider = listOf(LocationManager.NETWORK_PROVIDER, "fused", LocationManager.GPS_PROVIDER)
        .firstOrNull { try { lm.isProviderEnabled(it) } catch (e: Exception) { false } } ?: return null
    val fresh = try {
        suspendCancellableCoroutine<android.location.Location?> { cont ->
            val cancel = android.os.CancellationSignal()
            cont.invokeOnCancellation { cancel.cancel() }
            lm.getCurrentLocation(provider, cancel, ContextCompat.getMainExecutor(ctx)) { loc -> if (cont.isActive) cont.resume(loc) }
        }
    } catch (e: Exception) {
        null
    }
    val loc = fresh ?: try { lm.getLastKnownLocation(provider) } catch (e: Exception) { null } ?: return null
    return loc.latitude to loc.longitude
}
