package muhamad.irfan.si_tahu.ui.product

import android.content.Intent
import android.os.Bundle
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.ui.base.AktivitasDaftarDasar
import muhamad.irfan.si_tahu.ui.price.AktivitasDaftarHarga
import muhamad.irfan.si_tahu.util.EkstraAplikasi
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class AktivitasDaftarProduk : AktivitasDaftarDasar() {

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val categories = listOf("Semua", "DASAR", "OLAHAN")
    private var products: List<DataBarisProduk> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureScreen("Daftar Produk", "Kelola produk dasar dan olahan")
        setPrimaryFilter(categories) { refresh() }
        hideSecondaryFilter()

        setPrimaryButton("Tambah Produk") {
            startActivity(Intent(this, AktivitasFormProduk::class.java))
        }

        setSecondaryButton("Harga Kanal") {
            startActivity(Intent(this, AktivitasDaftarHarga::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadProducts()
    }

    override fun onSearchChanged() = refresh()

    private fun loadProducts() {
        firestore.collection("produk")
            .get()
            .addOnSuccessListener { snapshot ->
                products = snapshot.documents.map { doc ->
                    DataBarisProduk(
                        id = doc.id,
                        kodeProduk = doc.getString("kodeProduk").orEmpty(),
                        namaProduk = doc.getString("namaProduk").orEmpty(),
                        jenisProduk = doc.getString("jenisProduk").orEmpty(),
                        satuan = doc.getString("satuan").orEmpty(),
                        stokSaatIni = doc.getLong("stokSaatIni") ?: 0L,
                        stokMinimum = doc.getLong("stokMinimum") ?: 0L,
                        tampilDiKasir = doc.getBoolean("tampilDiKasir") ?: false,
                        aktifDijual = doc.getBoolean("aktifDijual") ?: false
                    )
                }
                refresh()
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat produk: ${e.message}")
                products = emptyList()
                refresh()
            }
    }

    private fun refresh() {
        val query = searchText()
        val category = primarySelection()

        val rows = products.filter { item ->
            item.namaProduk.lowercase().contains(query) &&
                    (category == "Semua" || item.jenisProduk == category)
        }.map { item ->
            ItemBaris(
                id = item.id,
                title = item.namaProduk,
                subtitle = "${item.kodeProduk} • ${item.jenisProduk} • ${item.satuan}",
                badge = if (item.aktifDijual) "Aktif" else "Nonaktif",
                amount = "Stok ${item.stokSaatIni}",
                actionLabel = if (item.aktifDijual) "Nonaktifkan" else "Aktifkan",
                tone = when {
                    item.stokSaatIni <= 0L -> WarnaBaris.ORANGE
                    item.stokSaatIni <= item.stokMinimum -> WarnaBaris.GOLD
                    else -> WarnaBaris.GREEN
                }
            )
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
                        tone = WarnaBaris.GOLD
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

    override fun onRowAction(item: ItemBaris) {
        if (item.id == "info_empty") return

        val product = products.firstOrNull { it.id == item.id } ?: return
        val nextActive = !product.aktifDijual

        showConfirmationModal(
            title = if (nextActive) "Aktifkan produk?" else "Nonaktifkan produk?",
            message = if (nextActive) {
                "Produk ${product.namaProduk} akan diaktifkan kembali."
            } else {
                "Produk ${product.namaProduk} akan dinonaktifkan dari penjualan."
            },
            confirmLabel = if (nextActive) "Aktifkan" else "Nonaktifkan"
        ) {
            val updates = hashMapOf<String, Any>(
                "aktifDijual" to nextActive
            )

            if (!nextActive) {
                updates["tampilDiKasir"] = false
            }

            firestore.collection("produk")
                .document(product.id)
                .update(updates)
                .addOnSuccessListener {
                    showMessage(
                        if (nextActive) "Produk berhasil diaktifkan."
                        else "Produk berhasil dinonaktifkan."
                    )
                    loadProducts()
                }
                .addOnFailureListener { e ->
                    showMessage("Gagal mengubah status produk: ${e.message}")
                }
        }
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
    val aktifDijual: Boolean
)