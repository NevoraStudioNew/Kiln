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
import id.nevora.kiln.BuildUi
import id.nevora.kiln.KilnViewModel
import id.nevora.kiln.Phase
import kotlinx.coroutines.delay

@Composable
fun BuildTab(vm: KilnViewModel, onOpenLog: () -> Unit) {
    val b = vm.build
    val busy = b.phase == Phase.Preparing || b.phase == Phase.Running
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item { Hero(vm, b, busy) }
        if (b.steps.isNotEmpty()) item { StepsCard(b.steps) }
        item { SectionTitle("Riwayat build") }
        if (vm.runs.isEmpty()) item { EmptyState(Icons.Rounded.LocalFireDepartment, "Belum ada build. Nyalakan api pertamamu!") }
        items(vm.runs, key = { it.id }) { run ->
            RunRow(run, Modifier.animateItemPlacement()) { vm.openLog(run.id); onOpenLog() }
        }
    }
}

@Composable
private fun Hero(vm: KilnViewModel, b: BuildUi, busy: Boolean) {
    val pct by animateIntAsState((b.progress * 100).toInt(), tween(700), label = "pct")
    val dead = b.phase == Phase.Failed || b.phase == Phase.Cancelled
    GlassCard(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(190.dp)) {
            Flame(Modifier.fillMaxSize(), if (b.phase == Phase.Idle) 0.1f else b.progress, dead)
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (b.phase == Phase.Idle) "Siap" else "$pct%", fontSize = 44.sp, fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic, color = if (dead) K.Muted else K.Text,
            )
            AnimatedContent(
                b.label,
                transitionSpec = { (fadeIn(tween(300)) + slideInVertically { it / 2 }) togetherWith (fadeOut(tween(200)) + slideOutVertically { -it / 2 }) },
                label = "build-label",
            ) { Text(it, color = K.Muted, textAlign = TextAlign.Center) }
            VGap(12)
            Pill("Versi berikutnya  v${vm.nextVersion}", K.Purple)
            if (vm.kind == ProjectKind.Unknown) {
                VGap(8)
                Text("Butuh settings.gradle(.kts) atau index.html di root repo.", color = K.Err, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            VGap(14)
            if (busy) GhostButton("Batalkan build", Modifier.fillMaxWidth(), Icons.Rounded.Stop, K.Err) { vm.cancelBuild() }
            else FireButton("Bakar APK", Modifier.fillMaxWidth(), Icons.Rounded.LocalFireDepartment, enabled = vm.kind != ProjectKind.Unknown) { vm.startBuild() }
        }
    }
}

@Composable
private fun StepsCard(steps: List<Step>) {
    GlassCard(Modifier.fillMaxWidth()) {
        SectionTitle("Tahapan")
        VGap(6)
        steps.forEach { s ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    Crossfade(s.status + s.conclusion, label = "step") { _ ->
                        when {
                            s.status == "completed" && (s.conclusion == "success" || s.conclusion == "skipped") -> Icon(Icons.Rounded.Check, null, tint = K.Ok, modifier = Modifier.size(18.dp))
                            s.status == "completed" -> Icon(Icons.Rounded.Close, null, tint = K.Err, modifier = Modifier.size(18.dp))
                            s.status == "in_progress" -> CircularProgressIndicator(Modifier.size(16.dp), color = K.Orange, strokeWidth = 2.dp)
                            else -> Box(Modifier.size(8.dp).background(K.Muted.copy(alpha = 0.4f), CircleShape))
                        }
                    }
                }
                HGap(10)
                Text(s.name, color = if (s.status == "pending") K.Muted else K.Text, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun RunRow(run: Run, modifier: Modifier, onClick: () -> Unit) {
    val (c, t) = when {
        run.status != "completed" -> K.Amber to "Berjalan"
        run.conclusion == "success" -> K.Ok to "Sukses"
        run.conclusion == "cancelled" -> K.Muted to "Batal"
        else -> K.Err to "Gagal"
    }
    Row(
        modifier.fillMaxWidth().bounceClick(onClick = onClick).clip(RoundedCornerShape(16.dp)).background(K.Surface.copy(alpha = 0.7f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(c, CircleShape))
        HGap(12)
        Column(Modifier.weight(1f)) {
            Text(run.title.ifBlank { "Build #${run.number}" }, color = K.Text, fontWeight = FontWeight.SemiBold)
            Text("#${run.number} • ${fmtTime(run.createdAt)}", color = K.Muted, fontSize = 12.sp)
        }
        Pill(t, c)
    }
}

// ---------------------------------------------------------------- LOG

@Composable
fun LogTab(vm: KilnViewModel) {
    val clip = LocalClipboardManager.current
    LaunchedEffect(vm.runs.firstOrNull()?.id) {
        if (vm.logRunId == null) vm.runs.firstOrNull()?.let { vm.openLog(it.id) }
    }
    val live = vm.build.phase == Phase.Running && vm.build.runId == vm.logRunId
    LaunchedEffect(live, vm.logRunId) { while (live) { delay(4000); vm.refreshLog() } }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(vm.runs.take(10), key = { it.id }) { run ->
                    FilterChip(run.id == vm.logRunId, { vm.openLog(run.id) }, { Text("#${run.number}") })
                }
            }
            IconButton({ vm.refreshLog() }) {
                if (vm.logBusy) CircularProgressIndicator(Modifier.size(18.dp), color = K.Orange, strokeWidth = 2.dp)
                else Icon(Icons.Rounded.Refresh, "Muat ulang", tint = K.Muted)
            }
            IconButton({ clip.setText(AnnotatedString(vm.logLines.joinToString("\n"))) }) { Icon(Icons.Rounded.ContentCopy, "Salin", tint = K.Muted) }
        }
        VGap(6)
        if (vm.logLines.isEmpty()) {
            EmptyState(Icons.Rounded.Terminal, if (vm.runs.isEmpty()) "Belum ada log. Jalankan build dulu." else "Log belum tersedia")
        } else {
            val state = rememberLazyListState()
            LaunchedEffect(vm.logLines.size) { if (live) state.scrollToItem(vm.logLines.lastIndex) }
            LazyColumn(
                Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color(0xFF05030B)).padding(10.dp),
                state = state,
            ) {
                items(vm.logLines.size) { i -> LogLine(vm.logLines[i]) }
            }
        }
    }
}

@Composable
private fun LogLine(l: String) {
    val c = when {
        "##[error]" in l || l.startsWith("Error") -> K.Err
        "##[warning]" in l -> K.Amber
        l.startsWith("##[group]") -> K.Purple
        l.startsWith("▶") -> K.Orange
        else -> K.Text.copy(alpha = 0.85f)
    }
    Text(
        l.removePrefix("##[group]").removePrefix("##[error]"), color = c, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
        fontWeight = if (l.startsWith("##[group]") || l.startsWith("▶")) FontWeight.Bold else FontWeight.Normal,
    )
}
