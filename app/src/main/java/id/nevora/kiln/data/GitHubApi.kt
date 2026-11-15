/*
 * Kiln — Bakar script jadi APK, langsung dari HP.
 * Open source di bawah Lisensi MIT.
 * Copyright (c) 2026 Nevora Studio
 * Saluran resmi: https://whatsapp.com/channel/0029VbDwlX6EawdkN0IZm713
 */

package id.nevora.kiln.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit

private const val RAW = "application/vnd.github.raw+json"
private fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k)

/** Klien GitHub REST API tipis — semua yang dibutuhkan Kiln, tanpa library tambahan. */
class GitHubApi(private val token: () -> String) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
    private val base = "https://api.github.com"

    private fun req(url: String, accept: String = "application/vnd.github+json") = Request.Builder().url(url)
        .header("Authorization", "Bearer ${token()}")
        .header("Accept", accept)
        .header("X-GitHub-Api-Version", "2022-11-28")

    private suspend fun <T> exec(r: Request, parse: (Response) -> T): T = withContext(Dispatchers.IO) {
        http.newCall(r).execute().use { resp ->
            if (!resp.isSuccessful) {
                val msg = runCatching { JSONObject(resp.body?.string().orEmpty()).optString("message") }.getOrDefault("")
                throw ApiException(resp.code, msg.ifBlank { "HTTP ${resp.code}" })
            }
            parse(resp)
        }
    }

    private fun json(j: JSONObject) = j.toString().toRequestBody("application/json".toMediaType())
    private suspend fun obj(path: String) = exec(req(base + path).build()) { JSONObject(it.body!!.string()) }
    private suspend fun arr(path: String) = exec(req(base + path).build()) { JSONArray(it.body!!.string()) }
    private fun enc(p: String) = p.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
    private fun contents(r: Repo, p: String) =
        "/repos/${r.fullName}/contents" + (if (p.isEmpty()) "" else "/" + enc(p)) + "?ref=" + URLEncoder.encode(r.branch, "UTF-8")

    // ---------- akun & repo ----------
    suspend fun user(): String = obj("/user").getString("login")

    suspend fun repos(): List<Repo> {
        val out = mutableListOf<Repo>()
        for (page in 1..3) {
            val a = arr("/user/repos?per_page=100&sort=updated&page=$page")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out += Repo(
                    o.getString("full_name"), o.getString("name"), o.getJSONObject("owner").getString("login"),
                    o.getBoolean("private"), o.str("default_branch").ifBlank { "main" }, o.str("description"), o.str("updated_at"),
                )
            }
            if (a.length() < 100) break
        }
        return out
    }

    // ---------- berkas ----------
    suspend fun listDir(r: Repo, path: String): List<Entry> = try {
        val a = arr(contents(r, path))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Entry(o.getString("name"), o.getString("path"), o.getString("type") == "dir", o.str("sha"), o.optLong("size"))
        }.sortedWith(compareByDescending<Entry> { it.isDir }.thenBy { it.name.lowercase() })
    } catch (e: ApiException) {
        if (e.code == 404) emptyList() else throw e
    }

    suspend fun fileText(r: Repo, path: String): String =
        exec(req(base + contents(r, path), RAW).build()) { it.body!!.string() }

    suspend fun fileMeta(r: Repo, path: String): Meta? = try {
        val o = obj(contents(r, path))
        Meta(o.getString("sha"), String(Base64.decode(o.str("content"), Base64.DEFAULT)))
    } catch (e: ApiException) {
        if (e.code == 404) null else throw e
    }

    suspend fun putFile(r: Repo, path: String, bytes: ByteArray, message: String, sha: String?): String {
        val j = JSONObject().put("message", message)
            .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP)).put("branch", r.branch)
        if (sha != null) j.put("sha", sha)
        return exec(req("$base/repos/${r.fullName}/contents/${enc(path)}").put(json(j)).build()) {
            JSONObject(it.body!!.string()).getJSONObject("content").getString("sha")
        }
    }

    suspend fun deleteFile(r: Repo, path: String, sha: String, message: String) {
        val j = JSONObject().put("message", message).put("sha", sha).put("branch", r.branch)
        exec(req("$base/repos/${r.fullName}/contents/${enc(path)}").delete(json(j)).build()) { }
    }

    // untuk preview media / WebView
    fun rawUrl(r: Repo, path: String) = base + contents(r, path)
    fun rawHeaders() = mapOf("Authorization" to "Bearer ${token()}", "Accept" to RAW)
    fun rawBytesBlocking(r: Repo, path: String): ByteArray? = try {
        http.newCall(req(rawUrl(r, path), RAW).build()).execute().use { if (it.isSuccessful) it.body!!.bytes() else null }
    } catch (e: Exception) { null }

    // ---------- secrets (ENV) ----------
    suspend fun secretNames(r: Repo): Set<String> {
        val a = obj("/repos/${r.fullName}/actions/secrets?per_page=100").getJSONArray("secrets")
        return (0 until a.length()).map { a.getJSONObject(it).getString("name") }.toSet()
    }
    suspend fun publicKey(r: Repo): Pair<String, String> {
        val o = obj("/repos/${r.fullName}/actions/secrets/public-key")
        return o.getString("key_id") to o.getString("key")
    }
    suspend fun putSecret(r: Repo, name: String, sealed: String, keyId: String) {
        val j = JSONObject().put("encrypted_value", sealed).put("key_id", keyId)
        exec(req("$base/repos/${r.fullName}/actions/secrets/$name").put(json(j)).build()) { }
    }

    // ---------- Actions ----------
    suspend fun dispatch(r: Repo, inputs: Map<String, String>) {
        val j = JSONObject().put("ref", r.branch).put("inputs", JSONObject(inputs))
        exec(req("$base/repos/${r.fullName}/actions/workflows/kiln-build.yml/dispatches").post(json(j)).build()) { }
    }

    private fun parseRun(o: JSONObject) = Run(
        o.getLong("id"), o.str("status"), o.str("conclusion").ifBlank { null }, o.str("display_title"),
        o.optInt("run_number"), o.str("created_at"),
        runCatching { Instant.parse(o.str("created_at")).toEpochMilli() }.getOrDefault(0L), o.str("html_url"),
    )

    suspend fun runs(r: Repo): List<Run> = try {
        val a = obj("/repos/${r.fullName}/actions/workflows/kiln-build.yml/runs?per_page=15").getJSONArray("workflow_runs")
        (0 until a.length()).map { parseRun(a.getJSONObject(it)) }
    } catch (e: ApiException) {
        if (e.code == 404) emptyList() else throw e
    }

    suspend fun runById(r: Repo, id: Long): Run = parseRun(obj("/repos/${r.fullName}/actions/runs/$id"))

    suspend fun jobs(r: Repo, runId: Long): List<JobInfo> {
        val a = obj("/repos/${r.fullName}/actions/runs/$runId/jobs").getJSONArray("jobs")
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val s = o.optJSONArray("steps") ?: JSONArray()
            JobInfo(
                o.getLong("id"), o.str("name"), o.str("status"), o.str("conclusion").ifBlank { null },
                (0 until s.length()).map { k ->
                    val p = s.getJSONObject(k)
                    Step(p.optInt("number"), p.str("name"), p.str("status"), p.str("conclusion").ifBlank { null })
                },
            )
        }
    }

    suspend fun jobLogs(r: Repo, jobId: Long): String =
        exec(req("$base/repos/${r.fullName}/actions/jobs/$jobId/logs").build()) { it.body!!.string() }

    suspend fun cancel(r: Repo, runId: Long) {
        exec(req("$base/repos/${r.fullName}/actions/runs/$runId/cancel").post("".toRequestBody()).build()) { }
    }

    // ---------- Releases ----------
    suspend fun releases(r: Repo): List<Release> {
        val a = arr("/repos/${r.fullName}/releases?per_page=30")
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val s = o.getJSONArray("assets")
            Release(
                o.getLong("id"), o.str("tag_name"), o.str("name"), o.str("body"), o.str("created_at"),
                (0 until s.length()).map { k ->
                    val p = s.getJSONObject(k)
                    Asset(p.getLong("id"), p.str("name"), p.optLong("size"), p.optInt("download_count"))
                },
            )
        }
    }

    suspend fun deleteRelease(r: Repo, rel: Release) {
        exec(req("$base/repos/${r.fullName}/releases/${rel.id}").delete().build()) { }
        runCatching { exec(req("$base/repos/${r.fullName}/git/refs/tags/${rel.tag}").delete().build()) { } }
    }

    suspend fun downloadAsset(r: Repo, id: Long, dest: File, onProgress: (Float) -> Unit) {
        exec(req("$base/repos/${r.fullName}/releases/assets/$id", "application/octet-stream").build()) { resp ->
            val total = resp.body!!.contentLength()
            resp.body!!.byteStream().use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress(read.toFloat() / total)
                    }
                }
            }
        }
    }

    suspend fun zipball(r: Repo, out: OutputStream) {
        exec(req("$base/repos/${r.fullName}/zipball/${URLEncoder.encode(r.branch, "UTF-8")}").build()) {
            it.body!!.byteStream().use { i -> i.copyTo(out) }
        }
    }
}
