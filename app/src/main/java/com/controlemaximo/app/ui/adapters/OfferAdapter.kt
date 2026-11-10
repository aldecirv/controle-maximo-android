package com.controlemaximo.app.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.controlemaximo.app.data.Offer
import com.controlemaximo.app.data.formatDate
import com.controlemaximo.app.data.formatMoney
import com.controlemaximo.app.data.formatQty
import com.controlemaximo.app.databinding.ItemOfferBinding

class OfferAdapter(
    private val items: List<Offer>,
    private val onReserve: (Offer) -> Unit
) : RecyclerView.Adapter<OfferAdapter.Holder>() {
    inner class Holder(val binding: ItemOfferBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Offer) {
            binding.category.text = item.lot.category.uppercase()
            binding.days.text = when {
                item.daysRemaining == 0L -> "vence hoje"
                else -> "vence em ${item.daysRemaining} dia(s)"
            }
            binding.name.text = item.lot.productName
            binding.lot.text = "${item.lot.code}  •  validade ${formatDate(item.lot.expiryDate)}"
            binding.original.text = "De ${formatMoney(item.lot.price)}"
            binding.price.text = formatMoney(item.discountedPrice)
            binding.discount.text = "${item.lot.discount.toInt()}% de desconto"
            binding.stock.text = "${formatQty(item.remainingQty)} ${item.lot.unit} restantes"
            binding.reserve.setOnClickListener { onReserve(item) }
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemOfferBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size
}
