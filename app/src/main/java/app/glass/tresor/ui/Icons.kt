package app.glass.tresor.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Eigene Linien-Icons (24er Raster), damit alles einheitlich wie im Entwurf aussieht. */
object Ic {
    private fun stroke(name: String, d: String, width: Float = 2f) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = addPathNodes(d),
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()

    private fun filled(name: String, d: String) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = addPathNodes(d),
            fill = SolidColor(Color.White),
        ).build()

    val lock = stroke("lock", "M7 11h10a3 3 0 0 1 3 3v4a3 3 0 0 1 -3 3H7a3 3 0 0 1 -3 -3v-4a3 3 0 0 1 3 -3z M8 11V7a4 4 0 0 1 8 0v4")
    val unlock = stroke("unlock", "M7 11h10a3 3 0 0 1 3 3v4a3 3 0 0 1 -3 3H7a3 3 0 0 1 -3 -3v-4a3 3 0 0 1 3 -3z M8 11V7a4 4 0 0 1 7.5 -2")
    val settings = stroke("settings", "M4 7h9 M17 7h3 M15 5v4 M4 17h3 M11 17h9 M9 15v4")
    val search = stroke("search", "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0 -14z M20 20l-3.5 -3.5")
    val photo = stroke("photo", "M6 4h12a3 3 0 0 1 3 3v10a3 3 0 0 1 -3 3H6a3 3 0 0 1 -3 -3V7a3 3 0 0 1 3 -3z M21 16l-5 -5l-9 9 M9 8a2 2 0 1 0 0 4a2 2 0 1 0 0 -4z")
    val video = stroke("video", "M6 6h7a3 3 0 0 1 3 3v6a3 3 0 0 1 -3 3H6a3 3 0 0 1 -3 -3V9a3 3 0 0 1 3 -3z M16 10l5 -3v10l-5 -3")
    val folder = stroke("folder", "M3 7a2 2 0 0 1 2 -2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2z")
    val note = stroke("note", "M14 3H6a2 2 0 0 0 -2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2 -2V9z M14 3v6h6 M8 13h8 M8 17h5")
    val file = stroke("file", "M14 3H6a2 2 0 0 0 -2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2 -2V9z M14 3v6h6")
    val shield = stroke("shield", "M12 3L4 6v6c0 5 3.5 8 8 9c4.5 -1 8 -4 8 -9V6z M9 12l2 2l4 -4")
    val back = stroke("back", "M15 18l-6 -6l6 -6", 2.2f)
    val forward = stroke("forward", "M9 18l6 -6l-6 -6", 2.2f)
    val plus = stroke("plus", "M12 5v14 M5 12h14", 2.2f)
    val camera = stroke("camera", "M4 8h3l2 -3h6l2 3h3a1 1 0 0 1 1 1v9a1 1 0 0 1 -1 1H4a1 1 0 0 1 -1 -1V9a1 1 0 0 1 1 -1z M12 9.5a3.5 3.5 0 1 0 0 7a3.5 3.5 0 1 0 0 -7z")
    val grid = stroke("grid", "M5 3h3a2 2 0 0 1 2 2v3a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2V5a2 2 0 0 1 2 -2z M16 3h3a2 2 0 0 1 2 2v3a2 2 0 0 1 -2 2h-3a2 2 0 0 1 -2 -2V5a2 2 0 0 1 2 -2z M5 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2v-3a2 2 0 0 1 2 -2z M16 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1 -2 2h-3a2 2 0 0 1 -2 -2v-3a2 2 0 0 1 2 -2z")
    val fingerprint = stroke("fingerprint", "M12 11v3 M8.5 9.5a4.5 4.5 0 0 1 7 3.5c0 2 -0.5 4 -1.5 6 M6 12a6 6 0 0 1 11 -3.3 M9 14c0 2 -0.4 3.6 -1.2 5 M18 13c0 2.2 -0.4 4.3 -1.1 6", 1.8f)
    val eye = stroke("eye", "M2 12s4 -7 10 -7s10 7 10 7s-4 7 -10 7S2 12 2 12z M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0 -6z")
    val eyeOff = stroke("eyeOff", "M3 3l18 18 M10.6 6.1A10 10 0 0 1 12 6c5 0 9 6 9 6a17 17 0 0 1 -2.7 3.4 M6.6 7.6C4.4 9.1 3 12 3 12s4 6 9 6c1.4 0 2.7 -0.4 3.9 -1")
    val trash = stroke("trash", "M4 7h16 M10 11v6 M14 11v6 M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2 -2l1 -12 M9 7V4h6v3")
    val export = stroke("export", "M12 3v12 M7 8l5 -5l5 5 M5 14v5a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2v-5")
    val move = stroke("move", "M3 7a2 2 0 0 1 2 -2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2z M10 13h6 M13 10l3 3l-3 3")
    val check = stroke("check", "M5 12l5 5l9 -10", 3f)
    val close = stroke("close", "M6 6l12 12 M18 6L6 18", 2.2f)
    val backspace = stroke("backspace", "M9 5h11a1 1 0 0 1 1 1v12a1 1 0 0 1 -1 1H9l-6 -7z M12 9l6 6 M18 9l-6 6", 1.8f)
    val refresh = stroke("refresh", "M20 12a8 8 0 1 1 -2.3 -5.6 M20 4v5h-5")
    val edit = stroke("edit", "M4 20h4L19 9a2.8 2.8 0 0 0 -4 -4L4 16z M13.5 6.5l4 4")
    val calc = stroke("calc", "M8 3h8a3 3 0 0 1 3 3v12a3 3 0 0 1 -3 3H8a3 3 0 0 1 -3 -3V6a3 3 0 0 1 3 -3z M8 7h8 M8.5 12h0.01 M12 12h0.01 M15.5 12h0.01 M8.5 16h0.01 M12 16h0.01 M15.5 16h0.01")
    val calendar = stroke("calendar", "M6 5h12a3 3 0 0 1 3 3v10a3 3 0 0 1 -3 3H6a3 3 0 0 1 -3 -3V8a3 3 0 0 1 3 -3z M3 10h18 M8 3v4 M16 3v4")
    val checklist = stroke("checklist", "M4 7l2 2l3 -3 M4 15l2 2l3 -3 M13 8h7 M13 16h7")
    val news = stroke("news", "M6 4h12a3 3 0 0 1 3 3v10a3 3 0 0 1 -3 3H6a3 3 0 0 1 -3 -3V7a3 3 0 0 1 3 -3z M7 9h10 M7 13h10 M7 17h6")
    val keypad = filled("keypad", "M6 6.4a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0 -3.2z M12 6.4a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0 -3.2z M18 6.4a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0 -3.2z M6 12.4a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0 -3.2z M12 12.4a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0 -3.2z M18 12.4a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0 -3.2z M12 18.4a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0 -3.2z")
    val keyboard = stroke("keyboard", "M5 6h14a3 3 0 0 1 3 3v6a3 3 0 0 1 -3 3H5a3 3 0 0 1 -3 -3V9a3 3 0 0 1 3 -3z M6 10h0.01 M10 10h0.01 M14 10h0.01 M18 10h0.01 M7 14h10")
    val play = filled("play", "M8 5v14l11 -7z")
    val select = stroke("select", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0 -18z M8 12l3 3l5 -6")
    val dots = filled("dots", "M5 10.4a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0 -3.2z M12 10.4a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0 -3.2z M19 10.4a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0 -3.2z")
    val globe = stroke("globe", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0 -18z M3 12h18 M12 3c2.5 2.6 3.8 5.6 3.8 9s-1.3 6.4 -3.8 9 M12 3c-2.5 2.6 -3.8 5.6 -3.8 9s1.3 6.4 3.8 9")
    val chat = stroke("chat", "M5 5h14a2 2 0 0 1 2 2v9a2 2 0 0 1 -2 2H10l-5 4v-4a2 2 0 0 1 -2 -2V7a2 2 0 0 1 2 -2z")
    val qr = stroke("qr", "M4 4h6v6H4z M14 4h6v6h-6z M4 14h6v6H4z M14 14h2v2h-2z M18 14h2 M14 18h2 M18 18h2v2 M16 16h2v2")
    val info = stroke("info", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0 -18z M12 11v6 M12 7.5h0.01")
    val send = filled("send", "M3.4 20.4L21 12L3.4 3.6L3.4 10L15 12L3.4 14z")
    val clock = stroke("clock", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0 -18z M12 7v5l3 2")
    val copy = stroke("copy", "M9 9h10a1 1 0 0 1 1 1v10a1 1 0 0 1 -1 1H9a1 1 0 0 1 -1 -1V10a1 1 0 0 1 1 -1z M5 15H4a1 1 0 0 1 -1 -1V4a1 1 0 0 1 1 -1h10a1 1 0 0 1 1 1v1")
    val person = stroke("person", "M12 4a4 4 0 1 0 0 8a4 4 0 1 0 0 -8z M4 20c0.8 -3.5 4 -6 8 -6s7.2 2.5 8 6")

    // Sprachnachrichten und Anrufe
    val phone = stroke("phone", "M5 4h3l1.5 4.5l-2 1.5a11 11 0 0 0 6.5 6.5l1.5 -2l4.5 1.5v3a2 2 0 0 1 -2 2A16 16 0 0 1 3 6a2 2 0 0 1 2 -2z")
    val hangup = filled("hangup", "M12 9c-3.2 0 -6.2 0.9 -8.6 2.5c-0.9 0.6 -1.1 1.8 -0.5 2.7l1 1.4c0.5 0.7 1.4 0.9 2.2 0.6l2.4 -1c0.6 -0.3 1 -0.9 1 -1.6v-1.4c1.6 -0.4 3.4 -0.4 5 0v1.4c0 0.7 0.4 1.3 1 1.6l2.4 1c0.8 0.3 1.7 0.1 2.2 -0.6l1 -1.4c0.6 -0.9 0.4 -2.1 -0.5 -2.7C18.2 9.9 15.2 9 12 9z")
    val mic = stroke("mic", "M12 3a3 3 0 0 1 3 3v6a3 3 0 0 1 -6 0V6a3 3 0 0 1 3 -3z M5 11a7 7 0 0 0 14 0 M12 18v3")
    val micOff = stroke("micOff", "M3 3l18 18 M15 10V6a3 3 0 0 0 -5.7 -1.3 M9 9v3a3 3 0 0 0 5 2.2 M5 11a7 7 0 0 0 11.2 5.6 M19 11a7 7 0 0 1 -0.6 2.8 M12 18v3")
    val speaker = stroke("speaker", "M4 9h4l5 -4v14l-5 -4H4z M16.5 8.5a5 5 0 0 1 0 7 M19 6a8.5 8.5 0 0 1 0 12")
    val pause = filled("pause", "M7 5h3.5v14H7z M13.5 5H17v14h-3.5z")
    val stop = filled("stop", "M7 7h10v10H7z")
    val chevronDown = stroke("chevronDown", "M6 9l6 6l6 -6")
    val flash = stroke("flash", "M13 3L5 13h6l-1 8l8 -10h-6z")

    // Musik
    val music = stroke("music", "M9 18V6l11 -2v12 M9 18a3 3 0 1 1 -6 0a3 3 0 1 1 6 0z M20 16a3 3 0 1 1 -6 0a3 3 0 1 1 6 0z")
    val next = filled("next", "M5 5.5v13l9 -6.5z M14 5.5v13l7 -6.5z")
    val prev = filled("prev", "M19 5.5v13l-9 -6.5z M10 5.5v13l-7 -6.5z")
    val shuffle = stroke("shuffle", "M3 7h3c4 0 6 10 10 10h4 M17 14l3 3l-3 3 M3 17h3c1.6 0 2.8 -1.6 3.9 -3.5 M13.2 9.3C14 8 15 7 16 7h4 M17 4l3 3l-3 3")
    val repeat = stroke("repeat", "M4 11V9a3 3 0 0 1 3 -3h13 M17 3l3 3l-3 3 M20 13v2a3 3 0 0 1 -3 3H4 M7 21l-3 -3l3 -3")

    // Wetter
    val sun = stroke("sun", "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0 -8z M12 2v2 M12 20v2 M4.9 4.9l1.4 1.4 M17.7 17.7l1.4 1.4 M2 12h2 M20 12h2 M4.9 19.1l1.4 -1.4 M17.7 6.3l1.4 -1.4")
    val moon = stroke("moon", "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z")
    val cloud = stroke("cloud", "M7 18h10a4 4 0 0 0 0.6 -7.95A6 6 0 0 0 6.1 9.1A4.5 4.5 0 0 0 7 18z")
    val partly = stroke("partly", "M9 4.5a3.5 3.5 0 0 1 4.9 3.4 M4.2 6.2l1 1 M8.5 2v1.2 M3 10h1.3 M8 20h9a3.5 3.5 0 0 0 0.5 -6.96A5 5 0 0 0 8 12.3A3.9 3.9 0 0 0 8 20z")
    val rain = stroke("rain", "M7 14h10a4 4 0 0 0 0.6 -7.95A6 6 0 0 0 6.1 5.1A4.5 4.5 0 0 0 7 14z M8 17l-1 3 M12 17l-1 3 M16 17l-1 3")
    val snow = stroke("snow", "M7 14h10a4 4 0 0 0 0.6 -7.95A6 6 0 0 0 6.1 5.1A4.5 4.5 0 0 0 7 14z M8 18h0.01 M12 20h0.01 M16 18h0.01 M10 22h0.01 M14 22h0.01", 2.4f)
    val storm = stroke("storm", "M7 14h10a4 4 0 0 0 0.6 -7.95A6 6 0 0 0 6.1 5.1A4.5 4.5 0 0 0 7 14z M13 14l-3 4h4l-3 4")
    val fog = stroke("fog", "M4 9h16 M6 13h12 M4 17h16 M8 5h8")
    val location = stroke("location", "M12 21s-7 -6.2 -7 -11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21z M12 7a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0 -5z")
    val wind = stroke("wind", "M3 8h11a3 3 0 1 0 -3 -3 M3 12h15a3 3 0 1 1 -3 3 M3 16h7")
    val drop = stroke("drop", "M12 3s6 6.5 6 11a6 6 0 0 1 -12 0c0 -4.5 6 -11 6 -11z")
}
