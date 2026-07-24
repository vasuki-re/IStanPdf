package vasuki.istanpdf;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import vasuki.istanpdf.di.AppModule;
import vasuki.istanpdf.model.PageItem;
import vasuki.istanpdf.presentation.EditorViewBuilder;
import vasuki.istanpdf.presentation.EditorViewModel;
import vasuki.istanpdf.presentation.HomeViewBuilder;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_PICK_MERGE_PDF = 10;
    private static final int REQ_PICK_REORDER_PDF = 12;
    private static final int REQ_PICK_REORDER_DOCX = 13;
    private static final int REQ_PICK_REORDER_DOCX_EXPORT = 14;
    private static final int REQ_SAVE_MERGE_PDF = 20;
    private static final int REQ_SAVE_REORDER_PDF = 22;
    private static final int REQ_SAVE_REORDER_DOCX_EXPORT = 23;

    private static final int REQ_PICK_IMAGES_TO_PDF = 32;
    private static final int REQ_SAVE_IMAGES_TO_PDF = 33;
    private static final int REQ_PICK_PDF_TO_JPG = 34;
    private static final int REQ_SAVE_PDF_TO_JPG = 35;
    private static final int REQ_PICK_IMAGES_TO_PDF_ADD = 36;
    private static final int REQ_PICK_DOCX_ADD = 37;
    private static final int REQ_PICK_PDF_ADD = 38;
    private static final int REQ_PICK_MERGE_PDF_ADD = 39;

    private static final String MIME_PDF = "application/pdf";
    private static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    public final AtomicBoolean cancelJob = new AtomicBoolean(false);

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
    private boolean isHome = true;
    private int activeReq;
    private String themeToken;
    private ActivityResultLauncher<Intent> docLauncher;
    private static final String WAITING_TEXT = "Ready";
    private android.os.CountDownTimer activeBlinkTimer;

    private EditorViewModel editorViewModel;

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

        editorViewModel = new ViewModelProvider(this).get(EditorViewModel.class);

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
        applySystemBarTheme();

        regularFont = Typeface.createFromAsset(getAssets(), "vasuki.ttf");
        boldFont = Typeface.createFromAsset(getAssets(), "vasuki_bold.ttf");
        pruneStaleCacheFiles();
        themeToken = ThemePrefs.token(this);
        buildHome();
        blinkHandler.postDelayed(blinkRunnable, 20000);

        android.content.SharedPreferences prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE);
        checkForUpdates();

        if (prefs.getBoolean("improve_docx_perf", false)) {
            AppModule.get().docxEngine().preLoad();
        }
    }

    private void checkForUpdates() {
        android.content.SharedPreferences prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE);
        if (!prefs.getBoolean("check_updates", true)) return;

        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("https://cdn.jsdelivr.net/gh/vasuki-re/IStanPdf@Mitsuba/changelog.txt");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                String line;
                String versionname = "";
                long remoteVersionCode = 0;
                String link = "";
                StringBuilder changelog = new StringBuilder();
                boolean parsingChangelog = false;
                
                while ((line = in.readLine()) != null) {
                    if (parsingChangelog) {
                        changelog.append(line).append("\n");
                    } else if (line.startsWith("versionname:")) {
                        versionname = line.substring("versionname:".length()).trim();
                    } else if (line.startsWith("versioncode:")) {
                        try { remoteVersionCode = Long.parseLong(line.substring("versioncode:".length()).trim()); } catch (Exception ignored) {}
                    } else if (line.startsWith("link:")) {
                        link = line.substring("link:".length()).trim();
                    } else if (line.startsWith("changelog:")) {
                        parsingChangelog = true;
                    }
                }
                in.close();
                
                long currentVersionCode = 0;
                try {
                    currentVersionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                } catch (Exception ignored) {}

                if (remoteVersionCode > currentVersionCode) {
                    String finalVersionName = versionname;
                    String finalLink = link;
                    String finalChangelog = changelog.toString().trim();
                    
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        
                        View changelogView = createChangelogView(finalChangelog);
                        
                        showCustomDialog("Update Available: " + finalVersionName, changelogView, "Later", null, "Download", () -> {
                            String architecture = "arm";
                            for (String abi : android.os.Build.SUPPORTED_ABIS) {
                                if (abi.contains("arm64")) {
                                    architecture = "arm64";
                                    break;
                                }
                            }
                            String downloadUrl = finalLink.replace("*", architecture);
                            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(downloadUrl)));
                        });
                    });
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    private CharSequence parseMarkdownText(String text) {
        android.text.SpannableStringBuilder builder = new android.text.SpannableStringBuilder();
        int currentIndex = 0;
        while (currentIndex < text.length()) {
            int startBold = text.indexOf("**", currentIndex);
            if (startBold != -1) {
                int endBold = text.indexOf("**", startBold + 2);
                if (endBold != -1) {
                    builder.append(text.substring(currentIndex, startBold));
                    int startSpan = builder.length();
                    builder.append(text.substring(startBold + 2, endBold));
                    builder.setSpan(new CustomTypefaceSpan(boldFont), startSpan, builder.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    builder.setSpan(new android.text.style.ForegroundColorSpan(color(R.color.istan_text)), startSpan, builder.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    currentIndex = endBold + 2;
                    continue;
                }
            }
            builder.append(text.substring(currentIndex));
            break;
        }
        return builder;
    }

    private View createChangelogView(String changelogText) {
        ScrollView scrollView = new ScrollView(this) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.70);
                int heightSize = View.MeasureSpec.getSize(heightMeasureSpec);
                if (maxHeight > 0 && (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED || heightSize > maxHeight)) {
                    heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST);
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setFadingEdgeLength(dp(16));
        scrollView.setVerticalFadingEdgeEnabled(true);
        
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, dp(4), 0, dp(12));
        
        String[] lines = changelogText.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                View space = new View(this);
                space.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)));
                container.addView(space);
                continue;
            }
            
            if (line.startsWith("- ")) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, 0, 0, dp(4));
                
                TextView bullet = text("•", 15, R.color.istan_olive, true);
                bullet.setPadding(dp(8), dp(1), dp(8), 0);
                
                TextView body = text("", 15, R.color.istan_text_muted, false);
                body.setLineSpacing(dp(2), 1.0f);
                body.setText(parseMarkdownText(line.substring(2)));
                
                row.addView(bullet);
                row.addView(body);
                container.addView(row);
            } else if (line.startsWith("[") && line.endsWith("]")) {
                TextView header = text(line.substring(1, line.length() - 1), 17, R.color.istan_olive, true);
                header.setPadding(dp(4), dp(8), 0, dp(4));
                container.addView(header);
            } else {
                TextView body = text("", 15, R.color.istan_text_muted, false);
                body.setLineSpacing(dp(2), 1.0f);
                body.setPadding(dp(8), 0, dp(8), dp(4));
                body.setText(parseMarkdownText(line));
                container.addView(body);
            }
        }
        
        scrollView.addView(container);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.weight = 1.0f;
        scrollView.setLayoutParams(params);
        
        return scrollView;
    }

    @Override
    protected void onResume() {
        super.onResume();
        String latestThemeToken = ThemePrefs.token(this);
        if (themeToken != null && !themeToken.equals(latestThemeToken)) {
            themeToken = latestThemeToken;
            applySystemBarTheme();
            if (isHome) {
                buildHome();
            }
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (ThemePrefs.themeMode(this) == ThemePrefs.THEME_AUTO) {
            String latestThemeToken = ThemePrefs.token(this);
            if (!latestThemeToken.equals(themeToken)) {
                themeToken = latestThemeToken;
                applySystemBarTheme();
                if (isHome) {
                    buildHome();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        if (activeBlinkTimer != null) { activeBlinkTimer.cancel(); activeBlinkTimer = null; }
        blinkHandler.removeCallbacks(blinkRunnable);
        editorViewModel.clearPages();
        
        super.onDestroy();
    }

    private void clearPages() {
        editorViewModel.clearPages();
    }

    private void onDocResult(ActivityResult result) {
        Intent data = result.getData();
        int requestCode = activeReq;
        if (result.getResultCode() != Activity.RESULT_OK || data == null) {
            if (requestCode == REQ_PICK_MERGE_PDF) {
                editorViewModel.clearPendingUris();
            } else if (requestCode == REQ_PICK_IMAGES_TO_PDF) {
                editorViewModel.clearPendingImageUris();
            }
            setBusy(false, "Ready");
            return;
        }

        if (requestCode == REQ_PICK_MERGE_PDF) {
            List<Uri> selected = readSelectedUris(data);
            List<Uri> pendingUris = editorViewModel.getPendingUris();
            int dupes = 0;
            int emptyFiles = 0;
            for (Uri u : selected) {
                boolean exists = false;
                for (Uri existing : pendingUris) {
                    if (existing.toString().equals(u.toString())) { exists = true; break; }
                }
                if (exists) { dupes++; continue; }
                try {
                    android.database.Cursor cursor = getContentResolver().query(u, new String[]{android.provider.OpenableColumns.SIZE}, null, null, null);
                    if (cursor != null) {
                        if (cursor.moveToFirst()) {
                            long fileSize = cursor.getLong(0);
                            if (fileSize == 0) { cursor.close(); emptyFiles++; continue; }
                        }
                        cursor.close();
                    }
                } catch (Exception ignored) {}
                pendingUris.add(u);
            }
            if (pendingUris.isEmpty()) {
                if (emptyFiles > 0) {
                    Toast.makeText(this, "Empty PDF skipped", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            if (emptyFiles > 0) {
                status.setText(emptyFiles + "Empty PDF skipped.");
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
                    editorViewModel.clearPages();
                    List<PageItem> pages = editorViewModel.getPages();
                    pages.addAll(rendered);
                    for (PageItem p : pages) p.keep = true;
                    buildPageEditor("Merge PDF", "Merge PDF", false, true);
                    status.setText("Ready");
                });
            });
            return;
        }

        if (requestCode == REQ_PICK_REORDER_PDF) {
            editorViewModel.setReorderSource(data.getData());
            loadPdfPreview(editorViewModel.getReorderSource(), false);
            return;
        }

        if (requestCode == REQ_PICK_REORDER_DOCX) {
            Uri docx = data.getData();
            loadDocxPreviewViaLibreOffice(docx);
            return;
        }

        if (requestCode == REQ_PICK_REORDER_DOCX_EXPORT) {
            Uri docx = data.getData();
            editorViewModel.setOriginalFileName(getDisplayName(docx));
            setBusy(true, "Converting DOCX to PDF for preview...");
            worker.execute(() -> {
                try {
                    File pdfFile = AppModule.get().docxToPdf().execute(docx);
                    List<PageItem> rendered = renderPdfPages(Uri.fromFile(pdfFile));
                    runOnUiThread(() -> {
                        editorViewModel.clearPages();
                        editorViewModel.getPages().addAll(rendered);
                        editorViewModel.setReorderSource(Uri.fromFile(pdfFile));
                        setBusy(false, "Ready");
                        buildPageEditor("Reorder Pages from DOCX", "Save PDF", false, true);
                    });
                } catch (Exception exception) {
                    showError(exception);
                }
            });
            return;
        }

        if (requestCode == REQ_PICK_IMAGES_TO_PDF) {
            List<Uri> rawUris = readSelectedUris(data);
            if (rawUris.isEmpty()) return;
            editorViewModel.clearPendingImageUris();
            loadImagePreview(rawUris, true);
            return;
        }

        if (requestCode == REQ_PICK_MERGE_PDF_ADD) {
            List<Uri> rawUris = readSelectedUris(data);
            if (rawUris.isEmpty()) return;

            List<Uri> pendingUris = editorViewModel.getPendingUris();
            List<PageItem> pages = editorViewModel.getPages();
            List<Uri> validUris = new ArrayList<>();
            int emptyFiles = 0;
            for (Uri u : rawUris) {
                try {
                    android.database.Cursor cursor = getContentResolver().query(u, new String[]{android.provider.OpenableColumns.SIZE}, null, null, null);
                    if (cursor != null) {
                        if (cursor.moveToFirst()) {
                            long fileSize = cursor.getLong(0);
                            if (fileSize == 0) { cursor.close(); emptyFiles++; continue; }
                        }
                        cursor.close();
                    }
                } catch (Exception ignored) {}
                validUris.add(u);
            }
            if (emptyFiles > 0) {
                status.setText(emptyFiles + " Empty PDF skipped.");
            }
            if (validUris.isEmpty()) return;
            
            setStatusIndicatorColor(color(R.color.istan_olive));
            status.setText("Loading PDFs...");
            worker.execute(() -> {
                List<PageItem> rendered = new ArrayList<>();
                int startIndex = pages.size();
                for (Uri uri : validUris) {
                    try {
                        Bitmap thumb = renderFirstPdfPage(uri);
                        if (thumb != null) {
                            rendered.add(new PageItem(startIndex++, thumb, getDisplayName(uri)));
                        }
                    } catch (Exception ignored) {}
                }
                runOnUiThread(() -> {
                    for (PageItem p : rendered) p.keep = true;
                    pendingUris.addAll(validUris);
                    pages.addAll(rendered);
                    editorViewModel.setPagesAdded(true);
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
                    List<PageItem> pages = editorViewModel.getPages();
                    List<PageItem> newPages = new ArrayList<>();
                    for (Uri addedUri : rawUris) {
                        String mime = getContentResolver().getType(addedUri);
                        File addedPdf = null;
                        try {
                            if (mime != null && mime.equals(MIME_DOCX)) {
                                addedPdf = AppModule.get().docxToPdf().execute(addedUri);
                            } else if (mime != null && mime.startsWith("image/")) {
                                int size = dp(280);
                                Bitmap thumb = loadThumbnail(addedUri, size);
                                addedPdf = new File(getCacheDir(), "img_" + System.currentTimeMillis() + ".pdf");
                                float iw = thumb.getWidth(), ih = thumb.getHeight();
                                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                                thumb.compress(Bitmap.CompressFormat.JPEG, 95, baos);
                                com.itextpdf.io.image.ImageData imgData = com.itextpdf.io.image.ImageDataFactory.createJpeg(baos.toByteArray());
                                com.itextpdf.kernel.pdf.PdfDocument imgDoc = new com.itextpdf.kernel.pdf.PdfDocument(
                                        new com.itextpdf.kernel.pdf.PdfWriter(new java.io.FileOutputStream(addedPdf)));
                                com.itextpdf.kernel.geom.PageSize ps = new com.itextpdf.kernel.geom.PageSize(iw, ih);
                                com.itextpdf.kernel.pdf.PdfPage imgPage = imgDoc.addNewPage(ps);
                                com.itextpdf.kernel.pdf.canvas.PdfCanvas pdfCanvas = new com.itextpdf.kernel.pdf.canvas.PdfCanvas(imgPage);
                                pdfCanvas.addImageFittedIntoRectangle(imgData, new com.itextpdf.kernel.geom.Rectangle(0, 0, iw, ih), false);
                                imgDoc.close();
                            } else {
                                addedPdf = vasuki.istanpdf.util.ContentFiles.copyUriToCache(MainActivity.this, addedUri, ".pdf");
                            }
                            File mergedFile = new File(getCacheDir(), "merged_" + System.currentTimeMillis() + ".pdf");
                            Uri reorderSource = editorViewModel.getReorderSource();
                            java.io.File srcFile = vasuki.istanpdf.util.ContentFiles.copyUriToCache(MainActivity.this, reorderSource, ".pdf");
                            try {
                                com.itextpdf.kernel.pdf.PdfDocument srcDoc = new com.itextpdf.kernel.pdf.PdfDocument(
                                        new com.itextpdf.kernel.pdf.PdfReader(srcFile));
                                com.itextpdf.kernel.pdf.PdfDocument addDoc = new com.itextpdf.kernel.pdf.PdfDocument(
                                        new com.itextpdf.kernel.pdf.PdfReader(addedPdf));
                                com.itextpdf.kernel.pdf.PdfDocument destDoc = new com.itextpdf.kernel.pdf.PdfDocument(
                                        new com.itextpdf.kernel.pdf.PdfWriter(new java.io.FileOutputStream(mergedFile)));
                                com.itextpdf.kernel.utils.PdfMerger pdfMerger = new com.itextpdf.kernel.utils.PdfMerger(destDoc);
                                pdfMerger.merge(srcDoc, 1, srcDoc.getNumberOfPages());
                                pdfMerger.merge(addDoc, 1, addDoc.getNumberOfPages());
                                addDoc.close();
                                srcDoc.close();
                                destDoc.close();
                            } finally {
                                if (srcFile.exists()) srcFile.delete();
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
                            editorViewModel.setReorderSource(mergedUri);
                        } finally {
                            if (addedPdf != null && addedPdf.exists()
                                    && addedPdf.getAbsolutePath().startsWith(getCacheDir().getAbsolutePath())) {
                                addedPdf.delete();
                            }
                        }
                    }
                    runOnUiThread(() -> {
                        pages.addAll(newPages);
                        editorViewModel.setPagesAdded(true);
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
            editorViewModel.clearPendingUris();
            editorViewModel.getPendingUris().add(pdfUri);
            String prefix = getDisplayName(pdfUri);
            createDocument("application/zip", prefix + "_images.zip", REQ_SAVE_PDF_TO_JPG);
            return;
        }

        Uri destination = data.getData();
        List<PageItem> pages = editorViewModel.getPages();
        List<Uri> pendingUris = editorViewModel.getPendingUris();

        if (requestCode == REQ_SAVE_MERGE_PDF) {
            List<Integer> rotations = new ArrayList<>();
            for (PageItem p : pages) rotations.add(p.rotation);
            runJob("Merging PDFs...", () -> AppModule.get().mergePdf().execute(new ArrayList<>(pendingUris), rotations, destination));
        } else if (requestCode == REQ_SAVE_IMAGES_TO_PDF) {
            List<PageItem> snapshot = new ArrayList<>(pages);
            runJob("Converting Images...", () -> AppModule.get().imagesToPdf().execute(new ArrayList<>(pendingUris), snapshot, destination));
        } else if (requestCode == REQ_SAVE_PDF_TO_JPG) {
            runJob("Converting to JPGs...", () -> AppModule.get().pdfToJpeg().execute(pendingUris.get(0), destination));

        } else if (requestCode == REQ_SAVE_REORDER_PDF || requestCode == REQ_SAVE_REORDER_DOCX_EXPORT) {
            Uri source = editorViewModel.getReorderSource();
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
                    AppModule.get().saveDocx().execute(source, snapshot, destination);
                } else {
                    AppModule.get().reorderPdf().execute(source, snapshot, destination);
                }
            });
        }
    }

    private void buildHome() {
        isHome = true;
        editorViewModel.resetForHome();

        HomeViewBuilder builder = new HomeViewBuilder(this, regularFont, boldFont);
        View homeView = builder.build(new HomeViewBuilder.HomeActions() {
            @Override public void onMergePdf() { editorViewModel.clearPendingUris(); pickMany(new String[]{MIME_PDF}, REQ_PICK_MERGE_PDF); }
            @Override public void onModifyPdf() { pickOne(new String[]{MIME_PDF}, REQ_PICK_REORDER_PDF); }
            @Override public void onImageToPdf() { editorViewModel.clearPendingImageUris(); pickMany(new String[]{"image/jpeg","image/png","image/webp","image/bmp"}, REQ_PICK_IMAGES_TO_PDF); }
            @Override public void onPdfToImage() { pickOne(new String[]{MIME_PDF}, REQ_PICK_PDF_TO_JPG); }
            @Override public void onDocxRemovePages() { pickOne(new String[]{MIME_DOCX}, REQ_PICK_REORDER_DOCX); }
            @Override public void onDocxReorderPages() { pickOne(new String[]{MIME_DOCX}, REQ_PICK_REORDER_DOCX_EXPORT); }
            @Override public void onSupportDeveloper() { showDonationPicker(null); }
            @Override public void onOpenSettings() { startActivity(new android.content.Intent(MainActivity.this, SettingsActivity.class)); }
        });
        status = builder.getStatus();
        statusIndicator = builder.getStatusIndicator();
        setViewWithLoading(homeView);
    }

    private void buildPageEditor(String titleText, String saveLabelText, boolean docxExport, boolean allowReorder) {
        isHome = false;
        editorViewModel.setPagesAdded(false);

        EditorViewBuilder builder = new EditorViewBuilder(this, regularFont, boldFont);
        View editorView = builder.build(titleText, saveLabelText, docxExport, allowReorder, new EditorViewBuilder.EditorActions() {
            @Override public void onBack() { buildHome(); }
            @Override public void onSave(String title, boolean isDocx) { handleSave(title, isDocx); }
            @Override public void onAddItems(String title) { handleAddItems(title); }
            @Override public void onShowCustomDialog(String title, View content, String negStr, Runnable negAction, String posStr, Runnable posAction) {
                showCustomDialog(title, content, negStr, negAction, posStr, posAction);
            }
            @Override public void toast(String msg) { MainActivity.this.toast(msg); }
            @Override public List<PageItem> getPages() { return editorViewModel.getPages(); }
            @Override public List<Uri> getPendingUris() { return editorViewModel.getPendingUris(); }
            @Override public boolean isPagesAdded() { return editorViewModel.isPagesAdded(); }
        });
        status = builder.getStatus();
        statusIndicator = builder.getStatusIndicator();
        pageList = builder.getPageList();
        setViewWithLoading(editorView);
    }

    private void handleSave(String titleText, boolean docxExport) {
        List<PageItem> pages = editorViewModel.getPages();
        List<Uri> pendingUris = editorViewModel.getPendingUris();

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
            List<Uri> keptUris = new ArrayList<>();
            for (int i = 0; i < pages.size(); i++) {
                if (pages.get(i).keep && i < pendingUris.size()) {
                    keptUris.add(pendingUris.get(i));
                }
            }
            if (keptUris.size() < 2) {
                toast("Please keep at least 2 PDFs to merge.");
                return;
            }
            pendingUris.clear();
            pendingUris.addAll(keptUris);
            String mergePrefix = getDisplayName(keptUris.get(0));
            createDocument(MIME_PDF, mergePrefix + "_merged.pdf", REQ_SAVE_MERGE_PDF);
            return;
        }

        boolean pagesAdded = editorViewModel.isPagesAdded();

        if ("Images to PDF".equals(titleText)) {
            
        } else if (!removed && !reordered && !rotated && !pagesAdded) {
            if ("Reorder Pages from DOCX".equals(titleText)) {
                TextView msg = text("No pages were modified. Do you still want to save the document as a PDF?", 14, R.color.istan_text_muted, false);
                msg.setPadding(0, dp(8), 0, dp(8));
                showCustomDialog("Save without changes?", msg, "Cancel", null, "Save as PDF", () -> {
                    String prefix = editorViewModel.getOriginalFileName() != null ? editorViewModel.getOriginalFileName() : getDisplayName(editorViewModel.getReorderSource());
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

        String prefix = editorViewModel.getOriginalFileName() != null ? editorViewModel.getOriginalFileName() : getDisplayName(editorViewModel.getReorderSource());
        if (docxExport) {
            createDocument(MIME_DOCX, prefix + suffix + ".docx", REQ_SAVE_REORDER_DOCX_EXPORT);
        } else if ("Images to PDF".equals(titleText)) {
            String imgPrefix = getDisplayName(pendingUris.isEmpty() ? null : pendingUris.get(0));
            createDocument(MIME_PDF, imgPrefix + "_converted.pdf", REQ_SAVE_IMAGES_TO_PDF);
        } else {
            createDocument(MIME_PDF, prefix + suffix + ".pdf", REQ_SAVE_REORDER_PDF);
        }
    }

    private void handleAddItems(String titleText) {
        if ("Reorder Pages from DOCX".equals(titleText)) {
            pickMany(new String[]{"image/jpeg", "image/png", "image/webp", "image/bmp", MIME_DOCX, MIME_PDF}, REQ_PICK_DOCX_ADD);
        } else if (titleText.equals("Remove/Reorder PDF")) {
            pickMany(new String[]{"image/jpeg", "image/png", "image/webp", "image/bmp", MIME_PDF}, REQ_PICK_PDF_ADD);
        } else if ("Merge PDF".equals(titleText)) {
            pickMany(new String[]{MIME_PDF}, REQ_PICK_MERGE_PDF_ADD);
        } else {
            pickMany(new String[]{"image/jpeg", "image/png", "image/webp", "image/bmp", "application/pdf"}, REQ_PICK_IMAGES_TO_PDF_ADD);
        }
    }

    private void loadPdfPreview(Uri uri, boolean docxExport) {
        setBusy(true, "Rendering page previews...", true);
        worker.execute(() -> {
            try {
                List<PageItem> rendered = renderPdfPages(uri);
                runOnUiThread(() -> {
                    editorViewModel.clearPages();
                    editorViewModel.getPages().addAll(rendered);
                    editorViewModel.setReorderSource(uri);
                    editorViewModel.setOriginalFileName(null);
                    setBusy(false, "Ready");
                    buildPageEditor("Remove/Reorder PDF", "Save PDF", docxExport, true);
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
                List<PageItem> pages = editorViewModel.getPages();
                List<Uri> pendingUris = editorViewModel.getPendingUris();
                List<File> tempImageFiles = editorViewModel.getTempImageFiles();
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
                    if (clearExisting) editorViewModel.clearPages();
                    editorViewModel.getPages().addAll(rendered);
                    pendingUris.addAll(processedUris);
                    editorViewModel.setReorderSource(null);
                    editorViewModel.setOriginalFileName(null);
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
                File pdfFile = AppModule.get().docxToPdf().execute(docx);
                List<PageItem> rendered = renderPdfPages(Uri.fromFile(pdfFile));

                runOnUiThread(() -> {
                    editorViewModel.clearPages();
                    editorViewModel.getPages().addAll(rendered);
                    editorViewModel.setReorderSource(docx);
                    editorViewModel.setOriginalFileName(null);
                    setBusy(false, "Ready");
                    buildPageEditor("Remove Pages from DOCX", "Save DOCX", true, false);
                });
            } catch (Exception exception) {
                showError(exception);
            }
        });
    }

    private Bitmap renderFirstPdfPage(Uri uri) throws Exception {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int targetWidth = Math.max(dp(280), screenWidth - dp(64));
        return AppModule.get().pdfEngine().renderFirstPage(uri, targetWidth);
    }

    private List<PageItem> renderPdfPages(Uri uri) throws Exception {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int targetWidth = Math.max(dp(280), screenWidth - dp(64));
        return AppModule.get().pdfEngine().renderAllPages(uri, targetWidth, cancelJob, (current, total) -> {
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
        });
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

    public android.app.Dialog showCustomDialog(String titleStr, View content, String negativeStr, Runnable negativeAction, String positiveStr, Runnable positiveAction) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        dialog.setCancelable(false);

        LinearLayout dialogRoot = new LinearLayout(this);
        dialogRoot.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(color(R.color.istan_surface));
        gd.setCornerRadius(dp(28));
        gd.setStroke(dp(1), color(R.color.istan_outline));
        dialogRoot.setBackground(gd);
        dialogRoot.setPadding(dp(24), dp(24), dp(24), dp(24));

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

        if (negativeStr != null) {
            TextView cancel = text(negativeStr, 14, R.color.istan_text_muted, true);
            cancel.setPadding(dp(16), dp(8), dp(16), dp(8));
            cancel.setOnClickListener(v -> {
                dialog.dismiss();
                if (negativeAction != null) negativeAction.run();
            });
            btnRow.addView(cancel);
        }

        if (positiveStr != null) {
            TextView positive = text(positiveStr, 14, R.color.istan_olive, true);
            positive.setPadding(dp(16), dp(8), dp(16), dp(8));
            positive.setOnClickListener(v -> {
                dialog.dismiss();
                if (positiveAction != null) positiveAction.run();
            });
            btnRow.addView(positive);
        }

        dialogRoot.addView(btnRow);
        dialog.setContentView(dialogRoot);
        dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels * 0.85), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.show();
        return dialog;
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
        panelBg.setStroke(dp(1), color(R.color.istan_outline));
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
        cancelBtn.setRadius(dp(100));
        cancelBtn.setStrokeWidth(dp(1));
        cancelBtn.setStrokeColor(color(R.color.istan_outline));
        cancelBtn.setCardElevation(0);
        cancelBtn.setUseCompatPadding(false);

        TextView cancelText = text("Cancel", 15, R.color.istan_text_muted, false);
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
        return ThemePrefs.resolveColor(this, colorRes);
    }

    private void applySystemBarTheme() {
        androidx.core.view.WindowInsetsControllerCompat controller =
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        boolean lightBars = !ThemePrefs.isAmoled(this);
        controller.setAppearanceLightStatusBars(lightBars);
        controller.setAppearanceLightNavigationBars(lightBars);
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
        return AppModule.get().documentManager().getDisplayName(uri);
    }

    private interface Job {
        void run() throws Exception;
    }

    private void showDonationPicker(Runnable onComplete) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(12), 0, dp(4));

        android.app.Dialog[] dialogRef = new android.app.Dialog[1];

        Runnable clickKofi = () -> {
            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/ramakanthgacharya")));
            if (dialogRef[0] != null) dialogRef[0].dismiss();
            if (onComplete != null) onComplete.run();
        };

        Runnable clickUpi = () -> {
            try {
                startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("upi://pay?pa=ramakanthacharya@slc&pn=Ramakanth")));
            } catch (Exception e) {
                android.widget.Toast.makeText(MainActivity.this, "No UPI app found", android.widget.Toast.LENGTH_SHORT).show();
            }
            if (dialogRef[0] != null) dialogRef[0].dismiss();
            if (onComplete != null) onComplete.run();
        };

        com.google.android.material.card.MaterialCardView card1 = new com.google.android.material.card.MaterialCardView(this);
        card1.setCardBackgroundColor(Color.TRANSPARENT);
        card1.setRadius(dp(16));
        card1.setStrokeWidth(dp(1));
        card1.setStrokeColor(color(R.color.istan_outline));
        card1.setCardElevation(0);
        card1.setOnClickListener(v -> clickKofi.run());

          TextView opt1 = text("Ko-fi (Global)", 15, R.color.istan_text, false);
        opt1.setPadding(dp(20), dp(16), dp(20), dp(16));
        card1.addView(opt1);

        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp1.setMargins(0, 0, 0, dp(12));
        content.addView(card1, lp1);

        com.google.android.material.card.MaterialCardView card2 = new com.google.android.material.card.MaterialCardView(this);
        card2.setCardBackgroundColor(Color.TRANSPARENT);
        card2.setRadius(dp(16));
        card2.setStrokeWidth(dp(1));
        card2.setStrokeColor(color(R.color.istan_outline));
        card2.setCardElevation(0);
        card2.setOnClickListener(v -> clickUpi.run());

          TextView opt2 = text("UPI (India)", 15, R.color.istan_text, false);
        opt2.setPadding(dp(20), dp(16), dp(20), dp(16));
        card2.addView(opt2);

        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        content.addView(card2, lp2);

        dialogRef[0] = showCustomDialog("Support the Developer", content, "Cancel", onComplete, null, null);
    }

    private void pruneStaleCacheFiles() {
        AppModule.get().cacheManager().pruneStaleCacheFiles();
    }
}
