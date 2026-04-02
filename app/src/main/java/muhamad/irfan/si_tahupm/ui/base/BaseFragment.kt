package muhamad.irfan.si_tahupm.ui.base

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.data.UserRole
import muhamad.irfan.si_tahupm.util.ModalHelper
import muhamad.irfan.si_tahupm.util.PrintHelper

open class BaseFragment(layoutRes: Int) : Fragment(layoutRes) {
    override fun onCreate(savedInstanceState: Bundle?) {
        DemoRepository.init(requireContext().applicationContext)
        super.onCreate(savedInstanceState)
    }

    protected fun showMessage(view: View, message: String) {
        Snackbar.make(view, message, Snackbar.LENGTH_LONG).show()
    }

    protected fun currentUserId(): String {
        return DemoRepository.sessionUser()?.id ?: DemoRepository.loginAs(UserRole.ADMIN).id
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
        ModalHelper.showDetailModal(
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

    protected fun showConfirmationModal(title: String, message: String, confirmLabel: String = "Hapus", onConfirm: () -> Unit) {
        ModalHelper.showConfirmationModal(requireContext(), title, message, confirmLabel, onConfirm)
    }

    protected fun sharePlainText(title: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, title))
    }

    protected fun showReceiptModal(title: String, receiptText: String, printLabel: String = "Cetak Struk") {
        showDetailModal(
            title = title,
            message = receiptText,
            neutralLabel = "Bagikan",
            onNeutral = { sharePlainText(title, receiptText) },
            negativeLabel = printLabel,
            onNegative = { PrintHelper.printPlainText(requireContext(), title, receiptText) },
            monospace = true
        )
    }
}
