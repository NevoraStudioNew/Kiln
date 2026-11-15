/*
 * Kiln — Bakar script jadi APK, langsung dari HP.
 * Open source di bawah Lisensi MIT.
 * Copyright (c) 2026 Nevora Studio
 * Saluran resmi: https://whatsapp.com/channel/0029VbDwlX6EawdkN0IZm713
 */

package id.nevora.kiln.data

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Backup keystore terenkripsi: PBKDF2-SHA256 (120rb iterasi) + AES-256-GCM. */
object Backup {
    private const val MAGIC = "KILNBK1"

    fun pack(map: Map<String, KilnKeystore>, pass: String): ByteArray {
        val root = JSONObject().put("v", 1).put("created", System.currentTimeMillis())
        val ks = JSONObject()
        map.forEach { (repo, k) -> ks.put(repo, JSONObject().put("b64", k.b64).put("pass", k.password).put("alias", k.alias)) }
        root.put("keystores", ks)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(pass, salt), GCMParameterSpec(128, iv))
        return MAGIC.toByteArray() + salt + iv + c.doFinal(root.toString().toByteArray())
    }

    fun unpack(data: ByteArray, pass: String): Map<String, KilnKeystore> {
        val m = MAGIC.length
        require(data.size > m + 28 && String(data, 0, m) == MAGIC) { "Bukan berkas backup Kiln" }
        val salt = data.copyOfRange(m, m + 16)
        val iv = data.copyOfRange(m + 16, m + 28)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(pass, salt), GCMParameterSpec(128, iv))
        val plain = try { c.doFinal(data, m + 28, data.size - m - 28) } catch (e: Exception) { throw IllegalArgumentException("Password salah atau berkas rusak") }
        val ks = JSONObject(String(plain)).getJSONObject("keystores")
        return ks.keys().asSequence().associateWith {
            val o = ks.getJSONObject(it)
            KilnKeystore(o.getString("b64"), o.getString("pass"), o.getString("alias"))
        }
    }

    private fun key(pass: String, salt: ByteArray) = SecretKeySpec(
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pass.toCharArray(), salt, 120_000, 256)).encoded, "AES",
    )
}
