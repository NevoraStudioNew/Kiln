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
import id.nevora.kiln.KilnViewModel

sealed interface Screen {
    object Splash : Screen
    object Login : Screen
    object Projects : Screen
    object Project : Screen
    object Editor : Screen
}

/** Navigasi sederhana dengan transisi geser + pudar + zoom tipis di setiap pindah halaman. */
@Composable
fun KilnApp(vm: KilnViewModel) {
    var stack by remember { mutableStateOf<List<Screen>>(listOf(Screen.Splash)) }
    var forward by remember { mutableStateOf(true) }
    fun push(s: Screen) { forward = true; stack = stack + s }
    fun pop() { if (stack.size > 1) { forward = false; stack = stack.dropLast(1) } }
    fun reset(s: Screen) { forward = true; stack = listOf(s) }

    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }
    BackHandler(enabled = stack.size > 1 && stack.last() != Screen.Editor) { pop() }

    KilnBackground {
        AnimatedContent(
            targetState = stack.last(),
            transitionSpec = {
                if (forward) {
                    (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(380)) + scaleIn(tween(380), 0.94f)) togetherWith
                        (slideOutHorizontally(tween(380)) { -it / 5 } + fadeOut(tween(250)) + scaleOut(tween(380), 0.96f))
                } else {
                    (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { -it / 5 } + fadeIn(tween(380)) + scaleIn(tween(380), 0.96f)) togetherWith
                        (slideOutHorizontally(tween(380)) { it / 3 } + fadeOut(tween(250)) + scaleOut(tween(380), 0.94f))
                }
            },
            label = "nav", modifier = Modifier.fillMaxSize(),
        ) { screen ->
            when (screen) {
                Screen.Splash -> SplashScreen {
                    if (vm.loggedIn) { vm.loadRepos(); reset(Screen.Projects) } else reset(Screen.Login)
                }
                Screen.Login -> LoginScreen(vm) { reset(Screen.Projects) }
                Screen.Projects -> ProjectsScreen(vm, onOpen = { vm.openRepo(it); push(Screen.Project) }, onLogout = { reset(Screen.Login) })
                Screen.Project -> ProjectScreen(vm, onBack = { pop() }, onOpenFile = { vm.openFileEntry(it); push(Screen.Editor) })
                Screen.Editor -> EditorScreen(vm, onBack = { pop() })
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 76.dp))
    }
}
