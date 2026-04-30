package muhamad.irfan.si_tahu.ui.umum

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import muhamad.irfan.si_tahu.data.ItemKeranjang
import muhamad.irfan.si_tahu.data.Produk

class AdapterKeranjang(
    val onIncrease: (ItemKeranjang) -> Unit,
    val onDecrease: (ItemKeranjang) -> Unit,
    val onRemove: (ItemKeranjang) -> Unit,
    val getProduk: (String) -> Produk?
) {
    var items by mutableStateOf<List<ItemKeranjang>>(emptyList())
        private set

    fun submitList(newItems: List<ItemKeranjang>) {
        if (items === newItems || items == newItems) return
        items = newItems.toList()
    }
}
