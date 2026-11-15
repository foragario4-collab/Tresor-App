package app.glass.tresor.vault

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import app.glass.tresor.crypto.EncryptedReader

/** Spielt Videos direkt aus der verschlüsselten Datei ab – Block für Block im Speicher. */
@OptIn(UnstableApi::class)
class EncryptedDataSource(private val openReader: () -> EncryptedReader) : BaseDataSource(false) {
    private var reader: EncryptedReader? = null
    private var uri: Uri? = null
    private var pos = 0L
    private var remaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val r = openReader()
        reader = r
        pos = dataSpec.position
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else r.length - pos
        if (remaining < 0) remaining = 0
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val r = reader ?: return C.RESULT_END_OF_INPUT
        val toRead = minOf(length.toLong(), remaining).toInt()
        val n = r.read(pos, buffer, offset, toRead)
        if (n <= 0) return C.RESULT_END_OF_INPUT
        pos += n
        remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        reader?.close()
        reader = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    class Factory(private val openReader: () -> EncryptedReader) : DataSource.Factory {
        override fun createDataSource(): DataSource = EncryptedDataSource(openReader)
    }
}
