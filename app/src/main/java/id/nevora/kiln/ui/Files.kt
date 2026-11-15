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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import id.nevora.kiln.KilnViewModel

@Composable
fun FilesTab(vm: KilnViewModel, onOpen: (Entry) -> Unit) {
    var newDlg by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf<Entry?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importFile(it) } }
    BackHandler(vm.path.isNotEmpty()) { vm.up() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                item { Crumb("root") { vm.loadDir("") } }
                val parts = vm.path.split('/').filter { it.isNotEmpty() }
                itemsIndexed(parts) { i, p ->
                    Text(" / ", color = K.Muted)
                    Crumb(p) { vm.loadDir(parts.take(i + 1).joinToString("/")) }
                }
            }
            IconButton({ newDlg = true }) { Icon(Icons.Rounded.Add, "Berkas baru", tint = K.Orange) }
            IconButton({ picker.launch(arrayOf("*/*")) }) { Icon(Icons.Rounded.Upload, "Impor", tint = K.Purple) }
        }
        if (vm.filesBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp), color = K.Orange, trackColor = K.Surface2)
        AnimatedContent(
            targetState = vm.path,
            transitionSpec = {
                val d = if (targetState.length >= initialState.length) 1 else -1
                (slideInHorizontally(tween(280)) { it * d / 4 } + fadeIn(tween(280))) togetherWith (slideOutHorizontally(tween(200)) { -it * d / 4 } + fadeOut(tween(150)))
            },
            label = "dir",
        ) { _ ->
            if (vm.entries.isEmpty() && !vm.filesBusy) EmptyState(Icons.Rounded.FolderOpen, "Folder ini kosong")
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.entries, key = { it.path }) { e ->
                    EntryRow(e, Modifier.animateItemPlacement(), onClick = { if (e.isDir) vm.loadDir(e.path) else onOpen(e) }, onLong = { del = e })
                }
            }
        }
    }

    if (newDlg) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newDlg = false }, containerColor = K.Surface,
            title = { Text("Berkas baru", color = K.Text) },
            text = { KilnField(name, { name = it }, "contoh: index.html") },
            confirmButton = { TextButton({ if (name.isNotBlank()) vm.createFile(name); newDlg = false }) { Text("Buat", color = K.Orange) } },
            dismissButton = { TextButton({ newDlg = false }) { Text("Batal", color = K.Muted) } },
        )
    }
    del?.let { e ->
        AlertDialog(
            onDismissRequest = { del = null }, containerColor = K.Surface,
            title = { Text("Hapus ${e.name}?", color = K.Text) },
            text = { Text("Perubahan ini di-commit langsung ke GitHub.", color = K.Muted) },
            confirmButton = { TextButton({ vm.deleteEntry(e); del = null }) { Text("Hapus", color = K.Err) } },
            dismissButton = { TextButton({ del = null }) { Text("Batal", color = K.Muted) } },
        )
    }
}

@Composable
private fun Crumb(text: String, onClick: () -> Unit) {
    Text(text, color = K.Amber, fontWeight = FontWeight.SemiBold, modifier = Modifier.bounceClick(onClick = onClick).padding(vertical = 8.dp))
}

@Composable
private fun EntryRow(e: Entry, modifier: Modifier, onClick: () -> Unit, onLong: () -> Unit) {
    val (icon, tint) = if (e.isDir) Icons.Rounded.Folder to K.Amber else when (fileKind(e.name)) {
        FileKind.Html -> Icons.Rounded.Html to K.Orange
        FileKind.Image -> Icons.Rounded.Image to K.Magenta
        FileKind.Video -> Icons.Rounded.Videocam to K.Purple
        FileKind.Audio -> Icons.Rounded.Audiotrack to K.Purple
        FileKind.Binary -> Icons.Rounded.InsertDriveFile to K.Muted
        FileKind.Text -> Icons.Rounded.Description to K.Text
    }
    Row(
        modifier.fillMaxWidth().bounceClick(onLongClick = onLong, onClick = onClick).clip(RoundedCornerShape(16.dp))
            .background(K.Surface.copy(alpha = 0.7f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint)
        HGap(12)
        Text(e.name, color = K.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!e.isDir) Text(fmtSize(e.size), color = K.Muted, fontSize = 12.sp)
    }
}
