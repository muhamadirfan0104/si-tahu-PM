package muhamad.irfan.si_tahu.ui.utama

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.databinding.FragmentStockListBinding
import muhamad.irfan.si_tahu.ui.dasar.FragmenDasar
import muhamad.irfan.si_tahu.ui.produk.AktivitasDaftarProduk
import muhamad.irfan.si_tahu.ui.stok.AktivitasDetailStok
import muhamad.irfan.si_tahu.ui.stok.AktivitasMonitoringStok
import muhamad.irfan.si_tahu.ui.stok.AktivitasStockAdjustment
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
    private var hasLoadedOnce = false
    private var isLoading = false
    private var lastLoadedAt = 0L

    private val adapter by lazy {
        AdapterBarisUmum(
            onItemClick = onItemClick@{ item ->
                val safeContext = context ?: return@onItemClick
                val intent = Intent(safeContext, AktivitasDetailStok::class.java)
                intent.putExtra(AktivitasStockAdjustment.EXTRA_PRODUCT_ID, item.id)
                launchActivitySafely(intent)
            }
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentStockListBinding.bind(view)

        setupView()
        setupActions()
        loadProducts(forceInitialLoading = true)
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null && shouldRefreshData()) {
            loadProducts(forceInitialLoading = !hasLoadedOnce)
        }
    }

    private fun shouldRefreshData(): Boolean {
        if (!hasLoadedOnce) return true
        return SystemClock.elapsedRealtime() - lastLoadedAt > 30_000L
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
            val safeContext = context ?: return@setOnClickListener
            launchActivitySafely(Intent(safeContext, AktivitasStockAdjustment::class.java))
        }

        btnOpenMonitoring.setOnClickListener {
            val safeContext = context ?: return@setOnClickListener
            launchActivitySafely(Intent(safeContext, AktivitasMonitoringStok::class.java))
        }

        btnAllProducts.setOnClickListener {
            val safeContext = context ?: return@setOnClickListener
            launchActivitySafely(Intent(safeContext, AktivitasDaftarProduk::class.java))
        }

        btnRefreshStock.setOnClickListener {
            loadProducts(forceInitialLoading = false)
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

    private fun loadProducts(forceInitialLoading: Boolean) {
        val currentBinding = _binding ?: return
        if (isLoading) return

        isLoading = true
        setLoadingState(showLoading = forceInitialLoading && !hasLoadedOnce)

        firestore.collection("Produk")
            .get()
            .addOnSuccessListener { snapshot ->
                semuaProduk = snapshot.documents
                    .filter { it.getBoolean("dihapus") != true }
                    .map { doc ->
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

                hasLoadedOnce = true
                lastLoadedAt = SystemClock.elapsedRealtime()
                currentPage = 1
                refreshList()
                setLoadingState(showLoading = false)
                isLoading = false
            }
            .addOnFailureListener { e ->
                setLoadingState(showLoading = false)
                isLoading = false
                showMessage(currentBinding.root, "Gagal memuat stok: ${e.message}")
            }
    }

    private fun refreshList() {
        val currentBinding = _binding ?: return
        val keyword = currentBinding.etSearch.text?.toString()?.trim().orEmpty().lowercase()
        val selectedStatus = statusOptions.getOrNull(currentBinding.spStatus.selectedItemPosition)
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

        currentBinding.tvStockTitle.text = "Stok hari ini"
        currentBinding.tvStockTotal.text = "$totalProduk produk"
        currentBinding.tvStockLow.text = "Menipis: $stokMenipis"
        currentBinding.tvStockOut.text = "Habis: $stokHabis"
        currentBinding.tvStockSubtitle.text = "Pantau stok real-time, minimum stok, dan adjustment"

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

        currentBinding.tvEmptyState.visibility = if (pagedItems.isEmpty()) View.VISIBLE else View.GONE
        currentBinding.tvEmptyState.text = if (semuaProduk.isEmpty()) {
            "Belum ada data produk."
        } else {
            "Produk tidak ditemukan."
        }

        currentBinding.paginationContainer.visibility = if (filtered.size > pageSize) View.VISIBLE else View.GONE
        currentBinding.tvPageInfo.text = "Halaman $currentPage dari $totalPages"
        currentBinding.btnPrevPage.isEnabled = currentPage > 1
        currentBinding.btnNextPage.isEnabled = currentPage < totalPages
        currentBinding.btnPrevPage.alpha = if (currentPage > 1) 1f else 0.45f
        currentBinding.btnNextPage.alpha = if (currentPage < totalPages) 1f else 0.45f
    }

    private fun setLoadingState(showLoading: Boolean) {
        val currentBinding = _binding ?: return
        currentBinding.progressLoadStock.isVisible = showLoading
        currentBinding.contentStock.isVisible = !showLoading
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
