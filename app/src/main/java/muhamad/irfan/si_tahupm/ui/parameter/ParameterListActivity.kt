package muhamad.irfan.si_tahupm.ui.parameter

import android.content.Intent
import android.os.Bundle
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.ui.base.BaseListScreenActivity
import muhamad.irfan.si_tahupm.util.AppExtras
import muhamad.irfan.si_tahupm.util.RowItem
import muhamad.irfan.si_tahupm.util.RowTone

class ParameterListActivity : BaseListScreenActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureScreen("Parameter Produksi", "Standar hasil per masak")
        hidePrimaryFilter()
        hideSecondaryFilter()
        setPrimaryButton("Tambah Parameter") { startActivity(Intent(this, ParameterFormActivity::class.java)) }
        hideSecondaryButton()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onSearchChanged() = refresh()

    private fun refresh() {
        val query = searchText()
        submitRows(DemoRepository.allParameters().filter {
            val productName = DemoRepository.getProduct(it.productId)?.name ?: it.productId
            (productName + " " + it.note).lowercase().contains(query)
        }.map {
            RowItem(
                id = it.id,
                title = DemoRepository.getProduct(it.productId)?.name ?: it.productId,
                subtitle = "${it.resultPerBatch} pcs / masak • ${it.note}",
                badge = if (it.active) "Aktif" else "Nonaktif",
                actionLabel = "Hapus",
                amount = "",
                tone = if (it.active) RowTone.GREEN else RowTone.GOLD
            )
        })
    }

    override fun onRowClick(item: RowItem) {
        startActivity(Intent(this, ParameterFormActivity::class.java).putExtra(AppExtras.EXTRA_PARAMETER_ID, item.id))
    }

    override fun onRowAction(item: RowItem) {
        showConfirmationModal(
            title = "Hapus parameter?",
            message = "Parameter ${item.title} akan dihapus. Minimal harus tersisa 1 parameter aktif di aplikasi. Lanjutkan?"
        ) {
            runCatching { DemoRepository.deleteParameter(item.id) }
                .onSuccess {
                    showMessage("Parameter berhasil dihapus.")
                    refresh()
                }
                .onFailure { showMessage(it.message ?: "Gagal menghapus parameter") }
        }
    }
}
