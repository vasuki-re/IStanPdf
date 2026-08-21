package vasuki.istanpdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfNumber
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import vasuki.istanpdf.model.PageItem
import java.io.ByteArrayOutputStream

object ImagesToPdf {
    private const val MAX_IMAGE_DIMENSION = 4096

    @Throws(Exception::class)
    fun run(ctx: Context, src: List<Uri>, pages: List<PageItem>, dst: Uri) {
        PdfStore.openDst(ctx, dst).use { out ->
            val pdfDoc = PdfDocument(PdfWriter(out))
            try {
                for (pageItem in pages) {
                    if (!pageItem.keep) continue
                    val uri = pageItem.replacementFile?.let { Uri.fromFile(it) }
                        ?: pageItem.uri
                        ?: src[pageItem.originalIndex]

                    val opt = BitmapFactory.Options()
                    opt.inJustDecodeBounds = true
                    (ctx.contentResolver.openInputStream(uri)
                        ?: throw IllegalArgumentException("Cannot open image")).use { boundsIn ->
                        BitmapFactory.decodeStream(boundsIn, null, opt)
                    }

                    if (opt.outWidth <= 0 || opt.outHeight <= 0) {
                        throw IllegalArgumentException("Cannot decode image: ${pageItem.displayName}")
                    }

                    val mime = opt.outMimeType
                    val needsConversion = mime != "image/jpeg"

                    val imageData: com.itextpdf.io.image.ImageData
                    var imgWidth = opt.outWidth.toFloat()
                    var imgHeight = opt.outHeight.toFloat()

                    if (needsConversion) {
                        opt.inJustDecodeBounds = false
                        opt.inSampleSize = calcSampleSize(opt.outWidth, opt.outHeight)
                        val bmp = (ctx.contentResolver.openInputStream(uri)
                            ?: throw IllegalArgumentException("Cannot open image")).use { pixelIn ->
                            BitmapFactory.decodeStream(pixelIn, null, opt)
                        } ?: throw IllegalArgumentException("Cannot decode image")
                        try {
                            imgWidth = (bmp.width * opt.inSampleSize).toFloat()
                            imgHeight = (bmp.height * opt.inSampleSize).toFloat()
                            val jpegOut = ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, 90, jpegOut)
                            imageData = ImageDataFactory.createJpeg(jpegOut.toByteArray())
                        } finally {
                            bmp.recycle()
                        }
                    } else {
                        (ctx.contentResolver.openInputStream(uri)
                            ?: throw IllegalArgumentException("Cannot open image")).use { rawIn ->
                            val imgBytes = PdfStore.bytes(rawIn)
                            imageData = if ("image/jpeg" == mime) {
                                ImageDataFactory.createJpeg(imgBytes)
                            } else {
                                ImageDataFactory.create(imgBytes)
                            }
                        }
                    }

                    var pw = imgWidth * 72f / 300f
                    var ph = imgHeight * 72f / 300f
                    if (pw <= 0 || ph <= 0) {
                        pw = 595f
                        ph = 842f
                    }

                    val pageSize = PageSize(pw, ph)
                    val page = pdfDoc.addNewPage(pageSize)
                    if (pageItem.rotation != 0) {
                        var r = pageItem.rotation % 360
                        if (r < 0) r += 360
                        page.put(PdfName.Rotate, PdfNumber(r))
                    }

                    val canvas = PdfCanvas(page)
                    canvas.addImageFittedIntoRectangle(imageData, Rectangle(0f, 0f, pw, ph), false)
                }
            } finally {
                pdfDoc.close()
            }
        }
    }

    private fun calcSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / (sample * 2) > MAX_IMAGE_DIMENSION ||
            height / (sample * 2) > MAX_IMAGE_DIMENSION
        ) {
            sample *= 2
        }
        return sample
    }
}
