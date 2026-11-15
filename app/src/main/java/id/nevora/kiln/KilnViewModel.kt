/*
 * Kiln — Bakar script jadi APK, langsung dari HP.
 * Open source di bawah Lisensi MIT.
 * Copyright (c) 2026 Nevora Studio
 * Saluran resmi: https://whatsapp.com/channel/0029VbDwlX6EawdkN0IZm713
 */

package id.nevora.kiln

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.nevora.kiln.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Phase { Idle, Preparing, Running, Success, Failed, Cancelled }

data class BuildUi(
    val phase: Phase = Phase.Idle,
    val progress: Float = 0f,
    val label: String = "Tungku siap dinyalakan",
    val runId: Long? = null,
    val steps: List<Step> = emptyList(),
    val error: String? = null,
)

class KilnViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    val api = GitHubApi { store.token }
    private val ctx get() = getApplication<Application>()

    var message by mutableStateOf<String?>(null)
    fun toast(s: String) { message = s }

    // ---------- akun ----------
    var loggedIn by mutableStateOf(store.token.isNotBlank())
    var login by mutableStateOf(store.login)
    var loginBusy by mutableStateOf(false)
    var loginError by mutableStateOf<String?>(null)

    fun signIn(token: String, onDone: () -> Unit) {
        viewModelScope.launch {
            loginBusy = true; loginError = null
            try {
                store.token = token.trim()
                val u = api.user()
                store.login = u; login = u; loggedIn = true
                loadRepos(); onDone()
            } catch (e: Exception) {
                store.token = ""; loggedIn = false
                loginError = "Token ditolak: ${e.message}"
            }
            loginBusy = false
        }
    }

    fun logout() {
        store.token = ""; store.login = ""
        loggedIn = false; login = ""; repos = emptyList(); repo = null
    }

    // ---------- repo ----------
    var repos by mutableStateOf<List<Repo>>(emptyList())
    var reposBusy by mutableStateOf(false)
    var repo by mutableStateOf<Repo?>(null)

    fun loadRepos() {
        viewModelScope.launch {
            reposBusy = true
            try { repos = api.repos() } catch (e: Exception) { toast("Gagal memuat proyek: ${e.message}") }
            reposBusy = false
        }
    }

    fun openRepo(r: Repo) {
        buildJob?.cancel(); buildJob = null
        repo = r; path = ""; entries = emptyList(); kind = ProjectKind.Unknown
        build = BuildUi(); runs = emptyList(); releases = emptyList()
        logRunId = null; logLines = emptyList()
        localKs = store.keystore(r.fullName); secretsOk = null; fingerprint = ""
        loadDir(""); loadRuns(attach = true); loadReleases(); loadVersion(); refreshBackup()
    }

    // ---------- berkas ----------
    var path by mutableStateOf("")
    var entries by mutableStateOf<List<Entry>>(emptyList())
    var filesBusy by mutableStateOf(false)
    var kind by mutableStateOf(ProjectKind.Unknown)

    fun loadDir(p: String) {
        val r = repo ?: return
        viewModelScope.launch {
            filesBusy = true
            try {
                val l = api.listDir(r, p)
                path = p; entries = l
                if (p.isEmpty()) {
                    val n = l.map { it.name }.toSet()
                    kind = when {
                        "settings.gradle" in n || "settings.gradle.kts" in n -> ProjectKind.Gradle
                        "index.html" in n -> ProjectKind.Web
                        else -> ProjectKind.Unknown
                    }
                }
            } catch (e: Exception) { toast("Gagal memuat berkas: ${e.message}") }
            filesBusy = false
        }
    }

    fun up() = loadDir(path.substringBeforeLast('/', ""))

    fun createFile(name: String) {
        val r = repo ?: return
        val full = (if (path.isEmpty()) "" else "$path/") + name.trim().trim('/')
        viewModelScope.launch {
            try {
                api.putFile(r, full, "\n".toByteArray(), "Buat ${name.trim()} via Kiln", null)
                toast("Berkas dibuat"); loadDir(path)
            } catch (e: Exception) { toast("Gagal: ${e.message}") }
        }
    }

    fun importFile(uri: Uri) {
        val r = repo ?: return
        viewModelScope.launch {
            try {
                val (name, bytes) = withContext(Dispatchers.IO) {
                    val cr = ctx.contentResolver
                    val n = cr.query(uri, null, null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
                    } ?: "berkas"
                    n to cr.openInputStream(uri)!!.use { it.readBytes() }
                }
                val full = (if (path.isEmpty()) "" else "$path/") + name
                val old = api.fileMeta(r, full)
                api.putFile(r, full, bytes, "Impor $name via Kiln", old?.sha)
                toast("$name diimpor"); loadDir(path)
            } catch (e: Exception) { toast("Gagal impor: ${e.message}") }
        }
    }

    fun deleteEntry(e: Entry) {
        val r = repo ?: return
        if (e.isDir) { toast("Folder dihapus dengan menghapus isinya satu-satu"); return }
        viewModelScope.launch {
            try { api.deleteFile(r, e.path, e.sha, "Hapus ${e.name} via Kiln"); toast("${e.name} dihapus"); loadDir(path) }
            catch (ex: Exception) { toast("Gagal hapus: ${ex.message}") }
        }
    }

    // ---------- editor ----------
    var openFile by mutableStateOf<Entry?>(null)
    var fileText by mutableStateOf<String?>(null)
    var fileError by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)

    fun openFileEntry(e: Entry) {
        val r = repo ?: return
        openFile = e; fileText = null; fileError = null
        val k = fileKind(e.name)
        if (k != FileKind.Html && k != FileKind.Text) { fileText = ""; return }
        if (e.size > 1_000_000) { fileText = ""; fileError = "Berkas terlalu besar untuk diedit di sini (> 1 MB)"; return }
        viewModelScope.launch {
            try {
                val t = api.fileText(r, e.path)
                if (t.contains('\u0000')) { fileText = ""; fileError = "Berkas biner — tidak bisa ditampilkan sebagai teks" } else fileText = t
            } catch (ex: Exception) { fileText = ""; fileError = ex.message }
        }
    }

    fun saveFile(text: String, msg: String) {
        val r = repo ?: return
        val e = openFile ?: return
        viewModelScope.launch {
            saving = true
            try {
                val sha = api.putFile(r, e.path, text.toByteArray(), msg, e.sha)
                openFile = e.copy(sha = sha, size = text.length.toLong()); fileText = text
                toast("Tersimpan ke GitHub"); loadDir(path)
            } catch (ex: Exception) { toast("Gagal menyimpan: ${ex.message}") }
            saving = false
        }
    }

    // ---------- build ----------
    var build by mutableStateOf(BuildUi())
    var runs by mutableStateOf<List<Run>>(emptyList())
    var nextVersion by mutableStateOf("1.0.0")
    private var buildJob: Job? = null

    private fun setBuild(phase: Phase, p: Float, label: String) { build = build.copy(phase = phase, progress = p, label = label, error = null) }

    fun loadVersion() {
        val r = repo ?: return
        viewModelScope.launch {
            try {
                val m = api.fileMeta(r, ".kiln/state.json")
                nextVersion = m?.let { Versioning.parse(it.text) }?.let { Versioning.next(it).version } ?: Versioning.first().version
            } catch (_: Exception) {}
        }
    }

    fun loadRuns(attach: Boolean = false) {
        val r = repo ?: return
        viewModelScope.launch {
            try {
                val l = api.runs(r)
                runs = l
                if (attach && buildJob?.isActive != true) {
                    l.firstOrNull { it.status != "completed" }?.let { run ->
                        buildJob = launch {
                            try { pollRun(r, run.id) } catch (e: CancellationException) { throw e } catch (e: Exception) { toast(e.message ?: "Error") }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun startBuild() {
        val r = repo ?: return
        if (buildJob?.isActive == true) return
        buildJob = viewModelScope.launch {
            try {
                build = BuildUi(Phase.Preparing, 0.01f, "Menyiapkan tungku...")
                setBuild(Phase.Preparing, 0.03f, "Memeriksa kunci tanda tangan...")
                ensureKeystore(r)
                setBuild(Phase.Preparing, 0.05f, "Memasang workflow Kiln...")
                ensureWorkflow(r)
                setBuild(Phase.Preparing, 0.07f, "Menghitung versi...")
                val meta = api.fileMeta(r, ".kiln/state.json")
                val cur = meta?.let { Versioning.parse(it.text) }
                val next = if (cur == null) Versioning.first() else Versioning.next(cur)
                val known = api.runs(r).map { it.id }.toSet()

                setBuild(Phase.Preparing, 0.08f, "Menyalakan api v${next.version}...")
                var tries = 0
                while (true) {
                    try {
                        api.dispatch(r, mapOf("version" to next.version, "version_code" to next.code.toString()))
                        break
                    } catch (e: ApiException) {
                        if (++tries >= 6 || e.code !in listOf(404, 422)) throw e
                        delay(3000)
                    }
                }
                api.putFile(r, ".kiln/state.json", Versioning.toJson(next).toByteArray(), "Kiln: build v${next.version}", meta?.sha)
                nextVersion = Versioning.next(next).version

                setBuild(Phase.Preparing, 0.09f, "Menunggu runner GitHub...")
                var run: Run? = null
                repeat(40) {
                    if (run == null) {
                        run = api.runs(r).firstOrNull { it.id !in known }
                        if (run == null) delay(3000)
                    }
                }
                val found = run ?: error("Runner tidak merespons. Coba lagi sebentar lagi.")
                loadRuns()
                pollRun(r, found.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                build = BuildUi(Phase.Failed, build.progress, "Gagal: ${e.message}", error = e.message)
            }
        }
    }

    fun cancelBuild() {
        val r = repo ?: return
        val id = build.runId
        viewModelScope.launch {
            try { if (id != null) api.cancel(r, id) } catch (_: Exception) {}
            buildJob?.cancel()
            build = BuildUi(Phase.Cancelled, build.progress, "Build dibatalkan", id)
            loadRuns()
        }
    }

    private fun stepLabel(n: String) = when {
        n == "Set up job" -> "Menyiapkan runner..."
        n == "Complete job" -> "Merapikan..."
        n.startsWith("Post ") -> "Membersihkan..."
        else -> n + "..."
    }

    private suspend fun pollRun(r: Repo, runId: Long) {
        while (true) {
            val run = api.runById(r, runId)
            val steps = api.jobs(r, runId).firstOrNull()?.steps.orEmpty()
            if (run.status == "completed") {
                val ok = run.conclusion == "success"
                val cancelled = run.conclusion == "cancelled"
                build = BuildUi(
                    if (ok) Phase.Success else if (cancelled) Phase.Cancelled else Phase.Failed,
                    if (ok) 1f else build.progress,
                    if (ok) "Selesai! APK sudah ada di Rilis 🔥" else if (cancelled) "Build dibatalkan" else "Build gagal — cek tab Log",
                    runId, steps, if (ok || cancelled) null else "Hasil: ${run.conclusion}",
                )
                loadRuns(); loadVersion()
                if (ok) loadReleases() else if (!cancelled) openLog(runId)
                return
            }
            val total = maxOf(steps.size, 1)
            val done = steps.count { it.status == "completed" }
            val cur = steps.firstOrNull { it.status == "in_progress" }
            val p = (0.10f + 0.89f * (done + if (cur != null) 0.5f else 0f) / total).coerceIn(0.10f, 0.99f)
            build = BuildUi(
                Phase.Running, maxOf(p, build.progress),
                if (cur != null) stepLabel(cur.name) else "Menunggu runner GitHub...", runId, steps,
            )
            delay(3000)
        }
    }

    private suspend fun ensureWorkflow(r: Repo) {
        val wanted = ctx.assets.open("kiln-build.yml").bufferedReader().use { it.readText() }
        val path = ".github/workflows/kiln-build.yml"
        val cur = api.fileMeta(r, path)
        if (cur == null || cur.text != wanted) {
            api.putFile(r, path, wanted.toByteArray(), "Kiln: pasang workflow build", cur?.sha)
        }
    }

    private suspend fun ensureKeystore(r: Repo) {
        val names = api.secretNames(r)
        val local = store.keystore(r.fullName)
        if ("KILN_KEYSTORE_B64" in names) {
            if (local == null) toast("Keystore sudah ada di GitHub tapi belum ada cadangan di HP ini. Impor backup-mu!")
            return
        }
        val ks = local ?: withContext(Dispatchers.Default) { KeystoreManager.generate(r.name) }.also { store.saveKeystore(r.fullName, it) }
        localKs = ks
        pushSecrets(r, ks)
        refreshBackup()
        toast("Keystore baru dibuat & disimpan ke ENV. Segera buat backup-nya!")
    }

    private suspend fun pushSecrets(r: Repo, k: KilnKeystore) {
        val (keyId, key) = api.publicKey(r)
        suspend fun put(n: String, v: String) = api.putSecret(r, n, KeystoreManager.seal(v, key), keyId)
        put("KILN_KEYSTORE_PASSWORD", k.password)
        put("KILN_KEY_ALIAS", k.alias)
        put("KILN_KEY_PASSWORD", k.password)
        put("KILN_KEYSTORE_B64", k.b64) // terakhir: kalau ini ada, berarti semuanya sudah lengkap
    }

    // ---------- log ----------
    var logRunId by mutableStateOf<Long?>(null)
    var logLines by mutableStateOf<List<String>>(emptyList())
    var logBusy by mutableStateOf(false)

    fun openLog(runId: Long) { logRunId = runId; refreshLog() }

    fun refreshLog() {
        val r = repo ?: return
        val id = logRunId ?: return
        viewModelScope.launch {
            logBusy = true
            try {
                val sb = StringBuilder()
                for (j in api.jobs(r, id)) {
                    sb.append("▶ ${j.name}\n")
                    sb.append(runCatching { api.jobLogs(r, j.id) }.getOrElse { "(log belum tersedia: ${it.message})\n" })
                }
                val ts = Regex("^\\d{4}-\\d\\d-\\d\\dT[\\d:.]+Z ")
                val ansi = Regex("\u001B\\[[0-9;]*m")
                logLines = sb.lines().map { ansi.replace(ts.replace(it, ""), "") }.takeLast(3000)
            } catch (e: Exception) { toast("Gagal memuat log: ${e.message}") }
            logBusy = false
        }
    }

    // ---------- rilis ----------
    var releases by mutableStateOf<List<Release>>(emptyList())
    var releasesBusy by mutableStateOf(false)
    var dlId by mutableStateOf<Long?>(null)
    var dlProgress by mutableStateOf(0f)

    fun loadReleases() {
        val r = repo ?: return
        viewModelScope.launch {
            releasesBusy = true
            try { releases = api.releases(r) } catch (_: Exception) {}
            releasesBusy = false
        }
    }

    fun deleteRelease(rel: Release) {
        val r = repo ?: return
        viewModelScope.launch {
            try { api.deleteRelease(r, rel); toast("Rilis ${rel.tag} dihapus"); loadReleases() }
            catch (e: Exception) { toast("Gagal hapus rilis: ${e.message}") }
        }
    }

    fun install(a: Asset) {
        val r = repo ?: return
        viewModelScope.launch {
            dlId = a.id; dlProgress = 0f
            try {
                val f = File(ctx.cacheDir, "apk").apply { mkdirs() }.resolve(a.name)
                api.downloadAsset(r, a.id, f) { dlProgress = it }
                Installer.install(ctx, f)
            } catch (e: Exception) { toast("Gagal mengunduh: ${e.message}") }
            dlId = null
        }
    }

    // ---------- backup ----------
    var localKs by mutableStateOf<KilnKeystore?>(null)
    var secretsOk by mutableStateOf<Boolean?>(null)
    var fingerprint by mutableStateOf("")

    fun refreshBackup() {
        val r = repo ?: return
        viewModelScope.launch {
            localKs = store.keystore(r.fullName)
            fingerprint = localKs?.let { withContext(Dispatchers.Default) { KeystoreManager.fingerprint(it) } } ?: ""
            secretsOk = try { "KILN_KEYSTORE_B64" in api.secretNames(r) } catch (_: Exception) { null }
        }
    }

    fun exportKeystores(uri: Uri, pass: String) {
        viewModelScope.launch {
            try {
                val all = store.allKeystores()
                if (all.isEmpty()) { toast("Belum ada keystore untuk dibackup"); return@launch }
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(Backup.pack(all, pass)) } }
                toast("Backup tersimpan (${all.size} keystore)")
            } catch (e: Exception) { toast("Backup gagal: ${e.message}") }
        }
    }

    fun importKeystores(uri: Uri, pass: String) {
        viewModelScope.launch {
            try {
                val map = withContext(Dispatchers.IO) { Backup.unpack(ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }, pass) }
                map.forEach { (repoName, k) -> store.saveKeystore(repoName, k) }
                toast("${map.size} keystore dipulihkan"); refreshBackup()
            } catch (e: Exception) { toast(e.message ?: "Gagal memulihkan") }
        }
    }

    fun republishSecrets() {
        val r = repo ?: return
        val k = store.keystore(r.fullName) ?: run { toast("Tidak ada keystore lokal. Impor backup dulu."); return }
        viewModelScope.launch {
            try { pushSecrets(r, k); toast("Keystore dikirim ulang ke ENV GitHub"); refreshBackup() }
            catch (e: Exception) { toast("Gagal: ${e.message}") }
        }
    }

    fun exportZip(uri: Uri) {
        val r = repo ?: return
        viewModelScope.launch {
            try {
                toast("Mengunduh kode...")
                ctx.contentResolver.openOutputStream(uri)!!.use { api.zipball(r, it) }
                toast("Backup kode (ZIP) tersimpan")
            } catch (e: Exception) { toast("Gagal: ${e.message}") }
        }
    }
}
