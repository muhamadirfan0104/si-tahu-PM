package muhamad.irfan.si_tahu.ui.parameter

import android.content.Intent
import android.os.Bundle
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.ui.base.AktivitasDaftarDasar
import muhamad.irfan.si_tahu.util.EkstraAplikasi
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class AktivitasDaftarParameter : AktivitasDaftarDasar() {

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var parameters: List<DataBarisParameter> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureScreen("Parameter Produksi", "Standar hasil per masak")
        hidePrimaryFilter()
        hideSecondaryFilter()

        setPrimaryButton("Tambah Parameter") {
            startActivity(Intent(this, AktivitasFormParameter::class.java))
        }

        hideSecondaryButton()
    }

    override fun onResume() {
        super.onResume()
        loadParameters()
    }

    override fun onSearchChanged() = refresh()

    private fun loadParameters() {
        firestore.collection("parameterProduksi")
            .get()
            .addOnSuccessListener { snapshot ->
                parameters = snapshot.documents.map { doc ->
                    DataBarisParameter(
                        id = doc.id,
                        idProduk = doc.getString("idProduk").orEmpty(),
                        kodeProduk = doc.getString("kodeProduk").orEmpty(),
                        namaProduk = doc.getString("namaProduk").orEmpty(),
                        hasilPerProduksi = doc.getLong("hasilPerProduksi") ?: 0L,
                        satuanHasil = doc.getString("satuanHasil").orEmpty(),
                        aktif = doc.getBoolean("aktif") ?: false,
                        catatan = doc.getString("catatan").orEmpty()
                    )
                }
                refresh()
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat parameter produksi: ${e.message}")
                parameters = emptyList()
                refresh()
            }
    }

    private fun refresh() {
        val query = searchText()

        val rows = parameters.filter { item ->
            (item.namaProduk + " " + item.catatan).lowercase().contains(query)
        }.map { item ->
            ItemBaris(
                id = item.id,
                title = item.namaProduk.ifBlank { item.idProduk },
                subtitle = "${item.hasilPerProduksi} ${item.satuanHasil} / produksi • ${item.catatan}",
                badge = if (item.aktif) "Aktif" else "Nonaktif",
                actionLabel = if (item.aktif) "Nonaktifkan" else "Aktifkan",
                amount = "",
                tone = if (item.aktif) WarnaBaris.GREEN else WarnaBaris.GOLD
            )
        }

        submitRows(
            if (rows.isNotEmpty()) {
                rows
            } else {
                listOf(
                    ItemBaris(
                        id = "info_empty",
                        title = "Belum ada parameter produksi",
                        subtitle = "Parameter produksi akan tampil dari koleksi Firebase.",
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
            Intent(this, AktivitasFormParameter::class.java)
                .putExtra(EkstraAplikasi.EXTRA_PARAMETER_ID, item.id)
        )
    }

    override fun onRowAction(item: ItemBaris) {
        if (item.id == "info_empty") return

        val parameter = parameters.firstOrNull { it.id == item.id } ?: return
        val nextActive = !parameter.aktif

        showConfirmationModal(
            title = if (nextActive) "Aktifkan parameter?" else "Nonaktifkan parameter?",
            message = if (nextActive) {
                "Parameter ${parameter.namaProduk} akan diaktifkan kembali."
            } else {
                "Parameter ${parameter.namaProduk} akan dinonaktifkan."
            },
            confirmLabel = if (nextActive) "Aktifkan" else "Nonaktifkan"
        ) {
            firestore.collection("parameterProduksi")
                .document(parameter.id)
                .update("aktif", nextActive)
                .addOnSuccessListener {
                    showMessage(
                        if (nextActive) "Parameter berhasil diaktifkan."
                        else "Parameter berhasil dinonaktifkan."
                    )
                    loadParameters()
                }
                .addOnFailureListener { e ->
                    showMessage("Gagal mengubah parameter: ${e.message}")
                }
        }
    }
}

private data class DataBarisParameter(
    val id: String,
    val idProduk: String,
    val kodeProduk: String,
    val namaProduk: String,
    val hasilPerProduksi: Long,
    val satuanHasil: String,
    val aktif: Boolean,
    val catatan: String
)