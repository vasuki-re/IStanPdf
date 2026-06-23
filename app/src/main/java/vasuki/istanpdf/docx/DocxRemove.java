package vasuki.istanpdf.docx;

import android.content.Context;
import android.net.Uri;

import org.libreoffice.kit.Document;
import org.libreoffice.kit.LibreOfficeKit;
import org.libreoffice.kit.Office;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import vasuki.istanpdf.libreoffice.LibreOfficeDocumentEngine;
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

        LibreOfficeDocumentEngine.initialize(a);
        File in = null;
        File out = null;
        File odt = null;
        Office office = null;
        Document doc = null;
        
        try {
            in = ContentFiles.copyUriToCache(a, src, ".docx");
            office = new Office(LibreOfficeKit.getLibreOfficeKitHandle());
            out = File.createTempFile("reordered_uno_", ".docx", a.getCacheDir());
            
            doc = office.documentLoad(Uri.fromFile(in).toString());
            if (doc == null) {
                throw new IllegalStateException("LO couldn't load DOCX");
            }

            int total = doc.getParts();
            if (total <= 0) {
                total = pages.size();
            }

            remove(doc, pages);

            doc.postUnoCommand(".uno:Repaginate", "", false);
            Thread.sleep(1500);

            if (directDocx(in)) {
                doc.saveAs(Uri.fromFile(out).toString(), "docx", null);
            } else {
                odt = File.createTempFile("cleaned_uno_", ".odt", a.getCacheDir());
                doc.saveAs(Uri.fromFile(odt).toString(), "odt", null);
                
                if (!odt.exists() || odt.length() == 0) {
                    throw new IllegalStateException("LO failed to generate ODT file");
                }

                doc.destroy();
                doc = null;

                doc = office.documentLoad(Uri.fromFile(odt).toString());
                if (doc == null) {
                    throw new IllegalStateException("LO couldn't reload ODT");
                }
                doc.saveAs(Uri.fromFile(out).toString(), "docx", null);
            }
            
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
            if (odt != null && odt.exists()) odt.delete();
        }
    }

    private static boolean directDocx(File in) {
        try (ZipFile zip = new ZipFile(in)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith("word/charts/")) {
                    return true;
                }
            }

            ZipEntry doc = zip.getEntry("word/document.xml");
            if (doc == null) {
                return false;
            }
            String xml = text(zip, doc);
            return xml.contains("<wp:anchor")
                    || xml.contains("drawingml/2006/chart")
                    || xml.contains("<c:chart");
        } catch (Exception e) {
            return false;
        }
    }

    private static String text(ZipFile zip, ZipEntry entry) throws Exception {
        try (InputStream in = zip.getInputStream(entry);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void remove(Document doc, List<PageSelection> pages) throws Exception {
        doc.postUnoCommand(".uno:GoToEndOfDoc", "", false);
        doc.postUnoCommand(".uno:GoToStartOfDoc", "", false);
        Thread.sleep(200);

        List<PageSelection> order = new ArrayList<>(pages);
        Collections.sort(order, (a, b) -> Integer.compare(a.originalIndex, b.originalIndex));

        int last = order.size() - 1;
        for (int i = order.size() - 1; i >= 0; i--) {
            PageSelection page = order.get(i);
            if (page.keep) {
                continue;
            }
            doc.postUnoCommand(".uno:GoToStartOfDoc", "", false);
            for (int k = 0; k < page.originalIndex; k++) {
                doc.postUnoCommand(".uno:GoToStartOfNextPage", "", false);
            }
            doc.postUnoCommand(".uno:GoToEndOfPageSel", "", false);
            doc.postUnoCommand(".uno:Delete", "", false);
            Thread.sleep(100);
            if (page.originalIndex == last) {
                if (page.originalIndex > 0) {
                    doc.postUnoCommand(".uno:SwBackspace", "", false);
                }
                last--;
            } else {
                doc.postUnoCommand(".uno:GoRight", "", false);
                doc.postUnoCommand(".uno:SwBackspace", "", false);
            }
            Thread.sleep(400);
        }
    }
}
