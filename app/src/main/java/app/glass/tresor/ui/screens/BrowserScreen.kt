package app.glass.tresor.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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

private val quickLinks = listOf(
    Bookmark("DuckDuckGo", "https://duckduckgo.com"),
    Bookmark("Wikipedia", "https://de.wikipedia.org"),
    Bookmark("ORF", "https://orf.at"),
    Bookmark("Wetter", "https://www.wetter.com"),
)

/** Eingabe → Adresse: echte Adresse direkt, sonst Suche. */
private fun toUrl(input: String): String {
    val t = input.trim()
    if (t.startsWith("http://") || t.startsWith("https://")) return t
    if (!t.contains(' ') && t.contains('.') && !t.endsWith('.')) return "https://$t"
    return SEARCH + Uri.encode(t)
}

private fun hostOf(url: String): String = try {
    Uri.parse(url).host?.removePrefix("www.") ?: url
} catch (e: Exception) {
    url
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen() {
    val vault = App.vault ?: return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    var input by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(1f) }
    var started by remember { mutableStateOf(false) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var bookmarks by remember { mutableStateOf(BrowserData.bookmarks(vault)) }
    var showBookmarks by remember { mutableStateOf(false) }
    var downloads by remember { mutableIntStateOf(0) }
    val fieldFocus = remember { FocusRequester() }

    BrowserData.used = true

    val web = remember {
        WebView(ctx).apply {
            setBackgroundColor(android.graphics.Color.WHITE)
            // Die Webseite soll dem Adressfeld nicht den Fokus wegnehmen
            isFocusableInTouchMode = true
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
                loadWithOverviewMode = true
                useWideViewPort = true
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
                    if (url == "about:blank") return
                    currentUrl = url; error = null
                    canBack = view.canGoBack(); canForward = view.canGoForward()
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (url == "about:blank") return
                    currentUrl = url; title = view.title ?: ""
                    canBack = view.canGoBack(); canForward = view.canGoForward()
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, err: WebResourceError) {
                    if (request.isForMainFrame) {
                        error = "Seite konnte nicht geladen werden (${err.description}).\nIst die Berechtigung „Netzwerk“ für die App erlaubt?"
                    }
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
                downloads++
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
                    downloads--
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

    fun go(text: String) {
        if (text.isBlank()) return
        val url = toUrl(text)
        started = true
        error = null
        editing = false
        focus.clearFocus()
        currentUrl = url
        web.loadUrl(url)
    }

    fun home() {
        web.stopLoading()
        started = false
        title = ""; currentUrl = ""; input = ""; error = null
        web.loadUrl("about:blank")
        web.clearHistory()
        canBack = false; canForward = false
    }

    BackHandler(enabled = started || editing) {
        when {
            editing -> { editing = false; focus.clearFocus() }
            web.canGoBack() -> web.goBack()
            else -> home()
        }
    }

    val isBookmarked = currentUrl.isNotEmpty() && bookmarks.any { it.url == currentUrl }

    GlassBackground {
        Column(Modifier.fillMaxSize().imePadding()) {
            // Obere Leiste: schließen + Seitentitel
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(Ic.close, "Browser schließen") { App.nav.pop() }
                Spacer(Modifier.width(12.dp))
                Text(
                    if (started) title.ifBlank { hostOf(currentUrl) } else "Privater Browser",
                    style = G.headline.copy(fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (downloads > 0) Text("⬇ $downloads", style = G.footnote)
            }

            // Inhalt: Webseite oder Startseite
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { web },
                    update = { it.visibility = if (started) View.VISIBLE else View.GONE },
                    modifier = Modifier.fillMaxSize()
                )
                if (!started) {
                    StartPage(bookmarks, onOpen = { go(it.url) }, onSearch = {
                        editing = true
                        try { fieldFocus.requestFocus() } catch (_: Exception) {}
                    })
                }
                val err = error
                if (started && err != null) {
                    Column(
                        Modifier.fillMaxSize().background(G.bg).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Ic.globe, null, tint = G.dim, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(err, style = G.callout.copy(color = G.dim), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        app.glass.tresor.ui.PillButton("Erneut versuchen", height = 44.dp) { error = null; web.reload() }
                    }
                }
                // Ladebalken
                val shown by animateFloatAsState(if (progress < 1f && started) 1f else 0f, label = "prog")
                if (shown > 0f) {
                    Box(
                        Modifier.align(Alignment.TopStart).fillMaxWidth(progress.coerceIn(0.05f, 1f)).height(3.dp)
                            .background(G.accent.copy(alpha = shown))
                    )
                }
            }

            // Adressleiste unten (wie Safari)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp)
                    .height(50.dp)
                    .pressable(Pill, haptic = Haptic.NONE, highlight = 0.06f) {
                        editing = true
                        try { fieldFocus.requestFocus() } catch (_: Exception) {}
                    }
                    .glass(Pill, G.fillStrong)
                    .padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(if (!editing && currentUrl.startsWith("https://")) Ic.lock else Ic.search, null, tint = G.dim, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    val shownText = if (editing) input else if (started) hostOf(currentUrl) else ""
                    if (shownText.isEmpty()) Text("Suchen oder Adresse eingeben", style = G.body.copy(fontSize = 16.sp, color = G.faint))
                    BasicTextField(
                        value = shownText,
                        onValueChange = { input = it; editing = true },
                        singleLine = true,
                        textStyle = G.body.copy(fontSize = 16.sp, fontWeight = if (editing) FontWeight.Normal else FontWeight.Medium),
                        cursorBrush = SolidColor(G.accentText),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                        keyboardActions = KeyboardActions(onGo = { go(input) }, onDone = { go(input) }, onSearch = { go(input) }),
                        modifier = Modifier.fillMaxWidth()
                            .focusRequester(fieldFocus)
                            .onFocusChanged {
                                if (it.isFocused && !editing) {
                                    editing = true
                                    input = if (started) currentUrl else ""
                                }
                            }
                    )
                }
                if (editing && input.isNotEmpty()) {
                    Box(Modifier.size(36.dp).pressable(CircleShape, haptic = Haptic.NONE) { input = "" }, contentAlignment = Alignment.Center) {
                        Icon(Ic.close, "Leeren", tint = G.dim, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier.height(38.dp).pressable(Pill, highlight = 0f, haptic = Haptic.CONFIRM) { go(input) }
                            .clip(Pill).background(G.accent).padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Los", style = G.headline.copy(fontSize = 15.sp)) }
                } else if (started) {
                    Box(Modifier.size(38.dp).pressable(CircleShape) { web.reload() }, contentAlignment = Alignment.Center) {
                        Icon(Ic.refresh, "Neu laden", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Werkzeugleiste
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp).height(48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BarIcon(Ic.back, "Zurück", started) { if (web.canGoBack()) web.goBack() else home() }
                BarIcon(Ic.forward, "Vor", canForward) { web.goForward() }
                BarIcon(if (isBookmarked) Ic.check else Ic.plus, if (isBookmarked) "Lesezeichen entfernen" else "Lesezeichen speichern", started && currentUrl.startsWith("http")) {
                    bookmarks = if (isBookmarked) bookmarks.filterNot { it.url == currentUrl }
                    else bookmarks + Bookmark(title.ifBlank { hostOf(currentUrl) }, currentUrl)
                    val list = bookmarks
                    scope.launch(Dispatchers.IO) { BrowserData.saveBookmarks(vault, list) }
                    App.toast(if (isBookmarked) "Lesezeichen entfernt" else "Lesezeichen verschlüsselt gespeichert")
                }
                BarIcon(Ic.note, "Lesezeichen", true) { showBookmarks = true }
                BarIcon(Ic.grid, "Startseite", started) { home() }
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
private fun StartPage(bookmarks: List<Bookmark>, onOpen: (Bookmark) -> Unit, onSearch: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Großes Suchfeld – antippen öffnet die Tastatur in der Adressleiste
        Row(
            Modifier.fillMaxWidth().height(56.dp).pressable(Pill, haptic = Haptic.TICK, onClick = onSearch)
                .glass(Pill, G.fillStrong).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Ic.search, null, tint = G.dim, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("Suchen oder Adresse eingeben", style = G.body.copy(color = G.faint))
        }

        Text("FAVORITEN", style = G.caption, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        val all = (bookmarks + quickLinks).distinctBy { it.url }.take(12)
        all.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { b ->
                    Column(
                        Modifier.weight(1f).pressable(sq(18)) { onOpen(b) }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.size(58.dp).glass(sq(16)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(b.title.take(1).uppercase(), style = G.title3)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(b.title, style = G.footnote.copy(color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Row(Modifier.fillMaxWidth().glass(sq(22)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.shield, null, tint = G.mint, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Kein Verlauf, keine Spuren", style = G.headline.copy(fontSize = 15.sp))
                Text("Cookies und Website-Daten werden beim Sperren gelöscht. Downloads landen verschlüsselt im Tresor.", style = G.footnote)
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
