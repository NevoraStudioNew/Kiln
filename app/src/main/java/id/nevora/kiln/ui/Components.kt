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
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val CHANNEL_URL = "https://whatsapp.com/channel/0029VbDwlX6EawdkN0IZm713"

fun openUrl(ctx: android.content.Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

fun fmtSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.1f KB".format(b / 1024.0)
    else -> "%.1f MB".format(b / 1048576.0)
}

private val timeFmt = DateTimeFormatter.ofPattern("dd MMM, HH:mm").withZone(ZoneId.systemDefault())
fun fmtTime(iso: String): String = runCatching { timeFmt.format(Instant.parse(iso)) }.getOrDefault(iso)

/** Latar belakang hidup: cahaya ungu & oranye yang bergeser pelan, seperti bara. */
@Composable
fun KilnBackground(content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "bg")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Box(
        Modifier.fillMaxSize().background(K.Bg).drawBehind {
            drawRect(Brush.radialGradient(listOf(K.Violet.copy(alpha = 0.38f), Color.Transparent), Offset(size.width * (0.05f + 0.35f * a), size.height * 0.06f), size.width * 0.95f))
            drawRect(Brush.radialGradient(listOf(K.Orange.copy(alpha = 0.24f), Color.Transparent), Offset(size.width * (0.98f - 0.35f * a), size.height * 0.98f), size.width * 0.95f))
        },
        content = content,
    )
}

/** Efek tekan: mengecil lalu memantul balik. Letakkan PALING AWAL di rantai modifier. */
fun Modifier.bounceClick(enabled: Boolean = true, onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (pressed) 0.94f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "bounce")
    this.scale(s).pointerInput(enabled) {
        if (enabled) detectTapGestures(
            onPress = { pressed = true; tryAwaitRelease(); pressed = false },
            onLongPress = { onLongClick?.invoke() },
            onTap = { onClick() },
        )
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.clip(shape).background(K.Surface.copy(alpha = 0.80f))
            .border(1.dp, Brush.linearGradient(listOf(K.Purple.copy(alpha = 0.6f), K.Orange.copy(alpha = 0.35f))), shape)
            .padding(16.dp),
        content = content,
    )
}

@Composable fun VGap(h: Int) = Spacer(Modifier.height(h.dp))
@Composable fun HGap(w: Int) = Spacer(Modifier.width(w.dp))

@Composable
fun FireButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.bounceClick(enabled && !loading, onClick = onClick).height(52.dp).clip(RoundedCornerShape(16.dp))
            .background(if (enabled) K.FireH else Brush.horizontalGradient(listOf(K.Surface2, K.Surface2))),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
        else Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp)); HGap(8) }
            Text(text, color = if (enabled) Color.White else K.Muted, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = K.Purple, onClick: () -> Unit) {
    Row(
        modifier.bounceClick(onClick = onClick).height(48.dp).clip(RoundedCornerShape(14.dp))
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(18.dp)); HGap(8) }
        Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Muncul berurutan (fade + naik) — dipakai di tiap layar supaya terasa hidup. */
@Composable
fun Stagger(index: Int, content: @Composable () -> Unit) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(index * 70L); show = true }
    AnimatedVisibility(show, enter = fadeIn(tween(420)) + slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it / 5 }) { content() }
}

@Composable
fun KilnField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, leading: ImageVector? = null, password: Boolean = false, singleLine: Boolean = true) {
    OutlinedTextField(
        value, onChange, modifier.fillMaxWidth(), singleLine = singleLine,
        placeholder = { Text(hint, color = K.Muted) },
        leadingIcon = if (leading != null) ({ Icon(leading, null, tint = K.Muted) }) else null,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = K.Purple, unfocusedBorderColor = K.Muted.copy(alpha = 0.35f), cursorColor = K.Orange,
            focusedContainerColor = K.Surface.copy(alpha = 0.6f), unfocusedContainerColor = K.Surface.copy(alpha = 0.6f),
        ),
    )
}

@Composable
fun EmptyState(icon: ImageVector, text: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = K.Muted.copy(alpha = 0.6f), modifier = Modifier.size(44.dp))
        VGap(10)
        Text(text, color = K.Muted, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionTitle(text: String) = Text(text, color = K.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))

@Composable
fun AboutDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose, containerColor = K.Surface,
        title = { Text("Kiln 1.0.0", color = K.Text, fontWeight = FontWeight.Black) },
        text = { Text("Bakar script jadi APK, langsung dari HP.\n\nDibuat oleh Nevora Studio.\nOpen source — Lisensi MIT.", color = K.Muted) },
        confirmButton = { TextButton({ openUrl(ctx, CHANNEL_URL) }) { Text("Saluran WhatsApp", color = K.Orange) } },
        dismissButton = { TextButton(onClose) { Text("Tutup", color = K.Muted) } },
    )
}
