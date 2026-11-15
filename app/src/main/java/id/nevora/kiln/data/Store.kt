/*
 * Kiln — Bakar script jadi APK, langsung dari HP.
 * Open source di bawah Lisensi MIT.
 * Copyright (c) 2026 Nevora Studio
 * Saluran resmi: https://whatsapp.com/channel/0029VbDwlX6EawdkN0IZm713
 */

package id.nevora.kiln.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

/** Penyimpanan terenkripsi (AES-256) untuk token GitHub dan salinan lokal keystore. */
class Store(ctx: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        ctx, "kiln_secure",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(v) { prefs.edit().putString("token", v).apply() }

    var login: String
        get() = prefs.getString("login", "") ?: ""
        set(v) { prefs.edit().putString("login", v).apply() }

    private fun decode(s: String): KilnKeystore? = runCatching {
        val o = JSONObject(s)
        KilnKeystore(o.getString("b64"), o.getString("pass"), o.getString("alias"))
    }.getOrNull()

    fun keystore(repo: String): KilnKeystore? = prefs.getString("ks:$repo", null)?.let { decode(it) }

    fun saveKeystore(repo: String, k: KilnKeystore) {
        val o = JSONObject().put("b64", k.b64).put("pass", k.password).put("alias", k.alias)
        prefs.edit().putString("ks:$repo", o.toString()).apply()
    }

    fun allKeystores(): Map<String, KilnKeystore> = prefs.all
        .filterKeys { it.startsWith("ks:") }
        .mapNotNull { (k, v) -> (v as? String)?.let { decode(it) }?.let { k.removePrefix("ks:") to it } }
        .toMap()
}
