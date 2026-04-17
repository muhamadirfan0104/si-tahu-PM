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

class AktivitasDetailStok : AktivitasDasar() {

    private lateinit var binding: ActivityStockDetailBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val movementAdapter by lazy {
        AdapterBarisUmum(onItemClick = {})
    }

    private var productId: String = ""

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
            val intent = Intent(this, AktivitasAdjustmentStok::class.java)
            intent.putExtra(AktivitasMonitoringStok.EXTRA_PRODUCT_ID, productId)
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
        firestore.collection("produk")
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
                val satuan = doc.getString("satuan").orEmpty()
                val stokSaatIni = doc.getLong("stokSaatIni") ?: 0L
                val stokMinimum = doc.getLong("stokMinimum") ?: 0L
                val aktifDijual = doc.getBoolean("aktifDijual") ?: true

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
        firestore.collection("aktivitasStok")
            .whereEqualTo("idProduk", productId)
            .get()
            .addOnSuccessListener { snapshot ->
                val items = snapshot.documents.map { doc ->
                    val jenisAktivitas = doc.getString("jenisAktivitas").orEmpty()
                    val qty = doc.getLong("qty") ?: 0L
                    val satuan = doc.getString("satuan").orEmpty()
                    val catatan = doc.getString("catatan").orEmpty()
                    val dibuatPada = doc.getTimestamp("dibuatPada") ?: Timestamp.now()

                    val (title, tone, amountText) = when {
                        jenisAktivitas.contains("PRODUKSI_DASAR_MASUK") -> Triple(
                            "Produksi Dasar",
                            WarnaBaris.GREEN,
                            "+$qty $satuan"
                        )
                        jenisAktivitas.contains("KONVERSI_MASUK") -> Triple(
                            "Konversi Masuk",
                            WarnaBaris.GREEN,
                            "+$qty $satuan"
                        )
                        jenisAktivitas.contains("KONVERSI_KELUAR") -> Triple(
                            "Konversi Keluar",
                            WarnaBaris.ORANGE,
                            "-$qty $satuan"
                        )
                        jenisAktivitas.contains("ADJUSTMENT_TAMBAH") -> Triple(
                            "Adjustment Tambah",
                            WarnaBaris.BLUE,
                            "+$qty $satuan"
                        )
                        jenisAktivitas.contains("ADJUSTMENT_KURANG") -> Triple(
                            "Adjustment Kurang",
                            WarnaBaris.RED,
                            "-$qty $satuan"
                        )
                        jenisAktivitas.contains("PENJUALAN") -> Triple(
                            "Penjualan",
                            WarnaBaris.RED,
                            "-$qty $satuan"
                        )
                        else -> Triple(
                            jenisAktivitas.ifBlank { "Aktivitas Stok" },
                            WarnaBaris.DEFAULT,
                            "$qty $satuan"
                        )
                    }

                    ItemBaris(
                        id = doc.id,
                        title = title,
                        subtitle = if (catatan.isBlank()) {
                            Formatter.readableDateTime(dibuatPada.toDate().let {
                                java.text.SimpleDateFormat(
                                    "yyyy-MM-dd'T'HH:mm:ss",
                                    java.util.Locale.US
                                ).format(it)
                            })
                        } else {
                            catatan
                        },
                        badge = Formatter.readableDate(dibuatPada.toDate().let {
                            java.text.SimpleDateFormat(
                                "yyyy-MM-dd",
                                java.util.Locale.US
                            ).format(it)
                        }),
                        amount = amountText,
                        tone = tone
                    )
                }.sortedByDescending { it.badge + it.title }

                movementAdapter.submitList(items)
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat riwayat stok: ${e.message}")
            }
    }
}