package vasuki.istanpdf.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

class DocumentManager(context: Context) {
    private val context = context.applicationContext

    fun getDisplayName(uri: Uri?): String {
        if (uri == null) return "IStanPdf"
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        val name = cursor.getString(index)
                        val dot = name.lastIndexOf('.')
                        return if (dot > 0) name.substring(0, dot) else name
                    }
                }
                null
            } ?: "IStanPdf"
        } catch (e: Exception) {
            "IStanPdf"
        }
    }
}
