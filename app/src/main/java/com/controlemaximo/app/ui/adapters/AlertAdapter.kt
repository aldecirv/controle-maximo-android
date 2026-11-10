package com.controlemaximo.app.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.controlemaximo.app.R
import com.controlemaximo.app.data.AlertItem
import com.controlemaximo.app.data.AlertLevel
import com.controlemaximo.app.data.formatDate
import com.controlemaximo.app.data.formatQty
import com.controlemaximo.app.databinding.ItemAlertBinding

class AlertAdapter(
    private val items: List<AlertItem>,
    private val onMovement: (AlertItem) -> Unit,
    private val onShowcase: (AlertItem) -> Unit
) : RecyclerView.Adapter<AlertAdapter.Holder>() {

    inner class Holder(val binding: ItemAlertBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AlertItem) {
            binding.level.text = item.level.label
            val (bg, color) = when (item.level) {
                AlertLevel.URGENT -> R.drawable.bg_badge_red to R.color.danger
                AlertLevel.EXPIRED -> R.drawable.bg_badge_amber to R.color.warning
                AlertLevel.ATTENTION -> R.drawable.bg_badge_blue to R.color.primary
            }
            binding.level.setBackgroundResource(bg)
            binding.level.setTextColor(binding.root.context.getColor(color))
            binding.days.text = when {
                item.daysRemaining < 0 -> "vencido há ${-item.daysRemaining} dia(s)"
                item.daysRemaining == 0L -> "vence hoje"
                else -> "vence em ${item.daysRemaining} dia(s)"
            }
            binding.product.text = item.lot.productName
            binding.details.text = "${item.lot.code}  •  validade ${formatDate(item.lot.expiryDate)}  •  ${item.lot.discount.toInt()}% off"
            binding.stock.text = "${formatQty(item.lot.freeQty)} ${item.lot.unit} livres  •  ${formatQty(item.lot.reservedQty)} reservadas"
            binding.movement.setOnClickListener { onMovement(item) }
            binding.showcase.setOnClickListener { onShowcase(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemAlertBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size
}
