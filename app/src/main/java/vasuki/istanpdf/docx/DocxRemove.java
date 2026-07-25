package vasuki.istanpdf.docx;

import android.content.Context;
import android.net.Uri;

import org.libreoffice.kit.Document;
import org.libreoffice.kit.Office;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import vasuki.istanpdf.libreoffice.LibreOfficeDocumentEngine;
import vasuki.istanpdf.libreoffice.UnoCommandHelper;
import vasuki.istanpdf.util.ContentFiles;

public final class DocxRemove {
    private DocxRemove() {
    }

    public static final class PageSelection {
        public final int originalIndex;
        public final boolean keep;

        public PageSelection(int originalIndex, boolean keep) {
            this.originalIndex = originalIndex;
            this.keep = keep;
        }
    }

    public static void run(Context a, Uri src, List<PageSelection> pages, Uri dst) throws Exception {
        boolean anyKept = false;
        for (PageSelection p : pages) {
            if (p.keep) {
                anyKept = true;
                break;
            }
        }
        if (!anyKept) {
            throw new IllegalStateException("Keep at least one page");
        }

        Office office = LibreOfficeDocumentEngine.getOffice(a);
        File in = null;
        File out = null;
        Document doc = null;
        
        try {
            in = ContentFiles.copyUriToCache(a, src, ".docx");
            out = File.createTempFile("removed_uno_", ".docx", a.getCacheDir());
            
            doc = office.documentLoad(Uri.fromFile(in).toString());
            if (doc == null) {
                throw new IllegalStateException("LO couldn't load DOCX");
            }

            doc.initializeForRendering();

            int total = doc.getParts();
            if (total <= 0) {
                total = pages.size();
            }

            remove(doc, pages, total);

            UnoCommandHelper.postAndWait(doc, ".uno:Repaginate");

            doc.saveAs(Uri.fromFile(out).toString(), "docx", null);
            
            if (!out.exists() || out.length() == 0) {
                throw new IllegalStateException("LibreOffice failed to generate an output file");
            }
            
            ContentFiles.copyFileToUri(a, out, dst);
        } finally {
            if (doc != null) {
                doc.destroy();
            }
            if (in != null && in.exists()) in.delete();
            if (out != null && out.exists()) out.delete();
        }
    }

    private static void remove(Document doc, List<PageSelection> pages, int totalPages) throws Exception {
        doc.setPart(0);
        UnoCommandHelper.postAndWait(doc, ".uno:GoToStartOfDoc");

        List<PageSelection> order = new ArrayList<>(pages);
        Collections.sort(order, (a, b) -> Integer.compare(a.originalIndex, b.originalIndex));

        int last = totalPages - 1;
        for (int i = order.size() - 1; i >= 0; i--) {
            PageSelection page = order.get(i);
            if (page.keep) {
                continue;
            }
            if (Thread.interrupted()) {
                throw new InterruptedException("Operation cancelled");
            }
            doc.setPart(page.originalIndex);
            if (page.originalIndex == last) {
                UnoCommandHelper.postAndWait(doc, ".uno:GoToEndOfPageSel");
                UnoCommandHelper.postAndWait(doc, ".uno:Delete");
                if (page.originalIndex > 0) {
                    UnoCommandHelper.postAndWait(doc, ".uno:SwBackspace");
                }
                last--;
            } else {
                UnoCommandHelper.postAndWait(doc, ".uno:GoToStartOfNextPageSel");
                UnoCommandHelper.postAndWait(doc, ".uno:Delete");
            }
        }
    }
}
