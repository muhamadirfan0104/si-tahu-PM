package muhamad.irfan.si_tahu.ui.utama

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.databinding.FragmentSalesMenuBinding
import muhamad.irfan.si_tahu.ui.dasar.FragmenDasar
import muhamad.irfan.si_tahu.ui.penjualan.AktivitasRekapPasar
import muhamad.irfan.si_tahu.ui.penjualan.AktivitasRiwayatPenjualan
import muhamad.irfan.si_tahu.ui.umum.AdapterBarisUmum
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.PembantuFloatingMenu
import muhamad.irfan.si_tahu.util.WarnaBaris

class FragmenPenjualan : FragmenDasar(R.layout.fragment_sales_menu) {

    private var _binding: FragmentSalesMenuBinding? = null
    private val binding get() = _binding!!

    private val recentAdapter by lazy {
        AdapterBarisUmum(onItemClick = ::openDetailPenjualan)
    }

    private var hasLoadedOnce = false
    private var isLoading = false
    private var lastLoadedAt = 0L
    private var isActionMenuOpen = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentSalesMenuBinding.bind(view)
        prepareActionMenuState()

        setupView()
        setupActions()
        renderSummary(forceInitialLoading = true)
    }

    override fun onResume() {
        super.onResume()
        closeActionMenuImmediately()
        if (_binding != null && shouldRefreshData()) {
            renderSummary(forceInitialLoading = !hasLoadedOnce)
        }
    }

    private fun shouldRefreshData(): Boolean {
        if (!hasLoadedOnce) return true
        return SystemClock.elapsedRealtime() - lastLoadedAt > 30_000L
    }

    private fun setupView() = with(binding) {
        rvSalesRecent.layoutManager = LinearLayoutManager(requireContext())
        rvSalesRecent.adapter = recentAdapter
    }

    private fun setupActions() = with(binding) {
        fabToggleSalesMenu.setOnClickListener {
            toggleActionMenu(!isActionMenuOpen)
        }

        salesActionScrim.setOnClickListener {
            toggleActionMenu(false)
        }

        btnMarketRecap.setOnClickListener {
            toggleActionMenu(false)
            val safeContext = context ?: return@setOnClickListener
            launchActivitySafely(Intent(safeContext, AktivitasRekapPasar::class.java))
        }

        btnSalesHistory.setOnClickListener {
            toggleActionMenu(false)
            val safeContext = context ?: return@setOnClickListener
            launchActivitySafely(
                AktivitasRiwayatPenjualan.intentRiwayatSemuaAdmin(safeContext)
            )
        }
    }

    private fun prepareActionMenuState() {
        val currentBinding = _binding ?: return
        PembantuFloatingMenu.siapkanLatar(currentBinding.salesActionScrim)
        currentBinding.salesFabMenu.apply {
            isVisible = false
            alpha = 0f
            translationY = menuOffsetPx()
            children.forEach { child ->
                child.alpha = 0f
                child.translationY = menuOffsetPx() / 2f
                child.scaleX = 0.92f
                child.scaleY = 0.92f
            }
        }
        currentBinding.fabToggleSalesMenu.rotation = 0f
    }

    private fun closeActionMenuImmediately() {
        toggleActionMenu(open = false, animate = false)
    }

    private fun toggleActionMenu(open: Boolean, animate: Boolean = true) {
        val currentBinding = _binding ?: return
        if (open == isActionMenuOpen && currentBinding.salesFabMenu.isVisible == open) return

        isActionMenuOpen = open
        currentBinding.salesFabMenu.animate().cancel()
        currentBinding.fabToggleSalesMenu.animate().cancel()
        currentBinding.salesFabMenu.children.forEach { it.animate().cancel() }

        val offset = menuOffsetPx()
        PembantuFloatingMenu.aturLatar(
            content = currentBinding.contentSales,
            scrim = currentBinding.salesActionScrim,
            terbuka = open,
            animate = animate
        )
        if (open) {
            currentBinding.salesFabMenu.isVisible = true
            currentBinding.salesFabMenu.alpha = if (animate) 0f else 1f
            currentBinding.salesFabMenu.translationY = if (animate) offset else 0f

            currentBinding.salesFabMenu.children.forEachIndexed { index, child ->
                child.alpha = if (animate) 0f else 1f
                child.translationY = if (animate) offset / 2f else 0f
                child.scaleX = if (animate) 0.92f else 1f
                child.scaleY = if (animate) 0.92f else 1f

                if (animate) {
                    child.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setStartDelay((index * 24L))
                        .setDuration(180L)
                        .start()
                }
            }

            if (animate) {
                currentBinding.salesFabMenu.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(200L)
                    .start()

                currentBinding.fabToggleSalesMenu.animate()
                    .rotation(45f)
                    .scaleX(1.06f)
                    .scaleY(1.06f)
                    .setDuration(180L)
                    .withEndAction {
                        currentBinding.fabToggleSalesMenu.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(90L)
                            .start()
                    }
                    .start()
            } else {
                currentBinding.fabToggleSalesMenu.rotation = 45f
            }
        } else {
            if (animate) {
                currentBinding.salesFabMenu.children.forEach { child ->
                    child.animate()
                        .alpha(0f)
                        .translationY(offset / 3f)
                        .scaleX(0.92f)
                        .scaleY(0.92f)
                        .setDuration(120L)
                        .start()
                }

                currentBinding.salesFabMenu.animate()
                    .alpha(0f)
                    .translationY(offset / 2f)
                    .setDuration(140L)
                    .withEndAction {
                        if (!isActionMenuOpen) {
                            currentBinding.salesFabMenu.isVisible = false
                        }
                    }
                    .start()

                currentBinding.fabToggleSalesMenu.animate()
                    .rotation(0f)
                    .setDuration(160L)
                    .start()
            } else {
                currentBinding.salesFabMenu.isVisible = false
                currentBinding.salesFabMenu.alpha = 0f
                currentBinding.salesFabMenu.translationY = offset
                currentBinding.salesFabMenu.children.forEach { child ->
                    child.alpha = 0f
                    child.translationY = offset / 2f
                    child.scaleX = 0.92f
                    child.scaleY = 0.92f
                }
                currentBinding.fabToggleSalesMenu.rotation = 0f
            }
        }
    }

    private fun menuOffsetPx(): Float = 24f * resources.displayMetrics.density

    private fun renderSummary(forceInitialLoading: Boolean) {
        val currentBinding = _binding ?: return
        if (isLoading) return

        isLoading = true
        setLoadingState(showLoading = forceInitialLoading && !hasLoadedOnce)

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatRingkasanPenjualan() }
                .onSuccess { summary ->
                    val safeBinding = _binding ?: return@onSuccess
                    hasLoadedOnce = true
                    lastLoadedAt = SystemClock.elapsedRealtime()

                    safeBinding.tvSalesTitle.text = "Penjualan hari ini"
                    safeBinding.tvSalesTotal.text = Formatter.currency(summary.totalHariIni)
                    safeBinding.tvCashierTotal.text =
                        "Rumahan tercatat: ${Formatter.currency(summary.totalKasirHariIni)}"
                    safeBinding.tvRecapTotal.text =
                        "Pasar tercatat: ${Formatter.currency(summary.totalRekapHariIni)}"
                    safeBinding.tvSalesCount.text =
                        "Transaksi: ${summary.jumlahTransaksiHariIni}"
                    safeBinding.tvSoldQty.text =
                        "Item terjual: ${summary.totalItemHariIni}"

                    val rows = summary.recentRows.map {
                        ItemBaris(
                            id = it.id,
                            title = it.title,
                            subtitle = it.subtitle,
                            badge = it.badge,
                            amount = it.amount,
                            tone = when (it.badge) {
                                "Rumahan" -> WarnaBaris.GREEN
                                "Pasar" -> WarnaBaris.BLUE
                                else -> WarnaBaris.GOLD
                            },
                            actionLabel = "⋮"
                        )
                    }

                    recentAdapter.submitList(
                        if (rows.isEmpty()) {
                            listOf(
                                ItemBaris(
                                    id = "empty-sales",
                                    title = "Belum ada transaksi penjualan",
                                    subtitle = "Penjualan Rumahan dan Rekap Pasar yang tersimpan akan tampil paling atas di sini",
                                    badge = "Kosong",
                                    amount = Formatter.currency(0),
                                    tone = WarnaBaris.GOLD
                                )
                            )
                        } else {
                            rows
                        }
                    )

                    setLoadingState(showLoading = false)
                    isLoading = false
                }
                .onFailure {
                    val safeBinding = _binding ?: return@onFailure
                    if (!hasLoadedOnce) {
                        safeBinding.tvSalesTitle.text = "Penjualan hari ini"
                        safeBinding.tvSalesTotal.text = Formatter.currency(0)
                        safeBinding.tvCashierTotal.text = "Rumahan tercatat: ${Formatter.currency(0)}"
                        safeBinding.tvRecapTotal.text = "Pasar tercatat: ${Formatter.currency(0)}"
                        safeBinding.tvSalesCount.text = "Transaksi: 0"
                        safeBinding.tvSoldQty.text = "Item terjual: 0"
                        recentAdapter.submitList(
                            listOf(
                                ItemBaris(
                                    id = "error-sales",
                                    title = "Ringkasan belum bisa dimuat",
                                    subtitle = it.message ?: "Terjadi kendala saat mengambil data penjualan",
                                    badge = "Error",
                                    amount = "",
                                    tone = WarnaBaris.RED
                                )
                            )
                        )
                    }

                    setLoadingState(showLoading = false)
                    isLoading = false
                    showMessage(safeBinding.root, it.message ?: "Gagal memuat ringkasan penjualan")
                }
        }
    }

    private fun setLoadingState(showLoading: Boolean) {
        val currentBinding = _binding ?: return
        currentBinding.progressLoadSales.isVisible = showLoading
        currentBinding.contentSales.isVisible = !showLoading
    }

    private fun openDetailPenjualan(item: ItemBaris) {
        if (item.id == "empty-sales" || item.id == "error-sales") return

        val currentBinding = _binding ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.buildReceiptText(item.id) }
                .onSuccess { detail ->
                    showReceiptModal("Detail Penjualan", detail)
                }
                .onFailure {
                    showMessage(currentBinding.root, it.message ?: "Gagal memuat detail penjualan")
                }
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) closeActionMenuImmediately()
    }

    override fun onPause() {
        closeActionMenuImmediately()
        super.onPause()
    }

    override fun onDestroyView() {
        closeActionMenuImmediately()
        _binding = null
        super.onDestroyView()
    }
}
