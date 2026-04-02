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

    private fun refresh() {
        submitRows(DemoRepository.allParameters().map {
            RowItem(
                id = it.id,
                title = DemoRepository.getProduct(it.productId)?.name ?: it.productId,
                subtitle = "${it.resultPerBatch} pcs / masak • ${it.note}",
                badge = if (it.active) "Aktif" else "Nonaktif",
                amount = "",
                tone = if (it.active) RowTone.GREEN else RowTone.GOLD
            )
        })
    }

    override fun onRowClick(item: RowItem) {
        startActivity(Intent(this, ParameterFormActivity::class.java).putExtra(AppExtras.EXTRA_PARAMETER_ID, item.id))
    }
}
