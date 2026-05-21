package muhamad.irfan.si_tahu.util

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
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
        val palette = modalPalette(context)
        val container = modalContainer(context, palette)

        container.addView(headerView(context, title, palette.primary, "i", palette))

        val body = TextView(context).apply {
            text = message
            textSize = 13f
            setTextColor(palette.text)
            typeface = if (monospace) Typeface.MONOSPACE else Typeface.SANS_SERIF
            setLineSpacing(2f, 1.0f)
            setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14))
            background = roundedStroke(context, palette.inputFill, palette.border, 18f)
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
                primaryColor = palette.primary,
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
                },
                palette = palette
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
        val palette = modalPalette(context)
        val isDanger = isDestructiveAction(confirmLabel) || title.contains("batal", ignoreCase = true)
        val accent = if (isDanger) palette.danger else palette.primary
        val container = modalContainer(context, palette)

        container.addView(headerView(context, title, accent, if (isDanger) "!" else "?", palette))

        val helper = TextView(context).apply {
            text = hint
            textSize = 13f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(palette.text)
            setPadding(0, 0, 0, dp(context, 8))
        }
        container.addView(helper)

        val input = EditText(context).apply {
            setText(initialValue)
            this.hint = hint
            minLines = 3
            maxLines = 4
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setSingleLine(false)
            background = roundedStroke(context, palette.inputFill, palette.border, 16f)
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
            textSize = 14f
            setTextColor(palette.text)
            setHintTextColor(palette.muted)
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
            setTextColor(palette.danger)
            visibility = View.GONE
            setPadding(dp(context, 2), dp(context, 8), dp(context, 2), 0)
        }
        container.addView(error)

        container.addView(
            actionsRow(
                context = context,
                primaryLabel = confirmLabel,
                primaryColor = accent,
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
                negativeLabel = "Batal",
                onNegative = { dialog.dismiss() },
                palette = palette
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
        val palette = modalPalette(context)
        val isDanger = isDestructiveAction(confirmLabel) || title.contains("hapus", ignoreCase = true) || title.contains("batal", ignoreCase = true)
        val accent = if (isDanger) palette.danger else palette.primary
        val container = modalContainer(context, palette)

        container.addView(headerView(context, title, accent, if (isDanger) "!" else "?", palette))
        container.addView(TextView(context).apply {
            text = message
            textSize = 14f
            setTextColor(palette.text)
            setLineSpacing(3f, 1.0f)
            setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14))
            background = roundedStroke(context, palette.inputFill, palette.border, 18f)
        })

        container.addView(
            actionsRow(
                context = context,
                primaryLabel = confirmLabel,
                primaryColor = accent,
                onPrimary = {
                    onConfirm()
                    dialog.dismiss()
                },
                negativeLabel = cancelLabel,
                onNegative = { dialog.dismiss() },
                palette = palette
            )
        )

        dialog.setContentView(container)
        dialog.showWide()
        return dialog
    }

    private data class Palette(
        val surface: Int,
        val inputFill: Int,
        val border: Int,
        val text: Int,
        val muted: Int,
        val primary: Int,
        val danger: Int,
        val secondaryButton: Int
    )

    private fun modalPalette(context: Context): Palette {
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return if (isNight) {
            Palette(
                surface = Color.rgb(31, 41, 55),
                inputFill = Color.rgb(17, 24, 39),
                border = Color.rgb(55, 65, 81),
                text = Color.rgb(249, 250, 251),
                muted = Color.rgb(156, 163, 175),
                primary = Color.rgb(59, 130, 246),
                danger = Color.rgb(239, 68, 68),
                secondaryButton = Color.rgb(55, 65, 81)
            )
        } else {
            Palette(
                surface = Color.WHITE,
                inputFill = Color.rgb(248, 250, 252),
                border = Color.rgb(226, 232, 240),
                text = Color.rgb(17, 24, 39),
                muted = Color.rgb(107, 114, 128),
                primary = Color.rgb(37, 99, 235),
                danger = Color.rgb(220, 38, 38),
                secondaryButton = Color.rgb(243, 244, 246)
            )
        }
    }

    private fun modalContainer(context: Context, palette: Palette): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 20))
            background = roundedStroke(context, palette.surface, palette.border, 28f)
            elevation = dp(context, 10).toFloat()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            dividerDrawable = null
            showDividers = LinearLayout.SHOW_DIVIDER_NONE
        }
    }

    private fun headerView(context: Context, title: String, accentColor: Int, iconText: String, palette: Palette): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(context, 16))

            addView(TextView(context).apply {
                text = iconText
                gravity = Gravity.CENTER
                textSize = 18f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(accentColor)
                background = roundedStroke(context, withAlpha(accentColor, 28), Color.TRANSPARENT, 16f)
            }, LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)))

            addView(TextView(context).apply {
                text = title
                textSize = 20f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(palette.text)
                setPadding(dp(context, 12), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun actionsRow(
        context: Context,
        primaryLabel: String,
        primaryColor: Int,
        onPrimary: () -> Unit,
        neutralLabel: String? = null,
        onNeutral: (() -> Unit)? = null,
        negativeLabel: String? = null,
        onNegative: (() -> Unit)? = null,
        palette: Palette
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(context, 16), 0, 0)

            if (!negativeLabel.isNullOrBlank()) {
                addView(actionButton(context, negativeLabel, false, primaryColor, palette) { onNegative?.invoke() })
            }
            if (!neutralLabel.isNullOrBlank()) {
                addView(actionButton(context, neutralLabel, false, primaryColor, palette) { onNeutral?.invoke() })
            }
            addView(actionButton(context, primaryLabel, true, primaryColor, palette) { onPrimary() })
        }
    }

    private fun actionButton(
        context: Context,
        label: String,
        primary: Boolean,
        primaryColor: Int,
        palette: Palette,
        onClick: () -> Unit
    ): Button {
        return Button(context).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(if (primary) Color.WHITE else palette.text)
            background = if (primary) {
                roundedStroke(context, primaryColor, Color.TRANSPARENT, 16f)
            } else {
                roundedStroke(context, palette.secondaryButton, palette.border, 16f)
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, dp(context, 50), 1f).apply {
                marginStart = dp(context, 8)
            }
        }
    }

    private fun isDestructiveAction(label: String): Boolean {
        return label.contains("hapus", ignoreCase = true) ||
            label.contains("batalkan", ignoreCase = true) ||
            label.contains("delete", ignoreCase = true) ||
            label.contains("batal", ignoreCase = true)
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
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
