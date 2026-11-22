package app.glass.tresor.vault

import org.json.JSONObject

/** CHAT = ein weiteres Chat-Konto (eigene Identität, eigene Kontakte), geschützt wie ein Ordner. */
/** BROWSER = ein geheimer Browser-Bereich (eigene Tabs/Favoriten), geschützt wie ein Ordner. */
enum class FolderKind { MEDIA, FILES, NOTES, CHAT, MUSIC, BROWSER }
enum class Protection { NONE, PIN, PASSWORD, SYSTEM }
enum class ItemType { IMAGE, VIDEO, FILE, NOTE, AUDIO }

data class Folder(
    val id: String,
    val name: String,
    val kind: FolderKind,
    val protection: Protection,
    val hidden: Boolean,
    val relock: Boolean,
    val created: Long,
    val wrapMk: String? = null,
    val wrapPw: String? = null,
    val wrapSys: String? = null,
    /** Ordner-Schlüssel, verschlüsselt mit dem Container-Schlüssel seiner Kategorie (falls die Kategorie geschützt ist). */
    val wrapCat: String? = null,
) {
    val isProtected get() = protection != Protection.NONE

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("kind", kind.name)
        put("prot", protection.name); put("hidden", hidden); put("relock", relock)
        put("created", created)
        wrapMk?.let { put("wMk", it) }
        wrapPw?.let { put("wPw", it) }
        wrapSys?.let { put("wSys", it) }
        wrapCat?.let { put("wCat", it) }
    }

    companion object {
        fun fromJson(o: JSONObject) = Folder(
            id = o.getString("id"),
            name = o.getString("name"),
            kind = FolderKind.valueOf(o.getString("kind")),
            protection = Protection.valueOf(o.getString("prot")),
            hidden = o.optBoolean("hidden"),
            relock = o.optBoolean("relock", true),
            created = o.optLong("created"),
            wrapMk = o.optString("wMk").ifEmpty { null },
            wrapPw = o.optString("wPw").ifEmpty { null },
            wrapSys = o.optString("wSys").ifEmpty { null },
            wrapCat = o.optString("wCat").ifEmpty { null },
        )
    }
}

data class Item(
    val id: String,
    val name: String,
    val mime: String,
    val type: ItemType,
    val size: Long,
    val created: Long,
    val modified: Long,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0,
    val hasThumb: Boolean = false,
    val artist: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("mime", mime); put("type", type.name)
        put("size", size); put("created", created); put("modified", modified)
        put("w", width); put("h", height); put("dur", durationMs); put("thumb", hasThumb)
        if (artist.isNotEmpty()) put("ar", artist)
    }

    companion object {
        fun fromJson(o: JSONObject) = Item(
            id = o.getString("id"),
            name = o.optString("name"),
            mime = o.optString("mime"),
            type = ItemType.valueOf(o.getString("type")),
            size = o.optLong("size"),
            created = o.optLong("created"),
            modified = o.optLong("modified"),
            width = o.optInt("w"),
            height = o.optInt("h"),
            durationMs = o.optLong("dur"),
            hasThumb = o.optBoolean("thumb"),
            artist = o.optString("ar"),
        )
    }
}
