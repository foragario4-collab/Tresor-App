package app.glass.tresor.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.glass.tresor.App
import app.glass.tresor.vault.Item
import app.glass.tresor.vault.ItemType
import app.glass.tresor.vault.Media
import app.glass.tresor.vault.ThumbCache
import app.glass.tresor.vault.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ───────────────────────── Vorschaubild ─────────────────────────

private val placeholderColors = listOf(
    Color(0xFF34507E), Color(0xFF6B4A7D), Color(0xFF2F6A62), Color(0xFF7D5A3A),
    Color(0xFF4A5D8A), Color(0xFF3D6B4F), Color(0xFF80485A), Color(0xFF55607A)
)

fun placeholderFor(id: String) = placeholderColors[(id.hashCode() and 0x7fffffff) % placeholderColors.size]

@Composable
fun Thumb(vault: Vault, fid: String, item: Item, modifier: Modifier = Modifier, showBadge: Boolean = true) {
    var bmp by remember(fid, item.id) { mutableStateOf<ImageBitmap?>(ThumbCache.get(vault.slot, fid, item.id)) }
    LaunchedEffect(fid, item.id) {
        if (bmp == null && item.hasThumb) {
            bmp = withContext(Dispatchers.IO) {
                vault.readThumb(fid, item.id)?.let { bytes ->
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }?.also { ThumbCache.put(vault.slot, fid, item.id, it) }
            }
        }
    }
    val alpha by animateFloatAsState(if (bmp != null) 1f else 0f, tween(220), label = "thumbAlpha")
    Box(modifier.background(placeholderFor(item.id))) {
        bmp?.let {
            Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha })
        }
        if (!item.hasThumb && item.type != ItemType.IMAGE) {
            Icon(
                when (item.type) { ItemType.VIDEO -> Ic.video; ItemType.AUDIO -> Ic.music; else -> Ic.file }, null, tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.Center).size(26.dp)
            )
        }
        if (showBadge && item.type == ItemType.VIDEO) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Ic.play, null, tint = Color.White, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text(formatDuration(item.durationMs), style = G.caption.copy(color = Color.White, fontSize = 11.sp))
            }
        }
    }
}

// ───────────────────────── Sheet & Eingabefelder ─────────────────────────

/** Glas-Sheet, das von unten hochfährt (im selben Fenster → Screenshot-Schutz gilt). */
@Composable
fun BoxScope.Sheet(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }
        )
    }
    AnimatedVisibility(
        visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 420f)) { it } + fadeIn(tween(160)),
        exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(200))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(10.dp)
                .clip(sq(38))
                .background(G.sheet)
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.06f))), sq(38))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(22.dp),
            content = content
        )
    }
    if (visible) BackHandler { onDismiss() }
}

/** Welches Passwortfeld gerade die eigene Tastatur zeigt (immer nur eines). */
object SecureKbState {
    var active by mutableStateOf<Any?>(null)
}

@Composable
fun GlassField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    numeric: Boolean = false,
    autoFocus: Boolean = false,
    singleLine: Boolean = true,
    onDone: (() -> Unit)? = null,
) {
    var show by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    // Codes und Passwörter über die eigene Tastatur: keine fremde Tastatur-App kann mitlesen
    val secure = password && App.prefs.getBoolean("secureKb", true)
    val myId = remember { Any() }
    if (autoFocus) LaunchedEffect(Unit) {
        if (secure) SecureKbState.active = myId
        else try { fr.requestFocus() } catch (_: Exception) {}
    }
    val kbOpen = secure && SecureKbState.active === myId
    Column(modifier.fillMaxWidth()) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(Pill)
            .background(Color.White.copy(alpha = if (kbOpen) 0.13f else 0.09f))
            .border(1.dp, if (kbOpen) G.accentText.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.16f), Pill)
            .then(if (secure) Modifier.pressable(Pill, haptic = Haptic.NONE, scaleTo = 1f, highlight = 0f) { SecureKbState.active = myId } else Modifier)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = G.body.copy(color = G.faint))
            if (secure) {
                Text(if (show) value else "•".repeat(value.length), style = G.body, maxLines = 1)
            } else BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = singleLine,
                textStyle = G.body,
                cursorBrush = SolidColor(G.accentText),
                visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = when {
                        numeric -> KeyboardType.NumberPassword
                        password -> KeyboardType.Password
                        else -> KeyboardType.Text
                    },
                    imeAction = ImeAction.Done,
                    autoCorrectEnabled = !password && !numeric
                ),
                keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                modifier = Modifier.fillMaxWidth().focusRequester(fr)
            )
        }
        if (password) {
            Box(
                Modifier.size(40.dp).pressable(CircleShape, haptic = Haptic.NONE) { show = !show },
                contentAlignment = Alignment.Center
            ) {
                Icon(if (show) Ic.eyeOff else Ic.eye, if (show) "Verbergen" else "Anzeigen", tint = G.dim, modifier = Modifier.size(20.dp))
            }
        }
    }
    if (kbOpen) {
        Spacer(Modifier.height(8.dp))
        SecureKeyboard(
            numeric = numeric,
            onKey = { c -> if (value.length < 64) onChange(value + c) },
            onDelete = { if (value.isNotEmpty()) onChange(value.dropLast(1)) },
            onDone = { SecureKbState.active = null; onDone?.invoke() }
        )
    }
    }
}

/** Eingebaute Tastatur für Codes und Passwörter (QWERTZ, Zahlen, Zeichen). */
@Composable
fun SecureKeyboard(numeric: Boolean, onKey: (Char) -> Unit, onDelete: () -> Unit, onDone: () -> Unit) {
    var mode by remember { mutableStateOf(if (numeric) 2 else 0) } // 0 = Buchstaben, 1 = Zeichen, 2 = Ziffern
    var shift by remember { mutableStateOf(false) }
    val rows: List<String> = when (mode) {
        0 -> listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnm")
        1 -> listOf("1234567890", "-/:;()€&@\"", ".,?!'_%*+=")
        else -> listOf("123", "456", "789")
    }
    Column(
        Modifier.fillMaxWidth().clip(sq(18)).background(Color.White.copy(alpha = 0.06f)).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEachIndexed { ri, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (mode == 0 && ri == 2) KbKey(if (shift) "⇧" else "⇪", Modifier.weight(1.5f), dark = true) { shift = !shift }
                row.forEach { ch ->
                    val c = if (mode == 0 && shift) ch.uppercaseChar() else ch
                    KbKey(c.toString(), Modifier.weight(1f), tall = mode == 2) { onKey(c); if (shift) shift = false }
                }
                if (mode == 0 && ri == 2) KbKey("⌫", Modifier.weight(1.5f), dark = true) { onDelete() }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (numeric) {
                Spacer(Modifier.weight(1f))
                KbKey("0", Modifier.weight(1f), tall = true) { onKey('0') }
                KbKey("⌫", Modifier.weight(1f), dark = true, tall = true) { onDelete() }
            } else {
                KbKey(if (mode == 0) "123" else "ABC", Modifier.weight(1.4f), dark = true) { mode = if (mode == 0) 1 else 0 }
                KbKey("Leertaste", Modifier.weight(4f)) { onKey(' ') }
                if (mode != 0) KbKey("⌫", Modifier.weight(1.2f), dark = true) { onDelete() }
            }
            KbKey("OK", Modifier.weight(if (numeric) 1f else 1.4f), accent = true, tall = numeric) { onDone() }
        }
    }
}

@Composable
private fun KbKey(label: String, modifier: Modifier, dark: Boolean = false, accent: Boolean = false, tall: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .height(if (tall) 48.dp else 42.dp)
            .pressable(sq(10), scaleTo = 0.92f, highlight = 0.12f, haptic = Haptic.NONE) { onClick() }
            .clip(sq(10))
            .background(
                when {
                    accent -> G.accent
                    dark -> Color.White.copy(alpha = 0.10f)
                    else -> Color.White.copy(alpha = 0.18f)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = G.body.copy(fontSize = if (label.length > 3) 14.sp else 18.sp, color = Color.White), maxLines = 1)
    }
}

@Composable
fun SheetHeader(icon: ImageVector, title: String, text: String?) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = G.title2.copy(fontSize = 21.sp))
        if (text != null) {
            Spacer(Modifier.height(4.dp))
            Text(text, style = G.callout.copy(color = G.dim), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        Spacer(Modifier.height(18.dp))
    }
}

// ───────────────────────── Import / Kamera / Export ─────────────────────────

class ImportActions(
    val busy: () -> Int,
    val pickMedia: () -> Unit,
    val pickFiles: () -> Unit,
    val takePhoto: () -> Unit,
    val takeVideo: () -> Unit,
    val pickAudio: () -> Unit = {},
)

private val stamp get() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMANY).format(Date())

@Composable
fun rememberImport(vault: Vault, targetFid: () -> String?, onDone: (Int) -> Unit = {}): ImportActions {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableIntStateOf(0) }

    // Android fragt selbst nach, ob die Originale gelöscht werden dürfen (wie bei Photok)
    val deleter = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        App.suppressLock = false
        if (r.resultCode == android.app.Activity.RESULT_OK) App.toast("Originale gelöscht")
    }

    fun offerDeleteOriginals(imported: List<Uri>) {
        if (!App.prefs.getBoolean("deleteOriginals", true) || imported.isEmpty()) return
        val media = imported.mapNotNull { toMediaStoreUri(ctx, it) }
        if (media.isEmpty()) return
        try {
            val pi = android.provider.MediaStore.createDeleteRequest(ctx.contentResolver, media)
            App.suppressLock = true
            deleter.launch(androidx.activity.result.IntentSenderRequest.Builder(pi.intentSender).build())
        } catch (e: Exception) {
            App.suppressLock = false
            App.toast("Originale bitte in der Galerie löschen")
        }
    }

    fun run(uris: List<Uri>, names: List<String?>? = null, offerDelete: Boolean = false, cleanup: (() -> Unit)? = null) {
        val fid = targetFid() ?: return
        busy = uris.size
        scope.launch {
            var n = 0
            val done = mutableListOf<Uri>()
            for ((i, u) in uris.withIndex()) {
                val ok = withContext(Dispatchers.IO) { Media.importUri(ctx, vault, fid, u, names?.getOrNull(i)) != null }
                if (ok) { n++; done += u }
                busy--
            }
            cleanup?.invoke()
            busy = 0
            onDone(n)
            if (n > 0) App.toast(if (n == 1) "1 Element verschlüsselt" else "$n Elemente verschlüsselt")
            else App.toast("Import fehlgeschlagen")
            if (offerDelete) offerDeleteOriginals(done)
        }
    }

    val media = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        App.suppressLock = false
        if (uris.isNotEmpty()) run(uris, offerDelete = true)
    }
    val docs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        App.suppressLock = false
        if (uris.isNotEmpty()) run(uris, offerDelete = true)
    }
    return remember(vault) {
        ImportActions(
            busy = { busy },
            pickMedia = {
                App.suppressLock = true
                try {
                    media.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                } catch (e: Exception) {
                    App.suppressLock = false
                    App.toast("Keine Galerie-Auswahl verfügbar")
                }
            },
            pickFiles = {
                App.suppressLock = true
                try {
                    docs.launch(arrayOf("*/*"))
                } catch (e: Exception) {
                    App.suppressLock = false
                }
            },
            // Eigene Kamera: Aufnahmen gehen direkt verschlüsselt in den Ordner
            takePhoto = { targetFid()?.let { App.nav.push(app.glass.tresor.Screen.Camera(it, false)) } },
            takeVideo = { targetFid()?.let { App.nav.push(app.glass.tresor.Screen.Camera(it, true)) } },
            pickAudio = {
                App.suppressLock = true
                try {
                    docs.launch(arrayOf("audio/*", "application/ogg"))
                } catch (e: Exception) {
                    App.suppressLock = false
                }
            },
        )
    }
}

/** Wandelt eine Auswahl-Adresse in die Galerie-Adresse um, damit Android das Original löschen kann. */
fun toMediaStoreUri(ctx: android.content.Context, u: Uri): Uri? {
    try {
        android.provider.MediaStore.getMediaUri(ctx, u)?.let { return it }
    } catch (_: Exception) {
    }
    if (u.authority == android.provider.MediaStore.AUTHORITY) {
        val id = u.lastPathSegment?.toLongOrNull() ?: return null
        return if (u.path?.contains("picker") == true)
            android.content.ContentUris.withAppendedId(
                android.provider.MediaStore.Files.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL), id
            )
        else u
    }
    return null
}

fun fileUri(ctx: android.content.Context, f: File): Uri =
    FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)

/** Export in einen Ordner nach Wahl (entschlüsselt!). */
@Composable
fun rememberExport(vault: Vault): (List<Pair<String, Item>>) -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<List<Pair<String, Item>>>(emptyList()) }
    val tree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        App.suppressLock = false
        val list = pending
        pending = emptyList()
        if (treeUri == null || list.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val n = withContext(Dispatchers.IO) {
                var count = 0
                val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
                for ((fid, item) in list) {
                    try {
                        val name = if (item.type == ItemType.NOTE) "${item.name.ifBlank { "Notiz" }}.txt" else item.name
                        val mime = if (item.type == ItemType.NOTE) "text/plain" else item.mime
                        val doc = DocumentsContract.createDocument(ctx.contentResolver, parent, mime, name) ?: continue
                        ctx.contentResolver.openOutputStream(doc)?.use { vault.exportTo(fid, item.id, it) }
                        count++
                    } catch (_: Exception) {
                    }
                }
                count
            }
            App.toast(if (n == 1) "1 Element exportiert" else "$n Elemente exportiert")
        }
    }
    return { list ->
        pending = list
        App.suppressLock = true
        try {
            tree.launch(null)
        } catch (e: Exception) {
            App.suppressLock = false
        }
    }
}

/** Datei entschlüsselt an eine andere App übergeben (z. B. PDF-Viewer). */
suspend fun openWith(ctx: android.content.Context, vault: Vault, fid: String, item: Item) {
    val f = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val out = File(dir, item.name.replace("/", "_"))
        out.outputStream().use { vault.exportTo(fid, item.id, it) }
        out
    }
    App.lastShare = System.currentTimeMillis()
    val i = Intent(Intent.ACTION_VIEW)
        .setDataAndType(fileUri(ctx, f), item.mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        ctx.startActivity(Intent.createChooser(i, "Öffnen mit"))
    } catch (e: Exception) {
        App.toast("Keine passende App gefunden")
    }
}

// ───────────────────────── Bestätigung ─────────────────────────

@Composable
fun BoxScope.ConfirmSheet(
    visible: Boolean,
    title: String,
    text: String,
    confirm: String,
    destructive: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Sheet(visible, onDismiss) {
        SheetHeader(if (destructive) Ic.trash else Ic.check, title, text)
        PillButton(
            confirm, primary = !destructive, modifier = Modifier.fillMaxWidth(),
            textColor = if (destructive) G.red else null
        ) { onConfirm() }
        Spacer(Modifier.height(10.dp))
        PillButton("Abbrechen", modifier = Modifier.fillMaxWidth()) { onDismiss() }
    }
}

val dateFmt get() = SimpleDateFormat("d. MMM yyyy", Locale.GERMANY)

@Composable
fun BusyBanner(count: Int, modifier: Modifier = Modifier) {
    AnimatedVisibility(count > 0, modifier = modifier, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
        Row(
            Modifier.glass(Pill, G.fillStrong).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Text("Verschlüssele … noch $count", style = G.callout.copy(fontWeight = FontWeight.Medium))
        }
    }
}

