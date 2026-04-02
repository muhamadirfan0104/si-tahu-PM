package muhamad.irfan.si_tahupm.ui.sales

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.data.RecapDraftItem
import muhamad.irfan.si_tahupm.databinding.ActivityMarketRecapBinding
import muhamad.irfan.si_tahupm.ui.base.BaseActivity
import muhamad.irfan.si_tahupm.ui.common.GenericRowAdapter
import muhamad.irfan.si_tahupm.ui.main.SimpleItemSelectedListener
import muhamad.irfan.si_tahupm.util.Formatters
import muhamad.irfan.si_tahupm.util.RowItem
import muhamad.irfan.si_tahupm.util.RowTone

class MarketRecapActivity : BaseActivity() {
    private lateinit var binding: ActivityMarketRecapBinding
    private val draftItems = mutableListOf<RecapDraftItem>()
    private val channels = listOf("PASAR", "RESELLER", "GROSIR")
    private val products by lazy { DemoRepository.allProducts() }
    private val adapter = GenericRowAdapter(onItemClick = {}, onActionClick = { row ->
        draftItems.removeAll { it.id == row.id }
        refreshDraftList()
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMarketRecapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Rekap Pasar", "Input penjualan kanal pasar")

        binding.etDate.setText(DemoRepository.latestDateOnly())
        binding.spChannel.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, channels)
        binding.spProduct.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, products.map { it.name })
        binding.rvItems.layoutManager = LinearLayoutManager(this)
        binding.rvItems.adapter = adapter
        binding.spChannel.onItemSelectedListener = SimpleItemSelectedListener { refreshPriceInfo() }
        binding.spProduct.onItemSelectedListener = SimpleItemSelectedListener { refreshPriceInfo() }
        binding.btnAddItem.setOnClickListener { addDraftItem() }
        binding.btnSaveRecap.setOnClickListener { saveRecap() }
        refreshPriceInfo()
        refreshDraftList()
    }

    private fun selectedProduct() = products.getOrNull(binding.spProduct.selectedItemPosition)
    private fun selectedChannel() = channels.getOrNull(binding.spChannel.selectedItemPosition) ?: "PASAR"

    private fun refreshPriceInfo() {
        val product = selectedProduct() ?: return
        val price = DemoRepository.channelByLabel(product, selectedChannel())?.price ?: 0L
        binding.tvChannelPrice.text = "Harga kanal ${selectedChannel()}: ${Formatters.currency(price)}"
    }

    private fun addDraftItem() {
        val product = selectedProduct() ?: return
        val qty = binding.etQty.text?.toString()?.toIntOrNull() ?: 0
        if (qty <= 0) {
            showMessage("Jumlah item rekap harus lebih dari 0.")
            return
        }
        val price = DemoRepository.channelByLabel(product, selectedChannel())?.price ?: 0L
        draftItems += RecapDraftItem(Formatters.newId("rec"), product.id, product.name, qty, price)
        refreshDraftList()
    }

    private fun refreshDraftList() {
        adapter.submitList(draftItems.map {
            RowItem(
                id = it.id,
                title = it.productName,
                subtitle = "${it.qty} x ${Formatters.currency(it.price)}",
                badge = selectedChannel(),
                amount = Formatters.currency(it.qty.toLong() * it.price),
                actionLabel = "Hapus",
                tone = RowTone.GOLD
            )
        })
        binding.tvTotal.text = "Total rekap: ${Formatters.currency(draftItems.sumOf { it.qty.toLong() * it.price })}"
    }

    private fun saveRecap() {
        runCatching {
            DemoRepository.saveMarketRecap(
                dateOnly = binding.etDate.text?.toString().orEmpty(),
                source = selectedChannel(),
                items = draftItems.toList(),
                userId = currentUserId()
            )
        }.onSuccess { sale ->
            showMessage("Rekap pasar berhasil disimpan.")
            draftItems.clear()
            refreshDraftList()
            showDetailModal("Struk ${sale.id}", DemoRepository.buildReceiptText(sale.id))
        }.onFailure {
            showMessage(it.message ?: "Gagal menyimpan rekap pasar")
        }
    }
}
