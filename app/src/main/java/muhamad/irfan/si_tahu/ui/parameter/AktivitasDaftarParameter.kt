package muhamad.irfan.si_tahu.ui.parameter

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDaftarDasar
import muhamad.irfan.si_tahu.util.EkstraAplikasi
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class AktivitasDaftarParameter : AktivitasDaftarDasar() {

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private var products: List<OpsiProdukParameter> = emptyList()
    private var parameters: List<DataBarisParameter> = emptyList()
    private var initialSelectionIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureScreen("Parameter Produksi", "Standar hasil per masak")
        hideSearch()
        hideSecondaryFilter()
        hideButtons()

        setFabAdd {
            selectedProductId()?.let { productId ->
                startActivity(
                    Intent(this, AktivitasFormParameter::class.java)
                        .putExtra(EkstraAplikasi.EXTRA_PRODUCT_ID, productId)
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadBasicProducts()
    }

    private fun parameterCollection(productId: String) =
        firestore.collection("Produk").document(productId).collection("parameterProduksi")

    private fun loadBasicProducts() {
        firestore.collection("Produk")
            .whereEqualTo("jenisProduk", "DASAR")
            .get()
            .addOnSuccessListener { snapshot ->
                products = snapshot.documents
                    .filter { it.getBoolean("dihapus") != true }
                    .map { doc ->
                    OpsiProdukParameter(
                        id = doc.id,
                        namaProduk = doc.getString("namaProduk").orEmpty()
                    )
                }.sortedBy { it.namaProduk.lowercase() }

                if (products.isEmpty()) {
                    hideFabAdd()
                    hidePrimaryFilter()
                    submitRows(listOf(infoBelumAdaProdukDasar()))
                    return@addOnSuccessListener
                }

                val initialProductId = intent.getStringExtra(EkstraAplikasi.EXTRA_PRODUCT_ID)
                initialSelectionIndex =
                    products.indexOfFirst { it.id == initialProductId }.takeIf { it >= 0 } ?: 0

                setPrimaryFilter(products.map { it.namaProduk }, initialSelectionIndex) {
                    loadParameters()
                }
                setFabAdd {
                    selectedProductId()?.let { productId ->
                        startActivity(
                            Intent(this, AktivitasFormParameter::class.java)
                                .putExtra(EkstraAplikasi.EXTRA_PRODUCT_ID, productId)
                        )
                    }
                }

                loadParameters()
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat produk dasar: ${e.message}")
                hideFabAdd()
                submitRows(listOf(infoBelumAdaProdukDasar()))
            }
    }

    private fun selectedProductId(): String? {
        val selectedName = primarySelection()
        return products.firstOrNull { it.namaProduk == selectedName }?.id
            ?: products.firstOrNull()?.id
    }

    private fun loadParameters() {
        val productId = selectedProductId()
        if (productId.isNullOrBlank()) {
            submitRows(listOf(infoBelumAdaProdukDasar()))
            return
        }

        parameterCollection(productId)
            .get()
            .addOnSuccessListener { snapshot ->
                parameters = snapshot.documents
                    .filter { it.getBoolean("dihapus") != true }
                    .map { doc ->
                    DataBarisParameter(
                        id = doc.id,
                        idProduk = doc.getString("idProduk").orEmpty().ifBlank { productId },
                        namaProduk = doc.getString("namaProduk").orEmpty(),
                        hasilPerProduksi = doc.getLong("hasilPerProduksi") ?: 0L,
                        satuanHasil = doc.getString("satuanHasil").orEmpty(),
                        aktif = doc.getBoolean("aktif") ?: false,
                        catatan = doc.getString("catatan").orEmpty(),
                        dibuatPadaMillis = doc.getTimestamp("dibuatPada")?.toDate()?.time ?: 0L
                    )
                }.sortedByDescending { it.dibuatPadaMillis }

                refresh()
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat parameter produksi: ${e.message}")
                parameters = emptyList()
                refresh()
            }
    }

    private fun refresh() {
        val product = products.firstOrNull { it.id == selectedProductId() }

        if (products.isEmpty()) {
            submitRows(listOf(infoBelumAdaProdukDasar()))
            return
        }

        if (parameters.isEmpty()) {
            submitRows(
                listOf(
                    ItemBaris(
                        id = "info_parameter_kosong",
                        title = "Belum ada parameter produksi",
                        subtitle = "Tambahkan parameter untuk produk ${product?.namaProduk.orEmpty()}.",
                        badge = "Info",
                        amount = "",
                        tone = WarnaBaris.BLUE
                    )
                )
            )
            return
        }

        val rows = parameters.map { item ->
            ItemBaris(
                id = item.id,
                title = item.namaProduk.ifBlank { product?.namaProduk ?: item.idProduk },
                subtitle = item.catatan.ifBlank { "${item.id.uppercase()} • Standar hasil produksi" },
                badge = if (item.aktif) "Aktif" else "Nonaktif",
                amount = "${item.hasilPerProduksi} ${item.satuanHasil}",
                priceStatus = "Hasil per produksi",
                parameterStatus = if (item.catatan.isNotBlank()) "Ada catatan" else "Tanpa catatan",
                actionLabel = "⋮",
                tone = if (item.aktif) WarnaBaris.GREEN else WarnaBaris.GOLD,
                priceTone = WarnaBaris.BLUE,
                parameterTone = if (item.catatan.isNotBlank()) WarnaBaris.GOLD else WarnaBaris.DEFAULT
            )
        }

        submitRows(rows)
    }

    private fun infoBelumAdaProdukDasar(): ItemBaris {
        return ItemBaris(
            id = "info_produk_dasar_kosong",
            title = "Belum ada produk dasar",
            subtitle = "Tambahkan produk DASAR terlebih dahulu sebelum mengatur parameter produksi.",
            badge = "Info",
            amount = "",
            tone = WarnaBaris.BLUE
        )
    }

    override fun onRowClick(item: ItemBaris) {
        if (item.id.startsWith("info_")) return

        startActivity(
            Intent(this, AktivitasFormParameter::class.java)
                .putExtra(EkstraAplikasi.EXTRA_PARAMETER_ID, item.id)
                .putExtra(EkstraAplikasi.EXTRA_PRODUCT_ID, selectedProductId())
        )
    }

    override fun onRowAction(item: ItemBaris, anchor: View) {
        if (item.id.startsWith("info_")) return

        val parameter = parameters.firstOrNull { it.id == item.id } ?: return
        showMenuPopup(parameter, anchor)
    }

    override fun onRowDelete(item: ItemBaris) {
        if (item.id.startsWith("info_")) return
        val parameter = parameters.firstOrNull { it.id == item.id } ?: return
        softDeleteParameter(parameter)
    }

    private fun showMenuPopup(parameter: DataBarisParameter, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, if (parameter.aktif) "Nonaktifkan Parameter" else "Aktifkan Parameter")
        popup.menu.add(0, 2, 1, "Hapus Parameter")

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    confirmToggleParameter(parameter)
                    true
                }
                2 -> {
                    confirmDeleteParameter(parameter)
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    private fun confirmToggleParameter(parameter: DataBarisParameter) {
        val nextActive = !parameter.aktif

        showConfirmationModal(
            title = if (nextActive) "Aktifkan parameter?" else "Nonaktifkan parameter?",
            message = if (nextActive) {
                "Parameter ${parameter.namaProduk} akan dijadikan parameter aktif."
            } else {
                "Parameter ${parameter.namaProduk} akan dinonaktifkan."
            },
            confirmLabel = if (nextActive) "Aktifkan" else "Nonaktifkan"
        ) {
            if (nextActive) {
                activateParameterExclusively(parameter)
            } else {
                deactivateParameter(parameter)
            }
        }
    }

    private fun confirmDeleteParameter(parameter: DataBarisParameter) {
        showConfirmationModal(
            title = "Hapus parameter?",
            message = "Parameter ${parameter.namaProduk} (${parameter.id}) akan di-soft delete. Lanjutkan?",
            confirmLabel = "Hapus"
        ) {
            softDeleteParameter(parameter)
        }
    }

    private fun activateParameterExclusively(parameter: DataBarisParameter) {
        parameterCollection(parameter.idProduk)
            .get()
            .addOnSuccessListener { snapshot ->
                val docs = snapshot.documents.filter { it.getBoolean("dihapus") != true }
                val batch = firestore.batch()
                val now = Timestamp.now()

                docs.forEach { doc ->
                    batch.update(
                        doc.reference,
                        mapOf(
                            "aktif" to (doc.id == parameter.id),
                            "diperbaruiPada" to now
                        )
                    )
                }

                batch.commit()
                    .addOnSuccessListener {
                        showMessage("Parameter aktif berhasil diperbarui.")
                        loadParameters()
                    }
                    .addOnFailureListener { e ->
                        showMessage("Gagal mengaktifkan parameter: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat parameter produk: ${e.message}")
            }
    }

    private fun deactivateParameter(parameter: DataBarisParameter) {
        parameterCollection(parameter.idProduk)
            .document(parameter.id)
            .update(
                mapOf(
                    "aktif" to false,
                    "diperbaruiPada" to Timestamp.now()
                )
            )
            .addOnSuccessListener {
                showMessage("Parameter berhasil dinonaktifkan.")
                loadParameters()
            }
            .addOnFailureListener { e ->
                showMessage("Gagal menonaktifkan parameter: ${e.message}")
            }
    }

    private fun softDeleteParameter(parameter: DataBarisParameter) {
        val parameterRef = parameterCollection(parameter.idProduk)

        parameterRef.document(parameter.id)
            .get()
            .addOnSuccessListener { targetDoc ->
                if (!targetDoc.exists()) {
                    showMessage("Data parameter tidak ditemukan.")
                    return@addOnSuccessListener
                }

                val isActive = targetDoc.getBoolean("aktif") == true

                parameterRef
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val otherDocs = snapshot.documents
                            .filter { it.getBoolean("dihapus") != true && it.id != parameter.id }
                        val replacementDoc = otherDocs.firstOrNull()
                        val now = Timestamp.now()
                        val batch = firestore.batch()

                        batch.update(
                            parameterRef.document(parameter.id),
                            mapOf(
                                "dihapus" to true,
                                "aktif" to false,
                                "dihapusPada" to now,
                                "diperbaruiPada" to now
                            )
                        )

                        if (isActive && replacementDoc != null) {
                            batch.update(
                                replacementDoc.reference,
                                mapOf(
                                    "aktif" to true,
                                    "diperbaruiPada" to now
                                )
                            )
                        }

                        batch.commit()
                            .addOnSuccessListener {
                                showMessage("Parameter berhasil dihapus.")
                                loadParameters()
                            }
                            .addOnFailureListener { e ->
                                showMessage("Gagal menghapus parameter: ${e.message}")
                            }
                    }
                    .addOnFailureListener { e ->
                        showMessage("Gagal memvalidasi parameter: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat parameter: ${e.message}")
            }
    }
}

private data class OpsiProdukParameter(
    val id: String,
    val namaProduk: String
)

private data class DataBarisParameter(
    val id: String,
    val idProduk: String,
    val namaProduk: String,
    val hasilPerProduksi: Long,
    val satuanHasil: String,
    val aktif: Boolean,
    val catatan: String,
    val dibuatPadaMillis: Long
)
