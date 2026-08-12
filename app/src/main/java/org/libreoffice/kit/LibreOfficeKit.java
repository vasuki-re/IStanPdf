package org.libreoffice.kit;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.util.Log;

import java.nio.ByteBuffer;

public final class LibreOfficeKit
{
    private static String LOGTAG = LibreOfficeKit.class.getSimpleName();
    private static AssetManager mgr;

    private LibreOfficeKit() {
    }

    public static void initializeLibrary() {
    }

    private static native boolean initializeNative(String dataDir, String cacheDir, String apkFile, AssetManager mgr);

    public static native ByteBuffer getLibreOfficeKitHandle();

    public static native void putenv(String string);

    public static native void redirectStdio(boolean state);

    static boolean initializeDone = false;

    public static synchronized void init(Context context)
    {
        if (initializeDone) {
            return;
        }

        mgr = context.getResources().getAssets();

        ApplicationInfo applicationInfo = context.getApplicationInfo();
        String dataDir = applicationInfo.dataDir;
        Log.i(LOGTAG, String.format("Initializing LibreOfficeKit, dataDir=%s\n", dataDir));

        redirectStdio(true);

        String cacheDir = context.getCacheDir().getAbsolutePath();
        String apkFile = context.getPackageResourcePath();

        if (!initializeNative(dataDir, cacheDir, apkFile, mgr)) {
            Log.e(LOGTAG, "Initialize native failed!");
            return;
        }
        initializeDone = true;
    }

    public static void loadNativeLibraries(String libraryDir) {
        NativeLibLoader.setLibraryDir(libraryDir);
        NativeLibLoader.load();
    }

    static {
    }
}

class NativeLibLoader {
    private static boolean done = false;
    private static String libraryDir = null;

    public static void setLibraryDir(String dir) {
        libraryDir = dir;
    }

    protected static synchronized void load() {
        if (done)
            return;
        
        if (libraryDir != null) {
            System.load(libraryDir + "/libc++_shared.so");
            System.load(libraryDir + "/liblo-native-code.so");
        } else {
            System.loadLibrary("c++_shared");
            System.loadLibrary("lo-native-code");
        }
        done = true;
    }
}
