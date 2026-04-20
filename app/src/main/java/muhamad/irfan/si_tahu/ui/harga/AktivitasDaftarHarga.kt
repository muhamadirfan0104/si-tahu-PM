package muhamad.irfan.si_tahu.ui.harga

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDaftarDasar
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
        hideButtons()

        setFabAdd {
            selectedProductId()?.let { productId ->
                startActivity(
                    Intent(this, AktivitasFormHarga::class.java)
                        .putExtra(EkstraAplikasi.EXTRA_PRODUCT_ID, productId)
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadProducts()
    }

    private fun loadProducts() {
        firestore.collection("Produk")
            .get()
            .addOnSuccessListener { snapshot ->
                products = snapshot.documents
                    .filter { it.getBoolean("dihapus") != true }
                    .map { doc ->
                    OpsiProdukHarga(
                        id = doc.id,
                        name = doc.getString("namaProduk").orEmpty()
                    )
                }.sortedBy { it.name }

                if (products.isEmpty()) {
                    hideFabAdd()
                    hidePrimaryFilter()
                    submitRows(listOf(infoBelumAdaProduk()))
                    return@addOnSuccessListener
                }

                val initialProductId = intent.getStringExtra(EkstraAplikasi.EXTRA_PRODUCT_ID)
                initialSelectionIndex =
                    products.indexOfFirst { it.id == initialProductId }.takeIf { it >= 0 } ?: 0

                setPrimaryFilter(products.map { it.name }, initialSelectionIndex) {
                    loadChannels()
                }
                setFabAdd {
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
                hideFabAdd()
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

        firestore.collection("Produk")
            .document(productId)
            .collection("hargaJual")
            .get()
            .addOnSuccessListener { snapshot ->
                channels = snapshot.documents
                    .filter { it.getBoolean("dihapus") != true }
                    .map { doc ->
                    DataBarisHarga(
                        id = doc.id,
                        kanalHarga = doc.getString("kanalHarga").orEmpty().ifBlank { doc.getString("namaHarga").orEmpty() },
                        hargaSatuan = doc.getLong("hargaSatuan") ?: 0L,
                        aktif = doc.getBoolean("aktif") ?: true,
                        hargaUtama = doc.getBoolean("hargaUtama") ?: false,
                        dibuatPadaMillis = doc.getTimestamp("dibuatPada")?.toDate()?.time ?: 0L
                    )
                }.sortedByDescending { it.dibuatPadaMillis }

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
                        actionLabel = null,
                        tone = WarnaBaris.BLUE
                    )
                )
            )
            return
        }

        val rows = channels.map { item ->
            ItemBaris(
                id = item.id,
                title = item.kanalHarga,
                subtitle = if (item.aktif) "Harga kanal aktif" else "Harga kanal nonaktif",
                badge = if (item.aktif) "Aktif" else "Nonaktif",
                amount = Formatter.currency(item.hargaSatuan),
                priceStatus = if (item.hargaUtama) "Default Kasir" else "Harga Lainnya",
                parameterStatus = "",
                actionLabel = "⋮",
                tone = when {
                    item.hargaUtama -> WarnaBaris.GREEN
                    item.aktif -> WarnaBaris.GOLD
                    else -> WarnaBaris.ORANGE
                },
                priceTone = if (item.hargaUtama) WarnaBaris.GREEN else WarnaBaris.BLUE,
                parameterTone = WarnaBaris.DEFAULT
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
        showMenuPopup(productId, channel, anchor)
    }

    override fun onRowDelete(item: ItemBaris) {
        if (item.id.startsWith("info_")) return
        val productId = selectedProductId() ?: return
        softDeletePriceWithDefaultGuard(productId = productId, priceId = item.id, title = item.title)
    }

    private fun showMenuPopup(productId: String, channel: DataBarisHarga, anchor: View) {
        val popup = PopupMenu(this, anchor)
        var order = 0

        if (!channel.hargaUtama) {
            popup.menu.add(0, 1, order++, "Jadikan Default Kasir")
        }

        popup.menu.add(
            0,
            2,
            order++,
            if (channel.aktif) "Nonaktifkan Harga" else "Aktifkan Harga"
        )
        popup.menu.add(0, 3, order, "Hapus Harga")

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    setDefaultKasir(productId, channel)
                    true
                }
                2 -> {
                    confirmToggleHarga(productId, channel)
                    true
                }
                3 -> {
                    confirmDeleteHarga(productId, channel)
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    private fun setDefaultKasir(productId: String, channel: DataBarisHarga) {
        val hargaRef = firestore.collection("Produk")
            .document(productId)
            .collection("hargaJual")

        hargaRef.get()
            .addOnSuccessListener { snapshot ->
                val docs = snapshot.documents.filter { it.getBoolean("dihapus") != true }
                val batch = firestore.batch()
                val now = Timestamp.now()

                docs.forEach { doc ->
                    val isTarget = doc.id == channel.id
                    batch.update(
                        doc.reference,
                        mapOf(
                            "hargaUtama" to isTarget,
                            "aktif" to if (isTarget) true else (doc.getBoolean("aktif") ?: true),
                            "diperbaruiPada" to now
                        )
                    )
                }

                batch.commit()
                    .addOnSuccessListener {
                        showMessage("Harga default kasir berhasil diperbarui.")
                        loadChannels()
                    }
                    .addOnFailureListener { e ->
                        showMessage("Gagal mengubah default kasir: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat harga kanal: ${e.message}")
            }
    }

    private fun confirmToggleHarga(productId: String, channel: DataBarisHarga) {
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
                activatePriceWithDefaultSync(productId = productId, priceId = channel.id)
            } else {
                deactivatePriceWithDefaultGuard(
                    productId = productId,
                    priceId = channel.id,
                    title = channel.kanalHarga
                )
            }
        }
    }

    private fun confirmDeleteHarga(productId: String, channel: DataBarisHarga) {
        showConfirmationModal(
            title = "Hapus harga kanal?",
            message = "Harga kanal ${channel.kanalHarga} akan di-soft delete. Transaksi lama tetap tersimpan. Lanjutkan?",
            confirmLabel = "Hapus"
        ) {
            softDeletePriceWithDefaultGuard(
                productId = productId,
                priceId = channel.id,
                title = channel.kanalHarga
            )
        }
    }

    private fun activatePriceWithDefaultSync(
        productId: String,
        priceId: String,
    ) {
        val hargaRef = firestore.collection("Produk")
            .document(productId)
            .collection("hargaJual")

        hargaRef.document(priceId)
            .get()
            .addOnSuccessListener { targetDoc ->
                if (!targetDoc.exists()) {
                    showMessage("Data harga tidak ditemukan.")
                    return@addOnSuccessListener
                }

                hargaRef.get()
                    .addOnSuccessListener { snapshot ->
                        val activeDocs = snapshot.documents
                            .filter { it.getBoolean("dihapus") != true && it.getBoolean("aktif") != false && it.id != priceId }
                        val shouldBePrimary = activeDocs.isEmpty()
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
                            activeDocs.forEach { doc ->
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
        val hargaRef = firestore.collection("Produk")
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

                hargaRef.get()
                    .addOnSuccessListener { snapshot ->
                        val activeDocs = snapshot.documents
                            .filter { it.getBoolean("dihapus") != true && it.getBoolean("aktif") != false }
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
        val hargaRef = firestore.collection("Produk")
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

                hargaRef.get()
                    .addOnSuccessListener { snapshot ->
                        val activeDocs = snapshot.documents
                            .filter { it.getBoolean("dihapus") != true && it.getBoolean("aktif") != false }
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
    val hargaUtama: Boolean,
    val dibuatPadaMillis: Long
)
