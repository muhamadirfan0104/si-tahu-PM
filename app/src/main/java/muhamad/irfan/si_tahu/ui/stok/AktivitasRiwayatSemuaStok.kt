package muhamad.irfan.si_tahu.ui.stok

import android.os.Bundle
import android.view.View
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.databinding.ActivityListScreenBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.umum.AdapterBarisUmum
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris
import muhamad.irfan.si_tahu.utilitas.PembantuFilterRiwayat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AktivitasRiwayatSemuaStok : AktivitasDasar() {

    private lateinit var binding: ActivityListScreenBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val pageSize = 5

    private var semuaRiwayat: List<RiwayatStokGlobalUi> = emptyList()
    private var currentPage = 1
    private var totalPages = 1

    private var kategoriAktif = FILTER_SEMUA
    private var tanggalTunggal: String? = null
    private var rentangMulai: String? = null
    private var rentangSelesai: String? = null

    private val adapter by lazy {
        AdapterBarisUmum(
            onItemClick = onItemClick@{ item ->
                if (item.id.isBlank()) return@onItemClick
                lifecycleScope.launch {
                    runCatching { RepositoriFirebaseUtama.buildStockMutationDetailText(item.id) }
                        .onSuccess { detail ->
                            showReceiptModal("Detail Riwayat Stok", detail)
                        }
                        .onFailure {
                            showMessage(it.message ?: "Gagal memuat detail riwayat stok")
                        }
                }
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "", null)
        setupView()
        setupActions()
        updateFilterUi()
        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun setupView() = with(binding) {
        rvList.layoutManager = LinearLayoutManager(this@AktivitasRiwayatSemuaStok)
        rvList.adapter = adapter

        etSearch.hint = "Cari mutasi stok..."
        cardPrimaryFilter.visibility = View.GONE
        spPrimaryFilter.visibility = View.GONE
        cardSecondaryFilter.visibility = View.GONE
        cardDateFilter.visibility = View.GONE
        fabAdd.visibility = View.GONE
        buttonRow.visibility = View.GONE
        btnOpenFilters.visibility = View.VISIBLE
        tvFilterBadge.visibility = View.GONE
    }

    private fun setupActions() = with(binding) {
        etSearch.addTextChangedListener {
            currentPage = 1
            renderList()
        }

        btnOpenFilters.setOnClickListener {
            bukaBottomSheetFilter()
        }

        btnPagePrev.setOnClickListener {
            if (currentPage > 1) {
                currentPage--
                renderList()
            }
        }

        btnPageNext.setOnClickListener {
            if (currentPage < totalPages) {
                currentPage++
                renderList()
            }
        }
    }

    private fun loadData() {
        firestore.collection("RiwayatStok")
            .get()
            .addOnSuccessListener { snapshot ->
                semuaRiwayat = snapshot.documents.map { doc ->
                    val jenisMutasi = doc.getString("jenisMutasi").orEmpty()
                    val qtyMasuk = doc.getLong("qtyMasuk") ?: 0L
                    val qtyKeluar = doc.getLong("qtyKeluar") ?: 0L
                    val stokSebelum = doc.getLong("stokSebelum") ?: 0L
                    val stokSesudah = doc.getLong("stokSesudah") ?: 0L
                    val catatan = doc.getString("catatan").orEmpty()
                    val sumberMutasi = doc.getString("sumberMutasi").orEmpty()
                    val namaProduk = doc.getString("namaProduk").orEmpty().ifBlank { "Produk" }
                    val produkId = doc.getString("idProduk").orEmpty()
                    val waktuMutasi = doc.getTimestamp("tanggalMutasi")
                        ?: doc.getTimestamp("dibuatPada")
                        ?: Timestamp.now()

                    val title = when {
                        jenisMutasi.contains("PRODUKSI_DASAR") -> "Produksi Dasar"
                        jenisMutasi.contains("KONVERSI_MASUK") -> "Produk Olahan Masuk"
                        jenisMutasi.contains("KONVERSI_KELUAR") -> "Produk Olahan Keluar"
                        jenisMutasi.contains("ADJUSTMENT_KADALUARSA") -> "Adjustment Kadaluarsa"
                        jenisMutasi.contains("ADJUSTMENT_TAMBAH") -> "Adjustment Tambah"
                        jenisMutasi.contains("ADJUSTMENT_KURANG") -> "Adjustment Kurang"
                        jenisMutasi.contains("PENJUALAN") -> "Penjualan"
                        else -> jenisMutasi.ifBlank { "Mutasi Stok" }
                    }

                    RiwayatStokGlobalUi(
                        mutationId = doc.id,
                        productId = produkId,
                        namaProduk = namaProduk,
                        title = title,
                        subtitle = buildString {
                            append(
                                if (catatan.isNotBlank()) {
                                    catatan
                                } else {
                                    sumberMutasi.ifBlank { "Perubahan stok" }
                                }
                            )
                            append(" • stok ")
                            append(Formatter.ribuan(stokSebelum))
                            append(" → ")
                            append(Formatter.ribuan(stokSesudah))
                        },
                        badge = Formatter.readableDateTime(isoFromTimestamp(waktuMutasi)),
                        amount = when {
                            qtyMasuk > 0L -> "+${Formatter.ribuan(qtyMasuk)}"
                            qtyKeluar > 0L -> "-${Formatter.ribuan(qtyKeluar)}"
                            else -> "0"
                        },
                        kategori = kategoriUntuk(jenisMutasi, qtyMasuk, qtyKeluar),
                        dibuatPada = waktuMutasi
                    )
                }.sortedByDescending { it.dibuatPada.toDate().time }

                currentPage = 1
                renderList()
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat riwayat stok: ${e.message}")
            }
    }

    private fun renderList() {
        val keyword = binding.etSearch.text?.toString()?.trim().orEmpty().lowercase()

        val filtered = semuaRiwayat.filter { row ->
            val cocokKeyword = keyword.isBlank() ||
                    row.namaProduk.lowercase().contains(keyword) ||
                    row.subtitle.lowercase().contains(keyword) ||
                    row.title.lowercase().contains(keyword)

            val cocokTanggal = cocokFilterTanggal(row.dibuatPada.toDate())
            val cocokKategori = when (kategoriAktif) {
                FILTER_PRODUKSI_DASAR -> row.title.contains("Produksi Dasar", true)
                FILTER_PRODUK_OLAHAN -> row.title.contains("Produk Olahan", true)
                FILTER_PENJUALAN -> row.title.contains("Penjualan", true)
                FILTER_ADJUSTMENT -> row.title.contains("Adjustment", true)
                FILTER_MASUK -> row.amount.startsWith("+")
                FILTER_KELUAR -> row.amount.startsWith("-")
                else -> true
            }

            cocokKeyword && cocokTanggal && cocokKategori
        }

        totalPages = if (filtered.isEmpty()) 1 else ((filtered.size - 1) / pageSize) + 1
        if (currentPage > totalPages) currentPage = totalPages
        if (currentPage < 1) currentPage = 1

        val fromIndex = (currentPage - 1) * pageSize
        val toIndex = minOf(fromIndex + pageSize, filtered.size)
        val pagedItems =
            if (filtered.isEmpty()) emptyList() else filtered.subList(fromIndex, toIndex)

        adapter.submitList(
            pagedItems.map { row ->
                ItemBaris(
                    id = row.mutationId,
                    title = row.namaProduk,
                    subtitle = row.subtitle,
                    badge = row.title,
                    amount = row.amount,
                    priceStatus = row.badge,
                    parameterStatus = "Tap untuk lihat detail",
                    tone = when (row.kategori) {
                        "Adjustment" -> if (row.title.contains("Kadaluarsa", true)) WarnaBaris.ORANGE else WarnaBaris.BLUE
                        "Penjualan" -> WarnaBaris.GOLD
                        "Produksi" -> WarnaBaris.GREEN
                        else -> if (row.amount.startsWith("+")) WarnaBaris.GREEN else WarnaBaris.RED
                    },
                    priceTone = WarnaBaris.DEFAULT,
                    parameterTone = WarnaBaris.BLUE
                )
            }
        )

        binding.tvEmpty.visibility = if (pagedItems.isEmpty()) View.VISIBLE else View.GONE
        binding.tvEmpty.text = if (semuaRiwayat.isEmpty()) {
            "Belum ada riwayat stok"
        } else {
            "Riwayat stok tidak ditemukan"
        }
        binding.rvList.visibility = if (pagedItems.isEmpty()) View.GONE else View.VISIBLE
        binding.paginationContainer.visibility =
            if (filtered.size > pageSize) View.VISIBLE else View.GONE
        binding.tvPageInfo.text = "Halaman $currentPage dari $totalPages"
        binding.btnPagePrev.isEnabled = currentPage > 1
        binding.btnPagePrev.alpha = if (currentPage > 1) 1f else 0.45f
        binding.btnPageNext.isEnabled = currentPage < totalPages
        binding.btnPageNext.alpha = if (currentPage < totalPages) 1f else 0.45f

        updateFilterUi()
    }

    private fun bukaBottomSheetFilter() {
        PembantuFilterRiwayat.show(
            activity = this,
            kategori = listOf(
                FILTER_SEMUA,
                FILTER_PRODUKSI_DASAR,
                FILTER_PRODUK_OLAHAN,
                FILTER_PENJUALAN,
                FILTER_ADJUSTMENT,
                FILTER_MASUK,
                FILTER_KELUAR
            ),
            kategoriTerpilih = kategoriAktif,
            tanggalLabel = labelDateRangeUntukField(),
            jumlahFilterAktif = jumlahFilterAktif(),
            onKategoriDipilih = {
                kategoriAktif = it
                currentPage = 1
                renderList()
            },
            onPilihTanggal = { bukaDateRangePicker() },
            onHapusTanggal = {
                clearDateFilter(showToast = true)
            },
            onReset = {
                resetSemuaFilter()
            }
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
            renderList()
        }

        picker.show(supportFragmentManager, "filter_range_stok")
    }

    private fun resetSemuaFilter() {
        kategoriAktif = FILTER_SEMUA
        clearDateFilter(showToast = false)
        currentPage = 1
        renderList()
        showMessage("Semua filter direset")
    }

    private fun clearDateFilter(showToast: Boolean) {
        tanggalTunggal = null
        rentangMulai = null
        rentangSelesai = null
        currentPage = 1
        renderList()
        if (showToast) {
            showMessage("Filter tanggal dihapus")
        }
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

    private fun updateFilterUi() {
        binding.tvFilterBadge.visibility = if (jumlahFilterAktif() > 0) View.VISIBLE else View.GONE
        binding.tvFilterBadge.text = if (jumlahFilterAktif() > 9) "9+" else jumlahFilterAktif().toString()
        binding.toolbar.subtitle = null
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
        if (kategoriAktif != FILTER_SEMUA) total++
        if (punyaFilterTanggal()) total++
        return total
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

    private fun kategoriUntuk(jenisMutasi: String, qtyMasuk: Long, qtyKeluar: Long): String {
        return when {
            jenisMutasi.contains("ADJUSTMENT") -> "Adjustment"
            jenisMutasi.contains("PENJUALAN") -> "Penjualan"
            jenisMutasi.contains("PRODUKSI") || jenisMutasi.contains("KONVERSI") -> "Produksi"
            qtyMasuk > 0L -> "Masuk"
            qtyKeluar > 0L -> "Keluar"
            else -> "Lainnya"
        }
    }

    private fun isoFromTimestamp(timestamp: Timestamp?): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        return formatter.format((timestamp ?: Timestamp.now()).toDate())
    }

    companion object {
        private const val FILTER_SEMUA = "Semua"
        private const val FILTER_PRODUKSI_DASAR = "Produksi Dasar"
        private const val FILTER_PRODUK_OLAHAN = "Produk Olahan"
        private const val FILTER_PENJUALAN = "Penjualan"
        private const val FILTER_ADJUSTMENT = "Adjustment"
        private const val FILTER_MASUK = "Masuk"
        private const val FILTER_KELUAR = "Keluar"
    }
}

data class RiwayatStokGlobalUi(
    val mutationId: String,
    val productId: String,
    val namaProduk: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val amount: String,
    val kategori: String,
    val dibuatPada: Timestamp
)
