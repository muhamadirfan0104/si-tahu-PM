package muhamad.irfan.si_tahu.ui.dasar

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.google.firebase.auth.FirebaseAuth
import muhamad.irfan.si_tahu.ui.masuk.AktivitasMasuk
import muhamad.irfan.si_tahu.util.PembantuCetak
import muhamad.irfan.si_tahu.util.PembantuModal

open class FragmenDasar(@Suppress("UNUSED_PARAMETER") layoutRes: Int = 0) : Fragment() {

    private var lastNavigationActionAt: Long = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply { setContent { Surface {} } }
    }

    protected fun showMessage(view: View, message: String) {
        if (!isAdded) return
        Toast.makeText(view.context, message, Toast.LENGTH_LONG).show()
    }

    protected fun currentUserId(): String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    protected fun requireLoginOrRedirect(): Boolean {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) return true
        val ctx = activity ?: return false
        startActivity(Intent(ctx, AktivitasMasuk::class.java))
        ctx.finish()
        return false
    }

    protected fun shouldIgnoreRapidTap(minIntervalMs: Long = 220L): Boolean {
        val now = SystemClock.elapsedRealtime()
        val tooFast = now - lastNavigationActionAt < minIntervalMs
        if (!tooFast) lastNavigationActionAt = now
        return tooFast
    }

    protected fun launchActivitySafely(intent: Intent, finishCurrent: Boolean = false, debounceMs: Long = 220L) {
        if (shouldIgnoreRapidTap(debounceMs)) return
        val hostActivity = activity ?: return
        if (!isAdded || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        runCatching {
            startActivity(intent)
            if (finishCurrent) hostActivity.finish()
        }
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
        monospace: Boolean = true
    ) {
        val safeContext = context ?: return
        PembantuModal.showDetailModal(
            safeContext,
            title,
            message,
            positiveLabel = positiveLabel,
            onPositive = onPositive,
            neutralLabel = neutralLabel,
            onNeutral = onNeutral,
            negativeLabel = negativeLabel,
            onNegative = onNegative,
            monospace = monospace
        )
    }

    protected fun showInputModal(title: String, hint: String, confirmLabel: String = "Simpan", initialValue: String = "", onConfirm: (String) -> Unit) {
        val safeContext = context ?: return
        PembantuModal.showInputModal(safeContext, title, hint, confirmLabel, initialValue, onConfirm)
    }

    protected fun showConfirmationModal(title: String, message: String, confirmLabel: String = "Hapus", onConfirm: () -> Unit) {
        val safeContext = context ?: return
        PembantuModal.showConfirmationModal(safeContext, title, message, confirmLabel, onConfirm)
    }

    protected fun sharePlainText(title: String, text: String) {
        if (!isAdded) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, title))
    }

    protected fun showReceiptModal(title: String, receiptText: String, pdfLabel: String = "Download") {
        val safeContext = context ?: return
        val showStrukActions = pdfLabel.isNotBlank()
        showDetailModal(
            title = title,
            message = receiptText,
            positiveLabel = if (showStrukActions) "Cetak" else "Tutup",
            onPositive = if (showStrukActions) ({ PembantuCetak.printNota(safeContext, title, receiptText) }) else null,
            negativeLabel = "Share",
            onNegative = { PembantuCetak.shareStrukPdf(safeContext, title, receiptText) },
            neutralLabel = pdfLabel.takeIf { it.isNotBlank() },
            onNeutral = if (showStrukActions) ({ PembantuCetak.downloadStrukPdf(safeContext, title, receiptText) }) else null,
            monospace = true
        )
    }
}
