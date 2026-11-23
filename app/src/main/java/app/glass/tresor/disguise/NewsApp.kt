package app.glass.tresor.disguise

import android.content.Intent
import android.net.Uri
import android.util.Xml
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import app.glass.tresor.App
import app.glass.tresor.ui.Blob
import app.glass.tresor.ui.G
import app.glass.tresor.ui.GlassBackground
import app.glass.tresor.ui.GlassIconButton
import app.glass.tresor.ui.Ic
import app.glass.tresor.ui.Pill
import app.glass.tresor.ui.PillButton
import app.glass.tresor.ui.Sheet
import app.glass.tresor.ui.TopBar
import app.glass.tresor.ui.glass
import app.glass.tresor.ui.pressable
import app.glass.tresor.ui.sq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val newsBlobs = listOf(
    Blob(Color(0xFF2F6BF6), -0.1f, 0.0f, 1.0f, 0.4f),
    Blob(Color(0xFF8B4FF0), 1.1f, 0.7f, 0.9f, 0.3f),
)

private data class Feed(val name: String, val url: String)
private data class Article(val title: String, val link: String, val text: String, val date: Date?, val source: String, val image: String? = null)

private val feeds = listOf(
    Feed("ORF", "https://rss.orf.at/news.xml"),
    Feed("Der Standard", "https://www.derstandard.at/rss"),
    Feed("Tagesschau", "https://www.tagesschau.de/index~rss2.xml"),
)

/**
 * Lädt echte Nachrichten per RSS. Das ist der einzige Netzwerk-Code der App –
 * er hat keinerlei Zugriff auf den Tresor.
 */
private fun fetch(feed: Feed): List<Article> {
    val c = app.glass.tresor.net.TorNet.open(feed.url)
    c.instanceFollowRedirects = true
    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) News")
    try {
        c.inputStream.use { input ->
            val p = Xml.newPullParser()
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            p.setInput(input, null)
            val out = mutableListOf<Article>()
            var inItem = false
            var title = ""; var link = ""; var desc = ""; var date = ""; var img = ""
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> when (p.name) {
                        "item", "entry" -> { inItem = true; title = ""; link = ""; desc = ""; date = ""; img = "" }
                        // Bilder: media:content / media:thumbnail / enclosure
                        "media:content", "media:thumbnail", "enclosure" -> if (inItem && img.isEmpty()) {
                            val u = p.getAttributeValue(null, "url") ?: ""
                            val type = p.getAttributeValue(null, "type") ?: p.getAttributeValue(null, "medium") ?: "image"
                            if (u.isNotEmpty() && type.startsWith("image")) img = u
                        }
                        "title" -> if (inItem) title = p.nextText()
                        "link" -> if (inItem) {
                            val href = p.getAttributeValue(null, "href")
                            link = href ?: p.nextText()
                        }
                        "description", "summary", "content:encoded", "content" -> if (inItem) {
                            val t = p.nextText()
                            if (img.isEmpty()) imgRegex.find(t)?.let { img = it.groupValues[1] }
                            if (desc.isEmpty()) desc = t
                        }
                        "pubDate", "dc:date", "updated", "published" -> if (inItem && date.isEmpty()) date = p.nextText()
                    }
                    XmlPullParser.END_TAG -> if (p.name == "item" || p.name == "entry") {
                        inItem = false
                        if (title.isNotBlank()) {
                            out += Article(
                                title.trim(), link.trim(),
                                HtmlCompat.fromHtml(desc, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().replace('￼', ' ').trim(),
                                parseDate(date.trim()), feed.name,
                                img.trim().replace("&amp;", "&").replace("http://", "https://").takeIf { it.startsWith("https://") }
                            )
                        }
                    }
                }
                ev = p.next()
            }
            return out
        }
    } finally {
        c.disconnect()
    }
}

private val imgRegex = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

private fun parseDate(s: String): Date? {
    if (s.isEmpty()) return null
    val patterns = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    for (pt in patterns) {
        try { return SimpleDateFormat(pt, Locale.ENGLISH).parse(s) } catch (_: Exception) {}
    }
    return null
}

private fun ago(d: Date?): String {
    d ?: return ""
    val min = (System.currentTimeMillis() - d.time) / 60_000
    return when {
        min < 1 -> "gerade eben"
        min < 60 -> "vor $min Min."
        min < 60 * 24 -> "vor ${min / 60} Std."
        else -> SimpleDateFormat("d. MMM", Locale.GERMANY).format(d)
    }
}

/** News-App mit echten Nachrichten. Geheim: den Titel „News“ lange drücken. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewsApp() {
    val ctx = LocalContext.current
    val view = LocalView.current
    var source by remember { mutableIntStateOf(App.dprefs.getInt("newsSrc", 0).coerceIn(0, feeds.lastIndex)) }
    var articles by remember { mutableStateOf<List<Article>?>(null) }
    var error by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<Article?>(null) }

    LaunchedEffect(source, reload) {
        articles = null; error = false
        val r = withContext(Dispatchers.IO) { try { fetch(feeds[source]) } catch (e: Exception) { null } }
        if (r == null) error = true else articles = r
    }

    GlassBackground(newsBlobs) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                TopBar(onBack = null, horizontalPadding = 0.dp) {
                    GlassIconButton(Ic.refresh, "Aktualisieren") { reload++ }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "News", style = G.largeTitle,
                    modifier = Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                        onClick = {},
                        onLongClick = {
                            openSecretLock()
                        }
                    )
                )
                Text(SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY).format(Date()), style = G.footnote)
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(feeds.size) { i ->
                        val sel = i == source
                        Box(
                            Modifier.height(38.dp).pressable(Pill) {
                                source = i; App.dprefs.edit().putInt("newsSrc", i).apply()
                            }.then(
                                if (sel) Modifier.clip(Pill).background(Color.White) else Modifier.glass(Pill)
                            ).padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(feeds[i].name, style = G.callout.copy(fontWeight = FontWeight.SemiBold, color = if (sel) Color(0xFF0B0D12) else Color.White))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            val list = articles
            when {
                error -> item {
                    Column(Modifier.fillMaxWidth().glass(sq(24)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Keine Verbindung", style = G.headline)
                        Spacer(Modifier.height(4.dp))
                        Text("Nachrichten konnten nicht geladen werden.", style = G.footnote)
                        Spacer(Modifier.height(14.dp))
                        PillButton("Erneut versuchen", height = 44.dp) { reload++ }
                    }
                }
                list == null -> item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                }
                else -> items(list, key = { it.link + it.title }) { a ->
                    Column(
                        Modifier.fillMaxWidth().pressable(sq(22)) { open = a }.glass(sq(22))
                    ) {
                        val img = a.image
                        if (img != null) NetImage(img, Modifier.fillMaxWidth().height(180.dp).clip(sq(22)))
                        Column(Modifier.padding(16.dp)) {
                        Text("${a.source} · ${ago(a.date)}".trimEnd(' ', '·'), style = G.caption)
                        Spacer(Modifier.height(4.dp))
                        Text(a.title, style = G.headline.copy(fontSize = 17.sp, lineHeight = 22.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (a.text.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(a.text, style = G.footnote, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        }
                    }
                }
            }
        }

        Sheet(open != null, { open = null }) {
            val a = open ?: return@Sheet
            a.image?.let { NetImage(it, Modifier.fillMaxWidth().height(200.dp).clip(sq(18))); Spacer(Modifier.height(12.dp)) }
            Text("${a.source} · ${ago(a.date)}".trimEnd(' ', '·'), style = G.caption)
            Spacer(Modifier.height(6.dp))
            Text(a.title, style = G.title3)
            Spacer(Modifier.height(10.dp))
            if (a.text.isNotBlank()) Text(a.text, style = G.callout.copy(color = Color.White.copy(alpha = 0.85f), lineHeight = 21.sp))
            Spacer(Modifier.height(18.dp))
            if (a.link.isNotBlank()) {
                PillButton("Ganzen Artikel lesen", primary = true, modifier = Modifier.fillMaxWidth()) {
                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.link))) } catch (_: Exception) {}
                }
            }
        }
    }
}

