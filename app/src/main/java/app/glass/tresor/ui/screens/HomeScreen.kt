package app.glass.tresor.ui.screens

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
    }
}

@Composable
fun HomeScreen() {
    val vault = App.vault ?: return
    var query by remember { mutableStateOf("") }

    val visible = vault.folders.filter { !it.hidden && it.id in vault.unlockedFolders }
    fun count(kind: FolderKind, type: ItemType?) = visible.filter { it.kind == kind }
        .sumOf { f -> vault.itemsOf(f.id).count { type == null || it.type == type } }

    val defaultMedia = visible.firstOrNull { it.kind == FolderKind.MEDIA && !it.isProtected }?.id
    val import = rememberImport(vault, { defaultMedia })

    GlassBackground {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ic.lock, null, tint = G.dim, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("AES-256 verschlüsselt", style = G.footnote.copy(fontWeight = FontWeight.Medium))
                    }
                    Text("Tresor", style = G.largeTitle)
                }
                GlassIconButton(Ic.settings, "Einstellungen") { App.nav.push(Screen.Settings) }
            }

            SearchField(query) { query = it }

            if (query.isNotBlank()) {
                SearchResults(vault, query)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CategoryCard(Modifier.weight(1f), Ic.photo, G.blue, "Fotos", count(FolderKind.MEDIA, ItemType.IMAGE)) {
                            App.nav.push(Screen.Albums(FolderKind.MEDIA, ItemType.IMAGE))
                        }
                        CategoryCard(Modifier.weight(1f), Ic.video, G.purple, "Videos", count(FolderKind.MEDIA, ItemType.VIDEO)) {
                            App.nav.push(Screen.Albums(FolderKind.MEDIA, ItemType.VIDEO))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CategoryCard(Modifier.weight(1f), Ic.folder, G.amber, "Dateien", count(FolderKind.FILES, null)) {
                            App.nav.push(Screen.Albums(FolderKind.FILES, null))
                        }
                        CategoryCard(Modifier.weight(1f), Ic.note, G.teal, "Notizen", count(FolderKind.NOTES, null)) {
                            App.nav.push(Screen.Albums(FolderKind.NOTES, null))
                        }
                    }
                }

                val recent = vault.recentMedia()
                if (recent.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Zuletzt hinzugefügt", style = G.title3)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(end = 4.dp)
                        ) {
                            items(recent, key = { it.first + it.second.id }) { (fid, item) ->
                                Box(
                                    Modifier
                                        .size(78.dp)
                                        .pressable(sq(18)) { openItem(fid, item, null) }
                                ) {
                                    Thumb(vault, fid, item, Modifier.fillMaxSize().clip(sq(18)))
                                }
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().glass(sq(22)).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Ic.shield, null, tint = G.mint, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Geschützt", style = G.headline.copy(fontSize = 15.sp))
                        Text(
                            buildString {
                                append("Sperre beim Verlassen")
                                if (App.secure) append(" · Screenshots blockiert")
                            },
                            style = G.footnote
                        )
                    }
                }
            }
        }

        BusyBanner(import.busy(), Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp))

        // Untere Leiste
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 18.dp)
                .fillMaxWidth()
                .height(66.dp)
                .glass(Pill, G.fill)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            NavTab(Ic.grid, "Tresor", active = true) {}
            Box(
                Modifier
                    .size(54.dp)
                    .pressable(CircleShape, scaleTo = 0.9f, highlight = 0f, haptic = Haptic.CONFIRM, onLongClick = {
                        if (defaultMedia != null) import.takeVideo()
                    }) {
                        if (defaultMedia != null) import.takePhoto() else App.toast("Kein offener Foto-Ordner")
                    }
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(Ic.camera, "Kamera (lange drücken für Video)", tint = Color(0xFF0B0D12), modifier = Modifier.size(24.dp))
            }
            NavTab(Ic.lock, "Sperren", active = false) { App.lock() }
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
private fun CategoryCard(modifier: Modifier, icon: ImageVector, color: Color, title: String, count: Int, onClick: () -> Unit) {
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
            Text(if (count == 1) "1 Element" else "$count Elemente", style = G.footnote)
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
            BasicTextField(
                value = query, onValueChange = onChange, singleLine = true,
                textStyle = G.body.copy(fontSize = 16.sp), cursorBrush = SolidColor(G.accentText),
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
