package muhamad.irfan.si_tahu.util

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object PembantuModal {
    fun showDetailModal(
        context: Context,
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
    ): Dialog {
        val dialog = Dialog(context)
        val container = modalContainer(context)

        container.addView(titleView(context, title))

        val body = TextView(context).apply {
            text = message
            textSize = 13f
            setTextColor(Color.rgb(35, 35, 35))
            typeface = if (monospace) Typeface.MONOSPACE else Typeface.SANS_SERIF
            setLineSpacing(2f, 1.0f)
        }
        val scroll = ScrollView(context).apply {
            addView(body)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 360)
            )
        }
        container.addView(scroll)

        container.addView(
            actionsRow(
                context = context,
                primaryLabel = positiveLabel,
                onPrimary = {
                    onPositive?.invoke()
                    dialog.dismiss()
                },
                neutralLabel = neutralLabel,
                onNeutral = {
                    onNeutral?.invoke()
                    dialog.dismiss()
                },
                negativeLabel = negativeLabel,
                onNegative = {
                    onNegative?.invoke()
                    dialog.dismiss()
                }
            )
        )

        dialog.setContentView(container)
        dialog.setOnDismissListener { onClosed?.invoke() }
        dialog.showWide()
        return dialog
    }

    fun showInputModal(
        context: Context,
        title: String,
        hint: String,
        confirmLabel: String = "Simpan",
        initialValue: String = "",
        onConfirm: (String) -> Unit
    ): Dialog {
        val dialog = Dialog(context)
        val container = modalContainer(context)

        container.addView(titleView(context, title))

        val input = EditText(context).apply {
            setText(initialValue)
            this.hint = hint
            minLines = 3
            maxLines = 4
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setSingleLine(false)
            background = roundedStroke(context, Color.WHITE, Color.rgb(205, 205, 205), 14f)
            setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
            textSize = 14f
            setTextColor(Color.rgb(30, 30, 30))
        }
        container.addView(
            input,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val error = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.rgb(190, 40, 40))
            visibility = View.GONE
        }
        container.addView(error)

        container.addView(
            actionsRow(
                context = context,
                primaryLabel = confirmLabel,
                onPrimary = {
                    val trimmed = input.text?.toString()?.trim().orEmpty()
                    if (trimmed.isBlank()) {
                        error.text = "$hint wajib diisi"
                        error.visibility = View.VISIBLE
                    } else {
                        onConfirm(trimmed)
                        dialog.dismiss()
                    }
                },
                negativeLabel = "Tutup",
                onNegative = { dialog.dismiss() }
            )
        )

        dialog.setContentView(container)
        dialog.showWide()
        return dialog
    }

    fun showConfirmationModal(
        context: Context,
        title: String,
        message: String,
        confirmLabel: String = "Hapus",
        onConfirm: () -> Unit,
        cancelLabel: String = "Batal"
    ): Dialog {
        val dialog = Dialog(context)
        val container = modalContainer(context)

        container.addView(titleView(context, title))
        container.addView(TextView(context).apply {
            text = message
            textSize = 14f
            setTextColor(Color.rgb(45, 45, 45))
            setLineSpacing(2f, 1.0f)
        })

        container.addView(
            actionsRow(
                context = context,
                primaryLabel = confirmLabel,
                onPrimary = {
                    onConfirm()
                    dialog.dismiss()
                },
                negativeLabel = cancelLabel,
                onNegative = { dialog.dismiss() }
            )
        )

        dialog.setContentView(container)
        dialog.showWide()
        return dialog
    }

    private fun modalContainer(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 18))
            background = roundedStroke(context, Color.WHITE, Color.TRANSPARENT, 26f)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            dividerDrawable = null
            showDividers = LinearLayout.SHOW_DIVIDER_NONE
        }
    }

    private fun titleView(context: Context, title: String): TextView {
        return TextView(context).apply {
            text = title
            textSize = 20f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(Color.rgb(25, 25, 25))
            setPadding(0, 0, 0, dp(context, 12))
        }
    }

    private fun actionsRow(
        context: Context,
        primaryLabel: String,
        onPrimary: () -> Unit,
        neutralLabel: String? = null,
        onNeutral: (() -> Unit)? = null,
        negativeLabel: String? = null,
        onNegative: (() -> Unit)? = null
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(context, 14), 0, 0)

            if (!negativeLabel.isNullOrBlank()) {
                addView(actionButton(context, negativeLabel, false) { onNegative?.invoke() })
            }
            if (!neutralLabel.isNullOrBlank()) {
                addView(actionButton(context, neutralLabel, false) { onNeutral?.invoke() })
            }
            addView(actionButton(context, primaryLabel, true) { onPrimary() })
        }
    }

    private fun actionButton(
        context: Context,
        label: String,
        primary: Boolean,
        onClick: () -> Unit
    ): Button {
        return Button(context).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            setTextColor(if (primary) Color.WHITE else Color.rgb(45, 45, 45))
            background = if (primary) {
                roundedStroke(context, Color.rgb(199, 154, 61), Color.TRANSPARENT, 14f)
            } else {
                roundedStroke(context, Color.rgb(246, 246, 246), Color.rgb(220, 220, 220), 14f)
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, dp(context, 48), 1f).apply {
                marginStart = dp(context, 8)
            }
        }
    }

    private fun roundedStroke(context: Context, fill: Int, stroke: Int, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, radiusDp.toInt()).toFloat()
            setColor(fill)
            if (stroke != Color.TRANSPARENT) {
                setStroke(dp(context, 1), stroke)
            }
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    private fun Dialog.showWide() {
        show()
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}
