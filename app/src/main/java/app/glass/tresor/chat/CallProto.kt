package app.glass.tresor.chat

import app.glass.tresor.crypto.Crypto
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** G.711 µ-law: 8 kHz Sprache, 8 Bit pro Abtastwert (64 kbit/s) – robust und ohne Codec-Bibliothek. */
object Mulaw {
    private const val BIAS = 0x84
    private const val CLIP = 32635

    fun encode(sample: Short): Byte {
        var s = sample.toInt()
        val sign = if (s < 0) 0x80 else 0
        if (s < 0) s = -s
        if (s > CLIP) s = CLIP
        s += BIAS
        var exp = 7
        var mask = 0x4000
        while (exp > 0 && (s and mask) == 0) {
            exp--; mask = mask shr 1
        }
        val mant = (s shr (exp + 3)) and 0x0F
        return (sign or (exp shl 4) or mant).inv().toByte()
    }

    fun decode(b: Byte): Short {
        val u = b.toInt().inv() and 0xFF
        val sign = u and 0x80
        val exp = (u shr 4) and 0x07
        val mant = u and 0x0F
        val s = (((mant shl 3) + BIAS) shl exp) - BIAS
        return (if (sign != 0) -s else s).toShort()
    }

    fun encode(pcm: ShortArray, n: Int = pcm.size): ByteArray = ByteArray(n) { encode(pcm[it]) }
    fun decode(data: ByteArray): ShortArray = ShortArray(data.size) { decode(data[it]) }
}

/** Verbindung über Tor (SOCKS5) zu einer .onion-Adresse. */
object Socks {
    fun connect(socksPort: Int, onion: String, port: Int, replyTimeoutMs: Int): Socket {
        val s = Socket()
        try {
            s.connect(InetSocketAddress("127.0.0.1", socksPort), 10_000)
            // Der Aufbau zu einem Onion-Dienst kann über Tor etwas dauern
            s.soTimeout = replyTimeoutMs
            val inp = DataInputStream(s.getInputStream())
            val out = DataOutputStream(s.getOutputStream())
            out.write(byteArrayOf(5, 1, 0)); out.flush()
            val greet = ByteArray(2); inp.readFully(greet)
            require(greet[0].toInt() == 5 && greet[1].toInt() == 0) { "SOCKS" }
            val host = onion.toByteArray(Charsets.US_ASCII)
            require(host.size in 1..255) { "Adresse" }
            out.write(byteArrayOf(5, 1, 0, 3, host.size.toByte()))
            out.write(host)
            out.write(byteArrayOf((port shr 8).toByte(), port.toByte()))
            out.flush()
            val head = ByteArray(4); inp.readFully(head)
            require(head[1].toInt() == 0) { "Nicht erreichbar" }
            when (head[3].toInt()) {
                1 -> inp.readFully(ByteArray(4 + 2))
                3 -> inp.readFully(ByteArray(inp.readUnsignedByte() + 2))
                4 -> inp.readFully(ByteArray(16 + 2))
                else -> throw IllegalStateException("SOCKS")
            }
            return s
        } catch (e: Exception) {
            try { s.close() } catch (_: Exception) {}
            throw e
        }
    }
}

/**
 * Anruf-Protokoll (über dieselbe Onion-Adresse wie der Chat):
 *
 *  Anrufer → MAGIC, Länge, Angebot   (Ende-zu-Ende verschlüsselt + vom Anrufer signiert, enthält Einmal-Schlüssel)
 *  Angerufener → RINGING             (sofort, nur zur Anzeige "Klingelt")
 *  Angerufener → Länge, Antwort      (verschlüsselt + signiert, eigener Einmal-Schlüssel) oder DECLINE
 *  danach: verschlüsselte Pakete in beide Richtungen (eigener Schlüssel je Richtung, Zähler gegen Wiederholung)
 *
 * Die Gesprächsschlüssel entstehen nur aus den Einmal-Schlüsseln → nach dem Auflegen nicht mehr herstellbar.
 */
object CallProto {
    const val MAGIC = 0x54434C4C // "TCLL"
    const val RINGING = -1
    const val DECLINE = 0
    const val MAX_HANDSHAKE = 64 * 1024
    const val OFFER_MAX_AGE_MS = 120_000L

    const val T_AUDIO = 1
    const val T_HANGUP = 2
    const val T_PING = 3

    class Offer(val callId: ByteArray, val eph: ChatCrypto.KeyPair, val frame: ByteArray)

    fun makeOffer(me: ChatCrypto.KeyPair, peerPub: ByteArray, now: Long = System.currentTimeMillis()): Offer {
        val id = Crypto.random(16)
        val e = ChatCrypto.newKeyPair()
        val plain = JSONObject().put("t", "call").put("id", Crypto.b64(id)).put("e", Crypto.b64(e.pub)).put("ts", now)
        return Offer(id, e, ChatCrypto.seal(me, peerPub, plain.toString().toByteArray()))
    }

    class ParsedOffer(val senderPub: ByteArray, val callId: ByteArray, val ephPub: ByteArray, val ts: Long)

    /** Wirft, wenn das Angebot manipuliert ist oder nicht an uns geht. */
    fun parseOffer(me: ChatCrypto.KeyPair, frame: ByteArray): ParsedOffer {
        val o = ChatCrypto.open(me, frame)
        val j = JSONObject(String(o.plain))
        require(j.getString("t") == "call") { "Typ" }
        val id = Crypto.unb64(j.getString("id"))
        val e = Crypto.unb64(j.getString("e"))
        require(id.size == 16 && e.size == 32) { "Format" }
        return ParsedOffer(o.senderPub, id, e, j.getLong("ts"))
    }

    fun makeAnswer(me: ChatCrypto.KeyPair, callerPub: ByteArray, callId: ByteArray): Pair<ChatCrypto.KeyPair, ByteArray> {
        val e = ChatCrypto.newKeyPair()
        val plain = JSONObject().put("t", "answer").put("id", Crypto.b64(callId)).put("e", Crypto.b64(e.pub))
        return e to ChatCrypto.seal(me, callerPub, plain.toString().toByteArray())
    }

    /** Prüft die Antwort (richtiger Gesprächspartner, richtiger Anruf). Gibt seinen Einmal-Schlüssel zurück. */
    fun parseAnswer(me: ChatCrypto.KeyPair, peerPub: ByteArray, callId: ByteArray, frame: ByteArray): ByteArray {
        val o = ChatCrypto.open(me, frame)
        require(o.senderPub.contentEquals(peerPub)) { "Absender" }
        val j = JSONObject(String(o.plain))
        require(j.getString("t") == "answer") { "Typ" }
        require(Crypto.unb64(j.getString("id")).contentEquals(callId)) { "Anruf" }
        val e = Crypto.unb64(j.getString("e"))
        require(e.size == 32) { "Format" }
        return e
    }

    /** Gesprächsschlüssel: (senden, empfangen). */
    fun sessionKeys(
        myEph: ChatCrypto.KeyPair, peerEph: ByteArray, callId: ByteArray,
        callerPub: ByteArray, calleePub: ByteArray, iAmCaller: Boolean
    ): Pair<ByteArray, ByteArray> {
        val shared = ChatCrypto.agree(myEph.priv, peerEph)
        try {
            val a2b = hkdf(shared, callId, "tresor-call-v1|a2b".toByteArray() + callerPub + calleePub)
            val b2a = hkdf(shared, callId, "tresor-call-v1|b2a".toByteArray() + callerPub + calleePub)
            return if (iAmCaller) a2b to b2a else b2a to a2b
        } finally {
            Crypto.wipe(shared)
        }
    }

    private fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray): ByteArray {
        val g = HKDFBytesGenerator(SHA256Digest())
        g.init(HKDFParameters(ikm, salt, info))
        val out = ByteArray(32)
        g.generateBytes(out, 0, 32)
        return out
    }
}

/** Verschlüsselter Gesprächskanal: AES-256-GCM, Zähler als Nonce, je Richtung ein Schlüssel. */
class SecureChannel(private val socket: Socket, sendKey: ByteArray, recvKey: ByteArray) {
    private val out = DataOutputStream(socket.getOutputStream().buffered())
    private val inp = DataInputStream(socket.getInputStream().buffered())
    private val sk = SecretKeySpec(sendKey, "AES")
    private val rk = SecretKeySpec(recvKey, "AES")
    private val enc = Cipher.getInstance("AES/GCM/NoPadding")
    private val dec = Cipher.getInstance("AES/GCM/NoPadding")
    private var sendCtr = 0L
    private var recvCtr = -1L

    init {
        Crypto.wipe(sendKey); Crypto.wipe(recvKey)
    }

    private fun nonce(ctr: Long) = ByteArray(12).also { n ->
        for (i in 0 until 8) n[4 + i] = (ctr ushr (56 - 8 * i)).toByte()
    }

    @Synchronized
    fun send(type: Int, payload: ByteArray = ByteArray(0)) {
        val ctr = sendCtr++
        enc.init(Cipher.ENCRYPT_MODE, sk, GCMParameterSpec(128, nonce(ctr)))
        val ct = enc.doFinal(byteArrayOf(type.toByte()) + payload)
        out.writeLong(ctr)
        out.writeShort(ct.size)
        out.write(ct)
        out.flush()
    }

    /** Liest das nächste Paket. Wirft bei Manipulation, Wiederholung oder Verbindungsende. */
    fun receive(): Pair<Int, ByteArray> {
        val ctr = inp.readLong()
        val len = inp.readUnsignedShort()
        if (len < 17) throw EOFException("kurz")
        val ct = ByteArray(len)
        inp.readFully(ct)
        if (ctr <= recvCtr) throw SecurityException("Wiederholung")
        dec.init(Cipher.DECRYPT_MODE, rk, GCMParameterSpec(128, nonce(ctr)))
        val p = dec.doFinal(ct)
        recvCtr = ctr
        return (p[0].toInt() and 0xFF) to p.copyOfRange(1, p.size)
    }

    fun setReadTimeout(ms: Int) {
        socket.soTimeout = ms
    }

    fun close() {
        try { socket.close() } catch (_: Exception) {}
    }
}
