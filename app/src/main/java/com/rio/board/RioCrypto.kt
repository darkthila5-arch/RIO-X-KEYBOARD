package com.rio.board

import android.util.Base64
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Message locking: password -> PBKDF2-HMAC-SHA256 (100 000 rounds, random salt) -> AES-256-GCM.
 *
 * Locked text looks like   🔒RIO:<base64url>   and contains
 *   [version 1][salt 16][iv 12][cipher text + 16 byte tag]
 * The GCM tag also proves the password was right and the text was not changed.
 * Everything is standard, so the same message opens on any phone that has RIO BOARD.
 */
object RioCrypto {
    const val PREFIX = "🔒RIO:"
    private const val MARKER = "RIO:"
    private const val VERSION: Byte = 1
    private const val ITERATIONS = 100_000
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val MIN_BYTES = 1 + SALT_LEN + IV_LEN + 16
    private val AAD = "RIO1".toByteArray(Charsets.US_ASCII)
    private val B64 = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

    sealed class Result {
        class Ok(val text: String) : Result()
        object WrongPassword : Result()
        object Invalid : Result()
    }

    /** Blocking (about a second): call from a background thread. */
    fun encrypt(message: String, password: String): String {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(AAD)
        val ct = cipher.doFinal(message.toByteArray(Charsets.UTF_8))
        val blob = ByteArray(1 + SALT_LEN + IV_LEN + ct.size)
        blob[0] = VERSION
        System.arraycopy(salt, 0, blob, 1, SALT_LEN)
        System.arraycopy(iv, 0, blob, 1 + SALT_LEN, IV_LEN)
        System.arraycopy(ct, 0, blob, 1 + SALT_LEN + IV_LEN, ct.size)
        return PREFIX + Base64.encodeToString(blob, B64)
    }

    /** Finds the locked part inside any text (the person may have copied extra words around it). */
    fun findToken(text: String): String? {
        val i = text.indexOf(MARKER)
        if (i < 0) return null
        var j = i + MARKER.length
        val sb = StringBuilder()
        while (j < text.length) {
            val c = text[j]
            if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_') {
                sb.append(c)
                j++
            } else break
        }
        return if (sb.length >= 60) sb.toString() else null
    }

    /** Blocking (about a second): call from a background thread. */
    fun decrypt(text: String, password: String): Result {
        val token = findToken(text) ?: return Result.Invalid
        val blob = try {
            Base64.decode(token, B64)
        } catch (e: IllegalArgumentException) {
            return Result.Invalid
        }
        if (blob.size < MIN_BYTES || blob[0] != VERSION) return Result.Invalid
        val salt = blob.copyOfRange(1, 1 + SALT_LEN)
        val iv = blob.copyOfRange(1 + SALT_LEN, 1 + SALT_LEN + IV_LEN)
        val ct = blob.copyOfRange(1 + SALT_LEN + IV_LEN, blob.size)
        return try {
            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(AAD)
            Result.Ok(String(cipher.doFinal(ct), Charsets.UTF_8))
        } catch (e: GeneralSecurityException) {
            Result.WrongPassword
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        // normalise so the same Sinhala/emoji password typed on another keyboard gives the same key
        val pw = Normalizer.normalize(password, Normalizer.Form.NFC).toByteArray(Charsets.UTF_8)
        return pbkdf2(pw, salt, ITERATIONS, 32)
    }

    /** PBKDF2-HMAC-SHA256 written out by hand so every Android version gives identical keys. */
    private fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, dkLen: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        val hLen = 32
        val blocks = (dkLen + hLen - 1) / hLen
        val out = ByteArray(blocks * hLen)
        for (b in 1..blocks) {
            mac.update(salt)
            mac.update(byteArrayOf((b ushr 24).toByte(), (b ushr 16).toByte(), (b ushr 8).toByte(), b.toByte()))
            var u = mac.doFinal()
            val t = u.copyOf()
            for (i in 1 until iterations) {
                u = mac.doFinal(u)
                for (j in 0 until hLen) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            System.arraycopy(t, 0, out, (b - 1) * hLen, hLen)
        }
        return out.copyOf(dkLen)
    }
}
