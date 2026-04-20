// FragmenDasar.kt
package muhamad.irfan.si_tahu.ui.dasar

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import muhamad.irfan.si_tahu.ui.masuk.AktivitasMasuk
import muhamad.irfan.si_tahu.util.PembantuCetak
import muhamad.irfan.si_tahu.util.PembantuModal

open class FragmenDasar(layoutRes: Int) : Fragment(layoutRes) {

    private var lastNavigationActionAt: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    protected fun showMessage(view: View, message: String) {
        if (!isAdded) return
        Snackbar.make(view, message, Snackbar.LENGTH_LONG).show()
    }

    protected fun currentUserId(): String {
        return FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    }

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
        if (!tooFast) {
            lastNavigationActionAt = now
        }
        return tooFast
    }

    protected fun launchActivitySafely(
        intent: Intent,
        finishCurrent: Boolean = false,
        debounceMs: Long = 220L
    ) {
        if (shouldIgnoreRapidTap(debounceMs)) return
        val hostActivity = activity ?: return
        if (!isAdded || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return

        runCatching {
            startActivity(intent)
            if (finishCurrent) {
                hostActivity.finish()
            }
        }
    }

    protected fun showDetailModal(
        title: String,
        message: String,
        neutralLabel: String? = null,
        onNeutral: (() -> Unit)? = null,
        negativeLabel: String? = null,
        onNegative: (() -> Unit)? = null,
        monospace: Boolean = true
    ) {
        val safeContext = context ?: return
        PembantuModal.showDetailModal(
            context = safeContext,
            title = title,
            message = message,
            neutralLabel = neutralLabel,
            onNeutral = onNeutral,
            negativeLabel = negativeLabel,
            onNegative = onNegative,
            monospace = monospace
        )
    }

    protected fun showConfirmationModal(
        title: String,
        message: String,
        confirmLabel: String = "Hapus",
        onConfirm: () -> Unit
    ) {
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

    protected fun showReceiptModal(
        title: String,
        receiptText: String,
        printLabel: String = "Cetak Struk"
    ) {
        val safeContext = context ?: return
        showDetailModal(
            title = title,
            message = receiptText,
            neutralLabel = "Bagikan",
            onNeutral = { sharePlainText(title, receiptText) },
            negativeLabel = printLabel,
            onNegative = { PembantuCetak.printPlainText(safeContext, title, receiptText) },
            monospace = true
        )
    }
}
