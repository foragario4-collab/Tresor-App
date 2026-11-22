package app.glass.tresor.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.glass.tresor.App
import app.glass.tresor.ui.G
import app.glass.tresor.ui.Haptic
import app.glass.tresor.ui.Ic
import app.glass.tresor.ui.Pill
import app.glass.tresor.ui.PillButton
import app.glass.tresor.ui.fileUri
import app.glass.tresor.ui.formatDuration
import app.glass.tresor.ui.glass
import app.glass.tresor.ui.pressable
import app.glass.tresor.vault.Item
import app.glass.tresor.vault.ItemType
import app.glass.tresor.vault.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * Eigene Kamera: Fotos gehen direkt aus dem Speicher verschlüsselt in den Tresor – keine fremde Kamera-App,
 * keine Galerie, keine Datei. Ortsdaten (EXIF) werden dabei automatisch entfernt.
 * Videos liegen während der Aufnahme kurz im privaten App-Speicher und werden danach verschlüsselt und überschrieben.
 */
@Composable
fun CameraScreen(fid: String, startVideo: Boolean) {
    val vault = App.vault ?: return
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var videoMode by remember { mutableStateOf(startVideo) }
    var front by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recStart by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    var saved by remember { mutableStateOf(0) }
    fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    var camOk by remember { mutableStateOf(has(Manifest.permission.CAMERA)) }

    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        App.suppressLock = false
        camOk = r[Manifest.permission.CAMERA] == true || has(Manifest.permission.CAMERA)
    }
    LaunchedEffect(Unit) {
        if (!camOk || !has(Manifest.permission.RECORD_AUDIO)) {
            App.suppressLock = true
            perms.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    val controller = remember {
        LifecycleCameraController(ctx).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
    }
    DisposableEffect(camOk) {
        if (camOk) try { controller.bindToLifecycle(owner) } catch (_: Exception) {}
        onDispose {
            try { recording?.stop() } catch (_: Exception) {}
            try { controller.unbind() } catch (_: Exception) {}
        }
    }
    LaunchedEffect(front) {
        controller.cameraSelector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
    }
    LaunchedEffect(flash) {
        controller.imageCaptureFlashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }
    LaunchedEffect(recording) {
        while (recording != null) { now = System.currentTimeMillis(); delay(500) }
    }

    fun takePhoto() {
        if (busy) return
        busy = true
        controller.takePicture(ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rot = image.imageInfo.rotationDegrees
                val buf = image.planes[0].buffer
                val raw = ByteArray(buf.remaining()).also { buf.get(it) }
                image.close()
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { savePhoto(vault, fid, raw, rot, front) }
                    busy = false
                    if (ok) saved++ else App.toast("Foto konnte nicht gespeichert werden")
                }
            }

            override fun onError(exception: ImageCaptureException) {
                busy = false
                App.toast("Foto fehlgeschlagen")
            }
        })
    }

    fun toggleVideo() {
        val r = recording
        if (r != null) { r.stop(); recording = null; return }
        val dir = File(ctx.cacheDir, "cam").apply { mkdirs() }
        val file = File(dir, "rec_${System.currentTimeMillis()}.mp4")
        val audio = if (has(Manifest.permission.RECORD_AUDIO)) AudioConfig.create(true) else AudioConfig.AUDIO_DISABLED
        try {
            recStart = System.currentTimeMillis()
            recording = controller.startRecording(FileOutputOptions.Builder(file).build(), audio, ContextCompat.getMainExecutor(ctx)) { ev ->
                if (ev is VideoRecordEvent.Finalize) {
                    recording = null
                    busy = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            try {
                                if (ev.hasError() && file.length() == 0L) false
                                else Media.importUri(ctx, vault, fid, fileUri(ctx, file), "Video_${System.currentTimeMillis()}.mp4") != null
                            } catch (e: Exception) {
                                false
                            } finally {
                                shred(file)
                            }
                        }
                        busy = false
                        if (ok) saved++ else App.toast("Video konnte nicht gespeichert werden")
                    }
                }
            }
        } catch (e: Exception) {
            recording = null
            shred(file)
            App.toast("Videoaufnahme nicht möglich")
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (camOk) {
            AndroidView(
                factory = { c -> PreviewView(c).apply { this.controller = controller; scaleType = PreviewView.ScaleType.FILL_CENTER } },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(Modifier.align(Alignment.Center).padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Ic.camera, null, tint = G.dim, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text("Kamera-Zugriff fehlt", style = G.headline)
                Spacer(Modifier.height(12.dp))
                PillButton("Erlauben", primary = true) { App.suppressLock = true; perms.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }
            }
        }

        // Oben: Schließen, Blitz, Zähler
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CamRound(Ic.close, "Schließen") { App.nav.pop() }
            Spacer(Modifier.weight(1f))
            if (recording != null) {
                Row(Modifier.glass(Pill).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(G.red))
                    Spacer(Modifier.width(8.dp))
                    Text(formatDuration(now - recStart), style = G.callout.copy(color = Color.White))
                }
            } else if (saved > 0) {
                Text("$saved verschlüsselt", style = G.footnote.copy(color = Color.White), modifier = Modifier.glass(Pill).padding(horizontal = 14.dp, vertical = 8.dp))
            }
            Spacer(Modifier.weight(1f))
            if (!videoMode) CamRound(Ic.flash, if (flash) "Blitz an" else "Blitz aus", active = flash) { flash = !flash }
            else Spacer(Modifier.size(48.dp))
        }

        // Unten: Modus, Auslöser, Kamera wechseln
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 26.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.glass(Pill).padding(4.dp)) {
                ModeTab("Foto", !videoMode, enabled = recording == null) { videoMode = false }
                ModeTab("Video", videoMode, enabled = recording == null) { videoMode = true }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Spacer(Modifier.size(52.dp))
                Box(
                    Modifier.size(78.dp).border(4.dp, Color.White, CircleShape).padding(7.dp)
                        .pressable(CircleShape, scaleTo = 0.9f, highlight = 0f, haptic = Haptic.CONFIRM, enabled = camOk && !busy) {
                            if (videoMode) toggleVideo() else takePhoto()
                        }
                        .clip(if (recording != null) app.glass.tresor.ui.sq(10) else CircleShape)
                        .background(if (videoMode) G.red else Color.White)
                )
                CamRound(Ic.refresh, "Kamera wechseln", enabled = recording == null) { front = !front }
            }
        }
    }
}

@Composable
private fun CamRound(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).pressable(CircleShape, scaleTo = 0.88f, enabled = enabled) { onClick() }
            .clip(CircleShape).background(if (active) Color.White else Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = if (active) Color.Black else Color.White, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.pressable(Pill, enabled = enabled, haptic = Haptic.NONE) { onClick() }
            .clip(Pill).background(if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(label, style = G.callout.copy(color = if (selected) Color(0xFFFFD60A) else Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp))
    }
}

/** Foto drehen, Metadaten (Ort, Gerät) entfernen, verschlüsselt speichern. Nichts landet als Datei. */
private fun savePhoto(vault: app.glass.tresor.vault.Vault, fid: String, jpeg: ByteArray, rotation: Int, mirror: Boolean): Boolean = try {
    val src = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: throw IllegalStateException()
    val m = Matrix().apply {
        postRotate(rotation.toFloat())
        if (mirror) postScale(-1f, 1f)
    }
    val bmp = if (rotation != 0 || mirror) Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true) else src
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 94, out)
    val data = out.toByteArray()
    val longest = maxOf(bmp.width, bmp.height)
    val small = Bitmap.createScaledBitmap(bmp, bmp.width * 480 / longest, bmp.height * 480 / longest, true)
    val thumb = Media.jpeg(small)
    val now = System.currentTimeMillis()
    val name = "Foto_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.GERMANY).format(java.util.Date(now)) + ".jpg"
    val item = Item(vault.newItemId(), name, "image/jpeg", ItemType.IMAGE, data.size.toLong(), now, now, bmp.width, bmp.height, hasThumb = true)
    vault.importStream(fid, ByteArrayInputStream(data), item, thumb)
    if (small !== bmp) small.recycle()
    if (bmp !== src) bmp.recycle()
    src.recycle()
    java.util.Arrays.fill(data, 0)
    true
} catch (e: Exception) {
    false
}

/** Temporäre Videodatei überschreiben und löschen. */
private fun shred(f: File) {
    try {
        if (f.exists()) {
            RandomAccessFile(f, "rw").use { raf ->
                val zero = ByteArray(64 * 1024)
                var left = raf.length()
                while (left > 0) { val n = minOf(left, zero.size.toLong()).toInt(); raf.write(zero, 0, n); left -= n }
            }
        }
    } catch (_: Exception) {
    }
    f.delete()
}
