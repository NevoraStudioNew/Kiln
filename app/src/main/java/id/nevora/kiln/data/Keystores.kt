/*
 * Kiln — Bakar script jadi APK, langsung dari HP.
 * Open source di bawah Lisensi MIT.
 * Copyright (c) 2026 Nevora Studio
 * Saluran resmi: https://whatsapp.com/channel/0029VbDwlX6EawdkN0IZm713
 */

package id.nevora.kiln.data

import android.util.Base64
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Box
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Membuat keystore (PKCS12) langsung di HP — tanpa JKS manual dan tanpa library crypto tambahan —
 * lalu mengenkripsi nilainya (libsodium sealed box) agar bisa disimpan sebagai GitHub Secrets (ENV).
 */
object KeystoreManager {
    private val rnd = SecureRandom()

    fun generate(appName: String): KilnKeystore {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, rnd) }.generateKeyPair()
        val pass = randomPass()
        val alias = "kiln"
        val cert = selfSigned(kp, appName.filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }.ifBlank { "Kiln App" })
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry(alias, kp.private, pass.toCharArray(), arrayOf(cert))
        val out = ByteArrayOutputStream()
        ks.store(out, pass.toCharArray())
        return KilnKeystore(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP), pass, alias)
    }

    fun fingerprint(k: KilnKeystore): String = runCatching {
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(ByteArrayInputStream(Base64.decode(k.b64, Base64.DEFAULT)), k.password.toCharArray())
        val der = ks.getCertificate(k.alias).encoded
        MessageDigest.getInstance("SHA-256").digest(der).joinToString(":") { "%02X".format(it) }
    }.getOrDefault("-")

    /** crypto_box_seal — format yang diminta GitHub untuk mengisi Actions secret. */
    fun seal(value: String, publicKeyB64: String): String {
        val ls = LazySodiumAndroid(SodiumAndroid())
        val pk = Base64.decode(publicKeyB64, Base64.DEFAULT)
        val msg = value.toByteArray()
        val out = ByteArray(msg.size + Box.SEALBYTES)
        check(ls.cryptoBoxSeal(out, msg, msg.size.toLong(), pk)) { "Gagal mengenkripsi secret" }
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun randomPass(): String {
        val chars = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..28).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
    }

    // ---- X.509 self-signed (DER manual) ----
    private fun len(n: Int): ByteArray = when {
        n < 128 -> byteArrayOf(n.toByte())
        n < 256 -> byteArrayOf(0x81.toByte(), n.toByte())
        else -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
    }
    private fun tlv(tag: Int, v: ByteArray) = byteArrayOf(tag.toByte()) + len(v.size) + v
    private fun seq(vararg p: ByteArray) = tlv(0x30, p.fold(ByteArray(0)) { a, b -> a + b })
    private fun set(vararg p: ByteArray) = tlv(0x31, p.fold(ByteArray(0)) { a, b -> a + b })
    private fun oid(vararg b: Int) = tlv(0x06, ByteArray(b.size) { b[it].toByte() })
    private fun utf8(s: String) = tlv(0x0C, s.toByteArray())
    private fun fmt(p: String) = SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun selfSigned(kp: KeyPair, cn: String): X509Certificate {
        val sigAlg = seq(oid(0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x0B), tlv(0x05, ByteArray(0)))
        val name = seq(
            set(seq(oid(0x55, 0x04, 0x0A), utf8("Kiln"))),
            set(seq(oid(0x55, 0x04, 0x03), utf8(cn))),
        )
        val now = Date(System.currentTimeMillis() - 60_000)
        val end = Date(System.currentTimeMillis() + 36500L * 24 * 3600 * 1000)
        val serial = tlv(0x02, BigInteger(63, rnd).add(BigInteger.ONE).toByteArray())
        val tbs = seq(
            tlv(0xA0, tlv(0x02, byteArrayOf(2))),
            serial, sigAlg, name,
            seq(tlv(0x17, fmt("yyMMddHHmmss'Z'").format(now).toByteArray()), tlv(0x18, fmt("yyyyMMddHHmmss'Z'").format(end).toByteArray())),
            name, kp.public.encoded,
        )
        val sig = Signature.getInstance("SHA256withRSA").run { initSign(kp.private); update(tbs); sign() }
        val der = seq(tbs, sigAlg, tlv(0x03, byteArrayOf(0) + sig))
        return CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der)) as X509Certificate
    }
}
