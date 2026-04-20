package muhamad.irfan.si_tahu.ui.stok

import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
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

    private var productId: String = ""
    private var productUnit: String = ""

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
                        "Aman" -> muhamad.irfan.si_tahu.R.drawable.bg_tone_green
                        "Menipis" -> muhamad.irfan.si_tahu.R.drawable.bg_tone_gold
                        else -> muhamad.irfan.si_tahu.R.drawable.bg_tone_red
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
                val items = snapshot.documents
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
                            jenisMutasi.contains("KONVERSI_MASUK") -> "Konversi Masuk"
                            jenisMutasi.contains("KONVERSI_KELUAR") -> "Konversi Keluar"
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

                movementAdapter.submitList(items)
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat riwayat stok: ${e.message}")
            }
    }
}
