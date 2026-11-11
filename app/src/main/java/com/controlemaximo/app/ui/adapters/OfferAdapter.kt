package com.controlemaximo.app.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.controlemaximo.app.data.PublicOffer
import com.controlemaximo.app.data.formatDate
import com.controlemaximo.app.data.formatMoney
import com.controlemaximo.app.data.formatQty
import com.controlemaximo.app.databinding.ItemOfferBinding

class OfferAdapter(
    private val items: List<PublicOffer>,
    private val onReserve: (PublicOffer) -> Unit
) : RecyclerView.Adapter<OfferAdapter.Holder>() {
    inner class Holder(val binding: ItemOfferBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: PublicOffer) {
            binding.category.text = item.category.uppercase()
            binding.days.text = when {
                item.daysRemaining == 0L -> "vence hoje"
                else -> "vence em ${item.daysRemaining} dia(s)"
            }
            binding.name.text = item.productName
            binding.lot.text = "${item.code}  •  validade ${formatDate(item.expiryDate)}"
            binding.original.text = "De ${formatMoney(item.originalPrice)}"
            binding.price.text = formatMoney(item.finalPrice)
            binding.discount.text = "${item.discountPercent.toInt()}% de desconto"
            binding.stock.text = "${formatQty(item.remainingQty)} ${item.unit} restantes"
            binding.reserve.setOnClickListener { onReserve(item) }
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemOfferBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size
}
