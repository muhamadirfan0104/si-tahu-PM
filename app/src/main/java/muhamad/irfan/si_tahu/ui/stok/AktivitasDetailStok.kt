package muhamad.irfan.si_tahu.ui.stok

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.databinding.ActivityStockDetailBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.umum.AdapterBarisUmum
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris
import java.text.SimpleDateFormat
import java.util.Locale

class AktivitasDetailStok : AktivitasDasar() {

    private lateinit var binding: ActivityStockDetailBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val movementAdapter by lazy {
        AdapterBarisUmum(onItemClick = {})
    }

    private val pageSize = 5

    private var productId: String = ""
    private var productUnit: String = ""
    private var allMovements: List<ItemBaris> = emptyList()
    private var currentPage = 1
    private var totalPages = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindToolbar(
            binding.toolbar,
            "Detail Stok",
            "Lihat posisi stok dan riwayat pergerakan"
        )

        productId = intent.getStringExtra(AktivitasMonitoringStok.EXTRA_PRODUCT_ID).orEmpty()
        if (productId.isBlank()) {
            showMessage("Produk tidak valid.")
            finish()
            return
        }

        binding.rvMovement.layoutManager = LinearLayoutManager(this)
        binding.rvMovement.adapter = movementAdapter

        binding.btnAdjustStock.setOnClickListener {
            val intent = Intent(this, AktivitasStockAdjustment::class.java)
            intent.putExtra(AktivitasStockAdjustment.EXTRA_PRODUCT_ID, productId)
            startActivity(intent)
        }

        binding.btnPagePrev.setOnClickListener {
            if (currentPage > 1) {
                currentPage--
                renderMovement()
            }
        }

        binding.btnPageNext.setOnClickListener {
            if (currentPage < totalPages) {
                currentPage++
                renderMovement()
            }
        }

        loadDetail()
        loadMovement()
    }

    override fun onResume() {
        super.onResume()
        if (productId.isNotBlank()) {
            loadDetail()
            loadMovement()
        }
    }

    private fun loadDetail() {
        firestore.collection("Produk")
            .document(productId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    showMessage("Produk tidak ditemukan.")
                    finish()
                    return@addOnSuccessListener
                }

                val namaProduk = doc.getString("namaProduk").orEmpty()
                val jenisProduk = doc.getString("jenisProduk").orEmpty()
                val satuan = doc.getString("satuan").orEmpty().ifBlank { "pcs" }
                val stokSaatIni = doc.getLong("stokSaatIni") ?: 0L
                val stokMinimum = doc.getLong("stokMinimum") ?: 0L
                val aktifDijual = doc.getBoolean("aktifDijual") ?: true

                productUnit = satuan

                val status = when {
                    stokSaatIni <= 0L -> "Habis"
                    stokSaatIni <= stokMinimum -> "Menipis"
                    else -> "Aman"
                }

                binding.tvProductName.text = namaProduk
                binding.tvProductMeta.text = "$jenisProduk • ${if (aktifDijual) "Aktif" else "Nonaktif"}"
                binding.tvStockNow.text = "Stok saat ini: $stokSaatIni $satuan"
                binding.tvMinStock.text = "Stok minimum: $stokMinimum $satuan"
                binding.tvStatus.text = status
                binding.tvStatus.setBackgroundResource(
                    when (status) {
                        "Aman" -> R.drawable.bg_tone_green
                        "Menipis" -> R.drawable.bg_tone_gold
                        else -> R.drawable.bg_tone_red
                    }
                )
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat detail stok: ${e.message}")
            }
    }

    private fun loadMovement() {
        firestore.collection("RiwayatStok")
            .whereEqualTo("idProduk", productId)
            .get()
            .addOnSuccessListener { snapshot ->
                allMovements = snapshot.documents
                    .sortedByDescending {
                        (it.getTimestamp("tanggalMutasi") ?: it.getTimestamp("dibuatPada"))
                            ?.toDate()
                            ?.time ?: 0L
                    }
                    .map { doc ->
                        val jenisMutasi = doc.getString("jenisMutasi").orEmpty()
                        val sumberMutasi = doc.getString("sumberMutasi").orEmpty()
                        val qtyMasuk = doc.getLong("qtyMasuk") ?: 0L
                        val qtyKeluar = doc.getLong("qtyKeluar") ?: 0L
                        val stokSebelum = doc.getLong("stokSebelum") ?: 0L
                        val stokSesudah = doc.getLong("stokSesudah") ?: 0L
                        val catatan = doc.getString("catatan").orEmpty()
                        val waktuMutasi = doc.getTimestamp("tanggalMutasi")
                            ?: doc.getTimestamp("dibuatPada")
                            ?: Timestamp.now()

                        val tanggalLabel = SimpleDateFormat(
                            "yyyy-MM-dd'T'HH:mm:ss",
                            Locale.US
                        ).format(waktuMutasi.toDate())

                        val title = when {
                            jenisMutasi.contains("PRODUKSI_DASAR_MASUK") -> "Produksi Dasar"
                            jenisMutasi.contains("KONVERSI_MASUK") -> "Produk Olahan Masuk"
                            jenisMutasi.contains("KONVERSI_KELUAR") -> "Produk Olahan Keluar"
                            jenisMutasi.contains("ADJUSTMENT_TAMBAH") -> "Penyesuaian Stok (+)"
                            jenisMutasi.contains("ADJUSTMENT_KURANG") -> "Penyesuaian Stok (-)"
                            jenisMutasi.contains("PENJUALAN") -> "Penjualan"
                            else -> jenisMutasi.ifBlank { "Mutasi Stok" }
                        }

                        val tone = when {
                            qtyMasuk > 0L && qtyKeluar <= 0L -> WarnaBaris.GREEN
                            qtyKeluar > 0L && qtyMasuk <= 0L -> WarnaBaris.RED
                            jenisMutasi.contains("ADJUSTMENT") -> WarnaBaris.BLUE
                            else -> WarnaBaris.DEFAULT
                        }

                        val amountText = when {
                            qtyMasuk > 0L -> "+$qtyMasuk ${productUnit.ifBlank { "pcs" }}"
                            qtyKeluar > 0L -> "-$qtyKeluar ${productUnit.ifBlank { "pcs" }}"
                            else -> "0 ${productUnit.ifBlank { "pcs" }}"
                        }

                        val subtitle = buildString {
                            append(
                                if (catatan.isNotBlank()) catatan
                                else sumberMutasi.ifBlank { "Perubahan stok" }
                            )
                            append(" • stok ")
                            append(stokSebelum)
                            append(" → ")
                            append(stokSesudah)
                        }

                        ItemBaris(
                            id = doc.id,
                            title = title,
                            subtitle = subtitle,
                            badge = Formatter.readableDateTime(tanggalLabel),
                            amount = amountText,
                            tone = tone,
                            priceStatus = jenisMutasi,
                            parameterStatus = Formatter.readableDate(tanggalLabel)
                        )
                    }

                currentPage = 1
                renderMovement()
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat riwayat stok: ${e.message}")
            }
    }

    private fun renderMovement() {
        totalPages = if (allMovements.isEmpty()) 1 else ((allMovements.size - 1) / pageSize) + 1
        if (currentPage > totalPages) currentPage = totalPages
        if (currentPage < 1) currentPage = 1

        val fromIndex = (currentPage - 1) * pageSize
        val toIndex = minOf(fromIndex + pageSize, allMovements.size)
        val pagedItems = if (allMovements.isEmpty()) emptyList() else allMovements.subList(fromIndex, toIndex)

        movementAdapter.submitList(pagedItems)

        binding.tvEmptyMovement.visibility = if (pagedItems.isEmpty()) View.VISIBLE else View.GONE
        binding.rvMovement.visibility = if (pagedItems.isEmpty()) View.GONE else View.VISIBLE

        binding.paginationContainer.visibility = if (allMovements.size > pageSize) View.VISIBLE else View.GONE
        binding.tvPageInfo.text = "Halaman $currentPage dari $totalPages"
        binding.btnPagePrev.isEnabled = currentPage > 1
        binding.btnPagePrev.alpha = if (currentPage > 1) 1f else 0.45f
        binding.btnPageNext.isEnabled = currentPage < totalPages
        binding.btnPageNext.alpha = if (currentPage < totalPages) 1f else 0.45f
    }
}