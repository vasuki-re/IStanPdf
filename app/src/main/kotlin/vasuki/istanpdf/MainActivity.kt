package vasuki.istanpdf

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import vasuki.istanpdf.di.AppModule
import vasuki.istanpdf.model.PageItem
import vasuki.istanpdf.presentation.CropOverlayView
import vasuki.istanpdf.presentation.EditorViewBuilder
import vasuki.istanpdf.presentation.EditorViewModel
import vasuki.istanpdf.presentation.HomeViewBuilder
import vasuki.istanpdf.util.BitmapUtils
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    val cancelJob = AtomicBoolean(false)

    private var pageList: RecyclerView? = null
    private var loadingOverlay: FrameLayout? = null
    private var loadingMessage: TextView? = null
    private var loadingSubtitle: TextView? = null
    private var loadingSpinner: com.google.android.material.progressindicator.CircularProgressIndicator? = null
    private val fakeProgressHandler = Handler(Looper.getMainLooper())
    private var fakeProgressRunnable: Runnable? = null
    private var dismissOverlayRunnable: Runnable? = null
    private var status: TextView? = null
    private var statusIndicator: ImageView? = null
    private lateinit var regularFont: Typeface
    private lateinit var boldFont: Typeface
    private var isHome = true
    private var activeReq = 0
    private var themeToken: String? = null
    private lateinit var docLauncher: ActivityResultLauncher<Intent>
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    private var cameraPhotoFile: File? = null
    private var cropDialog: android.app.Dialog? = null
    private var cropView: CropOverlayView? = null
    private var cropBitmap: Bitmap? = null
    private var currentEditorTitle: String? = null
    private var updateThread: Thread? = null
    private var cameraMergeToPdf = false

    private var compressMode = COMPRESS_MODE_RESOLUTION
    private var compressDpi = 150
    private var compressQuality = 70
    private var compressTargetBytes = 0L

    private lateinit var editorViewModel: EditorViewModel

        
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        editorViewModel = ViewModelProvider(this).get(EditorViewModel::class.java)

        docLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult(), ::onDocResult)
        cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult(), ::onCameraResult)
        cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCameraForCapture()
            else toast("Camera permission is required to take photos")
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (loadingOverlay != null && loadingOverlay!!.visibility == View.VISIBLE) {
                    return
                }
                if (!isHome) {
                    buildHome()
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = Color.TRANSPARENT
        window.statusBarColor = Color.TRANSPARENT
        applySystemBarTheme()

        regularFont = Typeface.createFromAsset(assets, "vasuki.ttf")
        boldFont = Typeface.createFromAsset(assets, "vasuki_bold.ttf")
        pruneStaleCacheFiles()
        themeToken = ThemePrefs.token(this)
        ThemePrefs.ensureLauncherIcon(this)
        buildHome()
        
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        checkForUpdates()
        showSponsorDialogOnStartup()

        if (prefs.getBoolean("improve_docx_perf", false)) {
            AppModule.get().docxEngine.preLoad()
        }
    }

    private fun showSponsorDialogOnStartup() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("never_show_sponsor", false)) {
            return
        }

        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.window!!.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(false)

        val dialogRoot = LinearLayout(this)
        dialogRoot.orientation = LinearLayout.VERTICAL
        val gd = android.graphics.drawable.GradientDrawable()
        gd.setColor(color(R.color.istan_surface))
        gd.cornerRadius = dp(28).toFloat()
        gd.setStroke(dp(1), color(R.color.istan_outline))
        dialogRoot.background = gd
        dialogRoot.setPadding(dp(24), dp(24), dp(24), dp(24))

        val title = text("Sponsor this project", 22, R.color.istan_text, true)
        title.gravity = Gravity.CENTER
        title.setPadding(0, 0, 0, dp(16))
        dialogRoot.addView(title)

        val body = text("This project is completely community-driven and relies on donations to keep development alive. If you find this app useful, please consider sponsoring!", 14, R.color.istan_text_muted, false)
        body.gravity = Gravity.CENTER
        body.setLineSpacing(0f, 1.2f)
        body.setPadding(0, 0, 0, dp(24))
        dialogRoot.addView(body)

        val checkRow = LinearLayout(this)
        checkRow.orientation = LinearLayout.HORIZONTAL
        checkRow.gravity = Gravity.CENTER_VERTICAL
        checkRow.setPadding(dp(8), 0, 0, dp(24))

        val checkBox = android.widget.CheckBox(this)
        checkBox.buttonTintList = android.content.res.ColorStateList.valueOf(color(R.color.istan_text))
        checkRow.addView(checkBox)

        val checkText = text("Never show again", 14, R.color.istan_text, false)
        checkText.setPadding(dp(8), 0, 0, 0)
        checkText.setOnClickListener { checkBox.isChecked = !checkBox.isChecked }
        checkRow.addView(checkText)

        dialogRoot.addView(checkRow)

        val btnSponsor = text("💖 Sponsor on GitHub", 16, android.R.color.white, true)
        btnSponsor.gravity = Gravity.CENTER
        val btnGd = android.graphics.drawable.GradientDrawable()
        btnGd.setColor(color(R.color.istan_olive))
        btnGd.cornerRadius = dp(100).toFloat()
        btnSponsor.background = btnGd
        btnSponsor.setPadding(0, dp(16), 0, dp(16))
        val btnParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        btnParams.bottomMargin = dp(12)
        btnSponsor.layoutParams = btnParams
        btnSponsor.setOnClickListener {
            if (checkBox.isChecked) {
                prefs.edit().putBoolean("never_show_sponsor", true).apply()
            }
            dialog.dismiss()
            showDonationPicker(null)
        }
        dialogRoot.addView(btnSponsor)

        val btnNotNow = text("Not now", 14, R.color.istan_text, true)
        btnNotNow.gravity = Gravity.CENTER
        btnNotNow.setPadding(0, dp(12), 0, dp(12))
        btnNotNow.setOnClickListener {
            if (checkBox.isChecked) {
                prefs.edit().putBoolean("never_show_sponsor", true).apply()
            }
            dialog.dismiss()
        }
        dialogRoot.addView(btnNotNow)

        dialog.setContentView(dialogRoot)
        dialog.show()

        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun checkForUpdates() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("check_updates", true)) return

        updateThread = Thread {
            try {
                val url = java.net.URL("https://cdn.jsdelivr.net/gh/vasuki-re/IStanPdf@Mitsuba/changelog.txt")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val reader = java.io.BufferedReader(java.io.InputStreamReader(conn.inputStream))
                var line: String?
                var versionname = ""
                var remoteVersionCode = 0L
                var link = ""
                val changelog = StringBuilder()
                var parsingChangelog = false

                while (reader.readLine().also { line = it } != null) {
                    val l = line!!
                    if (parsingChangelog) {
                        changelog.append(l).append("\n")
                    } else if (l.startsWith("versionname:")) {
                        versionname = l.substring("versionname:".length).trim()
                    } else if (l.startsWith("versioncode:")) {
                        try { remoteVersionCode = l.substring("versioncode:".length).trim().toLong() } catch (_: Exception) {}
                    } else if (l.startsWith("link:")) {
                        link = l.substring("link:".length).trim()
                    } else if (l.startsWith("changelog:")) {
                        parsingChangelog = true
                    }
                }
                reader.close()

                var currentVersionCode = 0L
                try {
                    currentVersionCode = packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
                } catch (_: Exception) {}

                if (remoteVersionCode > currentVersionCode) {
                    val finalVersionName = versionname
                    val finalLink = link
                    val finalChangelog = changelog.toString().trim()

                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread

                        val changelogView = createChangelogView(finalChangelog)

                        showCustomDialog("Update Available: $finalVersionName", changelogView, "Later", null, "Download") {
                            var architecture = "arm"
                            for (abi in android.os.Build.SUPPORTED_ABIS) {
                                if (abi.contains("arm64")) {
                                    architecture = "arm64"
                                    break
                                }
                            }
                            val downloadUrl = finalLink.replace("*", architecture)
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        updateThread!!.start()
    }

    private fun parseMarkdownText(text: String): CharSequence {
        val builder = android.text.SpannableStringBuilder()
        var currentIndex = 0
        while (currentIndex < text.length) {
            val startBold = text.indexOf("**", currentIndex)
            if (startBold != -1) {
                val endBold = text.indexOf("**", startBold + 2)
                if (endBold != -1) {
                    builder.append(text.substring(currentIndex, startBold))
                    val startSpan = builder.length
                    builder.append(text.substring(startBold + 2, endBold))
                    builder.setSpan(CustomTypefaceSpan(boldFont), startSpan, builder.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.setSpan(android.text.style.ForegroundColorSpan(color(R.color.istan_text)), startSpan, builder.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    currentIndex = endBold + 2
                    continue
                }
            }
            builder.append(text.substring(currentIndex))
            break
        }
        return builder
    }

    private fun createChangelogView(changelogText: String): View {
        val scrollView = object : ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val maxHeight = (resources.displayMetrics.heightPixels * 0.70).toInt()
                val heightSize = MeasureSpec.getSize(heightMeasureSpec)
                val adjusted = if (maxHeight > 0 && (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED || heightSize > maxHeight)) {
                    MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
                } else {
                    heightMeasureSpec
                }
                super.onMeasure(widthMeasureSpec, adjusted)
            }
        }
        scrollView.isVerticalScrollBarEnabled = false
        scrollView.setFadingEdgeLength(dp(16))
        scrollView.isVerticalFadingEdgeEnabled = true

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(0, dp(4), 0, dp(12))

        val lines = changelogText.split("\n")
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                val space = View(this)
                space.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4))
                container.addView(space)
                continue
            }

            if (line.startsWith("- ")) {
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.setPadding(0, 0, 0, dp(4))

                val bullet = text("•", 15, R.color.istan_olive, true)
                bullet.setPadding(dp(8), dp(1), dp(8), 0)

                val bodyText = text("", 15, R.color.istan_text_muted, false)
                bodyText.setLineSpacing(dp(2).toFloat(), 1.0f)
                bodyText.text = parseMarkdownText(line.substring(2))

                row.addView(bullet)
                row.addView(bodyText)
                container.addView(row)
            } else if (line.startsWith("[") && line.endsWith("]")) {
                val header = text(line.substring(1, line.length - 1), 17, R.color.istan_olive, true)
                header.setPadding(dp(4), dp(8), 0, dp(4))
                container.addView(header)
            } else {
                val bodyText = text("", 15, R.color.istan_text_muted, false)
                bodyText.setLineSpacing(dp(2).toFloat(), 1.0f)
                bodyText.setPadding(dp(8), 0, dp(8), dp(4))
                bodyText.text = parseMarkdownText(line)
                container.addView(bodyText)
            }
        }

        scrollView.addView(container)

        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.weight = 1.0f
        scrollView.layoutParams = params

        return scrollView
    }

    override fun onResume() {
        super.onResume()
        val latestThemeToken = ThemePrefs.token(this)
        if (themeToken != null && themeToken != latestThemeToken) {
            themeToken = latestThemeToken
            applySystemBarTheme()
            if (isHome) {
                buildHome()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (ThemePrefs.themeMode(this) == ThemePrefs.THEME_AUTO) {
            val latestThemeToken = ThemePrefs.token(this)
            if (latestThemeToken != themeToken) {
                themeToken = latestThemeToken
                applySystemBarTheme()
                if (isHome) {
                    buildHome()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("activeReq", activeReq)
        cameraPhotoFile?.let { outState.putString("cameraPhotoFile", it.absolutePath) }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        activeReq = savedInstanceState.getInt("activeReq", 0)
        val path = savedInstanceState.getString("cameraPhotoFile")
        if (path != null) cameraPhotoFile = File(path)
    }

    override fun onDestroy() {
        fakeProgressHandler.removeCallbacksAndMessages(null)
        updateThread?.interrupt()
        updateThread = null
        worker.shutdownNow()
        editorViewModel.clearPages()
        super.onDestroy()
    }

    private fun clearPages() {
        editorViewModel.clearPages()
    }

    private fun onDocResult(result: ActivityResult) {
        val data = result.data
        val requestCode = activeReq
        if (result.resultCode != Activity.RESULT_OK || data == null) {
            if (requestCode == REQ_PICK_MERGE_PDF) {
                editorViewModel.clearPendingUris()
            } else if (requestCode == REQ_PICK_IMAGES_TO_PDF) {
                editorViewModel.clearPendingImageUris()
            }
            setBusy(false, "Ready")
            return
        }

        if (requestCode == REQ_PICK_MERGE_PDF) {
            val selected = readSelectedUris(data)
            val pendingUris = editorViewModel.pendingUris
            var dupes = 0
            var emptyFiles = 0
            for (u in selected) {
                var exists = false
                for (existing in pendingUris) {
                    if (existing.toString() == u.toString()) { exists = true; break }
                }
                if (exists) { dupes++; continue }
                try {
                    val cursor = contentResolver.query(u, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
                    if (cursor != null) {
                        if (cursor.moveToFirst()) {
                            val fileSize = cursor.getLong(0)
                            if (fileSize == 0L) { cursor.close(); emptyFiles++; continue }
                        }
                        cursor.close()
                    }
                } catch (_: Exception) { emptyFiles++; continue }
                pendingUris.add(u)
            }
            if (pendingUris.isEmpty()) {
                if (emptyFiles > 0) {
                    Toast.makeText(this, "Empty PDF skipped", Toast.LENGTH_SHORT).show()
                }
                return
            }
            if (emptyFiles > 0) {
                status?.text = "${emptyFiles}Empty PDF skipped."
            }

            setStatusIndicatorColor(color(R.color.istan_olive))
            status?.text = "Loading PDFs..."
            worker.execute {
                val rendered = mutableListOf<PageItem>()
                var startIndex = 0
                var skipped = 0
                for (uri in pendingUris) {
                    try {
                        val thumb = renderFirstPdfPage(uri)
                        if (thumb != null) {
                            rendered.add(PageItem(startIndex++, thumb, getDisplayName(uri)))
                        }
                    } catch (_: Exception) { skipped++ }
                }
                runOnUiThread {
                    editorViewModel.clearPages()
                    val pages = editorViewModel.pages
                    pages.addAll(rendered)
                    for (p in pages) p.keep = true
                    buildPageEditor("Merge PDF", "Merge PDF", false, true)
                    if (skipped > 0) Toast.makeText(this@MainActivity, "$skipped PDF(s) could not be loaded", Toast.LENGTH_SHORT).show()
                    status?.text = "Ready"
                }
            }
            return
        }

        if (requestCode == REQ_PICK_REORDER_PDF) {
            editorViewModel.reorderSource = data.data
            loadPdfPreview(editorViewModel.reorderSource ?: return, false)
            return
        }

        if (requestCode == REQ_PICK_REORDER_DOCX) {
            val docx = data.data ?: return
            loadDocxPreviewViaLibreOffice(docx)
            return
        }

        if (requestCode == REQ_PICK_REORDER_DOCX_EXPORT) {
            val docx = data.data ?: return
            editorViewModel.originalFileName = getDisplayName(docx)
            setBusy(true, "Converting DOCX to PDF for preview...")
            worker.execute {
                try {
                    val pdfFile = AppModule.get().docxToPdf.execute(docx)
                    val rendered = renderPdfPages(Uri.fromFile(pdfFile))
                    runOnUiThread {
                        editorViewModel.clearPages()
                        editorViewModel.pages.addAll(rendered)
                        editorViewModel.reorderSource = Uri.fromFile(pdfFile)
                        setBusy(false, "Ready")
                        buildPageEditor("Reorder Pages from DOCX", "Save PDF", false, true)
                    }
                } catch (exception: Exception) {
                    showError(exception)
                }
            }
            return
        }

        if (requestCode == REQ_PICK_IMAGES_TO_PDF) {
            val rawUris = readSelectedUris(data)
            if (rawUris.isEmpty()) return
            editorViewModel.clearPendingImageUris()
            loadImagePreview(rawUris, true)
            return
        }

        if (requestCode == REQ_PICK_MERGE_PDF_ADD) {
            val rawUris = readSelectedUris(data)
            if (rawUris.isEmpty()) return

            val pendingUris = editorViewModel.pendingUris
            val pages = editorViewModel.pages
            val validUris = mutableListOf<Uri>()
            var emptyFiles = 0
            for (u in rawUris) {
                try {
                    val cursor = contentResolver.query(u, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
                    if (cursor != null) {
                        if (cursor.moveToFirst()) {
                            val fileSize = cursor.getLong(0)
                            if (fileSize == 0L) { cursor.close(); emptyFiles++; continue }
                        }
                        cursor.close()
                    }
                } catch (_: Exception) { emptyFiles++; continue }
                validUris.add(u)
            }
            if (emptyFiles > 0) {
                status?.text = "$emptyFiles Empty PDF skipped."
            }
            if (validUris.isEmpty()) return

            setStatusIndicatorColor(color(R.color.istan_olive))
            status?.text = "Loading PDFs..."
            worker.execute {
                val rendered = mutableListOf<PageItem>()
                var startIndex = pages.size
                var skipped = 0
                for (uri in validUris) {
                    try {
                        val thumb = renderFirstPdfPage(uri)
                        if (thumb != null) {
                            rendered.add(PageItem(startIndex++, thumb, getDisplayName(uri)))
                        }
                    } catch (_: Exception) { skipped++ }
                }
                runOnUiThread {
                    for (p in rendered) p.keep = true
                    pendingUris.addAll(validUris)
                    pages.addAll(rendered)
                    editorViewModel.pagesAdded = true
                    pageList?.adapter?.notifyDataSetChanged()
                    if (skipped > 0) Toast.makeText(this@MainActivity, "$skipped PDF(s) could not be loaded", Toast.LENGTH_SHORT).show()
                    status?.text = "Ready"
                }
            }
            return
        }

        if (requestCode == REQ_PICK_IMAGES_TO_PDF_ADD) {
            val rawUris = readSelectedUris(data)
            if (rawUris.isEmpty()) return
            loadImagePreview(rawUris, false)
            return
        }

        if (requestCode == REQ_PICK_DOCX_ADD || requestCode == REQ_PICK_PDF_ADD) {
            val rawUris = readSelectedUris(data)
            if (rawUris.isEmpty()) return
            setBusy(true, "Rendering previews...", true)
            worker.execute {
                try {
                    val pages = editorViewModel.pages
                    val newPages = mutableListOf<PageItem>()
                    for (addedUri in rawUris) {
                        val mime = contentResolver.getType(addedUri)
                        var addedPdf: File? = null
                        try {
                            addedPdf = when {
                                mime != null && mime == MIME_DOCX -> AppModule.get().docxToPdf.execute(addedUri)
                                mime != null && mime.startsWith("image/") -> {
                                    val size = dp(280)
                                    val thumb = loadThumbnail(addedUri, size)
                                    val imgPdf = File(cacheDir, "img_${System.currentTimeMillis()}.pdf")
                                    val iw = thumb.width.toFloat()
                                    val ih = thumb.height.toFloat()
                                    val baos = java.io.ByteArrayOutputStream()
                                    thumb.compress(Bitmap.CompressFormat.JPEG, 95, baos)
                                    val imgData = com.itextpdf.io.image.ImageDataFactory.createJpeg(baos.toByteArray())
                                    val imgDoc = com.itextpdf.kernel.pdf.PdfDocument(
                                        com.itextpdf.kernel.pdf.PdfWriter(java.io.FileOutputStream(imgPdf)))
                                    val ps = com.itextpdf.kernel.geom.PageSize(iw, ih)
                                    val imgPage = imgDoc.addNewPage(ps)
                                    val pdfCanvas = com.itextpdf.kernel.pdf.canvas.PdfCanvas(imgPage)
                                    pdfCanvas.addImageFittedIntoRectangle(imgData, com.itextpdf.kernel.geom.Rectangle(0f, 0f, iw, ih), false)
                                    imgDoc.close()
                                    imgPdf
                                }
                                else -> vasuki.istanpdf.util.ContentFiles.copyUriToCache(this@MainActivity, addedUri, ".pdf")
                            }
                            val mergedFile = File(cacheDir, "merged_${System.currentTimeMillis()}.pdf")
                            val reorderSource = editorViewModel.reorderSource!!
                            val srcFile = vasuki.istanpdf.util.ContentFiles.copyUriToCache(this@MainActivity, reorderSource, ".pdf")
                            try {
                                val srcDoc = com.itextpdf.kernel.pdf.PdfDocument(com.itextpdf.kernel.pdf.PdfReader(srcFile))
                                val addDoc = com.itextpdf.kernel.pdf.PdfDocument(com.itextpdf.kernel.pdf.PdfReader(addedPdf))
                                val destDoc = com.itextpdf.kernel.pdf.PdfDocument(
                                    com.itextpdf.kernel.pdf.PdfWriter(java.io.FileOutputStream(mergedFile)))
                                val pdfMerger = com.itextpdf.kernel.utils.PdfMerger(destDoc)
                                pdfMerger.merge(srcDoc, 1, srcDoc.numberOfPages)
                                pdfMerger.merge(addDoc, 1, addDoc.numberOfPages)
                                addDoc.close()
                                srcDoc.close()
                                destDoc.close()
                            } finally {
                                if (srcFile.exists()) srcFile.delete()
                            }
                            val mergedUri = Uri.fromFile(mergedFile)
                            val allRendered = renderPdfPages(mergedUri)
                            val existingCount = pages.size + newPages.size
                            for (k in existingCount until allRendered.size) {
                                newPages.add(allRendered[k])
                            }

                            if ("file" == reorderSource.scheme) {
                                val oldFile = File(reorderSource.path!!)
                                if (oldFile.exists() && oldFile.absolutePath.startsWith(cacheDir.absolutePath)) {
                                    oldFile.delete()
                                }
                            }
                            editorViewModel.reorderSource = mergedUri
                        } finally {
                            if (addedPdf != null && addedPdf.exists()
                                && addedPdf.absolutePath.startsWith(cacheDir.absolutePath)) {
                                addedPdf.delete()
                            }
                        }
                    }
                    runOnUiThread {
                        pages.addAll(newPages)
                        editorViewModel.pagesAdded = true
                        pageList?.adapter?.notifyDataSetChanged()
                        setBusy(false, "Ready")
                    }
                } catch (exception: Exception) {
                    showError(exception)
                }
            }
            return
        }

        if (requestCode == REQ_PICK_PDF_TO_JPG) {
            val pdfUri = data.data ?: return
            try {
                val cursor = contentResolver.query(pdfUri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        val fileSize = cursor.getLong(0)
                        if (fileSize == 0L) {
                            cursor.close()
                            toast("Error: PDF file is empty (0 bytes)")
                            return
                        }
                    }
                    cursor.close()
                }
            } catch (_: Exception) {}
            editorViewModel.clearPendingUris()
            editorViewModel.pendingUris.add(pdfUri)
            val prefix = getDisplayName(pdfUri)
            createDocument(MIME_ZIP, "${prefix}_images.zip", REQ_SAVE_PDF_TO_JPG)
            return
        }

        if (requestCode == REQ_PICK_DOCX_TO_PDF) {
            val docxUri = data.data ?: return
            editorViewModel.clearPendingUris()
            editorViewModel.pendingUris.add(docxUri)
            val prefix = getDisplayName(docxUri)
            createDocument(MIME_PDF, "${prefix}_converted.pdf", REQ_SAVE_DOCX_TO_PDF)
            return
        }

        if (requestCode == REQ_PICK_MD_TO_PDF) {
            val mdUri = data.data ?: return
            try {
                val cursor = contentResolver.query(mdUri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        val fileSize = cursor.getLong(0)
                        if (fileSize == 0L) {
                            cursor.close()
                            toast("Error: File is empty (0 bytes)")
                            return
                        }
                    }
                    cursor.close()
                }
            } catch (_: Exception) {}
            editorViewModel.clearPendingUris()
            editorViewModel.pendingUris.add(mdUri)
            val prefix = getDisplayName(mdUri)
            createDocument(MIME_PDF, "${prefix}_converted.pdf", REQ_SAVE_MD_TO_PDF)
            return
        }

        if (requestCode == REQ_PICK_COMPRESS_PDF) {
            val pdfUri = data.data ?: return
            try {
                val cursor = contentResolver.query(pdfUri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        val fileSize = cursor.getLong(0)
                        if (fileSize == 0L) {
                            cursor.close()
                            toast("Error: PDF file is empty (0 bytes)")
                            return
                        }
                    }
                    cursor.close()
                }
            } catch (_: Exception) {}
            editorViewModel.clearPendingUris()
            editorViewModel.pendingUris.add(pdfUri)
            worker.submit {
                val hasImages = try {
                    AppModule.get().compressPdf.hasEmbeddedImages(pdfUri)
                } catch (_: Exception) { true }
                runOnUiThread {
                    if (!hasImages) {
                        showCustomDialog(
                            "No Images Found",
                            text("This PDF has no embedded images. Compression may not significantly reduce file size.", 14, R.color.istan_text_muted, false),
                            "Cancel", null,
                            "Continue", Runnable { showCompressModeDialog(pdfUri) }
                        )
                    } else {
                        showCompressModeDialog(pdfUri)
                    }
                }
            }
            return
        }

        val destination = data.data ?: return
        val pages = editorViewModel.pages
        val pendingUris = editorViewModel.pendingUris

        if (requestCode == REQ_SAVE_MERGE_PDF) {
            val rotations = pages.map { it.rotation }
            runJob("Merging PDFs...", OutputRef(destination, MIME_PDF, getDisplayName(destination))) { AppModule.get().mergePdf.execute(ArrayList(pendingUris), rotations, destination) }
        } else if (requestCode == REQ_SAVE_IMAGES_TO_PDF) {
            val snapshot = ArrayList(pages)
            runJob("Converting Images...", OutputRef(destination, MIME_PDF, getDisplayName(destination))) { AppModule.get().imagesToPdf.execute(ArrayList(pendingUris), snapshot, destination) }
        } else if (requestCode == REQ_SAVE_PDF_TO_JPG) {
            runJob("Converting to JPGs...", OutputRef(destination, MIME_ZIP, getDisplayName(destination))) { AppModule.get().pdfToJpeg.execute(pendingUris[0], destination) }
        } else if (requestCode == REQ_SAVE_DOCX_TO_PDF) {
            runJob("Converting DOCX to PDF...", OutputRef(destination, MIME_PDF, getDisplayName(destination))) {
                val docxUri = pendingUris[0]
                val pdfFile = AppModule.get().docxToPdf.execute(docxUri)
                contentResolver.openInputStream(Uri.fromFile(pdfFile))?.use { input ->
                    contentResolver.openOutputStream(destination)?.use { output ->
                        input.copyTo(output)
                    }
                }
                if (pdfFile.exists()) {
                    pdfFile.delete()
                }
            }
        } else if (requestCode == REQ_SAVE_MD_TO_PDF) {
            runJob("Converting Markdown to PDF...", OutputRef(destination, MIME_PDF, getDisplayName(destination))) {
                val mdUri = pendingUris[0]
                val pdfFile = AppModule.get().mdToPdf.execute(mdUri)
                contentResolver.openInputStream(Uri.fromFile(pdfFile))?.use { input ->
                    contentResolver.openOutputStream(destination)?.use { output ->
                        input.copyTo(output)
                    }
                }
                if (pdfFile.exists()) {
                    pdfFile.delete()
                }
            }
        } else if (requestCode == REQ_SAVE_COMPRESS_PDF) {
            val source = pendingUris[0]
            if (compressMode == COMPRESS_MODE_RESOLUTION) {
                runJob("Compressing PDF...", OutputRef(destination, MIME_PDF, getDisplayName(destination))) {
                    AppModule.get().compressPdf.executeByResolution(source, destination, compressDpi, compressQuality)
                }
            } else {
                runJob("Compressing PDF...", OutputRef(destination, MIME_PDF, getDisplayName(destination))) {
                    val actual = AppModule.get().compressPdf.executeBySize(source, destination, compressTargetBytes)
                    if (actual > compressTargetBytes) {
                        runOnUiThread { toast("Could not reach target. Saved at ${actual / 1024} KB.") }
                    }
                }
            }
        } else if (requestCode == REQ_SAVE_REORDER_PDF || requestCode == REQ_SAVE_REORDER_DOCX_EXPORT) {
            val source = editorViewModel.reorderSource ?: return
            val snapshot = ArrayList(pages)
            val isDocx = (requestCode == REQ_SAVE_REORDER_DOCX_EXPORT)

            var removed = false
            for (p in snapshot) {
                if (!p.keep) removed = true
            }

            var reordered = false
            var rotated = false
            var lastIndex = -1
            for (p in snapshot) {
                if (p.keep) {
                    if (lastIndex != -1 && p.originalIndex < lastIndex) {
                        reordered = true
                    }
                    if (p.rotation != 0) {
                        rotated = true
                    }
                    lastIndex = p.originalIndex
                }
            }

            val activeStatus = when {
                removed && reordered -> "Remove+Reorder Pages..."
                removed -> "Removing Pages..."
                else -> "Saving Pages..."
            }

            runJob(activeStatus, OutputRef(destination, if (isDocx) MIME_DOCX else MIME_PDF, getDisplayName(destination))) {
                if (isDocx) {
                    if (snapshot.any { it.replacementFile != null }) {
                        val replFile = File(cacheDir, "replaced_${System.currentTimeMillis()}.pdf")
                        try {
                            AppModule.get().pdfEngine.replacePages(source, snapshot, Uri.fromFile(replFile))
                            AppModule.get().saveDocx.execute(source, snapshot, destination)
                        } finally {
                            if (replFile.exists()) replFile.delete()
                        }
                    } else {
                        AppModule.get().saveDocx.execute(source, snapshot, destination)
                    }
                } else {
                    AppModule.get().reorderPdf.execute(source, snapshot, destination)
                }
            }
        }
    }

    private fun buildHome() {
        isHome = true
        editorViewModel.resetForHome()

        val builder = HomeViewBuilder(this, regularFont, boldFont)
        val homeView = builder.build(object : HomeViewBuilder.HomeActions {
            override fun onMergePdf() { editorViewModel.clearPendingUris(); pickMany(arrayOf(MIME_PDF), REQ_PICK_MERGE_PDF) }
            override fun onModifyPdf() { pickOne(arrayOf(MIME_PDF), REQ_PICK_REORDER_PDF) }
            override fun onCompressPdf() { pickOne(arrayOf(MIME_PDF), REQ_PICK_COMPRESS_PDF) }
            override fun onImageToPdf() { editorViewModel.clearPendingImageUris(); showImageSourceChooser() }
            override fun onPdfToImage() { pickOne(arrayOf(MIME_PDF), REQ_PICK_PDF_TO_JPG) }
            override fun onDocxToPdf() { pickOne(arrayOf(MIME_DOCX), REQ_PICK_DOCX_TO_PDF) }
            override fun onMdToPdf() { pickOne(arrayOf(MIME_MD, "text/plain"), REQ_PICK_MD_TO_PDF) }
            override fun onDocxRemovePages() { pickOne(arrayOf(MIME_DOCX), REQ_PICK_REORDER_DOCX) }
            override fun onDocxReorderPages() { pickOne(arrayOf(MIME_DOCX), REQ_PICK_REORDER_DOCX_EXPORT) }
            override fun onSupportDeveloper() { showDonationPicker(null) }
            override fun onOpenSettings() { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        })
        status = builder.status
        statusIndicator = builder.statusIndicator
        setViewWithLoading(homeView)
    }

    private fun buildPageEditor(titleText: String, saveLabelText: String, docxExport: Boolean, allowReorder: Boolean) {
        isHome = false
        editorViewModel.pagesAdded = false
        currentEditorTitle = titleText

        val builder = EditorViewBuilder(this, regularFont, boldFont)
        val editorView = builder.build(titleText, saveLabelText, docxExport, allowReorder, object : EditorViewBuilder.EditorActions {
            override fun onBack() { buildHome() }
            override fun onSave(title: String, isDocx: Boolean) { handleSave(title, isDocx) }
            override fun onAddItems(title: String) { handleAddItems(title) }
            override fun onTakePhoto() {
                if (currentEditorTitle != "Images to PDF") cameraMergeToPdf = true
                launchCameraForCapture()
            }
            override fun onCropPage(position: Int, onCropped: Runnable?) { cropEditorPage(position, onCropped) }
            override fun onShowCustomDialog(title: String, content: View, negStr: String?, negAction: Runnable?, posStr: String?, posAction: Runnable?) {
                showCustomDialog(title, content, negStr, negAction, posStr, posAction)
            }
            override fun toast(msg: String) { this@MainActivity.toast(msg) }
            override fun getPages(): MutableList<PageItem> = editorViewModel.pages
            override fun getPendingUris(): MutableList<Uri> = editorViewModel.pendingUris
            override fun isPagesAdded(): Boolean = editorViewModel.pagesAdded
        })
        status = builder.status
        statusIndicator = builder.statusIndicator
        pageList = builder.pageList
        setViewWithLoading(editorView)
    }

    private fun handleSave(titleText: String, docxExport: Boolean) {
        val pages = editorViewModel.pages
        val pendingUris = editorViewModel.pendingUris

        var removed = false
        var keepCount = 0
        var cropped = false
        for (p in pages) {
            if (!p.keep) removed = true
            else keepCount++
            if (p.replacementFile != null) cropped = true
        }
        if (keepCount == 0) {
            toast("Please select at least one page to save.")
            return
        }

        var reordered = false
        var rotated = false
        var lastIndex = -1
        for (p in pages) {
            if (p.keep) {
                if (lastIndex != -1 && p.originalIndex < lastIndex) {
                    reordered = true
                }
                if (p.rotation != 0) {
                    rotated = true
                }
                lastIndex = p.originalIndex
            }
        }

        if ("Merge PDF" == titleText) {
            val keptUris = mutableListOf<Uri>()
            for (i in pages.indices) {
                if (pages[i].keep && i < pendingUris.size) {
                    keptUris.add(pendingUris[i])
                }
            }
            if (keptUris.size < 2) {
                toast("Please keep at least 2 PDFs to merge.")
                return
            }
            pendingUris.clear()
            pendingUris.addAll(keptUris)
            val mergePrefix = getDisplayName(keptUris[0])
            createDocument(MIME_PDF, "${mergePrefix}_merged.pdf", REQ_SAVE_MERGE_PDF)
            return
        }

        val pagesAdded = editorViewModel.pagesAdded

        if ("Images to PDF" == titleText) {
        } else if (!removed && !reordered && !rotated && !pagesAdded && !cropped) {
            status?.text = "No changes to save."
            return
        }

        var changedCount = 0
        if (removed) changedCount++
        if (reordered) changedCount++
        if (rotated) changedCount++
        if (pagesAdded) changedCount++
        if (cropped) changedCount++

        val suffix = when {
            changedCount > 1 -> "_modified"
            pagesAdded -> "_added"
            reordered -> "_reordered"
            removed -> "_removed"
            rotated -> "_rotated"
            cropped -> "_cropped"
            else -> ""
        }

        val prefix = editorViewModel.originalFileName ?: getDisplayName(editorViewModel.reorderSource)
        when {
            docxExport -> createDocument(MIME_DOCX, "$prefix$suffix.docx", REQ_SAVE_REORDER_DOCX_EXPORT)
            "Images to PDF" == titleText -> {
                val imgPrefix = getDisplayName(if (pendingUris.isEmpty()) null else pendingUris[0])
                createDocument(MIME_PDF, "${imgPrefix}_converted.pdf", REQ_SAVE_IMAGES_TO_PDF)
            }
            else -> createDocument(MIME_PDF, "$prefix$suffix.pdf", REQ_SAVE_REORDER_PDF)
        }
    }

    private fun handleAddItems(titleText: String) {
        when {
            "Reorder Pages from DOCX" == titleText -> pickMany(arrayOf("image/jpeg", "image/png", "image/webp", "image/bmp", MIME_DOCX, MIME_PDF), REQ_PICK_DOCX_ADD)
            titleText == "Remove/Reorder PDF" -> pickMany(arrayOf("image/jpeg", "image/png", "image/webp", "image/bmp", MIME_PDF), REQ_PICK_PDF_ADD)
            "Merge PDF" == titleText -> pickMany(arrayOf(MIME_PDF), REQ_PICK_MERGE_PDF_ADD)
            else -> pickMany(arrayOf("image/jpeg", "image/png", "image/webp", "image/bmp", "application/pdf"), REQ_PICK_IMAGES_TO_PDF_ADD)
        }
    }

    private fun showImageSourceChooser() {
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(0, dp(12), 0, dp(4))

        val dialogRef = arrayOfNulls<android.app.Dialog>(1)

        val card1 = MaterialCardView(this)
        card1.setCardBackgroundColor(Color.TRANSPARENT)
        card1.radius = dp(16).toFloat()
        card1.strokeWidth = dp(1)
        card1.strokeColor = color(R.color.istan_outline)
        card1.cardElevation = 0f
        card1.setOnClickListener {
            dialogRef[0]?.dismiss()
            pickMany(IMAGE_MIME_TYPES, REQ_PICK_IMAGES_TO_PDF)
        }

        val opt1 = text("From Gallery", 15, R.color.istan_text, false)
        opt1.setPadding(dp(20), dp(16), dp(20), dp(16))
        card1.addView(opt1)

        val lp1 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp1.setMargins(0, 0, 0, dp(12))
        content.addView(card1, lp1)

        val card2 = MaterialCardView(this)
        card2.setCardBackgroundColor(Color.TRANSPARENT)
        card2.radius = dp(16).toFloat()
        card2.strokeWidth = dp(1)
        card2.strokeColor = color(R.color.istan_outline)
        card2.cardElevation = 0f
        card2.setOnClickListener {
            dialogRef[0]?.dismiss()
            launchCameraForCapture()
        }

        val opt2 = text("Take Photo", 15, R.color.istan_text, false)
        opt2.setPadding(dp(20), dp(16), dp(20), dp(16))
        card2.addView(opt2)

        val lp2 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        content.addView(card2, lp2)

        dialogRef[0] = showCustomDialog("Add Images", content, "Cancel", null, null, null)
    }

    private fun launchCameraForCapture() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            return
        }
        val file = createCameraCaptureFile() ?: run {
            toast("Could not create a photo file.")
            return
        }
        cameraPhotoFile = file
        val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
        captureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
            cameraLauncher.launch(captureIntent)
        } catch (e: Exception) {
            discardCameraPhoto()
            toast("No camera app found. Choose from gallery instead.")
            pickMany(IMAGE_MIME_TYPES, REQ_PICK_IMAGES_TO_PDF)
        }
    }

    private fun createCameraCaptureFile(): File? {
        return try {
            val dir = File(cacheDir, "camera_capture")
            if (!dir.exists()) dir.mkdirs()
            File.createTempFile("istan_cam_", ".jpg", dir)
        } catch (e: Exception) {
            null
        }
    }

    private fun onCameraResult(result: ActivityResult) {
        val file = cameraPhotoFile ?: return
        if (result.resultCode == Activity.RESULT_OK) {
            if (file.exists() && file.length() > 0) {
                showCropDialog(file)
            } else {
                val uri = result.data?.data
                if (uri != null) {
                    try {
                        val copied = vasuki.istanpdf.util.ContentFiles.copyUriToCache(this, uri, ".jpg")
                        file.delete()
                        cameraPhotoFile = copied
                        showCropDialog(copied)
                    } catch (e: Exception) {
                        discardCameraPhoto()
                        showError(e)
                    }
                } else {
                    discardCameraPhoto()
                }
            }
        } else {
            discardCameraPhoto()
        }
    }

    private fun showCropDialog(photoFile: File) {
        if (cropDialog != null) return
        val bmp = try {
            BitmapUtils.loadCameraBitmap(photoFile, CROP_MAX_DIM)
        } catch (e: Exception) {
            discardCameraPhoto()
            showError(e)
            return
        }
        cropBitmap = bmp
        showCropUi("Crop Photo", bmp, {
            cameraPhotoFile?.let { if (it.exists()) it.delete() }
            cameraPhotoFile = null
            clearCropState()
            launchCameraForCapture()
        }) { cropped ->
            cameraPhotoFile = null
            clearCropState()
            if (cameraMergeToPdf) {
                cameraMergeToPdf = false
                appendCameraPhotoToPdfOps(cropped)
            } else {
                addCameraImage(cropped)
            }
        }
    }

    private fun showPageCropDialog(bitmap: Bitmap, onResult: (Bitmap) -> Unit) {
        if (cropDialog != null) {
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }
        cropBitmap = bitmap
        showCropUi("Crop Page", bitmap, null) { cropped ->
            clearCropState()
            onResult(cropped)
        }
    }

    private fun showCropUi(title: String, bmp: Bitmap, retakeAction: (() -> Unit)?, onAdd: (Bitmap) -> Unit) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        dialog.window?.statusBarColor = Color.parseColor("#E6252525")
        dialog.window?.navigationBarColor = Color.parseColor("#E6252525")
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))

        val dialogRoot = LinearLayout(this)
        dialogRoot.orientation = LinearLayout.VERTICAL
        dialogRoot.setBackgroundColor(Color.parseColor("#E6252525"))

        val topBar = FrameLayout(this)
        val closeBtn = TextView(this)
        closeBtn.text = "✕"
        closeBtn.setTextColor(Color.WHITE)
        closeBtn.textSize = 26f
        closeBtn.setPadding(dp(16), dp(12), dp(16), dp(12))
        val clsLp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        clsLp.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        topBar.addView(closeBtn, clsLp)

        val topTitle = text(title, 18, R.color.istan_surface, true)
        topTitle.setTextColor(Color.WHITE)
        val ttLp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ttLp.gravity = Gravity.CENTER
        topBar.addView(topTitle, ttLp)
        dialogRoot.addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val cropView = CropOverlayView(this)
        cropView.setImage(bmp)
        dialogRoot.addView(cropView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        this.cropView = cropView

        val pillBg = android.graphics.drawable.GradientDrawable()
        pillBg.setColor(Color.parseColor("#22FFFFFF"))
        pillBg.cornerRadius = dp(32).toFloat()
        val controlsPill = LinearLayout(this)
        controlsPill.orientation = LinearLayout.HORIZONTAL
        controlsPill.gravity = Gravity.CENTER_VERTICAL
        controlsPill.isBaselineAligned = false
        controlsPill.background = pillBg
        controlsPill.setPadding(dp(4), dp(4), dp(4), dp(4))

        val rotLeftBtn = cropIconButton(R.drawable.rotate_left) { rotateWorkingBitmap(-90f) }
        controlsPill.addView(rotLeftBtn, LinearLayout.LayoutParams(dp(48), dp(48)))

        val rotRightBtn = cropIconButton(R.drawable.rotate_right) { rotateWorkingBitmap(90f) }
        controlsPill.addView(rotRightBtn, LinearLayout.LayoutParams(dp(48), dp(48)))

        if (retakeAction != null) {
            val retakeBtn = cropIconButton(R.drawable.close_24px) {
                dialog.dismiss()
                retakeAction()
            }
            controlsPill.addView(retakeBtn, LinearLayout.LayoutParams(dp(48), dp(48)))
        }

        val addBtn = cropIconButton(R.drawable.check_24px) {
            val cropped = try {
                this.cropView?.cropBitmap()
            } catch (e: Exception) {
                android.util.Log.e("IStanPdf", "Crop failed", e)
                null
            }
            dialog.dismiss()
            if (cropped != null) {
                onAdd(cropped)
            } else {
                Toast.makeText(this@MainActivity, "Could not crop image", Toast.LENGTH_SHORT).show()
                if (retakeAction != null) discardCameraPhoto() else clearCropState()
            }
        }
        controlsPill.addView(addBtn, LinearLayout.LayoutParams(dp(48), dp(48)))

        val bottomBar = FrameLayout(this)
        val pillLp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        pillLp.gravity = Gravity.CENTER
        bottomBar.setPadding(0, dp(14), 0, dp(20))
        bottomBar.addView(controlsPill, pillLp)
        dialogRoot.addView(bottomBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        closeBtn.setOnClickListener {
            dialog.dismiss()
            if (retakeAction != null) discardCameraPhoto() else clearCropState()
        }
        dialog.setOnCancelListener {
            if (retakeAction != null) discardCameraPhoto() else clearCropState()
        }
        dialog.setOnDismissListener { this.cropDialog = null }
        dialog.setContentView(dialogRoot)
        cropDialog = dialog
        dialog.show()
    }

    private fun cropIconButton(drawableRes: Int, action: () -> Unit): ImageView {
        val iv = ImageView(this)
        iv.setImageResource(drawableRes)
        iv.setColorFilter(Color.WHITE)
        iv.setPadding(dp(12), dp(12), dp(12), dp(12))
        iv.setOnClickListener { action() }
        return iv
    }

    private fun rotateWorkingBitmap(degrees: Float) {
        val current = cropBitmap ?: return
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees)
        val rotated = try {
            Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
        } catch (e: Throwable) {
            if (e is OutOfMemoryError) Toast.makeText(this, "Not enough memory to rotate", Toast.LENGTH_SHORT).show()
            null
        } ?: return
        cropView?.setImage(rotated)
        if (!current.isRecycled) current.recycle()
        cropBitmap = rotated
    }

    private fun clearCropState() {
        cropBitmap?.let { if (!it.isRecycled) it.recycle() }
        cropBitmap = null
        cropView = null
    }

    private fun discardCameraPhoto() {
        cameraMergeToPdf = false
        cameraPhotoFile?.let { if (it.exists()) it.delete() }
        cameraPhotoFile = null
        clearCropState()
    }

    private fun cropEditorPage(position: Int, onCropped: Runnable?) {
        val pages = editorViewModel.pages
        if (position < 0 || position >= pages.size) return
        val page = pages[position]
        val title = currentEditorTitle
        setBusy(true, "Preparing page for crop...", true)
        worker.execute {
            try {
                val src = if (page.replacementFile != null && page.replacementFile!!.exists()) {
                    BitmapUtils.loadCameraBitmap(page.replacementFile!!, CROP_MAX_DIM)
                } else if (title == "Images to PDF") {
                    val uri = page.uri
                    if (uri != null) BitmapUtils.loadImageUri(this@MainActivity, uri, CROP_MAX_DIM) else null
                } else {
                    editorViewModel.reorderSource?.let {
                        AppModule.get().pdfEngine.renderPage(it, page.originalIndex, CROP_MAX_DIM)
                    }
                }
                if (src == null) throw IllegalStateException("Cannot load page image")
                runOnUiThread {
                    setBusy(false, "Ready")
                    showPageCropDialog(src) { cropped ->
                        applyPageCrop(page, cropped, onCropped)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setBusy(false, "Ready")
                    showError(e)
                }
            }
        }
    }

    private fun applyPageCrop(page: PageItem, cropped: Bitmap, onDone: Runnable? = null) {
        worker.execute {
            try {
                val file = File(cacheDir, "crop_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { out ->
                    cropped.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                page.replacementFile = file
                editorViewModel.tempImageFiles.add(file)

                var thumb = BitmapUtils.scaleToFit(cropped, dp(280))
                if (page.rotation != 0) {
                    val matrix = android.graphics.Matrix()
                    matrix.postRotate(page.rotation.toFloat())
                    val rotated = Bitmap.createBitmap(thumb, 0, 0, thumb.width, thumb.height, matrix, true)
                    if (rotated !== thumb) {
                        if (!thumb.isRecycled) thumb.recycle()
                        thumb = rotated
                    }
                }
                if (thumb !== cropped && !cropped.isRecycled) cropped.recycle()
                val finalThumb = thumb
                runOnUiThread {
                    if (!page.thumbnail.isRecycled) page.thumbnail.recycle()
                    page.thumbnail = finalThumb
                    pageList?.adapter?.notifyDataSetChanged()
                    onDone?.run()
                }
            } catch (e: Exception) {
                runOnUiThread { showError(e) }
            }
        }
    }

    private fun appendCameraPhotoToPdfOps(bitmap: Bitmap) {
        setBusy(true, "Adding photo...", true)
        worker.execute {
            try {
                val photoFile = File(cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                FileOutputStream(photoFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                editorViewModel.tempImageFiles.add(photoFile)

                val reorderSource = editorViewModel.reorderSource
                    ?: throw IllegalStateException("No source document")
                val photoPdf = File(cacheDir, "photo_pdf_${System.currentTimeMillis()}.pdf")
                val iw = bitmap.width.toFloat()
                val ih = bitmap.height.toFloat()
                val baos = java.io.ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, baos)
                val imgData = com.itextpdf.io.image.ImageDataFactory.createJpeg(baos.toByteArray())
                val imgFos = java.io.FileOutputStream(photoPdf)
                val imgDoc = try {
                    com.itextpdf.kernel.pdf.PdfDocument(com.itextpdf.kernel.pdf.PdfWriter(imgFos))
                } catch (e: Exception) { imgFos.close(); throw e }
                val ps = com.itextpdf.kernel.geom.PageSize(iw, ih)
                val imgPage = imgDoc.addNewPage(ps)
                val pdfCanvas = com.itextpdf.kernel.pdf.canvas.PdfCanvas(imgPage)
                pdfCanvas.addImageFittedIntoRectangle(imgData, com.itextpdf.kernel.geom.Rectangle(0f, 0f, iw, ih), false)
                imgDoc.close()
                if (!bitmap.isRecycled) bitmap.recycle()

                val mergedFile = File(cacheDir, "merged_${System.currentTimeMillis()}.pdf")
                val srcFile = vasuki.istanpdf.util.ContentFiles.copyUriToCache(this@MainActivity, reorderSource, ".pdf")
                try {
                    val srcDoc = com.itextpdf.kernel.pdf.PdfDocument(com.itextpdf.kernel.pdf.PdfReader(srcFile))
                    val addDoc = com.itextpdf.kernel.pdf.PdfDocument(com.itextpdf.kernel.pdf.PdfReader(photoPdf))
                    val destFos = java.io.FileOutputStream(mergedFile)
                    val destDoc = try {
                        com.itextpdf.kernel.pdf.PdfDocument(com.itextpdf.kernel.pdf.PdfWriter(destFos))
                    } catch (e: Exception) { destFos.close(); throw e }
                    val pdfMerger = com.itextpdf.kernel.utils.PdfMerger(destDoc)
                    pdfMerger.merge(srcDoc, 1, srcDoc.numberOfPages)
                    pdfMerger.merge(addDoc, 1, addDoc.numberOfPages)
                    addDoc.close()
                    srcDoc.close()
                    destDoc.close()
                } finally {
                    if (srcFile.exists()) srcFile.delete()
                    if (photoPdf.exists()) photoPdf.delete()
                }
                val mergedUri = Uri.fromFile(mergedFile)
                val allRendered = renderPdfPages(mergedUri)
                val pages = editorViewModel.pages
                val existingCount = pages.size
                val newPages = allRendered.drop(existingCount).toMutableList()

                if ("file" == reorderSource.scheme) {
                    val oldFile = File(reorderSource.path!!)
                    if (oldFile.exists() && oldFile.absolutePath.startsWith(cacheDir.absolutePath)) {
                        oldFile.delete()
                    }
                }
                editorViewModel.reorderSource = mergedUri
                editorViewModel.tempImageFiles.add(mergedFile)
                runOnUiThread {
                    pages.addAll(newPages)
                    editorViewModel.pagesAdded = true
                    pageList?.adapter?.notifyDataSetChanged()
                    setBusy(false, "Ready")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setBusy(false, "Ready")
                    showError(e)
                }
            }
        }
    }

    private fun addCameraImage(bitmap: Bitmap) {
        worker.execute {
            try {
                val file = File(cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                editorViewModel.tempImageFiles.add(file)
                val fileUri = Uri.fromFile(file)

                val thumb = BitmapUtils.scaleToFit(bitmap, dp(280))
                if (thumb !== bitmap && !bitmap.isRecycled) bitmap.recycle()
                val newPage = PageItem(editorViewModel.pages.size, thumb, "Camera Photo", uri = fileUri)

                cameraPhotoFile?.let { if (it.exists()) it.delete() }
                cameraPhotoFile = null

                runOnUiThread {
                    editorViewModel.pendingUris.add(fileUri)
                    editorViewModel.pages.add(newPage)
                    editorViewModel.pagesAdded = true
                    if (isHome) {
                        buildPageEditor("Images to PDF", "Save PDF", false, true)
                    } else {
                        pageList?.adapter?.notifyDataSetChanged()
                    }
                    status?.text = "Ready"
                    setStatusIndicatorColor(color(R.color.istan_olive))
                }
            } catch (e: Exception) {
                runOnUiThread { showError(e) }
            }
        }
    }

    private fun loadPdfPreview(uri: Uri, docxExport: Boolean) {
        setBusy(true, "Rendering page previews...", true)
        worker.execute {
            try {
                val rendered = renderPdfPages(uri)
                runOnUiThread {
                    editorViewModel.clearPages()
                    editorViewModel.pages.addAll(rendered)
                    editorViewModel.reorderSource = uri
                    editorViewModel.originalFileName = null
                    setBusy(false, "Ready")
                    buildPageEditor("Remove/Reorder PDF", "Save PDF", docxExport, true)
                }
            } catch (exception: Exception) {
                showError(exception)
            }
        }
    }

    private fun loadImagePreview(rawUris: List<Uri>, clearExisting: Boolean) {
        setBusy(true, "Rendering image previews...", true)
        worker.execute {
            try {
                val pages = editorViewModel.pages
                val pendingUris = editorViewModel.pendingUris
                val tempImageFiles = editorViewModel.tempImageFiles
                val rendered = mutableListOf<PageItem>()
                val processedUris = mutableListOf<Uri>()
                var startIndex = if (clearExisting) 0 else pages.size
                for (i in rawUris.indices) {
                    if (cancelJob.get() || Thread.currentThread().isInterrupted) {
                        throw InterruptedException("Cancelled by user")
                    }
                    val current = i + 1
                    val total = rawUris.size
                    runOnUiThread {
                        if (loadingSubtitle != null && loadingOverlay != null && loadingOverlay!!.visibility == View.VISIBLE) {
                            if (fakeProgressRunnable != null) {
                                fakeProgressHandler.removeCallbacks(fakeProgressRunnable!!)
                                fakeProgressRunnable = null
                            }
                            loadingSubtitle!!.text = "Item $current of $total"
                            loadingSpinner?.let { spinner ->
                                if (spinner.max != total) {
                                    spinner.isIndeterminate = false
                                    spinner.max = total
                                }
                                spinner.setProgressCompat(current, true)
                            }
                        }
                    }
                    val uri = rawUris[i]
                    val mime = contentResolver.getType(uri)
                    if (mime != null && mime == MIME_PDF) {
                        val pdfPages = renderPdfPages(uri)
                        var pIdx = 1
                        for (pdfP in pdfPages) {
                            val f = File(cacheDir, "pdf_to_img_${System.currentTimeMillis()}_${pIdx}.jpg")
                            java.io.FileOutputStream(f).use { out ->
                                pdfP.thumbnail.compress(Bitmap.CompressFormat.JPEG, 95, out)
                            }
                            tempImageFiles.add(f)
                            val fileUri = Uri.fromFile(f)
                            processedUris.add(fileUri)
                            rendered.add(PageItem(startIndex++, pdfP.thumbnail, "PDF Page $pIdx", uri = fileUri))
                            pIdx++
                        }
                    } else {
                        processedUris.add(uri)
                        val size = dp(280)
                        val thumb = loadThumbnail(uri, size)
                        rendered.add(PageItem(startIndex++, thumb, getDisplayName(uri), uri = uri))
                    }
                }
                runOnUiThread {
                    if (clearExisting) editorViewModel.clearPages()
                    editorViewModel.pages.addAll(rendered)
                    pendingUris.addAll(processedUris)
                    editorViewModel.reorderSource = null
                    editorViewModel.originalFileName = null
                    if (clearExisting) {
                        setBusy(false, "Ready")
                        buildPageEditor("Images to PDF", "Save PDF", false, true)
                    } else {
                        pageList?.adapter?.notifyDataSetChanged()
                        setBusy(false, "Ready")
                    }
                }
            } catch (exception: Exception) {
                runOnUiThread { showError(exception) }
            }
        }
    }

    private fun loadThumbnail(uri: Uri, maxDim: Int): Bitmap {
        val opt = android.graphics.BitmapFactory.Options()
        opt.inJustDecodeBounds = true
        (contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open image file")).use { stream ->
            android.graphics.BitmapFactory.decodeStream(stream, null, opt)
        }
        var scale = 1
        while (opt.outWidth / scale / 2 >= maxDim && opt.outHeight / scale / 2 >= maxDim) {
            scale *= 2
        }
        val opt2 = android.graphics.BitmapFactory.Options()
        opt2.inSampleSize = scale
        return (contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open image file for decoding")).use { stream ->
            android.graphics.BitmapFactory.decodeStream(stream, null, opt2)
                ?: throw IllegalArgumentException("Cannot decode image file. Format not supported.")
        }
    }

    private fun loadDocxPreviewViaLibreOffice(docx: Uri) {
        setBusy(true, "Rendering DOCX previews...")
        worker.execute {
            try {
                val pdfFile = AppModule.get().docxToPdf.execute(docx)
                val rendered = renderPdfPages(Uri.fromFile(pdfFile))

                runOnUiThread {
                    editorViewModel.clearPages()
                    editorViewModel.pages.addAll(rendered)
                    editorViewModel.reorderSource = docx
                    editorViewModel.originalFileName = null
                    setBusy(false, "Ready")
                    buildPageEditor("Remove Pages from DOCX", "Save DOCX", true, false)
                }
            } catch (exception: Exception) {
                showError(exception)
            }
        }
    }

    private fun renderFirstPdfPage(uri: Uri): Bitmap? {
        val screenWidth = resources.displayMetrics.widthPixels
        val targetWidth = maxOf(dp(280), screenWidth - dp(64))
        return AppModule.get().pdfEngine.renderFirstPage(uri, targetWidth)
    }

    private fun renderPdfPages(uri: Uri): List<PageItem> {
        val screenWidth = resources.displayMetrics.widthPixels
        val targetWidth = maxOf(dp(280), screenWidth - dp(64))
        return AppModule.get().pdfEngine.renderAllPages(uri, targetWidth, cancelJob) { current, total ->
            runOnUiThread {
                if (loadingSubtitle != null && loadingOverlay != null && loadingOverlay!!.visibility == View.VISIBLE) {
                    if (fakeProgressRunnable != null) {
                        fakeProgressHandler.removeCallbacks(fakeProgressRunnable!!)
                        fakeProgressRunnable = null
                    }
                    loadingSubtitle!!.text = "Page $current of $total"
                    loadingSpinner?.let { spinner ->
                        if (spinner.max != total) {
                            spinner.isIndeterminate = false
                            spinner.max = total
                        }
                        spinner.setProgressCompat(current, true)
                    }
                }
            }
        }
    }

    private fun pickMany(mimeTypes: Array<String>, requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        if (mimeTypes.size == 1) {
            intent.type = mimeTypes[0]
        } else {
            intent.type = "*/*"
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        }
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        launchDocIntent(intent, requestCode)
    }

    private fun pickOne(mimeTypes: Array<String>, requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        if (mimeTypes.size == 1) {
            intent.type = mimeTypes[0]
        } else {
            intent.type = "*/*"
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        }
        launchDocIntent(intent, requestCode)
    }

    private fun createDocument(mimeType: String, name: String, requestCode: Int) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = mimeType
        intent.putExtra(Intent.EXTRA_TITLE, name)
        launchDocIntent(intent, requestCode)
    }

    private fun launchDocIntent(intent: Intent, requestCode: Int) {
        activeReq = requestCode
        docLauncher.launch(intent)
    }

    private fun readSelectedUris(data: Intent): List<Uri> {
        val uris = mutableListOf<Uri>()
        val clipData: ClipData? = data.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                uris.add(clipData.getItemAt(i).uri)
            }
        } else if (data.data != null) {
            uris.add(data.data!!)
        }
        return uris
    }

    fun showCustomDialog(titleStr: String, content: View?, negativeStr: String?, negativeAction: Runnable?, positiveStr: String?, positiveAction: Runnable?): android.app.Dialog {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.window!!.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(false)

        val dialogRoot = LinearLayout(this)
        dialogRoot.orientation = LinearLayout.VERTICAL
        val gd = android.graphics.drawable.GradientDrawable()
        gd.setColor(color(R.color.istan_surface))
        gd.cornerRadius = dp(28).toFloat()
        gd.setStroke(dp(1), color(R.color.istan_outline))
        dialogRoot.background = gd
        dialogRoot.setPadding(dp(24), dp(24), dp(24), dp(24))

        val title = text(titleStr, 20, R.color.istan_text, true)
        title.setPadding(0, 0, 0, dp(12))
        dialogRoot.addView(title)

        if (content != null) {
            dialogRoot.addView(content)
        }

        val btnRow = LinearLayout(this)
        btnRow.orientation = LinearLayout.HORIZONTAL
        btnRow.gravity = Gravity.END
        btnRow.setPadding(0, dp(12), 0, 0)

        if (negativeStr != null) {
            val cancel = text(negativeStr, 14, R.color.istan_text_muted, true)
            cancel.setPadding(dp(16), dp(8), dp(16), dp(8))
            cancel.setOnClickListener {
                dialog.dismiss()
                negativeAction?.run()
            }
            btnRow.addView(cancel)
        }

        if (positiveStr != null) {
            val positive = text(positiveStr, 14, R.color.istan_olive, true)
            positive.setPadding(dp(16), dp(8), dp(16), dp(8))
            positive.setOnClickListener {
                dialog.dismiss()
                positiveAction?.run()
            }
            btnRow.addView(positive)
        }

        dialogRoot.addView(btnRow)
        dialog.setContentView(dialogRoot)
        dialog.window!!.setLayout((resources.displayMetrics.widthPixels * 0.85).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
        return dialog
    }

    private fun interface Job {
        @Throws(Exception::class)
        fun run()
    }

    private data class OutputRef(val uri: Uri, val mime: String, val name: String)

    private fun runJob(message: String, output: OutputRef? = null, job: Job) {
        setBusy(true, message)
        worker.submit {
            try {
                job.run()
                runOnUiThread {
                    setBusy(false, "Ready")
                    if (output != null) {
                        showOutputSnackbar(output)
                    }
                }
            } catch (exception: Exception) {
                showError(exception)
            }
        }
    }

    private fun showOutputSnackbar(output: OutputRef) {
        val show = Runnable {
            val root = findViewById<View>(android.R.id.content) ?: return@Runnable
            val accent = ThemePrefs.accent(this)
            val amoled = ThemePrefs.isAmoled(this)
            val snackbar = Snackbar.make(root, "File saved successfully", Snackbar.LENGTH_LONG)
            snackbar.duration = 4500
            val background = android.graphics.drawable.GradientDrawable()
            background.setColor(color(R.color.istan_surface))
            background.cornerRadius = dp(8).toFloat()
            background.setStroke(dp(1), color(R.color.istan_outline))
            snackbar.view.background = background
            snackbar.view.backgroundTintList = null
            snackbar.view.elevation = dp(8).toFloat()
            snackbar.setTextColor(color(R.color.istan_text))
            snackbar.setActionTextColor(ThemePrefs.accentForeground(accent, amoled))
            val textView = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
            textView?.apply {
                typeface = regularFont
                textSize = 15f
                maxLines = 3
                includeFontPadding = false
            }
            val actionView = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_action)
            actionView?.typeface = boldFont
            snackbar.setAction("Open") { openSavedFile(output) }
            val bottomInset = androidx.core.view.ViewCompat.getRootWindowInsets(root)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
            val lp = snackbar.view.layoutParams as? ViewGroup.MarginLayoutParams
            lp?.setMargins(dp(16), 0, dp(16), dp(24) + bottomInset)
            if (lp != null) snackbar.view.layoutParams = lp
            snackbar.show()
        }
        if (loadingOverlay != null && loadingOverlay!!.visibility == View.VISIBLE) {
            fakeProgressHandler.postDelayed(show, 400)
        } else {
            show.run()
        }
    }

    private fun openSavedFile(output: OutputRef) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(output.uri, output.mime)
            intent.putExtra(Intent.EXTRA_TITLE, output.name)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setStatusIndicatorColor(color: Int) {
        val drawable = statusIndicator?.drawable
        if (drawable is android.graphics.drawable.GradientDrawable) {
            drawable.setColor(color)
        }
    }

    private fun showError(exception: Exception) {
        if (exception is InterruptedException || "Cancelled by user" == exception.message) {
            runOnUiThread { setBusy(false, "Ready") }
            return
        }
        android.util.Log.e("IStanPdf", "Operation failed", exception)
        runOnUiThread {
            loadingOverlay?.visibility = View.GONE
            val message = exception.message ?: exception.javaClass.simpleName
            if (status != null) {
                setStatusIndicatorColor(Color.RED)
                status!!.text = message
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun text(value: String, sp: Int, colorRes: Int, bold: Boolean): TextView {
        val textView = TextView(this)
        textView.text = value
        textView.textSize = sp.toFloat()
        textView.setTextColor(color(colorRes))
        textView.typeface = if (bold) boldFont else regularFont
        textView.includeFontPadding = false
        return textView
    }

    private fun setViewWithLoading(view: View) {
        loadingOverlay?.let { overlay ->
            (overlay.parent as? ViewGroup)?.removeView(overlay)
        }

        val rootFrame = FrameLayout(this)
        rootFrame.setBackgroundColor(color(R.color.istan_background))

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        if (view is ViewGroup) {
            view.clipToPadding = false
        }

        rootFrame.addView(view, FrameLayout.LayoutParams(-1, -1))

        loadingOverlay = FrameLayout(this)
        loadingOverlay!!.setBackgroundColor(Color.argb(178, 18, 18, 18))
        loadingOverlay!!.isClickable = true
        loadingOverlay!!.isFocusable = true
        loadingOverlay!!.visibility = View.GONE

        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.gravity = Gravity.CENTER
        panel.setPadding(dp(32), dp(32), dp(32), dp(24))
        val panelBg = android.graphics.drawable.GradientDrawable()
        panelBg.setColor(color(R.color.istan_surface))
        panelBg.cornerRadius = dp(28).toFloat()
        panelBg.setStroke(dp(1), color(R.color.istan_outline))
        panel.background = panelBg

        loadingSpinner = com.google.android.material.progressindicator.CircularProgressIndicator(this)
        loadingSpinner!!.isIndeterminate = true
        loadingSpinner!!.setIndicatorColor(color(R.color.istan_olive_dark))
        loadingSpinner!!.trackColor = color(R.color.istan_outline)
        loadingSpinner!!.trackThickness = dp(4)
        loadingSpinner!!.indicatorSize = dp(48)
        val spinnerParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        panel.addView(loadingSpinner, spinnerParams)

        loadingMessage = text(WAITING_TEXT, 18, R.color.istan_text, true)
        loadingMessage!!.gravity = Gravity.CENTER
        val messageParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        messageParams.topMargin = dp(20)
        panel.addView(loadingMessage, messageParams)

        loadingSubtitle = text("", 15, R.color.istan_text_muted, false)
        loadingSubtitle!!.gravity = Gravity.CENTER
        val subParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        subParams.topMargin = dp(8)
        panel.addView(loadingSubtitle, subParams)

        val cancelBtn = MaterialCardView(this)
        cancelBtn.setCardBackgroundColor(Color.TRANSPARENT)
        cancelBtn.radius = dp(100).toFloat()
        cancelBtn.strokeWidth = dp(1)
        cancelBtn.strokeColor = color(R.color.istan_outline)
        cancelBtn.cardElevation = 0f
        cancelBtn.useCompatPadding = false

        val cancelText = text("Cancel", 15, R.color.istan_text_muted, false)
        cancelText.setPadding(dp(24), dp(10), dp(24), dp(10))
        cancelBtn.addView(cancelText)

        cancelBtn.setOnClickListener {
            cancelJob.set(true)
            setBusy(false, "Ready")
        }

        val btnParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        btnParams.topMargin = dp(24)
        panel.addView(cancelBtn, btnParams)

        val panelParams = FrameLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        loadingOverlay!!.addView(panel, panelParams)

        rootFrame.addView(loadingOverlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(rootFrame)
        androidx.core.view.ViewCompat.requestApplyInsets(rootFrame)
    }

    private fun setBusy(busy: Boolean, message: String) {
        setBusy(busy, message, false)
    }

    private fun setBusy(busy: Boolean, message: String, isDeterminate: Boolean) {
        fakeProgressRunnable?.let {
            fakeProgressHandler.removeCallbacks(it)
            fakeProgressRunnable = null
        }
        dismissOverlayRunnable?.let {
            fakeProgressHandler.removeCallbacks(it)
            dismissOverlayRunnable = null
        }

        if (busy) {
            cancelJob.set(false)
            loadingSpinner?.let { spinner ->
                spinner.isIndeterminate = false
                spinner.max = 100
                spinner.setProgressCompat(0, false)
                if (!isDeterminate) {
                    val progress = intArrayOf(0)
                    fakeProgressRunnable = object : Runnable {
                        override fun run() {
                            if (progress[0] < 90) {
                                progress[0] += 2
                                spinner.setProgressCompat(progress[0], true)
                                fakeProgressHandler.postDelayed(this, 300)
                            }
                        }
                    }
                    fakeProgressHandler.postDelayed(fakeProgressRunnable!!, 300)
                }
            }
            loadingOverlay?.visibility = View.VISIBLE
            loadingMessage?.text = message
            loadingSubtitle?.let {
                it.text = ""
                it.visibility = View.VISIBLE
            }
            status?.let {
                it.animate().cancel()
                it.alpha = 1f
                setStatusIndicatorColor(color(R.color.istan_olive))
                it.text = message
            }
            pageList?.suppressLayout(true)
        } else {
            if (loadingSpinner != null && loadingOverlay != null && loadingOverlay!!.visibility == View.VISIBLE) {
                loadingSpinner!!.setProgressCompat(loadingSpinner!!.max, true)
                loadingMessage?.text = message
                loadingSubtitle?.text = ""
                dismissOverlayRunnable = Runnable {
                    loadingOverlay?.visibility = View.GONE
                    loadingSubtitle?.visibility = View.GONE
                    status?.let {
                        it.animate().cancel()
                        it.alpha = 1f
                        setStatusIndicatorColor(color(R.color.istan_olive))
                        it.text = message
                    }
                    pageList?.suppressLayout(false)
                    dismissOverlayRunnable = null
                }
                fakeProgressHandler.postDelayed(dismissOverlayRunnable!!, 400)
            } else {
                loadingOverlay?.visibility = View.GONE
                loadingMessage?.text = message
                loadingSubtitle?.let {
                    it.text = ""
                    it.visibility = View.GONE
                }
                status?.let {
                    it.animate().cancel()
                    it.alpha = 1f
                    setStatusIndicatorColor(color(R.color.istan_olive))
                    it.text = message
                }
                pageList?.suppressLayout(false)
            }
        }
    }

    private fun color(colorRes: Int): Int = ThemePrefs.resolveColor(this, colorRes)

    private fun applySystemBarTheme() {
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        val lightBars = !ThemePrefs.isAmoled(this)
        controller.isAppearanceLightStatusBars = lightBars
        controller.isAppearanceLightNavigationBars = lightBars
    }

    private fun dp(value: Int): Int = Math.round(value * resources.displayMetrics.density)

    private fun toast(message: String) {
        runOnUiThread {
            setBusy(false, message)
        }
    }

    private fun showCompressModeDialog(pdfUri: Uri) {
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(0, dp(12), 0, dp(4))

        val dialogRef = arrayOfNulls<android.app.Dialog>(1)

        val card1 = MaterialCardView(this)
        card1.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
        card1.radius = dp(16).toFloat()
        card1.strokeWidth = dp(1)
        card1.strokeColor = color(R.color.istan_outline)
        card1.cardElevation = 0f
        card1.setOnClickListener {
            dialogRef[0]?.dismiss()
            compressMode = COMPRESS_MODE_RESOLUTION
            showResolutionPresetDialog(pdfUri)
        }

        val opt1 = text("By Resolution", 15, R.color.istan_text, false)
        opt1.setPadding(dp(20), dp(16), dp(20), dp(16))
        card1.addView(opt1)

        val lp1 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp1.setMargins(0, 0, 0, dp(12))
        content.addView(card1, lp1)

        val card2 = MaterialCardView(this)
        card2.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
        card2.radius = dp(16).toFloat()
        card2.strokeWidth = dp(1)
        card2.strokeColor = color(R.color.istan_outline)
        card2.cardElevation = 0f
        card2.setOnClickListener {
            dialogRef[0]?.dismiss()
            compressMode = COMPRESS_MODE_SIZE
            showSizeInputDialog(pdfUri)
        }

        val opt2 = text("By Size", 15, R.color.istan_text, false)
        opt2.setPadding(dp(20), dp(16), dp(20), dp(16))
        card2.addView(opt2)

        val lp2 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        content.addView(card2, lp2)

        dialogRef[0] = showCustomDialog("Compression Mode", content, "Cancel", null, null, null)
    }

    private fun showResolutionPresetDialog(pdfUri: Uri) {
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(0, dp(12), 0, dp(4))

        val dialogRef = arrayOfNulls<android.app.Dialog>(1)

        val presets = arrayOf(
            Triple("Highest Compression", 72, 40),
            Triple("Balanced", 150, 70)
        )

        for ((index, preset) in presets.withIndex()) {
            val card = MaterialCardView(this)
            card.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
            card.radius = dp(16).toFloat()
            card.strokeWidth = dp(1)
            card.strokeColor = color(R.color.istan_outline)
            card.cardElevation = 0f
            card.setOnClickListener {
                dialogRef[0]?.dismiss()
                compressDpi = preset.second
                compressQuality = preset.third
                val prefix = getDisplayName(pdfUri)
                createDocument(MIME_PDF, "${prefix}_compressed.pdf", REQ_SAVE_COMPRESS_PDF)
            }

            val label = text(preset.first, 15, R.color.istan_text, false)
            label.setPadding(dp(20), dp(16), dp(20), dp(16))
            card.addView(label)

            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            if (index < presets.size - 1) lp.setMargins(0, 0, 0, dp(12))
            content.addView(card, lp)
        }

        dialogRef[0] = showCustomDialog("Resolution Preset", content, "Cancel", null, null, null)
    }

    private fun showSizeInputDialog(pdfUri: Uri) {
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(0, dp(8), 0, dp(4))

        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.hint = "Target size in KB"
        input.textSize = 15f
        input.typeface = regularFont
        input.setTextColor(color(R.color.istan_text))
        input.setHintTextColor(color(R.color.istan_text_muted))
        input.setPadding(dp(16), dp(12), dp(16), dp(12))
        val inputBg = android.graphics.drawable.GradientDrawable()
        inputBg.cornerRadius = dp(12).toFloat()
        inputBg.setStroke(dp(1), color(R.color.istan_outline))
        input.background = inputBg
        content.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        showCustomDialog("Target Size", content, "Cancel", null, "Compress", Runnable {
            val kb = input.text.toString().toLongOrNull()
            if (kb == null || kb <= 0) {
                toast("Please enter a valid size in KB.")
                return@Runnable
            }
            compressTargetBytes = kb * 1024L
            val prefix = getDisplayName(pdfUri)
            createDocument(MIME_PDF, "${prefix}_compressed.pdf", REQ_SAVE_COMPRESS_PDF)
        })
    }

    private fun getDisplayName(uri: Uri?): String = AppModule.get().documentManager.getDisplayName(uri)

    private fun showDonationPicker(onComplete: Runnable?) {
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(0, dp(12), 0, dp(4))

        val dialogRef = arrayOfNulls<android.app.Dialog>(1)

        val clickKofi = Runnable {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/ramakanthgacharya")))
            dialogRef[0]?.dismiss()
            onComplete?.run()
        }

        val clickUpi = Runnable {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay?pa=ramakanthacharya@slc&pn=Ramakanth")))
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "No UPI app found", Toast.LENGTH_SHORT).show()
            }
            dialogRef[0]?.dismiss()
            onComplete?.run()
        }

        val card1 = MaterialCardView(this)
        card1.setCardBackgroundColor(Color.TRANSPARENT)
        card1.radius = dp(16).toFloat()
        card1.strokeWidth = dp(1)
        card1.strokeColor = color(R.color.istan_outline)
        card1.cardElevation = 0f
        card1.setOnClickListener { clickKofi.run() }

        val opt1 = text("Ko-fi (Global)", 15, R.color.istan_text, false)
        opt1.setPadding(dp(20), dp(16), dp(20), dp(16))
        card1.addView(opt1)

        val lp1 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp1.setMargins(0, 0, 0, dp(12))
        content.addView(card1, lp1)

        val card2 = MaterialCardView(this)
        card2.setCardBackgroundColor(Color.TRANSPARENT)
        card2.radius = dp(16).toFloat()
        card2.strokeWidth = dp(1)
        card2.strokeColor = color(R.color.istan_outline)
        card2.cardElevation = 0f
        card2.setOnClickListener { clickUpi.run() }

        val opt2 = text("UPI (India)", 15, R.color.istan_text, false)
        opt2.setPadding(dp(20), dp(16), dp(20), dp(16))
        card2.addView(opt2)

        val lp2 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        content.addView(card2, lp2)

        dialogRef[0] = showCustomDialog("Support the Developer", content, "Cancel", onComplete, null, null)
    }

    private fun pruneStaleCacheFiles() {
        AppModule.get().cacheManager.pruneStaleCacheFiles()
    }

    companion object {
        private const val REQ_PICK_MERGE_PDF = 10
        private const val REQ_PICK_REORDER_PDF = 12
        private const val REQ_PICK_REORDER_DOCX = 13
        private const val REQ_PICK_REORDER_DOCX_EXPORT = 14
        private const val REQ_SAVE_MERGE_PDF = 20
        private const val REQ_SAVE_REORDER_PDF = 22
        private const val REQ_SAVE_REORDER_DOCX_EXPORT = 23

        private const val REQ_PICK_IMAGES_TO_PDF = 32
        private const val REQ_SAVE_IMAGES_TO_PDF = 33
        private const val REQ_PICK_PDF_TO_JPG = 34
        private const val REQ_SAVE_PDF_TO_JPG = 35
        private const val REQ_PICK_IMAGES_TO_PDF_ADD = 36
        private const val REQ_PICK_DOCX_ADD = 37
        private const val REQ_PICK_PDF_ADD = 38
        private const val REQ_PICK_MERGE_PDF_ADD = 39

        private const val REQ_PICK_DOCX_TO_PDF = 40
        private const val REQ_SAVE_DOCX_TO_PDF = 41

        private const val REQ_PICK_MD_TO_PDF = 42
        private const val REQ_SAVE_MD_TO_PDF = 43

        private const val REQ_PICK_COMPRESS_PDF = 44
        private const val REQ_SAVE_COMPRESS_PDF = 45

        private const val COMPRESS_MODE_RESOLUTION = 0
        private const val COMPRESS_MODE_SIZE = 1

        private const val MIME_PDF = "application/pdf"
        private const val MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        private const val MIME_MD = "text/markdown"
        private const val MIME_ZIP = "application/zip"
        private const val WAITING_TEXT = "Ready"

        private val IMAGE_MIME_TYPES = arrayOf("image/jpeg", "image/png", "image/webp", "image/bmp")

        private const val CROP_MAX_DIM = 2048
    }
}
