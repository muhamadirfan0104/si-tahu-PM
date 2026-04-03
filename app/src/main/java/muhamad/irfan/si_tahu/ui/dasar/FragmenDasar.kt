package muhamad.irfan.si_tahu.ui.base

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import muhamad.irfan.si_tahu.data.RepositoriLokal
import muhamad.irfan.si_tahu.ui.login.AktivitasMasuk
import muhamad.irfan.si_tahu.util.PembantuModal
import muhamad.irfan.si_tahu.util.PembantuCetak

open class FragmenDasar(layoutRes: Int) : Fragment(layoutRes) {

    override fun onCreate(savedInstanceState: Bundle?) {
        context?.applicationContext?.let { RepositoriLokal.init(it) }
        super.onCreate(savedInstanceState)
    }

    protected fun showMessage(view: View, message: String) {
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

    protected fun showDetailModal(
        title: String,
        message: String,
        neutralLabel: String? = null,
        onNeutral: (() -> Unit)? = null,
        negativeLabel: String? = null,
        onNegative: (() -> Unit)? = null,
        monospace: Boolean = true
    ) {
        PembantuModal.showDetailModal(
            context = requireContext(),
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
        PembantuModal.showConfirmationModal(requireContext(), title, message, confirmLabel, onConfirm)
    }

    protected fun sharePlainText(title: String, text: String) {
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
        showDetailModal(
            title = title,
            message = receiptText,
            neutralLabel = "Bagikan",
            onNeutral = { sharePlainText(title, receiptText) },
            negativeLabel = printLabel,
            onNegative = { PembantuCetak.printPlainText(requireContext(), title, receiptText) },
            monospace = true
        )
    }
}