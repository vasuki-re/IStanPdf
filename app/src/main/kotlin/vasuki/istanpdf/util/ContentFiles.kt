package vasuki.istanpdf.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

object ContentFiles {
    private const val BUFFER_SIZE = 64 * 1024

    @Throws(IOException::class)
    fun copyUriToCache(context: Context, uri: Uri, suffix: String): File {
        val file = File.createTempFile("istanpdf_", suffix, context.cacheDir)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open selected file")
        input.use { src ->
            FileOutputStream(file).use { dst ->
                src.copyTo(dst, BUFFER_SIZE)
            }
        }
        return file
    }

    @Throws(IOException::class)
    fun copyFileToUri(context: Context, file: File, uri: Uri) {
        FileInputStream(file).use { input ->
            val output = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("Cannot open destination file")
            output.use { dst ->
                input.copyTo(dst, BUFFER_SIZE)
            }
        }
    }
}
