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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.sin

/**
 * Api Kiln. [intensity] 0..1 — makin besar progress, makin besar & terang apinya.
 * Tiga lidah api bergoyang + percikan bara naik. [dead] = padam (abu-abu) untuk build gagal/batal.
 */
@Composable
fun Flame(modifier: Modifier = Modifier, intensity: Float, dead: Boolean = false) {
    val t = rememberInfiniteTransition(label = "flame")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1700, easing = LinearEasing)), label = "ph")
    val level by animateFloatAsState(intensity.coerceIn(0f, 1f), tween(800, easing = FastOutSlowInEasing), label = "lvl")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val s = 0.14f + 0.86f * level
        val baseY = h * 0.97f
        val outer = if (dead) listOf(Color(0xFF6B6480), Color(0xFF3A3350)) else listOf(Color(0xFFFF3D00), K.Orange, K.Amber)
        val mid = if (dead) listOf(Color(0xFF5A546E), Color(0xFF332D46)) else listOf(K.Orange, K.Amber, K.Yellow)
        val inner = if (dead) listOf(Color(0xFF4A4460), Color(0xFF2B253C)) else listOf(K.Amber, K.Yellow, Color(0xFFFFF4B8))
        val glow = if (dead) Color(0xFF6B6480) else K.Orange

        val gr = w * 0.55f * s + 30f
        val gc = Offset(cx, baseY - h * 0.25f * s)
        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.10f + 0.35f * level), Color.Transparent), gc, gr), gr, gc)

        fun flame(scale: Float, amp: Float, ph: Float): Path {
            val fh = h * 0.92f * s * scale
            val fw = w * 0.5f * (0.45f + 0.55f * s) * scale
            val sway = sin(phase + ph) * fw * amp
            val top = baseY - fh * (1f + 0.05f * sin(phase * 2f + ph))
            return Path().apply {
                moveTo(cx - fw / 2f, baseY)
                cubicTo(cx - fw * 0.8f, baseY - fh * 0.35f, cx - fw * 0.1f + sway * 0.4f, baseY - fh * 0.62f, cx + sway, top)
                cubicTo(cx + fw * 0.15f + sway * 0.6f, baseY - fh * 0.6f, cx + fw * 0.85f, baseY - fh * 0.36f, cx + fw / 2f, baseY)
                quadraticBezierTo(cx, baseY + fh * 0.08f, cx - fw / 2f, baseY)
                close()
            }
        }
        fun brush(c: List<Color>) = Brush.verticalGradient(c, startY = baseY - h * 0.92f * s, endY = baseY)

        translate(left = -w * 0.17f * s) { drawPath(flame(0.62f, 0.35f, 1.9f), brush(outer)) }
        translate(left = w * 0.18f * s) { drawPath(flame(0.5f, 0.4f, 3.4f), brush(outer)) }
        drawPath(flame(1f, 0.22f, 0f), brush(outer))
        drawPath(flame(0.76f, 0.2f, 0.6f), brush(mid))
        drawPath(flame(0.5f, 0.15f, 1.1f), brush(inner))

        if (level > 0.04f && !dead) {
            for (i in 0 until 9) {
                val life = ((phase / (2f * PI.toFloat())) + i / 9f) % 1f
                val px = cx + sin(life * 7f + i * 1.7f) * w * 0.22f * s
                val py = baseY - h * 0.15f - life * h * 0.85f * s
                drawCircle(if (i % 2 == 0) K.Amber else K.Orange, (1f - life) * 3.2.dp.toPx() + 0.6f, Offset(px, py), alpha = (1f - life).coerceIn(0f, 1f))
            }
        }
    }
}
