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

/** Satu proyek = lima tab: Berkas, Build, Log, Rilis, Backup. Pindah tab = geser + pudar. */
@Composable
fun ProjectScreen(vm: KilnViewModel, onBack: () -> Unit, onOpenFile: (Entry) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val repo = vm.repo ?: return
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "Kembali", tint = K.Text) }
            Column(Modifier.weight(1f)) {
                Text(repo.name, color = K.Text, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${repo.fullName} • ${repo.branch}", color = K.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Pill(vm.kind.label, if (vm.kind == ProjectKind.Unknown) K.Err else K.Orange)
            HGap(8)
        }
        Box(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val d = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it * d / 5 } + fadeIn(tween(320))) togetherWith
                        (slideOutHorizontally(tween(320)) { -it * d / 5 } + fadeOut(tween(200)))
                },
                label = "tab",
            ) { t ->
                when (t) {
                    0 -> FilesTab(vm, onOpenFile)
                    1 -> BuildTab(vm) { tab = 2 }
                    2 -> LogTab(vm)
                    3 -> ReleasesTab(vm)
                    else -> BackupTab(vm)
                }
            }
        }
        BottomBar(tab) { tab = it }
    }
}

@Composable
private fun BottomBar(sel: Int, onSel: (Int) -> Unit) {
    val items = listOf(
        "Berkas" to Icons.Rounded.Folder, "Build" to Icons.Rounded.LocalFireDepartment, "Log" to Icons.Rounded.Terminal,
        "Rilis" to Icons.Rounded.Inventory2, "Backup" to Icons.Rounded.Shield,
    )
    Row(
        Modifier.fillMaxWidth().background(K.Surface.copy(alpha = 0.96f)).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        items.forEachIndexed { i, (label, icon) ->
            val on = i == sel
            val sc by animateFloatAsState(if (on) 1.12f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "tab-scale")
            Column(Modifier.weight(1f).bounceClick { onSel(i) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.scale(sc).clip(RoundedCornerShape(14.dp))
                        .then(if (on) Modifier.background(K.Fire) else Modifier).padding(horizontal = 16.dp, vertical = 6.dp),
                ) { Icon(icon, label, tint = if (on) Color.White else K.Muted) }
                Text(label, fontSize = 11.sp, color = if (on) K.Text else K.Muted)
            }
        }
    }
}
