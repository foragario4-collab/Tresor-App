package app.glass.tresor.ui.screens

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import app.glass.tresor.ui.Auth
import app.glass.tresor.ui.GlassField
import app.glass.tresor.ui.Ic
import app.glass.tresor.ui.PillButton
import app.glass.tresor.ui.Sheet
import app.glass.tresor.ui.SheetHeader
import app.glass.tresor.vault.FolderKind
import app.glass.tresor.vault.Protection
import app.glass.tresor.vault.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Geschützten Container (Kategorie) öffnen – Passwort, Tresor-PIN oder Fingerabdruck/Gesicht. */
@Composable
fun BoxScope.CategoryUnlockSheet(vault: Vault, kind: FolderKind?, onDismiss: () -> Unit, onUnlocked: (FolderKind) -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    val scope = rememberCoroutineScope()
    var code by remember(kind) { mutableStateOf("") }
    var err by remember(kind) { mutableStateOf<String?>(null) }
    var busy by remember(kind) { mutableStateOf(false) }
    val prot = kind?.let { vault.categoryProtection(it) } ?: Protection.NONE
    val title = kind?.let { categoryTitle(it) } ?: ""

    fun bio() {
        val k = kind ?: return
        val c = vault.categorySysCipher(k)
        if (c == null) { err = "Systemsperre geht nicht mehr – bitte den Tresor-Code eingeben."; return }
        Auth.prompt(activity, "„$title“ öffnen", null, c, onSuccess = { ok ->
            scope.launch {
                val done = withContext(Dispatchers.Default) { vault.unlockCategorySys(k, ok) }
                if (done) onUnlocked(k) else err = "Hat nicht geklappt."
            }
        }, onFail = {})
    }

    LaunchedEffect(kind) {
        if (kind != null && prot == Protection.SYSTEM && Auth.available(activity)) bio()
    }

    Sheet(kind != null, onDismiss) {
        val k = kind ?: return@Sheet
        SheetHeader(
            Ic.lock, "„$title“ ist geschützt",
            err ?: when (prot) {
                Protection.PASSWORD -> "Passwort des Containers eingeben."
                Protection.SYSTEM -> "Fingerabdruck / Gesicht – oder den Tresor-Code."
                else -> "Tresor-Code eingeben."
            }
        )
        GlassField(
            code, { code = it; err = null },
            if (prot == Protection.PASSWORD) "Passwort" else "Tresor-Code",
            password = true, numeric = prot != Protection.PASSWORD, autoFocus = prot != Protection.SYSTEM,
            onDone = {
                if (code.isNotEmpty() && !busy) {
                    busy = true
                    val c = code
                    scope.launch {
                        val ok = withContext(Dispatchers.Default) { vault.unlockCategory(k, c.toCharArray()) }
                        busy = false
                        if (ok) onUnlocked(k) else { err = "Falsch. Nochmal versuchen."; code = "" }
                    }
                }
            }
        )
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
            else PillButton("Öffnen", primary = true, enabled = code.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                busy = true
                val c = code
                scope.launch {
                    val ok = withContext(Dispatchers.Default) { vault.unlockCategory(k, c.toCharArray()) }
                    busy = false
                    if (ok) onUnlocked(k) else { err = "Falsch. Nochmal versuchen."; code = "" }
                }
            }
        }
        if (prot == Protection.SYSTEM && Auth.available(activity)) {
            Spacer(Modifier.height(10.dp))
            PillButton("Mit Fingerabdruck / Gesicht", icon = Ic.fingerprint, modifier = Modifier.fillMaxWidth()) { bio() }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
