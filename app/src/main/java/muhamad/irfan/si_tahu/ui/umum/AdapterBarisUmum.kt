package muhamad.irfan.si_tahu.ui.umum

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.databinding.ItemGenericRowBinding
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class AdapterBarisUmum(
    private val onItemClick: (ItemBaris) -> Unit,
    private val onActionClick: ((ItemBaris) -> Unit)? = null,
    private val onEditClick: ((ItemBaris) -> Unit)? = null,
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

            binding.tvLeading.text =
                item.title.firstOrNull()?.uppercaseChar()?.toString() ?: "#"

            binding.tvBadge.isVisible = item.badge.isNotBlank()
            binding.tvAmount.isVisible = item.amount.isNotBlank()
            binding.cardPriceStatus.isVisible = item.priceStatus.isNotBlank()

            binding.btnAction.isVisible = !item.actionLabel.isNullOrBlank()
            binding.btnAction.text = item.actionLabel.orEmpty()

            binding.btnEdit.isVisible = !item.editLabel.isNullOrBlank()
            binding.btnEdit.text = item.editLabel.orEmpty()

            binding.btnDelete.isVisible = !item.deleteLabel.isNullOrBlank()
            binding.btnDelete.text = item.deleteLabel.orEmpty()

            binding.root.setOnClickListener { onItemClick(item) }
            binding.btnAction.setOnClickListener { onActionClick?.invoke(item) }
            binding.btnEdit.setOnClickListener { onEditClick?.invoke(item) }
            binding.btnDelete.setOnClickListener { onDeleteClick?.invoke(item) }

            val bgRes = when (item.tone) {
                WarnaBaris.GREEN -> R.drawable.bg_tone_green
                WarnaBaris.GOLD -> R.drawable.bg_tone_gold
                WarnaBaris.ORANGE -> R.drawable.bg_tone_orange
                WarnaBaris.BLUE -> R.drawable.bg_tone_blue
                WarnaBaris.RED -> R.drawable.bg_tone_red
                WarnaBaris.DEFAULT -> R.drawable.bg_tone_neutral
            }
            binding.tvLeading.setBackgroundResource(bgRes)

            val priceBgRes = when (item.priceTone) {
                WarnaBaris.GREEN -> R.drawable.bg_tone_green
                WarnaBaris.GOLD -> R.drawable.bg_tone_gold
                WarnaBaris.ORANGE -> R.drawable.bg_tone_orange
                WarnaBaris.BLUE -> R.drawable.bg_tone_blue
                WarnaBaris.RED -> R.drawable.bg_tone_red
                WarnaBaris.DEFAULT -> R.drawable.bg_tone_neutral
            }
            binding.tvPriceStatus.setBackgroundResource(priceBgRes)
        }
    }
}