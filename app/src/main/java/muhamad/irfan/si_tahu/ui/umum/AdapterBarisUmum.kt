package muhamad.irfan.si_tahu.ui.umum

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.databinding.ItemGenericRowBinding
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class AdapterBarisUmum(
    private val onItemClick: (ItemBaris) -> Unit,
    private val onActionClick: ((ItemBaris, View) -> Unit)? = null,
    private val onEditClick: ((ItemBaris, View) -> Unit)? = null,
    private val onDeleteClick: ((ItemBaris) -> Unit)? = null
) : RecyclerView.Adapter<AdapterBarisUmum.PenampungBaris>() {

    private val items = mutableListOf<ItemBaris>()

    fun submitList(newItems: List<ItemBaris>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PenampungBaris {
        val binding = ItemGenericRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PenampungBaris(binding)
    }

    override fun onBindViewHolder(holder: PenampungBaris, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PenampungBaris(
        private val binding: ItemGenericRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ItemBaris) {
            binding.tvTitle.text = item.title
            binding.tvSubtitle.text = item.subtitle
            binding.tvBadge.text = item.badge
            binding.tvAmount.text = item.amount
            binding.tvPriceStatus.text = item.priceStatus
            binding.tvParameterStatus.text = item.parameterStatus
            binding.btnAction.text = if (item.actionLabel.isNullOrBlank()) "⋮" else item.actionLabel

            binding.tvLeading.text =
                item.title.firstOrNull()?.uppercaseChar()?.toString() ?: "#"

            binding.cardBadge.isVisible = item.badge.isNotBlank()
            binding.tvAmount.isVisible = item.amount.isNotBlank()
            binding.cardPriceStatus.isVisible = item.priceStatus.isNotBlank()
            binding.cardParameterStatus.isVisible = item.parameterStatus.isNotBlank()
            binding.btnAction.isVisible = !item.actionLabel.isNullOrBlank()

            binding.root.setOnClickListener { onItemClick(item) }
            binding.btnAction.setOnClickListener { view -> onActionClick?.invoke(item, view) }

            binding.tvLeading.setBackgroundResource(backgroundForTone(item.tone))
            binding.tvPriceStatus.setBackgroundResource(backgroundForTone(item.priceTone))
            binding.tvParameterStatus.setBackgroundResource(backgroundForTone(item.parameterTone))
        }

        private fun backgroundForTone(tone: WarnaBaris): Int {
            return when (tone) {
                WarnaBaris.GREEN -> R.drawable.bg_tone_green
                WarnaBaris.GOLD -> R.drawable.bg_tone_gold
                WarnaBaris.ORANGE -> R.drawable.bg_tone_orange
                WarnaBaris.BLUE -> R.drawable.bg_tone_blue
                WarnaBaris.RED -> R.drawable.bg_tone_red
                WarnaBaris.DEFAULT -> R.drawable.bg_tone_neutral
            }
        }
    }
}
