package muhamad.irfan.si_tahu.ui.umum

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import muhamad.irfan.si_tahu.data.Produk

class AdapterProduk(
    val onAdd: (Produk) -> Unit,
    val getHarga: (Produk) -> Long,
    val getStatus: (Produk) -> String
) {
    var items by mutableStateOf<List<Produk>>(emptyList())
        private set

    fun submitList(newItems: List<Produk>) {
        if (items === newItems || items == newItems) return
        items = newItems.toList()
    }
}
