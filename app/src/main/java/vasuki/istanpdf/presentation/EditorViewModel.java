package vasuki.istanpdf.presentation;

import android.net.Uri;

import androidx.lifecycle.ViewModel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import vasuki.istanpdf.model.PageItem;

/**
 * ViewModel for the page editor screen. Holds operational state:
 * page list, pending URIs, reorder source, and flags for
 * tracking modifications (added, reordered, rotated, removed).
 */
public class EditorViewModel extends ViewModel {

    private final List<PageItem> pages = new ArrayList<>();
    private final List<Uri> pendingUris = new ArrayList<>();
    private final List<Uri> pendingImageUris = new ArrayList<>();
    private final List<File> tempImageFiles = new ArrayList<>();

    private Uri reorderSource;
    private String originalFileName;
    private boolean pagesAdded = false;

    

    public List<PageItem> getPages() { return pages; }

    public void clearPages() {
        for (PageItem p : pages) {
            if (p != null && p.thumbnail != null && !p.thumbnail.isRecycled()) {
                p.thumbnail.recycle();
            }
        }
        pages.clear();
    }

    

    public List<Uri> getPendingUris() { return pendingUris; }

    public void clearPendingUris() { pendingUris.clear(); }

    

    public List<Uri> getPendingImageUris() { return pendingImageUris; }
    public void clearPendingImageUris() { pendingImageUris.clear(); }

    

    public List<File> getTempImageFiles() { return tempImageFiles; }

    public void cleanupTempFiles() {
        for (File f : tempImageFiles) {
            if (f.exists()) f.delete();
        }
        tempImageFiles.clear();
    }

    

    public Uri getReorderSource() { return reorderSource; }
    public void setReorderSource(Uri source) { this.reorderSource = source; }

    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String name) { this.originalFileName = name; }

    

    public boolean isPagesAdded() { return pagesAdded; }
    public void setPagesAdded(boolean added) { this.pagesAdded = added; }

    

    public void resetForHome() {
        clearPages();
        clearPendingUris();
        pendingImageUris.clear();
        reorderSource = null;
        originalFileName = null;
        pagesAdded = false;
        cleanupTempFiles();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        clearPages();
        cleanupTempFiles();
    }
}
