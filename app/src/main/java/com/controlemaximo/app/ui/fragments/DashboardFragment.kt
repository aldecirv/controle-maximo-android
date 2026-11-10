package com.controlemaximo.app.ui.fragments

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.controlemaximo.app.R
import com.controlemaximo.app.data.AlertLevel
import com.controlemaximo.app.data.InventoryDb
import com.controlemaximo.app.data.MovementType
import com.controlemaximo.app.data.formatDate
import com.controlemaximo.app.data.formatDateTime
import com.controlemaximo.app.data.formatQty
import com.controlemaximo.app.databinding.FragmentDashboardBinding
import com.controlemaximo.app.ui.MainActivity
import com.controlemaximo.app.ui.ReportsActivity
import com.controlemaximo.app.ui.ShowcaseActivity
import com.google.android.material.card.MaterialCardView

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: InventoryDb

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        db = InventoryDb(requireContext())
        binding.quickRegister.setOnClickListener { (activity as? MainActivity)?.select(R.id.navRegister) }
        binding.quickMovement.setOnClickListener { (activity as? MainActivity)?.openMovement() }
        binding.quickShowcase.setOnClickListener { startActivity(Intent(requireContext(), ShowcaseActivity::class.java)) }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        if (_binding == null) return
        val settings = db.settings()
        val lots = db.lots()
        val alerts = db.refreshAlerts()
        val active = lots.filter { it.availableQty > 0 && it.status == "ativo" }
        val stock = lots.sumOf { it.availableQty }
        val urgent = alerts.count { it.level == AlertLevel.URGENT }
        val expired = alerts.count { it.level == AlertLevel.EXPIRED }
        binding.storeLine.text = "${settings.storeName}  •  ${formatDate(System.currentTimeMillis())}"
        binding.kpiLots.text = active.size.toString()
        binding.kpiStock.text = formatQty(stock)
        binding.kpiUrgent.text = urgent.toString()
        binding.kpiExpired.text = expired.toString()
        binding.urgentLine.text = when {
            alerts.isEmpty() -> "Nenhum lote em risco. Bom trabalho!"
            else -> "${alerts.size} lote(s) exigem ação: $urgent urgente(s) e $expired vencido(s)."
        }
        binding.alertsContainer.removeAllViews()
        if (alerts.isEmpty()) {
            binding.alertsContainer.addView(emptyText("Nenhuma prioridade no momento."))
        } else {
            alerts.take(3).forEach { alert ->
                binding.alertsContainer.addView(alertCard(alert.lot.id, alert.lot.productName, alert.lot.code, alert.daysRemaining, alert.level))
            }
        }
        binding.lotsContainer.removeAllViews()
        if (active.isEmpty()) binding.lotsContainer.addView(emptyText("Nenhum lote ativo."))
        else active.take(4).forEach { lot ->
            binding.lotsContainer.addView(lotCard(lot.id, lot.productName, lot.code, lot.expiryDate, lot.availableQty, lot.unit, lot.discount))
        }
        binding.movementsContainer.removeAllViews()
        val movements = db.movements(3)
        if (movements.isEmpty()) binding.movementsContainer.addView(emptyText("Nenhuma movimentação registrada."))
        else movements.forEach { m ->
            binding.movementsContainer.addView(movementCard(m.type.label, m.productName, m.quantity, m.unit, m.dateLabel))
        }
        (activity as? MainActivity)?.refreshHeader()
    }

    private fun alertCard(lotId: String, product: String, code: String, days: Long, level: AlertLevel): View {
        val card = baseCard()
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val left = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        left.addView(title(product))
        left.addView(caption("$code  •  ${validityText(days)}"))
        left.addView(badge(level.label, level))
        row.addView(left)
        val action = TextView(requireContext()).apply {
            text = "Tratar"
            setTextColor(color(R.color.primary))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        action.setOnClickListener { (activity as? MainActivity)?.openMovement(lotId) }
        row.addView(action)
        card.addView(row)
        return card
    }

    private fun lotCard(lotId: String, product: String, code: String, expiry: String, qty: Double, unit: String, discount: Double): View {
        val card = baseCard()
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val left = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        left.addView(title(product))
        left.addView(caption("$code  •  ${formatDate(expiry)}  •  ${formatQty(qty)} $unit"))
        row.addView(left)
        row.addView(badge("${discount.toInt()}% off", AlertLevel.ATTENTION))
        card.addView(row)
        card.setOnClickListener { (activity as? MainActivity)?.openMovement(lotId) }
        return card
    }

    private fun movementCard(type: String, product: String, qty: Double, unit: String, date: String): View {
        val card = baseCard()
        card.addView(title(type))
        card.addView(caption("$product  •  ${formatQty(qty)} $unit  •  $date"))
        return card
    }

    private fun baseCard(): MaterialCardView = MaterialCardView(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(7) }
        radius = dp(14).toFloat()
        setCardBackgroundColor(color(R.color.surface))
        strokeWidth = dp(1)
        strokeColor = color(R.color.border)
        cardElevation = 0f
        setContentPadding(dp(11), dp(10), dp(11), dp(10))
    }

    private fun title(text: String) = TextView(requireContext()).apply {
        this.text = text
        setTextColor(color(R.color.text_primary))
        textSize = 12f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private fun caption(text: String) = TextView(requireContext()).apply {
        this.text = text
        setTextColor(color(R.color.text_tertiary))
        textSize = 9.5f
        setPadding(0, dp(2), 0, 0)
    }

    private fun badge(text: String, level: AlertLevel) = TextView(requireContext()).apply {
        this.text = text
        textSize = 8f
        setTextColor(color(when (level) {
            AlertLevel.URGENT -> R.color.danger
            AlertLevel.EXPIRED -> R.color.warning
            AlertLevel.ATTENTION -> R.color.primary
        }))
        setBackgroundResource(when (level) {
            AlertLevel.URGENT -> R.drawable.bg_badge_red
            AlertLevel.EXPIRED -> R.drawable.bg_badge_amber
            AlertLevel.ATTENTION -> R.drawable.bg_badge_blue
        })
        setPadding(dp(6), dp(2), dp(6), dp(2))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) }
    }

    private fun emptyText(text: String) = TextView(requireContext()).apply {
        this.text = text
        setTextColor(color(R.color.text_tertiary))
        textSize = 11f
        setPadding(dp(4), dp(10), dp(4), dp(10))
    }

    private fun validityText(days: Long): String = when {
        days < 0 -> "venceu há ${-days} dia(s)"
        days == 0L -> "vence hoje"
        else -> "vence em $days dia(s)"
    }

    private fun color(id: Int) = ContextCompat.getColor(requireContext(), id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        if (::db.isInitialized) db.close()
        super.onDestroyView()
        _binding = null
    }
}
