package vasuki.istanpdf.pdf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal object PdfStore {
    @Throws(IOException::class)
    fun openDst(ctx: Context, dst: Uri): OutputStream {
        val raw = ctx.contentResolver.openOutputStream(dst, "wt")
            ?: throw IOException("Cannot open destination file")
        return BufferedOutputStream(raw, 64 * 1024)
    }

    fun base(ctx: Context, uri: Uri?): String {
        if (uri == null) return "Document"
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1) {
                    val name = c.getString(idx)
                    val dot = name.lastIndexOf('.')
                    return if (dot > 0) name.substring(0, dot) else name
                }
            }
        }
        return "Document"
    }

    @Throws(Exception::class)
    fun bytes(input: InputStream): ByteArray = input.readBytes()
}
