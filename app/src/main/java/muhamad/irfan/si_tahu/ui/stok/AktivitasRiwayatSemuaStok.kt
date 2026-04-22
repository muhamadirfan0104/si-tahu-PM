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
import muhamad.irfan.si_tahu.ui.utama.PendengarPilihItemSederhana
import muhamad.irfan.si_tahu.util.AdapterSpinner
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris
import java.text.SimpleDateFormat
import java.util.Locale

class AktivitasRiwayatSemuaStok : AktivitasDasar() {

    private lateinit var binding: ActivityListScreenBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val filterOptions = listOf("Semua Mutasi", "Masuk", "Keluar", "Adjustment", "Penjualan", "Produksi")
    private val pageSize = 20

    private var semuaRiwayat: List<RiwayatStokGlobalUi> = emptyList()
    private var currentPage = 1
    private var totalPages = 1

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
            "Semua mutasi stok lintas produk"
        )

        setupView()
        setupActions()
        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun setupView() = with(binding) {
        rvList.layoutManager = LinearLayoutManager(this@AktivitasRiwayatSemuaStok)
        rvList.adapter = adapter
        spPrimaryFilter.adapter = AdapterSpinner.stringAdapter(this@AktivitasRiwayatSemuaStok, filterOptions)
        cardSecondaryFilter.visibility = View.GONE
        buttonRow.visibility = View.GONE
        fabAdd.visibility = View.GONE
    }

    private fun setupActions() = with(binding) {
        etSearch.hint = "Cari produk atau catatan..."

        etSearch.addTextChangedListener {
            currentPage = 1
            renderList()
        }

        spPrimaryFilter.onItemSelectedListener = PendengarPilihItemSederhana {
            currentPage = 1
            renderList()
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
                            append(if (catatan.isNotBlank()) catatan else sumberMutasi.ifBlank { "Perubahan stok" })
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
        val selectedFilter = filterOptions.getOrNull(binding.spPrimaryFilter.selectedItemPosition)
            ?: filterOptions.first()

        val filtered = semuaRiwayat.filter { row ->
            val cocokKeyword = keyword.isBlank() ||
                row.namaProduk.lowercase().contains(keyword) ||
                row.subtitle.lowercase().contains(keyword) ||
                row.title.lowercase().contains(keyword)

            val cocokFilter = when (selectedFilter) {
                "Masuk" -> row.amount.startsWith("+")
                "Keluar" -> row.amount.startsWith("-")
                "Adjustment" -> row.kategori == "Adjustment"
                "Penjualan" -> row.kategori == "Penjualan"
                "Produksi" -> row.kategori == "Produksi"
                else -> true
            }
            cocokKeyword && cocokFilter
        }

        totalPages = if (filtered.isEmpty()) 1 else ((filtered.size - 1) / pageSize) + 1
        if (currentPage > totalPages) currentPage = totalPages
        if (currentPage < 1) currentPage = 1

        val fromIndex = (currentPage - 1) * pageSize
        val toIndex = minOf(fromIndex + pageSize, filtered.size)
        val pagedItems = if (filtered.isEmpty()) emptyList() else filtered.subList(fromIndex, toIndex)

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
        binding.paginationContainer.visibility = if (filtered.size > pageSize) View.VISIBLE else View.GONE
        binding.tvPageInfo.text = "Halaman $currentPage dari $totalPages"
        binding.btnPagePrev.isEnabled = currentPage > 1
        binding.btnPagePrev.alpha = if (currentPage > 1) 1f else 0.45f
        binding.btnPageNext.isEnabled = currentPage < totalPages
        binding.btnPageNext.alpha = if (currentPage < totalPages) 1f else 0.45f
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
