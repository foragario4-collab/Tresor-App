package app.glass.tresor.disguise

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glass.tresor.App
import app.glass.tresor.ui.Blob
import app.glass.tresor.ui.G
import app.glass.tresor.ui.GlassBackground
import app.glass.tresor.ui.GlassField
import app.glass.tresor.ui.GlassIconButton
import app.glass.tresor.ui.Ic
import app.glass.tresor.ui.PillButton
import app.glass.tresor.ui.Sheet
import app.glass.tresor.ui.SheetHeader
import app.glass.tresor.ui.TopBar
import app.glass.tresor.ui.glass
import app.glass.tresor.ui.pressable
import app.glass.tresor.ui.sq
import androidx.compose.animation.core.animateFloatAsState
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val calBlobs = listOf(
    Blob(Color(0xFFE5484D), 1.1f, 0.0f, 0.9f, 0.32f),
    Blob(Color(0xFF6D4CFF), -0.1f, 0.85f, 0.9f, 0.32f),
)

private data class Event(val date: String, val title: String, val time: String)

private fun loadEvents(): List<Event> = try {
    val arr = JSONArray(App.dprefs.getString("events", "[]"))
    (0 until arr.length()).map {
        val o = arr.getJSONObject(it)
        Event(o.getString("d"), o.getString("t"), o.optString("h"))
    }
} catch (e: Exception) {
    emptyList()
}

private fun saveEvents(list: List<Event>) {
    val arr = JSONArray()
    list.forEach { arr.put(JSONObject().put("d", it.date).put("t", it.title).put("h", it.time)) }
    App.dprefs.edit().putString("events", arr.toString()).apply()
}

/** Funktionierender Kalender. Geheim: das heutige Datum lange drücken. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarApp() {
    val today = remember { LocalDate.now() }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var dir by remember { mutableIntStateOf(1) }
    var selected by remember { mutableStateOf(today) }
    var events by remember { mutableStateOf(loadEvents()) }
    var adding by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    val view = LocalView.current
    val locale = Locale.GERMANY

    GlassBackground(calBlobs) {
        Column(Modifier.fillMaxSize()) {
            TopBar(onBack = null) {
                GlassIconButton(Ic.back, "Voriger Monat") { dir = -1; month = month.minusMonths(1) }
                GlassIconButton(Ic.forward, "Nächster Monat") { dir = 1; month = month.plusMonths(1) }
                GlassIconButton(Ic.plus, "Termin hinzufügen") { title = ""; time = ""; adding = true }
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 14.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(month.month.getDisplayName(TextStyle.FULL, locale), style = G.largeTitle)
                    Spacer(Modifier.width(10.dp))
                    Text("${month.year}", style = G.largeTitle.copy(fontWeight = FontWeight.Light, color = Color.White.copy(alpha = 0.65f)))
                }
                AnimatedContent(
                    targetState = month,
                    transitionSpec = {
                        (slideInHorizontally(spring(dampingRatio = 1f, stiffness = 500f)) { it * dir / 3 } + fadeIn(tween(200)))
                            .togetherWith(slideOutHorizontally { -it * dir / 3 } + fadeOut(tween(150)))
                    },
                    label = "month"
                ) { m ->
                    Column(Modifier.fillMaxWidth().glass(sq(28)).padding(horizontal = 10.dp, vertical = 14.dp)) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach {
                                Text(it, style = G.caption, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                        val first = m.atDay(1)
                        val offset = first.dayOfWeek.value - 1
                        val days = m.lengthOfMonth()
                        val cells = (0 until 42).map { i -> (i - offset + 1).takeIf { it in 1..days } }
                        val rows = cells.chunked(7).filter { r -> r.any { it != null } }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            rows.forEach { r ->
                                Row(Modifier.fillMaxWidth()) {
                                    r.forEach { d ->
                                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                            if (d != null) {
                                                val date = m.atDay(d)
                                                DayCell(
                                                    day = d,
                                                    isToday = date == today,
                                                    isSelected = date == selected,
                                                    hasEvent = events.any { it.date == date.toString() },
                                                    onClick = { selected = date },
                                                    onLong = {
                                                        if (date == today) {
                                                            openSecretLock()
                                                        } else selected = date
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val dayName = selected.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                Text("$dayName, ${selected.dayOfMonth}. ${selected.month.getDisplayName(TextStyle.FULL, locale)}", style = G.headline.copy(fontSize = 15.sp, color = Color.White.copy(alpha = 0.85f)))
                val todays = events.filter { it.date == selected.toString() }.sortedBy { it.time }
                if (todays.isEmpty()) {
                    Text(
                        "Keine Termine", style = G.callout.copy(color = G.dim),
                        modifier = Modifier.fillMaxWidth().glass(sq(22)).padding(18.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                } else {
                    Column(Modifier.fillMaxWidth().glass(sq(22)).padding(horizontal = 16.dp, vertical = 4.dp)) {
                        todays.forEachIndexed { i, e ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(G.divider))
                            Row(
                                Modifier.fillMaxWidth().pressable(sq(12), onLongClick = {
                                    events = events - e; saveEvents(events)
                                }) {}.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.width(4.dp).height(36.dp).clip(CircleShape).background(if (i % 2 == 0) Color(0xFFE5484D) else G.accent))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(e.title, style = G.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium))
                                    Text(e.time.ifBlank { "Ganztägig" }, style = G.footnote)
                                }
                            }
                        }
                    }
                    Text("Termin lange drücken zum Löschen", style = G.footnote.copy(color = G.faint), modifier = Modifier.padding(start = 4.dp))
                }
            }
        }

        Sheet(adding, { adding = false }) {
            SheetHeader(Ic.calendar, "Neuer Termin", "${selected.dayOfMonth}. ${selected.month.getDisplayName(TextStyle.FULL, locale)} ${selected.year}")
            GlassField(title, { title = it }, "Titel", autoFocus = adding)
            Spacer(Modifier.height(10.dp))
            GlassField(time, { time = it }, "Uhrzeit (z. B. 18:00)")
            Spacer(Modifier.height(14.dp))
            PillButton("Hinzufügen", primary = true, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                events = events + Event(selected.toString(), title.trim(), time.trim())
                saveEvents(events)
                adding = false
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCell(day: Int, isToday: Boolean, isSelected: Boolean, hasEvent: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.86f else 1f,
        if (pressed) spring(dampingRatio = 1f, stiffness = 1600f) else spring(dampingRatio = 0.45f, stiffness = 420f),
        label = "day"
    )
    val bg by animateColorAsState(
        when {
            isSelected && isToday -> Color(0xFFE5484D)
            isSelected -> Color.White
            else -> Color.Transparent
        }, tween(200), label = "daybg"
    )
    val fg = when {
        isSelected && isToday -> Color.White
        isSelected -> Color(0xFF0B0D12)
        isToday -> Color(0xFFFF6B70)
        else -> Color.White
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(44.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(bg)
                .combinedClickable(interactionSource = src, indication = null, role = Role.Button, onLongClick = onLong, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text("$day", style = G.body.copy(color = fg, fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal))
        }
        Box(Modifier.size(5.dp).clip(CircleShape).background(if (hasEvent) Color.White.copy(alpha = 0.6f) else Color.Transparent))
    }
}

