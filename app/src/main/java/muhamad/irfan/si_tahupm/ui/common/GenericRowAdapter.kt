package muhamad.irfan.si_tahupm.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import muhamad.irfan.si_tahupm.R
import muhamad.irfan.si_tahupm.databinding.ItemGenericRowBinding
import muhamad.irfan.si_tahupm.util.RowItem
import muhamad.irfan.si_tahupm.util.RowTone

class GenericRowAdapter(
    private val onItemClick: (RowItem) -> Unit,
    private val onActionClick: ((RowItem) -> Unit)? = null
) : RecyclerView.Adapter<GenericRowAdapter.RowViewHolder>() {

    private val items = mutableListOf<RowItem>()

    fun submitList(newItems: List<RowItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val binding = ItemGenericRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RowViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class RowViewHolder(private val binding: ItemGenericRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RowItem) {
            binding.tvTitle.text = item.title
            binding.tvSubtitle.text = item.subtitle
            binding.tvBadge.text = item.badge
            binding.tvAmount.text = item.amount
            binding.tvLeading.text = item.title.firstOrNull()?.uppercase() ?: "#"
            binding.tvBadge.isVisible = item.badge.isNotBlank()
            binding.tvAmount.isVisible = item.amount.isNotBlank()
            binding.btnAction.isVisible = !item.actionLabel.isNullOrBlank()
            binding.btnAction.text = item.actionLabel.orEmpty()
            binding.root.setOnClickListener { onItemClick(item) }
            binding.btnAction.setOnClickListener { onActionClick?.invoke(item) }
            val bgRes = when (item.tone) {
                RowTone.GREEN -> R.drawable.bg_tone_green
                RowTone.GOLD -> R.drawable.bg_tone_gold
                RowTone.ORANGE -> R.drawable.bg_tone_orange
                RowTone.BLUE -> R.drawable.bg_tone_blue
                RowTone.DEFAULT -> R.drawable.bg_tone_neutral
            }
            binding.tvLeading.setBackgroundResource(bgRes)
        }
    }
}
