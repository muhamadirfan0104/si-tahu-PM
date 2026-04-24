package muhamad.irfan.si_tahu.utilitas

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.chip.ChipGroup
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.ui.umum.AdapterBarisUmum
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris
import muhamad.irfan.si_tahu.util.Formatter

object PembantuPilihProduk {

    fun show(
        activity: AppCompatActivity,
        title: String = "Pilih Produk",
        produk: List<ProdukPilihanUi>,
        selectedId: String? = null,
        kategoriOptions: List<String> = listOf("Semua", "Dasar", "Olahan"),
        onDipilih: (ProdukPilihanUi) -> Unit
    ) {
        val dialog = BottomSheetDialog(activity)
        val view = LayoutInflater.from(activity)
            .inflate(R.layout.bottom_sheet_product_picker, null, false)
        dialog.setContentView(view)

        val tvTitle = view.findViewById<TextView>(R.id.tvPickerTitle)
        val etSearch = view.findViewById<TextInputEditText>(R.id.etProductSearch)
        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupProductCategory)
        val rvProduk = view.findViewById<RecyclerView>(R.id.rvProductPicker)
        val tvEmpty = view.findViewById<TextView>(R.id.tvProductEmpty)

        tvTitle.text = title

        var kategoriAktif = kategoriOptions.firstOrNull().orEmpty().ifBlank { "Semua" }
        val adapter = AdapterBarisUmum(
            onItemClick = { item ->
                produk.firstOrNull { it.id == item.id }?.let {
                    onDipilih(it)
                    dialog.dismiss()
                }
            }
        )
        rvProduk.layoutManager = LinearLayoutManager(activity)
        rvProduk.adapter = adapter

        fun render() {
            val keyword = etSearch.text?.toString().orEmpty().trim().lowercase()
            val filtered = produk.filter { item ->
                val cocokKeyword = keyword.isBlank() ||
                        item.namaProduk.lowercase().contains(keyword) ||
                        item.jenisProduk.lowercase().contains(keyword)
                val cocokKategori = when (kategoriAktif.lowercase()) {
                    "dasar" -> item.jenisProduk.equals("DASAR", true)
                    "olahan" -> item.jenisProduk.equals("OLAHAN", true)
                    "siap dijual" -> item.aktifDijual && item.stokSaatIni > 0L
                    "habis" -> item.stokSaatIni <= 0L
                    else -> true
                }
                cocokKeyword && cocokKategori
            }

            tvEmpty.isVisible = filtered.isEmpty()
            rvProduk.isVisible = filtered.isNotEmpty()
            adapter.submitList(
                filtered.map { item ->
                    ItemBaris(
                        id = item.id,
                        title = item.namaProduk,
                        subtitle = item.subtitleUntukPicker(),
                        badge = item.jenisProduk.ifBlank { "Produk" },
                        amount = item.stokLabel(),
                        priceStatus = if (item.aktifDijual) "Aktif" else "Nonaktif",
                        parameterStatus = if (item.id == selectedId) "Dipilih" else item.infoTambahan,
                        tone = when {
                            item.id == selectedId -> WarnaBaris.GREEN
                            item.jenisProduk.equals("DASAR", true) -> WarnaBaris.GOLD
                            item.jenisProduk.equals("OLAHAN", true) -> WarnaBaris.BLUE
                            else -> WarnaBaris.DEFAULT
                        },
                        priceTone = if (item.aktifDijual) WarnaBaris.GREEN else WarnaBaris.ORANGE,
                        parameterTone = if (item.id == selectedId) WarnaBaris.GREEN else WarnaBaris.BLUE
                    )
                }
            )
        }

        chipGroup.removeAllViews()
        kategoriOptions.ifEmpty { listOf("Semua") }.forEachIndexed { index, label ->
            val chip = buildChip(activity, label, index == 0) {
                kategoriAktif = label
                render()
            }
            chipGroup.addView(chip)
            if (index == 0) chipGroup.check(chip.id)
        }

        etSearch.addTextChangedListener { render() }
        render()
        dialog.show()
    }

    private fun buildChip(
        activity: AppCompatActivity,
        label: String,
        checked: Boolean,
        onClick: () -> Unit
    ): Chip {
        val chip = Chip(activity)
        chip.id = View.generateViewId()
        chip.text = label
        chip.isCheckable = true
        chip.isChecked = checked
        chip.isClickable = true
        chip.checkedIcon = null
        chip.isCloseIconVisible = false
        chip.minHeight = dpToPx(activity, 44)
        chip.setPadding(dpToPx(activity, 10), dpToPx(activity, 6), dpToPx(activity, 10), dpToPx(activity, 6))

        chip.chipBackgroundColor = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(0xFFE7EFDC.toInt(), 0xFFFFFFFF.toInt())
        )
        chip.chipStrokeColor = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(0xFF5F7D49.toInt(), 0xFFDDD5C7.toInt())
        )
        chip.chipStrokeWidth = dpToPx(activity, 1).toFloat()
        chip.setTextColor(0xFF293226.toInt())
        chip.textSize = 14f
        chip.setOnClickListener { onClick() }
        return chip
    }

    private fun ProdukPilihanUi.subtitleUntukPicker(): String {
        val jenis = jenisProduk.ifBlank { "Produk" }
        val status = if (aktifDijual) "Aktif" else "Nonaktif"
        return "$jenis • $status • Stok ${stokLabel()}"
    }

    private fun ProdukPilihanUi.stokLabel(): String {
        return "${Formatter.ribuan(stokSaatIni)} ${satuan.ifBlank { "pcs" }}"
    }

    private fun dpToPx(activity: AppCompatActivity, dp: Int): Int {
        return (dp * activity.resources.displayMetrics.density).toInt()
    }
}

data class ProdukPilihanUi(
    val id: String,
    val namaProduk: String,
    val jenisProduk: String,
    val stokSaatIni: Long,
    val satuan: String,
    val aktifDijual: Boolean,
    val infoTambahan: String = ""
) {
    fun ringkasanSingkat(): String {
        val status = if (aktifDijual) "Aktif" else "Nonaktif"
        val jenis = jenisProduk.ifBlank { "Produk" }
        val stok = "${Formatter.ribuan(stokSaatIni)} ${satuan.ifBlank { "pcs" }}"
        return listOf(jenis, status, "Stok $stok", infoTambahan)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
    }
}
