package app.glass.tresor.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.glass.tresor.vault.Folder
import app.glass.tresor.vault.FolderKind
import app.glass.tresor.vault.Vault

/**
 * Geheime Browser-Bereiche: eigene Tabs und Favoriten, schützbar und versteckbar wie Ordner.
 * null = Standard-Bereich.
 */
object BrowserAreas {
    var selected by mutableStateOf<String?>(null)

    fun reset() {
        selected = null
    }

    /** Gewählter Bereich, falls noch offen – sonst Standard. */
    fun current(v: Vault): String? {
        val s = selected ?: return null
        if (v.folder(s) != null && v.isFolderUnlocked(s)) return s
        selected = null
        return null
    }

    fun name(v: Vault, area: String?): String = if (area == null) "Standard" else v.folder(area)?.name ?: "Bereich"

    fun visible(v: Vault): List<Folder> =
        v.folders.toList().filter { it.kind == FolderKind.BROWSER && (!it.hidden || it.id in v.revealed) }

    fun create(v: Vault, name: String): Folder = v.createFolder(name.trim().take(40).ifBlank { "Bereich" }, FolderKind.BROWSER)
}
