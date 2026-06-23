package vasuki.istanpdf.model;

import android.graphics.Bitmap;

public class PageItem {
    public final int originalIndex;
    public Bitmap thumbnail;
    public String displayName = "";
    public boolean keep = true;
    public int rotation = 0;

    public PageItem(int originalIndex, Bitmap thumbnail) {
        this.originalIndex = originalIndex;
        this.thumbnail = thumbnail;
    }

    public PageItem(int originalIndex, Bitmap thumbnail, String displayName) {
        this.originalIndex = originalIndex;
        this.thumbnail = thumbnail;
        this.displayName = displayName;
    }
}
