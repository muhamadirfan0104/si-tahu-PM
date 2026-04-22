package muhamad.irfan.si_tahu.util

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

object PembantuCetak {
    fun printPlainText(context: Context, jobName: String, text: String) {
        val safeText = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br>")

        val html = buildString {
            append("<html><body style=\"margin:0;padding:32px;background:#f6f1e7;font-family:monospace;color:#223022;\">")
            append("<div style=\"max-width:720px;margin:0 auto;background:#ffffff;border:1px solid #e6ddcf;border-radius:18px;padding:28px;box-shadow:0 6px 18px rgba(0,0,0,0.05);white-space:normal;line-height:1.6;\">")
            append("<div style=\"font-size:12px;letter-spacing:2px;color:#8a7c65;margin-bottom:12px;\">NOTA PENJUALAN</div>")
            append("<div style=\"white-space:pre-wrap;font-size:13px;\">")
            append(safeText)
            append("</div></div></body></html>")
        }
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                printManager.print(
                    jobName,
                    view.createPrintDocumentAdapter(jobName),
                    PrintAttributes.Builder().build()
                )
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
}
