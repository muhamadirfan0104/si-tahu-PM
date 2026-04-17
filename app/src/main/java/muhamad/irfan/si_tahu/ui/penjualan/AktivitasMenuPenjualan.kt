package muhamad.irfan.si_tahu.ui.penjualan

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.databinding.ActivityModuleSalesBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.produksi.AktivitasMenuProduksi
import muhamad.irfan.si_tahu.ui.stok.AktivitasMonitoringStok
import muhamad.irfan.si_tahu.ui.umum.AdapterBarisUmum
import muhamad.irfan.si_tahu.ui.utama.AktivitasUtamaAdmin
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class AktivitasMenuPenjualan : AktivitasDasar() {

    private lateinit var binding: ActivityModuleSalesBinding
    private val recentAdapter = AdapterBarisUmum(onItemClick = ::openDetail)
    private var isQuickMenuOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        binding = ActivityModuleSalesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Penjualan", "Rekap pasar dan riwayat", showBack = false)

        binding.contentSalesMenu.rvSalesRecent.layoutManager = LinearLayoutManager(this)
        binding.contentSalesMenu.rvSalesRecent.adapter = recentAdapter

        setupBottomNavigation()

        binding.fabQuickSales.setOnClickListener { toggleQuickMenu() }
        binding.btnQuickMarketRecap.setOnClickListener {
            hideQuickMenu()
            startActivity(Intent(this, AktivitasRekapPasar::class.java))
        }
        binding.btnQuickSalesHistory.setOnClickListener {
            hideQuickMenu()
            startActivity(Intent(this, AktivitasRiwayatPenjualan::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        hideQuickMenu()
        renderSummary()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.menu.clear()
        binding.bottomNavigation.inflateMenu(R.menu.menu_bottom_admin)
        binding.bottomNavigation.selectedItemId = R.id.nav_admin_sales
        binding.bottomNavigation.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_admin_dashboard -> {
                    startActivity(AktivitasUtamaAdmin.intent(this, R.id.nav_admin_dashboard, clearTop = true))
                    true
                }
                R.id.nav_admin_menu -> {
                    startActivity(AktivitasUtamaAdmin.intent(this, R.id.nav_admin_menu, clearTop = true))
                    true
                }
                R.id.nav_admin_production -> {
                    startActivity(Intent(this, AktivitasMenuProduksi::class.java))
                    true
                }
                R.id.nav_admin_sales -> true
                R.id.nav_admin_stock -> {
                    startActivity(Intent(this, AktivitasMonitoringStok::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun renderSummary() {
        lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatRingkasanPenjualan() }
                .onSuccess { summary ->
                    binding.contentSalesMenu.tvSalesTotal.text = muhamad.irfan.si_tahu.util.Formatter.currency(summary.totalHariIni)
                    binding.contentSalesMenu.tvCashierTotal.text = "Rumahan/Kasir: ${muhamad.irfan.si_tahu.util.Formatter.currency(summary.totalKasirHariIni)}"
                    binding.contentSalesMenu.tvRecapTotal.text = "Rekap pasar: ${muhamad.irfan.si_tahu.util.Formatter.currency(summary.totalRekapHariIni)}"
                    binding.contentSalesMenu.tvSalesCount.text = "Jumlah transaksi: ${summary.jumlahTransaksiHariIni}"
                    binding.contentSalesMenu.tvSoldQty.text = "Item terjual: ${summary.totalItemHariIni} pcs"
                    recentAdapter.submitList(summary.recentRows.map {
                        ItemBaris(
                            id = it.id,
                            title = it.title,
                            subtitle = it.subtitle,
                            badge = it.badge,
                            amount = it.amount,
                            tone = if (it.badge == "Rumahan") WarnaBaris.GREEN else WarnaBaris.BLUE
                        )
                    })
                }
                .onFailure {
                    showMessage(it.message ?: "Gagal memuat ringkasan penjualan")
                    recentAdapter.submitList(emptyList())
                }
        }
    }

    private fun openDetail(item: ItemBaris) {
        lifecycleScope.launch {
            showReceiptModal("Detail Penjualan", RepositoriFirebaseUtama.buildReceiptText(item.id), "Bagikan")
        }
    }

    private fun toggleQuickMenu() {
        isQuickMenuOpen = !isQuickMenuOpen
        binding.layoutQuickSalesMenu.visibility = if (isQuickMenuOpen) View.VISIBLE else View.GONE
        binding.fabQuickSales.setImageResource(
            if (isQuickMenuOpen) android.R.drawable.ic_menu_close_clear_cancel else android.R.drawable.ic_input_add
        )
    }

    private fun hideQuickMenu() {
        if (!isQuickMenuOpen && binding.layoutQuickSalesMenu.visibility != View.VISIBLE) return
        isQuickMenuOpen = false
        binding.layoutQuickSalesMenu.visibility = View.GONE
        binding.fabQuickSales.setImageResource(android.R.drawable.ic_input_add)
    }
}
