package muhamad.irfan.si_tahu.ui.pengguna

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDaftarDasar
import muhamad.irfan.si_tahu.util.EkstraAplikasi
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class AktivitasDaftarPengguna : AktivitasDaftarDasar() {

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var users: List<DataBarisPengguna> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureScreen(
            title = "Pengguna",
            subtitle = "Kelola admin dan kasir",
            searchHint = "Cari nama, email, atau telepon..."
        )

        setPrimaryFilter(listOf("Semua", "ADMIN", "KASIR")) {
            refresh()
        }

        setSecondaryFilter(listOf("Semua", "Aktif", "Nonaktif")) {
            refresh()
        }

        setPrimaryButton("Tambah Pengguna") {
            startActivity(Intent(this, AktivitasFormPengguna::class.java))
        }

        hideSecondaryButton()
    }

    override fun onResume() {
        super.onResume()
        loadUsers()
    }

    override fun onSearchChanged() = refresh()

    private fun loadUsers() {
        firestore.collection("pengguna")
            .get()
            .addOnSuccessListener { snapshot ->
                users = snapshot.documents.map { doc ->
                    DataBarisPengguna(
                        id = doc.id,
                        namaPengguna = doc.getString("namaPengguna").orEmpty(),
                        email = doc.getString("email").orEmpty(),
                        nomorTelepon = doc.getString("nomorTelepon").orEmpty(),
                        peranAsli = doc.getString("peranAsli").orEmpty(),
                        aktif = doc.getBoolean("aktif") ?: true
                    )
                }.sortedWith(
                    compareByDescending<DataBarisPengguna> { it.aktif }
                        .thenBy { it.namaPengguna.lowercase() }
                )

                refresh()
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat pengguna: ${e.message}")
                users = emptyList()
                refresh()
            }
    }

    private fun refresh() {
        val query = searchText()
        val roleFilter = primarySelection()
        val statusFilter = secondarySelection()

        val filtered = users.filter { item ->
            val matchQuery = listOf(
                item.namaPengguna,
                item.email,
                item.nomorTelepon
            ).joinToString(" ").lowercase().contains(query)

            val matchRole = when (roleFilter) {
                "ADMIN" -> item.peranAsli == "ADMIN"
                "KASIR" -> item.peranAsli == "KASIR"
                else -> true
            }

            val matchStatus = when (statusFilter) {
                "Aktif" -> item.aktif
                "Nonaktif" -> !item.aktif
                else -> true
            }

            matchQuery && matchRole && matchStatus
        }

        val rows = filtered.map { item ->
            ItemBaris(
                id = item.id,
                title = item.namaPengguna.ifBlank { item.id },
                subtitle = buildString {
                    append(item.email.ifBlank { "-" })
                    append(" • ")
                    append(item.nomorTelepon.ifBlank { "-" })
                },
                badge = item.peranAsli.ifBlank { "Pengguna" },
                amount = if (item.aktif) "Aktif" else "Nonaktif",
                actionLabel = if (item.aktif) "Nonaktifkan" else "Aktifkan",
                deleteLabel = "Hapus",
                tone = when {
                    item.aktif && item.peranAsli == "ADMIN" -> WarnaBaris.GREEN
                    item.aktif -> WarnaBaris.GOLD
                    else -> WarnaBaris.ORANGE
                }
            )
        }

        submitRows(
            if (rows.isNotEmpty()) {
                rows
            } else {
                listOf(
                    ItemBaris(
                        id = "info_pengguna_kosong",
                        title = "Belum ada pengguna",
                        subtitle = "Tambahkan admin atau kasir untuk mulai menggunakan data pengguna.",
                        badge = "Info",
                        amount = "",
                        tone = WarnaBaris.BLUE
                    )
                )
            }
        )
    }

    override fun onRowClick(item: ItemBaris) {
        if (item.id.startsWith("info_")) return

        startActivity(
            Intent(this, AktivitasFormPengguna::class.java)
                .putExtra(EkstraAplikasi.EXTRA_USER_ID, item.id)
        )
    }

    override fun onRowAction(item: ItemBaris, anchor: View) {
        if (item.id.startsWith("info_")) return

        val user = users.firstOrNull { it.id == item.id } ?: return
        val nextActive = !user.aktif

        showConfirmationModal(
            title = if (nextActive) "Aktifkan pengguna?" else "Nonaktifkan pengguna?",
            message = if (nextActive) {
                "Pengguna ${user.namaPengguna} akan diaktifkan."
            } else {
                "Pengguna ${user.namaPengguna} akan dinonaktifkan."
            },
            confirmLabel = if (nextActive) "Aktifkan" else "Nonaktifkan"
        ) {
            firestore.collection("pengguna")
                .document(user.id)
                .update(
                    mapOf(
                        "aktif" to nextActive,
                        "bolehMasuk" to nextActive,
                        "diperbaruiPada" to Timestamp.now()
                    )
                )
                .addOnSuccessListener {
                    showMessage(
                        if (nextActive) "Pengguna berhasil diaktifkan."
                        else "Pengguna berhasil dinonaktifkan."
                    )
                    loadUsers()
                }
                .addOnFailureListener { e ->
                    showMessage("Gagal mengubah status pengguna: ${e.message}")
                }
        }
    }

    override fun onRowDelete(item: ItemBaris) {
        if (item.id.startsWith("info_")) return

        val user = users.firstOrNull { it.id == item.id } ?: return

        showConfirmationModal(
            title = "Hapus pengguna?",
            message = "Pengguna ${user.namaPengguna} akan dihapus permanen. Lanjutkan?",
            confirmLabel = "Hapus"
        ) {
            firestore.collection("pengguna")
                .document(user.id)
                .delete()
                .addOnSuccessListener {
                    showMessage("Pengguna berhasil dihapus.")
                    loadUsers()
                }
                .addOnFailureListener { e ->
                    showMessage("Gagal menghapus pengguna: ${e.message}")
                }
        }
    }
}

private data class DataBarisPengguna(
    val id: String,
    val namaPengguna: String,
    val email: String,
    val nomorTelepon: String,
    val peranAsli: String,
    val aktif: Boolean
)