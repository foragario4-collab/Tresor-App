package app.glass.tresor.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glass.tresor.App
import app.glass.tresor.Screen
import app.glass.tresor.ui.BusyBanner
import app.glass.tresor.ui.ConfirmSheet
import app.glass.tresor.ui.EmptyState
import app.glass.tresor.ui.G
import app.glass.tresor.ui.GlassBackground
import app.glass.tresor.ui.GlassIconButton
import app.glass.tresor.ui.Haptic
import app.glass.tresor.ui.Ic
import app.glass.tresor.ui.Pill
import app.glass.tresor.ui.PillButton
import app.glass.tresor.ui.Sheet
import app.glass.tresor.ui.SheetHeader
import app.glass.tresor.ui.Thumb
import app.glass.tresor.ui.TopBar
import app.glass.tresor.ui.dateFmt
import app.glass.tresor.ui.formatSize
import app.glass.tresor.ui.formatDuration
import app.glass.tresor.ui.glass
import app.glass.tresor.ui.pressable
import app.glass.tresor.ui.rememberExport
import app.glass.tresor.ui.rememberImport
import app.glass.tresor.ui.sq
import app.glass.tresor.vault.FolderKind
import app.glass.tresor.vault.Item
import app.glass.tresor.vault.ItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

@Composable
fun FolderScreen(fid: String, filter: ItemType?) {
    val vault = App.vault ?: return
    val folder = vault.folder(fid)
    val scope = rememberCoroutineScope()
    val items = folderItems(vault, fid, filter)
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var moveSheet by remember { mutableStateOf(false) }
    val import = rememberImport(vault, { fid })
    val export = rememberExport(vault)

    if (folder == null || fid !in vault.unlockedFolders) {
        GlassBackground {
            TopBar(onBack = { App.nav.pop() })
            EmptyState(Ic.lock, "Gesperrt", "Dieser Ordner ist wieder gesperrt.", Modifier.align(Alignment.Center))
        }
        return
    }

    fun toggle(id: String) {
        selected = if (id in selected) selected - id else selected + id
        if (selected.isEmpty()) selecting = false
    }

    fun tap(item: Item) {
        if (selecting) toggle(item.id) else openItem(fid, item, filter)
    }

    fun long(item: Item) {
        selecting = true
        toggle(item.id)
    }

    GlassBackground {
        val header: @Composable () -> Unit = {
            Column {
                TopBar(onBack = { App.nav.pop() }, horizontalPadding = 0.dp) {
                    if (items.isNotEmpty()) {
                        PillButton(if (selecting) "Fertig" else "Auswählen", height = 44.dp) {
                            selecting = !selecting
                            selected = emptySet()
                        }
                    }
                    GlassIconButton(Ic.dots, "Ordner-Einstellungen") { App.nav.push(Screen.FolderSettings(fid)) }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(folder.name, style = G.largeTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (folder.isProtected) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Ic.unlock, null, tint = G.dim, modifier = Modifier.size(18.dp))
                    }
                }
                Text(
                    if (selecting) "${selected.size} ausgewählt" else "${items.size} Elemente · verschlüsselt",
                    style = G.footnote
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        when (folder.kind) {
            FolderKind.MEDIA -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item(span = { GridItemSpan(3) }) { Box(Modifier.padding(horizontal = 20.dp)) { header() } }
                if (items.isEmpty()) item(span = { GridItemSpan(3) }) {
                    EmptyState(Ic.photo, "Noch leer", "Importiere Fotos und Videos oder nimm direkt welche auf.")
                }
                items(items, key = { it.id }) { item ->
                    MediaTile(vault, fid, item, selecting, item.id in selected, onLong = { long(item) }) { tap(item) }
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { header() }
                if (items.isEmpty()) item {
                    if (folder.kind == FolderKind.NOTES) EmptyState(Ic.note, "Keine Notizen", "Tippe unten auf „Neue Notiz“.")
                    else if (folder.kind == FolderKind.MUSIC) EmptyState(Ic.music, "Noch keine Musik", "Füge MP3-, M4A-, FLAC- oder andere Musikdateien hinzu.")
                    else EmptyState(Ic.folder, "Keine Dateien", "Füge Dokumente, PDFs oder beliebige Dateien hinzu.")
                }
                items(items, key = { it.id }) { item ->
                    Box(Modifier.animateItem()) {
                        ListRow(vault, fid, item, selecting, item.id in selected, onLong = { long(item) }) { tap(item) }
                    }
                }
            }
        }

        BusyBanner(import.busy(), Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp))

        // Untere Leiste: normal oder Auswahl-Aktionen
        AnimatedContent(
            targetState = selecting,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp),
            transitionSpec = {
                (fadeIn(tween(180)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { it / 2 })
                    .togetherWith(fadeOut(tween(120)) + slideOutVertically { it / 2 })
            },
            label = "bar"
        ) { sel ->
            Row(
                Modifier.height(60.dp).glass(Pill, G.fillStrong).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (sel) {
                    BarButton(Ic.export, "Export", enabled = selected.isNotEmpty()) {
                        export(items.filter { it.id in selected }.map { fid to it })
                    }
                    BarButton(Ic.move, "Verschieben", enabled = selected.isNotEmpty()) { moveSheet = true }
                    BarButton(Ic.trash, "Löschen", enabled = selected.isNotEmpty(), tint = G.red) { confirmDelete = true }
                } else when (folder.kind) {
                    FolderKind.MEDIA -> {
                        BarButton(Ic.plus, "Importieren") { import.pickMedia() }
                        RoundBarButton(Ic.camera, "Foto") { import.takePhoto() }
                        RoundBarButton(Ic.video, "Video") { import.takeVideo() }
                    }
                    FolderKind.FILES -> BarButton(Ic.plus, "Datei hinzufügen") { import.pickFiles() }
                    FolderKind.NOTES -> BarButton(Ic.edit, "Neue Notiz") { App.nav.push(Screen.NoteEdit(fid, null)) }
                    FolderKind.CHAT -> {}
                    FolderKind.MUSIC -> {
                        BarButton(Ic.plus, "Musik hinzufügen") { import.pickAudio() }
                        if (items.any { it.type == ItemType.AUDIO }) RoundBarButton(Ic.shuffle, "Zufällig abspielen") {
                            val list = items.filter { it.type == ItemType.AUDIO }.map { fid to it }
                            if (!app.glass.tresor.music.MusicPlayer.shuffle) app.glass.tresor.music.MusicPlayer.toggleShuffle()
                            app.glass.tresor.music.MusicPlayer.play(vault, list, list.indices.random())
                            App.nav.push(Screen.Player)
                        }
                    }
                }
            }
        }

        ConfirmSheet(
            visible = confirmDelete,
            title = if (selected.size == 1) "Element löschen?" else "${selected.size} Elemente löschen?",
            text = "Wird endgültig aus dem Tresor entfernt.",
            confirm = "Löschen",
            onDismiss = { confirmDelete = false }
        ) {
            val ids = selected
            confirmDelete = false
            selected = emptySet(); selecting = false
            scope.launch { withContext(Dispatchers.IO) { vault.deleteItems(fid, ids) } }
        }

        Sheet(moveSheet, { moveSheet = false }) {
            SheetHeader(Ic.move, "Verschieben nach", null)
            val targets = vault.folders.filter { it.kind == folder.kind && it.id != fid && it.id in vault.unlockedFolders }
            if (targets.isEmpty()) {
                Text(
                    "Kein anderer offener Ordner. Geschützte Ordner zuerst öffnen.",
                    style = G.callout.copy(color = G.dim), modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                targets.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().pressable(sq(18)) {
                            val ids = selected
                            moveSheet = false
                            selected = emptySet(); selecting = false
                            scope.launch {
                                withContext(Dispatchers.IO) { vault.moveItems(fid, t.id, ids) }
                                App.toast("Verschoben nach „${t.name}“")
                            }
                        }.glass(sq(18)).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(if (t.isProtected) Ic.lock else Ic.folder, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(t.name, style = G.body)
                    }
                }
            }
        }
    }
}

/** Im Videos-Bereich nur Videos zeigen, sonst alles. */
fun folderItems(vault: app.glass.tresor.vault.Vault, fid: String, filter: ItemType?): List<Item> {
    val all = vault.itemsOf(fid)
    return if (filter == ItemType.VIDEO) all.filter { it.type == ItemType.VIDEO } else all
}

@Composable
private fun MediaTile(
    vault: app.glass.tresor.vault.Vault, fid: String, item: Item,
    selecting: Boolean, selected: Boolean, onLong: () -> Unit, onClick: () -> Unit
) {
    val inset by animateFloatAsState(if (selected) 0.88f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "inset")
    Box(
        Modifier
            .aspectRatio(1f)
            .pressable(null, scaleTo = 0.95f, onLongClick = onLong, onClick = onClick)
    ) {
        Thumb(
            vault, fid, item,
            Modifier.fillMaxSize().graphicsLayer { scaleX = inset; scaleY = inset }
                .clip(if (selected) sq(10) else androidx.compose.ui.graphics.RectangleShape)
        )
        if (selecting) {
            val bg by animateColorAsState(if (selected) G.accent else Color.Black.copy(alpha = 0.25f), label = "selbg")
            Box(
                Modifier.align(Alignment.BottomEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(bg)
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected) Icon(Ic.check, null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }
    }
}

@Composable
private fun ListRow(
    vault: app.glass.tresor.vault.Vault, fid: String, item: Item,
    selecting: Boolean, selected: Boolean, onLong: () -> Unit, onClick: () -> Unit
) {
    val isNote = item.type == ItemType.NOTE
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(sq(20), onLongClick = onLong, onClick = onClick)
            .glass(sq(20), if (selected) G.accent.copy(alpha = 0.25f) else G.fill)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isNote) {
            Box(Modifier.size(44.dp).clip(sq(12)).background(G.teal), contentAlignment = Alignment.Center) {
                Icon(Ic.note, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        } else {
            Thumb(vault, fid, item, Modifier.size(44.dp).clip(sq(12)), showBadge = false)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name.ifBlank { "Ohne Titel" }, style = G.body.copy(fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    isNote -> dateFmt.format(Date(item.modified))
                    item.type == ItemType.AUDIO -> item.artist.ifBlank { "Unbekannter Interpret" } + " · " + formatDuration(item.durationMs)
                    else -> "${formatSize(item.size)} · ${dateFmt.format(Date(item.created))}"
                },
                style = G.footnote
            )
        }
        if (selecting) {
            Spacer(Modifier.width(8.dp))
            val bg by animateColorAsState(if (selected) G.accent else Color.Transparent, label = "selbg2")
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(bg).border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                contentAlignment = Alignment.Center
            ) { if (selected) Icon(Ic.check, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, enabled: Boolean = true, tint: Color = Color.White, onClick: () -> Unit) {
    Row(
        Modifier
            .height(46.dp)
            .pressable(Pill, enabled = enabled, haptic = Haptic.CONFIRM, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val c = if (enabled) tint else tint.copy(alpha = 0.4f)
        Icon(icon, null, tint = c, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = G.headline.copy(fontSize = 15.sp, color = c))
    }
}

@Composable
private fun RoundBarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .pressable(CircleShape, scaleTo = 0.9f, highlight = 0f, haptic = Haptic.CONFIRM, onClick = onClick)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = Color(0xFF0B0D12), modifier = Modifier.size(22.dp))
    }
}

