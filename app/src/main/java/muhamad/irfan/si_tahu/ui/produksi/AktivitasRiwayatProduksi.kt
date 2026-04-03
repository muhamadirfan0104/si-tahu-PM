package muhamad.irfan.si_tahu.ui.production

import android.os.Bundle
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.ui.base.AktivitasDaftarDasar
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris
import java.text.SimpleDateFormat
import java.util.Locale

class AktivitasRiwayatProduksi : AktivitasDaftarDasar() {

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var histories: List<BarisRiwayatProduksi> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureScreen("Riwayat Produksi", "Daftar produksi tahu dasar")
        hidePrimaryFilter()
        hideSecondaryFilter()
        hideButtons()
    }

    override fun onResume() {
        super.onResume()
        loadHistories()
    }

    override fun onSearchChanged() = refresh()

    private fun loadHistories() {
        firestore.collection("catatanProduksi")
            .whereEqualTo("jenisProduksi", "DASAR")
            .get()
            .addOnSuccessListener { snapshot ->
                histories = snapshot.documents.map { doc ->
                    BarisRiwayatProduksi(
                        id = doc.id,
                        namaProduk = doc.getString("namaProdukHasil").orEmpty(),
                        tanggal = formatTimestamp(doc.getTimestamp("tanggalProduksi")),
                        jumlahHasil = doc.getLong("jumlahHasil") ?: 0L,
                        satuanHasil = doc.getString("satuanHasil").orEmpty(),
                        catatan = doc.getString("catatan").orEmpty()
                    )
                }.sortedByDescending { it.tanggal }

                refresh()
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat riwayat produksi: ${e.message}")
                histories = emptyList()
                refresh()
            }
    }

    private fun refresh() {
        val query = searchText()

        val rows = histories.filter { item ->
            (item.id + " " + item.namaProduk + " " + item.catatan)
                .lowercase()
                .contains(query)
        }.map { item ->
            ItemBaris(
                id = item.id,
                title = item.id,
                subtitle = "${item.namaProduk} • ${item.tanggal}",
                badge = "Produksi",
                amount = "${item.jumlahHasil} ${item.satuanHasil}",
                tone = WarnaBaris.GREEN
            )
        }

        submitRows(
            if (rows.isNotEmpty()) {
                rows
            } else {
                listOf(
                    ItemBaris(
                        id = "info_empty",
                        title = "Belum ada riwayat produksi",
                        subtitle = "Riwayat produksi dasar akan tampil dari Firebase.",
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

        val detail = histories.firstOrNull { it.id == item.id } ?: return
        showDetailModal(
            detail.id,
            buildString {
                appendLine("Produk: ${detail.namaProduk}")
                appendLine("Tanggal: ${detail.tanggal}")
                appendLine("Hasil: ${detail.jumlahHasil} ${detail.satuanHasil}")
                appendLine("Catatan: ${detail.catatan.ifBlank { "-" }}")
            }
        )
    }

    override fun onRowAction(item: ItemBaris) {
        // Tidak dipakai dulu
    }

    private fun formatTimestamp(timestamp: Timestamp?): String {
        if (timestamp == null) return "-"
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
        return sdf.format(timestamp.toDate())
    }
}

private data class BarisRiwayatProduksi(
    val id: String,
    val namaProduk: String,
    val tanggal: String,
    val jumlahHasil: Long,
    val satuanHasil: String,
    val catatan: String
)