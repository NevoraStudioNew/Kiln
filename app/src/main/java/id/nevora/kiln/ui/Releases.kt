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
import id.nevora.kiln.KilnViewModel

@Composable
fun ReleasesTab(vm: KilnViewModel) {
    var del by remember { mutableStateOf<Release?>(null) }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hasil build", color = K.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                IconButton({ vm.loadReleases() }) {
                    if (vm.releasesBusy) CircularProgressIndicator(Modifier.size(18.dp), color = K.Orange, strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Refresh, "Muat ulang", tint = K.Muted)
                }
            }
        }
        if (vm.releases.isEmpty()) item { EmptyState(Icons.Rounded.Inventory2, "Belum ada rilis.\nBakar dulu di tab Build 🔥") }
        itemsIndexed(vm.releases, key = { _, r -> r.id }) { i, rel ->
            Stagger(i.coerceAtMost(6)) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rel.tag, color = K.Amber, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            Text(fmtTime(rel.createdAt), color = K.Muted, fontSize = 12.sp)
                        }
                        IconButton({ del = rel }) { Icon(Icons.Rounded.Delete, "Hapus", tint = K.Muted) }
                    }
                    rel.assets.forEach { a ->
                        VGap(8)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(K.Surface2).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(a.name, color = K.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${fmtSize(a.size)} • ${a.downloads}× diunduh", color = K.Muted, fontSize = 11.sp)
                                if (vm.dlId == a.id) {
                                    VGap(6)
                                    LinearProgressIndicator({ vm.dlProgress }, Modifier.fillMaxWidth(), color = K.Orange, trackColor = K.Surface)
                                }
                            }
                            HGap(8)
                            GhostButton("Pasang", icon = Icons.Rounded.Download, color = K.Orange) { if (vm.dlId == null) vm.install(a) }
                        }
                    }
                }
            }
        }
    }
    del?.let { r ->
        AlertDialog(
            onDismissRequest = { del = null }, containerColor = K.Surface,
            title = { Text("Hapus rilis ${r.tag}?", color = K.Text) },
            text = { Text("Rilis dan tag-nya dihapus dari GitHub.", color = K.Muted) },
            confirmButton = { TextButton({ vm.deleteRelease(r); del = null }) { Text("Hapus", color = K.Err) } },
            dismissButton = { TextButton({ del = null }) { Text("Batal", color = K.Muted) } },
        )
    }
}
