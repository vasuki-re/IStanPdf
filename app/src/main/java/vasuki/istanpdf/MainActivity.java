package vasuki.istanpdf;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import vasuki.istanpdf.model.PageItem;
import vasuki.istanpdf.libreoffice.DocxPreviewService;
import vasuki.istanpdf.pdf.ImagesToPdf;
import vasuki.istanpdf.pdf.PdfMerge;
import vasuki.istanpdf.pdf.PdfReorder;
import vasuki.istanpdf.pdf.PdfToJpegZip;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_PICK_MERGE_PDF = 10;
    private static final int REQ_PICK_REORDER_PDF = 12;
    private static final int REQ_PICK_REORDER_DOCX = 13;
    private static final int REQ_PICK_REORDER_DOCX_EXPORT = 14;
    private static final int REQ_SAVE_MERGE_PDF = 20;
    private static final int REQ_SAVE_REORDER_PDF = 22;
    private static final int REQ_SAVE_REORDER_DOCX_EXPORT = 23;

    private static final int REQ_PICK_SPLIT_PDF = 30;
    private static final int REQ_SAVE_SPLIT_PDF = 31;
    private static final int REQ_PICK_IMAGES_TO_PDF = 32;
    private static final int REQ_SAVE_IMAGES_TO_PDF = 33;
    private static final int REQ_PICK_PDF_TO_JPG = 34;
    private static final int REQ_SAVE_PDF_TO_JPG = 35;
    private static final int REQ_PICK_IMAGES_TO_PDF_ADD = 36;
    private static final int REQ_PICK_DOCX_ADD = 37;
    private static final int REQ_PICK_PDF_ADD = 38;
    private static final int REQ_PICK_MERGE_PDF_ADD = 39;

    private static final long LIBREOFFICE_OPERATION_TIMEOUT_MS = 120000L;

    private static final String MIME_PDF = "application/pdf";
    private static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    public final java.util.concurrent.atomic.AtomicBoolean cancelJob = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final List<PageItem> pages = new ArrayList<>();
    private final List<Uri> pendingUris = new ArrayList<>();
    private final List<Uri> pendingImageUris = new ArrayList<>();
    private final List<java.io.File> tempImageFiles = new ArrayList<>();

    private LinearLayout root;
    private RecyclerView pageList;
    private FrameLayout loadingOverlay;
    private TextView loadingMessage;
    private TextView loadingSubtitle;
    private com.google.android.material.progressindicator.CircularProgressIndicator loadingSpinner;
    private android.os.Handler fakeProgressHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable fakeProgressRunnable;
    private Runnable dismissOverlayRunnable;
    private TextView status;
    private ImageView statusIndicator;
    private Typeface regularFont;
    private Typeface boldFont;
    private Uri reorderSource;
    private String originalFileName;
    private boolean isHome = true;
    private boolean pagesAdded = false;
    private int activeReq;
    private ActivityResultLauncher<Intent> docLauncher;
    private static final String WAITING_TEXT = "Ready";
    private android.os.CountDownTimer activeBlinkTimer;

    private final android.os.Handler blinkHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable blinkRunnable = new Runnable() {
        private boolean isDev = false;
        @Override
        public void run() {
            if (status != null && (loadingOverlay == null || loadingOverlay.getVisibility() != View.VISIBLE)) {
                String current = status.getText().toString();
                if (current.equals("Ready") || current.equals("Dev By Ramakanth")) {
                    isDev = !isDev;
                    String nextText = isDev ? "Dev By Ramakanth" : "Ready";
                    final int animDuration = (int) (400 / 0.3f); 
                    activeBlinkTimer = new android.os.CountDownTimer(animDuration, 16) {
                        boolean textSwapped = false;
                        @Override
                        public void onTick(long millisUntilFinished) {
                            if (loadingOverlay != null && loadingOverlay.getVisibility() == View.VISIBLE) {
                                cancel();
                                status.setAlpha(1f);
                                return;
                            }
                            float progress = 1f - ((float) millisUntilFinished / animDuration);
                            if (progress < 0.5f) {
                                status.setAlpha(1f - (progress * 2f));
                            } else {
                                if (!textSwapped) {
                                    status.setText(nextText);
                                    textSwapped = true;
                                }
                                status.setAlpha((progress - 0.5f) * 2f);
                            }
                        }
                        @Override
                        public void onFinish() {
                            status.setText(nextText);
                            status.setAlpha(1f);
                        }
                    }.start();
                }
            }
            blinkHandler.postDelayed(this, 20000);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        docLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::onDocResult);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (loadingOverlay != null && loadingOverlay.getVisibility() == View.VISIBLE) {
                    return;
                }
                if (!isHome) {
                    buildHome();
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        regularFont = Typeface.createFromAsset(getAssets(), "vasuki.ttf");
        boldFont = Typeface.createFromAsset(getAssets(), "vasuki_bold.ttf");
        pruneStaleCacheFiles();
        buildHome();
        blinkHandler.postDelayed(blinkRunnable, 20000);
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        if (activeBlinkTimer != null) { activeBlinkTimer.cancel(); activeBlinkTimer = null; }
        blinkHandler.removeCallbacks(blinkRunnable);
        clearPages();
        super.onDestroy();
    }

    private void clearPages() {
        for (PageItem p : pages) {
            if (p != null && p.thumbnail != null && !p.thumbnail.isRecycled()) {
                p.thumbnail.recycle();
            }
        }
        pages.clear();
    }

    private void onDocResult(ActivityResult result) {
        Intent data = result.getData();
        int requestCode = activeReq;
        if (result.getResultCode() != Activity.RESULT_OK || data == null) {
            if (requestCode == REQ_PICK_MERGE_PDF) {
                pendingUris.clear();
            } else if (requestCode == REQ_PICK_IMAGES_TO_PDF) {
                pendingImageUris.clear();
            }
            setBusy(false, "Ready");
            return;
        }

        if (requestCode == REQ_PICK_MERGE_PDF) {
            List<Uri> selected = readSelectedUris(data);
            int dupes = 0;
            for (Uri u : selected) {
                boolean exists = false;
                for (Uri existing : pendingUris) {
                    if (existing.toString().equals(u.toString())) { exists = true; break; }
                }
                if (exists) dupes++;
                else pendingUris.add(u);
            }

            if (pendingUris.size() < 2) {
                String name = pendingUris.isEmpty() ? "" : getDisplayName(pendingUris.get(0));
                String tMsg = "Added: " + name + "\nPlease select another PDF to merge.";
                if (dupes > 0) {
                    tMsg = dupes + " duplicate(s) skipped.\n" + tMsg;
                }
                toast(tMsg);
                status.setText("Need 2 or more PDFs to merge");
                pickMany(new String[]{MIME_PDF}, REQ_PICK_MERGE_PDF);
                return;
            }

            setStatusIndicatorColor(color(R.color.istan_olive));
            status.setText("Loading PDFs...");
            worker.execute(() -> {
                List<PageItem> rendered = new ArrayList<>();
                int startIndex = 0;
                for (Uri uri : pendingUris) {
                    try {
                        Bitmap thumb = renderFirstPdfPage(uri);
                        if (thumb != null) {
                            rendered.add(new PageItem(startIndex++, thumb, getDisplayName(uri)));
                        }
                    } catch (Exception ignored) {}
                }
                runOnUiThread(() -> {
                    clearPages();
                    pages.addAll(rendered);
                    for (PageItem p : pages) p.keep = true;
                    buildPageEditor("Merge PDF", "Merge PDF", false, true);
                    status.setText("Ready");
                });
            });
            return;
        }

        if (requestCode == REQ_PICK_REORDER_PDF) {
            reorderSource = data.getData();
            loadPdfPreview(reorderSource, false);
            return;
        }

        if (requestCode == REQ_PICK_REORDER_DOCX) {
            Uri docx = data.getData();
            loadDocxPreviewViaLibreOffice(docx);
            return;
        }

        if (requestCode == REQ_PICK_REORDER_DOCX_EXPORT) {
            Uri docx = data.getData();
            originalFileName = getDisplayName(docx);
            setBusy(true, "Converting DOCX to PDF for preview...");
            worker.execute(() -> {
                try {
                    File pdfFile = vasuki.istanpdf.docx.DocxServiceBridge.exportDocxToPdfViaLibreOffice(MainActivity.this, docx);
                    List<PageItem> rendered = renderPdfPages(Uri.fromFile(pdfFile));
                    runOnUiThread(() -> {
                        clearPages();
                        pages.addAll(rendered);
                        reorderSource = Uri.fromFile(pdfFile);
                        setBusy(false, "Ready");
                        buildPageEditor("Reorder Pages from DOCX", "Save PDF", false, true);
                    });
                } catch (Exception exception) {
                    showError(exception);
                }
            });
            return;
        }

        if (requestCode == REQ_PICK_SPLIT_PDF) {
            reorderSource = data.getData();
            loadPdfPreviewForSplit(reorderSource);
            return;
        }

        if (requestCode == REQ_PICK_IMAGES_TO_PDF) {
            List<Uri> rawUris = readSelectedUris(data);
            if (rawUris.isEmpty()) return;
            pendingImageUris.clear();
            loadImagePreview(rawUris, true);
            return;
        }

        if (requestCode == REQ_PICK_MERGE_PDF_ADD) {
            List<Uri> rawUris = readSelectedUris(data);
            if (rawUris.isEmpty()) return;
            
            setStatusIndicatorColor(color(R.color.istan_olive));
            status.setText("Loading PDFs...");
            worker.execute(() -> {
                List<PageItem> rendered = new ArrayList<>();
                int startIndex = pages.size();
                for (Uri uri : rawUris) {
                    try {
                        Bitmap thumb = renderFirstPdfPage(uri);
                        if (thumb != null) {
                            rendered.add(new PageItem(startIndex++, thumb, getDisplayName(uri)));
                        }
                    } catch (Exception ignored) {}
                }
                runOnUiThread(() -> {
                    for (PageItem p : rendered) p.keep = true;
                    pendingUris.addAll(rawUris);
                    pages.addAll(rendered);
                    pagesAdded = true;
                    if (pageList != null && pageList.getAdapter() != null) {
                        pageList.getAdapter().notifyDataSetChanged();
                    }
                    status.setText("Ready");
                });
            });
            return;
        }

        if (requestCode == REQ_PICK_IMAGES_TO_PDF_ADD) {
            List<Uri> rawUris = readSelectedUris(data);
            if (rawUris.isEmpty()) return;
            loadImagePreview(rawUris, false);
            return;
        }

        if (requestCode == REQ_PICK_DOCX_ADD || requestCode == REQ_PICK_PDF_ADD) {
            List<Uri> rawUris = readSelectedUris(data);
            if (rawUris.isEmpty()) return;
            setBusy(true, "Rendering previews...", true);
            worker.execute(() -> {
                try {
                    List<PageItem> newPages = new ArrayList<>();
                    for (Uri addedUri : rawUris) {
                        String mime = getContentResolver().getType(addedUri);
                        File addedPdf = null;
                        try {
                            if (mime != null && mime.equals(MIME_DOCX)) {
                                addedPdf = vasuki.istanpdf.docx.DocxServiceBridge.exportDocxToPdfViaLibreOffice(MainActivity.this, addedUri);
                            } else if (mime != null && mime.startsWith("image/")) {
                                int size = dp(280);
                                Bitmap thumb = loadThumbnail(addedUri, size);
                                addedPdf = new File(getCacheDir(), "img_" + System.currentTimeMillis() + ".pdf");
                                com.tom_roush.pdfbox.pdmodel.PDDocument imgDoc = new com.tom_roush.pdfbox.pdmodel.PDDocument();
                                com.tom_roush.pdfbox.pdmodel.PDPage imgPage = new com.tom_roush.pdfbox.pdmodel.PDPage(
                                        new com.tom_roush.pdfbox.pdmodel.common.PDRectangle(thumb.getWidth(), thumb.getHeight()));
                                imgDoc.addPage(imgPage);
                                com.tom_roush.pdfbox.pdmodel.PDPageContentStream cs = new com.tom_roush.pdfbox.pdmodel.PDPageContentStream(imgDoc, imgPage);
                                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                                thumb.compress(Bitmap.CompressFormat.JPEG, 95, baos);
                                com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject pdImg = com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromByteArray(
                                        imgDoc, baos.toByteArray(), "img");
                                cs.drawImage(pdImg, 0, 0, thumb.getWidth(), thumb.getHeight());
                                cs.close();
                                imgDoc.save(addedPdf);
                                imgDoc.close();
                            } else {
                                addedPdf = vasuki.istanpdf.util.ContentFiles.copyUriToCache(MainActivity.this, addedUri, ".pdf");
                            }
                            File mergedFile = new File(getCacheDir(), "merged_" + System.currentTimeMillis() + ".pdf");
                            com.tom_roush.pdfbox.io.MemoryUsageSetting memSetting = com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024);
                            try (java.io.InputStream srcIn = getContentResolver().openInputStream(reorderSource);
                                 com.tom_roush.pdfbox.pdmodel.PDDocument srcDoc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(srcIn, memSetting);
                                 com.tom_roush.pdfbox.pdmodel.PDDocument addDoc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(addedPdf, memSetting)) {
                                for (int p = 0; p < addDoc.getNumberOfPages(); p++) {
                                    srcDoc.addPage(addDoc.getPage(p));
                                }
                                srcDoc.save(mergedFile);
                            }
                            Uri mergedUri = Uri.fromFile(mergedFile);
                            List<PageItem> allRendered = renderPdfPages(mergedUri);
                            int existingCount = pages.size() + newPages.size();
                            for (int k = existingCount; k < allRendered.size(); k++) {
                                newPages.add(allRendered.get(k));
                            }
                            
                            if (reorderSource != null && "file".equals(reorderSource.getScheme())) {
                                java.io.File oldFile = new java.io.File(reorderSource.getPath());
                                if (oldFile.exists() && oldFile.getAbsolutePath().startsWith(getCacheDir().getAbsolutePath())) {
                                    oldFile.delete();
                                }
                            }
                            reorderSource = mergedUri;
                        } finally {
                            if (addedPdf != null && addedPdf.exists()
                                    && addedPdf.getAbsolutePath().startsWith(getCacheDir().getAbsolutePath())) {
                                addedPdf.delete();
                            }
                        }
                    }
                    runOnUiThread(() -> {
                        pages.addAll(newPages);
                        pagesAdded = true;
                        if (pageList != null && pageList.getAdapter() != null) {
                            pageList.getAdapter().notifyDataSetChanged();
                        }
                        setBusy(false, "Ready");
                    });
                } catch (Exception exception) {
                    showError(exception);
                }
            });
            return;
        }

        if (requestCode == REQ_PICK_PDF_TO_JPG) {
            Uri pdfUri = data.getData();
            try {
                android.database.Cursor cursor = getContentResolver().query(pdfUri, new String[]{android.provider.OpenableColumns.SIZE}, null, null, null);
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        long fileSize = cursor.getLong(0);
                        if (fileSize == 0) {
                            cursor.close();
                            toast("Error: PDF file is empty (0 bytes)");
                            return;
                        }
                    }
                    cursor.close();
                }
            } catch (Exception ignored) {}
            pendingUris.clear();
            pendingUris.add(pdfUri);
            String prefix = getDisplayName(pdfUri);
            createDocument("application/zip", prefix + "_images.zip", REQ_SAVE_PDF_TO_JPG);
            return;
        }

        Uri destination = data.getData();
        if (requestCode == REQ_SAVE_MERGE_PDF) {
            List<Integer> rotations = new ArrayList<>();
            for (PageItem p : pages) rotations.add(p.rotation);
            runJob("Merging PDFs...", () -> PdfMerge.run(this, new ArrayList<>(pendingUris), rotations, destination));
        } else if (requestCode == REQ_SAVE_SPLIT_PDF) {
            List<PageItem> snapshot = new ArrayList<>(pages);
            runJob("Splitting PDF...", () -> vasuki.istanpdf.pdf.PdfSplit.run(MainActivity.this, reorderSource, snapshot, destination));
        } else if (requestCode == REQ_SAVE_IMAGES_TO_PDF) {
            List<PageItem> snapshot = new ArrayList<>(pages);
            runJob("Converting Images...", () -> ImagesToPdf.run(this, new ArrayList<>(pendingUris), snapshot, destination));
        } else if (requestCode == REQ_SAVE_PDF_TO_JPG) {
            runJob("Converting to JPGs...", () -> PdfToJpegZip.run(this, pendingUris.get(0), destination));

        } else if (requestCode == REQ_SAVE_REORDER_PDF || requestCode == REQ_SAVE_REORDER_DOCX_EXPORT) {
            Uri source = reorderSource;
            List<PageItem> snapshot = new ArrayList<>(pages);
            boolean isDocx = (requestCode == REQ_SAVE_REORDER_DOCX_EXPORT);

            boolean removed = false;
            for (PageItem p : snapshot) {
                if (!p.keep) removed = true;
            }

            boolean reordered = false;
            boolean rotated = false;
            int lastIndex = -1;
            for (PageItem p : snapshot) {
                if (p.keep) {
                    if (lastIndex != -1 && p.originalIndex < lastIndex) {
                        reordered = true;
                    }
                    if (p.rotation != 0) {
                        rotated = true;
                    }
                    lastIndex = p.originalIndex;
                }
            }

            String activeStatus;
            if (removed && reordered) {
                activeStatus = "Remove+Reorder Pages...";
            } else if (removed) {
                activeStatus = "Removing Pages...";
            } else {
                activeStatus = "Saving Pages...";
            }

            runJob(activeStatus, () -> {
                if (isDocx) {
                    vasuki.istanpdf.docx.DocxServiceBridge.saveDocxViaLibreOffice(MainActivity.this, source, snapshot, destination);
                } else {
                    PdfReorder.run(MainActivity.this, source, snapshot, destination);
                }
            });
        }
    }

    private void buildHome() {
        isHome = true;
        
        clearPages();
        pendingUris.clear();
        pendingImageUris.clear();
        reorderSource = null;
        for (java.io.File f : tempImageFiles) { if (f.exists()) f.delete(); }
        tempImageFiles.clear();

        LinearLayout mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setBackgroundColor(color(R.color.istan_background));

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(mainContainer, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        mainContainer.addView(scrollView, scrollParams);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(16));

        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = text("", 36, R.color.istan_text, true);
        android.text.SpannableString ss = new android.text.SpannableString("IStanPdf");
        ss.setSpan(new android.text.style.ForegroundColorSpan(Color.BLACK), 0, 5, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new android.text.style.ForegroundColorSpan(color(R.color.istan_olive)), 5, 8, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setText(ss);
        root.addView(title);

        TextView subtitle = text("Offline app for PDF and DOCX operations", 16, R.color.istan_text_muted, false);
        subtitle.setPadding(0, dp(4), 0, dp(24));
        root.addView(subtitle);

        root.addView(createSectionHeader("PDF TOOLS"));
        LinearLayout pdfRow1 = new LinearLayout(this);
        pdfRow1.setOrientation(LinearLayout.HORIZONTAL);
        pdfRow1.addView(dashboardCard("Merge PDF", R.drawable.merge_24px, () -> { pendingUris.clear(); pickMany(new String[]{MIME_PDF}, REQ_PICK_MERGE_PDF); }));
        pdfRow1.addView(dashboardCard("Split PDF", R.drawable.split_24px, () -> pickOne(new String[]{MIME_PDF}, REQ_PICK_SPLIT_PDF)));
        root.addView(pdfRow1);

        LinearLayout pdfRow2 = new LinearLayout(this);
        pdfRow2.setOrientation(LinearLayout.HORIZONTAL);
        pdfRow2.addView(dashboardCard("Remove/Reorder Pages", R.drawable.remove_reorder_pdf_24px, () -> pickOne(new String[]{MIME_PDF}, REQ_PICK_REORDER_PDF)));
        root.addView(pdfRow2);

        root.addView(createSectionHeader("CONVERSIONS"));
        LinearLayout convRow1 = new LinearLayout(this);
        convRow1.setOrientation(LinearLayout.HORIZONTAL);
        convRow1.addView(dashboardCard("Image to PDF", R.drawable.img2pdf_24px, () -> { pendingImageUris.clear(); pickMany(new String[]{"image/jpeg", "image/png", "image/webp", "image/bmp"}, REQ_PICK_IMAGES_TO_PDF); }));
        convRow1.addView(dashboardCard("PDF to Image", R.drawable.pdf2img_24px, () -> pickOne(new String[]{MIME_PDF}, REQ_PICK_PDF_TO_JPG)));
        root.addView(convRow1);

        root.addView(createSectionHeader("DOCX TOOLS"));
        LinearLayout docxRow1 = new LinearLayout(this);
        docxRow1.setOrientation(LinearLayout.HORIZONTAL);
        docxRow1.addView(dashboardCard("Remove Pages", R.drawable.remove_page_docx_24px, () -> pickOne(new String[]{MIME_DOCX}, REQ_PICK_REORDER_DOCX)));
        docxRow1.addView(dashboardCard("Reorder Pages", R.drawable.reorder_docx_24px, () -> pickOne(new String[]{MIME_DOCX}, REQ_PICK_REORDER_DOCX_EXPORT)));
        root.addView(docxRow1);
        
        root.addView(kofiCard("Support me on Ko-fi", R.drawable.ic_kofi, () -> {
            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/ramakanthgacharya")));
        }));
        
        View topSpacer = new View(this);
        root.addView(topSpacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setGravity(Gravity.CENTER);
        statusCard.setPadding(dp(16), dp(10), dp(20), dp(10));
        android.graphics.drawable.GradientDrawable statusBg = new android.graphics.drawable.GradientDrawable();
        statusBg.setColor(color(R.color.istan_surface));
        statusBg.setCornerRadius(dp(30));
        statusBg.setStroke(dp(1), color(R.color.istan_outline));
        statusCard.setBackground(statusBg);

        statusIndicator = new ImageView(this);
        android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
        dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dot.setColor(color(R.color.istan_olive));
        statusIndicator.setImageDrawable(dot);
        statusCard.addView(statusIndicator, new LinearLayout.LayoutParams(dp(12), dp(12)));

        status = text(WAITING_TEXT, 15, R.color.istan_olive, false);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(8), 0, 0, 0);
        statusCard.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams scParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scParams.setMargins(0, 0, 0, dp(24));
        scParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(statusCard, scParams);

        TextView footerText = text("1.0-Yotsuba", 15, R.color.istan_text_muted, false);
        LinearLayout.LayoutParams ftParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ftParams.gravity = Gravity.CENTER_HORIZONTAL;
        ftParams.setMargins(0, 0, 0, dp(4));
        root.addView(footerText, ftParams);

        setViewWithLoading(mainContainer);
    }

    private View dashboardCard(String title, int iconResId, Runnable action) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.WHITE);
        card.setRadius(dp(16));
        card.setStrokeWidth(0);
        card.setCardElevation(dp(2));
        card.setUseCompatPadding(false);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBaselineAligned(false);
        row.setPadding(dp(12), dp(20), dp(8), dp(20));
        row.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(row);

        if (iconResId != 0) {
            ImageView icon = new ImageView(this);
            icon.setImageResource(iconResId);
            icon.setColorFilter(color(R.color.istan_olive_dark));
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(26), dp(26));
            iconParams.setMargins(0, 0, dp(10), 0);
            row.addView(icon, iconParams);
        }

        TextView label = text(title, 15, R.color.istan_text, false);
        label.setMaxLines(2);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView chevron = text(">", 20, R.color.istan_text_muted, false);
        chevron.setGravity(Gravity.CENTER);
        chevron.setPadding(dp(4), 0, dp(4), 0);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        row.addView(chevron, chevronParams);

        card.setOnClickListener(view -> action.run());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        params.setMargins(dp(8), dp(8), dp(8), dp(8));
        card.setLayoutParams(params);
        return card;
    }

    private View kofiCard(String title, int iconResId, Runnable action) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.WHITE);
        card.setRadius(dp(16));
        card.setStrokeWidth(0);
        card.setCardElevation(dp(2));
        card.setUseCompatPadding(false);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBaselineAligned(false);
        row.setPadding(dp(12), dp(20), dp(12), dp(20));
        row.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(row);

        if (iconResId != 0) {
            ImageView icon = new ImageView(this);
            icon.setImageResource(iconResId);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(32), dp(26));
            iconParams.setMargins(0, 0, dp(10), 0);
            row.addView(icon, iconParams);
        }

        TextView label = text(title, 15, R.color.istan_text, false);
        label.setMaxLines(2);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        card.setOnClickListener(view -> action.run());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(8), dp(8), dp(8), dp(8));
        card.setLayoutParams(params);
        return card;
    }

    private TextView createSectionHeader(String title) {
        TextView header = text(title, 14, R.color.istan_olive_dark, true);
        header.setAllCaps(true);
        header.setPadding(dp(6), dp(20), 0, dp(4));
        return header;
    }

    private View actionButton(String title, boolean primary, Runnable action) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(color(primary ? R.color.istan_olive : R.color.istan_surface));
        card.setRadius(dp(16));
        if (!primary) {
            card.setStrokeWidth(dp(1));
            card.setStrokeColor(color(R.color.istan_outline));
        } else {
            card.setStrokeWidth(0);
        }
        card.setCardElevation(0);
        card.setUseCompatPadding(true);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(22), dp(12), dp(22), dp(12));
        row.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(row);

        TextView label = text(title, 15, R.color.istan_text, false);
        label.setTextColor(primary ? Color.WHITE : color(R.color.istan_text));
        label.setGravity(Gravity.CENTER);
        row.addView(label);

        card.setOnClickListener(view -> action.run());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(6));
        card.setLayoutParams(params);
        return card;
    }

    private void buildPageEditor(String titleText, String saveLabelText, boolean docxExport, boolean allowReorder) {
        isHome = false;
        pagesAdded = false;
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(color(R.color.istan_background));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(22), dp(16), dp(22), 0);
        outer.addView(header);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        
        TextView backArrow = text("←", 24, R.color.istan_text, true);
        backArrow.setPadding(0, 0, dp(16), dp(2));
        backArrow.setOnClickListener(v -> buildHome());
        titleRow.addView(backArrow);

        TextView title = text(titleText, 22, R.color.istan_text, true);
        titleRow.addView(title);
        
        header.addView(titleRow);

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setGravity(Gravity.CENTER);
        statusCard.setPadding(dp(16), dp(10), dp(20), dp(10));
        android.graphics.drawable.GradientDrawable statusBg = new android.graphics.drawable.GradientDrawable();
        statusBg.setColor(color(R.color.istan_surface));
        statusBg.setCornerRadius(dp(30));
        statusBg.setStroke(dp(1), color(R.color.istan_outline));
        statusCard.setBackground(statusBg);

        statusIndicator = new ImageView(this);
        android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
        dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dot.setColor(color(R.color.istan_olive));
        dot.setSize(dp(12), dp(12));
        statusIndicator.setImageDrawable(dot);
        statusCard.addView(statusIndicator);

        status = text(WAITING_TEXT, 16, R.color.istan_olive, false);
        status.setMaxLines(1);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(8), 0, dp(4), 0);
        statusCard.addView(status);

        LinearLayout.LayoutParams scParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scParams.setMargins(0, dp(16), 0, dp(12));
        header.addView(statusCard, scParams);

        pageList = new RecyclerView(this);
        pageList.setLayoutManager(new LinearLayoutManager(this));
        pageList.setPadding(dp(8), dp(8), dp(8), dp(8));
        pageList.setClipToPadding(false);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        outer.addView(pageList, listParams);

        final Runnable[] updateCountRef = new Runnable[1];

        if ("Split PDF".equals(titleText)) {
            LinearLayout selectedRow = new LinearLayout(this);
            selectedRow.setOrientation(LinearLayout.HORIZONTAL);
            selectedRow.setGravity(Gravity.CENTER_VERTICAL);
            selectedRow.setPadding(dp(22), dp(8), dp(22), dp(8));

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            selectedRow.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView selTitle = text("Selected Pages", 18, R.color.istan_text, true);
            textCol.addView(selTitle);

            TextView selSub = text("", 14, R.color.istan_olive, false);
            selSub.setPadding(0, dp(4), 0, 0);
            textCol.addView(selSub);

            MaterialCardView editBtn = new MaterialCardView(this);
            editBtn.setCardBackgroundColor(Color.TRANSPARENT);
            editBtn.setRadius(dp(8));
            editBtn.setStrokeWidth(dp(1));
            editBtn.setStrokeColor(color(R.color.istan_olive));
            editBtn.setCardElevation(0);
            editBtn.setRippleColorResource(android.R.color.transparent);

            LinearLayout editLayout = new LinearLayout(this);
            editLayout.setOrientation(LinearLayout.HORIZONTAL);
            editLayout.setGravity(Gravity.CENTER_VERTICAL);
            editLayout.setPadding(dp(12), dp(6), dp(12), dp(6));

            ImageView editIcon = new ImageView(this);
            editIcon.setImageResource(R.drawable.edit_minimal_24px);
            editIcon.setColorFilter(color(R.color.istan_olive));
            editLayout.addView(editIcon, new LinearLayout.LayoutParams(dp(16), dp(16)));

            TextView editTxt = text("Edit Range", 14, R.color.istan_olive, false);
            editTxt.setPadding(dp(6), 0, 0, 0);
            editLayout.addView(editTxt);

            editBtn.addView(editLayout);

            selectedRow.addView(editBtn);
            outer.addView(selectedRow, 1);

            updateCountRef[0] = () -> {
                int count = 0;
                for (PageItem p : pages) if (p.keep) count++;
                selSub.setText(count + " of " + pages.size() + " pages selected");
            };
            updateCountRef[0].run();

            editBtn.setOnClickListener(v -> {
                android.widget.EditText input = new android.widget.EditText(this);
                input.setTextColor(color(R.color.istan_text));
                input.setHint("Type range (e.g. 1-3, 5)...");
                input.setHintTextColor(color(R.color.istan_text_muted));
                input.setPadding(dp(16), dp(16), dp(16), dp(16));
                
                StringBuilder sb = new StringBuilder();
                int start = -1;
                int end = -1;
                List<Integer> kept = new ArrayList<>();
                for (PageItem p : pages) { if (p.keep) kept.add(p.originalIndex + 1); }
                Collections.sort(kept);
                for (int i = 0; i < kept.size(); i++) {
                    int num = kept.get(i);
                    if (start == -1) {
                        start = num;
                        end = num;
                    } else if (num == end + 1) {
                        end = num;
                    } else {
                        if (start == end) sb.append(start).append(",");
                        else sb.append(start).append("-").append(end).append(",");
                        start = num;
                        end = num;
                    }
                }
                if (start != -1) {
                     if (start == end) sb.append(start);
                     else sb.append(start).append("-").append(end);
                } else if (sb.length() > 0 && sb.charAt(sb.length() - 1) == ',') {
                     sb.setLength(sb.length() - 1);
                }
                input.setText(sb.toString());

                showCustomDialog("Edit Range", input, "Cancel", null, "Apply", () -> {
                    String rangeStr = input.getText().toString();
                    if (rangeStr.trim().isEmpty()) {
                        for (PageItem p : pages) p.keep = false;
                    } else {
                        try {
                            List<Integer> pagesToKeep = new ArrayList<>();
                            String[] parts = rangeStr.split(",");
                            for (String part : parts) {
                                String p = part.trim();
                                if (p.isEmpty()) continue;
                                if (p.contains("-")) {
                                    String[] bounds = p.split("-");
                                    if (bounds.length == 2) {
                                        int startIdx = Integer.parseInt(bounds[0].trim());
                                        int endIdx = Integer.parseInt(bounds[1].trim());
                                        for (int k = startIdx; k <= endIdx; k++) pagesToKeep.add(k - 1);
                                    }
                                } else {
                                    pagesToKeep.add(Integer.parseInt(p) - 1);
                                }
                            }
                            for (int k = 0; k < pages.size(); k++) {
                                pages.get(k).keep = pagesToKeep.contains(pages.get(k).originalIndex);
                            }
                        } catch (Exception ignored) {
                            for (PageItem p : pages) p.keep = false;
                        }
                    }
                    if (pageList.getAdapter() != null) pageList.getAdapter().notifyDataSetChanged();
                    updateCountRef[0].run();
                });
            });
        }

        boolean isSplit = titleText.startsWith("Split");
        boolean isRemoveDocx = titleText.equals("Remove Pages from DOCX");
        boolean hideRotate = isSplit || isRemoveDocx || titleText.equals("Merge PDF");
        boolean hideDrag = isSplit || isRemoveDocx;
        boolean isImg = titleText.equals("Images to PDF") 
                || titleText.equals("Remove/Reorder Pages from PDF")
                || titleText.equals("Reorder Pages from DOCX") 
                || isRemoveDocx
                || isSplit
                || titleText.equals("Merge PDF");

        boolean isMerge = titleText.equals("Merge PDF");
        PagesAdapter adapter = new PagesAdapter(() -> {
            if (updateCountRef[0] != null) {
                updateCountRef[0].run();
            }
        }, isImg, hideRotate, hideDrag, isMerge);
        pageList.setAdapter(adapter);

        if (allowReorder) {
            ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
                @Override
                public int getDragDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                    if (viewHolder.getItemViewType() == 1) return 0;
                    return super.getDragDirs(recyclerView, viewHolder);
                }

                @Override
                public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                    if (target.getItemViewType() == 1) return false;
                    int from = viewHolder.getBindingAdapterPosition();
                    int to = target.getBindingAdapterPosition();
                    if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                        return false;
                    }
                    PageItem item = pages.remove(from);
                    pages.add(to, item);
                    
                    if ("Merge PDF".equals(titleText)) {
                        Uri u = pendingUris.remove(from);
                        pendingUris.add(to, u);
                    }
                    
                    adapter.notifyItemMoved(from, to);
                    return true;
                }

                @Override
                public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                }
            });
            touchHelper.attachToRecyclerView(pageList);
        }

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(22), dp(8), dp(22), dp(18));

        if ("Images to PDF".equals(titleText) || "Reorder Pages from DOCX".equals(titleText) || titleText.equals("Remove/Reorder Pages from PDF") || "Merge PDF".equals(titleText)) {
            MaterialCardView addCard = new MaterialCardView(this);
            addCard.setCardBackgroundColor(color(R.color.istan_surface));
            addCard.setCardElevation(0);
            
            android.graphics.drawable.GradientDrawable dashBg = new android.graphics.drawable.GradientDrawable();
            dashBg.setColor(Color.TRANSPARENT);
            dashBg.setCornerRadius(dp(12));
            dashBg.setStroke(dp(1), color(R.color.istan_outline), dp(4), dp(4));
            addCard.setBackground(dashBg);

            LinearLayout addRow = new LinearLayout(this);
            addRow.setOrientation(LinearLayout.HORIZONTAL);
            addRow.setGravity(Gravity.CENTER);
            addRow.setPadding(dp(16), dp(12), dp(16), dp(12));
            addCard.addView(addRow);

            android.graphics.drawable.GradientDrawable circleBg = new android.graphics.drawable.GradientDrawable();
            circleBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            circleBg.setColor(color(R.color.istan_olive));

            TextView plusText = text("+", 20, R.color.istan_text, false);
            plusText.setTextColor(Color.WHITE);
            plusText.setGravity(Gravity.CENTER);
            plusText.setBackground(circleBg);
            addRow.addView(plusText, new LinearLayout.LayoutParams(dp(28), dp(28)));

            String labelStr = "Tap to Add Images / PDF";
            if ("Reorder Pages from DOCX".equals(titleText)) labelStr = "Tap to Add Images / DOCX / PDF";
            else if ("Merge PDF".equals(titleText)) labelStr = "Tap to Add PDF";
            TextView addTitle = text(labelStr, 15, R.color.istan_text, false);
            addTitle.setPadding(dp(12), 0, 0, 0);
            addRow.addView(addTitle);

            addCard.setOnClickListener(v -> {
                if ("Reorder Pages from DOCX".equals(titleText)) {
                    pickMany(new String[]{"image/jpeg", "image/png", "image/webp", "image/bmp", MIME_DOCX, MIME_PDF}, REQ_PICK_DOCX_ADD);
                } else if (titleText.equals("Remove/Reorder Pages from PDF")) {
                    pickMany(new String[]{"image/jpeg", "image/png", "image/webp", "image/bmp", MIME_PDF}, REQ_PICK_PDF_ADD);
                } else if ("Merge PDF".equals(titleText)) {
                    pickMany(new String[]{MIME_PDF}, REQ_PICK_MERGE_PDF_ADD);
                } else {
                    pickMany(new String[]{"image/jpeg", "image/png", "image/webp", "image/bmp", "application/pdf"}, REQ_PICK_IMAGES_TO_PDF_ADD);
                }
            });

            LinearLayout.LayoutParams acLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            acLp.setMargins(0, 0, 0, dp(12));
            footer.addView(addCard, 0, acLp);
        }

        View save = actionButton(saveLabelText, true, () -> {
            boolean removed = false;
            int keepCount = 0;
            for (PageItem p : pages) {
                if (!p.keep) removed = true;
                else keepCount++;
            }
            if (keepCount == 0) {
                toast("Please select at least one page to save.");
                return;
            }

            boolean reordered = false;
            boolean rotated = false;
            int lastIndex = -1;
            for (PageItem p : pages) {
                if (p.keep) {
                    if (lastIndex != -1 && p.originalIndex < lastIndex) {
                        reordered = true;
                    }
                    if (p.rotation != 0) {
                        rotated = true;
                    }
                    lastIndex = p.originalIndex;
                }
            }

            if ("Merge PDF".equals(titleText)) {
                if (pendingUris.size() < 2) {
                    toast("Please keep at least 2 PDFs to merge.");
                    return;
                }
                createDocument(MIME_PDF, "MergedDocument.pdf", REQ_SAVE_MERGE_PDF);
                return;
            }

            if ("Images to PDF".equals(titleText)) {
            } else if (!removed && !reordered && !rotated && !pagesAdded) {
                if ("Reorder Pages from DOCX".equals(titleText)) {
                    TextView msg = text("No pages were modified. Do you still want to save the document as a PDF?", 14, R.color.istan_text_muted, false);
                    msg.setPadding(0, dp(8), 0, dp(8));
                    showCustomDialog("Save without changes?", msg, "Cancel", null, "Save as PDF", () -> {
                        String prefix = originalFileName != null ? originalFileName : getDisplayName(reorderSource);
                        createDocument(MIME_PDF, prefix + ".pdf", REQ_SAVE_REORDER_PDF);
                    });
                    return;
                } else {
                    status.setText("No changes to save.");
                    return;
                }
            }

            int changedCount = 0;
            if (removed) changedCount++;
            if (reordered) changedCount++;
            if (rotated) changedCount++;
            if (pagesAdded) changedCount++;

            String suffix = "";
            if (titleText.startsWith("Split")) {
                suffix = "_split";
            } else {
                if (changedCount > 1) {
                    suffix = "_modified";
                } else if (pagesAdded) {
                    suffix = "_added";
                } else if (reordered) {
                    suffix = "_reordered";
                } else if (removed) {
                    suffix = "_removed";
                } else if (rotated) {
                    suffix = "_rotated";
                }
            }

            String prefix = originalFileName != null ? originalFileName : getDisplayName(reorderSource);
            if (docxExport) {
                createDocument(MIME_DOCX, prefix + suffix + ".docx", REQ_SAVE_REORDER_DOCX_EXPORT);
            } else if ("Images to PDF".equals(titleText)) {
                String imgPrefix = getDisplayName(pendingUris.isEmpty() ? null : pendingUris.get(0));
                createDocument(MIME_PDF, imgPrefix + "_converted.pdf", REQ_SAVE_IMAGES_TO_PDF);
            } else {
                createDocument(MIME_PDF, prefix + suffix + ".pdf", REQ_SAVE_REORDER_PDF);
            }
        });
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveLp.gravity = Gravity.CENTER_HORIZONTAL;
        saveLp.setMargins(0, 0, 0, dp(8));
        footer.addView(save, saveLp);

        outer.addView(footer);
        setViewWithLoading(outer);
    }

    private class PagesAdapter extends RecyclerView.Adapter<PagesAdapter.PageViewHolder> {

        private Runnable checkboxSyncListener;
        private boolean isImg;
        private boolean hideRotate;
        private boolean hideDrag;
        private boolean isMerge;

        PagesAdapter(Runnable checkboxSyncListener, boolean isImg, boolean hideRotate, boolean hideDrag, boolean isMerge) {
            this.checkboxSyncListener = checkboxSyncListener;
            this.isImg = isImg;
            this.hideRotate = hideRotate;
            this.hideDrag = hideDrag;
            this.isMerge = isMerge;
        }

        class PageViewHolder extends RecyclerView.ViewHolder {
            ImageView preview;
            CheckBox keep;
            TextView info;
            TextView titleText;
            ImageView rotateLeft;
            ImageView rotateRight;
            TextView crossBtn;

            PageViewHolder(View itemView, ImageView preview, CheckBox keep, TextView info, TextView titleText, ImageView rotateLeft, ImageView rotateRight, TextView crossBtn) {
                super(itemView);
                this.preview = preview;
                this.keep = keep;
                this.info = info;
                this.titleText = titleText;
                this.rotateLeft = rotateLeft;
                this.rotateRight = rotateRight;
                this.crossBtn = crossBtn;
            }
        }

        @Override
        public int getItemCount() {
            return pages.size();
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @NonNull
        @Override
        public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

            MaterialCardView card = new MaterialCardView(MainActivity.this);
            card.setCardBackgroundColor(color(R.color.istan_surface));
            card.setRadius(dp(16));
            card.setStrokeWidth(dp(1));
            card.setStrokeColor(color(R.color.istan_outline));
            card.setCardElevation(0);
            card.setUseCompatPadding(true);

            if (isImg) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(12), dp(20), dp(12));
                card.addView(row);

                TextView dragHandle = text("⋮⋮", 24, R.color.istan_olive, false);
                dragHandle.setPadding(dp(8), dp(4), dp(16), dp(4));
                if (hideDrag) dragHandle.setVisibility(View.GONE);
                row.addView(dragHandle);

                ImageView preview = new ImageView(MainActivity.this);
                preview.setBackgroundColor(Color.WHITE);
                preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                preview.setAdjustViewBounds(true);
                
                FrameLayout previewFrame = new FrameLayout(MainActivity.this);
                android.graphics.drawable.GradientDrawable frameBg = new android.graphics.drawable.GradientDrawable();
                frameBg.setColor(Color.WHITE);
                frameBg.setStroke(dp(1), Color.parseColor("#BDBDBD"));
                previewFrame.setBackground(frameBg);
                previewFrame.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                row.addView(previewFrame, new LinearLayout.LayoutParams(dp(54), dp(72)));

                LinearLayout infoBox = new LinearLayout(MainActivity.this);
                infoBox.setOrientation(LinearLayout.VERTICAL);
                infoBox.setPadding(dp(16), 0, dp(8), 0);
                row.addView(infoBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                LinearLayout titleRow = new LinearLayout(MainActivity.this);
                titleRow.setOrientation(LinearLayout.HORIZONTAL);
                titleRow.setGravity(Gravity.CENTER_VERTICAL);
                titleRow.setBaselineAligned(false);
                infoBox.addView(titleRow);

                TextView titleText = text("", 15, R.color.istan_text, true);
                titleText.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                titleText.setSingleLine(true);
                titleRow.addView(titleText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                android.content.res.ColorStateList cbColors = new android.content.res.ColorStateList(
                     new int[][]{
                          new int[]{-android.R.attr.state_checked},
                          new int[]{android.R.attr.state_checked}
                     },
                     new int[]{
                          color(R.color.istan_outline),
                          color(R.color.istan_olive)
                     }
                );
                CheckBox keepBox = new CheckBox(MainActivity.this);
                keepBox.setButtonTintList(cbColors);
                
                LinearLayout.LayoutParams cbParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cbParams.setMargins(dp(8), 0, 0, 0);
                cbParams.gravity = Gravity.CENTER_VERTICAL;
                
                TextView crossBtn = null;
                if (isMerge) {
                    keepBox.setVisibility(View.GONE);
                    crossBtn = new TextView(MainActivity.this);
                    crossBtn.setText("\u2715");
                    crossBtn.setTextSize(22);
                    crossBtn.setTextColor(color(R.color.istan_olive));
                    crossBtn.setPadding(dp(12), dp(4), dp(8), dp(4));
                    titleRow.addView(crossBtn);
                } else {
                    titleRow.addView(keepBox, cbParams);
                }

                TextView infoText = text("", 13, R.color.istan_olive, false);
                infoText.setPadding(0, dp(2), 0, 0);
                if (isMerge) infoText.setVisibility(View.GONE);
                infoBox.addView(infoText);
                
                ImageView rotateLeft = new ImageView(MainActivity.this);
                rotateLeft.setImageResource(R.drawable.rotate_left);
                rotateLeft.setColorFilter(color(R.color.istan_olive_dark));
                rotateLeft.setPadding(dp(8), dp(8), dp(8), dp(8));
                if (hideRotate) rotateLeft.setVisibility(View.GONE);
                
                ImageView rotateRight = new ImageView(MainActivity.this);
                rotateRight.setImageResource(R.drawable.rotate_right);
                rotateRight.setColorFilter(color(R.color.istan_olive_dark));
                rotateRight.setPadding(dp(8), dp(8), dp(8), dp(8));
                if (hideRotate) rotateRight.setVisibility(View.GONE);

                LinearLayout actionsBox = new LinearLayout(MainActivity.this);
                actionsBox.setOrientation(LinearLayout.HORIZONTAL);
                actionsBox.setGravity(Gravity.CENTER_VERTICAL);
                actionsBox.addView(rotateLeft, new LinearLayout.LayoutParams(dp(40), dp(40)));
                actionsBox.addView(rotateRight, new LinearLayout.LayoutParams(dp(40), dp(40)));
                row.addView(actionsBox);

                RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, dp(8));
                card.setLayoutParams(lp);

                return new PageViewHolder(card, preview, keepBox, infoText, titleText, rotateLeft, rotateRight, crossBtn);
            } else {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(12), dp(12), dp(12), dp(12));
                card.addView(row);

                ImageView preview = new ImageView(MainActivity.this);
                preview.setBackgroundColor(Color.WHITE);
                preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                preview.setAdjustViewBounds(true);
                preview.setMaxHeight(dp(560));
                row.addView(preview, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                LinearLayout infoBox = new LinearLayout(MainActivity.this);
                infoBox.setOrientation(LinearLayout.HORIZONTAL);
                infoBox.setGravity(Gravity.CENTER_VERTICAL);
                infoBox.setBaselineAligned(false);
                infoBox.setPadding(0, dp(12), 0, 0);
                row.addView(infoBox, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView infoText = text("", 16, R.color.istan_text, true);
                infoBox.addView(infoText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                ImageView rotateLeft = new ImageView(MainActivity.this);
                rotateLeft.setImageResource(R.drawable.rotate_left);
                rotateLeft.setColorFilter(color(R.color.istan_olive_dark));
                rotateLeft.setPadding(dp(8), dp(8), dp(8), dp(8));
                infoBox.addView(rotateLeft, new LinearLayout.LayoutParams(dp(40), dp(40)));

                ImageView rotateRight = new ImageView(MainActivity.this);
                rotateRight.setImageResource(R.drawable.rotate_right);
                rotateRight.setColorFilter(color(R.color.istan_olive_dark));
                rotateRight.setPadding(dp(8), dp(8), dp(8), dp(8));
                infoBox.addView(rotateRight, new LinearLayout.LayoutParams(dp(40), dp(40)));

                CheckBox keepBox = new CheckBox(MainActivity.this);
                keepBox.setText("Keep");
                keepBox.setTextSize(18);
                keepBox.setTextColor(color(R.color.istan_text));
                keepBox.setTypeface(regularFont);
                LinearLayout.LayoutParams keepLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                keepLp.gravity = Gravity.CENTER_VERTICAL;
                infoBox.addView(keepBox, keepLp);

                RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, dp(8));
                card.setLayoutParams(lp);

                return new PageViewHolder(card, preview, keepBox, infoText, null, rotateLeft, rotateRight, null);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
            PageItem item = pages.get(position);
            holder.preview.setImageBitmap(item.thumbnail);
            
            if (isMerge && holder.crossBtn != null) {
                holder.crossBtn.setOnClickListener(v -> {
                    int pos = holder.getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        pages.remove(pos);
                        pendingUris.remove(pos);
                        notifyItemRemoved(pos);
                        if (checkboxSyncListener != null) checkboxSyncListener.run();
                    }
                });
            }
            
            if (isImg) {
                if (holder.titleText != null) {
                    holder.titleText.setVisibility(View.VISIBLE);
                    holder.titleText.setText(item.displayName != null && !item.displayName.trim().isEmpty() ? item.displayName : "Page " + (position + 1));
                }
                holder.info.setText(item.keep ? "Selected" : "Unselected");
                holder.info.setTextColor(color(item.keep ? R.color.istan_olive : R.color.istan_text_muted));
                
                holder.preview.setOnClickListener(v -> {
                    android.app.Dialog dialog = new android.app.Dialog(MainActivity.this, android.R.style.Theme_Translucent_NoTitleBar);
                    dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                    dialog.getWindow().setStatusBarColor(Color.parseColor("#E6252525"));
                    dialog.getWindow().setNavigationBarColor(Color.parseColor("#E6252525"));
                    dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));

                    LinearLayout dialogRoot = new LinearLayout(MainActivity.this);
                    dialogRoot.setOrientation(LinearLayout.VERTICAL);
                    dialogRoot.setBackgroundColor(Color.parseColor("#E6252525"));

                    FrameLayout topBar = new FrameLayout(MainActivity.this);
                    TextView closeBtn = new TextView(MainActivity.this);
                    closeBtn.setText("\u2715");
                    closeBtn.setTextColor(Color.WHITE);
                    closeBtn.setTextSize(26);
                    closeBtn.setPadding(dp(16), dp(12), dp(16), dp(12));
                    FrameLayout.LayoutParams clsLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    clsLp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
                    topBar.addView(closeBtn, clsLp);
                    dialogRoot.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                    FrameLayout cardContainer = new FrameLayout(MainActivity.this);
                    LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
                    containerLp.setMargins(dp(12), dp(4), dp(12), dp(4));
                    dialogRoot.addView(cardContainer, containerLp);

                    MaterialCardView imgCard = new MaterialCardView(MainActivity.this);
                    imgCard.setCardBackgroundColor(Color.BLACK);
                    imgCard.setRadius(dp(12));
                    imgCard.setCardElevation(dp(8));
                    imgCard.setStrokeColor(Color.parseColor("#33FFFFFF"));
                    imgCard.setStrokeWidth(dp(1));

                    ImageView fullImg = new ImageView(MainActivity.this) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                            int w = getMeasuredWidth();
                            int h = getMeasuredHeight();
                            int maxH = (int) (w * 1.5f);
                            if (h > maxH) {
                                setMeasuredDimension(w, maxH);
                            }
                        }
                    };
                    fullImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    fullImg.setAdjustViewBounds(true);
                    imgCard.addView(fullImg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                    FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    cardLp.gravity = Gravity.CENTER;
                    cardContainer.addView(imgCard, cardLp);

                    LinearLayout bottomControls = new LinearLayout(MainActivity.this);
                    bottomControls.setOrientation(LinearLayout.HORIZONTAL);
                    bottomControls.setGravity(Gravity.CENTER_VERTICAL);
                    bottomControls.setBaselineAligned(false);
                    bottomControls.setPadding(dp(24), dp(8), dp(24), dp(8));
                    
                    TextView pageCounter = text("", 16, R.color.istan_surface, true);
                    pageCounter.setTextColor(Color.WHITE);
                    bottomControls.addView(pageCounter);
                    
                    View spacerBottom = new View(MainActivity.this);
                    bottomControls.addView(spacerBottom, new LinearLayout.LayoutParams(0, 0, 1));

                    TextView backTxt = new TextView(MainActivity.this);
                    backTxt.setText("Back");
                    backTxt.setTextColor(Color.parseColor("#AAAAAA"));
                    backTxt.setTextSize(14);
                    backTxt.setPadding(0, 0, dp(16), 0);
                    bottomControls.addView(backTxt);
                    
                    ImageView rotLeft = new ImageView(MainActivity.this);
                    rotLeft.setImageResource(R.drawable.rotate_left);
                    rotLeft.setColorFilter(Color.WHITE);
                    rotLeft.setPadding(dp(8), dp(8), dp(8), dp(8));
                    if (hideRotate) rotLeft.setVisibility(View.GONE);
                    bottomControls.addView(rotLeft, new LinearLayout.LayoutParams(dp(40), dp(40)));
                    
                    ImageView rotRight = new ImageView(MainActivity.this);
                    rotRight.setImageResource(R.drawable.rotate_right);
                    rotRight.setColorFilter(Color.WHITE);
                    rotRight.setPadding(dp(8), dp(8), dp(8), dp(8));
                    if (hideRotate) rotRight.setVisibility(View.GONE);
                    bottomControls.addView(rotRight, new LinearLayout.LayoutParams(dp(40), dp(40)));
                    
                    View sepCb = new View(MainActivity.this);
                    bottomControls.addView(sepCb, new LinearLayout.LayoutParams(dp(16), 0));

                    android.content.res.ColorStateList cbColors = new android.content.res.ColorStateList(
                         new int[][]{new int[]{-android.R.attr.state_checked}, new int[]{android.R.attr.state_checked}},
                         new int[]{Color.parseColor("#888888"), color(R.color.istan_olive)}
                    );
                    CheckBox keepBox = new CheckBox(MainActivity.this);
                    keepBox.setButtonTintList(cbColors);
                    keepBox.setText("Keep");
                    keepBox.setTextSize(14);
                    keepBox.setTextColor(Color.WHITE);
                    keepBox.setPadding(dp(8), 0, 0, 0);
                    LinearLayout.LayoutParams keepLpDialog = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    keepLpDialog.gravity = Gravity.CENTER_VERTICAL;
                    bottomControls.addView(keepBox, keepLpDialog);
                    
                    dialogRoot.addView(bottomControls, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));


                    final int[] currentPos = {holder.getBindingAdapterPosition()};

                    Runnable updateUi = () -> {
                        PageItem p = pages.get(currentPos[0]);
                        fullImg.setImageBitmap(p.thumbnail);
                        if (p.thumbnail != null && p.thumbnail.getWidth() > p.thumbnail.getHeight()) {
                            imgCard.setContentPadding(dp(24), 0, dp(24), 0);
                        } else {
                            imgCard.setContentPadding(0, dp(48), 0, dp(48));
                        }
                        pageCounter.setText((currentPos[0] + 1) + " / " + pages.size());
                        keepBox.setOnCheckedChangeListener(null);
                        keepBox.setChecked(p.keep);
                        keepBox.setOnCheckedChangeListener((bw, checked) -> {
                            p.keep = checked;
                            if (checkboxSyncListener != null) checkboxSyncListener.run();
                            RecyclerView.ViewHolder vh = pageList.findViewHolderForAdapterPosition(currentPos[0]);
                            if (vh instanceof PageViewHolder) {
                                ((PageViewHolder) vh).keep.setChecked(checked);
                                ((PageViewHolder) vh).info.setText(checked ? "Selected" : "Unselected");
                                ((PageViewHolder) vh).info.setTextColor(color(checked ? R.color.istan_olive : R.color.istan_text_muted));
                            }
                        });
                    };
                    
                    updateUi.run();
                    
                    rotLeft.setOnClickListener(x -> {
                        PageItem p = pages.get(currentPos[0]);
                        p.rotation = (p.rotation - 90) % 360;
                        android.graphics.Matrix matrix = new android.graphics.Matrix();
                        matrix.postRotate(-90);
                        Bitmap newThumb = Bitmap.createBitmap(p.thumbnail, 0, 0, p.thumbnail.getWidth(), p.thumbnail.getHeight(), matrix, true);
                        p.thumbnail.recycle();
                        p.thumbnail = newThumb;
                        updateUi.run();
                        RecyclerView.ViewHolder vh = pageList.findViewHolderForAdapterPosition(currentPos[0]);
                        if (vh instanceof PageViewHolder) {
                            ((PageViewHolder) vh).preview.setImageBitmap(p.thumbnail);
                        }
                    });
                    rotRight.setOnClickListener(x -> {
                        PageItem p = pages.get(currentPos[0]);
                        p.rotation = (p.rotation + 90) % 360;
                        android.graphics.Matrix matrix = new android.graphics.Matrix();
                        matrix.postRotate(90);
                        Bitmap newThumb = Bitmap.createBitmap(p.thumbnail, 0, 0, p.thumbnail.getWidth(), p.thumbnail.getHeight(), matrix, true);
                        p.thumbnail.recycle();
                        p.thumbnail = newThumb;
                        updateUi.run();
                        RecyclerView.ViewHolder vh = pageList.findViewHolderForAdapterPosition(currentPos[0]);
                        if (vh instanceof PageViewHolder) {
                            ((PageViewHolder) vh).preview.setImageBitmap(p.thumbnail);
                        }
                    });

                    fullImg.setOnTouchListener(new View.OnTouchListener() {
                        float startX = 0;
                        @Override
                        public boolean onTouch(View view, android.view.MotionEvent event) {
                            switch (event.getAction()) {
                                case android.view.MotionEvent.ACTION_DOWN:
                                    startX = event.getX();
                                    return true;
                                case android.view.MotionEvent.ACTION_UP:
                                    float diff = event.getX() - startX;
                                    if (diff > 150) {
                                        currentPos[0] = (currentPos[0] - 1 + pages.size()) % pages.size();
                                        updateUi.run();
                                    } else if (diff < -150) {
                                        currentPos[0] = (currentPos[0] + 1) % pages.size();
                                        updateUi.run();
                                    } else {
                                        view.performClick();
                                    }
                                    return true;
                            }
                            return true;
                        }
                    });

                    backTxt.setOnClickListener(x -> dialog.dismiss());
                    closeBtn.setOnClickListener(x -> dialog.dismiss());
                    dialog.setContentView(dialogRoot);
                    dialog.show();
                });

            } else {
                holder.info.setText("Page " + (item.originalIndex + 1));
                holder.preview.setOnClickListener(v -> {
                    android.app.Dialog dialog = new android.app.Dialog(MainActivity.this, android.R.style.Theme_Translucent_NoTitleBar);
                    dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                    dialog.getWindow().setStatusBarColor(Color.parseColor("#E6252525"));
                    dialog.getWindow().setNavigationBarColor(Color.parseColor("#E6252525"));
                    dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));

                    LinearLayout dialogRoot2 = new LinearLayout(MainActivity.this);
                    dialogRoot2.setOrientation(LinearLayout.VERTICAL);
                    dialogRoot2.setBackgroundColor(Color.parseColor("#E6252525"));

                    FrameLayout topBar2 = new FrameLayout(MainActivity.this);
                    TextView closeBtn = new TextView(MainActivity.this);
                    closeBtn.setText("\u2715");
                    closeBtn.setTextColor(Color.WHITE);
                    closeBtn.setTextSize(26);
                    closeBtn.setPadding(dp(16), dp(12), dp(16), dp(12));
                    FrameLayout.LayoutParams clsLp2 = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    clsLp2.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
                    topBar2.addView(closeBtn, clsLp2);
                    dialogRoot2.addView(topBar2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                    FrameLayout cardContainer = new FrameLayout(MainActivity.this);
                    LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
                    containerLp.setMargins(dp(12), dp(4), dp(12), dp(4));
                    dialogRoot2.addView(cardContainer, containerLp);

                    MaterialCardView imgCard = new MaterialCardView(MainActivity.this);
                    imgCard.setCardBackgroundColor(Color.BLACK);
                    imgCard.setRadius(dp(12));
                    imgCard.setCardElevation(dp(8));
                    imgCard.setStrokeColor(Color.parseColor("#33FFFFFF"));
                    imgCard.setStrokeWidth(dp(1));

                    ImageView fullImg = new ImageView(MainActivity.this) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                            int w = getMeasuredWidth();
                            int h = getMeasuredHeight();
                            int maxH = (int) (w * 1.5f);
                            if (h > maxH) {
                                setMeasuredDimension(w, maxH);
                            }
                        }
                    };
                    fullImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    fullImg.setAdjustViewBounds(true);
                    imgCard.addView(fullImg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                    FrameLayout.LayoutParams cardLp2 = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    cardLp2.gravity = Gravity.CENTER;
                    cardContainer.addView(imgCard, cardLp2);

                    LinearLayout bottomControls = new LinearLayout(MainActivity.this);
                    bottomControls.setOrientation(LinearLayout.HORIZONTAL);
                    bottomControls.setGravity(Gravity.CENTER_VERTICAL);
                    bottomControls.setBaselineAligned(false);
                    bottomControls.setPadding(dp(24), dp(8), dp(24), dp(8));

                    TextView pageCounter = text("", 16, R.color.istan_surface, true);
                    pageCounter.setTextColor(Color.WHITE);
                    bottomControls.addView(pageCounter);

                    View spacerBottom = new View(MainActivity.this);
                    bottomControls.addView(spacerBottom, new LinearLayout.LayoutParams(0, 0, 1));

                    TextView backTxt = new TextView(MainActivity.this);
                    backTxt.setText("Back");
                    backTxt.setTextColor(Color.parseColor("#AAAAAA"));
                    backTxt.setTextSize(14);
                    backTxt.setPadding(0, 0, dp(16), 0);
                    bottomControls.addView(backTxt);

                    ImageView rotLeft = new ImageView(MainActivity.this);
                    rotLeft.setImageResource(R.drawable.rotate_left);
                    rotLeft.setColorFilter(Color.WHITE);
                    rotLeft.setPadding(dp(8), dp(8), dp(8), dp(8));
                    if (hideRotate) rotLeft.setVisibility(View.GONE);
                    bottomControls.addView(rotLeft, new LinearLayout.LayoutParams(dp(40), dp(40)));

                    ImageView rotRight = new ImageView(MainActivity.this);
                    rotRight.setImageResource(R.drawable.rotate_right);
                    rotRight.setColorFilter(Color.WHITE);
                    rotRight.setPadding(dp(8), dp(8), dp(8), dp(8));
                    if (hideRotate) rotRight.setVisibility(View.GONE);
                    bottomControls.addView(rotRight, new LinearLayout.LayoutParams(dp(40), dp(40)));

                    View sepCb = new View(MainActivity.this);
                    bottomControls.addView(sepCb, new LinearLayout.LayoutParams(dp(16), 0));

                    android.content.res.ColorStateList cbColors2 = new android.content.res.ColorStateList(
                         new int[][]{new int[]{-android.R.attr.state_checked}, new int[]{android.R.attr.state_checked}},
                         new int[]{Color.parseColor("#888888"), color(R.color.istan_olive)}
                    );
                    CheckBox keepBox = new CheckBox(MainActivity.this);
                    keepBox.setButtonTintList(cbColors2);
                    keepBox.setText("Keep");
                    keepBox.setTextSize(14);
                    keepBox.setTextColor(Color.WHITE);
                    keepBox.setPadding(dp(8), 0, 0, 0);
                    if (pages.size() <= 1) keepBox.setVisibility(View.GONE);
                    LinearLayout.LayoutParams keepLpDialog2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    keepLpDialog2.gravity = Gravity.CENTER_VERTICAL;
                    bottomControls.addView(keepBox, keepLpDialog2);

                    dialogRoot2.addView(bottomControls, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                    final int[] currentPos = {holder.getBindingAdapterPosition()};

                    Runnable updateUi = () -> {
                        PageItem p = pages.get(currentPos[0]);
                        fullImg.setImageBitmap(p.thumbnail);
                        if (p.thumbnail != null && p.thumbnail.getWidth() > p.thumbnail.getHeight()) {
                            imgCard.setContentPadding(dp(24), 0, dp(24), 0);
                        } else {
                            imgCard.setContentPadding(0, dp(48), 0, dp(48));
                        }
                        pageCounter.setText((currentPos[0] + 1) + " / " + pages.size());
                        keepBox.setOnCheckedChangeListener(null);
                        keepBox.setChecked(p.keep);
                        keepBox.setOnCheckedChangeListener((bw, checked) -> {
                            p.keep = checked;
                            if (checkboxSyncListener != null) checkboxSyncListener.run();
                            RecyclerView.ViewHolder vh = pageList.findViewHolderForAdapterPosition(currentPos[0]);
                            if (vh instanceof PageViewHolder) {
                                ((PageViewHolder) vh).keep.setChecked(checked);
                            }
                        });
                    };

                    updateUi.run();

                    rotLeft.setOnClickListener(x -> {
                        PageItem p = pages.get(currentPos[0]);
                        p.rotation = (p.rotation - 90) % 360;
                        android.graphics.Matrix matrix = new android.graphics.Matrix();
                        matrix.postRotate(-90);
                        Bitmap newThumb = Bitmap.createBitmap(p.thumbnail, 0, 0, p.thumbnail.getWidth(), p.thumbnail.getHeight(), matrix, true);
                        p.thumbnail.recycle();
                        p.thumbnail = newThumb;
                        updateUi.run();
                        RecyclerView.ViewHolder vh = pageList.findViewHolderForAdapterPosition(currentPos[0]);
                        if (vh instanceof PageViewHolder) {
                            ((PageViewHolder) vh).preview.setImageBitmap(p.thumbnail);
                        }
                    });
                    rotRight.setOnClickListener(x -> {
                        PageItem p = pages.get(currentPos[0]);
                        p.rotation = (p.rotation + 90) % 360;
                        android.graphics.Matrix matrix = new android.graphics.Matrix();
                        matrix.postRotate(90);
                        Bitmap newThumb = Bitmap.createBitmap(p.thumbnail, 0, 0, p.thumbnail.getWidth(), p.thumbnail.getHeight(), matrix, true);
                        p.thumbnail.recycle();
                        p.thumbnail = newThumb;
                        updateUi.run();
                        RecyclerView.ViewHolder vh = pageList.findViewHolderForAdapterPosition(currentPos[0]);
                        if (vh instanceof PageViewHolder) {
                            ((PageViewHolder) vh).preview.setImageBitmap(p.thumbnail);
                        }
                    });

                    fullImg.setOnTouchListener(new View.OnTouchListener() {
                        float startX = 0;
                        @Override
                        public boolean onTouch(View vw, android.view.MotionEvent event) {
                            switch (event.getAction()) {
                                case android.view.MotionEvent.ACTION_DOWN:
                                    startX = event.getX();
                                    return true;
                                case android.view.MotionEvent.ACTION_UP:
                                    float diff = event.getX() - startX;
                                    if (diff > 150) {
                                        currentPos[0] = (currentPos[0] - 1 + pages.size()) % pages.size();
                                        updateUi.run();
                                    } else if (diff < -150) {
                                        currentPos[0] = (currentPos[0] + 1) % pages.size();
                                        updateUi.run();
                                    } else {
                                        vw.performClick();
                                    }
                                    return true;
                            }
                            return true;
                        }
                    });

                    backTxt.setOnClickListener(x -> dialog.dismiss());
                    closeBtn.setOnClickListener(x -> dialog.dismiss());
                    dialog.setContentView(dialogRoot2);
                    dialog.show();
                });
            }

            holder.keep.setOnCheckedChangeListener(null);
            holder.keep.setChecked(item.keep);
            holder.keep.setOnCheckedChangeListener((bw, checked) -> {
                item.keep = checked;
                if (isImg && holder.info != null) {
                    holder.info.setText(checked ? "Selected" : "Unselected");
                    holder.info.setTextColor(color(checked ? R.color.istan_olive : R.color.istan_text_muted));
                }
                if (checkboxSyncListener != null) {
                    checkboxSyncListener.run();
                }
            });
            
            if (isImg) {
                holder.keep.setVisibility(View.VISIBLE);
                if (hideRotate) {
                    holder.rotateLeft.setVisibility(View.GONE);
                    holder.rotateRight.setVisibility(View.GONE);
                } else {
                    holder.rotateLeft.setVisibility(View.VISIBLE);
                    holder.rotateRight.setVisibility(View.VISIBLE);
                }
            } else {
                holder.keep.setVisibility(pages.size() <= 1 ? View.GONE : View.VISIBLE);
                if (hideRotate) {
                    holder.rotateLeft.setVisibility(View.GONE);
                    holder.rotateRight.setVisibility(View.GONE);
                } else {
                    holder.rotateLeft.setVisibility(View.VISIBLE);
                    holder.rotateRight.setVisibility(View.VISIBLE);
                }
            }
            
            holder.rotateLeft.setOnClickListener(v -> {
                item.rotation = (item.rotation - 90) % 360;
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                matrix.postRotate(-90);
                Bitmap newThumb = Bitmap.createBitmap(item.thumbnail, 0, 0, item.thumbnail.getWidth(), item.thumbnail.getHeight(), matrix, true);
                item.thumbnail.recycle();
                item.thumbnail = newThumb;
                holder.preview.setImageBitmap(item.thumbnail);
            });
            holder.rotateRight.setOnClickListener(v -> {
                item.rotation = (item.rotation + 90) % 360;
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                matrix.postRotate(90);
                Bitmap newThumb = Bitmap.createBitmap(item.thumbnail, 0, 0, item.thumbnail.getWidth(), item.thumbnail.getHeight(), matrix, true);
                item.thumbnail.recycle();
                item.thumbnail = newThumb;
                holder.preview.setImageBitmap(item.thumbnail);
            });
        }

    }

    private void loadPdfPreview(Uri uri, boolean docxExport) {
        setBusy(true, "Rendering page previews...", true);
        worker.execute(() -> {
            try {
                List<PageItem> rendered = renderPdfPages(uri);
                runOnUiThread(() -> {
                    clearPages();
                    pages.addAll(rendered);
                    reorderSource = uri;
                    originalFileName = null;
                    setBusy(false, "Ready");
                    buildPageEditor("Remove/Reorder Pages from PDF", "Save PDF", docxExport, true);
                });
            } catch (Exception exception) {
                showError(exception);
            }
        });
    }

    private void loadPdfPreviewForSplit(Uri uri) {
        setBusy(true, "Rendering page previews...", true);
        worker.execute(() -> {
            try {
                List<PageItem> rendered = renderPdfPages(uri);
                if (rendered.size() <= 1) {
                    throw new Exception("Cannot split a 1-page PDF");
                }
                for (PageItem p : rendered) p.keep = true;
                runOnUiThread(() -> {
                    clearPages();
                    pages.addAll(rendered);
                    reorderSource = uri;
                    originalFileName = null;
                    setBusy(false, "Ready");
                    buildPageEditor("Split PDF", "Save PDF", false, false);
                });
            } catch (Exception exception) {
                showError(exception);
            }
        });
    }

    private void loadImagePreview(List<Uri> rawUris, boolean clearExisting) {
        setBusy(true, "Rendering image previews...", true);
        worker.execute(() -> {
            try {
                List<PageItem> rendered = new ArrayList<>();
                List<Uri> processedUris = new ArrayList<>();
                int startIndex = clearExisting ? 0 : pages.size();
                for (int i = 0; i < rawUris.size(); i++) {
                    if (cancelJob.get() || Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("Cancelled by user");
                    }
                    final int current = i + 1;
                    final int total = rawUris.size();
                    runOnUiThread(() -> {
                        if (loadingSubtitle != null && loadingOverlay != null && loadingOverlay.getVisibility() == View.VISIBLE) {
                            if (fakeProgressRunnable != null) {
                                fakeProgressHandler.removeCallbacks(fakeProgressRunnable);
                                fakeProgressRunnable = null;
                            }
                            loadingSubtitle.setText("Item " + current + " of " + total);
                            if (loadingSpinner != null) {
                                if (loadingSpinner.getMax() != total) {
                                    loadingSpinner.setIndeterminate(false);
                                    loadingSpinner.setMax(total);
                                }
                                loadingSpinner.setProgressCompat(current, true);
                            }
                        }
                    });
                    Uri uri = rawUris.get(i);
                    String mime = getContentResolver().getType(uri);
                    if (mime != null && mime.equals(MIME_PDF)) {
                        List<PageItem> pdfPages = renderPdfPages(uri);
                        int pIdx = 1;
                        for (PageItem pdfP : pdfPages) {
                            File f = new File(getCacheDir(), "pdf_to_img_" + System.currentTimeMillis() + "_" + pIdx + ".jpg");
                            try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
                                pdfP.thumbnail.compress(Bitmap.CompressFormat.JPEG, 95, out);
                            }
                            tempImageFiles.add(f);
                            processedUris.add(Uri.fromFile(f));
                            rendered.add(new PageItem(startIndex++, pdfP.thumbnail, "PDF Page " + pIdx));
                            pIdx++;
                        }
                    } else {
                        processedUris.add(uri);
                        int size = dp(280);
                        Bitmap thumb = loadThumbnail(uri, size);
                        rendered.add(new PageItem(startIndex++, thumb, getDisplayName(uri)));
                    }
                }
                runOnUiThread(() -> {
                    if (clearExisting) clearPages();
                    pages.addAll(rendered);
                    pendingUris.addAll(processedUris);
                    reorderSource = null;
                    originalFileName = null;
                    if (clearExisting) {
                        setBusy(false, "Ready");
                        buildPageEditor("Images to PDF", "Save PDF", false, true);
                    } else {
                        if (pageList != null && pageList.getAdapter() != null) {
                            pageList.getAdapter().notifyDataSetChanged();
                        }
                        setBusy(false, "Ready");
                    }
                });
            } catch (Exception exception) {
                showError(exception);
            }
        });
    }

    private Bitmap loadThumbnail(Uri uri, int maxDim) throws Exception {
        android.graphics.BitmapFactory.Options opt = new android.graphics.BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalArgumentException("Cannot open image file");
            android.graphics.BitmapFactory.decodeStream(in, null, opt);
        }
        int scale = 1;
        while (opt.outWidth / scale / 2 >= maxDim && opt.outHeight / scale / 2 >= maxDim) {
            scale *= 2;
        }
        android.graphics.BitmapFactory.Options opt2 = new android.graphics.BitmapFactory.Options();
        opt2.inSampleSize = scale;
        try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalArgumentException("Cannot open image file for decoding");
            Bitmap decoded = android.graphics.BitmapFactory.decodeStream(in, null, opt2);
            if (decoded == null) throw new IllegalArgumentException("Cannot decode image file. Format not supported.");
            return decoded;
        }
    }

    private void loadDocxPreviewViaLibreOffice(Uri docx) {
        setBusy(true, "Rendering DOCX previews...");
        worker.execute(() -> {
            try {
                File pdfFile = vasuki.istanpdf.docx.DocxServiceBridge.exportDocxToPdfViaLibreOffice(MainActivity.this, docx);
                List<PageItem> rendered = renderPdfPages(Uri.fromFile(pdfFile));

                runOnUiThread(() -> {
                    clearPages();
                    pages.addAll(rendered);
                    reorderSource = docx;
                    originalFileName = null;
                    setBusy(false, "Ready");
                    buildPageEditor("Remove Pages from DOCX", "Save DOCX", true, false);
                });
            } catch (Exception exception) {
                showError(exception);
            }
        });
    }


    private Bitmap renderFirstPdfPage(Uri uri) throws Exception {
        java.io.File tempPdf = null;
        ParcelFileDescriptor fd = null;
        try {
            if ("file".equals(uri.getScheme())) {
                fd = ParcelFileDescriptor.open(new java.io.File(uri.getPath()), ParcelFileDescriptor.MODE_READ_ONLY);
            } else {
                tempPdf = vasuki.istanpdf.util.ContentFiles.copyUriToCache(this, uri, ".pdf");
                fd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY);
            }
            if (fd == null) {
                throw new IllegalArgumentException("Cannot open PDF file");
            }
            try (android.graphics.pdf.PdfRenderer renderer = new android.graphics.pdf.PdfRenderer(fd)) {
                if (renderer.getPageCount() > 0) {
                    try (android.graphics.pdf.PdfRenderer.Page page = renderer.openPage(0)) {
                        int screenWidth = getResources().getDisplayMetrics().widthPixels;
                        int width = Math.max(dp(140), screenWidth / 2);
                        int height = width * page.getHeight() / Math.max(1, page.getWidth());
                        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.WHITE);
                        page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        return bitmap;
                    }
                }
            }
            return null;
        } finally {
            if (fd != null) {
                try { fd.close(); } catch (Exception ignored) {}
            }
            if (tempPdf != null && tempPdf.exists()) {
                tempPdf.delete();
            }
        }
    }

    private List<PageItem> renderPdfPages(Uri uri) throws Exception {
        List<PageItem> rendered = new ArrayList<>();
        java.io.File tempPdf = null;
        ParcelFileDescriptor fd = null;
        try {
            if ("file".equals(uri.getScheme())) {
                fd = ParcelFileDescriptor.open(new java.io.File(uri.getPath()), ParcelFileDescriptor.MODE_READ_ONLY);
            } else {
                tempPdf = vasuki.istanpdf.util.ContentFiles.copyUriToCache(this, uri, ".pdf");
                fd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY);
            }
            if (fd == null) {
                throw new IllegalArgumentException("Cannot open PDF file");
            }
            try (PdfRenderer renderer = new PdfRenderer(fd)) {
                for (int i = 0; i < renderer.getPageCount(); i++) {
                    if (cancelJob.get() || Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("Cancelled by user");
                    }
                    final int current = i + 1;
                    final int total = renderer.getPageCount();
                    runOnUiThread(() -> {
                        if (loadingSubtitle != null && loadingOverlay != null && loadingOverlay.getVisibility() == View.VISIBLE) {
                            if (fakeProgressRunnable != null) {
                                fakeProgressHandler.removeCallbacks(fakeProgressRunnable);
                                fakeProgressRunnable = null;
                            }
                            loadingSubtitle.setText("Page " + current + " of " + total);
                            if (loadingSpinner != null) {
                                if (loadingSpinner.getMax() != total) {
                                    loadingSpinner.setIndeterminate(false);
                                    loadingSpinner.setMax(total);
                                }
                                loadingSpinner.setProgressCompat(current, true);
                            }
                        }
                    });
                    try (PdfRenderer.Page page = renderer.openPage(i)) {
                        int screenWidth = getResources().getDisplayMetrics().widthPixels;
                        int width = Math.max(dp(280), screenWidth - dp(64));
                        int height = Math.max(dp(360), width * page.getHeight() / Math.max(1, page.getWidth()));
                        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.WHITE);
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        rendered.add(new PageItem(i, bitmap));
                    }
                }
            }
        } catch (Exception e) {
            for (PageItem p : rendered) {
                if (p.thumbnail != null && !p.thumbnail.isRecycled()) p.thumbnail.recycle();
            }
            rendered.clear();
            throw e;
        } finally {
            if (fd != null) {
                try { fd.close(); } catch (Exception ignored) {}
            }
            if (tempPdf != null && tempPdf.exists()) {
                tempPdf.delete();
            }
        }
        return rendered;
    }

    private void pickMany(String[] mimeTypes, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (mimeTypes != null && mimeTypes.length == 1) {
            intent.setType(mimeTypes[0]);
        } else {
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        }
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        launchDocIntent(intent, requestCode);
    }

    private void pickOne(String[] mimeTypes, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (mimeTypes != null && mimeTypes.length == 1) {
            intent.setType(mimeTypes[0]);
        } else {
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        }
        launchDocIntent(intent, requestCode);
    }

    private void createDocument(String mimeType, String name, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, name);
        launchDocIntent(intent, requestCode);
    }

    private void launchDocIntent(Intent intent, int requestCode) {
        activeReq = requestCode;
        docLauncher.launch(intent);
    }

    private List<Uri> readSelectedUris(Intent data) {
        List<Uri> uris = new ArrayList<>();
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                uris.add(clipData.getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        return uris;
    }

    public void showCustomDialog(String titleStr, View content, String negativeStr, Runnable negativeAction, String positiveStr, Runnable positiveAction) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        dialog.setCancelable(false);

        LinearLayout dialogRoot = new LinearLayout(this);
        dialogRoot.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(color(R.color.istan_surface));
        gd.setCornerRadius(dp(18));
        gd.setStroke(dp(1), color(R.color.istan_olive));
        dialogRoot.setBackground(gd);
        dialogRoot.setPadding(dp(20), dp(20), dp(20), dp(20));

        TextView title = text(titleStr, 20, R.color.istan_text, true);
        title.setPadding(0, 0, 0, dp(12));
        dialogRoot.addView(title);

        if (content != null) {
            dialogRoot.addView(content);
        }

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);
        btnRow.setPadding(0, dp(12), 0, 0);

        TextView cancel = text(negativeStr != null ? negativeStr : "Cancel", 14, R.color.istan_text_muted, true);
        cancel.setPadding(dp(16), dp(8), dp(16), dp(8));
        cancel.setOnClickListener(v -> {
            dialog.dismiss();
            if (negativeAction != null) negativeAction.run();
        });
        btnRow.addView(cancel);

        TextView positive = text(positiveStr, 14, R.color.istan_olive, true);
        positive.setPadding(dp(16), dp(8), dp(0), dp(8));
        positive.setOnClickListener(v -> {
            dialog.dismiss();
            if (positiveAction != null) positiveAction.run();
        });
        btnRow.addView(positive);

        dialogRoot.addView(btnRow);
        dialog.setContentView(dialogRoot);
        dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels * 0.85), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.show();
    }

    private void runJob(String message, Job job) {
        setBusy(true, message);
        worker.submit(() -> {
            try {
                job.run();
                runOnUiThread(() -> {
                    setBusy(false, "Ready");
                    toast("Done!");
                });
            } catch (Exception exception) {
                showError(exception);
            }
        });
    }

    private void setStatusIndicatorColor(int color) {
        if (statusIndicator != null && statusIndicator.getDrawable() instanceof android.graphics.drawable.GradientDrawable) {
            ((android.graphics.drawable.GradientDrawable) statusIndicator.getDrawable()).setColor(color);
        }
    }

    private void showError(Exception exception) {
        if (exception instanceof InterruptedException || "Cancelled by user".equals(exception.getMessage())) {
            runOnUiThread(() -> setBusy(false, "Ready"));
            return;
        }
        runOnUiThread(() -> {
            if (loadingOverlay != null) {
                loadingOverlay.setVisibility(View.GONE);
            }
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            if (status != null) {
                setStatusIndicatorColor(Color.RED);
                status.setText(message);
            }
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
        });
    }

    private TextView text(String value, int sp, int colorRes, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color(colorRes));
        textView.setTypeface(bold ? boldFont : regularFont);
        textView.setIncludeFontPadding(true);
        return textView;
    }

    private void setViewWithLoading(View view) {
        if (loadingOverlay != null && loadingOverlay.getParent() != null) {
            ((ViewGroup) loadingOverlay.getParent()).removeView(loadingOverlay);
        }

        FrameLayout rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(color(R.color.istan_background));
        
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        if (view instanceof ViewGroup) {
            ((ViewGroup) view).setClipToPadding(false);
        }
        
        rootFrame.addView(view, new FrameLayout.LayoutParams(-1, -1));

        loadingOverlay = new FrameLayout(this);
        loadingOverlay.setBackgroundColor(Color.argb(178, 18, 18, 18));
        loadingOverlay.setClickable(true);
        loadingOverlay.setFocusable(true);
        loadingOverlay.setVisibility(View.GONE);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(32), dp(32), dp(32), dp(24));
        android.graphics.drawable.GradientDrawable panelBg = new android.graphics.drawable.GradientDrawable();
        panelBg.setColor(color(R.color.istan_surface));
        panelBg.setCornerRadius(dp(28));
        panel.setBackground(panelBg);

        loadingSpinner = new com.google.android.material.progressindicator.CircularProgressIndicator(this);
        loadingSpinner.setIndeterminate(true);
        loadingSpinner.setIndicatorColor(color(R.color.istan_olive_dark));
        loadingSpinner.setTrackColor(color(R.color.istan_outline));
        loadingSpinner.setTrackThickness(dp(4));
        loadingSpinner.setIndicatorSize(dp(48));
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        panel.addView(loadingSpinner, spinnerParams);

        loadingMessage = text(WAITING_TEXT, 18, R.color.istan_text, true);
        loadingMessage.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = dp(20);
        panel.addView(loadingMessage, messageParams);

        loadingSubtitle = text("", 15, R.color.istan_text_muted, false);
        loadingSubtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(8);
        panel.addView(loadingSubtitle, subParams);

        MaterialCardView cancelBtn = new MaterialCardView(this);
        cancelBtn.setCardBackgroundColor(Color.TRANSPARENT);
        cancelBtn.setRadius(dp(20));
        cancelBtn.setStrokeWidth(dp(1));
        cancelBtn.setStrokeColor(color(R.color.istan_outline));
        cancelBtn.setCardElevation(0);
        cancelBtn.setUseCompatPadding(false);

        TextView cancelText = text("Cancel", 15, R.color.istan_olive_dark, false);
        cancelText.setPadding(dp(24), dp(10), dp(24), dp(10));
        cancelBtn.addView(cancelText);
        
        cancelBtn.setOnClickListener(v -> {
             cancelJob.set(true);
             setBusy(false, "Ready");
        });

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.topMargin = dp(24);
        panel.addView(cancelBtn, btnParams);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                dp(300),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        loadingOverlay.addView(panel, panelParams);

        rootFrame.addView(loadingOverlay, new FrameLayout.LayoutParams(-1, -1));
        setContentView(rootFrame);
        androidx.core.view.ViewCompat.requestApplyInsets(rootFrame);
    }

    private void setBusy(boolean busy, String message) {
        setBusy(busy, message, false);
    }

    private void setBusy(boolean busy, String message, boolean isDeterminate) {
        if (fakeProgressRunnable != null) {
            fakeProgressHandler.removeCallbacks(fakeProgressRunnable);
            fakeProgressRunnable = null;
        }
        if (dismissOverlayRunnable != null) {
            fakeProgressHandler.removeCallbacks(dismissOverlayRunnable);
            dismissOverlayRunnable = null;
        }

        if (busy) {
            cancelJob.set(false);
            if (loadingSpinner != null) {
                if (isDeterminate) {
                    loadingSpinner.setIndeterminate(false);
                    loadingSpinner.setMax(100);
                    loadingSpinner.setProgressCompat(0, false);
                } else {
                    loadingSpinner.setIndeterminate(false);
                    loadingSpinner.setMax(100);
                    loadingSpinner.setProgressCompat(0, false);
                    final int[] progress = {0};
                    fakeProgressRunnable = new Runnable() {
                        @Override
                        public void run() {
                            if (progress[0] < 90) {
                                progress[0] += 2;
                                loadingSpinner.setProgressCompat(progress[0], true);
                                fakeProgressHandler.postDelayed(this, 300);
                            }
                        }
                    };
                    fakeProgressHandler.postDelayed(fakeProgressRunnable, 300);
                }
            }
            if (loadingOverlay != null) {
                loadingOverlay.setVisibility(View.VISIBLE);
            }
            if (loadingMessage != null) {
                loadingMessage.setText(message);
            }
            if (loadingSubtitle != null) {
                loadingSubtitle.setText("");
                loadingSubtitle.setVisibility(View.VISIBLE);
            }
            if (status != null) {
                status.animate().cancel();
                status.setAlpha(1f);
                setStatusIndicatorColor(color(R.color.istan_olive));
                status.setText(message);
            }
            if (pageList != null) {
                pageList.suppressLayout(true);
            }
        } else {
            if (loadingSpinner != null && loadingOverlay != null && loadingOverlay.getVisibility() == View.VISIBLE) {
                loadingSpinner.setProgressCompat(loadingSpinner.getMax(), true);
                if (loadingMessage != null) {
                    loadingMessage.setText(message);
                }
                if (loadingSubtitle != null) {
                    loadingSubtitle.setText("");
                }
                dismissOverlayRunnable = () -> {
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                    if (loadingSubtitle != null) loadingSubtitle.setVisibility(View.GONE);
                    if (status != null) {
                        status.animate().cancel();
                        status.setAlpha(1f);
                        setStatusIndicatorColor(color(R.color.istan_olive));
                        status.setText(message);
                    }
                    if (pageList != null) {
                        pageList.suppressLayout(false);
                    }
                    dismissOverlayRunnable = null;
                };
                fakeProgressHandler.postDelayed(dismissOverlayRunnable, 400);
            } else {
                if (loadingOverlay != null) {
                    loadingOverlay.setVisibility(View.GONE);
                }
                if (loadingMessage != null) {
                    loadingMessage.setText(message);
                }
                if (loadingSubtitle != null) {
                    loadingSubtitle.setText("");
                    loadingSubtitle.setVisibility(View.GONE);
                }
                if (status != null) {
                    status.animate().cancel();
                    status.setAlpha(1f);
                    setStatusIndicatorColor(color(R.color.istan_olive));
                    status.setText(message);
                }
                if (pageList != null) {
                    pageList.suppressLayout(false);
                }
            }
        }
    }

    private LinearLayout.LayoutParams fullWidthMargins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private int color(int colorRes) {
        return getColor(colorRes);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        runOnUiThread(() -> {
            if ("Done!".equals(message)) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            } else {
                setBusy(false, message);
            }
        });
    }

    private String getDisplayName(Uri uri) {
        if (uri == null) return "IStanPdf";
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (index != -1) {
                    String name = cursor.getString(index);
                    int dot = name.lastIndexOf('.');
                    if (dot > 0) return name.substring(0, dot);
                    return name;
                }
            }
        } catch (Exception ignored) {
        }
        return "IStanPdf";
    }

    private interface Job {
        void run() throws Exception;
    }

    private void pruneStaleCacheFiles() {
        java.io.File cache = getCacheDir();
        java.io.File[] files = cache.listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - (24L * 60 * 60 * 1000);
        for (java.io.File f : files) {
            String n = f.getName();
            if (n.startsWith("istanpdf_") || n.startsWith("merged_")
                    || n.startsWith("img_") || n.startsWith("pdf_to_img_")) {
                if (f.lastModified() < cutoff) f.delete();
            }
        }
    }
}
