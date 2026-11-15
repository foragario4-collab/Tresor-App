package app.glass.tresor.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.glass.tresor.App
import app.glass.tresor.browser.Bookmark
import app.glass.tresor.browser.BrowserData
import app.glass.tresor.ui.G
import app.glass.tresor.ui.GlassBackground
import app.glass.tresor.ui.GlassIconButton
import app.glass.tresor.ui.Haptic
import app.glass.tresor.ui.Ic
import app.glass.tresor.ui.Pill
import app.glass.tresor.ui.Sheet
import app.glass.tresor.ui.SheetHeader
import app.glass.tresor.ui.glass
import app.glass.tresor.ui.pressable
import app.glass.tresor.ui.sq
import app.glass.tresor.vault.FolderKind
import app.glass.tresor.vault.Item
import app.glass.tresor.vault.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val SEARCH = "https://duckduckgo.com/?q="

/** Eingabe → Adresse: echte Adresse direkt, sonst Suche. */
private fun toUrl(input: String): String {
    val t = input.trim()
    if (t.startsWith("http://") || t.startsWith("https://")) return t
    if (!t.contains(' ') && t.contains('.') && !t.endsWith('.')) return "https://$t"
    return SEARCH + Uri.encode(t)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen() {
    val vault = App.vault ?: return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(1f) }
    var started by remember { mutableStateOf(false) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var bookmarks by remember { mutableStateOf(BrowserData.bookmarks(vault)) }
    var showBookmarks by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(0) }

    BrowserData.used = true

    val web = remember {
        WebView(ctx).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#07080C"))
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                allowFileAccess = false
                allowContentAccess = false
                setGeolocationEnabled(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                builtInZoomControls = true
                displayZoomControls = false
                setSupportMultipleWindows(false)
                mediaPlaybackRequiresUserGesture = true
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val s = request.url.scheme ?: return true
                    // Nur Webseiten – keine anderen Apps öffnen (intent:, tel:, market: …)
                    return s != "http" && s != "https"
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    currentUrl = url; address = url
                    canBack = view.canGoBack(); canForward = view.canGoForward()
                }

                override fun onPageFinished(view: WebView, url: String) {
                    currentUrl = url; address = url; title = view.title ?: ""
                    canBack = view.canGoBack(); canForward = view.canGoForward()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress = newProgress / 100f
                }
            }
            // Downloads landen direkt verschlüsselt im Tresor-Ordner „Downloads“
            setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                val name = URLUtil.guessFileName(url, contentDisposition, mimetype)
                downloading++
                App.toast("Lade „$name“ verschlüsselt …")
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        try {
                            val folder = vault.ensureFolder("Downloads", FolderKind.FILES)
                            val c = URL(url).openConnection() as HttpURLConnection
                            c.setRequestProperty("User-Agent", userAgent)
                            CookieManager.getInstance().getCookie(url)?.let { c.setRequestProperty("Cookie", it) }
                            c.connectTimeout = 15_000
                            c.readTimeout = 60_000
                            try {
                                val mime = mimetype?.takeIf { it.isNotBlank() } ?: (c.contentType ?: "application/octet-stream")
                                val now = System.currentTimeMillis()
                                val meta = Item(vault.newItemId(), name, mime, Media.typeOf(mime), -1, now, now)
                                c.inputStream.use { vault.importStream(folder.id, it, meta, null) }
                                true
                            } finally {
                                c.disconnect()
                            }
                        } catch (e: Exception) {
                            false
                        }
                    }
                    downloading--
                    App.toast(if (ok) "„$name“ liegt verschlüsselt in Dateien → Downloads" else "Download fehlgeschlagen")
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            web.stopLoading()
            web.loadUrl("about:blank")
            web.clearHistory()
            web.destroy()
        }
    }

    fun go(input: String) {
        if (input.isBlank()) return
        started = true
        web.loadUrl(toUrl(input))
    }

    BackHandler(enabled = started) {
        if (web.canGoBack()) web.goBack() else {
            started = false; address = ""; title = ""
            web.loadUrl("about:blank")
        }
    }

    val isBookmarked = bookmarks.any { it.url == currentUrl }

    GlassBackground {
        Column(Modifier.fillMaxSize().imePadding()) {
            // Adressleiste
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp).padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(Ic.close, "Browser schließen") { App.nav.pop() }
                Spacer(Modifier.width(8.dp))
                Row(
                    Modifier.weight(1f).height(44.dp).glass(Pill).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (currentUrl.startsWith("https://") || !started) Ic.lock else Ic.search, null, tint = G.dim, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (address.isEmpty()) Text("Suchen oder Adresse", style = G.body.copy(fontSize = 15.sp, color = G.faint))
                        BasicTextField(
                            value = address, onValueChange = { address = it }, singleLine = true,
                            textStyle = G.body.copy(fontSize = 15.sp), cursorBrush = SolidColor(G.accentText),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                            keyboardActions = KeyboardActions(onGo = { go(address) }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            val shown by animateFloatAsState(if (progress < 1f && started) 1f else 0f, label = "prog")
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).height(2.dp)) {
                if (shown > 0f) Box(Modifier.fillMaxWidth(progress.coerceIn(0.05f, 1f)).height(2.dp).clip(Pill).background(G.accent.copy(alpha = shown)))
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView({ web }, Modifier.fillMaxSize())
                if (!started) StartPage(bookmarks, onOpen = { go(it.url) })
            }

            // Untere Leiste
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 10.dp)
                    .height(56.dp).glass(Pill, G.fillStrong).padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BarIcon(Ic.back, "Zurück", canBack) { web.goBack() }
                BarIcon(Ic.forward, "Vor", canForward) { web.goForward() }
                BarIcon(Ic.refresh, "Neu laden", started) { web.reload() }
                BarIcon(if (isBookmarked) Ic.check else Ic.plus, if (isBookmarked) "Lesezeichen entfernen" else "Lesezeichen speichern", started && currentUrl.startsWith("http")) {
                    bookmarks = if (isBookmarked) bookmarks.filterNot { it.url == currentUrl }
                    else bookmarks + Bookmark(title.ifBlank { currentUrl }, currentUrl)
                    val list = bookmarks
                    scope.launch(Dispatchers.IO) { BrowserData.saveBookmarks(vault, list) }
                    App.toast(if (isBookmarked) "Lesezeichen entfernt" else "Lesezeichen verschlüsselt gespeichert")
                }
                BarIcon(Ic.note, "Lesezeichen", true) { showBookmarks = true }
            }
        }

        Sheet(showBookmarks, { showBookmarks = false }) {
            SheetHeader(Ic.note, "Lesezeichen", if (bookmarks.isEmpty()) "Noch keine Lesezeichen. Tippe auf + in der Leiste." else "Verschlüsselt in diesem Tresor gespeichert.")
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                bookmarks.forEach { b ->
                    Row(
                        Modifier.fillMaxWidth().pressable(sq(16), onLongClick = {
                            bookmarks = bookmarks - b
                            val list = bookmarks
                            scope.launch(Dispatchers.IO) { BrowserData.saveBookmarks(vault, list) }
                        }) { showBookmarks = false; go(b.url) }.glass(sq(16)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(b.title, style = G.body.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(b.url, style = G.footnote, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (bookmarks.isNotEmpty()) Text("Lange drücken zum Entfernen", style = G.footnote.copy(color = G.faint))
            }
        }
    }
}

@Composable
private fun StartPage(bookmarks: List<Bookmark>, onOpen: (Bookmark) -> Unit) {
    Column(
        Modifier.fillMaxSize().background(G.bg).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Privater Browser", style = G.largeTitle)
        Row(Modifier.fillMaxWidth().glass(sq(22)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.shield, null, tint = G.mint, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Kein Verlauf, keine Spuren", style = G.headline.copy(fontSize = 15.sp))
                Text("Cookies und Website-Daten werden beim Sperren gelöscht. Downloads landen verschlüsselt im Tresor.", style = G.footnote)
            }
        }
        if (bookmarks.isNotEmpty()) {
            Text("LESEZEICHEN", style = G.caption, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
            bookmarks.forEach { b ->
                Row(
                    Modifier.fillMaxWidth().pressable(sq(18)) { onOpen(b) }.glass(sq(18)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(G.blue), contentAlignment = Alignment.Center) {
                        Text(b.title.take(1).uppercase(), style = G.headline.copy(fontSize = 15.sp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.title, style = G.body.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Uri.parse(b.url).host ?: b.url, style = G.footnote, maxLines = 1)
                    }
                }
            }
        }
        Text(
            "Hinweis: Webseiten sehen deine normale Internet-Adresse. Für anonymes Surfen nutze zusätzlich den Tor Browser.",
            style = G.footnote.copy(color = G.faint), modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun BarIcon(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).pressable(CircleShape, enabled = enabled, scaleTo = 0.9f, haptic = Haptic.TICK, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f), modifier = Modifier.size(21.dp))
    }
}
