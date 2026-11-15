/*
 * Kiln — Bakar script jadi APK, langsung dari HP.
 * Open source di bawah Lisensi MIT.
 * Copyright (c) 2026 Nevora Studio
 * Saluran resmi: https://whatsapp.com/channel/0029VbDwlX6EawdkN0IZm713
 */

package id.nevora.kiln.data

import org.json.JSONObject

/**
 * Versi pertama 1.0.0. Tiap build menambah 0.0.1. Saat patch menyentuh 100,
 * naik ke minor berikutnya (1.0.99 → 1.1.0). Hal yang sama berlaku untuk minor → major.
 */
object Versioning {
    fun first() = VersionState("1.0.0", 1)

    fun next(cur: VersionState): VersionState {
        val p = cur.version.split(".").map { it.toIntOrNull() ?: 0 }
        var major = p.getOrElse(0) { 1 }
        var minor = p.getOrElse(1) { 0 }
        var patch = p.getOrElse(2) { 0 } + 1
        if (patch >= 100) { patch = 0; minor++ }
        if (minor >= 100) { minor = 0; major++ }
        return VersionState("$major.$minor.$patch", cur.code + 1)
    }

    fun parse(text: String): VersionState? = runCatching {
        val o = JSONObject(text)
        VersionState(o.getString("version"), o.getInt("code"))
    }.getOrNull()

    fun toJson(v: VersionState): String =
        JSONObject().put("version", v.version).put("code", v.code).toString(2) + "\n"
}
