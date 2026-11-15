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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import id.nevora.kiln.KilnViewModel

@Composable
fun BackupTab(vm: KilnViewModel) {
    val ctx = LocalContext.current
    var dialog by remember { mutableIntStateOf(0) } // 1 = backup, 2 = pulihkan
    var pass by remember { mutableStateOf("") }
    val exportKs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { it?.let { u -> vm.exportKeystores(u, pass) } }
    val importKs = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> vm.importKeystores(u, pass) } }
    val exportZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let { u -> vm.exportZip(u) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Stagger(0) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Kunci tanda tangan (keystore)")
                VGap(8)
                StatusRow("Salinan di HP ini", vm.localKs != null)
                StatusRow("Tersimpan di ENV GitHub", vm.secretsOk)
                if (vm.fingerprint.isNotBlank()) {
                    VGap(6)
                    Text("SHA-256", color = K.Muted, fontSize = 11.sp)
                    Text(vm.fingerprint, color = K.Text, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 14.sp)
                }
                VGap(8)
                Text(
                    "Keystore dibuat otomatis saat build pertama. Kalau hilang, APK tidak bisa di-update di atas versi lama — backup-lah.",
                    color = K.Muted, fontSize = 12.sp,
                )
            }
        }
        Stagger(1) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Backup & pulihkan")
                VGap(10)
                FireButton("Backup keystore (terenkripsi)", Modifier.fillMaxWidth(), Icons.Rounded.Backup) { dialog = 1 }
                VGap(8)
                GhostButton("Pulihkan dari backup", Modifier.fillMaxWidth(), Icons.Rounded.Restore) { dialog = 2 }
                VGap(8)
                GhostButton("Kirim ulang keystore ke ENV", Modifier.fillMaxWidth(), Icons.Rounded.Key, K.Amber) { vm.republishSecrets() }
                VGap(8)
                GhostButton("Backup kode (ZIP)", Modifier.fillMaxWidth(), Icons.Rounded.FolderZip, K.Magenta) {
                    exportZip.launch("${vm.repo?.name ?: "kode"}.zip")
                }
            }
        }
        Stagger(2) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Tentang")
                VGap(6)
                Text("Kiln 1.0.0 — by Nevora Studio\nOpen source • Lisensi MIT", color = K.Muted, fontSize = 13.sp)
                VGap(8)
                GhostButton("Saluran WhatsApp", Modifier.fillMaxWidth(), Icons.Rounded.OpenInNew, K.Orange) { openUrl(ctx, CHANNEL_URL) }
            }
        }
        VGap(12)
    }

    if (dialog != 0) {
        AlertDialog(
            onDismissRequest = { dialog = 0; pass = "" }, containerColor = K.Surface,
            title = { Text(if (dialog == 1) "Password backup" else "Password backup lama", color = K.Text) },
            text = { KilnField(pass, { pass = it }, "Minimal 6 karakter", password = true) },
            confirmButton = {
                TextButton({
                    if (pass.length >= 6) {
                        if (dialog == 1) exportKs.launch("kiln-backup.kilnbackup") else importKs.launch(arrayOf("*/*"))
                        dialog = 0
                    }
                }) { Text("Lanjut", color = K.Orange) }
            },
            dismissButton = { TextButton({ dialog = 0; pass = "" }) { Text("Batal", color = K.Muted) } },
        )
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean?) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            when (ok) { true -> Icons.Rounded.Check; false -> Icons.Rounded.Close; null -> Icons.Rounded.Info },
            null, tint = when (ok) { true -> K.Ok; false -> K.Err; null -> K.Muted }, modifier = Modifier.size(18.dp),
        )
        HGap(10)
        Text(label, color = K.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(when (ok) { true -> "Ada"; false -> "Belum"; null -> "Memeriksa…" }, color = K.Muted, fontSize = 12.sp)
    }
}
