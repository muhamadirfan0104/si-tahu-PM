package muhamad.irfan.si_tahu.ui.expense

import android.content.Intent
import android.os.Bundle
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.ui.base.AktivitasDaftarDasar
import muhamad.irfan.si_tahu.ui.report.AktivitasLaporan
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AktivitasDaftarPengeluaran : AktivitasDaftarDasar() {

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var expenses: List<DataBarisPengeluaran> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureScreen("Pengeluaran", "Riwayat biaya operasional")
        hidePrimaryFilter()
        hideSecondaryFilter()

        setPrimaryButton("Tambah Pengeluaran") {
            startActivity(Intent(this, AktivitasFormPengeluaran::class.java))
        }

        setSecondaryButton("Laporan") {
            startActivity(Intent(this, AktivitasLaporan::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadExpenses()
    }

    override fun onSearchChanged() = refresh()

    private fun loadExpenses() {
        firestore.collection("pengeluaran")
            .get()
            .addOnSuccessListener { snapshot ->
                expenses = snapshot.documents.map { doc ->
                    DataBarisPengeluaran(
                        id = doc.id,
                        tanggalTampil = extractDateLabel(doc.get("tanggalPengeluaran"), doc.getString("kunciTanggal")),
                        jenisPengeluaran = doc.getString("jenisPengeluaran").orEmpty(),
                        namaPengeluaran = doc.getString("namaPengeluaran").orEmpty(),
                        nominal = doc.getLong("nominal") ?: 0L,
                        catatan = doc.getString("catatan").orEmpty()
                    )
                }
                refresh()
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat pengeluaran: ${e.message}")
                expenses = emptyList()
                refresh()
            }
    }

    private fun refresh() {
        val query = searchText()

        val rows = expenses.filter { item ->
            (item.jenisPengeluaran + " " + item.namaPengeluaran + " " + item.catatan)
                .lowercase()
                .contains(query)
        }.map { item ->
            ItemBaris(
                id = item.id,
                title = item.namaPengeluaran.ifBlank { item.jenisPengeluaran },
                subtitle = item.tanggalTampil + if (item.catatan.isNotBlank()) " • ${item.catatan}" else "",
                badge = item.jenisPengeluaran.ifBlank { "Pengeluaran" },
                amount = Formatter.currency(item.nominal),
                tone = WarnaBaris.ORANGE
            )
        }

        submitRows(
            if (rows.isNotEmpty()) {
                rows
            } else {
                listOf(
                    ItemBaris(
                        id = "info_empty",
                        title = "Belum ada pengeluaran",
                        subtitle = "Data pengeluaran akan tampil dari Firebase.",
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

        val expense = expenses.firstOrNull { it.id == item.id } ?: return
        showDetailModal(
            title = expense.namaPengeluaran.ifBlank { expense.jenisPengeluaran },
            message = buildString {
                appendLine("Jenis: ${expense.jenisPengeluaran}")
                appendLine("Tanggal: ${expense.tanggalTampil}")
                appendLine("Nominal: ${Formatter.currency(expense.nominal)}")
                appendLine("Catatan: ${expense.catatan.ifBlank { "-" }}")
            },
            neutralLabel = "Edit",
            onNeutral = {
                startActivity(
                    Intent(this, AktivitasFormPengeluaran::class.java)
                        .putExtra("extra_expense_id", expense.id)
                )
            }
        )
    }

    override fun onRowAction(item: ItemBaris) {
        // Tidak dipakai dulu. Pengeluaran tetap ditampilkan dari Firebase.
    }

    private fun extractDateLabel(rawDate: Any?, fallbackDate: String?): String {
        return when (rawDate) {
            is Timestamp -> {
                val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
                sdf.format(rawDate.toDate())
            }
            is Date -> {
                val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
                sdf.format(rawDate)
            }
            is String -> rawDate
            else -> fallbackDate ?: "-"
        }
    }
}

private data class DataBarisPengeluaran(
    val id: String,
    val tanggalTampil: String,
    val jenisPengeluaran: String,
    val namaPengeluaran: String,
    val nominal: Long,
    val catatan: String
)