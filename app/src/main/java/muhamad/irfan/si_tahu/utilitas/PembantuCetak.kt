package muhamad.irfan.si_tahu.util

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

object PembantuCetak {
    fun printPlainText(context: Context, jobName: String, text: String) {
        val html = buildString {
            append("<html><body style=\"font-family:monospace;padding:24px;white-space:pre-wrap;color:#293226;background:#ffffff;\">")
            append(text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;"))
            append("</body></html>")
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
