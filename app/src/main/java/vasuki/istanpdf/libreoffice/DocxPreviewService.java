package vasuki.istanpdf.libreoffice;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;

import androidx.annotation.Nullable;
import androidx.core.content.IntentCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import vasuki.istanpdf.docx.DocxRemove;
import vasuki.istanpdf.docx.DocxReorder;

public class DocxPreviewService extends Service {
    public static final String EXTRA_OPERATION = "operation";
    public static final String EXTRA_DOCX_URI = "docx_uri";
    public static final String EXTRA_DESTINATION_URI = "destination_uri";
    public static final String EXTRA_PAGE_INDICES = "page_indices";
    public static final String EXTRA_PAGE_KEEP = "page_keep";
    public static final String EXTRA_RECEIVER = "receiver";
    public static final String EXTRA_PDF_PATH = "pdf_path";
    public static final String EXTRA_ERROR = "error";
    public static final String OP_EXPORT_DOCX_TO_PDF = "export_docx_to_pdf";
    public static final String OP_SAVE_EDITED_DOCX = "save_edited_docx";
    public static final int RESULT_OK = 1;
    public static final int RESULT_ERROR = 2;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final IBinder binder = new Binder();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        ResultReceiver receiver = IntentCompat.getParcelableExtra(intent, EXTRA_RECEIVER, ResultReceiver.class);
        String operation = intent.getStringExtra(EXTRA_OPERATION);
        worker.execute(() -> {
            Bundle result = new Bundle();
            try {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Service was cancelled via timeout");
                }
                if (OP_EXPORT_DOCX_TO_PDF.equals(operation)) {
                    File pdf = LibreOfficeDocumentEngine.exportDocxToPdf(this, Uri.parse(requireStringExtra(intent, EXTRA_DOCX_URI)));
                    if (Thread.currentThread().isInterrupted()) {
                        if (pdf != null && pdf.exists()) pdf.delete();
                        return;
                    }
                    result.putString(EXTRA_PDF_PATH, pdf.getAbsolutePath());
                } else if (OP_SAVE_EDITED_DOCX.equals(operation)) {
                    Uri source = Uri.parse(requireStringExtra(intent, EXTRA_DOCX_URI));
                    Uri destination = Uri.parse(requireStringExtra(intent, EXTRA_DESTINATION_URI));
                    int[] originalIndices = intent.getIntArrayExtra(EXTRA_PAGE_INDICES);
                    boolean[] keepFlags = intent.getBooleanArrayExtra(EXTRA_PAGE_KEEP);
                    if (originalIndices == null || keepFlags == null || originalIndices.length != keepFlags.length) {
                        throw new IllegalArgumentException("Invalid page selection");
                    }

                    List<DocxRemove.PageSelection> removeSelections = new ArrayList<>(originalIndices.length);
                    List<DocxReorder.PageSelection> reorderSelections = new ArrayList<>(originalIndices.length);
                    int last = -1;
                    boolean isPlainRemove = true;
                    for (int i = 0; i < originalIndices.length; i++) {
                        removeSelections.add(new DocxRemove.PageSelection(originalIndices[i], keepFlags[i]));
                        reorderSelections.add(new DocxReorder.PageSelection(originalIndices[i], keepFlags[i]));
                        if (keepFlags[i]) {
                            if (last != -1 && originalIndices[i] < last) {
                                isPlainRemove = false;
                            }
                            last = originalIndices[i];
                        }
                    }

                    if (isPlainRemove) {
                        DocxRemove.run(this, source, removeSelections, destination);
                    } else {
                        DocxReorder.run(this, source, reorderSelections, destination);
                    }
                    if (Thread.currentThread().isInterrupted()) return;
                } else {
                    throw new IllegalArgumentException("Unsupported LibreOffice operation");
                }
                if (receiver != null) {
                    receiver.send(RESULT_OK, result);
                }
            } catch (Throwable throwable) {
                if (Thread.currentThread().isInterrupted()) return;
                result.putString(EXTRA_ERROR, message(throwable));
                if (receiver != null) {
                    receiver.send(RESULT_ERROR, result);
                }
            } finally {
                stopSelf(startId);
            }
        });
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private static String message(Throwable throwable) {
        if (throwable.getMessage() != null) {
            return throwable.getMessage();
        }
        return throwable.getClass().getSimpleName();
    }

    private static String requireStringExtra(Intent intent, String key) {
        String value = intent.getStringExtra(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return value;
    }
}
