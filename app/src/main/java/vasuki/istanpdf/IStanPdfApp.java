package vasuki.istanpdf;

import android.app.Application;

import vasuki.istanpdf.di.AppModule;

public class IStanPdfApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppModule.init(this);
    }
}