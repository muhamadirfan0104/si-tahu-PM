package muhamad.irfan.si_tahupm.ui.expense

import android.content.Intent
import android.os.Bundle
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.ui.base.BaseListScreenActivity
import muhamad.irfan.si_tahupm.ui.report.ReportActivity
import muhamad.irfan.si_tahupm.util.AppExtras
import muhamad.irfan.si_tahupm.util.Formatters
import muhamad.irfan.si_tahupm.util.RowItem
import muhamad.irfan.si_tahupm.util.RowTone

class ExpenseListActivity : BaseListScreenActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureScreen("Pengeluaran", "Riwayat biaya operasional")
        hidePrimaryFilter()
        hideSecondaryFilter()
        setPrimaryButton("Tambah Pengeluaran") { startActivity(Intent(this, ExpenseFormActivity::class.java)) }
        setSecondaryButton("Laporan") { startActivity(Intent(this, ReportActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onSearchChanged() = refresh()

    private fun refresh() {
        val query = searchText()
        submitRows(DemoRepository.allExpenses().filter {
            (it.category + " " + it.note).lowercase().contains(query)
        }.map {
            RowItem(
                id = it.id,
                title = it.category,
                subtitle = Formatters.readableDate(it.date) + if (it.note.isNotBlank()) " • ${it.note}" else "",
                badge = "Pengeluaran",
                amount = Formatters.currency(it.amount),
                actionLabel = "Hapus",
                tone = RowTone.ORANGE
            )
        })
    }

    override fun onRowClick(item: RowItem) {
        showDetailModal(
            title = item.title,
            message = DemoRepository.buildExpenseDetailText(item.id),
            neutralLabel = "Edit",
            onNeutral = {
                startActivity(Intent(this, ExpenseFormActivity::class.java).putExtra(AppExtras.EXTRA_EXPENSE_ID, item.id))
            }
        )
    }

    override fun onRowAction(item: RowItem) {
        showConfirmationModal(
            title = "Hapus pengeluaran?",
            message = "Pengeluaran ${item.title} akan dihapus dari riwayat. Lanjutkan?"
        ) {
            runCatching { DemoRepository.deleteExpense(item.id) }
                .onSuccess {
                    showMessage("Pengeluaran berhasil dihapus.")
                    refresh()
                }
                .onFailure { showMessage(it.message ?: "Gagal menghapus pengeluaran") }
        }
    }
}
