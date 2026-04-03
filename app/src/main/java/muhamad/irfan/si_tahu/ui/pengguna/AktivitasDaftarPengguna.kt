package muhamad.irfan.si_tahu.ui.user

import android.content.Intent
import android.os.Bundle
import muhamad.irfan.si_tahu.data.RepositoriLokal
import muhamad.irfan.si_tahu.ui.base.AktivitasDaftarDasar
import muhamad.irfan.si_tahu.util.EkstraAplikasi
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class AktivitasDaftarPengguna : AktivitasDaftarDasar() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureScreen("Pengguna", "Kelola akun admin dan kasir")
        hidePrimaryFilter()
        hideSecondaryFilter()
        setPrimaryButton("Tambah Pengguna") {
            startActivity(Intent(this, AktivitasFormPengguna::class.java))
        }
        hideSecondaryButton()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onSearchChanged() = refresh()

    private fun refresh() {
        val query = searchText()
        submitRows(
            RepositoriLokal.allUsers().filter {
                (it.name + " " + it.email + " " + it.role.name).lowercase().contains(query)
            }.map {
                ItemBaris(
                    id = it.id,
                    title = it.name,
                    subtitle = it.role.name + " • " + it.email,
                    badge = if (it.active) "Aktif" else "Nonaktif",
                    amount = "",
                    actionLabel = "Hapus",
                    tone = if (it.role.name == "ADMIN") WarnaBaris.GREEN else WarnaBaris.GOLD
                )
            }
        )
    }

    override fun onRowClick(item: ItemBaris) {
        startActivity(
            Intent(this, AktivitasFormPengguna::class.java)
                .putExtra(EkstraAplikasi.EXTRA_USER_ID, item.id)
        )
    }

    override fun onRowAction(item: ItemBaris) {
        showConfirmationModal(
            title = "Hapus pengguna?",
            message = "Pengguna ${item.title} akan di-soft delete. Transaksi lama tetap tersimpan. Lanjutkan?"
        ) {
            runCatching { RepositoriLokal.softDeleteUser(item.id) }
                .onSuccess {
                    showMessage("Pengguna berhasil dihapus secara soft delete.")
                    refresh()
                }
                .onFailure { showMessage(it.message ?: "Gagal menghapus pengguna") }
        }
    }
}
