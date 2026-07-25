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

public final class DocxReorder {
    private DocxReorder() {
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
            out = File.createTempFile("reordered_uno_", ".docx", a.getCacheDir());
            
            doc = office.documentLoad(Uri.fromFile(in).toString());
            if (doc == null) {
                throw new IllegalStateException("LO couldn't load DOCX");
            }

            doc.initializeForRendering();

            int total = doc.getParts();
            if (total <= 0) {
                total = pages.size();
            }

            List<Integer> toDelete = new ArrayList<>();
            for (PageSelection p : pages) {
                if (!p.keep) toDelete.add(p.originalIndex);
            }
            Collections.sort(toDelete);

            deletePages(doc, toDelete, total);

            int keptTotal = total - toDelete.size();
            copyKeptToEnd(doc, pages, toDelete, keptTotal);


            deleteOriginals(doc, keptTotal);

            UnoCommandHelper.postFireAndForget(doc, ".uno:GoToStartOfDoc");
            UnoCommandHelper.postAndWait(doc, ".uno:Delete");

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


    private static void deletePages(Document doc, List<Integer> indices, int currentTotal)
            throws Exception {
        for (int i = indices.size() - 1; i >= 0; i--) {
            if (Thread.interrupted()) throw new InterruptedException("Operation cancelled");
            int pageIdx = indices.get(i);
            doc.setPart(pageIdx);
            if (pageIdx == currentTotal - 1) {
                UnoCommandHelper.postAndWait(doc, ".uno:GoToEndOfPageSel");
                UnoCommandHelper.postAndWait(doc, ".uno:Delete");
                if (pageIdx > 0) {
                    UnoCommandHelper.postAndWait(doc, ".uno:SwBackspace");
                }
            } else {
                UnoCommandHelper.postAndWait(doc, ".uno:GoToStartOfNextPageSel");
                UnoCommandHelper.postAndWait(doc, ".uno:Delete");
            }
            currentTotal--;
        }
    }

    private static void copyKeptToEnd(Document doc, List<PageSelection> pages,
                                       List<Integer> deletedIndices, int keptTotal)
            throws Exception {
        for (PageSelection p : pages) {
            if (!p.keep) continue;
            if (Thread.interrupted()) throw new InterruptedException("Operation cancelled");
            int currentPos = p.originalIndex;
            for (int deleted : deletedIndices) {
                if (deleted < p.originalIndex) currentPos--;
            }

            doc.setPart(currentPos);
            if (currentPos == keptTotal - 1) {
                UnoCommandHelper.postAndWait(doc, ".uno:GoToEndOfPageSel");
            } else {
                UnoCommandHelper.postAndWait(doc, ".uno:GoToStartOfNextPageSel");
            }
            UnoCommandHelper.postAndWait(doc, ".uno:Copy");
            UnoCommandHelper.postFireAndForget(doc, ".uno:GoToEndOfDoc");
            UnoCommandHelper.postAndWait(doc, ".uno:InsertPagebreak");
            UnoCommandHelper.postAndWait(doc, ".uno:Paste");
        }
    }

    private static void deleteOriginals(Document doc, int count) throws Exception {
        for (int k = count - 1; k >= 0; k--) {
            if (Thread.interrupted()) throw new InterruptedException("Operation cancelled");
            doc.setPart(k);
            if (k == count - 1) {
                UnoCommandHelper.postAndWait(doc, ".uno:GoToEndOfPageSel");
                UnoCommandHelper.postAndWait(doc, ".uno:Delete");
                if (k > 0) {
                    UnoCommandHelper.postAndWait(doc, ".uno:SwBackspace");
                }
            } else {
                UnoCommandHelper.postAndWait(doc, ".uno:GoToStartOfNextPageSel");
                UnoCommandHelper.postAndWait(doc, ".uno:Delete");
            }
        }
    }
}
