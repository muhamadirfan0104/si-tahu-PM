package muhamad.irfan.si_tahu.ui.harga

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDaftarDasar
import muhamad.irfan.si_tahu.ui.produk.AktivitasDaftarProduk
import muhamad.irfan.si_tahu.util.EkstraAplikasi
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class AktivitasDaftarHarga : AktivitasDaftarDasar() {

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private var products: List<OpsiProdukHarga> = emptyList()
    private var channels: List<DataBarisHarga> = emptyList()
    private var initialSelectionIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureScreen("Harga Kanal", "Atur harga per kanal")
        hideSearch()
        hideSecondaryFilter()

        setSecondaryButton("Data Produk") {
            startActivity(Intent(this, AktivitasDaftarProduk::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadProducts()
    }

    private fun loadProducts() {
        firestore.collection("produk")
            .whereEqualTo("dihapus", false)
            .get()
            .addOnSuccessListener { snapshot ->
                products = snapshot.documents.map { doc ->
                    OpsiProdukHarga(
                        id = doc.id,
                        name = doc.getString("namaProduk").orEmpty()
                    )
                }.sortedBy { it.name }

                if (products.isEmpty()) {
                    hidePrimaryFilter()
                    setPrimaryButton("Data Produk") {
                        startActivity(Intent(this, AktivitasDaftarProduk::class.java))
                    }
                    hideSecondaryButton()
                    submitRows(listOf(infoBelumAdaProduk()))
                    return@addOnSuccessListener
                }

                val initialProductId = intent.getStringExtra(EkstraAplikasi.EXTRA_PRODUCT_ID)
                initialSelectionIndex =
                    products.indexOfFirst { it.id == initialProductId }.takeIf { it >= 0 } ?: 0

                setPrimaryFilter(products.map { it.name }, initialSelectionIndex) {
                    loadChannels()
                }

                setPrimaryButton("Tambah Harga") {
                    selectedProductId()?.let { productId ->
                        startActivity(
                            Intent(this, AktivitasFormHarga::class.java)
                                .putExtra(EkstraAplikasi.EXTRA_PRODUCT_ID, productId)
                        )
                    }
                }

                loadChannels()
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat produk: ${e.message}")
                submitRows(listOf(infoBelumAdaProduk()))
            }
    }

    private fun selectedProductId(): String? {
        val selectedName = primarySelection()
        return products.firstOrNull { it.name == selectedName }?.id ?: products.firstOrNull()?.id
    }

    private fun loadChannels() {
        val productId = selectedProductId()
        if (productId.isNullOrBlank()) {
            submitRows(listOf(infoBelumAdaProduk()))
            return
        }

        firestore.collection("produk")
            .document(productId)
            .collection("hargaJual")
            .whereEqualTo("dihapus", false)
            .get()
            .addOnSuccessListener { snapshot ->
                channels = snapshot.documents.map { doc ->
                    DataBarisHarga(
                        id = doc.id,
                        kanalHarga = doc.getString("kanalHarga").orEmpty(),
                        hargaSatuan = doc.getLong("hargaSatuan") ?: 0L,
                        aktif = doc.getBoolean("aktif") ?: true,
                        hargaUtama = doc.getBoolean("hargaUtama") ?: false
                    )
                }.sortedBy { it.kanalHarga.lowercase() }

                refresh()
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat harga kanal: ${e.message}")
                channels = emptyList()
                refresh()
            }
    }

    private fun refresh() {
        val product = products.firstOrNull { it.id == selectedProductId() }

        if (products.isEmpty()) {
            submitRows(listOf(infoBelumAdaProduk()))
            return
        }

        if (channels.isEmpty()) {
            submitRows(
                listOf(
                    ItemBaris(
                        id = "info_harga_kosong",
                        title = "Belum ada harga kanal",
                        subtitle = "Tambahkan harga kanal untuk produk ${product?.name.orEmpty()}.",
                        badge = "Info",
                        amount = "",
                        tone = WarnaBaris.BLUE
                    )
                )
            )
            return
        }

        val rows = channels.map {
            ItemBaris(
                id = it.id,
                title = it.kanalHarga,
                subtitle = if (it.aktif) "Kanal aktif" else "Kanal nonaktif",
                badge = if (it.hargaUtama) "Default" else "Harga",
                amount = Formatter.currency(it.hargaSatuan),
                actionLabel = if (it.aktif) "Nonaktifkan" else "Aktifkan",
                deleteLabel = "Hapus",
                tone = if (it.hargaUtama) WarnaBaris.GREEN else WarnaBaris.GOLD
            )
        }

        submitRows(rows)
    }

    private fun infoBelumAdaProduk(): ItemBaris {
        return ItemBaris(
            id = "info_produk_kosong",
            title = "Belum ada produk",
            subtitle = "Tambahkan data produk terlebih dahulu sebelum mengatur harga kanal.",
            badge = "Info",
            amount = "",
            tone = WarnaBaris.BLUE
        )
    }

    override fun onRowClick(item: ItemBaris) {
        if (item.id.startsWith("info_")) return

        val productId = selectedProductId() ?: return
        startActivity(
            Intent(this, AktivitasFormHarga::class.java)
                .putExtra(EkstraAplikasi.EXTRA_PRODUCT_ID, productId)
                .putExtra(EkstraAplikasi.EXTRA_PRICE_ID, item.id)
        )
    }

    override fun onRowAction(item: ItemBaris, anchor: View) {
        if (item.id.startsWith("info_")) return

        val productId = selectedProductId() ?: return
        val channel = channels.firstOrNull { it.id == item.id } ?: return
        val nextActive = !channel.aktif

        showConfirmationModal(
            title = if (nextActive) "Aktifkan harga kanal?" else "Nonaktifkan harga kanal?",
            message = if (nextActive) {
                "Harga kanal ${channel.kanalHarga} akan diaktifkan."
            } else {
                "Harga kanal ${channel.kanalHarga} akan dinonaktifkan."
            },
            confirmLabel = if (nextActive) "Aktifkan" else "Nonaktifkan"
        ) {
            if (nextActive) {
                activatePriceWithDefaultSync(
                    productId = productId,
                    priceId = channel.id
                )
            } else {
                deactivatePriceWithDefaultGuard(
                    productId = productId,
                    priceId = channel.id,
                    title = channel.kanalHarga
                )
            }
        }
    }

    override fun onRowDelete(item: ItemBaris) {
        if (item.id.startsWith("info_")) return

        val productId = selectedProductId() ?: return

        showConfirmationModal(
            title = "Hapus harga kanal?",
            message = "Harga kanal ${item.title} akan di-soft delete. Transaksi lama tetap tersimpan. Lanjutkan?",
            confirmLabel = "Hapus"
        ) {
            softDeletePriceWithDefaultGuard(
                productId = productId,
                priceId = item.id,
                title = item.title
            )
        }
    }

    private fun activatePriceWithDefaultSync(
        productId: String,
        priceId: String
    ) {
        val hargaRef = firestore.collection("produk")
            .document(productId)
            .collection("hargaJual")

        hargaRef.document(priceId)
            .get()
            .addOnSuccessListener { targetDoc ->
                if (!targetDoc.exists()) {
                    showMessage("Data harga tidak ditemukan.")
                    return@addOnSuccessListener
                }

                val targetWasPrimary = targetDoc.getBoolean("hargaUtama") == true

                hargaRef.whereEqualTo("dihapus", false)
                    .whereEqualTo("aktif", true)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val otherActiveDocs = snapshot.documents.filter { it.id != priceId }
                        val hasOtherPrimary = otherActiveDocs.any { it.getBoolean("hargaUtama") == true }
                        val shouldBePrimary = targetWasPrimary || !hasOtherPrimary
                        val now = Timestamp.now()
                        val batch = firestore.batch()

                        batch.update(
                            hargaRef.document(priceId),
                            mapOf(
                                "aktif" to true,
                                "hargaUtama" to shouldBePrimary,
                                "diperbaruiPada" to now
                            )
                        )

                        if (shouldBePrimary) {
                            otherActiveDocs.forEach { doc ->
                                if (doc.getBoolean("hargaUtama") == true) {
                                    batch.update(
                                        doc.reference,
                                        mapOf(
                                            "hargaUtama" to false,
                                            "diperbaruiPada" to now
                                        )
                                    )
                                }
                            }
                        }

                        batch.commit()
                            .addOnSuccessListener {
                                showMessage("Harga kanal berhasil diaktifkan.")
                                loadChannels()
                            }
                            .addOnFailureListener { e ->
                                showMessage("Gagal mengaktifkan harga kanal: ${e.message}")
                            }
                    }
                    .addOnFailureListener { e ->
                        showMessage("Gagal memvalidasi harga aktif: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat harga kanal: ${e.message}")
            }
    }

    private fun deactivatePriceWithDefaultGuard(
        productId: String,
        priceId: String,
        title: String
    ) {
        val hargaRef = firestore.collection("produk")
            .document(productId)
            .collection("hargaJual")

        hargaRef.document(priceId)
            .get()
            .addOnSuccessListener { targetDoc ->
                if (!targetDoc.exists()) {
                    showMessage("Data harga tidak ditemukan.")
                    return@addOnSuccessListener
                }

                val isPrimary = targetDoc.getBoolean("hargaUtama") == true

                hargaRef.whereEqualTo("dihapus", false)
                    .whereEqualTo("aktif", true)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val activeDocs = snapshot.documents
                        val otherActiveDocs = activeDocs.filter { it.id != priceId }

                        if (otherActiveDocs.isEmpty()) {
                            showMessage("Tidak bisa menonaktifkan harga ini karena harus ada minimal satu harga aktif.")
                            return@addOnSuccessListener
                        }

                        val batch = firestore.batch()
                        val now = Timestamp.now()

                        batch.update(
                            hargaRef.document(priceId),
                            mapOf(
                                "aktif" to false,
                                "hargaUtama" to false,
                                "diperbaruiPada" to now
                            )
                        )

                        if (isPrimary) {
                            val replacement = otherActiveDocs.firstOrNull()
                            if (replacement != null) {
                                batch.update(
                                    replacement.reference,
                                    mapOf(
                                        "hargaUtama" to true,
                                        "diperbaruiPada" to now
                                    )
                                )
                            }
                        }

                        batch.commit()
                            .addOnSuccessListener {
                                showMessage("Harga kanal $title berhasil dinonaktifkan.")
                                loadChannels()
                            }
                            .addOnFailureListener { e ->
                                showMessage("Gagal menonaktifkan harga kanal: ${e.message}")
                            }
                    }
                    .addOnFailureListener { e ->
                        showMessage("Gagal memvalidasi harga aktif: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat harga kanal: ${e.message}")
            }
    }

    private fun softDeletePriceWithDefaultGuard(
        productId: String,
        priceId: String,
        title: String
    ) {
        val hargaRef = firestore.collection("produk")
            .document(productId)
            .collection("hargaJual")

        hargaRef.document(priceId)
            .get()
            .addOnSuccessListener { targetDoc ->
                if (!targetDoc.exists()) {
                    showMessage("Data harga tidak ditemukan.")
                    return@addOnSuccessListener
                }

                val isPrimary = targetDoc.getBoolean("hargaUtama") == true
                val isActive = targetDoc.getBoolean("aktif") ?: true

                hargaRef.whereEqualTo("dihapus", false)
                    .whereEqualTo("aktif", true)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val activeDocs = snapshot.documents
                        val otherActiveDocs = activeDocs.filter { it.id != priceId }

                        if (isActive && otherActiveDocs.isEmpty()) {
                            showMessage("Tidak bisa menghapus harga ini karena ini harga aktif terakhir.")
                            return@addOnSuccessListener
                        }

                        val batch = firestore.batch()
                        val now = Timestamp.now()

                        batch.update(
                            hargaRef.document(priceId),
                            mapOf(
                                "dihapus" to true,
                                "aktif" to false,
                                "hargaUtama" to false,
                                "dihapusPada" to now,
                                "diperbaruiPada" to now
                            )
                        )

                        if (isPrimary) {
                            val replacement = otherActiveDocs.firstOrNull()
                            if (replacement != null) {
                                batch.update(
                                    replacement.reference,
                                    mapOf(
                                        "hargaUtama" to true,
                                        "diperbaruiPada" to now
                                    )
                                )
                            }
                        }

                        batch.commit()
                            .addOnSuccessListener {
                                showMessage("Harga kanal $title berhasil dihapus.")
                                loadChannels()
                            }
                            .addOnFailureListener { e ->
                                showMessage("Gagal menghapus harga kanal: ${e.message}")
                            }
                    }
                    .addOnFailureListener { e ->
                        showMessage("Gagal memvalidasi default harga: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat harga kanal: ${e.message}")
            }
    }
}

private data class OpsiProdukHarga(
    val id: String,
    val name: String
)

private data class DataBarisHarga(
    val id: String,
    val kanalHarga: String,
    val hargaSatuan: Long,
    val aktif: Boolean,
    val hargaUtama: Boolean
)