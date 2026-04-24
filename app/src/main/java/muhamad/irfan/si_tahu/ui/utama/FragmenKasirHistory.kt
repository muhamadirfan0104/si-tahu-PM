package muhamad.irfan.si_tahu.ui.utama

import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.databinding.FragmentCashierHistoryBinding
import muhamad.irfan.si_tahu.ui.dasar.FragmenDasar
import muhamad.irfan.si_tahu.ui.umum.AdapterBarisUmum
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris
import muhamad.irfan.si_tahu.utilitas.PembantuFilterRiwayat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class FragmenKasirHistory : FragmenDasar(R.layout.fragment_cashier_history) {

    private var _binding: FragmentCashierHistoryBinding? = null
    private val binding get() = _binding!!

    private val historyAdapter = AdapterBarisUmum(
        onItemClick = ::openDetail,
        onActionClick = ::showActionMenu
    )

    private val pageSize = 5

    private var semuaRows: List<RiwayatKasirUiRow> = emptyList()
    private var filteredRows: List<RiwayatKasirUiRow> = emptyList()
    private var currentPage = 1
    private var totalPages = 1

    private var statusAktif = FILTER_SEMUA
    private var tanggalTunggal: String? = null
    private var rentangMulai: String? = null
    private var rentangSelesai: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentCashierHistoryBinding.bind(view)

        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = historyAdapter
        binding.tvFilterBadge.isVisible = false

        binding.btnOpenFilters.setOnClickListener {
            bukaFilter()
        }

        binding.etSearch.addTextChangedListener {
            currentPage = 1
            refresh()
        }

        binding.btnPagePrev.setOnClickListener {
            if (currentPage > 1) {
                currentPage--
                refresh()
            }
        }

        binding.btnPageNext.setOnClickListener {
            if (currentPage < totalPages) {
                currentPage++
                refresh()
            }
        }

        buildRows()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) buildRows()
    }

    private fun buildRows() {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatRiwayatPenjualan() }
                .onSuccess { sales ->
                    semuaRows = sales
                        .filter { it.badge.equals("Rumahan", ignoreCase = true) }
                        .sortedByDescending { it.tanggalIso }
                        .map {
                            val statusLabel = if (it.statusPenjualan.equals("BATAL", true)) {
                                FILTER_BATAL
                            } else {
                                FILTER_SELESAI
                            }

                            RiwayatKasirUiRow(
                                tanggalIso = it.tanggalIso,
                                item = ItemBaris(
                                    id = it.id,
                                    title = it.title,
                                    subtitle = it.subtitle,
                                    amount = it.amount,
                                    badge = it.badge,
                                    tone = WarnaBaris.GREEN,
                                    parameterStatus = statusLabel,
                                    parameterTone = if (statusLabel == FILTER_BATAL) WarnaBaris.RED else WarnaBaris.GREEN,
                                    actionLabel = "⋮"
                                )
                            )
                        }

                    currentPage = 1
                    refresh()
                }
                .onFailure {
                    semuaRows = emptyList()
                    filteredRows = emptyList()
                    historyAdapter.submitList(emptyList())
                    binding.tvEmpty.isVisible = true
                    binding.paginationContainer.isVisible = false
                    updateFilterUi()
                    showMessage(binding.root, it.message ?: "Gagal memuat riwayat penjualan")
                }
        }
    }

    private fun refresh() {
        val keyword = binding.etSearch.text?.toString().orEmpty().trim().lowercase()

        filteredRows = semuaRows.filter { row ->
            val item = row.item
            val cocokKeyword = keyword.isBlank() ||
                    item.title.lowercase().contains(keyword) ||
                    item.subtitle.lowercase().contains(keyword) ||
                    item.badge.lowercase().contains(keyword) ||
                    item.parameterStatus.lowercase().contains(keyword)

            val cocokStatus = when (statusAktif) {
                FILTER_SELESAI -> item.parameterStatus.equals(FILTER_SELESAI, true)
                FILTER_BATAL -> item.parameterStatus.equals(FILTER_BATAL, true)
                else -> true
            }

            val cocokTanggal = cocokFilterTanggal(Formatter.parseDate(row.tanggalIso))

            cocokKeyword && cocokStatus && cocokTanggal
        }

        totalPages = if (filteredRows.isEmpty()) 1 else ((filteredRows.size - 1) / pageSize) + 1
        if (currentPage > totalPages) currentPage = totalPages
        if (currentPage < 1) currentPage = 1

        val fromIndex = (currentPage - 1) * pageSize
        val toIndex = minOf(fromIndex + pageSize, filteredRows.size)
        val pagedRows = if (filteredRows.isEmpty()) emptyList() else filteredRows.subList(fromIndex, toIndex)

        historyAdapter.submitList(pagedRows.map { it.item })

        binding.tvEmpty.isVisible = pagedRows.isEmpty()
        binding.tvEmpty.text = if (semuaRows.isEmpty()) {
            "Belum ada riwayat penjualan"
        } else {
            "Tidak ada data yang cocok"
        }
        binding.rvHistory.isVisible = pagedRows.isNotEmpty()

        binding.paginationContainer.isVisible = filteredRows.size > pageSize
        binding.tvPageInfo.text = "Halaman $currentPage dari $totalPages"
        binding.btnPagePrev.isEnabled = currentPage > 1
        binding.btnPagePrev.alpha = if (currentPage > 1) 1f else 0.45f
        binding.btnPageNext.isEnabled = currentPage < totalPages
        binding.btnPageNext.alpha = if (currentPage < totalPages) 1f else 0.45f
        updateFilterUi()
    }

    private fun bukaFilter() {
        PembantuFilterRiwayat.show(
            activity = requireActivity() as AppCompatActivity,
            kategori = listOf(FILTER_SEMUA, FILTER_SELESAI, FILTER_BATAL),
            kategoriTerpilih = statusAktif,
            tanggalLabel = labelDateRangeUntukField(),
            jumlahFilterAktif = jumlahFilterAktif(),
            onKategoriDipilih = { pilihan ->
                statusAktif = pilihan
                currentPage = 1
                refresh()
            },
            onPilihTanggal = { bukaDateRangePicker() },
            onHapusTanggal = { clearDateFilter(showToast = true) },
            onReset = { resetSemuaFilter() },
            kategoriLabel = "Status Transaksi"
        )
    }

    private fun bukaDateRangePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Pilih rentang")
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            val start = selection.first ?: return@addOnPositiveButtonClickListener
            val end = selection.second ?: start
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val mulai = formatter.format(Date(start))
            val selesai = formatter.format(Date(end))

            if (isSameDay(Formatter.parseDate(mulai), Formatter.parseDate(selesai))) {
                tanggalTunggal = mulai
                rentangMulai = null
                rentangSelesai = null
            } else if (Formatter.parseDate(mulai).after(Formatter.parseDate(selesai))) {
                tanggalTunggal = null
                rentangMulai = selesai
                rentangSelesai = mulai
            } else {
                tanggalTunggal = null
                rentangMulai = mulai
                rentangSelesai = selesai
            }

            currentPage = 1
            refresh()
        }

        picker.show(parentFragmentManager, "filter_range_kasir_history")
    }

    private fun resetSemuaFilter() {
        statusAktif = FILTER_SEMUA
        clearDateFilter(showToast = false)
        currentPage = 1
        refresh()
        showMessage(binding.root, "Semua filter direset")
    }

    private fun clearDateFilter(showToast: Boolean) {
        tanggalTunggal = null
        rentangMulai = null
        rentangSelesai = null
        currentPage = 1
        refresh()
        if (showToast) showMessage(binding.root, "Filter tanggal dihapus")
    }

    private fun cocokFilterTanggal(tanggalData: Date): Boolean {
        tanggalTunggal?.let { single ->
            val target = Formatter.parseDate(single)
            return isSameDay(tanggalData, target)
        }

        if (!rentangMulai.isNullOrBlank() && !rentangSelesai.isNullOrBlank()) {
            val mulai = startOfDay(Formatter.parseDate(rentangMulai))
            val selesai = endOfDay(Formatter.parseDate(rentangSelesai))
            return !tanggalData.before(mulai) && !tanggalData.after(selesai)
        }

        return true
    }

    private fun labelDateRangeUntukField(): String? {
        return when {
            !tanggalTunggal.isNullOrBlank() -> Formatter.readableDate(tanggalTunggal)
            !rentangMulai.isNullOrBlank() && !rentangSelesai.isNullOrBlank() ->
                "${Formatter.readableShortDate(rentangMulai)} - ${Formatter.readableShortDate(rentangSelesai)}"
            else -> null
        }
    }

    private fun jumlahFilterAktif(): Int {
        var total = 0
        if (statusAktif != FILTER_SEMUA) total++
        if (punyaFilterTanggal()) total++
        return total
    }

    private fun updateFilterUi() {
        val total = jumlahFilterAktif()
        binding.tvFilterBadge.isVisible = total > 0
        binding.tvFilterBadge.text = if (total > 9) "9+" else total.toString()
    }

    private fun punyaFilterTanggal(): Boolean {
        return !tanggalTunggal.isNullOrBlank() ||
                (!rentangMulai.isNullOrBlank() && !rentangSelesai.isNullOrBlank())
    }

    private fun isSameDay(first: Date, second: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = first }
        val cal2 = Calendar.getInstance().apply { time = second }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun startOfDay(date: Date): Date {
        return Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    private fun endOfDay(date: Date): Date {
        return Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.time
    }

    private fun openDetail(item: ItemBaris) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.buildReceiptText(item.id) }
                .onSuccess { detail ->
                    showReceiptModal("Detail Penjualan", detail)
                }
                .onFailure {
                    showMessage(binding.root, it.message ?: "Gagal memuat detail penjualan")
                }
        }
    }

    private fun showActionMenu(item: ItemBaris, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add("Lihat detail")
            menu.add("Bagikan")
            if (!item.parameterStatus.equals(FILTER_BATAL, true)) {
                menu.add("Batalkan penjualan")
            }

            setOnMenuItemClickListener {
                when (it.title.toString()) {
                    "Lihat detail" -> openDetail(item)
                    "Bagikan" -> shareItem(item)
                    "Batalkan penjualan" -> confirmCancel(item)
                }
                true
            }
        }.show()
    }

    private fun shareItem(item: ItemBaris) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.buildReceiptText(item.id) }
                .onSuccess { detail ->
                    sharePlainText("Penjualan ${item.title}", detail)
                }
                .onFailure {
                    showMessage(binding.root, it.message ?: "Gagal membagikan detail penjualan")
                }
        }
    }

    private fun confirmCancel(item: ItemBaris) {
        showInputModal(
            title = "Batalkan penjualan",
            hint = "Alasan pembatalan",
            confirmLabel = "Batalkan"
        ) { alasan ->
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching { RepositoriFirebaseUtama.batalkanPenjualan(item.id, alasan, currentUserId()) }
                    .onSuccess {
                        buildRows()
                        showMessage(binding.root, "Penjualan berhasil dibatalkan")
                    }
                    .onFailure {
                        showMessage(binding.root, it.message ?: "Gagal membatalkan penjualan")
                    }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val FILTER_SEMUA = "Semua"
        private const val FILTER_SELESAI = "Selesai"
        private const val FILTER_BATAL = "Batal"
    }
}

private data class RiwayatKasirUiRow(
    val tanggalIso: String,
    val item: ItemBaris
)
