package app.glass.tresor.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import app.glass.tresor.App
import app.glass.tresor.Screen
import app.glass.tresor.ui.AppTile
import app.glass.tresor.ui.Auth
import app.glass.tresor.ui.G
import app.glass.tresor.ui.GlassBackground
import app.glass.tresor.ui.GlassField
import app.glass.tresor.ui.Ic
import app.glass.tresor.ui.PillButton
import app.glass.tresor.ui.Sheet
import app.glass.tresor.ui.SheetHeader
import app.glass.tresor.ui.Thumb
import app.glass.tresor.ui.TopBar
import app.glass.tresor.ui.glass
import app.glass.tresor.ui.placeholderFor
import app.glass.tresor.ui.pressable
import app.glass.tresor.ui.sq
import app.glass.tresor.vault.Folder
import app.glass.tresor.vault.FolderKind
import app.glass.tresor.vault.ItemType
import app.glass.tresor.vault.Protection
import app.glass.tresor.vault.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun kindTitle(kind: FolderKind, filter: ItemType?) = when {
    kind == FolderKind.MEDIA && filter == ItemType.VIDEO -> "Videos"
    kind == FolderKind.MEDIA -> "Fotos"
    kind == FolderKind.FILES -> "Dateien"
    kind == FolderKind.MUSIC -> "Musik"
    kind == FolderKind.CHAT -> "Chat"
    else -> "Notizen"
}

fun protectionLabel(p: Protection) = when (p) {
    Protection.NONE -> ""
    Protection.PIN -> "Tresor-PIN"
    Protection.PASSWORD -> "Eigenes Passwort"
    Protection.SYSTEM -> "Systemsperre"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumsScreen(kind: FolderKind, filter: ItemType?) {
    val vault = App.vault ?: return
    val view = LocalView.current
    var codeSheet by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var unlockTarget by remember { mutableStateOf<Pair<Folder, Boolean>?>(null) } // Ordner, danach Einstellungen?

    // Ältere Tresore haben noch keinen Musik-Ordner → beim ersten Öffnen anlegen
    androidx.compose.runtime.LaunchedEffect(kind) {
        if (kind == FolderKind.MUSIC && vault.folders.none { it.kind == FolderKind.MUSIC }) {
            try { vault.createFolder("Musik", FolderKind.MUSIC) } catch (_: Exception) {}
        }
    }
    val all = vault.folders.filter { it.kind == kind }
    val normal = all.filter { !it.isProtected && !it.hidden }
    // Ausgeblendete Ordner sind unsichtbar, bis ihr Code (oder der Fingerabdruck) sie hervorholt
    val protectedF = all.filter { if (it.hidden) it.id in vault.revealed else it.isProtected }

    fun open(f: Folder, settings: Boolean) {
        if (vault.isFolderUnlocked(f.id)) {
            App.nav.push(if (settings) Screen.FolderSettings(f.id) else Screen.FolderView(f.id, filter))
        } else unlockTarget = f to settings
    }

    GlassBackground {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { GridItemSpan(2) }) {
                Column {
                    TopBar(onBack = { App.nav.pop() }, horizontalPadding = 0.dp) {
                        PillButton("Neuer Ordner", icon = Ic.plus, height = 44.dp) { newFolder = true }
                    }
                    Spacer(Modifier.height(14.dp))
                    // Lange auf den Titel drücken: Code oder Fingerabdruck für versteckte Ordner
                    Text(
                        kindTitle(kind, filter),
                        style = G.largeTitle,
                        modifier = Modifier.combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                            onLongClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                codeSheet = true
                            }
                        )
                    )
                }
            }
            items(normal, key = { it.id }) { f -> FolderCard(vault, f, filter, onLong = { open(f, true) }) { open(f, false) } }

            if (protectedF.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Text("GESCHÜTZT", style = G.caption, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                }
                items(protectedF, key = { it.id }) { f ->
                    Box(Modifier.animateItem()) {
                        FolderCard(vault, f, filter, onLong = { open(f, true) }) { open(f, false) }
                    }
                }
            }
            if (all.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Text("Noch keine Ordner", style = G.callout.copy(color = G.dim))
                }
            }
            item(span = { GridItemSpan(2) }) {
                Text(
                    "Ordner lange drücken für Schutz und Einstellungen.",
                    style = G.footnote.copy(color = G.faint),
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
            }
        }

        NewFolderSheet(newFolder, kind, onDismiss = { newFolder = false })
        FolderCodeSheet(vault, kind, codeSheet, onDismiss = { codeSheet = false })
        FolderUnlockSheet(vault, unlockTarget?.first, onDismiss = { unlockTarget = null }) { f ->
            val toSettings = unlockTarget?.second == true
            unlockTarget = null
            App.nav.push(if (toSettings) Screen.FolderSettings(f.id) else Screen.FolderView(f.id, filter))
        }
    }
}

@Composable
private fun FolderCard(vault: Vault, f: Folder, filter: ItemType?, onLong: () -> Unit, onClick: () -> Unit) {
    val unlocked = f.id in vault.unlockedFolders
    val items = if (unlocked) folderItems(vault, f.id, filter) else emptyList()
    val cover = items.firstOrNull { it.type == ItemType.IMAGE || it.type == ItemType.VIDEO || (it.type == ItemType.AUDIO && it.hasThumb) }
    // Nicht die ganze Spalte abrunden – sonst schneidet die Ecke die Beschriftung darunter an.
    // Drück-Effekt: ganze Karte federt, das Aufhellen passiert nur im abgerundeten Bild.
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val hl by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.14f else 0f, label = "cardHl")
    Column(
        Modifier.pressable(null, highlight = 0f, interaction = src, onLongClick = onLong, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(sq(24))
                .background(placeholderFor(f.id).copy(alpha = if (f.isProtected) 0.55f else 1f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), sq(24))
                .drawWithContent {
                    drawContent()
                    if (hl > 0f) drawRect(Color.White.copy(alpha = hl))
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                f.isProtected && !unlocked -> Box(
                    Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) { Icon(if (f.hidden) Ic.eyeOff else Ic.lock, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                cover != null -> Thumb(vault, f.id, cover, Modifier.fillMaxSize(), showBadge = false)
                else -> AppTile(
                    when (f.kind) {
                        FolderKind.MEDIA -> if (filter == ItemType.VIDEO) Ic.video else Ic.photo
                        FolderKind.FILES -> Ic.folder
                        FolderKind.NOTES -> Ic.note
                        FolderKind.CHAT -> Ic.chat
                        FolderKind.MUSIC -> Ic.music
                    },
                    Color.White.copy(alpha = 0.16f), size = 56.dp, radius = 16, iconSize = 26.dp
                )
            }
            if (f.isProtected && unlocked) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Ic.unlock, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
            }
        }
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(f.name, style = G.headline.copy(fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = when {
                f.isProtected && !unlocked -> protectionLabel(f.protection) + if (f.hidden) " · ausgeblendet" else ""
                f.hidden && !f.isProtected -> "${items.size} · ausgeblendet"
                else -> "${items.size}"
            }
            Text(sub, style = G.footnote)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.NewFolderSheet(visible: Boolean, kind: FolderKind, onDismiss: () -> Unit) {
    var name by remember(visible) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Sheet(visible, onDismiss) {
        SheetHeader(Ic.folder, "Neuer Ordner", null)
        GlassField(name, { name = it }, "Name", autoFocus = visible)
        Spacer(Modifier.height(14.dp))
        PillButton("Erstellen", primary = true, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            val v = App.vault ?: return@PillButton
            val n = name.trim()
            scope.launch {
                withContext(Dispatchers.IO) { v.createFolder(n, kind) }
                onDismiss()
            }
        }
    }
}

/** Entsperren eines geschützten Ordners: Passwort, Tresor-PIN oder Systemsperre. */
@Composable
fun androidx.compose.foundation.layout.BoxScope.FolderUnlockSheet(
    vault: Vault,
    folder: Folder?,
    onDismiss: () -> Unit,
    onUnlocked: (Folder) -> Unit
) {
    val activity = LocalContext.current as FragmentActivity
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var secret by remember(folder?.id) { mutableStateOf("") }
    var busy by remember(folder?.id) { mutableStateOf(false) }
    var error by remember(folder?.id) { mutableStateOf<String?>(null) }
    val f = folder

    fun trySecret() {
        if (f == null || secret.isEmpty()) return
        busy = true
        val s = secret
        scope.launch {
            val ok = withContext(Dispatchers.Default) { vault.unlockFolderSecret(f.id, s.toCharArray()) }
            busy = false
            if (ok) onUnlocked(f) else {
                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                error = "Falsch – bitte nochmal."
                secret = ""
            }
        }
    }

    fun trySystem() {
        if (f == null) return
        val c = vault.folderSysCipher(f.id) ?: run { error = "Systemsperre nicht verfügbar – nutze die PIN."; return }
        Auth.prompt(activity, "„${f.name}“ öffnen", null, c, onSuccess = { ok ->
            scope.launch {
                val success = withContext(Dispatchers.Default) { vault.unlockFolderSys(f.id, ok) }
                if (success) onUnlocked(f) else error = "Entsperren fehlgeschlagen."
            }
        }, onFail = {})
    }

    Sheet(f != null, onDismiss) {
        if (f == null) return@Sheet
        val isPw = f.protection == Protection.PASSWORD
        SheetHeader(
            Ic.lock, "„${f.name}“ ist geschützt",
            error ?: when (f.protection) {
                Protection.PASSWORD -> "Gib das Passwort für diesen Ordner ein."
                Protection.SYSTEM -> "Mit Systemsperre oder Tresor-PIN öffnen."
                else -> "Gib deinen Tresor-Code ein."
            }
        )
        GlassField(
            secret, { secret = it; error = null },
            if (isPw) "Passwort" else "Tresor-Code",
            password = true, numeric = !isPw, autoFocus = f.protection != Protection.SYSTEM,
            onDone = { trySecret() }
        )
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
            else PillButton("Entsperren", primary = true, enabled = secret.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { trySecret() }
        }
        if (f.protection == Protection.SYSTEM) {
            Spacer(Modifier.height(10.dp))
            PillButton("Systemsperre verwenden", icon = Ic.fingerprint, modifier = Modifier.fillMaxWidth()) { trySystem() }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
    androidx.compose.runtime.LaunchedEffect(f?.id) {
        if (f?.protection == Protection.SYSTEM) trySystem()
    }
}

/**
 * Versteckte Ordner hervorholen: Der eingegebene Code öffnet genau die Ordner, die diesen Code haben.
 * Der Fingerabdruck öffnet alle Ordner mit Systemsperre. Falsche Eingaben verraten nicht, ob es weitere gibt.
 */
@Composable
fun androidx.compose.foundation.layout.BoxScope.FolderCodeSheet(
    vault: Vault,
    kind: FolderKind,
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpened: (List<String>) -> Unit = {}
) {
    val activity = LocalContext.current as FragmentActivity
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var code by remember(visible) { mutableStateOf("") }
    var letters by remember(visible) { mutableStateOf(false) }
    var busy by remember(visible) { mutableStateOf(false) }
    var error by remember(visible) { mutableStateOf<String?>(null) }
    var wrong by remember { mutableStateOf(0) }
    val anyRevealed = vault.revealed.any { id -> vault.folder(id)?.kind == kind }

    fun done(opened: List<String>) {
        if (opened.isEmpty()) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            error = "Kein Ordner gefunden."
            code = ""
            wrong++
        } else {
            wrong = 0
            onDismiss()
            val what = if (kind == FolderKind.CHAT) "Konto" to "Konten" else "Ordner" to "Ordner"
            App.toast(if (opened.size == 1) "1 ${what.first} geöffnet" else "${opened.size} ${what.second} geöffnet")
            onOpened(opened)
        }
    }

    fun tryCode() {
        if (code.isEmpty() || busy) return
        busy = true
        val c = code.toCharArray()
        scope.launch {
            // Bremse gegen Durchprobieren: nach mehreren Fehlversuchen kurz warten
            if (wrong >= 3) kotlinx.coroutines.delay(minOf(30_000L, 2_000L * (wrong - 2)))
            val opened = withContext(Dispatchers.Default) {
                try {
                    vault.unlockByCode(c, kind)
                } finally {
                    java.util.Arrays.fill(c, '\u0000')
                }
            }
            busy = false
            done(opened)
        }
    }

    fun tryFinger() {
        if (busy) return
        val c = vault.sysGroupUnlockCipher()
        if (c == null) {
            done(emptyList())
            return
        }
        Auth.prompt(activity, "Ordner öffnen", null, c, onSuccess = { ok ->
            scope.launch {
                val opened = withContext(Dispatchers.Default) { vault.unlockAllSys(ok, kind) }
                done(opened)
            }
        }, onFail = {})
    }

    Sheet(visible, onDismiss) {
        SheetHeader(Ic.keypad, "Ordner öffnen", error ?: "Code eingeben – es erscheinen genau die Ordner mit diesem Code.")
        GlassField(
            code, { code = it; error = null },
            "Code",
            password = true, numeric = !letters, autoFocus = visible,
            onDone = { tryCode() }
        )
        Text(
            if (letters) "Nur Zahlen" else "Mit Buchstaben",
            style = G.footnote.copy(color = G.dim),
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 6.dp)
                .pressable(androidx.compose.foundation.shape.RoundedCornerShape(50)) { letters = !letters }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
            else PillButton("Öffnen", primary = true, enabled = code.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { tryCode() }
        }
        if (Auth.available(activity)) {
            Spacer(Modifier.height(10.dp))
            PillButton("Mit Fingerabdruck", icon = Ic.fingerprint, enabled = !busy, modifier = Modifier.fillMaxWidth()) { tryFinger() }
        }
        if (anyRevealed) {
            Spacer(Modifier.height(10.dp))
            PillButton("Wieder verstecken", icon = Ic.eyeOff, modifier = Modifier.fillMaxWidth()) {
                vault.hideRevealed()
                App.relockUnused()
                onDismiss()
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
