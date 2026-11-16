package app.glass.tresor.browser

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Werbe- und Tracker-Blocker für den privaten Browser.
 * Nutzt dieselben öffentlichen Listen wie uBlock Origin und Brave (EasyList, EasyPrivacy):
 * GitHub lädt sie bei jedem Build frisch herunter (assets/adblock.txt). Dazu eine eingebaute Grundliste.
 * Blockiert wird auf Domain-Ebene – die Seite selbst lädt immer, nur Werbung und Tracker nicht.
 */
object AdBlock {
    @Volatile
    private var domains: Set<String> = emptySet()

    @Volatile
    var loaded = false
        private set

    val count get() = domains.size

    /** Einmal laden (im Hintergrund aufrufen). */
    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        val set = HashSet<String>(BASE.size * 2)
        BASE.forEach { set.add(it) }
        try {
            ctx.assets.open("adblock.txt").bufferedReader().useLines { lines ->
                lines.forEach { l ->
                    val d = l.trim().lowercase()
                    if (d.isNotEmpty() && !d.startsWith("#") && d.contains('.') && d.length < 120) set.add(d)
                }
            }
        } catch (_: Exception) {
            // Keine heruntergeladene Liste (z. B. lokaler Build) → nur Grundliste
        }
        domains = set
        loaded = true
    }

    /** Gehört der Host (oder eine übergeordnete Domain) zu Werbung/Trackern? */
    fun isBlocked(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        val d = domains
        var h = host.lowercase().trimEnd('.')
        while (true) {
            if (h in d) return true
            val i = h.indexOf('.')
            if (i < 0 || h.indexOf('.', i + 1) < 0) return false // bis zur Hauptdomain (z. B. example.com)
            h = h.substring(i + 1)
        }
    }

    /** Für WebViewClient.shouldInterceptRequest: leere Antwort statt Werbung. null = normal laden. */
    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        if (request.isForMainFrame) return null // die Seite selbst nie blockieren
        if (!isBlocked(request.url.host)) return null
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }

    /** Eingebaute Grundliste der verbreitetsten Werbe- und Tracking-Dienste. */
    private val BASE = listOf(
        // Google Werbung & Analyse
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com",
        "googletagmanager.com", "googletagservices.com", "adservice.google.com", "pagead2.googlesyndication.com",
        "2mdn.net", "app-measurement.com", "imasdk.googleapis.com",
        // Meta, Microsoft, Amazon, Yahoo, Yandex …
        "connect.facebook.net", "pixel.facebook.com", "an.facebook.com", "bat.bing.com", "clarity.ms",
        "amazon-adsystem.com", "aax.amazon-adsystem.com", "ads.yahoo.com", "analytics.yahoo.com", "advertising.com",
        "mc.yandex.ru", "an.yandex.ru", "ads.linkedin.com", "px.ads.linkedin.com", "analytics.tiktok.com",
        "ads-api.tiktok.com", "ads.twitter.com", "static.ads-twitter.com", "analytics.twitter.com", "ads.pinterest.com",
        "ct.pinterest.com", "sc-static.net", "tr.snapchat.com",
        // Werbenetzwerke
        "adnxs.com", "criteo.com", "criteo.net", "taboola.com", "outbrain.com", "pubmatic.com", "rubiconproject.com",
        "openx.net", "casalemedia.com", "adsrvr.org", "smartadserver.com", "adform.net", "teads.tv", "yieldmo.com",
        "media.net", "revcontent.com", "mgid.com", "zedo.com", "33across.com", "sharethrough.com", "triplelift.com",
        "indexww.com", "contextweb.com", "spotxchange.com", "springserve.com", "adcolony.com", "applovin.com",
        "unityads.unity3d.com", "inmobi.com", "vungle.com", "chartboost.com", "admob.com", "mopub.com", "bidswitch.net",
        "lijit.com", "sovrn.com", "gumgum.com", "undertone.com", "kargo.com", "adition.com", "yieldlab.net",
        "stroeer.io", "ströer.de", "adscale.de", "ligatus.com", "plista.com", "seedtag.com", "nativo.com",
        "propellerads.com", "popads.net", "popcash.net", "adsterra.com", "exoclick.com", "juicyads.com",
        "trafficjunky.net", "hilltopads.net", "adcash.com", "adtelligent.com", "smaato.net", "rlcdn.com",
        // Tracking & Messung
        "scorecardresearch.com", "quantserve.com", "quantcount.com", "moatads.com", "adsafeprotected.com",
        "doubleverify.com", "chartbeat.com", "chartbeat.net", "hotjar.com", "hotjar.io", "mixpanel.com",
        "segment.io", "cdn.segment.com", "api.segment.io", "amplitude.com", "fullstory.com", "mouseflow.com",
        "crazyegg.com", "luckyorange.com", "inspectlet.com", "newrelic.com", "nr-data.net", "bugsnag.com",
        "krxd.net", "bluekai.com", "demdex.net", "omtrdc.net", "everesttech.net", "exelator.com", "agkn.com",
        "mathtag.com", "tapad.com", "adsymptotic.com", "crwdcntrl.net", "lotame.com", "eyeota.net",
        "addthis.com", "sharethis.com", "branch.io", "app.link", "adjust.com", "appsflyer.com", "kochava.com",
        "onesignal.com", "pushwoosh.com", "ioam.de", "xiti.com", "atinternet.com", "webtrekk.net", "etracker.com",
        "matomo.cloud", "statcounter.com", "histats.com", "clicky.com", "woopra.com", "kissmetrics.com",
        "optimizely.com", "cdn.optimizely.com", "vwo.com", "visualwebsiteoptimizer.com", "permutive.com",
        "parsely.com", "dotmetrics.net", "gemius.pl", "hit.gemius.pl", "cxense.com", "piano.io", "tinypass.com",
    )
}
