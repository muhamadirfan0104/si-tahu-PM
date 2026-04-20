package muhamad.irfan.si_tahu.ui.utama

import android.view.View
import android.widget.AdapterView

class PendengarPilihItemSederhana(
    private val onSelected: () -> Unit
) : AdapterView.OnItemSelectedListener {

    override fun onItemSelected(
        parent: AdapterView<*>?,
        view: View?,
        position: Int,
        id: Long
    ) {
        onSelected()
    }

    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
}