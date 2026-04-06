package muhamad.irfan.si_tahu.ui.parameter

import android.content.Intent
import android.os.Bundle
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDaftarDasar
import muhamad.irfan.si_tahu.ui.produk.AktivitasDaftarProduk
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

        setSecondaryButton("Data Produk") {
            startActivity(Intent(this, AktivitasDaftarProduk::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadBasicProducts()
    }

    private fun loadBasicProducts() {
        firestore.collection("produk")
            .whereEqualTo("jenisProduk", "DASAR")
            .get()
            .addOnSuccessListener { snapshot ->
                products = snapshot.documents.map { doc ->
                    OpsiProdukParameter(
                        id = doc.id,
                        namaProduk = doc.getString("namaProduk").orEmpty()
                    )
                }.sortedBy { it.namaProduk.lowercase() }

                if (products.isEmpty()) {
                    hidePrimaryFilter()
                    setPrimaryButton("Data Produk") {
                        startActivity(Intent(this, AktivitasDaftarProduk::class.java))
                    }
                    hideSecondaryButton()
                    submitRows(listOf(infoBelumAdaProdukDasar()))
                    return@addOnSuccessListener
                }

                val initialProductId = intent.getStringExtra(EkstraAplikasi.EXTRA_PRODUCT_ID)
                initialSelectionIndex =
                    products.indexOfFirst { it.id == initialProductId }.takeIf { it >= 0 } ?: 0

                setPrimaryFilter(products.map { it.namaProduk }, initialSelectionIndex) {
                    loadParameters()
                }

                setPrimaryButton("Tambah Parameter") {
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

        firestore.collection("parameterProduksi")
            .whereEqualTo("idProduk", productId)
            .whereEqualTo("dihapus", false)
            .get()
            .addOnSuccessListener { snapshot ->
                parameters = snapshot.documents.map { doc ->
                    DataBarisParameter(
                        id = doc.id,
                        idProduk = doc.getString("idProduk").orEmpty(),
                        namaProduk = doc.getString("namaProduk").orEmpty(),
                        hasilPerProduksi = doc.getLong("hasilPerProduksi") ?: 0L,
                        satuanHasil = doc.getString("satuanHasil").orEmpty(),
                        aktif = doc.getBoolean("aktif") ?: false,
                        catatan = doc.getString("catatan").orEmpty()
                    )
                }.sortedWith(
                    compareByDescending<DataBarisParameter> { it.aktif }
                        .thenBy { it.id.lowercase() }
                )

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
                title = item.namaProduk.ifBlank { item.idProduk },
                subtitle = buildString {
                    append(item.id.uppercase())
                    append(" • ")
                    append("${item.hasilPerProduksi} ${item.satuanHasil} / produksi")
                    if (item.catatan.isNotBlank()) {
                        append(" • ${item.catatan}")
                    }
                },
                badge = if (item.aktif) "Aktif" else "Nonaktif",
                actionLabel = if (item.aktif) "Nonaktifkan" else "Aktifkan",
                deleteLabel = "Hapus",
                amount = "",
                tone = if (item.aktif) WarnaBaris.GREEN else WarnaBaris.GOLD
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

    override fun onRowAction(item: ItemBaris) {
        if (item.id.startsWith("info_")) return

        val parameter = parameters.firstOrNull { it.id == item.id } ?: return
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

    override fun onRowDelete(item: ItemBaris) {
        if (item.id.startsWith("info_")) return

        val parameter = parameters.firstOrNull { it.id == item.id } ?: return

        showConfirmationModal(
            title = "Hapus parameter?",
            message = "Parameter ${parameter.namaProduk} (${parameter.id}) akan di-soft delete. Lanjutkan?",
            confirmLabel = "Hapus"
        ) {
            softDeleteParameter(parameter)
        }
    }

    private fun activateParameterExclusively(parameter: DataBarisParameter) {
        firestore.collection("parameterProduksi")
            .whereEqualTo("idProduk", parameter.idProduk)
            .whereEqualTo("dihapus", false)
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = firestore.batch()
                val now = Timestamp.now()

                snapshot.documents.forEach { doc ->
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
        firestore.collection("parameterProduksi")
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
        val parameterRef = firestore.collection("parameterProduksi")

        parameterRef.document(parameter.id)
            .get()
            .addOnSuccessListener { targetDoc ->
                if (!targetDoc.exists()) {
                    showMessage("Data parameter tidak ditemukan.")
                    return@addOnSuccessListener
                }

                val isActive = targetDoc.getBoolean("aktif") == true

                parameterRef
                    .whereEqualTo("idProduk", parameter.idProduk)
                    .whereEqualTo("dihapus", false)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val otherDocs = snapshot.documents.filter { it.id != parameter.id }
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
    val catatan: String
)