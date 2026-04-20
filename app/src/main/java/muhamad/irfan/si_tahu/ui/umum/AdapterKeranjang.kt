package muhamad.irfan.si_tahu.ui.umum

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import muhamad.irfan.si_tahu.data.ItemKeranjang
import muhamad.irfan.si_tahu.data.Produk
import muhamad.irfan.si_tahu.databinding.ItemCartRowBinding
import muhamad.irfan.si_tahu.util.Formatter

class AdapterKeranjang(
    private val onIncrease: (ItemKeranjang) -> Unit,
    private val onDecrease: (ItemKeranjang) -> Unit,
    private val onRemove: (ItemKeranjang) -> Unit,
    private val getProduk: (String) -> Produk?
) : RecyclerView.Adapter<AdapterKeranjang.PenampungKeranjang>() {

    private val items = mutableListOf<ItemKeranjang>()

    fun submitList(newItems: List<ItemKeranjang>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PenampungKeranjang {
        val binding = ItemCartRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PenampungKeranjang(binding)
    }

    override fun onBindViewHolder(holder: PenampungKeranjang, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PenampungKeranjang(
        private val binding: ItemCartRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ItemKeranjang) {
            val product = getProduk(item.productId)
            binding.tvTitle.text = product?.name ?: "Produk"
            binding.tvSubtitle.text = "${item.qty} x ${Formatter.currency(item.price)}"
            binding.tvQty.text = item.qty.toString()
            binding.tvTotal.text = Formatter.currency(item.qty.toLong() * item.price)
            binding.btnMinus.setOnClickListener { onDecrease(item) }
            binding.btnPlus.setOnClickListener { onIncrease(item) }
            binding.btnDelete.setOnClickListener { onRemove(item) }
        }
    }
}
