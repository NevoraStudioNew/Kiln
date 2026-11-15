/*
 * Kiln — Bakar script jadi APK, langsung dari HP.
 * Open source di bawah Lisensi MIT.
 * Copyright (c) 2026 Nevora Studio
 * Saluran resmi: https://whatsapp.com/channel/0029VbDwlX6EawdkN0IZm713
 */

package id.nevora.kiln.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import id.nevora.kiln.*
import id.nevora.kiln.data.*
import android.net.Uri
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import id.nevora.kiln.KilnViewModel
import java.io.ByteArrayInputStream

@Composable
fun EditorScreen(vm: KilnViewModel, onBack: () -> Unit) {
    val file = vm.openFile ?: return
    val repo = vm.repo ?: return
    val kind = fileKind(file.name)
    val loaded = vm.fileText
    var text by remember(file.path, loaded) { mutableStateOf(loaded ?: "") }
    val editable = (kind == FileKind.Html || kind == FileKind.Text) && vm.fileError == null
    val dirty = editable && loaded != null && text != loaded
    var mode by remember { mutableIntStateOf(0) }
    var exitDlg by remember { mutableStateOf(false) }
    var commitDlg by remember { mutableStateOf(false) }
    BackHandler { if (dirty) exitDlg = true else onBack() }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ if (dirty) exitDlg = true else onBack() }) { Icon(Icons.Rounded.ArrowBack, "Kembali", tint = K.Text) }
            Text(file.name + if (dirty) " •" else "", color = K.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (kind == FileKind.Html && editable) {
                FilterChip(mode == 0, { mode = 0 }, { Text("Kode") })
                HGap(6)
                FilterChip(mode == 1, { mode = 1 }, { Text("Preview") })
                HGap(6)
            }
            if (editable) {
                AnimatedVisibility(dirty, enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(), exit = scaleOut() + fadeOut()) {
                    IconButton({ commitDlg = true }, enabled = !vm.saving) {
                        if (vm.saving) CircularProgressIndicator(Modifier.size(20.dp), color = K.Orange, strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Save, "Simpan", tint = K.Orange)
                    }
                }
            }
        }
        Box(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 4.dp)) {
            when {
                vm.fileError != null -> EmptyState(Icons.Rounded.InsertDriveFile, vm.fileError ?: "")
                kind == FileKind.Image -> AsyncImage(
                    ImageRequest.Builder(LocalContext.current).data(vm.api.rawUrl(repo, file.path))
                        .apply { vm.api.rawHeaders().forEach { (k, v) -> addHeader(k, v) } }.crossfade(true).build(),
                    file.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                )
                kind == FileKind.Video || kind == FileKind.Audio -> MediaBox(vm.api.rawUrl(repo, file.path), vm.api.rawHeaders(), kind == FileKind.Audio)
                kind == FileKind.Binary -> EmptyState(Icons.Rounded.InsertDriveFile, "Berkas biner — tidak bisa dipreview.\nUkuran ${fmtSize(file.size)}")
                loaded == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Flame(Modifier.size(100.dp), 0.6f) }
                kind == FileKind.Html && mode == 1 -> HtmlPreview(vm, repo, file.path, text)
                else -> CodeField(text) { text = it }
            }
        }
    }

    if (commitDlg) {
        var msg by remember { mutableStateOf("Edit ${file.name} via Kiln") }
        AlertDialog(
            onDismissRequest = { commitDlg = false }, containerColor = K.Surface,
            title = { Text("Simpan ke GitHub", color = K.Text) },
            text = { KilnField(msg, { msg = it }, "Pesan commit") },
            confirmButton = { TextButton({ commitDlg = false; vm.saveFile(text, msg.ifBlank { "Edit via Kiln" }) }) { Text("Commit", color = K.Orange) } },
            dismissButton = { TextButton({ commitDlg = false }) { Text("Batal", color = K.Muted) } },
        )
    }
    if (exitDlg) {
        AlertDialog(
            onDismissRequest = { exitDlg = false }, containerColor = K.Surface,
            title = { Text("Buang perubahan?", color = K.Text) },
            text = { Text("Ada perubahan yang belum disimpan.", color = K.Muted) },
            confirmButton = { TextButton({ exitDlg = false; onBack() }) { Text("Buang", color = K.Err) } },
            dismissButton = { TextButton({ exitDlg = false }) { Text("Lanjut edit", color = K.Orange) } },
        )
    }
}

@Composable
private fun CodeField(text: String, onChange: (String) -> Unit) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color(0xFF0B0716)).verticalScroll(rememberScrollState())) {
        BasicTextField(
            value = text, onValueChange = onChange,
            textStyle = TextStyle(color = K.Text, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp),
            cursorBrush = SolidColor(K.Orange),
            modifier = Modifier.fillMaxWidth().heightIn(min = 600.dp).padding(14.dp),
        )
    }
}

/** Preview HTML. File relatif (css/js/gambar) diambil langsung dari repo lewat GitHub API. */
@Composable
private fun HtmlPreview(vm: KilnViewModel, repo: Repo, filePath: String, html: String) {
    val dir = filePath.substringBeforeLast('/', "")
    val base = "https://kiln.preview/" + if (dir.isEmpty()) "" else "$dir/"
    AndroidView(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(android.graphics.Color.WHITE)
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val u = request.url
                        if (u.host != "kiln.preview") return null
                        val p = Uri.decode(u.path.orEmpty().trimStart('/'))
                        val bytes = vm.api.rawBytesBlocking(repo, p)
                            ?: return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(p.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
                        return WebResourceResponse(mime, "utf-8", ByteArrayInputStream(bytes))
                    }
                }
            }
        },
        update = { w ->
            if (w.tag != html) { w.tag = html; w.loadDataWithBaseURL(base, html, "text/html", "utf-8", null) }
        },
    )
}

@Composable
private fun MediaBox(url: String, headers: Map<String, String>, audio: Boolean) {
    val ctx = LocalContext.current
    val player = remember(url) {
        val ds = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers).setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(ctx).setMediaSourceFactory(DefaultMediaSourceFactory(ds)).build().apply {
            setMediaItem(MediaItem.fromUri(url)); prepare(); playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (audio) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0.9f, 1.1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
                Icon(Icons.Rounded.Audiotrack, null, tint = K.Orange, modifier = Modifier.size(96.dp).scale(pulse))
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().then(if (audio) Modifier.height(120.dp) else Modifier.weight(1f)),
            factory = { PlayerView(it).apply { this.player = player; useController = true; controllerShowTimeoutMs = 0 } },
        )
    }
}
