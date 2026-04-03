package muhamad.irfan.si_tahu.ui.report

import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import muhamad.irfan.si_tahu.data.RepositoriLokal
import muhamad.irfan.si_tahu.databinding.ActivityReportBinding
import muhamad.irfan.si_tahu.ui.base.AktivitasDasar
import muhamad.irfan.si_tahu.ui.common.AdapterBarisUmum
import muhamad.irfan.si_tahu.ui.history.AktivitasRiwayatTransaksi
import muhamad.irfan.si_tahu.ui.main.PendengarPilihItemSederhana
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris
import muhamad.irfan.si_tahu.util.AdapterSpinner

class AktivitasLaporan : AktivitasDasar() {
    private lateinit var binding: ActivityReportBinding
    private val ranges = listOf("7", "30", "semua")
    private val labels = listOf("7 Hari", "30 Hari", "Semua")
    private val adapter = AdapterBarisUmum(onItemClick = {})

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Laporan", "Ringkasan usaha per periode")

        binding.spRange.adapter = AdapterSpinner.stringAdapter(this, labels)
        binding.rvMix.layoutManager = LinearLayoutManager(this)
        binding.rvMix.adapter = adapter
        binding.spRange.onItemSelectedListener = PendengarPilihItemSederhana { refresh() }
        binding.btnTransactions.setOnClickListener { startActivity(Intent(this, AktivitasRiwayatTransaksi::class.java)) }
        binding.btnShareCsv.setOnClickListener {
            val rangeKey = ranges[binding.spRange.selectedItemPosition]
            shareCacheFile("Bagikan CSV laporan", "laporan-si-tahu.csv", "text/csv", RepositoriLokal.buildCsv(rangeKey))
        }
        refresh()
    }

    private fun refresh() {
        val rangeKey = ranges[binding.spRange.selectedItemPosition]
        val summary = RepositoriLokal.reportSummary(rangeKey)
        binding.tvReportSales.text = "Penjualan: ${muhamad.irfan.si_tahu.util.Formatter.currency(summary.totalSales)}"
        binding.tvReportProfit.text = "Laba kotor: ${muhamad.irfan.si_tahu.util.Formatter.currency(summary.totalProfit)}"
        binding.tvReportProduction.text = "Produksi: ${summary.totalProduction} pcs"
        binding.tvReportExpenses.text = "Pengeluaran: ${muhamad.irfan.si_tahu.util.Formatter.currency(summary.totalExpenses)}"
        binding.tvReportTransactions.text = "Transaksi: ${summary.transactionCount}"
        adapter.submitList(summary.mix.map {
            ItemBaris(
                id = it.productId,
                title = it.name,
                subtitle = "Kontribusi penjualan periode ini",
                badge = "Terlaris",
                amount = "${it.qty} pcs",
                tone = WarnaBaris.GOLD
            )
        })
    }
}
