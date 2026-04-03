package muhamad.irfan.si_tahu.ui.sales

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import muhamad.irfan.si_tahu.data.ItemDraftRekap
import muhamad.irfan.si_tahu.data.RepositoriLokal
import muhamad.irfan.si_tahu.databinding.ActivityMarketRecapBinding
import muhamad.irfan.si_tahu.ui.base.AktivitasDasar
import muhamad.irfan.si_tahu.ui.common.AdapterBarisUmum
import muhamad.irfan.si_tahu.ui.main.PendengarPilihItemSederhana
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris
import muhamad.irfan.si_tahu.util.AdapterSpinner

class AktivitasRekapPasar : AktivitasDasar() {
    private lateinit var binding: ActivityMarketRecapBinding
    private val draftItems = mutableListOf<ItemDraftRekap>()
    private val channels = listOf("PASAR", "RESELLER", "GROSIR")
    private val products by lazy { RepositoriLokal.allProducts() }
    private val adapter = AdapterBarisUmum(onItemClick = {}, onActionClick = { row -> confirmRemoveDraft(row.id) })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMarketRecapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Rekap Pasar", "Input penjualan kanal pasar")

        binding.etDate.setText(Formatter.currentDateOnly())
        binding.spChannel.adapter = AdapterSpinner.stringAdapter(this, channels)
        binding.rvItems.layoutManager = LinearLayoutManager(this)
        binding.rvItems.adapter = adapter
        binding.spChannel.onItemSelectedListener = PendengarPilihItemSederhana { refreshPriceInfo() }
        binding.btnAddItem.setOnClickListener { addDraftItem() }
        binding.btnSaveRecap.setOnClickListener { saveRecap() }

        setupProduk()
        refreshDraftList()
    }

    private fun setupProduk() {
        if (products.isEmpty()) {
            binding.spProduct.adapter = AdapterSpinner.stringAdapter(this, listOf("Belum ada produk"))
            binding.btnAddItem.isEnabled = false
            binding.btnSaveRecap.isEnabled = false
            binding.tvChannelPrice.text = "Tambahkan data produk dan harga terlebih dahulu."
            return
        }

        binding.spProduct.adapter = AdapterSpinner.stringAdapter(this, products.map { it.name })
        binding.spProduct.onItemSelectedListener = PendengarPilihItemSederhana { refreshPriceInfo() }
        binding.btnAddItem.isEnabled = true
        binding.btnSaveRecap.isEnabled = true
        refreshPriceInfo()
    }

    private fun selectedProduct() = products.getOrNull(binding.spProduct.selectedItemPosition)
    private fun selectedChannel() = channels.getOrNull(binding.spChannel.selectedItemPosition) ?: "PASAR"

    private fun refreshPriceInfo() {
        val product = selectedProduct()
        if (product == null) {
            binding.tvChannelPrice.text = "Tambahkan data produk dan harga terlebih dahulu."
            return
        }
        val price = RepositoriLokal.channelByLabel(product, selectedChannel())?.price ?: 0L
        binding.tvChannelPrice.text = "Harga kanal ${selectedChannel()}: ${Formatter.currency(price)}"
    }

    private fun addDraftItem() {
        val product = selectedProduct()
        if (product == null) {
            showMessage("Produk belum tersedia.")
            return
        }

        val qty = binding.etQty.text?.toString()?.toIntOrNull() ?: 0
        if (qty <= 0) {
            showMessage("Jumlah item rekap harus lebih dari 0.")
            return
        }

        val price = RepositoriLokal.channelByLabel(product, selectedChannel())?.price ?: 0L
        if (price <= 0L) {
            showMessage("Harga kanal untuk produk ini belum tersedia.")
            return
        }

        draftItems += ItemDraftRekap(Formatter.newId("rec"), product.id, product.name, qty, price)
        binding.etQty.setText("")
        refreshDraftList()
    }

    private fun refreshDraftList() {
        adapter.submitList(draftItems.map {
            ItemBaris(
                id = it.id,
                title = it.productName,
                subtitle = "${it.qty} x ${Formatter.currency(it.price)}",
                badge = selectedChannel(),
                amount = Formatter.currency(it.qty.toLong() * it.price),
                actionLabel = "Hapus",
                tone = WarnaBaris.GOLD
            )
        })
        binding.tvTotal.text = "Total rekap: ${Formatter.currency(draftItems.sumOf { it.qty.toLong() * it.price })}"
    }

    private fun confirmRemoveDraft(draftId: String) {
        showConfirmationModal("Hapus item rekap?", "Item draft ini akan dihapus dari daftar rekap.") {
            draftItems.removeAll { it.id == draftId }
            refreshDraftList()
        }
    }

    private fun saveRecap() {
        if (draftItems.isEmpty()) {
            showMessage("Tambahkan minimal satu item rekap terlebih dahulu.")
            return
        }

        runCatching {
            RepositoriLokal.saveMarketRecap(
                dateOnly = binding.etDate.text?.toString().orEmpty(),
                source = selectedChannel(),
                items = draftItems.toList(),
                userId = currentUserId()
            )
        }.onSuccess { sale ->
            showMessage("Rekap pasar berhasil disimpan.")
            draftItems.clear()
            refreshDraftList()
            showReceiptModal("Struk ${sale.id}", RepositoriLokal.buildReceiptText(sale.id))
        }.onFailure {
            showMessage(it.message ?: "Gagal menyimpan rekap pasar")
        }
    }
}
