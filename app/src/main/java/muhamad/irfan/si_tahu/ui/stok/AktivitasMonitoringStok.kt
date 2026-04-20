package muhamad.irfan.si_tahu.ui.stok

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.databinding.ActivityListScreenBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.umum.AdapterBarisUmum
import muhamad.irfan.si_tahu.ui.utama.PendengarPilihItemSederhana
import muhamad.irfan.si_tahu.util.AdapterSpinner
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class AktivitasMonitoringStok : AktivitasDasar() {

    private lateinit var binding: ActivityListScreenBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val adapter by lazy {
        AdapterBarisUmum(
            onItemClick = { item ->
                val intent = Intent(this, AktivitasDetailStok::class.java)
                intent.putExtra(EXTRA_PRODUCT_ID, item.id)
                startActivity(intent)
            }
        )
    }

    private val statusOptions = listOf("Semua Stok", "Aman", "Menipis", "Habis")
    private var semuaProduk: List<ProdukStokItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindToolbar(
            binding.toolbar,
            "Monitoring Stok",
            "Pantau stok real-time dan status minimum"
        )

        setupView()
        setupActions()
        loadProducts()
    }

    override fun onResume() {
        super.onResume()
        loadProducts()
    }

    private fun setupView() = with(binding) {
        rvList.layoutManager = LinearLayoutManager(this@AktivitasMonitoringStok)
        rvList.adapter = adapter

        spPrimaryFilter.adapter = AdapterSpinner.stringAdapter(
            this@AktivitasMonitoringStok,
            statusOptions
        )

        cardSecondaryFilter.visibility = View.GONE
        buttonRow.visibility = View.VISIBLE
        fabAdd.visibility = View.GONE

        btnPrimary.text = "Adjustment"
        btnSecondary.text = "Refresh"
    }

    private fun setupActions() = with(binding) {
        btnPrimary.setOnClickListener {
            startActivity(Intent(this@AktivitasMonitoringStok, AktivitasStockAdjustment::class.java))
        }

        btnSecondary.setOnClickListener {
            loadProducts()
        }

        etSearch.addTextChangedListener {
            renderList()
        }

        spPrimaryFilter.onItemSelectedListener = PendengarPilihItemSederhana {
            renderList()
        }
    }

    private fun loadProducts() {
        setEmptyStateVisible(false)

        firestore.collection("Produk")
            .get()
            .addOnSuccessListener { snapshot ->
                semuaProduk = snapshot.documents
                    .filter { it.getBoolean("dihapus") != true }
                    .map { doc ->
                    ProdukStokItem(
                        id = doc.id,
                        namaProduk = doc.getString("namaProduk").orEmpty(),
                        jenisProduk = doc.getString("jenisProduk").orEmpty(),
                        satuan = doc.getString("satuan").orEmpty(),
                        stokSaatIni = doc.getLong("stokSaatIni") ?: 0L,
                        stokMinimum = doc.getLong("stokMinimum") ?: 0L,
                        aktifDijual = doc.getBoolean("aktifDijual") ?: true
                    )
                }.sortedBy { it.namaProduk.lowercase() }

                renderList()
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat stok: ${e.message}")
            }
    }

    private fun renderList() {
        val keyword = binding.etSearch.text?.toString()?.trim().orEmpty().lowercase()
        val selectedStatus = statusOptions.getOrNull(binding.spPrimaryFilter.selectedItemPosition)
            ?: statusOptions.first()

        val filtered = semuaProduk.filter { produk ->
            val cocokNama = keyword.isBlank() || produk.namaProduk.lowercase().contains(keyword)
            val status = produk.statusStok()

            val cocokStatus = when (selectedStatus) {
                "Aman" -> status == "Aman"
                "Menipis" -> status == "Menipis"
                "Habis" -> status == "Habis"
                else -> true
            }

            cocokNama && cocokStatus
        }

        val listItems = filtered.map { produk ->
            ItemBaris(
                id = produk.id,
                title = produk.namaProduk,
                subtitle = "${produk.jenisProduk} • Minimum ${produk.stokMinimum} ${produk.satuan}",
                badge = if (produk.aktifDijual) "Aktif" else "Nonaktif",
                amount = "${produk.stokSaatIni} ${produk.satuan}",
                priceStatus = produk.statusStok(),
                parameterStatus = "",
                tone = when (produk.statusStok()) {
                    "Aman" -> WarnaBaris.GREEN
                    "Menipis" -> WarnaBaris.GOLD
                    else -> WarnaBaris.RED
                },
                priceTone = when (produk.statusStok()) {
                    "Aman" -> WarnaBaris.GREEN
                    "Menipis" -> WarnaBaris.GOLD
                    else -> WarnaBaris.RED
                }
            )
        }

        adapter.submitList(listItems)

        if (listItems.isEmpty()) {
            val emptyText = if (semuaProduk.isEmpty()) {
                "Belum ada data produk"
            } else {
                "Tidak ada data yang cocok"
            }
            setEmptyStateVisible(true, emptyText)
        } else {
            setEmptyStateVisible(false)
        }
    }

    private fun setEmptyStateVisible(visible: Boolean, text: String = "") {
        val emptyView = findViewById<TextView?>(R.id.tvEmpty)
        emptyView?.visibility = if (visible) View.VISIBLE else View.GONE
        if (text.isNotBlank()) {
            emptyView?.text = text
        }
    }

    companion object {
        const val EXTRA_PRODUCT_ID = "extra_product_id"
    }
}

data class ProdukStokItem(
    val id: String,
    val namaProduk: String,
    val jenisProduk: String,
    val satuan: String,
    val stokSaatIni: Long,
    val stokMinimum: Long,
    val aktifDijual: Boolean
) {
    fun statusStok(): String {
        return when {
            stokSaatIni <= 0L -> "Habis"
            stokSaatIni <= stokMinimum -> "Menipis"
            else -> "Aman"
        }
    }
}