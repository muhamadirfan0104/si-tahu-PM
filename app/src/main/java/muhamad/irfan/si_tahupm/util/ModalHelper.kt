package muhamad.irfan.si_tahupm.util

import android.content.Context
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object ModalHelper {
    fun showDetailModal(
        context: Context,
        title: String,
        message: String,
        positiveLabel: String = "Tutup"
    ): AlertDialog {
        val textView = TextView(context).apply {
            text = message
            textSize = 14f
            setLineSpacing(0f, 1.25f)
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        val scroll = ScrollView(context).apply { addView(textView) }
        return MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton(positiveLabel, null)
            .show()
    }
}
