package muhamad.irfan.si_tahu.ui.penjualan

import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import muhamad.irfan.si_tahu.data.HargaKanal
import muhamad.irfan.si_tahu.data.ItemDraftRekap
import muhamad.irfan.si_tahu.data.Produk
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.databinding.ActivityMarketRecapBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.umum.AdapterBarisUmum
import muhamad.irfan.si_tahu.util.AdapterSpinner
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.InputAngka
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.PembantuPilihTanggalWaktu
import muhamad.irfan.si_tahu.util.WarnaBaris
import muhamad.irfan.si_tahu.utilitas.PembantuPilihProduk
import muhamad.irfan.si_tahu.utilitas.ProdukPilihanUi

class AktivitasRekapPasar : AktivitasDasar() {

    private lateinit var binding: ActivityMarketRecapBinding
    private val itemAdapter = AdapterBarisUmum(onItemClick = {}, onActionClick = ::showItemMenu)
    private lateinit var draftBottomSheetBehavior: BottomSheetBehavior<View>

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private var products: List<Produk> = emptyList()
    private val draftItems = mutableListOf<ItemDraftRekap>()
    private var opsiHargaAktif: List<OpsiHargaRekap> = emptyList()
    private var selectedProductIdAktif: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        binding = ActivityMarketRecapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Rekap Penjualan Pasar", "Catat penjualan di luar kasir")

        setupView()
        loadProductsBySelectedDate()
    }

    private fun setupView() {
        binding.etDate.setText(Formatter.currentDateOnly())
        binding.etTime.setText(Formatter.currentTimeOnly())

        binding.etDate.setOnClickListener {
            PembantuPilihTanggalWaktu.showDatePicker(
                this,
                binding.etDate.text?.toString()
            ) { selectedDate ->
                binding.etDate.setText(selectedDate)

                draftItems.clear()
                binding.etQty.setText("")
                refreshDraft()

                loadProductsBySelectedDate()
            }
        }

        binding.etTime.setOnClickListener {
            val previewDateTime =
                "${binding.etDate.text?.toString().orEmpty()}T${binding.etTime.text?.toString().orEmpty()}:00"
            PembantuPilihTanggalWaktu.showTimePicker(this, previewDateTime) { selectedTime ->
                binding.etTime.setText(selectedTime)
            }
        }

        binding.rvItems.layoutManager = LinearLayoutManager(this)
        binding.rvItems.adapter = itemAdapter
        binding.rvItems.isNestedScrollingEnabled = true
        InputAngka.pasang(binding.etQty)

        binding.spPrice.adapter = AdapterSpinner.stringAdapter(this, listOf("Belum ada harga aktif"))
        binding.cardProductPicker.setOnClickListener { showProductPicker() }

        binding.spPrice.onItemSelectedListener = RecapSpinnerListener {
            updateSelectedPriceInfo()
        }

        binding.btnAddItem.setOnClickListener { addItem() }
        binding.btnSaveRecap.setOnClickListener { saveRecap() }

        draftBottomSheetBehavior = BottomSheetBehavior.from(binding.sheetDraft)
        binding.sheetDraft.post {
            draftBottomSheetBehavior.peekHeight = dpToPx(220)
            draftBottomSheetBehavior.isHideable = false
            draftBottomSheetBehavior.skipCollapsed = false
            draftBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        refreshDraft()
        updateProductSelector()
        updateSelectedPriceInfo()
    }

    private fun loadProductsBySelectedDate() {
        val selectedDate = binding.etDate.text?.toString().orEmpty()
            .ifBlank { Formatter.currentDateOnly() }

        lifecycleScope.launch {
            runCatching {
                val produksiSnapshot = firestore.collection("CatatanProduksi")
                    .whereEqualTo("kunciTanggal", selectedDate)
                    .get()
                    .await()

                val producedProductIds = produksiSnapshot.documents
                    .mapNotNull { it.getString("idProdukHasil") }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .toSet()

                RepositoriFirebaseUtama.muatProdukAktif()
                    .filter { product ->
                        val stokLayakJual = product.safeStock + product.nearExpiredStock
                        val punyaHargaAktif = product.channels.any { it.active && it.price > 0L }
                        punyaHargaAktif && stokLayakJual > 0
                    }
                    .sortedWith(
                        compareByDescending<Produk> { it.id in producedProductIds }
                            .thenBy { it.name.lowercase() }
                    )
            }.onSuccess { result ->
                products = result
                selectedProductIdAktif = products.firstOrNull { it.id == selectedProductIdAktif }?.id
                    ?: products.firstOrNull()?.id
                updateProductSelector()
                refreshPriceOptions()
                refreshDraft()
            }.onFailure { error ->
                products = emptyList()
                selectedProductIdAktif = null
                updateProductSelector()
                refreshPriceOptions()
                refreshDraft()
                showMessage(error.message ?: "Gagal memuat produk")
            }
        }
    }

    private fun selectedProduct(): Produk? =
        products.firstOrNull { it.id == selectedProductIdAktif }

    private fun selectedPriceOption(): OpsiHargaRekap? =
        opsiHargaAktif.getOrNull(binding.spPrice.selectedItemPosition)

    private fun stokLayakJual(product: Produk): Int {
        return product.safeStock + product.nearExpiredStock
    }

    private fun labelStatusStokRekap(product: Produk): String {
        return when {
            stokLayakJual(product) <= 0 -> "Tidak layak jual"
            product.producedToday -> "Produksi Hari Ini"
            product.nearExpiredStock > 0 -> "Hampir Kadaluarsa"
            else -> "Stok Sisa"
        }
    }

    private fun updateProductSelector() {
        val product = selectedProduct()
        binding.tvProductPickerLabel.text = "Produk dipilih"
        binding.tvSelectedProductName.text = when {
            products.isEmpty() -> "Belum ada produk untuk rekap"
            product == null -> "Pilih produk rekap"
            else -> product.name
        }
        binding.tvSelectedProductMeta.text = when {
            products.isEmpty() -> "Tidak ada produk dengan stok layak jual dan harga aktif"
            product == null -> "Produk produksi hari ini dan stok sisa layak jual bisa direkap"
            else -> listOf(
                product.category.ifBlank { "Produk" },
                labelStatusStokRekap(product),
                "Layak jual ${Formatter.ribuan(stokLayakJual(product).toLong())} ${product.unit}"
            ).joinToString(" • ")
        }
        binding.tvProductLeading.text = product?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "P"
        binding.cardProductPicker.isEnabled = products.isNotEmpty()
        binding.cardProductPicker.alpha = if (products.isNotEmpty()) 1f else 0.7f
    }

    private fun showProductPicker() {
        if (products.isEmpty()) return

        PembantuPilihProduk.show(
            activity = this,
            title = "Pilih Produk Rekap",
            produk = products.map { product ->
                ProdukPilihanUi(
                    id = product.id,
                    namaProduk = product.name,
                    jenisProduk = product.category,
                    stokSaatIni = product.stock.toLong(),
                    satuan = product.unit,
                    aktifDijual = product.active,
                    infoTambahan = "${labelStatusStokRekap(product)} • layak jual ${Formatter.ribuan(stokLayakJual(product).toLong())} ${product.unit}"
                )
            },
            selectedId = selectedProductIdAktif,
            kategoriOptions = listOf("Semua", "Dasar", "Olahan")
        ) { selected ->
            selectedProductIdAktif = selected.id
            updateProductSelector()
            refreshPriceOptions()
        }
    }

    private fun refreshPriceOptions() {
        val product = selectedProduct()
        updateProductSelector()

        opsiHargaAktif = product?.channels
            ?.filter { it.active && it.price > 0L }
            ?.sortedWith(
                compareByDescending<HargaKanal> { it.defaultCashier }
                    .thenBy { it.label.lowercase() }
            )
            ?.map {
                OpsiHargaRekap(
                    label = it.label,
                    price = it.price,
                    defaultCashier = it.defaultCashier
                )
            }
            ?: emptyList()

        val labels = if (opsiHargaAktif.isEmpty()) {
            listOf("Belum ada harga aktif")
        } else {
            opsiHargaAktif.map { opsi ->
                buildString {
                    append(opsi.label)
                    append(" • ")
                    append(Formatter.currency(opsi.price))
                    if (opsi.defaultCashier) append(" (Default Kasir)")
                }
            }
        }

        binding.spPrice.adapter = AdapterSpinner.stringAdapter(this, labels)
        updateSelectedPriceInfo()
    }

    private fun updateSelectedPriceInfo() {
        val product = selectedProduct()
        val selectedPrice = selectedPriceOption()

        binding.tvSelectedPrice.text = when {
            product == null && products.isEmpty() ->
                "Tidak ada produk dengan stok layak jual dan harga aktif."

            product == null ->
                "Pilih produk untuk melihat harga."

            selectedPrice == null ->
                "Produk ${product.name} belum punya harga aktif yang bisa dipakai rekap."

            else -> {
                val labelDefault = if (selectedPrice.defaultCashier) " • Default Kasir" else ""
                "Harga terpilih: ${selectedPrice.label}$labelDefault\n${Formatter.currency(selectedPrice.price)}"
            }
        }

        binding.tvProductInfo.text = if (product == null) {
            "Produk yang tampil mencakup produksi hari ini dan stok sisa yang masih layak jual."
        } else {
            val hargaInfo = product.channels
                .filter { it.active }
                .sortedWith(
                    compareByDescending<HargaKanal> { it.defaultCashier }
                        .thenBy { it.label.lowercase() }
                )
                .joinToString("\n") {
                    val infoDefault = if (it.defaultCashier) " (Default Kasir)" else ""
                    "• ${it.label}: ${Formatter.currency(it.price)}$infoDefault"
                }
                .ifBlank { "Belum ada harga aktif" }

            "Pilihan harga produk:\n$hargaInfo"
        }

        binding.tvSelectedStock.text = if (product == null) {
            ""
        } else {
            "Stok layak jual: ${Formatter.ribuan(stokLayakJual(product).toLong())} ${product.unit}"
        }
    }

    private fun addItem() {
        val product = selectedProduct()
        if (product == null) {
            showMessage("Produk belum tersedia untuk rekap")
            return
        }

        val opsiHarga = selectedPriceOption()
        if (opsiHarga == null) {
            showMessage("Pilih harga yang aktif untuk ${product.name}")
            return
        }

        val qty = InputAngka.ambilInt(binding.etQty)
        if (qty <= 0) {
            showMessage("Jumlah harus lebih dari 0")
            return
        }

        val totalDraftProduk = draftItems
            .filter { it.productId == product.id }
            .sumOf { it.qty }

        if (totalDraftProduk + qty > stokLayakJual(product)) {
            showMessage("Stok layak jual ${product.name} tidak mencukupi")
            return
        }

        val existing = draftItems.firstOrNull {
            it.productId == product.id &&
                it.channelLabel.equals(opsiHarga.label, true) &&
                it.price == opsiHarga.price
        }

        if (existing == null) {
            draftItems += ItemDraftRekap(
                id = "draft-${product.id}-${opsiHarga.label}-${draftItems.size + 1}",
                productId = product.id,
                productName = product.name,
                channelLabel = opsiHarga.label,
                qty = qty,
                price = opsiHarga.price
            )
        } else {
            existing.qty += qty
        }

        binding.etQty.setText("")
        refreshDraft()
        draftBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun refreshDraft() {
        val rows = draftItems.map {
            ItemBaris(
                id = it.id,
                title = it.productName,
                subtitle = "${Formatter.ribuan(it.qty.toLong())} pcs x ${Formatter.currency(it.price)}",
                amount = Formatter.currency(it.qty.toLong() * it.price),
                badge = it.channelLabel,
                parameterStatus = "Harga ${it.channelLabel}",
                parameterTone = WarnaBaris.BLUE,
                tone = WarnaBaris.BLUE,
                actionLabel = "⋮"
            )
        }

        itemAdapter.submitList(rows)
        binding.tvDraftEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        binding.tvDraftSummary.text = if (draftItems.isEmpty()) {
            "Belum ada item. Pilih produk dan harga lalu tambahkan ke draft."
        } else {
            "${Formatter.ribuan(draftItems.size.toLong())} item draft • ${Formatter.ribuan(draftItems.sumOf { it.qty }.toLong())} pcs"
        }
        binding.tvTotal.text =
            "Total rekap: ${Formatter.currency(draftItems.sumOf { it.qty.toLong() * it.price })}"
        binding.btnSaveRecap.isEnabled = draftItems.isNotEmpty()
    }

    private fun showItemMenu(item: ItemBaris, anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Hapus item")
            setOnMenuItemClickListener {
                draftItems.removeAll { it.id == item.id }
                refreshDraft()
                true
            }
        }.show()
    }

    private fun saveRecap() {
        if (draftItems.isEmpty()) {
            showMessage("Item rekap masih kosong")
            return
        }

        val date = binding.etDate.text?.toString().orEmpty()
        if (date.isBlank()) {
            showMessage("Tanggal rekap wajib diisi")
            return
        }

        val time = binding.etTime.text?.toString().orEmpty()
        if (time.isBlank()) {
            showMessage("Jam rekap wajib diisi")
            return
        }

        lifecycleScope.launch {
            binding.btnSaveRecap.isEnabled = false

            runCatching {
                val saleId = RepositoriFirebaseUtama.simpanRekapPasar(
                    dateOnly = date,
                    timeOnly = time,
                    sumberTransaksi = "PASAR",
                    draftItems = draftItems.toList(),
                    userAuthId = currentUserId(),
                    products = products
                )
                RepositoriFirebaseUtama.buildReceiptText(saleId)
            }.onSuccess { receipt ->
                draftItems.clear()
                binding.etQty.setText("")
                refreshDraft()
                showReceiptModal("Rekap pasar tersimpan", receipt)
                loadProductsBySelectedDate()
            }.onFailure {
                showMessage(it.message ?: "Gagal menyimpan rekap")
                refreshDraft()
            }

            binding.btnSaveRecap.isEnabled = true
        }
    }

    private fun dpToPx(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class OpsiHargaRekap(
        val label: String,
        val price: Long,
        val defaultCashier: Boolean
    )
}

private class RecapSpinnerListener(
    private val onSelected: () -> Unit
) : android.widget.AdapterView.OnItemSelectedListener {

    override fun onItemSelected(
        parent: android.widget.AdapterView<*>?,
        view: View?,
        position: Int,
        id: Long
    ) = onSelected()

    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
}
