package vasuki.istanpdf.libreoffice;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.res.AssetManager;
import android.util.Log;

import androidx.core.content.pm.PackageInfoCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

final class LibreOfficeRuntime {
    private static final String TAG = "LibreOfficeRuntime";
    private static final String MARKER_PREFIX = "lo-runtime-";
    private static final int BUFFER_SIZE = 64 * 1024;

    private LibreOfficeRuntime() {
    }

    static synchronized void prepare(Context context) throws Exception {
        Context appContext = context.getApplicationContext();
        File dataDir = new File(appContext.getApplicationInfo().dataDir);
        String markerName = MARKER_PREFIX + versionCode(appContext) + ".ready";
        File marker = new File(dataDir, markerName);

        if (!marker.exists()) {
            clearOldMarkers(dataDir);
            copyAssetTree(appContext.getAssets(), "unpack", dataDir);
            marker.createNewFile();
        }

        prepareNativeLibraryDirectory(appContext, dataDir);
    }

    private static long versionCode(Context context) throws Exception {
        PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        return PackageInfoCompat.getLongVersionCode(packageInfo);
    }

    private static void clearOldMarkers(File dataDir) {
        File[] markers = dataDir.listFiles((dir, name) -> name.startsWith(MARKER_PREFIX));
        if (markers == null) {
            return;
        }
        for (File marker : markers) {
            if (!marker.delete()) {
                Log.w(TAG, "Could not remove old marker " + marker);
            }
        }
    }

    private static void copyAssetTree(AssetManager assetManager, String fromAssetPath, File targetDir)
            throws Exception {
        String[] children = assetManager.list(fromAssetPath);
        if (children == null || children.length == 0) {
            copyAsset(assetManager, fromAssetPath, targetDir);
            return;
        }

        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IllegalStateException("Could not create " + targetDir);
        }

        for (String child : children) {
            copyAssetTree(assetManager, fromAssetPath + "/" + child, new File(targetDir, child));
        }
    }

    private static void copyAsset(AssetManager assetManager, String fromAssetPath, File targetFile)
            throws Exception {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create " + parent);
        }

        try (InputStream input = assetManager.open(fromAssetPath);
             OutputStream output = new FileOutputStream(targetFile, false)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    private static void prepareNativeLibraryDirectory(Context context, File dataDir) throws Exception {
        File sourceDir = new File(context.getApplicationInfo().nativeLibraryDir);
        File targetDir = new File(dataDir, "lib");
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IllegalStateException("Could not create " + targetDir);
        }

        File[] libraries = sourceDir.listFiles((dir, name) -> name.endsWith(".so"));
        if (libraries == null || libraries.length == 0) {
            throw new IllegalStateException("No LibreOffice native libraries found in " + sourceDir);
        }

        for (File library : libraries) {
            File target = new File(targetDir, library.getName());
            if (target.exists() && Objects.equals(target.getCanonicalPath(), library.getCanonicalPath())) {
                continue;
            }
            if (target.exists() && target.lastModified() >= library.lastModified() && target.length() == library.length()) {
                continue;
            }
            replaceLibraryBridge(library, target);
        }
    }

    private static void replaceLibraryBridge(File library, File target) throws Exception {
        java.nio.file.Files.deleteIfExists(target.toPath());
        try {
            Path link = target.toPath();
            Path existing = library.toPath();
            Files.createSymbolicLink(link, existing);
        } catch (Throwable throwable) {
            java.nio.file.Files.deleteIfExists(target.toPath());
            copyFile(library, target);
        }
    }

    private static void copyFile(File source, File target) throws Exception {
        try (InputStream input = Files.newInputStream(source.toPath());
             OutputStream output = new FileOutputStream(target, false)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }
}
