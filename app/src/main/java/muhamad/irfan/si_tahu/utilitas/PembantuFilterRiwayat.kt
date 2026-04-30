package muhamad.irfan.si_tahu.utilitas

import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

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
        val dialog = AlertDialog.Builder(activity).create()
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 20), dp(activity, 18), dp(activity, 20), dp(activity, 14))
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(activity).apply {
            text = "Filter"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(Button(activity).apply {
            text = "Tutup"
            setOnClickListener { dialog.dismiss() }
        })
        root.addView(header)

        if (jumlahFilterAktif > 0) {
            root.addView(TextView(activity).apply {
                text = "$jumlahFilterAktif filter aktif"
                textSize = 13f
                setPadding(0, dp(activity, 4), 0, dp(activity, 10))
            })
        }

        addOptionGroup(
            activity = activity,
            root = root,
            title = kategoriLabel,
            options = kategori,
            selected = kategoriTerpilih,
            dialog = dialog,
            onPick = onKategoriDipilih
        )

        if (!secondaryLabel.isNullOrBlank() && secondaryOptions.isNotEmpty()) {
            addOptionGroup(
                activity = activity,
                root = root,
                title = secondaryLabel,
                options = secondaryOptions,
                selected = secondaryTerpilih,
                dialog = dialog,
                onPick = onSecondaryDipilih
            )
        }

        if (tampilkanTanggal) {
            root.addView(TextView(activity).apply {
                text = "Rentang tanggal"
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(activity, 14), 0, dp(activity, 6))
            })
            root.addView(TextView(activity).apply {
                text = tanggalLabel ?: "Belum dipilih"
                textSize = 14f
                setPadding(0, 0, 0, dp(activity, 8))
            })
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            row.addView(Button(activity).apply {
                text = "Pilih"
                setOnClickListener {
                    dialog.dismiss()
                    onPilihTanggal()
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, dp(activity, 8), 0)
            })
            row.addView(Button(activity).apply {
                text = "Hapus"
                setOnClickListener {
                    onHapusTanggal()
                    dialog.dismiss()
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            root.addView(row)
        }

        root.addView(Button(activity).apply {
            text = "Reset semua"
            setOnClickListener {
                onReset()
                dialog.dismiss()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(activity, 14)
        })

        val scroll = ScrollView(activity).apply { addView(root) }
        dialog.setView(scroll)
        dialog.show()
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun addOptionGroup(
        activity: AppCompatActivity,
        root: LinearLayout,
        title: String,
        options: List<String>,
        selected: String,
        dialog: AlertDialog,
        onPick: (String) -> Unit
    ) {
        root.addView(TextView(activity).apply {
            text = title
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(activity, 14), 0, dp(activity, 6))
        })

        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        options.forEachIndexed { index, label ->
            row.addView(Button(activity).apply {
                text = if (label == selected) "✓ $label" else label
                setOnClickListener {
                    onPick(label)
                    dialog.dismiss()
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index < options.lastIndex) setMargins(0, 0, dp(activity, 8), 0)
            })
        }
        root.addView(row)
    }

    private fun dp(activity: AppCompatActivity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
