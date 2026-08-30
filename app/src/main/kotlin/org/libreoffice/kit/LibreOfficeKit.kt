package org.libreoffice.kit

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import java.nio.ByteBuffer

class LibreOfficeKit private constructor() {

    companion object {
        private val LOGTAG: String = LibreOfficeKit::class.java.simpleName
        private var mgr: AssetManager? = null

        @JvmStatic
        fun initializeLibrary() {
        }

        @JvmStatic
        private external fun initializeNative(dataDir: String, cacheDir: String, apkFile: String, mgr: AssetManager): Boolean

        @JvmStatic
        external fun getLibreOfficeKitHandle(): ByteBuffer

        @JvmStatic
        external fun putenv(string: String)

        @JvmStatic
        external fun redirectStdio(state: Boolean)

        @JvmField
        internal var initializeDone: Boolean = false

        @JvmStatic
        @Synchronized
        fun init(context: Context) {
            if (initializeDone) {
                return
            }

            mgr = context.resources.assets

            val dataDir = context.filesDir.absolutePath
            Log.i(LOGTAG, String.format("Initializing LibreOfficeKit, dataDir=%s\n", dataDir))

            redirectStdio(true)

            val cacheDir = context.cacheDir.absolutePath
            val apkFile = context.packageResourcePath

            if (!initializeNative(dataDir, cacheDir, apkFile, mgr!!)) {
                Log.e(LOGTAG, "Initialize native failed!")
                return
            }
            initializeDone = true
        }

        @JvmStatic
        fun loadNativeLibraries(libraryDir: String) {
            NativeLibLoader.setLibraryDir(libraryDir)
            NativeLibLoader.load()
        }
    }
}

internal object NativeLibLoader {
    private var done = false
    private var libraryDir: String? = null

    fun setLibraryDir(dir: String) {
        libraryDir = dir
    }

    @Synchronized
    fun load() {
        if (done) return

        val dir = libraryDir
        if (dir != null) {
            System.load("$dir/libc++_shared.so")
            System.load("$dir/liblo-native-code.so")
        } else {
            System.loadLibrary("c++_shared")
            System.loadLibrary("lo-native-code")
        }
        done = true
    }
}
