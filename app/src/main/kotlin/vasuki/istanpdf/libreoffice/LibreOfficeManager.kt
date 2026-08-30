package vasuki.istanpdf.libreoffice

import android.content.Context
import android.util.Log
import java.io.File

object LibreOfficeManager {

    private const val TAG = "LibreOfficeManager"
    const val ENGINE_VERSION = "1.0"
    private const val MARKER_NAME = "engine.ready"

    private val DOWNLOAD_URLS = mapOf(
        "arm64-v8a"   to "https://github.com/vasuki-re/LibreOffice-Lite/releases/download/v2.0/LibreOffice-arm64.tar.xz",
        "armeabi-v7a" to "https://github.com/vasuki-re/LibreOffice-Lite/releases/download/v2.0/LibreOffice-arm.tar.xz"
    )

    fun engineRoot(context: Context): File =
        File(context.applicationContext.filesDir, "libreoffice")

    fun libDir(context: Context): File =
        File(engineRoot(context), "lib/${deviceAbi()}")

    private fun marker(context: Context): File = File(engineRoot(context), MARKER_NAME)

    fun isEngineInstalled(context: Context): Boolean {
        val m = marker(context)
        if (!m.exists() || m.length() == 0L) return false
        val lib = libDir(context)
        return File(lib, "liblo-native-code.so").exists() && File(lib, "libc++_shared.so").exists()
    }

    fun markEngineReady(context: Context) {
        val m = marker(context)
        m.parentFile?.mkdirs()
        m.writeText(ENGINE_VERSION)
    }

    fun deleteEngine(context: Context) {
        val root = engineRoot(context)
        if (root.exists()) {
            root.deleteRecursively()
            Log.i(TAG, "Engine directory deleted: $root")
        }
        val filesDir = context.applicationContext.filesDir
        File(filesDir, "program").deleteRecursively()
        File(filesDir, "share").deleteRecursively()
        File(filesDir, "etc").deleteRecursively()
        File(filesDir, "user").deleteRecursively()
    }

    fun deviceAbi(): String {
        for (abi in android.os.Build.SUPPORTED_ABIS) {
            if (abi in DOWNLOAD_URLS) return abi
        }
        return "arm64-v8a"
    }

    fun downloadUrl(): String =
        DOWNLOAD_URLS[deviceAbi()] ?: DOWNLOAD_URLS["arm64-v8a"]!!
}
