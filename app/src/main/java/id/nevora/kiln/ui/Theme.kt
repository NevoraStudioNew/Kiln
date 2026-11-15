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

/** Palet diambil langsung dari logo Kiln: ungu → magenta → oranye → kuning api. */
object K {
    val Bg = Color(0xFF07040F)
    val Surface = Color(0xFF130B24)
    val Surface2 = Color(0xFF1D1133)
    val Violet = Color(0xFF5B21F5)
    val Purple = Color(0xFF8B3DFF)
    val Magenta = Color(0xFFD946EF)
    val Orange = Color(0xFFFF6A00)
    val Amber = Color(0xFFFFB800)
    val Yellow = Color(0xFFFFD21F)
    val Text = Color(0xFFF4EEFF)
    val Muted = Color(0xFF9C8FB8)
    val Ok = Color(0xFF3DDC84)
    val Err = Color(0xFFFF4D5E)
    val Fire = Brush.linearGradient(listOf(Purple, Magenta, Orange, Yellow))
    val FireH = Brush.horizontalGradient(listOf(Violet, Magenta, Orange, Yellow))
}

private val Scheme = darkColorScheme(
    primary = K.Purple, onPrimary = Color.White, secondary = K.Orange,
    background = K.Bg, onBackground = K.Text, surface = K.Surface, onSurface = K.Text,
    surfaceVariant = K.Surface2, onSurfaceVariant = K.Muted, error = K.Err, outline = K.Muted,
)

@Composable
fun KilnTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Scheme, content = content)
