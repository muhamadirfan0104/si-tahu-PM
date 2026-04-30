package muhamad.irfan.si_tahu.ui.umum

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import muhamad.irfan.si_tahu.util.ItemBaris

/**
 * Compose-backed list adapter facade. Existing screens can keep calling submitList(),
 * while Compose renders items directly from [items] without XML.
 *
 * Optimization: keep the list as a single immutable snapshot. This avoids two UI updates
 * for every refresh (clear + addAll) and reduces unnecessary recomposition in LazyColumn.
 */
class AdapterBarisUmum(
    val onItemClick: (ItemBaris) -> Unit,
    val onActionClick: ((ItemBaris, View) -> Unit)? = null,
    val onEditClick: ((ItemBaris, View) -> Unit)? = null,
    val onDeleteClick: ((ItemBaris) -> Unit)? = null
) {
    var items by mutableStateOf<List<ItemBaris>>(emptyList())
        private set

    fun submitList(newItems: List<ItemBaris>) {
        if (items === newItems || items == newItems) return
        items = newItems.toList()
    }
}
