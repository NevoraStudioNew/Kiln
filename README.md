<!--
  Kiln — Bakar script jadi APK, langsung dari HP.
  Open source di bawah Lisensi MIT.
  Copyright (c) 2026 Nevora Studio
  Saluran resmi: https://whatsapp.com/channel/0029VbDwlX6EawdkN0IZm713
-->

# 🔥 Kiln

**Kiln** adalah aplikasi Android (Kotlin + Jetpack Compose) untuk "membakar" script menjadi APK — semacam GitHub Actions, tapi dibuat khusus untuk APK dan bisa dioperasikan penuh dari HP.

Nama *Kiln* artinya tungku pembakaran: kode mentah masuk, APK matang keluar.

## Cara kerja

Kiln bukan compiler di dalam HP. Kiln adalah **remote control** untuk GitHub Actions:

1. Kamu masuk dengan GitHub token, lalu memilih repo.
2. Saat tombol **Bakar APK** ditekan, Kiln memastikan tiga hal: keystore ada, workflow build terpasang di repo, dan nomor versi berikutnya sudah dihitung.
3. Kiln memicu workflow, lalu memantau tahapannya. Persentase dan api di layar tumbuh mengikuti tahapan nyata yang sedang berjalan di runner.
4. Runner membangun APK, menandatanganinya, lalu mengunggahnya ke **Releases**. Tidak ada artifact yang diunggah, jadi tidak menghabiskan kuota penyimpanan.

Dua jenis proyek dikenali otomatis:

- **Proyek Android (Gradle)** — repo yang punya `settings.gradle(.kts)`.
- **Proyek web** — repo yang punya `index.html`. Isinya dibungkus menjadi APK berbasis WebView.

## Fitur

- Penjelajah berkas repo dengan breadcrumb, buat/impor/hapus berkas.
- Editor kode dengan commit langsung ke GitHub.
- Preview HTML (file CSS/JS/gambar relatif ikut termuat), teks, gambar, video, dan audio.
- Halaman build dengan api animasi, persentase, dan daftar tahapan.
- Log build dengan pewarnaan, auto-scroll saat build berjalan.
- Hasil build di Rilis: unduh, pasang, atau hapus.
- Backup keystore terenkripsi, pemulihan, dan backup kode (ZIP).
- Tampilan mengikuti logo: ungu ke oranye, dengan animasi di tiap pergantian halaman.

## Keystore tanpa JKS manual

Kiln tidak meminta kamu menyiapkan JKS. Pada build pertama:

- Kiln membuat keystore (PKCS12, RSA 2048) langsung di HP.
- Isinya dienkripsi dengan sealed box libsodium, lalu disimpan sebagai **GitHub Secrets** (environment variable workflow): `KILN_KEYSTORE_B64`, `KILN_KEYSTORE_PASSWORD`, `KILN_KEY_ALIAS`, `KILN_KEY_PASSWORD`.
- Satu salinan terenkripsi disimpan di HP (EncryptedSharedPreferences, AES-256).

GitHub Secrets tidak bisa dibaca kembali setelah disimpan. Karena itu salinan lokal dan fitur **Backup** penting: tanpa keystore yang sama, APK baru tidak bisa memperbarui APK lama yang sudah terpasang.

## Penomoran versi

Versi pertama adalah `1.0.0`. Setiap build menambah `0.0.1`. Saat angka terakhir mencapai 100, versi naik ke minor berikutnya (`1.0.99` → `1.1.0`), dan aturan yang sama berlaku dari minor ke major. `versionCode` naik satu untuk setiap build. Status versi disimpan di `.kiln/state.json` di dalam repo.

## Izin token

Kiln membutuhkan akses ke isi repo, Actions, Secrets, dan workflow. Pada token klasik itu berarti scope `repo` dan `workflow`. Token disimpan terenkripsi di perangkat dan hanya dikirim ke `api.github.com`.

## Batasan yang jujur

- Build berjalan di server GitHub, jadi butuh koneksi internet dan kuota Actions repo.
- Proyek Gradle yang tidak punya `gradlew` dibangun dengan Gradle 8.9 — proyek yang butuh versi lain sebaiknya menyertakan wrapper sendiri.
- Menghapus folder dilakukan dengan menghapus isinya (batasan GitHub Contents API).
- Pembungkus proyek web adalah WebView sederhana tanpa akses fitur native.

## Struktur kode

- `data/` — klien GitHub API, penyimpanan terenkripsi, pembuat keystore, backup, penomoran versi.
- `KilnViewModel.kt` — alur build, polling run, log, rilis, dan backup.
- `ui/` — tema, komponen, api animasi, dan semua layar.
- `assets/kiln-build.yml` — workflow yang dipasang Kiln ke repo (salinannya ada di `.github/workflows/`, jadi Kiln bisa membangun dirinya sendiri).

## Lisensi

MIT © 2026 **Nevora Studio**. Lihat berkas `LICENSE`.

Saluran resmi: https://whatsapp.com/channel/0029VbDwlX6EawdkN0IZm713
