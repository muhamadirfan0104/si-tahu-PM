package muhamad.irfan.si_tahupm.ui.report

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.databinding.ActivityReportBinding
import muhamad.irfan.si_tahupm.ui.base.BaseActivity
import muhamad.irfan.si_tahupm.ui.common.GenericRowAdapter
import muhamad.irfan.si_tahupm.ui.history.TransactionHistoryActivity
import muhamad.irfan.si_tahupm.ui.main.SimpleItemSelectedListener
import muhamad.irfan.si_tahupm.util.RowItem
import muhamad.irfan.si_tahupm.util.RowTone

class ReportActivity : BaseActivity() {
    private lateinit var binding: ActivityReportBinding
    private val ranges = listOf("7", "30", "semua")
    private val labels = listOf("7 Hari", "30 Hari", "Semua")
    private val adapter = GenericRowAdapter(onItemClick = {})

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Laporan", "Ringkasan usaha per periode")

        binding.spRange.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        binding.rvMix.layoutManager = LinearLayoutManager(this)
        binding.rvMix.adapter = adapter
        binding.spRange.onItemSelectedListener = SimpleItemSelectedListener { refresh() }
        binding.btnTransactions.setOnClickListener { startActivity(Intent(this, TransactionHistoryActivity::class.java)) }
        binding.btnShareCsv.setOnClickListener {
            val rangeKey = ranges[binding.spRange.selectedItemPosition]
            shareCacheFile("Bagikan CSV laporan", "laporan-umkm-tahu-berkah.csv", "text/csv", DemoRepository.buildCsv(rangeKey))
        }
        refresh()
    }

    private fun refresh() {
        val rangeKey = ranges[binding.spRange.selectedItemPosition]
        val summary = DemoRepository.reportSummary(rangeKey)
        binding.tvReportSales.text = "Penjualan: ${muhamad.irfan.si_tahupm.util.Formatters.currency(summary.totalSales)}"
        binding.tvReportProfit.text = "Laba kotor: ${muhamad.irfan.si_tahupm.util.Formatters.currency(summary.totalProfit)}"
        binding.tvReportProduction.text = "Produksi: ${summary.totalProduction} pcs"
        binding.tvReportExpenses.text = "Pengeluaran: ${muhamad.irfan.si_tahupm.util.Formatters.currency(summary.totalExpenses)}"
        binding.tvReportTransactions.text = "Transaksi: ${summary.transactionCount}"
        adapter.submitList(summary.mix.map {
            RowItem(
                id = it.productId,
                title = it.name,
                subtitle = "Kontribusi penjualan periode ini",
                badge = "Terlaris",
                amount = "${it.qty} pcs",
                tone = RowTone.GOLD
            )
        })
    }
}
