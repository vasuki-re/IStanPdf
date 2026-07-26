package vasuki.istanpdf

import android.app.Application
import vasuki.istanpdf.di.AppModule

class IStanPdfApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppModule.init(this)
    }
}
