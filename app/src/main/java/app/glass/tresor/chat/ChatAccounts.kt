package app.glass.tresor.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.glass.tresor.vault.Folder
import app.glass.tresor.vault.FolderKind
import app.glass.tresor.vault.Vault

/**
 * Mehrere Chat-Konten pro Tresor. Das Hauptkonto gibt es immer, weitere Konten sind
 * Ordner der Art CHAT – sie lassen sich wie Galerie-Ordner schützen und verstecken.
 */
object ChatAccounts {
    /** Gerade gewähltes Konto (Hauptkonto oder Ordner-ID). Wird beim Sperren zurückgesetzt. */
    var selected by mutableStateOf(ChatEngine.MAIN)

    fun reset() {
        selected = ChatEngine.MAIN
    }

    /** Engine des gewählten Kontos – fällt aufs Hauptkonto zurück, wenn das Konto nicht (mehr) offen ist. */
    fun engine(v: Vault): ChatEngine {
        val sel = selected
        if (sel != ChatEngine.MAIN) {
            val f = v.folder(sel)
            if (f != null && v.isFolderUnlocked(sel)) return ChatEngine.of(v, sel)
            selected = ChatEngine.MAIN
        }
        return ChatEngine.of(v, ChatEngine.MAIN)
    }

    /** Weitere Konten, die man gerade sehen darf (nicht versteckt oder per Code hervorgeholt). */
    fun visible(v: Vault): List<Folder> =
        v.folders.toList().filter { it.kind == FolderKind.CHAT && (!it.hidden || it.id in v.revealed) }

    fun create(v: Vault, name: String): Folder = v.createFolder(name.trim().take(40).ifBlank { "Konto" }, FolderKind.CHAT)
}
