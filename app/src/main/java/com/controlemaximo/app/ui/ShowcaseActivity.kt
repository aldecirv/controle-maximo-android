package com.controlemaximo.app.ui

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.controlemaximo.app.data.InventoryDb
import com.controlemaximo.app.data.PublicOffer
import com.controlemaximo.app.data.formatDateTime
import com.controlemaximo.app.data.formatMoney
import com.controlemaximo.app.data.formatQty
import com.controlemaximo.app.databinding.ActivityShowcaseBinding
import com.controlemaximo.app.ui.adapters.OfferAdapter
import com.controlemaximo.app.util.ThemeUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ShowcaseActivity : AppCompatActivity() {
    private lateinit var binding: ActivityShowcaseBinding
    private lateinit var db: InventoryDb
    private var offers: List<PublicOffer> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtils.lightSystemBars(this)
        db = InventoryDb(this)
        binding = ActivityShowcaseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        binding.back.setOnClickListener { finish() }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.reservationsButton.setOnClickListener { showReservations() }
        binding.queryButton.setOnClickListener { queryReservation() }
        load()
    }

    private fun load() {
        db.expireReservations()
        offers = db.offers()
        binding.offerCount.text = "${offers.size} oferta(s) ativa(s)"
        binding.list.visibility = if (offers.isEmpty()) View.GONE else View.VISIBLE
        binding.emptyState.visibility = if (offers.isEmpty()) View.VISIBLE else View.GONE
        binding.list.adapter = OfferAdapter(offers) { reserve(it) }
    }

    private fun reserve(offer: PublicOffer) {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(0)) }
        val qty = EditText(this).apply { hint = "Quantidade (máx. 50)"; inputType = InputType.TYPE_CLASS_NUMBER; setText("1") }
        val name = EditText(this).apply { hint = "Nome completo"; inputType = InputType.TYPE_CLASS_TEXT }
        val phone = EditText(this).apply { hint = "Telefone com DDD"; inputType = InputType.TYPE_CLASS_PHONE }
        container.addView(qty); container.addView(name); container.addView(phone)
        MaterialAlertDialogBuilder(this)
            .setTitle("Reservar ${offer.productName}")
            .setMessage("${formatQty(offer.remainingQty)} ${offer.unit} restantes • ${formatMoney(offer.finalPrice)} cada\nA reserva fica ativa por 2 horas.")
            .setView(container)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Reservar") { _, _ ->
                runCatching {
                    db.createReservation(
                        offer.id,
                        qty.text.toString().toDoubleOrNull() ?: 0.0,
                        name.text.toString(),
                        phone.text.toString()
                    )
                }.onSuccess { reservation ->
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Reserva confirmada")
                        .setMessage(
                            "Código: ${reservation.code}\n\n${reservation.productName}\n" +
                                "${formatQty(reservation.quantity)} ${reservation.unit}\n" +
                                "Total: ${formatMoney(reservation.unitPrice * reservation.quantity)}\n\n" +
                                "Retire até ${formatDateTime(reservation.expiresAt)}.\nApresente o código no caixa."
                        )
                        .setPositiveButton("OK", null)
                        .show()
                    load()
                }.onFailure { Toast.makeText(this, it.message ?: "Não foi possível reservar.", Toast.LENGTH_LONG).show() }
            }
            .show()
    }

    private fun queryReservation() {
        val code = binding.codeInput.text?.toString().orEmpty()
        if (code.isBlank()) return Toast.makeText(this, "Informe o código da reserva.", Toast.LENGTH_SHORT).show()
        db.expireReservations()
        val reservation = db.queryReservation(code)
        if (reservation == null) {
            MaterialAlertDialogBuilder(this).setTitle("Reserva não encontrada").setMessage("Confira o código informado.").setPositiveButton("OK", null).show()
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle("Reserva ${reservation.code}")
                .setMessage(
                    "${reservation.productName}\n${formatQty(reservation.quantity)} ${reservation.unit}\n" +
                        "Status: ${reservation.status}\n" +
                        (if (reservation.status == "ativa") "Restam ${reservation.minutesRemaining} minuto(s).\n" else "Esta reserva não está ativa.\n") +
                        "Total: ${formatMoney(reservation.unitPrice * reservation.quantity)}"
                )
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun showReservations() {
        db.expireReservations()
        val all = db.reservations().take(10)
        val text = if (all.isEmpty()) "Nenhuma reserva registrada." else all.joinToString("\n\n") {
            "${it.code} • ${it.customerName}\n${it.productName} • ${formatQty(it.quantity)} ${it.unit}\nStatus: ${it.status} • ${formatMoney(it.unitPrice * it.quantity)}"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Reservas")
            .setMessage(text)
            .setPositiveButton("Fechar", null)
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }
}
