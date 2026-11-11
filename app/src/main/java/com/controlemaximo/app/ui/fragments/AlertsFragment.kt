package com.controlemaximo.app.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.controlemaximo.app.R
import com.controlemaximo.app.data.AlertLevel
import com.controlemaximo.app.data.InventoryDb
import com.controlemaximo.app.databinding.FragmentAlertsBinding
import com.controlemaximo.app.ui.MainActivity
import com.controlemaximo.app.ui.ShowcaseActivity
import com.controlemaximo.app.ui.adapters.AlertAdapter

class AlertsFragment : Fragment() {
    private var _binding: FragmentAlertsBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: InventoryDb
    private var filter = "ALL"
    private var alerts = emptyList<com.controlemaximo.app.data.AlertItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAlertsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        db = InventoryDb(requireContext())
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.filterGroup.setOnCheckedStateChangeListener { _, ids ->
            filter = when (ids.firstOrNull()) {
                R.id.filterUrgent -> "URGENTE"
                R.id.filterAttention -> "ATENÇÃO"
                R.id.filterExpired -> "VENCIDO"
                else -> "ALL"
            }
            render()
        }
        binding.refresh.setOnClickListener {
            val count = db.refreshAlerts().size
            Toast.makeText(requireContext(), "$count lote(s) em alerta.", Toast.LENGTH_SHORT).show()
            load()
        }
        binding.showcase.setOnClickListener { startActivity(Intent(requireContext(), ShowcaseActivity::class.java)) }
        binding.emailButton.setOnClickListener { sendEmailSummary() }
        load()
    }

    override fun onResume() { super.onResume(); load() }

    private fun sendEmailSummary() {
        if (alerts.isEmpty()) {
            Toast.makeText(requireContext(), "Não há alertas para enviar.", Toast.LENGTH_SHORT).show()
            return
        }
        val body = buildString {
            appendLine("Relatório de validade • ${db.settings().storeName}")
            appendLine()
            alerts.forEach { a ->
                appendLine("Produto: ${a.lot.productName}")
                appendLine("Lote: ${a.lot.code}")
                appendLine("Validade: ${com.controlemaximo.app.data.formatDate(a.lot.expiryDate)} (${a.daysRemaining} dia(s))")
                appendLine("Quantidade: ${com.controlemaximo.app.data.formatQty(a.lot.freeQty)} ${a.lot.unit}")
                appendLine("Nível: ${a.level.label}")
                appendLine()
            }
            appendLine("Equipe Controle Máximo Carrefour")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_SUBJECT, "Controle Máximo • Alertas de validade")
            putExtra(Intent.EXTRA_TEXT, body)
        }
        runCatching { startActivity(Intent.createChooser(intent, "Enviar resumo por e-mail")) }
            .onFailure { Toast.makeText(requireContext(), "Nenhum app de e-mail disponível.", Toast.LENGTH_SHORT).show() }
    }

    private fun load() {
        alerts = db.alerts()
        render()
    }

    private fun render() {
        if (_binding == null) return
        val filtered = when (filter) {
            "URGENTE" -> alerts.filter { it.level == AlertLevel.URGENT }
            "ATENÇÃO" -> alerts.filter { it.level == AlertLevel.ATTENTION }
            "VENCIDO" -> alerts.filter { it.level == AlertLevel.EXPIRED }
            else -> alerts
        }
        binding.count.text = "${alerts.count { it.level == AlertLevel.URGENT }} urgente(s) • ${alerts.count { it.level == AlertLevel.ATTENTION }} em atenção • ${alerts.count { it.level == AlertLevel.EXPIRED }} vencido(s)"
        binding.list.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        binding.emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.list.adapter = AlertAdapter(
            filtered,
            onMovement = { (activity as? MainActivity)?.openMovement(it.lot.id) },
            onShowcase = { startActivity(Intent(requireContext(), ShowcaseActivity::class.java)) }
        )
        (activity as? MainActivity)?.refreshHeader()
    }

    override fun onDestroyView() {
        if (::db.isInitialized) db.close()
        super.onDestroyView()
        _binding = null
    }
}
