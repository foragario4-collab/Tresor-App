package app.glass.tresor.crypto

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Dateiformat für große Dateien (Fotos, Videos, Dokumente):
 * Die Datei wird in 64-KB-Blöcke geteilt, jeder Block einzeln mit AES-256-GCM.
 * Dadurch können Videos direkt verschlüsselt abgespielt und gespult werden,
 * ohne dass je eine entschlüsselte Kopie auf dem Speicher liegt.
 * Blocknummer und "letzter Block" sind authentifiziert – Vertauschen oder Abschneiden fällt auf.
 */
object Chunked {
    const val MAGIC = 0x54525632 // "TRV2"
    const val CHUNK = 64 * 1024
    const val HEADER = 4 + 8 + 4
    private const val TAG = 16

    fun encrypt(input: InputStream, out: File, key: ByteArray, ctx: ByteArray): Long {
        val prefix = Crypto.random(8)
        val header = ByteBuffer.allocate(HEADER).putInt(MAGIC).put(prefix).putInt(CHUNK).array()
        val ks = SecretKeySpec(key, "AES")
        var total = 0L
        out.parentFile?.mkdirs()
        FileOutputStream(out).use { fos ->
            val os = fos.buffered(256 * 1024)
            os.write(header)
            var buf = ByteArray(CHUNK)
            var next = ByteArray(CHUNK)
            var n = readFully(input, buf)
            var idx = 0L
            while (true) {
                val m = if (n == CHUNK) readFully(input, next) else 0
                val isFinal = n < CHUNK || m == 0
                os.write(sealChunk(ks, prefix, header, idx, isFinal, buf, n, ctx))
                total += n
                if (isFinal) break
                val t = buf; buf = next; next = t
                n = m
                idx++
            }
            os.flush()
            fos.fd.sync()
            buf.fill(0); next.fill(0)
        }
        return total
    }

    private fun readFully(input: InputStream, buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val r = input.read(buf, off, buf.size - off)
            if (r < 0) break
            off += r
        }
        return off
    }

    internal fun nonce(prefix: ByteArray, idx: Long): ByteArray =
        ByteBuffer.allocate(12).put(prefix).putInt(idx.toInt()).array()

    /** Authentifiziert: Header, Blocknummer, "letzter Block" und die Element-ID (ctx). */
    internal fun aad(header: ByteArray, idx: Long, final: Boolean, ctx: ByteArray): ByteArray =
        ByteBuffer.allocate(header.size + 9 + ctx.size).put(header).putLong(idx).put(if (final) 1 else 0).put(ctx).array()

    private fun sealChunk(
        ks: SecretKeySpec, prefix: ByteArray, header: ByteArray,
        idx: Long, final: Boolean, buf: ByteArray, n: Int, ctx: ByteArray
    ): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, ks, GCMParameterSpec(TAG * 8, nonce(prefix, idx)))
        c.updateAAD(aad(header, idx, final, ctx))
        return c.doFinal(buf, 0, n)
    }

    fun encryptBytes(bytes: ByteArray, out: File, key: ByteArray, ctx: ByteArray): Long =
        encrypt(bytes.inputStream(), out, key, ctx)
}

class EncryptedReader(file: File, key: ByteArray, private val ctx: ByteArray) : Closeable {
    private val raf = RandomAccessFile(file, "r")
    private val ks = SecretKeySpec(key.copyOf(), "AES")
    private val header = ByteArray(Chunked.HEADER)
    private val prefix: ByteArray
    private val chunk: Int
    private val numChunks: Long
    private val fileLen: Long
    val length: Long

    private var cacheIdx = -1L
    private var cache = ByteArray(0)

    init {
        raf.readFully(header)
        val bb = ByteBuffer.wrap(header)
        require(bb.int == Chunked.MAGIC) { "Unbekanntes Format" }
        prefix = ByteArray(8).also { bb.get(it) }
        chunk = bb.int
        fileLen = raf.length()
        val body = fileLen - Chunked.HEADER
        val enc = chunk + 16L
        numChunks = maxOf(1L, (body + enc - 1) / enc)
        val lastEnc = body - (numChunks - 1) * enc
        length = (numChunks - 1) * chunk + (lastEnc - 16)
    }

    private fun load(idx: Long) {
        if (idx == cacheIdx) return
        val enc = chunk + 16L
        val off = Chunked.HEADER + idx * enc
        val size = minOf(enc, fileLen - off).toInt()
        val data = ByteArray(size)
        raf.seek(off)
        raf.readFully(data)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, ks, GCMParameterSpec(128, Chunked.nonce(prefix, idx)))
        c.updateAAD(Chunked.aad(header, idx, idx == numChunks - 1, ctx))
        cache.fill(0)
        cache = c.doFinal(data)
        cacheIdx = idx
    }

    @Synchronized
    fun read(pos: Long, dst: ByteArray, off: Int, len: Int): Int {
        if (pos >= length) return -1
        if (len == 0) return 0
        val idx = pos / chunk
        load(idx)
        val inChunk = (pos - idx * chunk).toInt()
        val n = minOf(len, cache.size - inChunk)
        if (n <= 0) return -1
        System.arraycopy(cache, inChunk, dst, off, n)
        return n
    }

    fun readAll(): ByteArray {
        require(length < Int.MAX_VALUE - 16) { "Datei zu groß" }
        val out = ByteArray(length.toInt())
        var p = 0L
        while (p < length) {
            val n = read(p, out, p.toInt(), (length - p).toInt())
            if (n <= 0) break
            p += n
        }
        return out
    }

    fun inputStream(): InputStream = object : InputStream() {
        private var p = 0L
        override fun read(): Int {
            val b = ByteArray(1)
            val n = read(b, 0, 1)
            return if (n <= 0) -1 else b[0].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            val n = this@EncryptedReader.read(p, b, off, len)
            if (n > 0) p += n
            return n
        }

        override fun available(): Int = (length - p).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    override fun close() {
        try {
            raf.close()
        } catch (_: Exception) {
        }
        cache.fill(0)
    }
}
