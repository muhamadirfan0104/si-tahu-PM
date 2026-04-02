package muhamad.irfan.si_tahupm.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import muhamad.irfan.si_tahupm.data.CartItem
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.databinding.ItemCartRowBinding
import muhamad.irfan.si_tahupm.util.Formatters

class CartAdapter(
    private val onIncrease: (CartItem) -> Unit,
    private val onDecrease: (CartItem) -> Unit,
    private val onRemove: (CartItem) -> Unit
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    private val items = mutableListOf<CartItem>()

    fun submitList(newItems: List<CartItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val binding = ItemCartRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CartViewHolder(private val binding: ItemCartRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CartItem) {
            val product = DemoRepository.getProduct(item.productId)
            binding.tvTitle.text = product?.name ?: "Produk"
            binding.tvSubtitle.text = "${item.qty} x ${Formatters.currency(item.price)}"
            binding.tvQty.text = item.qty.toString()
            binding.tvTotal.text = Formatters.currency(item.qty.toLong() * item.price)
            binding.btnMinus.setOnClickListener { onDecrease(item) }
            binding.btnPlus.setOnClickListener { onIncrease(item) }
            binding.btnDelete.setOnClickListener { onRemove(item) }
        }
    }
}
