package app.glass.tresor.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Grundbausteine:
 *  - AES-256-GCM für alle Daten (verschlüsselt + erkennt Manipulation)
 *  - PBKDF2-HMAC-SHA512 macht aus PIN/Passwort einen Schlüssel
 */
object Crypto {
    private val rng = SecureRandom()
    const val PBKDF2_ITERATIONS = 210_000
    private const val GCM_TAG_BITS = 128
    const val IV_LEN = 12

    fun random(n: Int): ByteArray = ByteArray(n).also { rng.nextBytes(it) }

    fun deriveKey(secret: CharArray, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): ByteArray {
        val spec = PBEKeySpec(secret, salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun seal(key: ByteArray, plain: ByteArray, aad: ByteArray? = null): ByteArray {
        val iv = random(IV_LEN)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        if (aad != null) c.updateAAD(aad)
        return iv + c.doFinal(plain)
    }

    fun open(key: ByteArray, blob: ByteArray, aad: ByteArray? = null): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, blob, 0, IV_LEN))
        if (aad != null) c.updateAAD(aad)
        return c.doFinal(blob, IV_LEN, blob.size - IV_LEN)
    }

    fun sha512(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-512").digest(data)

    fun wipe(b: ByteArray?) {
        b?.fill(0)
    }

    fun wipe(c: CharArray?) {
        c?.fill('\u0000')
    }

    fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
    fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    /** Schreibt erst in eine Temp-Datei und benennt dann um – nie halb geschriebene Dateien. */
    fun writeAtomic(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.outputStream().use { it.write(bytes); it.fd.sync() }
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}

/**
 * Schlüssel im Hardware-Chip (Android Keystore, auf Pixel/GrapheneOS StrongBox = Titan M).
 * Diese Schlüssel verlassen den Chip nie.
 */
object HwKeys {
    private const val PROVIDER = "AndroidKeyStore"
    const val PEPPER_ALIAS = "tresor_hw_pepper"

    private fun ks(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    fun getOrCreate(alias: String, requireAuth: Boolean): SecretKey {
        (ks().getKey(alias, null) as? SecretKey)?.let { return it }
        return generate(alias, requireAuth, strongBox = true)
            ?: generate(alias, requireAuth, strongBox = false)
            ?: throw IllegalStateException("Keystore nicht verfügbar")
    }

    private fun generate(alias: String, requireAuth: Boolean, strongBox: Boolean): SecretKey? {
        return try {
            val b = KeyGenParameterSpec.Builder(
                alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
            if (requireAuth) {
                b.setUserAuthenticationRequired(true)
                b.setUserAuthenticationParameters(
                    0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                )
                b.setInvalidatedByBiometricEnrollment(false)
            }
            if (strongBox) b.setIsStrongBoxBacked(true)
            val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            g.init(b.build())
            g.generateKey()
        } catch (e: Exception) {
            if (strongBox) null else throw e
        }
    }

    fun delete(alias: String) {
        try {
            ks().deleteEntry(alias)
        } catch (_: Exception) {
        }
    }

    fun deletePrefix(prefix: String) {
        try {
            val k = ks()
            k.aliases().toList().filter { it.startsWith(prefix) }.forEach { k.deleteEntry(it) }
        } catch (_: Exception) {
        }
    }

    fun encryptCipher(alias: String, requireAuth: Boolean): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, getOrCreate(alias, requireAuth))
        }

    fun decryptCipher(alias: String, iv: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            val key = ks().getKey(alias, null) as? SecretKey
                ?: throw IllegalStateException("Schlüssel fehlt")
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        }

    /** Zusätzliche Hardware-Schicht: Brute-Force geht nur noch auf diesem Gerät. */
    fun pepperSeal(plain: ByteArray): ByteArray {
        val c = encryptCipher(PEPPER_ALIAS, false)
        return c.iv + c.doFinal(plain)
    }

    fun pepperOpen(blob: ByteArray): ByteArray {
        val c = decryptCipher(PEPPER_ALIAS, blob.copyOfRange(0, Crypto.IV_LEN))
        return c.doFinal(blob, Crypto.IV_LEN, blob.size - Crypto.IV_LEN)
    }
}
