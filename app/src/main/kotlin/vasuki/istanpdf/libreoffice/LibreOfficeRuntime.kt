package vasuki.istanpdf.libreoffice

import android.content.Context
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files

internal object LibreOfficeRuntime {
    private const val TAG = "LibreOfficeRuntime"
    private const val MARKER_PREFIX = "lo-runtime-"
    private const val BUFFER_SIZE = 64 * 1024

    @Synchronized
    @Throws(Exception::class)
    fun prepare(context: Context) {
        val appContext = context.applicationContext
        val dataDir = appContext.filesDir
        val markerName = "${MARKER_PREFIX}${versionCode(appContext)}.ready"
        val marker = File(dataDir, markerName)

        if (!marker.exists()) {
            clearOldMarkers(dataDir)
            marker.createNewFile()
        }

        prepareNativeLibraryDirectory(appContext, dataDir)
    }

    private fun versionCode(context: Context): Long {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(packageInfo)
    }

    private fun clearOldMarkers(dataDir: File) {
        val markers = dataDir.listFiles { _, name -> name.startsWith(MARKER_PREFIX) } ?: return
        for (marker in markers) {
            if (!marker.delete()) {
                Log.w(TAG, "Could not remove old marker $marker")
            }
        }
    }

    private fun prepareNativeLibraryDirectory(context: Context, dataDir: File) {
        val sourceDir = LibreOfficeManager.libDir(context)
        val targetDir = File(dataDir, "lib")
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw IllegalStateException("Could not create $targetDir")
        }

        val libraries = sourceDir.listFiles { _, name -> name.endsWith(".so") }
        if (libraries == null || libraries.isEmpty()) {
            throw IllegalStateException("No LibreOffice native libraries found in $sourceDir")
        }

        for (library in libraries) {
            val target = File(targetDir, library.name)
            if (target.exists() && target.canonicalPath == library.canonicalPath) continue
            if (target.exists() && target.lastModified() >= library.lastModified() && target.length() == library.length()) continue
            replaceLibraryBridge(library, target)
        }
    }

    private fun replaceLibraryBridge(library: File, target: File) {
        Files.deleteIfExists(target.toPath())
        try {
            Files.createSymbolicLink(target.toPath(), library.toPath())
        } catch (_: Throwable) {
            Files.deleteIfExists(target.toPath())
            copyFile(library, target)
        }
    }

    private fun copyFile(source: File, target: File) {
        Files.newInputStream(source.toPath()).use { input ->
            FileOutputStream(target, false).use { output ->
                input.copyTo(output, BUFFER_SIZE)
            }
        }
    }
}
