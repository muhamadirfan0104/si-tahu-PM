package muhamad.irfan.si_tahu.ui.produksi

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.databinding.ActivityModuleProductionBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.parameter.AktivitasDaftarParameter
import muhamad.irfan.si_tahu.ui.penjualan.AktivitasMenuPenjualan
import muhamad.irfan.si_tahu.ui.stok.AktivitasMonitoringStok
import muhamad.irfan.si_tahu.ui.utama.AktivitasUtamaAdmin
import muhamad.irfan.si_tahu.ui.umum.AdapterBarisUmum
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class AktivitasMenuProduksi : AktivitasDasar() {

    private lateinit var binding: ActivityModuleProductionBinding
    private val recentAdapter = AdapterBarisUmum(onItemClick = ::openDetail)
    private var isQuickMenuOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        binding = ActivityModuleProductionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Produksi", "Produksi dasar dan turunan", showBack = false)

        binding.contentProductionMenu.rvProductionRecent.layoutManager = LinearLayoutManager(this)
        binding.contentProductionMenu.rvProductionRecent.adapter = recentAdapter

        setupBottomNavigation()

        binding.fabQuickProduction.setOnClickListener { toggleQuickMenu() }
        binding.btnQuickBasicProduction.setOnClickListener {
            hideQuickMenu()
            startActivity(Intent(this, AktivitasProduksiDasar::class.java))
        }
        binding.btnQuickConversion.setOnClickListener {
            hideQuickMenu()
            startActivity(Intent(this, AktivitasKonversi::class.java))
        }
        binding.btnQuickProductionHistory.setOnClickListener {
            hideQuickMenu()
            startActivity(Intent(this, AktivitasRiwayatProduksi::class.java))
        }
        binding.btnQuickParameter.setOnClickListener {
            hideQuickMenu()
            startActivity(Intent(this, AktivitasDaftarParameter::class.java))
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
        binding.bottomNavigation.selectedItemId = R.id.nav_admin_production
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
                R.id.nav_admin_production -> true
                R.id.nav_admin_sales -> {
                    startActivity(Intent(this, AktivitasMenuPenjualan::class.java))
                    true
                }
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
            runCatching { RepositoriFirebaseUtama.muatRingkasanProduksi() }
                .onSuccess { summary ->
                    binding.contentProductionMenu.tvProductionTotal.text = "${summary.totalProduksiDasarHariIni} pcs"
                    binding.contentProductionMenu.tvProductionBatch.text = "Batch dasar hari ini: ${summary.totalBatchHariIni}"
                    binding.contentProductionMenu.tvProductionDerived.text = "Konversi hari ini: ${summary.totalKonversiHariIni} transaksi"
                    binding.contentProductionMenu.tvParameterActive.text = "Parameter aktif: ${summary.totalParameterAktif}"
                    binding.contentProductionMenu.tvProductionHistoryCount.text = "Riwayat total: ${summary.totalRiwayat} catatan"
                    recentAdapter.submitList(summary.recentRows.map {
                        ItemBaris(
                            id = it.id,
                            title = it.title,
                            subtitle = it.subtitle,
                            amount = it.amount,
                            badge = it.badge,
                            tone = if (it.badge == "Konversi") WarnaBaris.BLUE else WarnaBaris.GREEN,
                            priceTone = if (it.badge == "Konversi") WarnaBaris.BLUE else WarnaBaris.GREEN
                        )
                    })
                }
                .onFailure {
                    showMessage(it.message ?: "Gagal memuat ringkasan produksi")
                    recentAdapter.submitList(emptyList())
                }
        }
    }

    private fun openDetail(item: ItemBaris) {
        lifecycleScope.launch {
            val text = RepositoriFirebaseUtama.buildProductionDetailText(item.id)
            showDetailModal("Detail Produksi", text)
        }
    }

    private fun toggleQuickMenu() {
        isQuickMenuOpen = !isQuickMenuOpen
        binding.layoutQuickProductionMenu.visibility = if (isQuickMenuOpen) View.VISIBLE else View.GONE
        binding.fabQuickProduction.setImageResource(
            if (isQuickMenuOpen) android.R.drawable.ic_menu_close_clear_cancel else android.R.drawable.ic_input_add
        )
    }

    private fun hideQuickMenu() {
        if (!isQuickMenuOpen && binding.layoutQuickProductionMenu.visibility != View.VISIBLE) return
        isQuickMenuOpen = false
        binding.layoutQuickProductionMenu.visibility = View.GONE
        binding.fabQuickProduction.setImageResource(android.R.drawable.ic_input_add)
    }
}
