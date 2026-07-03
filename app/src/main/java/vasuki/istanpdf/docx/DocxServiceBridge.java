package vasuki.istanpdf.docx;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;

import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import vasuki.istanpdf.libreoffice.DocxPreviewService;
import vasuki.istanpdf.model.PageItem;

public final class DocxServiceBridge {
    private static final long LIBREOFFICE_OPERATION_TIMEOUT_MS = 600000L;

    private DocxServiceBridge() {
    }

    public static File exportDocxToPdfViaLibreOffice(Context ctx, Uri docx) throws Exception {
        Intent intent = new Intent(ctx, DocxPreviewService.class);
        intent.putExtra(DocxPreviewService.EXTRA_OPERATION, DocxPreviewService.OP_EXPORT_DOCX_TO_PDF);
        intent.putExtra(DocxPreviewService.EXTRA_DOCX_URI, docx.toString());

        Bundle result = runLibreOfficeDocxOperation(ctx, intent, "Converting DOCX to PDF");
        String pdfPath = result.getString(DocxPreviewService.EXTRA_PDF_PATH);
        if (pdfPath == null || pdfPath.isEmpty()) {
            throw new IllegalStateException("LibreOffice did not return a PDF");
        }

        File pdfFile = new File(pdfPath);
        if (!pdfFile.exists()) {
            throw new IllegalStateException("LibreOffice returned a missing PDF file");
        }
        return pdfFile;
    }

    public static void saveDocxViaLibreOffice(Context ctx, Uri source, List<PageItem> snapshot, Uri destination) throws Exception {
        int[] originalIndices = new int[snapshot.size()];
        boolean[] keepFlags = new boolean[snapshot.size()];
        for (int i = 0; i < snapshot.size(); i++) {
            PageItem page = snapshot.get(i);
            originalIndices[i] = page.originalIndex;
            keepFlags[i] = page.keep;
        }

        Intent intent = new Intent(ctx, DocxPreviewService.class);
        intent.putExtra(DocxPreviewService.EXTRA_OPERATION, DocxPreviewService.OP_SAVE_EDITED_DOCX);
        intent.putExtra(DocxPreviewService.EXTRA_DOCX_URI, source.toString());
        intent.putExtra(DocxPreviewService.EXTRA_DESTINATION_URI, destination.toString());
        intent.putExtra(DocxPreviewService.EXTRA_PAGE_INDICES, originalIndices);
        intent.putExtra(DocxPreviewService.EXTRA_PAGE_KEEP, keepFlags);

        runLibreOfficeDocxOperation(ctx, intent, "Saving DOCX");
    }

    private static Bundle runLibreOfficeDocxOperation(Context ctx, Intent operationIntent, String failureMessage) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger resultCode = new AtomicInteger(DocxPreviewService.RESULT_ERROR);
        AtomicReference<Bundle> resultData = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        Runnable fail = () -> {
            if (completed.compareAndSet(false, true)) {
                Bundle error = new Bundle();
                error.putString(DocxPreviewService.EXTRA_ERROR, failureMessage + " Failed");
                resultCode.set(DocxPreviewService.RESULT_ERROR);
                resultData.set(error);
                latch.countDown();
            }
        };

        ResultReceiver receiver = new ResultReceiver(null) {
            @Override
            protected void onReceiveResult(int resultCodeValue, Bundle resultDataValue) {
                if (completed.compareAndSet(false, true)) {
                    resultCode.set(resultCodeValue);
                    resultData.set(resultDataValue);
                    latch.countDown();
                }
            }
        };

        operationIntent.putExtra(DocxPreviewService.EXTRA_RECEIVER, receiver);

        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                fail.run();
            }

            @Override
            public void onBindingDied(ComponentName name) {
                fail.run();
            }
        };

        Context appCtx = ctx.getApplicationContext();
        boolean bound = appCtx.bindService(new Intent(appCtx, DocxPreviewService.class), connection, Context.BIND_AUTO_CREATE);
        if (!bound) {
            throw new IllegalStateException("LibreOffice service could not be started");
        }

        try {
            ComponentName started = ctx.startService(operationIntent);
            if (started == null) {
                throw new IllegalStateException("LibreOffice service could not be started");
            }

            if (!latch.await(LIBREOFFICE_OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException(failureMessage + " Failed");
            }

            Bundle result = resultData.get();
            if (resultCode.get() != DocxPreviewService.RESULT_OK) {
                throw new IllegalStateException(failureMessage + " Failed");
            }

            return result == null ? new Bundle() : result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failureMessage + " was interrupted", exception);
        } finally {
            try {
                appCtx.unbindService(connection);
            } catch (Exception ignored) {
            }
        }
    }
}
