package muhamad.irfan.si_tahu.ui.stok

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
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
        AdapterBarisUmum(
            onItemClick = { item ->
                lifecycleScope.launch {
                    runCatching { RepositoriFirebaseUtama.buildStockMutationDetailText(item.id) }
                        .onSuccess { detail -> showReceiptModal("Detail Riwayat Stok", detail) }
                        .onFailure { showMessage(it.message ?: "Gagal memuat detail riwayat stok") }
                }
            }
        )
    }

    private val pageSize = 5

    private var productId: String = ""
    private var productUnit: String = ""
    private var allMovements: List<ItemBaris> = emptyList()
    private var currentPage = 1
    private var totalPages = 1
    private var expiredStockForAction = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindToolbar(
            binding.toolbar,
            "Detail Stok",
            "Pisahkan stok jual, stok fisik, dan stok perlu tindakan"
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

        binding.btnDisposeExpiredStock.setOnClickListener {
            val intent = Intent(this, AktivitasStockAdjustment::class.java)
            intent.putExtra(AktivitasStockAdjustment.EXTRA_PRODUCT_ID, productId)
            intent.putExtra(AktivitasStockAdjustment.EXTRA_EXPIRED_MODE, true)
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

                binding.tvProductName.text = namaProduk
                binding.tvProductMeta.text = "$jenisProduk • ${if (aktifDijual) "Aktif" else "Nonaktif"}"
                binding.tvMinStock.text = "Stok minimum: ${Formatter.ribuan(stokMinimum)} $satuan"
                loadBatchSummary(satuan, stokSaatIni, stokMinimum)
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat detail stok: ${e.message}")
            }
    }

    private fun loadBatchSummary(satuan: String, stokFisik: Long, stokMinimum: Long) {
        firestore.collection("BatchStok")
            .whereEqualTo("idProduk", productId)
            .get()
            .addOnSuccessListener { snapshot ->
                val aktif = snapshot.documents.filter { (it.getLong("qtySisa") ?: 0L) > 0L }
                val today = Formatter.currentDateOnly()
                val tomorrow = nextDayKey(today)
                var aman = 0L
                var hampir = 0L
                var kadaluarsa = 0L
                var nearestExpiry = ""

                aktif.forEach { doc ->
                    val qty = doc.getLong("qtySisa") ?: 0L
                    val expiry = doc.getString("kunciTanggalKadaluarsa").orEmpty()
                    when {
                        expiry.isBlank() -> aman += qty
                        expiry < today -> kadaluarsa += qty
                        expiry <= tomorrow -> {
                            hampir += qty
                            if (nearestExpiry.isBlank() || expiry < nearestExpiry) nearestExpiry = expiry
                        }
                        else -> {
                            aman += qty
                            if (nearestExpiry.isBlank() || expiry < nearestExpiry) nearestExpiry = expiry
                        }
                    }
                }

                val totalBatchQty = aman + hampir + kadaluarsa
                val stokLamaTanpaBatch = (stokFisik - totalBatchQty).coerceAtLeast(0L)
                aman += stokLamaTanpaBatch

                val stokLayakJual = aman + hampir
                expiredStockForAction = kadaluarsa
                binding.btnDisposeExpiredStock.visibility = if (kadaluarsa > 0L) View.VISIBLE else View.GONE

                binding.tvStockNow.text = "Stok layak jual: ${Formatter.ribuan(stokLayakJual)} $satuan"
                binding.tvBatchSummary.text = buildString {
                    append("Aman: ${Formatter.ribuan(aman)} $satuan")
                    append("\nHampir kadaluarsa: ${Formatter.ribuan(hampir)} $satuan")
                    append("\nKadaluarsa / perlu tindakan: ${Formatter.ribuan(kadaluarsa)} $satuan")
                    append("\nTotal fisik: ${Formatter.ribuan(stokFisik)} $satuan")
                    if (stokLamaTanpaBatch > 0L) append("\nStok lama tanpa batch: ${Formatter.ribuan(stokLamaTanpaBatch)} $satuan")
                    if (nearestExpiry.isNotBlank()) append("\nED terdekat: ${Formatter.readableShortDate(nearestExpiry)}")
                    if (kadaluarsa > 0L) append("\nTindakan: buang/adjust stok kadaluarsa agar tidak menggantung.")
                }

                val status = when {
                    stokLayakJual <= 0L && kadaluarsa > 0L -> "Perlu Tindakan"
                    stokLayakJual <= 0L -> "Habis"
                    kadaluarsa > 0L -> "Perlu Tindakan"
                    hampir > 0L -> "Hampir Kadaluarsa"
                    stokLayakJual <= stokMinimum -> "Menipis"
                    else -> "Aman"
                }

                binding.tvStatus.text = status
                binding.tvStatus.setBackgroundResource(
                    when (status) {
                        "Aman" -> R.drawable.bg_tone_green
                        "Menipis", "Hampir Kadaluarsa" -> R.drawable.bg_tone_gold
                        "Perlu Tindakan" -> R.drawable.bg_tone_orange
                        else -> R.drawable.bg_tone_red
                    }
                )
            }
            .addOnFailureListener {
                binding.tvStockNow.text = "Stok fisik: ${Formatter.ribuan(stokFisik)} $satuan"
                binding.tvBatchSummary.text = "Batch stok: gagal dimuat"
                binding.btnDisposeExpiredStock.visibility = View.GONE
            }
    }

    private fun nextDayKey(dateKey: String): String {
        val cal = java.util.Calendar.getInstance().apply {
            time = Formatter.parseDate("${dateKey}T00:00:00")
            add(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        return java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
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
                            jenisMutasi.contains("ADJUSTMENT_KADALUARSA") -> "Adjustment Kadaluarsa"
                            jenisMutasi.contains("PRODUKSI_DASAR_MASUK") -> "Produksi Dasar"
                            jenisMutasi.contains("KONVERSI_MASUK") -> "Produk Olahan Masuk"
                            jenisMutasi.contains("KONVERSI_KELUAR") -> "Produk Olahan Keluar"
                            jenisMutasi.contains("ADJUSTMENT_TAMBAH") -> "Penyesuaian Stok (+)"
                            jenisMutasi.contains("ADJUSTMENT_KURANG") -> "Penyesuaian Stok (-)"
                            jenisMutasi.contains("PENJUALAN") -> "Penjualan"
                            else -> jenisMutasi.ifBlank { "Mutasi Stok" }
                        }

                        val tone = when {
                            jenisMutasi.contains("ADJUSTMENT_KADALUARSA") -> WarnaBaris.ORANGE
                            qtyMasuk > 0L && qtyKeluar <= 0L -> WarnaBaris.GREEN
                            qtyKeluar > 0L && qtyMasuk <= 0L -> WarnaBaris.RED
                            jenisMutasi.contains("ADJUSTMENT") -> WarnaBaris.BLUE
                            else -> WarnaBaris.DEFAULT
                        }

                        val amountText = when {
                            qtyMasuk > 0L -> "+${Formatter.ribuan(qtyMasuk)} ${productUnit.ifBlank { "pcs" }}"
                            qtyKeluar > 0L -> "-${Formatter.ribuan(qtyKeluar)} ${productUnit.ifBlank { "pcs" }}"
                            else -> "0 ${productUnit.ifBlank { "pcs" }}"
                        }

                        val subtitle = buildString {
                            append(if (catatan.isNotBlank()) catatan else sumberMutasi.ifBlank { "Perubahan stok" })
                            append(" • stok ")
                            append(Formatter.ribuan(stokSebelum))
                            append(" → ")
                            append(Formatter.ribuan(stokSesudah))
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
