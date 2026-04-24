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
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.PembantuPilihTanggalWaktu
import muhamad.irfan.si_tahu.util.WarnaBaris

class AktivitasRekapPasar : AktivitasDasar() {

    private lateinit var binding: ActivityMarketRecapBinding
    private val itemAdapter = AdapterBarisUmum(onItemClick = {}, onActionClick = ::showItemMenu)
    private lateinit var draftBottomSheetBehavior: BottomSheetBehavior<View>

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private var products: List<Produk> = emptyList()
    private val draftItems = mutableListOf<ItemDraftRekap>()
    private var opsiHargaAktif: List<OpsiHargaRekap> = emptyList()

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

        binding.spProduct.adapter = AdapterSpinner.stringAdapter(this, listOf("Belum ada produk"))
        binding.spPrice.adapter = AdapterSpinner.stringAdapter(this, listOf("Belum ada harga aktif"))

        binding.spProduct.onItemSelectedListener = RecapSpinnerListener {
            refreshPriceOptions()
        }

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
        updateSelectedPriceInfo()
    }

    /**
     * Revisi nomor 1:
     * Halaman penjualan admin hanya menampilkan produk
     * yang punya catatan produksi pada tanggal terpilih.
     *
     * Alur:
     * 1. Ambil CatatanProduksi by kunciTanggal
     * 2. Ambil idProdukHasil yang unik
     * 3. Filter Produk aktif berdasarkan id tersebut
     *
     * Catatan:
     * - stok lama tidak membuat produk ikut tampil
     * - kasir tidak ikut logika ini
     */
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

                if (producedProductIds.isEmpty()) {
                    emptyList()
                } else {
                    RepositoriFirebaseUtama.muatProdukAktif()
                        .filter { it.id in producedProductIds }
                        .sortedBy { it.name.lowercase() }
                }
            }.onSuccess { result ->
                products = result

                val labels = if (products.isEmpty()) {
                    listOf("Tidak ada produksi pada tanggal ini")
                } else {
                    products.map { produk ->
                        "${produk.code} • ${produk.name}"
                    }
                }

                binding.spProduct.adapter =
                    AdapterSpinner.stringAdapter(this@AktivitasRekapPasar, labels)

                refreshPriceOptions()
                refreshDraft()
            }.onFailure { error ->
                products = emptyList()
                refreshPriceOptions()
                refreshDraft()
                showMessage(error.message ?: "Gagal memuat produk")
            }
        }
    }

    private fun selectedProduct(): Produk? =
        products.getOrNull(binding.spProduct.selectedItemPosition)

    private fun selectedPriceOption(): OpsiHargaRekap? =
        opsiHargaAktif.getOrNull(binding.spPrice.selectedItemPosition)

    private fun refreshPriceOptions() {
        val product = selectedProduct()

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
                "Tidak ada produk yang diproduksi pada tanggal ${binding.etDate.text?.toString().orEmpty()}."

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
            "Produk admin hanya tampil jika ada produksi pada tanggal yang dipilih."
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
            "Stok tersedia: ${product.stock} ${product.unit}"
        }
    }

    private fun addItem() {
        val product = selectedProduct()
        if (product == null) {
            showMessage("Produk belum tersedia pada tanggal ini")
            return
        }

        val opsiHarga = selectedPriceOption()
        if (opsiHarga == null) {
            showMessage("Pilih harga yang aktif untuk ${product.name}")
            return
        }

        val qty = binding.etQty.text?.toString()?.toIntOrNull() ?: 0
        if (qty <= 0) {
            showMessage("Jumlah harus lebih dari 0")
            return
        }

        val totalDraftProduk = draftItems
            .filter { it.productId == product.id }
            .sumOf { it.qty }

        if (totalDraftProduk + qty > product.stock) {
            showMessage("Stok ${product.name} tidak mencukupi")
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
                subtitle = "${it.qty} pcs x ${Formatter.currency(it.price)}",
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
            "${draftItems.size} item draft • ${draftItems.sumOf { it.qty }} pcs"
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