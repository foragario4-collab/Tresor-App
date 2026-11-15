package app.glass.tresor.chat

import app.glass.tresor.crypto.Crypto
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Ende-zu-Ende-Verschlüsselung für den Chat.
 *
 * Jede Nachricht:
 *  - neues Einmal-Schlüsselpaar (X25519) des Absenders
 *  - zwei Diffie-Hellman-Werte: Einmal↔Empfänger und Absender↔Empfänger
 *    → nur der echte Absender kann die Nachricht erzeugen, nur der Empfänger sie lesen
 *  - HKDF-SHA256 → 256-Bit-Schlüssel, AES-256-GCM, beide öffentlichen Schlüssel als AAD
 */
object ChatCrypto {
    private val rng = SecureRandom()
    private const val INFO = "tresor-chat-v1"

    data class KeyPair(val priv: ByteArray, val pub: ByteArray)

    fun newKeyPair(): KeyPair {
        val p = X25519PrivateKeyParameters(rng)
        return KeyPair(p.encoded, p.generatePublicKey().encoded)
    }

    private fun dh(priv: ByteArray, pub: ByteArray): ByteArray {
        val a = X25519Agreement()
        a.init(X25519PrivateKeyParameters(priv, 0))
        val out = ByteArray(a.agreementSize)
        a.calculateAgreement(X25519PublicKeyParameters(pub, 0), out, 0)
        if (out.all { it == 0.toByte() }) throw IllegalStateException("ungültiger Schlüssel")
        return out
    }

    private fun kdf(ikm: ByteArray, salt: ByteArray, sender: ByteArray, recipient: ByteArray): ByteArray {
        val g = HKDFBytesGenerator(SHA256Digest())
        g.init(HKDFParameters(ikm, salt, INFO.toByteArray() + sender + recipient))
        val out = ByteArray(32)
        g.generateBytes(out, 0, 32)
        return out
    }

    /** Verschlüsselt [plain] von [me] an [recipientPub]. Ergebnis = Übertragungs-Paket (JSON-Bytes). */
    fun seal(me: KeyPair, recipientPub: ByteArray, plain: ByteArray): ByteArray {
        val e = newKeyPair()
        val ikm = dh(e.priv, recipientPub) + dh(me.priv, recipientPub)
        val key = kdf(ikm, e.pub, me.pub, recipientPub)
        val nonce = Crypto.random(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        c.updateAAD(me.pub + recipientPub + e.pub)
        val ct = c.doFinal(plain)
        Crypto.wipe(ikm); Crypto.wipe(key); Crypto.wipe(e.priv)
        return JSONObject()
            .put("v", 1)
            .put("f", Crypto.b64(me.pub))
            .put("e", Crypto.b64(e.pub))
            .put("n", Crypto.b64(nonce))
            .put("c", Crypto.b64(ct))
            .toString().toByteArray()
    }

    data class Opened(val senderPub: ByteArray, val plain: ByteArray)

    /** Entschlüsselt ein Paket an [me]. Wirft bei Manipulation oder falschem Empfänger. */
    fun open(me: KeyPair, frame: ByteArray): Opened {
        val o = JSONObject(String(frame))
        require(o.getInt("v") == 1) { "Version" }
        val from = Crypto.unb64(o.getString("f"))
        val ePub = Crypto.unb64(o.getString("e"))
        val nonce = Crypto.unb64(o.getString("n"))
        val ct = Crypto.unb64(o.getString("c"))
        require(from.size == 32 && ePub.size == 32 && nonce.size == 12) { "Format" }
        val ikm = dh(me.priv, ePub) + dh(me.priv, from)
        val key = kdf(ikm, ePub, from, me.pub)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        c.updateAAD(from + me.pub + ePub)
        val plain = c.doFinal(ct)
        Crypto.wipe(ikm); Crypto.wipe(key)
        return Opened(from, plain)
    }

    /** Sicherheitsnummer zum Vergleichen: gleich auf beiden Handys. */
    fun safetyNumber(a: ByteArray, b: ByteArray): String {
        val (x, y) = if (Crypto.b64(a) < Crypto.b64(b)) a to b else b to a
        val h = MessageDigest.getInstance("SHA-256").digest(x + y)
        // 6 Blöcke à 5 Ziffern, z. B. "04821 77310 …"
        return (0 until 6).joinToString(" ") { i ->
            val v = ((h[i * 3].toInt() and 0xff) shl 16) or ((h[i * 3 + 1].toInt() and 0xff) shl 8) or (h[i * 3 + 2].toInt() and 0xff)
            "%05d".format(v % 100000)
        }
    }
}
