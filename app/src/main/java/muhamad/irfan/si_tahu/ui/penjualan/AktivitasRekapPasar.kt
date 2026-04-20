package muhamad.irfan.si_tahu.ui.penjualan

import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
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

    private var products: List<Produk> = emptyList()
    private val draftItems = mutableListOf<ItemDraftRekap>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        binding = ActivityMarketRecapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Rekap Penjualan Pasar", "Catat penjualan di luar kasir")

        setupView()
        loadProducts()
    }

    private fun setupView() {
        binding.etDate.setText(Formatter.currentDateOnly())
        binding.etDate.setOnClickListener {
            PembantuPilihTanggalWaktu.showDatePicker(
                this,
                binding.etDate.text?.toString()
            ) { binding.etDate.setText(it) }
        }

        binding.rvItems.layoutManager = LinearLayoutManager(this)
        binding.rvItems.adapter = itemAdapter
        binding.rvItems.isNestedScrollingEnabled = false

        binding.spChannel.adapter =
            AdapterSpinner.stringAdapter(this, listOf("Pasar"))

        binding.spProduct.onItemSelectedListener = RecapSpinnerListener { updateChannelPrice() }
        binding.spChannel.onItemSelectedListener = RecapSpinnerListener { updateChannelPrice() }

        binding.btnAddItem.setOnClickListener { addItem() }
        binding.btnSaveRecap.setOnClickListener { saveRecap() }

        refreshDraft()
    }

    private fun loadProducts() {
        lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatProdukAktif() }
                .onSuccess {
                    products = it
                    val labels = if (products.isEmpty()) {
                        listOf("Belum ada produk")
                    } else {
                        products.map { produk -> "${produk.code} • ${produk.name}" }
                    }

                    binding.spProduct.adapter =
                        AdapterSpinner.stringAdapter(this@AktivitasRekapPasar, labels)

                    updateChannelPrice()
                    refreshDraft()
                }
                .onFailure {
                    products = emptyList()
                    refreshDraft()
                    showMessage(it.message ?: "Gagal memuat produk")
                }
        }
    }

    private fun selectedProduct(): Produk? =
        products.getOrNull(binding.spProduct.selectedItemPosition)

    private fun channelPrice(product: Produk?, channel: String): Long {
        if (product == null) return 0L
        return product.channels.firstOrNull { it.label.equals(channel, true) && it.active }?.price
            ?: product.channels.firstOrNull { it.defaultCashier && it.active }?.price
            ?: product.channels.firstOrNull { it.active }?.price
            ?: 0L
    }

    private fun updateChannelPrice() {
        val product = selectedProduct()
        val channel = binding.spChannel.selectedItem?.toString().orEmpty()
        val price = channelPrice(product, channel)

        binding.tvChannelPrice.text = if (product == null) {
            "Pilih produk untuk melihat harga kanal."
        } else {
            "Harga ${channel.ifBlank { "kanal" }} untuk ${product.name}: ${Formatter.currency(price)}"
        }
    }

    private fun addItem() {
        val product = selectedProduct()
        if (product == null) {
            showMessage("Produk belum tersedia")
            return
        }

        val qty = binding.etQty.text?.toString()?.toIntOrNull() ?: 0
        if (qty <= 0) {
            showMessage("Jumlah harus lebih dari 0")
            return
        }

        val channel = binding.spChannel.selectedItem?.toString().orEmpty()
        val price = channelPrice(product, channel)

        val existing = draftItems.firstOrNull { it.productId == product.id && it.price == price }
        val totalQty = (existing?.qty ?: 0) + qty

        if (totalQty > product.stock) {
            showMessage("Stok ${product.name} tidak mencukupi")
            return
        }

        if (existing == null) {
            draftItems += ItemDraftRekap(
                id = "draft-${product.id}-${draftItems.size + 1}",
                productId = product.id,
                productName = product.name,
                qty = qty,
                price = price
            )
        } else {
            existing.qty += qty
        }

        binding.etQty.setText("")
        refreshDraft()
    }

    private fun refreshDraft() {
        val rows = draftItems.map {
            ItemBaris(
                id = it.id,
                title = it.productName,
                subtitle = "${it.qty} pcs x ${Formatter.currency(it.price)}",
                amount = Formatter.currency(it.qty.toLong() * it.price),
                badge = "Draft",
                tone = WarnaBaris.BLUE,
                actionLabel = "⋮"
            )
        }

        itemAdapter.submitList(rows)
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

        val channel = binding.spChannel.selectedItem?.toString().orEmpty()

        lifecycleScope.launch {
            binding.btnSaveRecap.isEnabled = false
            runCatching {
                val saleId = RepositoriFirebaseUtama.simpanRekapPasar(
                    dateOnly = date,
                    sumberTransaksi = channel,
                    draftItems = draftItems.toList(),
                    userAuthId = currentUserId(),
                    products = products
                )
                RepositoriFirebaseUtama.buildReceiptText(saleId)
            }.onSuccess { receipt ->
                draftItems.clear()
                binding.etQty.setText("")
                refreshDraft()
                showReceiptModal("Rekap pasar tersimpan", receipt, "Bagikan")
                loadProducts()
            }.onFailure {
                showMessage(it.message ?: "Gagal menyimpan rekap")
                refreshDraft()
            }
        }
    }
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