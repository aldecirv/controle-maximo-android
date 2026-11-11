package com.controlemaximo.app.ui.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.controlemaximo.app.R
import com.controlemaximo.app.data.AlertEngine
import com.controlemaximo.app.data.InventoryDb
import com.controlemaximo.app.data.Lot
import com.controlemaximo.app.data.MovementType
import com.controlemaximo.app.data.formatDate
import com.controlemaximo.app.data.formatDateTime
import com.controlemaximo.app.data.formatMoney
import com.controlemaximo.app.data.formatQty
import com.controlemaximo.app.data.paymentLabel
import com.controlemaximo.app.databinding.FragmentMovementsBinding
import com.controlemaximo.app.ui.MainActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView

class MovementsFragment : Fragment() {
    private var _binding: FragmentMovementsBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: InventoryDb
    private var lots = emptyList<Lot>()
    private var selectedLot: Lot? = null
    private var type = MovementType.SALE

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMovementsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        db = InventoryDb(requireContext())
        setupPayments()
        binding.typeGroup.setOnCheckedStateChangeListener { _, ids ->
            type = when (ids.firstOrNull()) {
                R.id.typeDonation -> MovementType.DONATION
                R.id.typeDisposal -> MovementType.DISPOSAL
                R.id.typeAdjustment -> MovementType.ADJUSTMENT
                else -> MovementType.SALE
            }
            binding.saleFields.visibility = if (type == MovementType.SALE) View.VISIBLE else View.GONE
            binding.donationFields.visibility = if (type == MovementType.DONATION) View.VISIBLE else View.GONE
            if (type != MovementType.DONATION) {
                selectedAttachment = null
                binding.attachmentLabel.visibility = View.GONE
            }
            binding.adjustmentFields.visibility = if (type == MovementType.ADJUSTMENT) View.VISIBLE else View.GONE
            binding.reasonInput.hint = if (type == MovementType.DISPOSAL) "Motivo do descarte (obrigatório)" else "Motivo (opcional)"
            updatePreview()
        }
        binding.quantityInput.addTextChangedListener(simpleWatcher { updatePreview() })
        binding.valueInput.addTextChangedListener(simpleWatcher { updatePreview() })
        binding.confirmButton.setOnClickListener { confirm() }
        binding.addInstitution.setOnClickListener { addInstitution() }
        binding.attachPdf.setOnClickListener { pdfPicker.launch(arrayOf("application/pdf")) }
        load()
        arguments?.getString(ARG_LOT)?.let { id -> selectLot(id) }
    }

    override fun onResume() { super.onResume(); load(keepSelection = true) }

    private fun load(keepSelection: Boolean = false) {
        val currentId = if (keepSelection) selectedLot?.id else null
        db.expireReservations()
        lots = db.lots().filter { it.availableQty > 0 }
        val adapter = ArrayAdapter(requireContext(), R.layout.item_dropdown, lots.map { "${it.productName}  •  ${it.code}" })
        (binding.lotSelector as MaterialAutoCompleteTextView).setAdapter(adapter)
        (binding.lotSelector as MaterialAutoCompleteTextView).setOnItemClickListener { _, _, position, _ ->
            selectedLot = lots.getOrNull(position)
            renderLot()
        }
        setupInstitutions()
        setupAdjustmentReferences()
        currentId?.let { selectLot(it) } ?: renderLot()
        renderMovements()
        (activity as? MainActivity)?.refreshHeader()
    }

    private fun selectLot(id: String) {
        selectedLot = lots.firstOrNull { it.id == id }
        selectedLot?.let { (binding.lotSelector as MaterialAutoCompleteTextView).setText("${it.productName}  •  ${it.code}", false) }
        renderLot()
    }

    private fun renderLot() {
        val lot = selectedLot
        if (lot == null) {
            binding.lotTitle.text = "Selecione um lote"
            binding.lotDetails.text = ""
            binding.lotStock.text = ""
            binding.preview.text = "Selecione um lote para ver o saldo."
            return
        }
        binding.lotTitle.text = lot.productName
        binding.lotDetails.text = "${lot.code}  •  validade ${formatDate(lot.expiryDate)}  •  ${lot.discount.toInt()}% off"
        binding.lotStock.text = "Disponível: ${formatQty(lot.availableQty)} ${lot.unit}  •  Livre: ${formatQty(lot.freeQty)} ${lot.unit}  •  Reservado: ${formatQty(lot.reservedQty)}"
        updatePreview()
    }

    private fun updatePreview() {
        val lot = selectedLot
        val qty = binding.quantityInput.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
        if (lot == null || qty == null || qty <= 0) {
            binding.preview.text = "Informe quantidade para ver o saldo após a baixa."
            return
        }
        val after = (lot.availableQty - qty).coerceAtLeast(0.0)
        val value = binding.valueInput.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
        val estimated = if (type == MovementType.SALE) (value ?: (qty * AlertEngine.discountedPrice(lot.price, lot.discount))) else 0.0
        binding.preview.text = "Após a baixa: ${formatQty(after)} ${lot.unit} disponíveis." +
            if (type == MovementType.SALE) "  •  Receita: ${formatMoney(estimated)}" else ""
    }

    private fun confirm() {
        val lot = selectedLot ?: return toast("Selecione um lote.")
        val qty = binding.quantityInput.text?.toString()?.replace(',', '.')?.toDoubleOrNull() ?: return toast("Informe a quantidade.")
        val value = binding.valueInput.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
        val payment = paymentValue(binding.paymentInput.text?.toString())
        val institution = selectedInstitution?.id
        val reason = binding.reasonInput.text?.toString()?.takeIf { it.isNotBlank() }
        val note = binding.noteInput.text?.toString()?.takeIf { it.isNotBlank() }
        if (type == MovementType.DISPOSAL && reason == null) {
            layoutOf(binding.reasonInput)?.error = "Informe o motivo do descarte (vencido, avariado ou outro)."
            binding.reasonInput.requestFocus()
            return
        }
        layoutOf(binding.reasonInput)?.error = null
        try {
            val afterMessage = db.simulateMovement(lot.id, type, qty)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Confirmar ${type.label.lowercase()}?")
                .setMessage("${lot.productName}\nLote ${lot.code}\nQuantidade: ${formatQty(qty)} ${lot.unit}\n\n$afterMessage\n\nEssa movimentação é imutável e entrará nos relatórios.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Confirmar") { _, _ ->
                    runCatching {
                        db.registerMovement(
                            lot.id, type, qty,
                            if (type == MovementType.SALE) value else null,
                            if (type == MovementType.SALE) payment else null,
                            if (type == MovementType.DONATION) institution else null,
                            reason, note,
                            if (type == MovementType.ADJUSTMENT) selectedReference?.id else null,
                            if (type == MovementType.DONATION) selectedAttachment else null
                        )
                    }.onSuccess {
                        Toast.makeText(requireContext(), "Baixa registrada com sucesso.", Toast.LENGTH_SHORT).show()
                        binding.quantityInput.setText("")
                        binding.valueInput.setText("")
                        binding.reasonInput.setText("")
                        binding.noteInput.setText("")
                        load()
                    }.onFailure { toast(it.message ?: "Falha ao registrar.") }
                }
                .show()
        } catch (e: Exception) {
            toast(e.message ?: "Baixa inválida.")
        }
    }

    private var selectedInstitution: com.controlemaximo.app.data.DonationInstitution? = null
    private var selectedReference: com.controlemaximo.app.data.Movement? = null
    private var selectedAttachment: String? = null

    private val pdfPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            val resolver = requireContext().contentResolver
            var name = "termo.pdf"
            var size = 0L
            resolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (nameIndex >= 0) name = c.getString(nameIndex) ?: name
                    if (sizeIndex >= 0) size = c.getLong(sizeIndex)
                }
            }
            require(size in 1..5L * 1024 * 1024) { "O PDF deve ter no máximo 5 MB." }
            val dir = java.io.File(requireContext().filesDir, "termos").apply { mkdirs() }
            val target = java.io.File(dir, "${System.currentTimeMillis()}-$name")
            resolver.openInputStream(uri)?.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                ?: throw IllegalArgumentException("Não foi possível ler o PDF.")
            selectedAttachment = target.name
            binding.attachmentLabel.visibility = View.VISIBLE
            binding.attachmentLabel.text = "Anexado: $name"
        }.onFailure { toast(it.message ?: "Falha ao anexar PDF.") }
    }

    private fun setupInstitutions() {
        val inst = db.institutions()
        val adapter = ArrayAdapter(requireContext(), R.layout.item_dropdown, inst.map { it.name })
        (binding.institutionInput as MaterialAutoCompleteTextView).setAdapter(adapter)
        (binding.institutionInput as MaterialAutoCompleteTextView).setOnItemClickListener { _, _, position, _ ->
            selectedInstitution = inst.getOrNull(position)
        }
    }

    private fun setupAdjustmentReferences() {
        val refs = db.movements(20)
        val labels = refs.map { "${it.type.label} • ${it.productName} • ${it.dateLabel}" }
        val adapter = ArrayAdapter(requireContext(), R.layout.item_dropdown, labels)
        (binding.referenceInput as MaterialAutoCompleteTextView).setAdapter(adapter)
        (binding.referenceInput as MaterialAutoCompleteTextView).setOnItemClickListener { _, _, position, _ ->
            selectedReference = refs.getOrNull(position)
        }
    }

    private fun setupPayments() {
        val options = listOf("Dinheiro" to "dinheiro", "Pix" to "pix", "Cartão de crédito" to "cartao_credito", "Cartão de débito" to "cartao_debito", "Outro" to "outro")
        val adapter = ArrayAdapter(requireContext(), R.layout.item_dropdown, options.map { it.first })
        (binding.paymentInput as MaterialAutoCompleteTextView).setAdapter(adapter)

        (binding.paymentInput as MaterialAutoCompleteTextView).setOnItemClickListener { _, _, position, _ ->
            binding.paymentInput.setText(options[position].first, false)
        }
    }

    private fun paymentValue(label: String?): String? = when (label) {
        "Dinheiro" -> "dinheiro"
        "Pix" -> "pix"
        "Cartão de crédito" -> "cartao_credito"
        "Cartão de débito" -> "cartao_debito"
        "Outro" -> "outro"
        else -> null
    }

    private fun addInstitution() {
        val container = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(0)) }
        val name = android.widget.EditText(requireContext()).apply { hint = "Nome da instituição" }
        val contact = android.widget.EditText(requireContext()).apply { hint = "Contato (opcional)" }
        val phone = android.widget.EditText(requireContext()).apply { hint = "Telefone (opcional)" }
        container.addView(name); container.addView(contact); container.addView(phone)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Nova instituição")
            .setView(container)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                runCatching { db.addInstitution(name.text.toString(), contact.text.toString().ifBlank { null }, phone.text.toString().ifBlank { null }) }
                    .onSuccess { setupInstitutions(); toast("Instituição cadastrada.") }
                    .onFailure { toast(it.message ?: "Falha ao salvar.") }
            }
            .show()
    }

    private fun renderMovements() {
        binding.movementsContainer.removeAllViews()
        val movements = db.movements(8)
        if (movements.isEmpty()) {
            binding.movementsContainer.addView(TextView(requireContext()).apply {
                text = "Nenhuma movimentação registrada."
                setTextColor(color(R.color.text_tertiary))
                textSize = 11f
                setPadding(dp(4), dp(8), dp(4), dp(8))
            })
            return
        }
        movements.forEach { m ->
            val card = MaterialCardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(7) }
                radius = dp(14).toFloat(); setCardBackgroundColor(color(R.color.surface)); strokeWidth = dp(1); strokeColor = color(R.color.border); cardElevation = 0f
            }
            val content = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(11), dp(9), dp(11), dp(9)) }
            content.addView(TextView(requireContext()).apply {
                text = "${m.type.label}  •  ${m.productName}"
                setTextColor(color(R.color.text_primary)); textSize = 11.5f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
            content.addView(TextView(requireContext()).apply {
                text = "${formatQty(m.quantity)} ${m.unit}  •  ${m.dateLabel}" + if (m.valueReceived != null) "  •  ${formatMoney(m.valueReceived)}" else ""
                setTextColor(color(R.color.text_tertiary)); textSize = 9.5f
            })
            card.addView(content)
            binding.movementsContainer.addView(card)
        }
    }

    private fun layoutOf(input: android.widget.TextView): com.google.android.material.textfield.TextInputLayout? {
        var parent: android.view.ViewParent? = input.parent
        while (parent != null) {
            if (parent is com.google.android.material.textfield.TextInputLayout) return parent
            parent = parent.parent
        }
        return null
    }

    private fun simpleWatcher(block: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = block()
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    private fun color(id: Int) = ContextCompat.getColor(requireContext(), id)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        if (::db.isInitialized) db.close()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_LOT = "lot_id"
        fun newInstance(lotId: String?): MovementsFragment = MovementsFragment().apply {
            arguments = Bundle().apply { if (lotId != null) putString(ARG_LOT, lotId) }
        }
    }
}
