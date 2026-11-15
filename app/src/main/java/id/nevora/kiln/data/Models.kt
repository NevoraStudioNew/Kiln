/*
 * Kiln — Bakar script jadi APK, langsung dari HP.
 * Open source di bawah Lisensi MIT.
 * Copyright (c) 2026 Nevora Studio
 * Saluran resmi: https://whatsapp.com/channel/0029VbDwlX6EawdkN0IZm713
 */

package id.nevora.kiln.data

data class Repo(
    val fullName: String, val name: String, val owner: String,
    val isPrivate: Boolean, val branch: String, val description: String, val updatedAt: String,
)
data class Entry(val name: String, val path: String, val isDir: Boolean, val sha: String, val size: Long)
data class Meta(val sha: String, val text: String)
data class Step(val number: Int, val name: String, val status: String, val conclusion: String?)
data class JobInfo(val id: Long, val name: String, val status: String, val conclusion: String?, val steps: List<Step>)
data class Run(
    val id: Long, val status: String, val conclusion: String?, val title: String,
    val number: Int, val createdAt: String, val createdMs: Long, val url: String,
)
data class Asset(val id: Long, val name: String, val size: Long, val downloads: Int)
data class Release(val id: Long, val tag: String, val name: String, val body: String, val createdAt: String, val assets: List<Asset>)
data class KilnKeystore(val b64: String, val password: String, val alias: String)
data class VersionState(val version: String, val code: Int)

class ApiException(val code: Int, message: String) : Exception(message)

enum class FileKind { Html, Text, Image, Video, Audio, Binary }
enum class ProjectKind(val label: String) {
    Gradle("Android Gradle"), Web("Web → APK"), Unknown("Tidak dikenali")
}

fun fileKind(name: String): FileKind = when (name.substringAfterLast('.', "").lowercase()) {
    "html", "htm", "svg" -> FileKind.Html
    "png", "jpg", "jpeg", "webp", "bmp", "ico" -> FileKind.Image
    "mp4", "webm", "mkv", "3gp", "mov" -> FileKind.Video
    "mp3", "ogg", "wav", "m4a", "aac", "flac", "opus" -> FileKind.Audio
    "gif", "apk", "zip", "jar", "aar", "so", "class", "dex", "keystore", "jks", "p12",
    "pdf", "ttf", "otf", "woff", "woff2", "7z", "gz", "rar" -> FileKind.Binary
    else -> FileKind.Text
}
