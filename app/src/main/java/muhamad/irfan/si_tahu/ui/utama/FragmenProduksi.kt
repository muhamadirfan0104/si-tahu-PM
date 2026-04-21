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
import muhamad.irfan.si_tahu.databinding.FragmentProductionMenuBinding
import muhamad.irfan.si_tahu.ui.dasar.FragmenDasar
import muhamad.irfan.si_tahu.ui.produksi.AktivitasKonversiProduk
import muhamad.irfan.si_tahu.ui.produksi.AktivitasProduksiTahuDasar
import muhamad.irfan.si_tahu.ui.produksi.AktivitasRiwayatProduksi
import muhamad.irfan.si_tahu.ui.umum.AdapterBarisUmum
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.PembantuFloatingMenu
import muhamad.irfan.si_tahu.util.WarnaBaris

class FragmenProduksi : FragmenDasar(R.layout.fragment_production_menu) {

    private var _binding: FragmentProductionMenuBinding? = null
    private val binding get() = _binding!!

    private val recentAdapter by lazy {
        AdapterBarisUmum(onItemClick = {})
    }

    private var hasLoadedOnce = false
    private var isLoading = false
    private var lastLoadedAt = 0L
    private var isActionMenuOpen = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentProductionMenuBinding.bind(view)
        prepareActionMenuState()

        binding.rvProductionRecent.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProductionRecent.adapter = recentAdapter

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

    private fun setupActions() = with(binding) {
        fabToggleProductionMenu.setOnClickListener {
            toggleActionMenu(!isActionMenuOpen)
        }

        productionActionScrim.setOnClickListener {
            toggleActionMenu(false)
        }

        btnBasicProduction.setOnClickListener {
            toggleActionMenu(false)
            val safeContext = context ?: return@setOnClickListener
            launchActivitySafely(Intent(safeContext, AktivitasProduksiTahuDasar::class.java))
        }

        btnConversion.setOnClickListener {
            toggleActionMenu(false)
            val safeContext = context ?: return@setOnClickListener
            launchActivitySafely(Intent(safeContext, AktivitasKonversiProduk::class.java))
        }

        btnProductionHistory.setOnClickListener {
            toggleActionMenu(false)
            val safeContext = context ?: return@setOnClickListener
            launchActivitySafely(Intent(safeContext, AktivitasRiwayatProduksi::class.java))
        }

    }

    private fun prepareActionMenuState() {
        val currentBinding = _binding ?: return
        PembantuFloatingMenu.siapkanLatar(currentBinding.productionActionScrim)
        currentBinding.productionFabMenu.apply {
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
        currentBinding.fabToggleProductionMenu.rotation = 0f
    }

    private fun closeActionMenuImmediately() {
        toggleActionMenu(open = false, animate = false)
    }

    private fun toggleActionMenu(open: Boolean, animate: Boolean = true) {
        val currentBinding = _binding ?: return
        if (open == isActionMenuOpen && currentBinding.productionFabMenu.isVisible == open) return

        isActionMenuOpen = open
        currentBinding.productionFabMenu.animate().cancel()
        currentBinding.fabToggleProductionMenu.animate().cancel()
        currentBinding.productionFabMenu.children.forEach { it.animate().cancel() }

        val offset = menuOffsetPx()
        PembantuFloatingMenu.aturLatar(
            content = currentBinding.contentProduction,
            scrim = currentBinding.productionActionScrim,
            terbuka = open,
            animate = animate
        )
        if (open) {
            currentBinding.productionFabMenu.isVisible = true
            currentBinding.productionFabMenu.alpha = if (animate) 0f else 1f
            currentBinding.productionFabMenu.translationY = if (animate) offset else 0f

            currentBinding.productionFabMenu.children.forEachIndexed { index, child ->
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
                currentBinding.productionFabMenu.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(200L)
                    .start()

                currentBinding.fabToggleProductionMenu.animate()
                    .rotation(45f)
                    .scaleX(1.06f)
                    .scaleY(1.06f)
                    .setDuration(180L)
                    .withEndAction {
                        currentBinding.fabToggleProductionMenu.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(90L)
                            .start()
                    }
                    .start()
            } else {
                currentBinding.fabToggleProductionMenu.rotation = 45f
            }
        } else {
            if (animate) {
                currentBinding.productionFabMenu.children.forEach { child ->
                    child.animate()
                        .alpha(0f)
                        .translationY(offset / 3f)
                        .scaleX(0.92f)
                        .scaleY(0.92f)
                        .setDuration(120L)
                        .start()
                }

                currentBinding.productionFabMenu.animate()
                    .alpha(0f)
                    .translationY(offset / 2f)
                    .setDuration(140L)
                    .withEndAction {
                        if (!isActionMenuOpen) {
                            currentBinding.productionFabMenu.isVisible = false
                        }
                    }
                    .start()

                currentBinding.fabToggleProductionMenu.animate()
                    .rotation(0f)
                    .setDuration(160L)
                    .start()
            } else {
                currentBinding.productionFabMenu.isVisible = false
                currentBinding.productionFabMenu.alpha = 0f
                currentBinding.productionFabMenu.translationY = offset
                currentBinding.productionFabMenu.children.forEach { child ->
                    child.alpha = 0f
                    child.translationY = offset / 2f
                    child.scaleX = 0.92f
                    child.scaleY = 0.92f
                }
                currentBinding.fabToggleProductionMenu.rotation = 0f
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
            runCatching { RepositoriFirebaseUtama.muatRingkasanProduksi() }
                .onSuccess { summary ->
                    val safeBinding = _binding ?: return@onSuccess
                    hasLoadedOnce = true
                    lastLoadedAt = SystemClock.elapsedRealtime()

                    safeBinding.tvProductionTotal.text = "${summary.totalProduksiDasarHariIni} pcs"
                    safeBinding.tvProductionBatch.text = "Batch dasar tercatat: ${summary.totalBatchHariIni}"
                    safeBinding.tvProductionDerived.text = "Konversi tercatat: ${summary.totalKonversiHariIni} transaksi"
                    safeBinding.tvParameterActive.text = "Parameter aktif: ${summary.totalParameterAktif}"
                    safeBinding.tvProductionHistoryCount.text = "Riwayat total: ${summary.totalRiwayat} catatan"

                    recentAdapter.submitList(
                        summary.recentRows.map {
                            ItemBaris(
                                id = it.id,
                                title = it.title,
                                subtitle = it.subtitle,
                                badge = it.badge,
                                amount = it.amount,
                                tone = if (it.badge == "Konversi") WarnaBaris.BLUE else WarnaBaris.GREEN
                            )
                        }
                    )

                    setLoadingState(showLoading = false)
                    isLoading = false
                }
                .onFailure {
                    val safeBinding = _binding ?: return@onFailure
                    if (!hasLoadedOnce) {
                        recentAdapter.submitList(emptyList())
                    }
                    setLoadingState(showLoading = false)
                    isLoading = false
                    showMessage(safeBinding.root, it.message ?: "Gagal memuat ringkasan produksi")
                }
        }
    }

    private fun setLoadingState(showLoading: Boolean) {
        val currentBinding = _binding ?: return
        currentBinding.progressLoadProduction.isVisible = showLoading
        currentBinding.contentProduction.isVisible = !showLoading
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
