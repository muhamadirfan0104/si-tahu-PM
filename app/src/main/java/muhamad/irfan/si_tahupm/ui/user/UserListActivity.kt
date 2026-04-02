package muhamad.irfan.si_tahupm.ui.user

import android.content.Intent
import android.os.Bundle
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.ui.base.BaseListScreenActivity
import muhamad.irfan.si_tahupm.util.AppExtras
import muhamad.irfan.si_tahupm.util.RowItem
import muhamad.irfan.si_tahupm.util.RowTone

class UserListActivity : BaseListScreenActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureScreen("Pengguna", "Kelola akun admin dan kasir")
        hidePrimaryFilter()
        hideSecondaryFilter()
        setPrimaryButton("Tambah Pengguna") { startActivity(Intent(this, UserFormActivity::class.java)) }
        setSecondaryButton("Reset Demo") {
            DemoRepository.resetDemo()
            showMessage("Data dummy berhasil direset.")
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onSearchChanged() = refresh()

    private fun refresh() {
        val query = searchText()
        submitRows(DemoRepository.allUsers().filter {
            (it.name + " " + it.email + " " + it.role.name).lowercase().contains(query)
        }.map {
            RowItem(
                id = it.id,
                title = it.name,
                subtitle = it.role.name + " • " + it.email,
                badge = if (it.active) "Aktif" else "Nonaktif",
                amount = "",
                actionLabel = "Hapus",
                tone = if (it.role.name == "ADMIN") RowTone.GREEN else RowTone.GOLD
            )
        })
    }

    override fun onRowClick(item: RowItem) {
        startActivity(Intent(this, UserFormActivity::class.java).putExtra(AppExtras.EXTRA_USER_ID, item.id))
    }

    override fun onRowAction(item: RowItem) {
        runCatching { DemoRepository.softDeleteUser(item.id) }
            .onSuccess {
                showMessage("Pengguna berhasil dihapus secara soft delete.")
                refresh()
            }
            .onFailure { showMessage(it.message ?: "Gagal menghapus pengguna") }
    }
}
