package app.glass.tresor.browser

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import app.glass.tresor.App
import app.glass.tresor.chat.ChatService
import app.glass.tresor.chat.ChatSpool

/**
 * Tor-Modus des Browsers: Alle Webseiten laufen über Tor (SOCKS5, Namensauflösung ebenfalls über Tor).
 * Solange Tor nicht bereit ist, lädt der Browser nichts – so geht nie etwas am Tor vorbei.
 */
object BrowserTor {
    /** Tor-Modus eingeschaltet (Einstellung). */
    var enabled by mutableStateOf(false)
        private set
    /** Weiterleitung über Tor ist aktiv – erst dann darf geladen werden. */
    var ready by mutableStateOf(false)
        private set

    /** Einheitliche Kennung wie beim Tor Browser für Android. */
    const val TOR_UA = "Mozilla/5.0 (Android 10; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0"

    val supported get() = WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)

    fun init() {
        enabled = App.prefs.getBoolean("browserTor", false)
    }

    fun turn(on: Boolean) {
        enabled = on
        App.prefs.edit().putBoolean("browserTor", on).apply()
        if (!on) release()
    }

    /** Tor starten (falls nötig) und die Weiterleitung setzen, sobald Tor bereit ist. Aufruf wiederholt. */
    fun ensure(ctx: Context) {
        if (!enabled || !supported) return
        ChatService.forBrowser = true
        if (!ChatService.running) ChatService.start(ctx)
        val port = ChatService.socksPort
        if (port <= 0 || ready) return
        try {
            val cfg = ProxyConfig.Builder().addProxyRule("socks5://127.0.0.1:$port").build()
            ProxyController.getInstance().setProxyOverride(cfg, ContextCompat.getMainExecutor(ctx)) { ready = true }
        } catch (e: Exception) {
            ready = false
        }
    }

    /** Weiterleitung aufheben und Tor beenden, wenn der Chat es nicht braucht. */
    fun release() {
        ready = false
        ChatService.forBrowser = false
        if (supported) try {
            ProxyController.getInstance().clearProxyOverride(ContextCompat.getMainExecutor(App.ctx)) {}
        } catch (_: Exception) {
        }
        if (ChatSpool.registry(App.ctx).isEmpty()) ChatService.stop(App.ctx)
    }

    /** Läuft gerade ein VPN (z. B. Proton VPN) für das ganze Handy? */
    fun vpnActive(ctx: Context): Boolean = try {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    } catch (e: Exception) {
        false
    }

    /** Wird vor jeder Seite ausgeführt: WebRTC abschalten (kann sonst die echte IP verraten). */
    const val ANTI_LEAK_JS = """(function(){try{
      ['RTCPeerConnection','webkitRTCPeerConnection','RTCDataChannel','RTCSessionDescription','RTCIceCandidate'].forEach(function(k){
        try{Object.defineProperty(window,k,{value:undefined,writable:false,configurable:false});}catch(e){}
      });
      try{Object.defineProperty(navigator,'mediaDevices',{value:undefined,configurable:false});}catch(e){}
    }catch(e){}})();"""
}
