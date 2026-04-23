package muhamad.irfan.si_tahu.ui.stok

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.databinding.ActivityListScreenBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.umum.AdapterBarisUmum
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.PembantuPilihTanggalWaktu
import muhamad.irfan.si_tahu.util.WarnaBaris
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

    private var tanggalTunggal: String? = null
    private var rentangMulai: String? = null
    private var rentangSelesai: String? = null

    private val adapter by lazy {
        AdapterBarisUmum(
            onItemClick = onItemClick@{ item ->
                if (item.id.isBlank()) return@onItemClick
                val intent = Intent(this, AktivitasDetailStok::class.java)
                intent.putExtra(AktivitasMonitoringStok.EXTRA_PRODUCT_ID, item.id)
                startActivity(intent)
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindToolbar(
            binding.toolbar,
            "Riwayat Semua Stok",
            "Semua mutasi stok lintas produk • semua tanggal"
        )

        setupView()
        setupActions()
        updateFilterTanggalUi()
        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun setupView() = with(binding) {
        rvList.layoutManager = LinearLayoutManager(this@AktivitasRiwayatSemuaStok)
        rvList.adapter = adapter

        spPrimaryFilter.visibility = View.GONE
        cardSecondaryFilter.visibility = View.GONE
        fabAdd.visibility = View.GONE

        buttonRow.visibility = View.VISIBLE
        btnPrimary.visibility = View.VISIBLE
        btnSecondary.visibility = View.VISIBLE
        btnPrimary.text = "Pilih Tanggal"
        btnSecondary.text = "Pilih Rentang"
    }

    private fun setupActions() = with(binding) {
        etSearch.hint = "Cari produk atau catatan..."

        etSearch.addTextChangedListener {
            currentPage = 1
            renderList()
        }

        btnPrimary.setOnClickListener {
            bukaPilihTanggal()
        }

        btnSecondary.setOnClickListener {
            bukaPilihRentang()
        }

        btnPrimary.setOnLongClickListener {
            resetFilterTanggal()
            true
        }

        btnSecondary.setOnLongClickListener {
            resetFilterTanggal()
            true
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
                        jenisMutasi.contains("ADJUSTMENT_TAMBAH") -> "Adjustment Tambah"
                        jenisMutasi.contains("ADJUSTMENT_KURANG") -> "Adjustment Kurang"
                        jenisMutasi.contains("PENJUALAN") -> "Penjualan"
                        else -> jenisMutasi.ifBlank { "Mutasi Stok" }
                    }

                    RiwayatStokGlobalUi(
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
                            append(stokSebelum)
                            append(" → ")
                            append(stokSesudah)
                        },
                        badge = Formatter.readableDateTime(isoFromTimestamp(waktuMutasi)),
                        amount = when {
                            qtyMasuk > 0L -> "+$qtyMasuk"
                            qtyKeluar > 0L -> "-$qtyKeluar"
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

            cocokKeyword && cocokTanggal
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
                    id = row.productId,
                    title = row.namaProduk,
                    subtitle = row.subtitle,
                    badge = row.title,
                    amount = row.amount,
                    priceStatus = row.badge,
                    parameterStatus = "Tap untuk detail produk",
                    tone = when (row.kategori) {
                        "Adjustment" -> WarnaBaris.BLUE
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
    }

    private fun bukaPilihTanggal() {
        PembantuPilihTanggalWaktu.showDatePicker(this, tanggalTunggal) { hasil ->
            tanggalTunggal = hasil
            rentangMulai = null
            rentangSelesai = null
            currentPage = 1
            updateFilterTanggalUi()
            renderList()
        }
    }

    private fun bukaPilihRentang() {
        PembantuPilihTanggalWaktu.showDatePicker(this, rentangMulai) { mulai ->
            PembantuPilihTanggalWaktu.showDatePicker(this, rentangSelesai ?: mulai) { selesai ->
                val tanggalMulai = Formatter.parseDate(mulai)
                val tanggalSelesai = Formatter.parseDate(selesai)

                if (tanggalMulai.after(tanggalSelesai)) {
                    rentangMulai = selesai
                    rentangSelesai = mulai
                } else {
                    rentangMulai = mulai
                    rentangSelesai = selesai
                }

                tanggalTunggal = null
                currentPage = 1
                updateFilterTanggalUi()
                renderList()
            }
        }
    }

    private fun resetFilterTanggal() {
        tanggalTunggal = null
        rentangMulai = null
        rentangSelesai = null
        currentPage = 1
        updateFilterTanggalUi()
        renderList()
        showMessage("Filter tanggal direset")
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

    private fun updateFilterTanggalUi() {
        binding.btnPrimary.text =
            tanggalTunggal?.let { Formatter.readableShortDate(it) } ?: "Pilih Tanggal"

        binding.btnSecondary.text =
            if (!rentangMulai.isNullOrBlank() && !rentangSelesai.isNullOrBlank()) {
                "${Formatter.readableShortDate(rentangMulai)} - ${Formatter.readableShortDate(rentangSelesai)}"
            } else {
                "Pilih Rentang"
            }

        binding.toolbar.subtitle = when {
            !tanggalTunggal.isNullOrBlank() ->
                "Semua mutasi stok lintas produk • ${Formatter.readableDate(tanggalTunggal)}"
            !rentangMulai.isNullOrBlank() && !rentangSelesai.isNullOrBlank() ->
                "Semua mutasi stok lintas produk • ${Formatter.readableShortDate(rentangMulai)} - ${Formatter.readableShortDate(rentangSelesai)}"
            else ->
                "Semua mutasi stok lintas produk • semua tanggal"
        }
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
}

data class RiwayatStokGlobalUi(
    val productId: String,
    val namaProduk: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val amount: String,
    val kategori: String,
    val dibuatPada: Timestamp
)