package muhamad.irfan.si_tahu.util

import android.content.Context
import android.widget.ArrayAdapter
import muhamad.irfan.si_tahu.R

object AdapterSpinner {
    fun stringAdapter(context: Context, items: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(context, R.layout.item_spinner_selected, items.toMutableList()).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
    }
}
