package muhamad.irfan.si_tahu.ui.stok

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.data.Produk
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.databinding.ActivityListScreenBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.umum.AdapterBarisUmum
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris
import muhamad.irfan.si_tahu.utilitas.PembantuFilterRiwayat

class AktivitasMonitoringStok : AktivitasDasar() {

    private lateinit var binding: ActivityListScreenBinding

    private val adapter by lazy {
        AdapterBarisUmum(
            onItemClick = { item ->
                val intent = Intent(this, AktivitasDetailStok::class.java)
                intent.putExtra(EXTRA_PRODUCT_ID, item.id)
                startActivity(intent)
            }
        )
    }

    private val statusOptions = listOf("Semua Stok", "Aman", "Menipis", "Habis", "Perlu Tindakan")
    private val pageSize = 5

    private var semuaProduk: List<Produk> = emptyList()
    private var currentPage = 1
    private var totalPages = 1
    private var statusAktif = statusOptions.first()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindToolbar(
            binding.toolbar,
            "Monitoring Stok",
            "Stok jual, stok fisik, dan stok kadaluarsa"
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

        cardPrimaryFilter.visibility = View.GONE
        spPrimaryFilter.visibility = View.GONE
        cardSecondaryFilter.visibility = View.GONE
        cardDateFilter.visibility = View.GONE
        btnOpenFilters.visibility = View.VISIBLE
        tvFilterBadge.visibility = View.GONE
        buttonRow.visibility = View.VISIBLE
        fabAdd.visibility = View.GONE
        tvEmpty.visibility = View.GONE
        paginationContainer.visibility = View.GONE

        btnPrimary.text = "Adjustment"
        btnSecondary.text = "Refresh"
        updateFilterUi()
    }

    private fun setupActions() = with(binding) {
        btnPrimary.setOnClickListener {
            startActivity(Intent(this@AktivitasMonitoringStok, AktivitasStockAdjustment::class.java))
        }

        btnSecondary.setOnClickListener { loadProducts() }

        etSearch.addTextChangedListener {
            currentPage = 1
            renderList()
        }

        btnOpenFilters.setOnClickListener { bukaFilter() }

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

    private fun loadProducts() {
        setEmptyStateVisible(false)
        lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatSemuaProduk() }
                .onSuccess { result ->
                    semuaProduk = result
                        .filter { !it.deleted }
                        .sortedBy { it.name.lowercase() }
                    currentPage = 1
                    renderList()
                }
                .onFailure { e ->
                    showMessage(e.message ?: "Gagal memuat stok")
                    semuaProduk = emptyList()
                    renderList()
                }
        }
    }

    private fun renderList() {
        val keyword = binding.etSearch.text?.toString()?.trim().orEmpty().lowercase()
        val selectedStatus = statusAktif

        val filtered = semuaProduk.filter { produk ->
            val cocokNama = keyword.isBlank() || produk.name.lowercase().contains(keyword)
            val status = statusStok(produk)
            val cocokStatus = when (selectedStatus) {
                "Aman" -> status == "Aman"
                "Menipis" -> status == "Menipis"
                "Habis" -> status == "Habis"
                "Perlu Tindakan" -> produk.expiredStock > 0
                else -> true
            }
            cocokNama && cocokStatus
        }

        totalPages = if (filtered.isEmpty()) 1 else ((filtered.size - 1) / pageSize) + 1
        if (currentPage > totalPages) currentPage = totalPages
        if (currentPage < 1) currentPage = 1

        val fromIndex = (currentPage - 1) * pageSize
        val toIndex = minOf(fromIndex + pageSize, filtered.size)
        val pagedItems = if (filtered.isEmpty()) emptyList() else filtered.subList(fromIndex, toIndex)

        val listItems = pagedItems.map { produk ->
            val layakJual = produk.safeStock + produk.nearExpiredStock
            val status = statusStok(produk)
            val subtitle = buildString {
                append(produk.category)
                append(" • Layak jual ${Formatter.ribuan(layakJual.toLong())} ${produk.unit}")
                append(" • Fisik ${Formatter.ribuan(produk.stock.toLong())} ${produk.unit}")
                if (produk.nearExpiredStock > 0) append(" • Hampir ED ${Formatter.ribuan(produk.nearExpiredStock.toLong())}")
                if (produk.expiredStock > 0) append(" • Kadaluarsa ${Formatter.ribuan(produk.expiredStock.toLong())}")
            }

            ItemBaris(
                id = produk.id,
                title = produk.name,
                subtitle = subtitle,
                badge = if (produk.expiredStock > 0) "Perlu Tindakan" else if (produk.active) "Aktif" else "Nonaktif",
                amount = "${Formatter.ribuan(layakJual.toLong())} ${produk.unit}",
                priceStatus = status,
                parameterStatus = if (produk.expiredStock > 0) "Buang stok kadaluarsa" else produk.stockBatchStatus,
                tone = toneUntukStatus(status, produk.expiredStock),
                priceTone = toneUntukStatus(status, produk.expiredStock),
                parameterTone = if (produk.expiredStock > 0) WarnaBaris.ORANGE else WarnaBaris.BLUE
            )
        }

        adapter.submitList(listItems)

        if (listItems.isEmpty()) {
            val emptyText = if (semuaProduk.isEmpty()) "Belum ada data produk" else "Tidak ada data yang cocok"
            setEmptyStateVisible(true, emptyText)
        } else {
            setEmptyStateVisible(false)
        }

        binding.paginationContainer.visibility = if (filtered.size > pageSize) View.VISIBLE else View.GONE
        binding.tvPageInfo.text = "Halaman $currentPage dari $totalPages"
        binding.btnPagePrev.isEnabled = currentPage > 1
        binding.btnPagePrev.alpha = if (currentPage > 1) 1f else 0.45f
        binding.btnPageNext.isEnabled = currentPage < totalPages
        binding.btnPageNext.alpha = if (currentPage < totalPages) 1f else 0.45f
        updateFilterUi()
    }

    private fun statusStok(produk: Produk): String {
        val layakJual = produk.safeStock + produk.nearExpiredStock
        return when {
            layakJual <= 0 -> "Habis"
            layakJual <= produk.minStock -> "Menipis"
            else -> "Aman"
        }
    }

    private fun toneUntukStatus(status: String, expiredStock: Int): WarnaBaris {
        return when {
            expiredStock > 0 -> WarnaBaris.ORANGE
            status == "Aman" -> WarnaBaris.GREEN
            status == "Menipis" -> WarnaBaris.GOLD
            else -> WarnaBaris.RED
        }
    }

    private fun bukaFilter() {
        PembantuFilterRiwayat.show(
            activity = this,
            kategori = statusOptions,
            kategoriTerpilih = statusAktif,
            tanggalLabel = null,
            jumlahFilterAktif = jumlahFilterAktif(),
            onKategoriDipilih = { pilihan ->
                statusAktif = pilihan
                currentPage = 1
                renderList()
            },
            onPilihTanggal = {},
            onHapusTanggal = {},
            onReset = {
                statusAktif = statusOptions.first()
                currentPage = 1
                renderList()
                showMessage("Filter stok direset")
            },
            kategoriLabel = "Status Stok",
            tampilkanTanggal = false
        )
    }

    private fun jumlahFilterAktif(): Int = if (statusAktif != statusOptions.first()) 1 else 0

    private fun updateFilterUi() {
        binding.tvFilterBadge.visibility = if (jumlahFilterAktif() > 0) View.VISIBLE else View.GONE
        binding.tvFilterBadge.text = jumlahFilterAktif().toString()
    }

    private fun setEmptyStateVisible(visible: Boolean, text: String = "") {
        binding.tvEmpty.visibility = if (visible) View.VISIBLE else View.GONE
        if (text.isNotBlank()) binding.tvEmpty.text = text
        binding.rvList.visibility = if (visible) View.GONE else View.VISIBLE
    }

    companion object {
        const val EXTRA_PRODUCT_ID = "extra_product_id"
    }
}
