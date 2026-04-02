package muhamad.irfan.si_tahupm.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import muhamad.irfan.si_tahupm.R
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.data.Product
import muhamad.irfan.si_tahupm.databinding.ItemProductCardBinding
import muhamad.irfan.si_tahupm.util.Formatters

class ProductAdapter(
    private val onAdd: (Product) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    private val items = mutableListOf<Product>()

    fun submitList(newItems: List<Product>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ProductViewHolder(private val binding: ItemProductCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Product) {
            binding.tvTitle.text = item.name
            binding.tvSubtitle.text = "Stok ${item.stock} ${item.unit} • Minimum ${item.minStock}"
            binding.tvPrice.text = Formatters.currency(DemoRepository.defaultChannel(item)?.price ?: 0)
            binding.tvCategory.text = item.category
            val status = DemoRepository.productStatus(item)
            binding.tvStatus.text = status
            val tone = when (status) {
                "Aman" -> R.drawable.bg_tone_green
                "Menipis" -> R.drawable.bg_tone_gold
                else -> R.drawable.bg_tone_orange
            }
            binding.tvStatus.setBackgroundResource(tone)
            binding.btnAdd.setOnClickListener { onAdd(item) }
        }
    }
}
