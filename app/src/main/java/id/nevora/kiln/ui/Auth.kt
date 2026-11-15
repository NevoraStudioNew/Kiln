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
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Layar pembuka: logo memantul masuk, api tumbuh 0% → 100% sambil teks "Menyiapkan..." berganti. */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.4f) }
    val logoAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { logoAlpha.animateTo(1f, tween(600)) }
        launch { logoScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
        p.animateTo(1f, tween(3000, easing = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)))
        delay(450)
        onDone()
    }
    val pct = (p.value * 100).toInt()
    val label = when {
        pct < 25 -> "Menyiapkan tungku..."
        pct < 50 -> "Memanaskan mesin..."
        pct < 80 -> "Menyalakan api..."
        pct < 100 -> "Hampir siap..."
        else -> "Siap membakar!"
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painterResource(R.drawable.kiln_logo), "Kiln",
            Modifier.size(170.dp).scale(logoScale.value).alpha(logoAlpha.value).clip(RoundedCornerShape(38.dp)),
        )
        VGap(8)
        Flame(Modifier.size(width = 220.dp, height = 150.dp), p.value)
        Text("$pct%", fontSize = 40.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, color = K.Text)
        AnimatedContent(label, transitionSpec = { (fadeIn(tween(300)) + slideInVertically { it / 2 }) togetherWith (fadeOut(tween(200)) + slideOutVertically { -it / 2 }) }, label = "splash-label") {
            Text(it, color = K.Muted, fontSize = 15.sp)
        }
        VGap(18)
        Box(Modifier.width(220.dp).height(6.dp).clip(RoundedCornerShape(50)).background(K.Surface2)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(p.value).background(K.FireH))
        }
        VGap(28)
        Text("by Nevora Studio", color = K.Muted.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}

@Composable
fun LoginScreen(vm: id.nevora.kiln.KilnViewModel, onDone: () -> Unit) {
    var token by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Stagger(0) { Image(painterResource(R.drawable.kiln_logo), "Kiln", Modifier.size(130.dp).clip(RoundedCornerShape(30.dp))) }
        VGap(16)
        Stagger(1) { Text("Bakar script jadi APK", fontSize = 26.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, color = K.Text, textAlign = TextAlign.Center) }
        Stagger(2) {
            Text(
                "Masuk dengan GitHub token. Build berjalan di GitHub Actions, hasilnya langsung ke Releases.",
                color = K.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp),
            )
        }
        Stagger(3) {
            GlassCard(Modifier.fillMaxWidth()) {
                KilnField(token, { token = it }, "ghp_… / github_pat_…", leading = Icons.Rounded.Key, password = true)
                vm.loginError?.let { Text(it, color = K.Err, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
                VGap(14)
                FireButton("Nyalakan Kiln", Modifier.fillMaxWidth(), Icons.Rounded.LocalFireDepartment, enabled = token.length > 10, loading = vm.loginBusy) {
                    vm.signIn(token, onDone)
                }
                TextButton({ openUrl(ctx, "https://github.com/settings/tokens/new?scopes=repo,workflow&description=Kiln") }, Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Buat token (scope: repo + workflow)", color = K.Orange, fontSize = 13.sp)
                }
            }
        }
        Stagger(4) { Text("by Nevora Studio", color = K.Muted.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(top = 18.dp)) }
    }
}
