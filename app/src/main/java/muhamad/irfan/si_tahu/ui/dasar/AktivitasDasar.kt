package muhamad.irfan.si_tahu.ui.dasar

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import com.google.firebase.auth.FirebaseAuth
import muhamad.irfan.si_tahu.databinding.ComposeToolbarState
import muhamad.irfan.si_tahu.ui.masuk.AktivitasMasuk
import muhamad.irfan.si_tahu.util.PembantuCetak
import muhamad.irfan.si_tahu.util.PembantuModal
import java.io.File

open class AktivitasDasar : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // KITA HAPUS setDefaultNightMode AGAR APLIKASI BISA MENGIKUTI HP
        super.onCreate(savedInstanceState)

        // Membiarkan konten aplikasi digambar hingga ke ujung layar (edge-to-edge)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // KITA HAPUS pewarnaan paksa statusBarColor dan navigationBarColor
        // KITA HAPUS pemaksaan ikon hitam (isAppearanceLightStatusBars)
        // Sekarang warnanya akan diatur otomatis oleh Theme XML (DayNight) dan Compose!
    }

    protected fun bindToolbar(
        toolbar: ComposeToolbarState,
        title: String,
        subtitle: String? = null,
        showBack: Boolean = true
    ) {
        toolbar.title = title
        toolbar.subtitle = subtitle
        toolbar.showBack = showBack
        toolbar.onBack = { if (showBack) onBackPressedDispatcher.onBackPressed() }
    }

    protected fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    protected fun currentUserId(): String {
        return FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    }

    protected fun requireLoginOrRedirect(): Boolean {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) return true

        startActivity(Intent(this, AktivitasMasuk::class.java))
        finish()
        return false
    }

    protected fun showDetailModal(
        title: String,
        message: String,
        positiveLabel: String = "Tutup",
        onPositive: (() -> Unit)? = null,
        neutralLabel: String? = null,
        onNeutral: (() -> Unit)? = null,
        negativeLabel: String? = null,
        onNegative: (() -> Unit)? = null,
        monospace: Boolean = true,
        onClosed: (() -> Unit)? = null
    ) {
        PembantuModal.showDetailModal(
            context = this,
            title = title,
            message = message,
            positiveLabel = positiveLabel,
            onPositive = onPositive,
            neutralLabel = neutralLabel,
            onNeutral = onNeutral,
            negativeLabel = negativeLabel,
            onNegative = onNegative,
            monospace = monospace,
            onClosed = onClosed
        )
    }

    protected fun showInputModal(
        title: String,
        hint: String,
        confirmLabel: String = "Simpan",
        initialValue: String = "",
        onConfirm: (String) -> Unit
    ) {
        PembantuModal.showInputModal(
            context = this,
            title = title,
            hint = hint,
            confirmLabel = confirmLabel,
            initialValue = initialValue,
            onConfirm = onConfirm
        )
    }

    protected fun showConfirmationModal(
        title: String,
        message: String,
        confirmLabel: String = "Hapus",
        onConfirm: () -> Unit
    ) {
        PembantuModal.showConfirmationModal(this, title, message, confirmLabel, onConfirm)
    }

    protected fun showReceiptModal(
        title: String,
        receiptText: String,
        pdfLabel: String = "Download",
        onClosed: (() -> Unit)? = null
    ) {
        if (isFinishing || isDestroyed) return
        runCatching {
            val showStrukActions = pdfLabel.isNotBlank()
            showDetailModal(
                title = title,
                message = receiptText,
                positiveLabel = if (showStrukActions) "Cetak" else "Tutup",
                onPositive = if (showStrukActions) ({
                    runCatching { PembantuCetak.printNota(this, title, receiptText) }
                        .onFailure { showMessage(it.message ?: "Gagal mencetak nota") }
                }) else null,
                negativeLabel = "Share",
                onNegative = {
                    runCatching { PembantuCetak.shareStrukPdf(this, title, receiptText) }
                        .onFailure { showMessage(it.message ?: "Gagal membagikan struk") }
                },
                neutralLabel = pdfLabel.takeIf { it.isNotBlank() },
                onNeutral = if (showStrukActions) ({
                    runCatching { PembantuCetak.downloadStrukPdf(this, title, receiptText) }
                        .onFailure { showMessage(it.message ?: "Gagal download struk") }
                }) else null,
                monospace = true,
                onClosed = onClosed
            )
        }.onFailure {
            showMessage(it.message ?: "Gagal menampilkan detail")
            onClosed?.invoke()
        }
    }

    protected fun sharePlainText(title: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, title))
    }

    protected fun shareCacheFile(title: String, fileName: String, mimeType: String, content: String) {
        val file = File(cacheDir, fileName)
        file.writeText(content)
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, title))
    }
}