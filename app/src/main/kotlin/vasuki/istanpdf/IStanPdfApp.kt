package vasuki.istanpdf

import android.app.Application
import com.itextpdf.kernel.utils.XmlProcessorCreator
import vasuki.istanpdf.di.AppModule
import vasuki.istanpdf.pdf.AndroidXmlParserFactory

class IStanPdfApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppModule.init(this)

        // Register custom XML Parser Factory to fix iText 7/8 "Unknown version 0.0" crash on Android
        XmlProcessorCreator.setXmlParserFactory(AndroidXmlParserFactory())
    }
}
