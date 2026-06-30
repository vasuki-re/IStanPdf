package vasuki.istanpdf.pdf;


public interface PdfProgressCallback {
  
  
    void onProgress(int current, int total);
}
