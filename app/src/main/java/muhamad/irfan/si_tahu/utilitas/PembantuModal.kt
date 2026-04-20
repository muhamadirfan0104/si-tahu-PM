package muhamad.irfan.si_tahu.util

import android.content.Context
import android.graphics.Typeface
import android.text.method.LinkMovementMethod
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object PembantuModal {
    fun showDetailModal(
        context: Context,
        title: String,
        message: String,
        positiveLabel: String = "Tutup",
        neutralLabel: String? = null,
        onNeutral: (() -> Unit)? = null,
        negativeLabel: String? = null,
        onNegative: (() -> Unit)? = null,
        monospace: Boolean = true
    ): AlertDialog {
        val density = context.resources.displayMetrics.density
        val textView = TextView(context).apply {
            text = message
            textSize = 14f
            setLineSpacing(0f, 1.35f)
            setPadding((20 * density).toInt(), (18 * density).toInt(), (20 * density).toInt(), (18 * density).toInt())
            setTextColor(context.getColorCompat(muhamad.irfan.si_tahu.R.color.text_primary))
            typeface = if (monospace) Typeface.MONOSPACE else Typeface.SANS_SERIF
            movementMethod = LinkMovementMethod.getInstance()
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(context).apply { addView(textView) }
        return MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton(positiveLabel, null)
            .apply {
                if (neutralLabel != null) setNeutralButton(neutralLabel) { _, _ -> onNeutral?.invoke() }
                if (negativeLabel != null) setNegativeButton(negativeLabel) { _, _ -> onNegative?.invoke() }
            }
            .show()
    }

    fun showConfirmationModal(
        context: Context,
        title: String,
        message: String,
        confirmLabel: String = "Hapus",
        onConfirm: () -> Unit,
        cancelLabel: String = "Batal"
    ): AlertDialog {
        return MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(cancelLabel, null)
            .setPositiveButton(confirmLabel) { _, _ -> onConfirm() }
            .show()
    }

    private fun Context.getColorCompat(colorRes: Int): Int = androidx.core.content.ContextCompat.getColor(this, colorRes)
}
