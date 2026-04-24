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
            val stokLayakJual = item.safeStock + item.nearExpiredStock
            val bisaDitambah = stokLayakJual > 0 && harga > 0L && status != "Habis" && status != "Kadaluarsa"

            binding.tvTitle.text = item.name
            binding.tvSubtitle.text = buildString {
                append("Stok layak jual ${Formatter.ribuan(stokLayakJual.toLong())} ${item.unit}")
                if (item.expiredStock > 0) {
                    append(" • Kadaluarsa ${Formatter.ribuan(item.expiredStock.toLong())}")
                }
                if (item.nearestExpiryDate.isNotBlank()) {
                    append(" • ED ${Formatter.readableShortDate(item.nearestExpiryDate)}")
                }
            }
            binding.tvPrice.text = Formatter.currency(harga)
            binding.tvCategory.text = item.category
            binding.tvStatus.text = status

            val tone = when (status) {
                "Produksi Hari Ini" -> R.drawable.bg_tone_green
                "Stok Sisa" -> R.drawable.bg_tone_gold
                "Hampir Kadaluarsa" -> R.drawable.bg_tone_orange
                "Kadaluarsa" -> R.drawable.bg_tone_red
                "Habis" -> R.drawable.bg_tone_red
                else -> R.drawable.bg_tone_neutral
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