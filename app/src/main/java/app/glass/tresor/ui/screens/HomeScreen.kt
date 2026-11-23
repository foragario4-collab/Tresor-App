package app.glass.tresor.ui.screens

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import app.glass.tresor.ui.TresorTextField
import androidx.compose.foundation.border
import app.glass.tresor.ui.Sheet
import app.glass.tresor.ui.SheetHeader
import app.glass.tresor.ui.PillButton
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import app.glass.tresor.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glass.tresor.App
import app.glass.tresor.Screen
import app.glass.tresor.ui.AppTile
import app.glass.tresor.ui.BusyBanner
import app.glass.tresor.ui.G
import app.glass.tresor.ui.GlassBackground
import app.glass.tresor.ui.GlassIconButton
import app.glass.tresor.ui.Haptic
import app.glass.tresor.ui.Ic
import app.glass.tresor.ui.Pill
import app.glass.tresor.ui.Thumb
import app.glass.tresor.ui.glass
import app.glass.tresor.ui.pressable
import app.glass.tresor.ui.rememberImport
import app.glass.tresor.ui.sq
import app.glass.tresor.vault.FolderKind
import app.glass.tresor.vault.Item
import app.glass.tresor.vault.ItemType
import app.glass.tresor.vault.Vault

fun openItem(fid: String, item: Item, filter: ItemType?) {
    when (item.type) {
        ItemType.NOTE -> App.nav.push(Screen.NoteEdit(fid, item.id))
        ItemType.IMAGE, ItemType.VIDEO -> App.nav.push(Screen.Viewer(fid, item.id, filter))
        ItemType.FILE -> App.nav.push(Screen.TextView(fid, item.id))
        ItemType.AUDIO -> {
            val v = App.vault ?: return
            // Ganzen Ordner als Wiedergabeliste, beginnend beim angetippten Lied
            val list = v.itemsOf(fid).filter { it.type == ItemType.AUDIO }.map { fid to it }
            app.glass.tresor.music.MusicPlayer.play(v, list, list.indexOfFirst { it.second.id == item.id }.coerceAtLeast(0))
            App.nav.push(Screen.Player)
        }
    }
}

// ───────────────────────── Startbildschirm: das „zweite Handy“ ─────────────────────────

val WALL = Color(0xFF0B1030)
val WEEKDAYS = listOf("Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag")
private val WD_SHORT = listOf("SO", "MO", "DI", "MI", "DO", "FR", "SA")
val MONTHS = listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")
private val ICON_SHAPE = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)

@Composable
fun HomeScreen() {
    val vault = App.vault ?: return
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var control by remember { mutableStateOf(false) }

    val visible = vault.folders.filter { !it.hidden && it.id in vault.unlockedFolders }
    val defaultMedia = visible.firstOrNull { it.kind == FolderKind.MEDIA && !it.isProtected }?.id
    var unlockCat by remember { mutableStateOf<Pair<FolderKind, Screen>?>(null) }
    // Kategorie öffnen: geschützter Container → erst entsperren
    fun openCat(kind: FolderKind, target: Screen) {
        if (vault.isCategoryOpen(kind)) App.nav.push(target) else unlockCat = kind to target
    }
    fun locked(kind: FolderKind) = !vault.isCategoryOpen(kind)
    fun catSettings(kind: FolderKind) = openCat(kind, Screen.CategorySettings(kind))
    val import = rememberImport(vault, { defaultMedia })
    fun camera(video: Boolean) {
        if (defaultMedia == null) { App.toast("Kein offener Foto-Ordner"); return }
        if (video) import.takeVideo() else import.takePhoto()
    }

    // Uhr läuft live mit
    val now by androidx.compose.runtime.produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(10_000) }
    }
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
    val engine = remember(vault) { try { app.glass.tresor.chat.ChatEngine.of(vault) } catch (e: Exception) { null } }
    val favs = engine?.contacts?.filter { it.accepted && !it.removedByPeer }?.sortedByDescending { it.fav }?.take(2) ?: emptyList()

    Box(Modifier.fillMaxSize().background(WALL)) {
        Wallpaper()
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)
        ) {
            // Oben rechts: Kontrollzentrum (auch: hier nach unten wischen) und sofort sperren
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total > 40.dp.toPx()) control = true },
                    ) { change, dy -> total += dy; change.consume() }
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))
                GlassIconButton(Ic.sliders, "Kontrollzentrum") { control = true }
                Spacer(Modifier.width(10.dp))
                GlassIconButton(Ic.lock, "Jetzt sperren") { App.lock() }
            }
            if (searching) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SearchField(query) { query = it } }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Fertig", style = G.body.copy(fontWeight = FontWeight.SemiBold, color = Color.White),
                        modifier = Modifier.pressable(Pill) { searching = false; query = "" }.padding(horizontal = 6.dp, vertical = 10.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if (query.isNotBlank()) SearchResults(vault, query)
                    else Text("Fotos, Dateien, Notizen und Musik durchsuchen", style = G.callout.copy(color = G.dim), modifier = Modifier.padding(top = 20.dp).fillMaxWidth())
                }
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
                    // Widgets
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ClockWidget(cal, Modifier.weight(1f))
                        FavWidget(favs, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(20.dp))
                    TipCard("home2", "Dein zweites Handy", "Symbol lange drücken = eigener Schlüssel für die Kategorie. Das Schloss oben rechts sperrt sofort. Alles weitere: Einstellungen → Handbuch.")
                    Spacer(Modifier.height(6.dp))
                    // App-Raster (4 Spalten)
                    val apps: List<@Composable (Modifier) -> Unit> = listOf(
                        { m -> AppIcon(R.drawable.ico_fotos, "Fotos", m, locked(FolderKind.MEDIA), onLong = { catSettings(FolderKind.MEDIA) }) { openCat(FolderKind.MEDIA, Screen.Albums(FolderKind.MEDIA, ItemType.IMAGE)) } },
                        { m -> AppIcon(R.drawable.ico_videos, "Videos", m, locked(FolderKind.MEDIA), onLong = { catSettings(FolderKind.MEDIA) }) { openCat(FolderKind.MEDIA, Screen.Albums(FolderKind.MEDIA, ItemType.VIDEO)) } },
                        { m -> AppIcon(R.drawable.ico_dateien, "Dateien", m, locked(FolderKind.FILES), onLong = { catSettings(FolderKind.FILES) }) { openCat(FolderKind.FILES, Screen.Albums(FolderKind.FILES, null)) } },
                        { m -> AppIcon(R.drawable.ico_notizen, "Notizen", m, locked(FolderKind.NOTES), onLong = { catSettings(FolderKind.NOTES) }) { openCat(FolderKind.NOTES, Screen.Albums(FolderKind.NOTES, null)) } },
                        { m -> AppIcon(R.drawable.ico_musik, "Musik", m, locked(FolderKind.MUSIC), onLong = { catSettings(FolderKind.MUSIC) }) { openCat(FolderKind.MUSIC, Screen.Albums(FolderKind.MUSIC, null)) } },
                        { m -> AppIcon(R.drawable.ico_kamera, "Kamera", m, onLong = { camera(true) }) { camera(false) } },
                        { m -> AppIcon(R.drawable.ico_kalender, "Kalender", m, overlay = { CalendarFace(cal) }) { App.nav.push(Screen.Calendar) } },
                        { m -> AppIcon(R.drawable.ico_passwoerter, "Passwörter", m) { App.nav.push(Screen.Passwords) } },
                        { m -> AppIcon(R.drawable.ico_browser, "Browser", m) { App.nav.push(Screen.Browser) } },
                        { m -> AppIcon(R.drawable.ico_kontakte, "Kontakte", m) { App.nav.push(Screen.Contacts) } },
                        { m -> AppIcon(R.drawable.ico_sprachmemo, "Sprachmemo", m) { App.nav.push(Screen.Memos) } },
                        { m -> AppIcon(R.drawable.ico_einstellungen, "Einstellungen", m) { App.nav.push(Screen.Settings) } },
                    )
                    apps.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            row.forEach { cell -> cell(Modifier.weight(1f)) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                // Suchen
                Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.pressable(Pill) { searching = true }.glass(Pill).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Ic.search, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Suchen", style = G.footnote.copy(color = Color.White, fontWeight = FontWeight.Medium))
                    }
                }
                // Dock
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp).glass(androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DockIcon(R.drawable.ico_telefon, "Telefon") { App.nav.push(Screen.Phone) }
                    DockIcon(R.drawable.ico_nachrichten, "Nachrichten") { App.nav.push(Screen.Chat) }
                    DockIcon(R.drawable.ico_kamera, "Kamera", onLong = { camera(true) }) { camera(false) }
                    DockIcon(R.drawable.ico_browser, "Browser") { App.nav.push(Screen.Browser) }
                }
            }
        }

        BusyBanner(import.busy(), Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp))

        CategoryUnlockSheet(vault, unlockCat?.first, { unlockCat = null }) {
            val t = unlockCat?.second
            unlockCat = null
            if (t != null) App.nav.push(t)
        }

        ControlCenter(
            visible = control,
            onClose = { control = false },
            onCamera = { control = false; camera(false) },
            onMusic = {
                control = false
                if (app.glass.tresor.music.MusicPlayer.active) App.nav.push(Screen.Player)
                else openCat(FolderKind.MUSIC, Screen.Albums(FolderKind.MUSIC, null))
            },
        )

    }
}

/** Hintergrund mit weichen Farbflecken (wie im Entwurf „Nacht“). */
@Composable
fun Wallpaper() {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        fun blob(c: Color, cx: Float, cy: Float, r: Float, a: Float) = drawCircle(
            Brush.radialGradient(listOf(c.copy(alpha = a), c.copy(alpha = a * 0.45f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(cx, cy), radius = r),
            radius = r, center = androidx.compose.ui.geometry.Offset(cx, cy)
        )
        blob(Color(0xFF3B4CFF), w * 0.18f, h * 0.15f, w * 0.85f, 0.85f)
        blob(Color(0xFFB04CFF), w * 0.92f, h * 0.62f, w * 0.80f, 0.75f)
        blob(Color(0xFF00B3A4), w * 0.48f, h * 0.98f, w * 0.65f, 0.65f)
    }
}

@Composable
private fun ClockWidget(cal: java.util.Calendar, modifier: Modifier) {
    Column(
        modifier.height(140.dp).glass(sq(26)).padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(WEEKDAYS[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1], style = G.footnote.copy(color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.SemiBold))
        Text(
            "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE)),
            style = G.body.copy(fontSize = 44.sp, fontWeight = FontWeight.Light, color = Color.White, letterSpacing = (-1).sp),
            maxLines = 1
        )
        Text("${cal.get(java.util.Calendar.DAY_OF_MONTH)}. ${MONTHS[cal.get(java.util.Calendar.MONTH)]}", style = G.footnote.copy(color = Color.White.copy(alpha = 0.85f)), maxLines = 1)
    }
}

@Composable
private fun FavWidget(favs: List<app.glass.tresor.chat.Contact>, modifier: Modifier) {
    Column(
        modifier.height(140.dp).pressable(sq(26)) { App.nav.push(Screen.Chat) }.glass(sq(26)).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.phone, null, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Favoriten", style = G.footnote.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
        }
        if (favs.isEmpty()) {
            Text("Noch keine Kontakte", style = G.footnote.copy(color = Color.White.copy(alpha = 0.8f)))
        } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            favs.forEach { c ->
                Box(
                    Modifier.size(52.dp).pressable(CircleShape, scaleTo = 0.9f) { App.nav.push(Screen.ChatThread(c.id)) }
                        .clip(CircleShape).background(avatarColor(c.name)),
                    contentAlignment = Alignment.Center
                ) { Text(c.name.take(1).uppercase(), style = G.headline.copy(fontSize = 20.sp, color = Color.White)) }
            }
        }
        Text("Anruf über Tor", style = G.footnote.copy(fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f)))
    }
}

private fun avatarColor(name: String): Color {
    val colors = listOf(Color(0xFFFF7A59), Color(0xFF3FBF8F), Color(0xFFA77BFF), Color(0xFF5B9CFF), Color(0xFFE0A21B), Color(0xFFFF5E8A))
    return colors[(name.hashCode() and 0x7fffffff) % colors.size]
}

/** Live-Datum auf dem Kalender-Symbol (wie beim iPhone). */
@Composable
private fun BoxScope.CalendarFace(cal: java.util.Calendar) {
    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(WD_SHORT[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1], style = G.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF3B30), letterSpacing = 0.8.sp))
        Text(
            "${cal.get(java.util.Calendar.DAY_OF_MONTH)}",
            style = G.body.copy(fontSize = 30.sp, fontWeight = FontWeight.Light, color = Color(0xFF1C1F26), lineHeight = 32.sp)
        )
    }
}

/** App-Symbol mit Beschriftung. Gesperrte Kategorien zeigen ein kleines Schloss. */
@Composable
private fun AppIcon(
    res: Int, label: String, modifier: Modifier, locked: Boolean = false,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    onLong: (() -> Unit)? = null, onClick: () -> Unit
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        val small = maxWidth < 84.dp
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconTile(res, label, locked, overlay, onLong, onClick)
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = G.caption.copy(fontSize = if (small) 10.5.sp else 12.sp, fontWeight = FontWeight.Medium, color = Color.White, letterSpacing = 0.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

@Composable
private fun DockIcon(res: Int, label: String, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    IconTile(res, label, false, null, onLong, onClick)
}

@Composable
private fun IconTile(
    res: Int, label: String, locked: Boolean,
    overlay: (@Composable BoxScope.() -> Unit)?,
    onLong: (() -> Unit)?, onClick: () -> Unit
) {
    Box(
        Modifier.size(62.dp)
            .pressable(ICON_SHAPE, scaleTo = 0.9f, highlight = 0.12f, onLongClick = onLong, onClick = onClick)
            .shadow(10.dp, ICON_SHAPE, clip = false, ambientColor = Color(0xFF050A28), spotColor = Color(0xFF050A28))
    ) {
        Image(
            painterResource(res), label,
            modifier = Modifier.fillMaxSize().clip(ICON_SHAPE),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )
        overlay?.invoke(this)
        if (locked) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp).size(22.dp)
                    .clip(CircleShape).background(Color(0xE60B1030))
                    .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Ic.lock, "Geschützt", tint = Color.White, modifier = Modifier.size(12.dp)) }
        }
    }
}

@Composable
private fun NavTab(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .size(width = 92.dp, height = 50.dp)
            .pressable(Pill, onClick = onClick)
            .then(if (active) Modifier.clip(Pill).background(Color.White.copy(alpha = 0.16f)) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = if (active) Color.White else G.dim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = G.caption.copy(color = if (active) Color.White else G.dim, fontSize = 11.sp, letterSpacing = 0.sp))
    }
}

@Composable
private fun CategoryCard(
    modifier: Modifier, icon: ImageVector, color: Color, title: String, count: Int,
    locked: Boolean = false, onLong: (() -> Unit)? = null, onClick: () -> Unit
) {
    Column(
        modifier
            .pressable(sq(24), onLongClick = onLong, onClick = onClick)
            .glass(sq(24))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            AppTile(icon, color)
            Spacer(Modifier.weight(1f))
            if (locked) Icon(Ic.lock, "Geschützt", tint = G.dim, modifier = Modifier.size(16.dp))
        }
        Column {
            Text(title, style = G.headline)
            Text(if (locked) "Geschützt" else if (count == 1) "1 Element" else "$count Elemente", style = G.footnote)
        }
    }
}

@Composable
private fun ToolCard(modifier: Modifier, icon: ImageVector, color: Color, title: String, sub: String, onClick: () -> Unit) {
    Column(
        modifier
            .pressable(sq(24), onClick = onClick)
            .glass(sq(24))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AppTile(icon, color)
        Column {
            Text(title, style = G.headline)
            Text(sub, style = G.footnote)
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).glass(Pill).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Ic.search, null, tint = G.dim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Im Tresor suchen", style = G.body.copy(fontSize = 16.sp, color = G.faint))
            TresorTextField(
                value = query, onValueChange = onChange, singleLine = true,
                textStyle = G.body.copy(fontSize = 16.sp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Box(Modifier.size(28.dp).pressable(CircleShape, haptic = Haptic.NONE) { onChange("") }, contentAlignment = Alignment.Center) {
                Icon(Ic.close, "Leeren", tint = G.dim, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SearchResults(vault: Vault, query: String) {
    val q = query.trim().lowercase()
    val results = vault.folders
        .filter { !it.hidden && it.id in vault.unlockedFolders }
        .flatMap { f -> vault.itemsOf(f.id).filter { it.name.lowercase().contains(q) }.map { f.id to it } }
        .take(60)
    if (results.isEmpty()) {
        Text("Keine Treffer", style = G.callout.copy(color = G.dim), modifier = Modifier.padding(top = 20.dp).fillMaxWidth())
        return
    }
    Column(Modifier.glass(sq(24)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        results.forEachIndexed { i, (fid, item) ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(G.divider))
            Row(
                Modifier.fillMaxWidth().pressable(sq(14)) { openItem(fid, item, null) }.padding(vertical = 8.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Thumb(vault, fid, item, Modifier.size(44.dp).clip(sq(10)), showBadge = false)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name.ifBlank { "Ohne Titel" }, style = G.body.copy(fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(vault.folder(fid)?.name ?: "", style = G.footnote)
                }
            }
        }
    }
}
