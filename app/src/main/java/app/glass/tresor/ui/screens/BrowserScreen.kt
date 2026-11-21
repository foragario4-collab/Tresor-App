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
import app.glass.tresor.ui.SettingRow
import app.glass.tresor.ui.GlassSwitch
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import app.glass.tresor.chat.ChatService
import android.content.Intent
import android.view.ViewGroup
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.glass.tresor.Screen
import app.glass.tresor.browser.BrowserAreas
import app.glass.tresor.browser.BrowserTor
import app.glass.tresor.ui.GlassField
import app.glass.tresor.ui.PillButton
import app.glass.tresor.ui.ConfirmSheet
import kotlinx.coroutines.delay

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


/** Ein Tab: eigene WebView, eigener Verlauf. */
private class Tab(val id: Long) {
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableFloatStateOf(1f)
    var started by mutableStateOf(false)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var web: WebView? = null
}

/**
 * Privater Browser mit Tabs, Werbeblocker, Tor-Modus und geheimen Bereichen.
 * disguise = true: läuft als Tarnung „Browser“ ohne Tresor – Code in die Adresszeile tippen öffnet den Tresor.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(disguise: Boolean = false) {
    val vault = if (disguise) null else (App.vault ?: return)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val view = LocalView.current

    val area = if (vault != null) BrowserAreas.current(vault) else null
    var input by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var bookmarks by remember(area) { mutableStateOf(BrowserData.bookmarks(vault, area)) }
    var adOffSites by remember(area) { mutableStateOf(BrowserData.adOffSites(vault, area)) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showTabs by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showAreas by remember { mutableStateOf(false) }
    var areaCode by remember { mutableStateOf(false) }
    var newArea by remember { mutableStateOf(false) }
    var newAreaName by remember { mutableStateOf("") }
    var unlockArea by remember { mutableStateOf<app.glass.tresor.vault.Folder?>(null) }
    var downloads by remember { mutableIntStateOf(0) }
    var blockedCount by remember { mutableIntStateOf(0) }
    var adSheet by remember { mutableStateOf(false) }
    var adOn by remember { mutableStateOf(App.prefs.getBoolean("adblock", true)) }
    var jsOn by remember { mutableStateOf(App.prefs.getBoolean("browserJs", true)) }
    val torMode = !disguise && BrowserTor.enabled
    val adActive = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    val blockCounter = remember { java.util.concurrent.atomic.AtomicInteger(0) }
    val tabs = remember(area) { mutableStateListOf<Tab>() }
    var currentId by remember(area) { mutableStateOf(0L) }
    val fieldFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { app.glass.tresor.browser.AdBlock.load(ctx) } }
    LaunchedEffect(Unit) { while (true) { blockedCount = blockCounter.get(); delay(500) } }
    // Tor-Modus: Weiterleitung aufbauen, sobald Tor bereit ist
    LaunchedEffect(torMode) {
        while (torMode && !BrowserTor.ready) { BrowserTor.ensure(ctx); delay(700) }
    }
    BrowserData.used = true

    fun newWeb(tab: Tab): WebView = WebView(ctx).apply {
        setBackgroundColor(android.graphics.Color.WHITE)
        isFocusableInTouchMode = true
        settings.apply {
            javaScriptEnabled = jsOn
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
            if (torMode) userAgentString = BrowserTor.TOR_UA
        }
        // WebRTC sperren – sonst könnte eine Seite die echte IP herausfinden (auch am Tor vorbei)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try { WebViewCompat.addDocumentStartJavaScript(this, BrowserTor.ANTI_LEAK_JS, setOf("*")) } catch (_: Exception) {}
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val s = request.url.scheme ?: return true
                return s != "http" && s != "https"
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                // Tor-Modus: Bevor die Weiterleitung steht, geht nichts raus
                if (torMode && !BrowserTor.ready) return android.webkit.WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
                if (!adActive.get() || !app.glass.tresor.browser.AdBlock.loaded) return null
                val r = app.glass.tresor.browser.AdBlock.intercept(request) ?: return null
                blockCounter.incrementAndGet()
                return r
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (url == "about:blank") return
                if (tab.id == currentId) blockCounter.set(0)
                adActive.set(adOn && hostOf(url).removePrefix("www.") !in adOffSites)
                tab.url = url; tab.error = null
                tab.canBack = view.canGoBack(); tab.canForward = view.canGoForward()
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (url == "about:blank") return
                tab.url = url; tab.title = view.title ?: ""
                tab.canBack = view.canGoBack(); tab.canForward = view.canGoForward()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, err: WebResourceError) {
                if (request.isForMainFrame) {
                    tab.error = if (torMode) "Seite über Tor nicht erreichbar (${err.description}).\nTor-Seiten brauchen manchmal etwas länger."
                    else "Seite konnte nicht geladen werden (${err.description}).\nIst die Berechtigung „Netzwerk“ für die App erlaubt?"
                }
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { tab.progress = newProgress / 100f }
        }
        setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            val v = vault
            if (v == null) { App.toast("Downloads sind hier nicht möglich"); return@setDownloadListener }
            val name = URLUtil.guessFileName(url, contentDisposition, mimetype)
            downloads++
            App.toast("Lade „$name“ verschlüsselt …")
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val folder = v.ensureFolder("Downloads", FolderKind.FILES)
                        val proxy = if (torMode && ChatService.socksPort > 0)
                            java.net.Proxy(java.net.Proxy.Type.SOCKS, java.net.InetSocketAddress("127.0.0.1", ChatService.socksPort)) else java.net.Proxy.NO_PROXY
                        if (torMode && proxy == java.net.Proxy.NO_PROXY) throw IllegalStateException("Tor nicht bereit")
                        val c = URL(url).openConnection(proxy) as HttpURLConnection
                        c.setRequestProperty("User-Agent", userAgent)
                        CookieManager.getInstance().getCookie(url)?.let { c.setRequestProperty("Cookie", it) }
                        c.connectTimeout = 30_000
                        c.readTimeout = 90_000
                        try {
                            val mime = mimetype?.takeIf { it.isNotBlank() } ?: (c.contentType ?: "application/octet-stream")
                            val now = System.currentTimeMillis()
                            val meta = Item(v.newItemId(), name, mime, Media.typeOf(mime), -1, now, now)
                            c.inputStream.use { v.importStream(folder.id, it, meta, null) }
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

    fun current(): Tab {
        tabs.firstOrNull { it.id == currentId }?.let { return it }
        val t = tabs.firstOrNull() ?: Tab(System.nanoTime()).also { it.web = newWeb(it); tabs.add(it) }
        currentId = t.id
        return t
    }
    val tab = current()
    adActive.set(adOn && hostOf(tab.url).removePrefix("www.") !in adOffSites)

    fun destroy(t: Tab) {
        t.web?.let { w ->
            try { (w.parent as? ViewGroup)?.removeView(w); w.stopLoading(); w.loadUrl("about:blank"); w.clearHistory(); w.destroy() } catch (_: Exception) {}
        }
        t.web = null
    }

    fun closeAll() {
        tabs.forEach { destroy(it) }
        tabs.clear()
        currentId = 0L
    }

    DisposableEffect(area) {
        onDispose { tabs.forEach { destroy(it) } }
    }

    fun newTab() {
        val t = Tab(System.nanoTime()).also { it.web = newWeb(it) }
        tabs.add(t)
        currentId = t.id
        editing = true
        input = ""
    }

    fun closeTab(t: Tab) {
        destroy(t)
        tabs.remove(t)
        if (currentId == t.id) currentId = tabs.lastOrNull()?.id ?: 0L
    }

    var secretTried = false
    fun go(text: String) {
        if (text.isBlank()) return
        val t = text.trim()
        // Tarnung: Code in die Adresszeile → Tresor öffnen (sieht aus wie eine Suche)
        if (disguise && t.length in 4..12 && t.all { it.isDigit() } && !secretTried) {
            editing = false; focus.clearFocus()
            scope.launch {
                // Passt der Code nicht, wird ganz normal danach gesucht
                if (App.tryUnlock(t, silent = true)) input = "" else { secretTried = true; go(t); secretTried = false }
            }
            return
        }
        if (torMode && !BrowserTor.ready) { App.toast("Tor verbindet noch …"); return }
        val url = toUrl(t)
        tab.started = true
        tab.error = null
        editing = false
        focus.clearFocus()
        tab.url = url
        tab.web?.loadUrl(url)
    }

    fun home() {
        val w = tab.web ?: return
        w.stopLoading()
        tab.started = false
        tab.title = ""; tab.url = ""; input = ""; tab.error = null
        w.loadUrl("about:blank")
        w.clearHistory()
        tab.canBack = false; tab.canForward = false
    }

    fun switchArea(a: String?) {
        closeAll()
        BrowserData.clearAll()
        BrowserData.used = true
        BrowserAreas.selected = a
    }

    BackHandler(enabled = tab.started || editing) {
        when {
            editing -> { editing = false; focus.clearFocus() }
            tab.web?.canGoBack() == true -> tab.web?.goBack()
            else -> home()
        }
    }

    val isBookmarked = tab.url.isNotEmpty() && bookmarks.any { it.url == tab.url }

    GlassBackground {
        Column(Modifier.fillMaxSize().imePadding()) {
            // Obere Leiste
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!disguise) {
                    GlassIconButton(Ic.close, "Browser schließen") { App.nav.pop() }
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (tab.started) tab.title.ifBlank { hostOf(tab.url) } else if (disguise) "Browser" else "Privater Browser",
                        style = G.headline.copy(fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (vault != null) {
                        // Bereich: tippen = wechseln, lange drücken = Code für geheime Bereiche
                        Text(
                            (if (torMode) "Tor · " else "") + BrowserAreas.name(vault, area) + " ▾",
                            style = G.footnote.copy(color = if (torMode) Color(0xFFB98CFF) else G.dim),
                            modifier = Modifier.pressable(Pill, haptic = Haptic.NONE, onLongClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); areaCode = true
                            }) { showAreas = true }
                        )
                    }
                }
                if (downloads > 0) Text("⬇ $downloads", style = G.footnote)
                Spacer(Modifier.width(8.dp))
                val active = adOn && hostOf(tab.url).removePrefix("www.") !in adOffSites
                Row(
                    Modifier.pressable(Pill) { adSheet = true }.glass(Pill).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Ic.shield, "Werbeblocker", tint = if (active) G.mint else G.faint, modifier = Modifier.size(16.dp))
                    if (active && tab.started) {
                        Spacer(Modifier.width(5.dp))
                        Text("$blockedCount", style = G.footnote.copy(color = Color.White))
                    }
                }
                Spacer(Modifier.width(8.dp))
                GlassIconButton(Ic.dots, "Menü") { showMenu = true }
            }

            // Inhalt
            Box(Modifier.weight(1f).fillMaxWidth()) {
                key(tab.id) {
                    AndroidView(
                        factory = { c -> tab.web?.let { w -> (w.parent as? ViewGroup)?.removeView(w) }; tab.web ?: View(c) },
                        update = { it.visibility = if (tab.started) View.VISIBLE else View.GONE },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (!tab.started) {
                    StartPage(bookmarks, disguise, torMode, onOpen = { go(it.url) }, onSearch = {
                        editing = true
                        try { fieldFocus.requestFocus() } catch (_: Exception) {}
                    })
                }
                if (torMode && !BrowserTor.ready) {
                    Row(
                        Modifier.align(Alignment.TopCenter).padding(top = 8.dp).glass(Pill, G.sheet).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Verbinde mit Tor …", style = G.footnote.copy(color = Color.White))
                    }
                }
                val err = tab.error
                if (tab.started && err != null) {
                    Column(
                        Modifier.fillMaxSize().background(G.bg).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Ic.globe, null, tint = G.dim, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(err, style = G.callout.copy(color = G.dim), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        PillButton("Erneut versuchen", height = 44.dp) { tab.error = null; tab.web?.reload() }
                    }
                }
                val shown by animateFloatAsState(if (tab.progress < 1f && tab.started) 1f else 0f, label = "prog")
                if (shown > 0f) {
                    Box(
                        Modifier.align(Alignment.TopStart).fillMaxWidth(tab.progress.coerceIn(0.05f, 1f)).height(3.dp)
                            .background((if (torMode) Color(0xFFB98CFF) else G.accent).copy(alpha = shown))
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
                Icon(if (!editing && tab.url.startsWith("https://")) Ic.lock else Ic.search, null, tint = G.dim, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    val shownText = if (editing) input else if (tab.started) hostOf(tab.url) else ""
                    if (shownText.isEmpty()) Text(if (torMode) "Über Tor suchen oder Adresse" else "Suchen oder Adresse eingeben", style = G.body.copy(fontSize = 16.sp, color = G.faint))
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
                                    input = if (tab.started) tab.url else ""
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
                } else if (tab.started) {
                    Box(Modifier.size(38.dp).pressable(CircleShape) { tab.web?.reload() }, contentAlignment = Alignment.Center) {
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
                BarIcon(Ic.back, "Zurück", tab.started) { if (tab.web?.canGoBack() == true) tab.web?.goBack() else home() }
                BarIcon(Ic.forward, "Vor", tab.canForward) { tab.web?.goForward() }
                BarIcon(if (isBookmarked) Ic.check else Ic.plus, if (isBookmarked) "Favorit entfernen" else "Als Favorit speichern", tab.started && tab.url.startsWith("http")) {
                    bookmarks = if (isBookmarked) bookmarks.filterNot { it.url == tab.url }
                    else bookmarks + Bookmark(tab.title.ifBlank { hostOf(tab.url) }, tab.url)
                    val list = bookmarks
                    scope.launch(Dispatchers.IO) { BrowserData.saveBookmarks(vault, list, area) }
                    App.toast(if (isBookmarked) "Favorit entfernt" else if (vault != null) "Favorit verschlüsselt gespeichert" else "Favorit gespeichert")
                }
                BarIcon(Ic.note, "Favoriten", true) { showBookmarks = true }
                // Tabs mit Anzahl
                Box(
                    Modifier.size(46.dp).pressable(CircleShape, scaleTo = 0.9f, haptic = Haptic.TICK) { showTabs = true },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(24.dp).border(1.8.dp, Color.White, sq(7)), contentAlignment = Alignment.Center) {
                        Text("${tabs.size}", style = G.footnote.copy(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        // ── Tabs ──
        Sheet(showTabs, { showTabs = false }) {
            SheetHeader(Ic.grid, "Tabs", if (tabs.size == 1) "1 offener Tab" else "${tabs.size} offene Tabs")
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs.toList().forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().pressable(sq(16)) { currentId = t.id; showTabs = false }
                            .glass(sq(16), if (t.id == currentId) G.accent.copy(alpha = 0.25f) else G.fill).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(if (t.started) t.title.ifBlank { hostOf(t.url) } else "Startseite", style = G.body.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (t.started) Text(hostOf(t.url), style = G.footnote, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(Modifier.size(38.dp).pressable(CircleShape) { closeTab(t) }, contentAlignment = Alignment.Center) {
                            Icon(Ic.close, "Tab schließen", tint = G.dim, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row {
                PillButton("Alle schließen", textColor = G.red, modifier = Modifier.weight(1f)) { closeAll(); showTabs = false }
                Spacer(Modifier.width(10.dp))
                PillButton("Neuer Tab", icon = Ic.plus, primary = true, modifier = Modifier.weight(1f)) { showTabs = false; newTab() }
            }
            Spacer(Modifier.navigationBarsPadding())
        }

        // ── Menü: Tor, JavaScript, VPN, Tor Browser ──
        Sheet(showMenu, { showMenu = false }) {
            val vpn = BrowserTor.vpnActive(ctx)
            SheetHeader(Ic.globe, "Privatsphäre", if (vpn) "VPN aktiv ✓ – deine Verbindung läuft über ein VPN." else "Kein VPN aktiv.")
            Column(Modifier.fillMaxWidth().glass(sq(20)).padding(horizontal = 14.dp, vertical = 2.dp)) {
                if (!disguise) {
                    SettingRow("Über Tor surfen", if (!BrowserTor.supported) "Auf diesem Gerät nicht verfügbar" else if (torMode && BrowserTor.ready) "Verbunden – Webseiten sehen deine IP nicht" else "Versteckt deine IP, öffnet .onion-Seiten") {
                        GlassSwitch(torMode, "Tor") { on ->
                            if (!BrowserTor.supported) return@GlassSwitch
                            closeAll()
                            BrowserData.clearAll(); BrowserData.used = true
                            BrowserTor.turn(on)
                        }
                    }
                }
                SettingRow("JavaScript", if (jsOn) "An – Seiten funktionieren normal" else "Aus – sicherer, manche Seiten gehen nicht", showDivider = !disguise) {
                    GlassSwitch(jsOn, "JavaScript") { on ->
                        jsOn = on; App.prefs.edit().putBoolean("browserJs", on).apply()
                        tabs.forEach { t -> t.web?.settings?.javaScriptEnabled = on; if (t.started) t.web?.reload() }
                    }
                }
            }
            if (torMode && BrowserTor.ready) {
                Spacer(Modifier.height(10.dp))
                PillButton("Neue Tor-Identität", icon = Ic.refresh, modifier = Modifier.fillMaxWidth()) {
                    if (ChatService.newIdentity()) { closeAll(); BrowserData.clearAll(); BrowserData.used = true; App.toast("Neue Tor-Wege – alle Tabs geschlossen") }
                    showMenu = false
                }
            }
            Spacer(Modifier.height(10.dp))
            PillButton("Im echten Tor Browser öffnen", modifier = Modifier.fillMaxWidth()) {
                showMenu = false
                val u = if (tab.started) tab.url else "https://duckduckgo.com"
                try {
                    App.suppressLock = true
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).setPackage("org.torproject.torbrowser").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    App.suppressLock = false
                    App.toast("Tor Browser ist nicht installiert")
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (torMode) "Tor versteckt deine IP-Adresse. Für höchste Anonymität (Schutz vor Wiedererkennung) nutze den echten Tor Browser."
                else "Tipp: Mit einem VPN wie Proton VPN (kostenlos, auch ohne Konto) läuft auch dieser Browser automatisch darüber.",
                style = G.footnote.copy(color = G.faint), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.navigationBarsPadding())
        }

        // ── Werbeblocker ──
        Sheet(adSheet, { adSheet = false }) {
            val host = hostOf(tab.url).removePrefix("www.")
            SheetHeader(
                Ic.shield, "Werbeblocker",
                if (!adOn) "Ausgeschaltet."
                else if (tab.started && host.isNotEmpty() && host !in adOffSites) "$blockedCount Werbe- und Tracker-Anfragen auf dieser Seite blockiert."
                else "Blockiert Werbung und Tracker mit den Listen von uBlock und Brave (EasyList, EasyPrivacy)."
            )
            Column(Modifier.fillMaxWidth().glass(sq(20)).padding(horizontal = 14.dp, vertical = 2.dp)) {
                SettingRow("Werbeblocker", "${app.glass.tresor.browser.AdBlock.count} bekannte Werbe- und Tracker-Adressen") {
                    GlassSwitch(adOn, "Werbeblocker") { adOn = it; App.prefs.edit().putBoolean("adblock", it).apply() }
                }
                if (adOn && host.isNotEmpty()) {
                    SettingRow("Für $host ausschalten", "Falls die Seite sonst nicht richtig funktioniert", showDivider = true) {
                        GlassSwitch(host in adOffSites, "Für diese Seite aus") { off ->
                            adOffSites = if (off) adOffSites + host else adOffSites - host
                            BrowserData.saveAdOffSites(vault, adOffSites, area)
                            tab.web?.reload()
                        }
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }

        // ── Favoriten ──
        Sheet(showBookmarks, { showBookmarks = false }) {
            SheetHeader(Ic.note, "Favoriten", if (bookmarks.isEmpty()) "Noch keine Favoriten. Tippe auf + in der Leiste." else if (vault != null) "Verschlüsselt gespeichert." else null)
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                bookmarks.forEach { b ->
                    Row(
                        Modifier.fillMaxWidth().pressable(sq(16), onLongClick = {
                            bookmarks = bookmarks - b
                            val list = bookmarks
                            scope.launch(Dispatchers.IO) { BrowserData.saveBookmarks(vault, list, area) }
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
            Spacer(Modifier.navigationBarsPadding())
        }

        // ── Bereiche (nur im Tresor) ──
        if (vault != null) {
            Sheet(showAreas, { showAreas = false }) {
                SheetHeader(Ic.globe, "Browser-Bereiche", "Jeder Bereich hat eigene Tabs und Favoriten. Bereiche lassen sich wie Ordner schützen und verstecken.")
                Column(Modifier.fillMaxWidth().glass(sq(20)).padding(6.dp)) {
                    AreaRow("Standard", null, area == null) { showAreas = false; if (area != null) switchArea(null) }
                    BrowserAreas.visible(vault).forEach { f ->
                        val open = vault.isFolderUnlocked(f.id)
                        AreaRow(f.name, if (!open) Ic.lock else if (f.hidden) Ic.eyeOff else null, area == f.id) {
                            showAreas = false
                            if (!open) unlockArea = f else if (area != f.id) switchArea(f.id)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                PillButton("Neuer Bereich", icon = Ic.plus, modifier = Modifier.fillMaxWidth()) { showAreas = false; newAreaName = ""; newArea = true }
                if (area != null) {
                    Spacer(Modifier.height(10.dp))
                    PillButton("Schutz & Verstecken", icon = Ic.lock, modifier = Modifier.fillMaxWidth()) { showAreas = false; App.nav.push(Screen.FolderSettings(area)) }
                }
                Spacer(Modifier.height(8.dp))
                Text("Lange auf den Bereichsnamen drücken öffnet versteckte Bereiche per Code.", style = G.footnote.copy(color = G.faint), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.navigationBarsPadding())
            }
            Sheet(newArea, { newArea = false }) {
                SheetHeader(Ic.plus, "Neuer Bereich", "Zum Beispiel „Privat“. Eigene Tabs und Favoriten, getrennt vom Rest.")
                GlassField(newAreaName, { newAreaName = it.take(40) }, "Name", autoFocus = newArea)
                Spacer(Modifier.height(12.dp))
                PillButton("Anlegen", primary = true, enabled = newAreaName.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    val f = BrowserAreas.create(vault, newAreaName)
                    newArea = false
                    switchArea(f.id)
                }
                Spacer(Modifier.navigationBarsPadding())
            }
            FolderUnlockSheet(vault, unlockArea, { unlockArea = null }) { f -> unlockArea = null; switchArea(f.id) }
            FolderCodeSheet(vault, FolderKind.BROWSER, areaCode, { areaCode = false }) { opened ->
                opened.firstOrNull()?.let { switchArea(it) }
            }
        }
    }
}

@Composable
private fun AreaRow(name: String, icon: ImageVector?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(sq(14)) { onClick() }.padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Ic.globe, null, tint = G.dim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(name, style = G.headline.copy(fontSize = 16.sp), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (icon != null) Icon(icon, null, tint = G.dim, modifier = Modifier.size(16.dp))
        if (selected) { Spacer(Modifier.width(8.dp)); Icon(Ic.check, "gewählt", tint = G.accentText, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun StartPage(bookmarks: List<Bookmark>, disguise: Boolean, tor: Boolean, onOpen: (Bookmark) -> Unit, onSearch: () -> Unit) {
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

        if (!disguise) {
            TipCard("browser", "Mehr Privatsphäre", "Menü ⋯ → „Über Tor surfen“ versteckt deine IP. Ein VPN wie Proton VPN wirkt automatisch auch hier. Tippe oben auf den Bereichsnamen für eigene, geschützte Bereiche.")
            Row(Modifier.fillMaxWidth().glass(sq(22)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.shield, null, tint = if (tor) Color(0xFFB98CFF) else G.mint, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(if (tor) "Tor-Modus" else "Kein Verlauf, keine Spuren", style = G.headline.copy(fontSize = 15.sp))
                    Text(
                        if (tor) "Alle Seiten laufen über Tor – Webseiten sehen deine IP nicht. .onion-Adressen funktionieren."
                        else "Cookies und Website-Daten werden beim Sperren gelöscht. Downloads landen verschlüsselt im Tresor.",
                        style = G.footnote
                    )
                }
            }
            Text(
                if (tor) "Für höchste Anonymität (Schutz vor Wiedererkennung) nutze den echten Tor Browser – Menü ⋯."
                else "Webseiten sehen deine normale Internet-Adresse. Für mehr: Menü ⋯ → „Über Tor surfen“.",
                style = G.footnote.copy(color = G.faint), modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
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
