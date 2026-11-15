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
import androidx.compose.ui.res.painterResource
import id.nevora.kiln.KilnViewModel
import id.nevora.kiln.R

@Composable
fun ProjectsScreen(vm: KilnViewModel, onOpen: (Repo) -> Unit, onLogout: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    val list = vm.repos.filter { it.fullName.contains(q, ignoreCase = true) }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.kiln_logo), null, Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)))
            HGap(12)
            Column(Modifier.weight(1f)) {
                Text("Kiln", fontSize = 24.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, color = K.Text)
                Text("@${vm.login}", color = K.Muted, fontSize = 12.sp)
            }
            IconButton({ vm.loadRepos() }) {
                if (vm.reposBusy) CircularProgressIndicator(Modifier.size(20.dp), color = K.Orange, strokeWidth = 2.dp)
                else Icon(Icons.Rounded.Refresh, "Muat ulang", tint = K.Muted)
            }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Menu", tint = K.Muted) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Tentang Kiln") }, { menu = false; about = true })
                    DropdownMenuItem({ Text("Keluar") }, { menu = false; vm.logout(); onLogout() })
                }
            }
        }
        KilnField(q, { q = it }, "Cari proyek…", leading = Icons.Rounded.Search)
        if (vm.reposBusy && list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Flame(Modifier.size(110.dp), 0.55f) }
        } else if (list.isEmpty()) {
            EmptyState(Icons.Rounded.FolderOpen, "Belum ada proyek yang cocok")
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(list, key = { _, r -> r.fullName }) { i, r ->
                Stagger(i.coerceAtMost(8)) { RepoCard(r) { onOpen(r) } }
            }
        }
    }
    if (about) AboutDialog { about = false }
}

@Composable
private fun RepoCard(r: Repo, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().bounceClick(onClick = onClick).clip(RoundedCornerShape(18.dp))
            .background(K.Surface.copy(alpha = 0.8f)).border(1.dp, K.Purple.copy(alpha = 0.25f), RoundedCornerShape(18.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(K.Fire), contentAlignment = Alignment.Center) {
            Icon(if (r.isPrivate) Icons.Rounded.Lock else Icons.Rounded.Code, null, tint = Color.White)
        }
        HGap(12)
        Column(Modifier.weight(1f)) {
            Text(r.name, color = K.Text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                r.description.ifBlank { r.fullName }, color = K.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Pill(r.branch, K.Purple)
    }
}
