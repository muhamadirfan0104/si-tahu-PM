package muhamad.irfan.si_tahu.ui.umum

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.data.Produk
import muhamad.irfan.si_tahu.databinding.ItemProductCardBinding
import muhamad.irfan.si_tahu.util.Formatter

class AdapterProduk(
    private val onAdd: (Produk) -> Unit,
    private val getHarga: (Produk) -> Long,
    private val getStatus: (Produk) -> String
) : RecyclerView.Adapter<AdapterProduk.PenampungProduk>() {

    private val items = mutableListOf<Produk>()

    fun submitList(newItems: List<Produk>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PenampungProduk {
        val binding = ItemProductCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PenampungProduk(binding)
    }

    override fun onBindViewHolder(holder: PenampungProduk, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PenampungProduk(
        private val binding: ItemProductCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Produk) {
            val status = getStatus(item)
            val harga = getHarga(item)
            val bisaDitambah = item.stock > 0 && harga > 0L

            binding.tvTitle.text = item.name
            binding.tvSubtitle.text = "Stok ${item.stock} ${item.unit} • Minimum ${item.minStock}"
            binding.tvPrice.text = Formatter.currency(harga)
            binding.tvCategory.text = item.category
            binding.tvStatus.text = status

            val tone = when (status) {
                "Aman" -> R.drawable.bg_tone_green
                "Menipis" -> R.drawable.bg_tone_gold
                else -> R.drawable.bg_tone_orange
            }

            binding.tvStatus.setBackgroundResource(tone)

            binding.btnAdd.isEnabled = bisaDitambah
            binding.btnAdd.alpha = if (bisaDitambah) 1f else 0.5f
            binding.btnAdd.text = if (bisaDitambah) {
                "Tambah ke Keranjang"
            } else {
                "Tidak Bisa Ditambahkan"
            }

            binding.btnAdd.setOnClickListener {
                if (bisaDitambah) {
                    onAdd(item)
                }
            }
        }
    }
}