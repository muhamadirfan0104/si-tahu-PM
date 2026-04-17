package muhamad.irfan.si_tahu.ui.utama

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.databinding.FragmentStockListBinding
import muhamad.irfan.si_tahu.ui.dasar.FragmenDasar
import muhamad.irfan.si_tahu.ui.stok.AktivitasAdjustmentStok
import muhamad.irfan.si_tahu.ui.stok.AktivitasDetailStok
import muhamad.irfan.si_tahu.ui.stok.AktivitasMonitoringStok
import muhamad.irfan.si_tahu.ui.umum.AdapterBarisUmum
import muhamad.irfan.si_tahu.util.AdapterSpinner
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class FragmenStok : FragmenDasar(R.layout.fragment_stock_list) {

    private var _binding: FragmentStockListBinding? = null
    private val binding get() = _binding!!

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val statusOptions = listOf("Semua Stok", "Aman", "Menipis", "Habis")
    private val pageSize = 10

    private var semuaProduk: List<ProdukStokUi> = emptyList()
    private var currentPage = 1
    private var totalPages = 1

    private val adapter by lazy {
        AdapterBarisUmum(
            onItemClick = { item ->
                val intent = Intent(requireContext(), AktivitasDetailStok::class.java)
                intent.putExtra(AktivitasMonitoringStok.EXTRA_PRODUCT_ID, item.id)
                startActivity(intent)
            }
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentStockListBinding.bind(view)

        setupView()
        setupActions()
        loadProducts()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) loadProducts()
    }

    private fun setupView() = with(binding) {
        rvStock.layoutManager = LinearLayoutManager(requireContext())
        rvStock.adapter = adapter

        spStatus.adapter = AdapterSpinner.stringAdapter(requireContext(), statusOptions)
    }

    private fun setupActions() = with(binding) {
        etSearch.addTextChangedListener {
            currentPage = 1
            refreshList()
        }

        spStatus.onItemSelectedListener = PendengarPilihItemSederhana {
            currentPage = 1
            refreshList()
        }

        btnAdjustment.setOnClickListener {
            startActivity(Intent(requireContext(), AktivitasAdjustmentStok::class.java))
        }

        btnPrevPage.setOnClickListener {
            if (currentPage > 1) {
                currentPage--
                refreshList()
            }
        }

        btnNextPage.setOnClickListener {
            if (currentPage < totalPages) {
                currentPage++
                refreshList()
            }
        }
    }

    private fun loadProducts() {
        firestore.collection("produk")
            .whereEqualTo("dihapus", false)
            .get()
            .addOnSuccessListener { snapshot ->
                semuaProduk = snapshot.documents.map { doc ->
                    ProdukStokUi(
                        id = doc.id,
                        namaProduk = doc.getString("namaProduk").orEmpty(),
                        jenisProduk = doc.getString("jenisProduk").orEmpty(),
                        satuan = doc.getString("satuan").orEmpty(),
                        stokSaatIni = doc.getLong("stokSaatIni") ?: 0L,
                        stokMinimum = doc.getLong("stokMinimum") ?: 0L,
                        aktifDijual = doc.getBoolean("aktifDijual") ?: true,
                        diperbaruiPada = doc.getTimestamp("diperbaruiPada"),
                        dibuatPada = doc.getTimestamp("dibuatPada")
                    )
                }.sortedWith(
                    compareByDescending<ProdukStokUi> { it.diperbaruiPada?.seconds ?: 0L }
                        .thenByDescending { it.dibuatPada?.seconds ?: 0L }
                        .thenBy { it.namaProduk.lowercase() }
                )

                currentPage = 1
                refreshList()
            }
            .addOnFailureListener { e ->
                showMessage(binding.root, "Gagal memuat stok: ${e.message}")
            }
    }

    private fun refreshList() = with(binding) {
        val keyword = etSearch.text?.toString()?.trim().orEmpty().lowercase()
        val selectedStatus = statusOptions.getOrNull(spStatus.selectedItemPosition)
            ?: statusOptions.first()

        val filtered = semuaProduk.filter { produk ->
            val cocokNama = keyword.isBlank() || produk.namaProduk.lowercase().contains(keyword)
            val cocokStatus = when (selectedStatus) {
                "Aman" -> produk.statusStok() == "Aman"
                "Menipis" -> produk.statusStok() == "Menipis"
                "Habis" -> produk.statusStok() == "Habis"
                else -> true
            }
            cocokNama && cocokStatus
        }

        val totalProduk = semuaProduk.size
        val stokMenipis = semuaProduk.count { it.statusStok() == "Menipis" }
        val stokHabis = semuaProduk.count { it.statusStok() == "Habis" }

        tvStockTotal.text = "$totalProduk produk"
        tvStockLow.text = "Menipis: $stokMenipis"
        tvStockOut.text = "Habis: $stokHabis"
        tvStockSubtitle.text = "Pantau stok real-time, minimum stok, dan adjustment"

        totalPages = if (filtered.isEmpty()) 1 else ((filtered.size - 1) / pageSize) + 1
        if (currentPage > totalPages) currentPage = totalPages
        if (currentPage < 1) currentPage = 1

        val fromIndex = (currentPage - 1) * pageSize
        val toIndex = minOf(fromIndex + pageSize, filtered.size)
        val pagedItems = if (filtered.isEmpty()) emptyList() else filtered.subList(fromIndex, toIndex)

        adapter.submitList(
            pagedItems.map { produk ->
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
        )

        tvEmptyState.visibility = if (pagedItems.isEmpty()) View.VISIBLE else View.GONE
        tvEmptyState.text = if (semuaProduk.isEmpty()) {
            "Belum ada data produk."
        } else {
            "Produk tidak ditemukan."
        }

        paginationContainer.visibility = if (filtered.size > pageSize) View.VISIBLE else View.GONE
        tvPageInfo.text = "Halaman $currentPage dari $totalPages"
        btnPrevPage.isEnabled = currentPage > 1
        btnNextPage.isEnabled = currentPage < totalPages
        btnPrevPage.alpha = if (currentPage > 1) 1f else 0.45f
        btnNextPage.alpha = if (currentPage < totalPages) 1f else 0.45f
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

data class ProdukStokUi(
    val id: String,
    val namaProduk: String,
    val jenisProduk: String,
    val satuan: String,
    val stokSaatIni: Long,
    val stokMinimum: Long,
    val aktifDijual: Boolean,
    val diperbaruiPada: Timestamp?,
    val dibuatPada: Timestamp?
) {
    fun statusStok(): String {
        return when {
            stokSaatIni <= 0L -> "Habis"
            stokSaatIni <= stokMinimum -> "Menipis"
            else -> "Aman"
        }
    }
}