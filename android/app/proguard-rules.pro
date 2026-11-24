# Keep the JavaScript bridge methods called from the web page.
-keepclassmembers class io.github.sreekumartp.kissai.AtlasBridge {
    @android.webkit.JavascriptInterface <methods>;
}
