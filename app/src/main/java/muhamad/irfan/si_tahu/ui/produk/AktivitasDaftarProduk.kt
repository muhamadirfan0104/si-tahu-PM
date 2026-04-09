package muhamad.irfan.si_tahu.ui.produk

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDaftarDasar
import muhamad.irfan.si_tahu.ui.harga.AktivitasDaftarHarga
import muhamad.irfan.si_tahu.ui.parameter.AktivitasDaftarParameter
import muhamad.irfan.si_tahu.util.EkstraAplikasi
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class AktivitasDaftarProduk : AktivitasDaftarDasar() {

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val categories = listOf("Semua", "DASAR", "OLAHAN")
    private val pageSize = 10

    private var products: List<DataBarisProduk> = emptyList()
    private var priceStatusMap: Map<String, StatusHargaProduk> = emptyMap()
    private var parameterStatusMap: Map<String, StatusParameterProduk> = emptyMap()

    private var currentPage = 1
    private var totalPages = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureScreen("Daftar Produk", "Kelola produk dasar dan olahan")
        setPrimaryFilter(categories) {
            currentPage = 1
            refresh()
        }
        hideSecondaryFilter()
        hideButtons()

        setFabAdd {
            startActivity(Intent(this, AktivitasFormProduk::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadProducts()
    }

    override fun onSearchChanged() {
        currentPage = 1
        refresh()
    }

    private fun loadProducts() {
        firestore.collection("produk")
            .orderBy("dibuatPada", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val loadedProducts = snapshot.documents.map { doc ->
                    DataBarisProduk(
                        id = doc.id,
                        kodeProduk = doc.getString("kodeProduk").orEmpty(),
                        namaProduk = doc.getString("namaProduk").orEmpty(),
                        jenisProduk = doc.getString("jenisProduk").orEmpty(),
                        satuan = doc.getString("satuan").orEmpty(),
                        stokSaatIni = doc.getLong("stokSaatIni") ?: 0L,
                        stokMinimum = doc.getLong("stokMinimum") ?: 0L,
                        tampilDiKasir = doc.getBoolean("tampilDiKasir") ?: false,
                        aktifDijual = doc.getBoolean("aktifDijual") ?: false,
                        dihapus = doc.getBoolean("dihapus") ?: false
                    )
                }

                if (loadedProducts.isEmpty()) {
                    products = emptyList()
                    priceStatusMap = emptyMap()
                    parameterStatusMap = emptyMap()
                    currentPage = 1
                    totalPages = 1
                    refresh()
                    return@addOnSuccessListener
                }

                val priceTasks = loadedProducts.map { product ->
                    firestore.collection("produk")
                        .document(product.id)
                        .collection("hargaJual")
                        .whereEqualTo("dihapus", false)
                        .get()
                        .continueWith { task ->
                            val docs = task.result?.documents.orEmpty()
                            product.id to StatusHargaProduk(
                                total = docs.size,
                                aktif = docs.count { it.getBoolean("aktif") ?: true }
                            )
                        }
                }

                firestore.collection("parameterProduksi")
                    .whereEqualTo("dihapus", false)
                    .get()
                    .continueWith { task ->
                        val docs = task.result?.documents.orEmpty()
                        docs.groupBy { it.getString("idProduk").orEmpty() }
                            .mapValues { (_, groupedDocs) ->
                                StatusParameterProduk(
                                    total = groupedDocs.size,
                                    aktif = groupedDocs.count { it.getBoolean("aktif") ?: false }
                                )
                            }
                    }
                    .addOnSuccessListener { parameterMap ->
                        Tasks.whenAllSuccess<Pair<String, StatusHargaProduk>>(priceTasks)
                            .addOnSuccessListener { result ->
                                products = loadedProducts
                                priceStatusMap = result.toMap()
                                parameterStatusMap = parameterMap
                                currentPage = 1
                                refresh()
                            }
                            .addOnFailureListener {
                                products = loadedProducts
                                priceStatusMap = emptyMap()
                                parameterStatusMap = parameterMap
                                currentPage = 1
                                refresh()
                            }
                    }
                    .addOnFailureListener {
                        Tasks.whenAllSuccess<Pair<String, StatusHargaProduk>>(priceTasks)
                            .addOnSuccessListener { result ->
                                products = loadedProducts
                                priceStatusMap = result.toMap()
                                parameterStatusMap = emptyMap()
                                currentPage = 1
                                refresh()
                            }
                            .addOnFailureListener {
                                products = loadedProducts
                                priceStatusMap = emptyMap()
                                parameterStatusMap = emptyMap()
                                currentPage = 1
                                refresh()
                            }
                    }
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat produk: ${e.message}")
                products = emptyList()
                priceStatusMap = emptyMap()
                parameterStatusMap = emptyMap()
                currentPage = 1
                totalPages = 1
                refresh()
            }
    }

    private fun refresh() {
        val query = searchText().trim().lowercase()
        val category = primarySelection()

        val filteredProducts = products.filter { item ->
            !item.dihapus &&
                item.namaProduk.lowercase().contains(query) &&
                (category == "Semua" || item.jenisProduk == category)
        }

        totalPages = if (filteredProducts.isEmpty()) 1 else ((filteredProducts.size - 1) / pageSize) + 1

        if (currentPage > totalPages) currentPage = totalPages
        if (currentPage < 1) currentPage = 1

        val fromIndex = (currentPage - 1) * pageSize
        val toIndex = minOf(fromIndex + pageSize, filteredProducts.size)

        val pagedProducts = if (filteredProducts.isEmpty()) emptyList() else filteredProducts.subList(fromIndex, toIndex)

        val rows = pagedProducts.map { item ->
            val hargaInfo = priceStatusMap[item.id] ?: StatusHargaProduk()
            val parameterInfo = parameterStatusMap[item.id] ?: StatusParameterProduk()

            val hargaLabel = when {
                hargaInfo.total <= 0 -> "Belum ada harga"
                hargaInfo.aktif <= 0 -> "Harga nonaktif"
                else -> "Harga tersedia"
            }

            val hargaTone = when {
                hargaInfo.total <= 0 -> WarnaBaris.GOLD
                hargaInfo.aktif <= 0 -> WarnaBaris.ORANGE
                else -> WarnaBaris.GREEN
            }

            val parameterLabel = when {
                item.jenisProduk != "DASAR" -> "Tidak perlu parameter"
                parameterInfo.total <= 0 -> "Belum ada parameter"
                parameterInfo.aktif <= 0 -> "Parameter nonaktif"
                else -> "Parameter tersedia"
            }

            val parameterTone = when {
                item.jenisProduk != "DASAR" -> WarnaBaris.BLUE
                parameterInfo.total <= 0 -> WarnaBaris.GOLD
                parameterInfo.aktif <= 0 -> WarnaBaris.ORANGE
                else -> WarnaBaris.GREEN
            }

            ItemBaris(
                id = item.id,
                title = item.namaProduk,
                subtitle = "${item.kodeProduk} • ${item.jenisProduk} • ${item.satuan}",
                badge = if (item.aktifDijual) "Aktif" else "Nonaktif",
                amount = "Stok ${item.stokSaatIni}",
                priceStatus = hargaLabel,
                parameterStatus = parameterLabel,
                actionLabel = if (item.id == "info_empty") null else "⋮",
                editLabel = null,
                deleteLabel = null,
                tone = when {
                    item.stokSaatIni <= 0L -> WarnaBaris.ORANGE
                    item.stokSaatIni <= item.stokMinimum -> WarnaBaris.GOLD
                    else -> WarnaBaris.GREEN
                },
                priceTone = hargaTone,
                parameterTone = parameterTone
            )
        }

        if (filteredProducts.isNotEmpty()) {
            showPagination(
                currentPage = currentPage,
                totalPages = totalPages,
                onPrev = if (currentPage > 1) ({ currentPage--; refresh() }) else null,
                onNext = if (currentPage < totalPages) ({ currentPage++; refresh() }) else null
            )
        } else {
            hidePagination()
        }

        submitRows(
            if (rows.isNotEmpty()) {
                rows
            } else {
                listOf(
                    ItemBaris(
                        id = "info_empty",
                        title = "Belum ada data produk",
                        subtitle = "Data produk akan tampil dari Firebase.",
                        badge = "Info",
                        amount = "",
                        priceStatus = "",
                        parameterStatus = "",
                        actionLabel = null,
                        editLabel = null,
                        deleteLabel = null,
                        tone = WarnaBaris.GOLD,
                        priceTone = WarnaBaris.DEFAULT,
                        parameterTone = WarnaBaris.DEFAULT
                    )
                )
            }
        )
    }

    override fun onRowClick(item: ItemBaris) {
        if (item.id == "info_empty") return

        startActivity(
            Intent(this, AktivitasFormProduk::class.java)
                .putExtra(EkstraAplikasi.EXTRA_PRODUCT_ID, item.id)
        )
    }

    override fun onRowAction(item: ItemBaris, anchor: View) {
        if (item.id == "info_empty") return

        val product = products.firstOrNull { it.id == item.id } ?: return
        showMenuPopup(product, anchor)
    }

    override fun onRowDelete(item: ItemBaris) {
        if (item.id == "info_empty") return
        val product = products.firstOrNull { it.id == item.id } ?: return
        hapusProduk(product)
    }

    private fun showMenuPopup(product: DataBarisProduk, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Harga Kanal")
        popup.menu.add(0, 2, 1, "Parameter Produksi")
        popup.menu.add(0, 3, 2, "Hapus Produk")

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    openHargaKanal(product.id)
                    true
                }
                2 -> {
                    openParameterProduksi(product)
                    true
                }
                3 -> {
                    hapusProduk(product)
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    private fun hapusProduk(product: DataBarisProduk) {
        showConfirmationModal(
            title = "Hapus produk?",
            message = "Produk ${product.namaProduk} akan dihapus dari daftar aktif.",
            confirmLabel = "Hapus"
        ) {
            val updates = hashMapOf<String, Any>(
                "dihapus" to true,
                "aktifDijual" to false,
                "tampilDiKasir" to false,
                "dihapusPada" to Timestamp.now(),
                "diperbaruiPada" to Timestamp.now()
            )

            firestore.collection("produk")
                .document(product.id)
                .update(updates)
                .addOnSuccessListener {
                    showMessage("Produk berhasil dihapus.")
                    loadProducts()
                }
                .addOnFailureListener { e ->
                    showMessage("Gagal menghapus produk: ${e.message}")
                }
        }
    }

    private fun openHargaKanal(productId: String) {
        startActivity(
            Intent(this, AktivitasDaftarHarga::class.java)
                .putExtra(EkstraAplikasi.EXTRA_PRODUCT_ID, productId)
        )
    }

    private fun openParameterProduksi(product: DataBarisProduk) {
        if (product.jenisProduk != "DASAR") {
            showMessage("Parameter produksi hanya tersedia untuk produk DASAR.")
            return
        }

        startActivity(
            Intent(this, AktivitasDaftarParameter::class.java)
                .putExtra(EkstraAplikasi.EXTRA_PRODUCT_ID, product.id)
        )
    }
}

private data class DataBarisProduk(
    val id: String,
    val kodeProduk: String,
    val namaProduk: String,
    val jenisProduk: String,
    val satuan: String,
    val stokSaatIni: Long,
    val stokMinimum: Long,
    val tampilDiKasir: Boolean,
    val aktifDijual: Boolean,
    val dihapus: Boolean
)

private data class StatusHargaProduk(
    val total: Int = 0,
    val aktif: Int = 0
)

private data class StatusParameterProduk(
    val total: Int = 0,
    val aktif: Int = 0
)
