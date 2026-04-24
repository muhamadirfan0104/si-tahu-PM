package muhamad.irfan.si_tahu.utilitas

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import muhamad.irfan.si_tahu.R

object PembantuFilterRiwayat {

    fun show(
        activity: AppCompatActivity,
        kategori: List<String>,
        kategoriTerpilih: String,
        tanggalLabel: String?,
        jumlahFilterAktif: Int,
        onKategoriDipilih: (String) -> Unit,
        onPilihTanggal: () -> Unit,
        onHapusTanggal: () -> Unit,
        onReset: () -> Unit,
        kategoriLabel: String = "Kategori",
        secondaryLabel: String? = null,
        secondaryOptions: List<String> = emptyList(),
        secondaryTerpilih: String = "",
        onSecondaryDipilih: (String) -> Unit = {},
        tampilkanTanggal: Boolean = true
    ) {
        val dialog = BottomSheetDialog(activity)
        val view = LayoutInflater.from(activity)
            .inflate(R.layout.bottom_sheet_list_filters, null, false)
        dialog.setContentView(view)

        val tvSheetActiveCount = view.findViewById<TextView>(R.id.tvSheetActiveCount)
        val tvResetAll = view.findViewById<TextView>(R.id.tvResetAll)
        val tvCategoryLabel = view.findViewById<TextView>(R.id.tvCategoryLabel)
        val chipGroupCategory = view.findViewById<ChipGroup>(R.id.chipGroupCategory)
        val tvSecondaryFilterLabel = view.findViewById<TextView>(R.id.tvSecondaryFilterLabel)
        val chipGroupSecondaryFilter = view.findViewById<ChipGroup>(R.id.chipGroupSecondaryFilter)
        val tvDateRangeLabel = view.findViewById<TextView>(R.id.tvDateRangeLabel)
        val cardDateRange = view.findViewById<MaterialCardView>(R.id.cardDateRange)
        val tvDateRangeValue = view.findViewById<TextView>(R.id.tvDateRangeValue)
        val ivClearDateRange = view.findViewById<ImageView>(R.id.ivClearDateRange)

        if (jumlahFilterAktif > 0) {
            tvSheetActiveCount.visibility = View.VISIBLE
            tvSheetActiveCount.text = if (jumlahFilterAktif > 9) "9+" else jumlahFilterAktif.toString()
        } else {
            tvSheetActiveCount.visibility = View.GONE
        }

        tvResetAll.setOnClickListener {
            onReset()
            dialog.dismiss()
        }

        tvCategoryLabel.text = kategoriLabel
        chipGroupCategory.removeAllViews()
        kategori.forEach { label ->
            val chip = buildFilterChip(activity, label, label == kategoriTerpilih) {
                onKategoriDipilih(label)
                dialog.dismiss()
            }
            chipGroupCategory.addView(chip)
        }

        if (!secondaryLabel.isNullOrBlank() && secondaryOptions.isNotEmpty()) {
            tvSecondaryFilterLabel.visibility = View.VISIBLE
            chipGroupSecondaryFilter.visibility = View.VISIBLE
            tvSecondaryFilterLabel.text = secondaryLabel
            chipGroupSecondaryFilter.removeAllViews()
            secondaryOptions.forEach { label ->
                val chip = buildFilterChip(activity, label, label == secondaryTerpilih) {
                    onSecondaryDipilih(label)
                    dialog.dismiss()
                }
                chipGroupSecondaryFilter.addView(chip)
            }
        } else {
            tvSecondaryFilterLabel.visibility = View.GONE
            chipGroupSecondaryFilter.visibility = View.GONE
        }

        if (tampilkanTanggal) {
            tvDateRangeLabel.visibility = View.VISIBLE
            cardDateRange.visibility = View.VISIBLE
            val hasDateRange = !tanggalLabel.isNullOrBlank()
            tvDateRangeValue.text = tanggalLabel ?: "Pilih rentang tanggal"
            tvDateRangeValue.setTextColor(
                if (hasDateRange) 0xFF293226.toInt() else 0xFF8A8577.toInt()
            )
            ivClearDateRange.visibility = if (hasDateRange) View.VISIBLE else View.GONE

            cardDateRange.setOnClickListener {
                dialog.dismiss()
                onPilihTanggal()
            }

            ivClearDateRange.setOnClickListener {
                onHapusTanggal()
                dialog.dismiss()
            }
        } else {
            tvDateRangeLabel.visibility = View.GONE
            cardDateRange.visibility = View.GONE
        }

        dialog.show()
    }

    private fun buildFilterChip(
        activity: AppCompatActivity,
        text: String,
        checked: Boolean,
        onClick: () -> Unit
    ): Chip {
        val chip = Chip(activity)
        chip.text = text
        chip.isCheckable = true
        chip.isChecked = checked
        chip.isClickable = true
        chip.isCloseIconVisible = false
        chip.checkedIcon = null
        chip.minHeight = dpToPx(activity, 48)
        chip.setPadding(dpToPx(activity, 10), dpToPx(activity, 6), dpToPx(activity, 10), dpToPx(activity, 6))

        val bgColors = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(0xFFE7EFDC.toInt(), 0xFFFFFFFF.toInt())
        )
        val strokeColors = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(0xFF5F7D49.toInt(), 0xFFDDD5C7.toInt())
        )
        val textColors = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(0xFF293226.toInt(), 0xFF293226.toInt())
        )

        chip.chipBackgroundColor = bgColors
        chip.chipStrokeColor = strokeColors
        chip.chipStrokeWidth = dpToPx(activity, 1).toFloat()
        chip.setTextColor(textColors)
        chip.textSize = 15f
        chip.setOnClickListener { onClick() }
        return chip
    }

    private fun dpToPx(activity: AppCompatActivity, dp: Int): Int {
        return (dp * activity.resources.displayMetrics.density).toInt()
    }
}
