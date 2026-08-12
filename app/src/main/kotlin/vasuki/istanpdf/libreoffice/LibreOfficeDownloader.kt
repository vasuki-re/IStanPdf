package vasuki.istanpdf.libreoffice

import android.content.Context
import android.util.Log
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object LibreOfficeDownloader {

    private const val TAG = "LibreOfficeDownloader"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS    = 60_000
    private const val BUFFER_SIZE        = 128 * 1024
    private const val XZ_MEMORY_LIMIT_KB = 300 * 1024

    fun interface ProgressCallback {
        fun onProgress(phase: String, downloaded: Long, total: Long)
    }

    @Throws(Exception::class)
    fun downloadAndInstall(context: Context, progress: ProgressCallback) {
        val appContext = context.applicationContext
        val engineRoot = LibreOfficeManager.engineRoot(appContext)
        val url        = LibreOfficeManager.downloadUrl()

        Log.i(TAG, "Starting engine download: $url")

        LibreOfficeManager.deleteEngine(appContext)
        engineRoot.mkdirs()

        val tempFile = File(appContext.cacheDir, "libreoffice_engine_download.tar.xz")
        try {
            download(url, tempFile, progress)

            progress.onProgress("Extracting", 0L, -1L)
            extract(tempFile, engineRoot, appContext, progress)


            LibreOfficeManager.markEngineReady(appContext)
            Log.i(TAG, "Engine installed successfully at $engineRoot")

        } catch (e: Exception) {
            Log.e(TAG, "Engine installation failed — cleaning up", e)
            LibreOfficeManager.deleteEngine(appContext)
            throw e
        } finally {
            tempFile.delete()
        }
    }

    private fun download(urlString: String, dest: File, progress: ProgressCallback) {
        val url  = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout    = READ_TIMEOUT_MS
        conn.instanceFollowRedirects = true
        conn.connect()

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            throw IllegalStateException("HTTP $responseCode downloading engine from $urlString")
        }

        val totalBytes = conn.contentLengthLong
        var downloaded = 0L

        BufferedInputStream(conn.inputStream, BUFFER_SIZE).use { input ->
            FileOutputStream(dest, false).use { output ->
                val buf = ByteArray(BUFFER_SIZE)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    output.write(buf, 0, read)
                    downloaded += read
                    progress.onProgress("Downloading", downloaded, totalBytes)
                }
            }
        }

        if (dest.length() == 0L) {
            throw IllegalStateException("Downloaded file is empty")
        }
        Log.i(TAG, "Download complete: ${dest.length()} bytes")
    }

    private fun extract(
        archive: File,
        engineRoot: File,
        context: Context,
        progress: ProgressCallback
    ) {
        var extractedEntries = 0L

        archive.inputStream().buffered(BUFFER_SIZE).use { rawIn ->
            XZInputStream(rawIn, XZ_MEMORY_LIMIT_KB).use { xzIn ->
                TarArchiveInputStream(xzIn).use { tar ->
                    var entry = tar.nextEntry
                    while (entry != null) {
                        val rawName = entry.name.trimStart('/', '.')
                        val dest = when {
                            rawName.startsWith("arm64-v8a/") ->
                                File(engineRoot, "lib/arm64-v8a/${rawName.removePrefix("arm64-v8a/")}")
                            rawName.startsWith("armeabi-v7a/") ->
                                File(engineRoot, "lib/armeabi-v7a/${rawName.removePrefix("armeabi-v7a/")}")
                            rawName == "arm64-v8a" || rawName == "armeabi-v7a" ->
                                File(engineRoot, "lib/$rawName")
                            else -> null
                        }

                        if (dest != null) {
                            if (entry.isDirectory) {
                                dest.mkdirs()
                            } else {
                                dest.parentFile?.mkdirs()
                                FileOutputStream(dest, false).use { tar.copyTo(it, BUFFER_SIZE) }
                                extractedEntries++
                            }
                        }
                        entry = tar.nextEntry
                    }
                }
            }
        }

        if (extractedEntries == 0L) {
            throw IllegalStateException("No libs extracted — archive may be corrupt")
        }
        Log.i(TAG, "Extraction complete: $extractedEntries entries")
        progress.onProgress("Extracting", 100L, 100L)
    }
}
