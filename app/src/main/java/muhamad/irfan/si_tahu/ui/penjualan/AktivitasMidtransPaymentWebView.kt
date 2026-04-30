package muhamad.irfan.si_tahu.ui.penjualan

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import muhamad.irfan.si_tahu.databinding.ActivityMidtransPaymentWebviewBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.util.Formatter

class AktivitasMidtransPaymentWebView : AktivitasDasar() {

    private lateinit var binding: ActivityMidtransPaymentWebviewBinding
    private var paymentUrl: String = ""
    private var orderId: String = ""
    private var total: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        binding = ActivityMidtransPaymentWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        paymentUrl = intent.getStringExtra(EXTRA_PAYMENT_URL).orEmpty()
        orderId = intent.getStringExtra(EXTRA_ORDER_ID).orEmpty()
        total = intent.getLongExtra(EXTRA_TOTAL, 0L)

        bindToolbar(
            toolbar = binding.toolbar,
            title = "Pembayaran Midtrans",
            subtitle = if (orderId.isBlank()) null else orderId,
            showBack = true
        )

        if (paymentUrl.isBlank()) {
            showMessage("Payment URL Midtrans kosong")
            finish()
            return
        }

        setupInstruction()
        setupWebView()
        setupActions()
        binding.webPayment.loadUrl(paymentUrl)
    }

    private fun setupInstruction() {
        binding.tvPaymentTitle.text = "Selesaikan pembayaran"
        binding.tvPaymentSubtitle.text = buildString {
            append("Total: ")
            append(Formatter.currency(total))
            if (orderId.isNotBlank()) append("\nOrder ID: ").append(orderId)
            append("\nSetelah pelanggan membayar, tekan Cek Status Pembayaran.")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webPayment.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            builtInZoomControls = false
            displayZoomControls = false
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
        }

        binding.webPayment.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressLoading.progress = newProgress
                binding.progressLoading.isVisible = newProgress in 1..99
            }
        }

        binding.webPayment.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.progressLoading.isVisible = true
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressLoading.isVisible = false
                super.onPageFinished(view, url)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return handleNavigation(request?.url?.toString().orEmpty())
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return handleNavigation(url.orEmpty())
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webPayment.canGoBack()) {
                    binding.webPayment.goBack()
                } else {
                    konfirmasiKeluar()
                }
            }
        })
    }

    private fun setupActions() {
        binding.btnCheckStatus.setOnClickListener {
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(EXTRA_ORDER_ID, orderId)
            )
            finish()
        }

        binding.btnBackCheckout.setOnClickListener {
            konfirmasiKeluar()
        }
    }

    private fun handleNavigation(url: String): Boolean {
        if (url.isBlank()) return false

        val lowerUrl = url.lowercase()
        if (lowerUrl.startsWith("http://") || lowerUrl.startsWith("https://")) {
            return false
        }

        if (lowerUrl.startsWith("intent://")) {
            return bukaIntentUrl(url)
        }

        return bukaAplikasiLain(url)
    }

    private fun bukaIntentUrl(url: String): Boolean {
        return runCatching {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            try {
                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                if (fallbackUrl.isNullOrBlank()) {
                    throw ActivityNotFoundException()
                }
                binding.webPayment.loadUrl(fallbackUrl)
            }
            true
        }.getOrElse {
            showMessage("Aplikasi pembayaran tidak ditemukan")
            true
        }
    }

    private fun bukaAplikasiLain(url: String): Boolean {
        return runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        }.getOrElse {
            showMessage("Tidak bisa membuka aplikasi pembayaran")
            true
        }
    }

    private fun konfirmasiKeluar() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Kembali ke checkout?")
            .setMessage("Pembayaran tidak otomatis disimpan. Setelah pelanggan selesai bayar, tekan Cek Status Pembayaran dari checkout.")
            .setNegativeButton("Tetap di sini", null)
            .setPositiveButton("Kembali") { _, _ -> finish() }
            .show()
    }

    override fun onDestroy() {
        if (this::binding.isInitialized) {
            binding.webPayment.apply {
                stopLoading()
                clearHistory()
                removeAllViews()
                destroy()
            }
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PAYMENT_URL = "payment_url"
        const val EXTRA_ORDER_ID = "order_id"
        const val EXTRA_TOTAL = "total"
    }
}
