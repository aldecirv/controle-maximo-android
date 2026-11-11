package com.controlemaximo.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.controlemaximo.app.R
import com.controlemaximo.app.data.InventoryDb
import com.controlemaximo.app.data.ISO
import com.controlemaximo.app.data.Product
import com.controlemaximo.app.data.formatDate
import com.controlemaximo.app.databinding.FragmentRegisterBinding
import com.controlemaximo.app.ui.MainActivity
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class RegisterFragment : Fragment() {
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: InventoryDb
    private var modeNew = true
    private var selectedProduct: Product? = null
    private var manufacture: LocalDate = LocalDate.now().minusDays(1)
    private var expiry: LocalDate = LocalDate.now().plusDays(30)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        db = InventoryDb(requireContext())
        binding.modeGroup.setOnCheckedStateChangeListener { _, ids ->
            modeNew = ids.firstOrNull() != R.id.modeExisting
            binding.productFields.visibility = if (modeNew) View.VISIBLE else View.GONE
            binding.existingLayout.visibility = if (modeNew) View.GONE else View.VISIBLE
            if (modeNew) binding.existingLayout.error = null
        }
        setupExistingProducts()
        setupCategorySuggestions()
        val categories = listOf("Laticínios", "Panificação", "Frios & Laticínios", "Bebidas", "Farmácia", "Confeitaria", "Hortifruti", "Mercearia", "Limpeza", "Outros")
        (binding.categoryInput as android.widget.AutoCompleteTextView).setAdapter(ArrayAdapter(requireContext(), R.layout.item_dropdown, categories))
        binding.manufactureInput.setText(formatDate(manufacture.format(ISO)))
        binding.expiryInput.setText(formatDate(expiry.format(ISO)))
        binding.manufactureInput.setOnClickListener { pickDate(manufacture) { manufacture = it; binding.manufactureInput.setText(formatDate(it.format(ISO))); validateDates(show = true) } }
        binding.expiryInput.setOnClickListener { pickDate(expiry) { expiry = it; binding.expiryInput.setText(formatDate(it.format(ISO))); validateDates(show = true) } }
        binding.quantityInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                val value = s?.toString()?.replace(',', '.')?.toDoubleOrNull()
                layoutOf(binding.quantityInput)?.error = if (s.isNullOrBlank()) null else if (value == null || value <= 0) "Quantidade deve ser maior que zero." else null
            }
        })
        binding.lotCodeInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                val code = s?.toString()?.trim().orEmpty()
                layoutOf(binding.lotCodeInput)?.error = when {
                    code.length < 2 -> null
                    db.lotCodeExists(code) -> "Código de lote já cadastrado."
                    else -> null
                }
            }
        })
        binding.discountInput.setText(db.settings().defaultDiscount.toInt().toString())
        binding.saveButton.setOnClickListener { save() }
        (activity as? MainActivity)?.refreshHeader()
    }

    private fun setupExistingProducts() {
        val products = db.products()
        val adapter = ArrayAdapter(requireContext(), R.layout.item_dropdown, products.map { "${it.name} • ${it.category}" })
        (binding.existingProduct as android.widget.AutoCompleteTextView).setAdapter(adapter)
        (binding.existingProduct as android.widget.AutoCompleteTextView).setOnItemClickListener { _, _, position, _ ->
            selectedProduct = products.getOrNull(position)
            binding.existingLayout.error = null
        }
    }

    private fun setupCategorySuggestions() {
        val categories = listOf("Laticínios", "Panificação", "Frios & Laticínios", "Bebidas", "Farmácia", "Confeitaria", "Hortifruti", "Mercearia", "Limpeza", "Outros")
        (binding.categoryInput as? android.widget.AutoCompleteTextView)?.setAdapter(ArrayAdapter(requireContext(), R.layout.item_dropdown, categories))
    }

    private fun pickDate(initial: LocalDate, onPick: (LocalDate) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Selecionar data")
            .setSelection(initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
        }
        picker.show(parentFragmentManager, "date")
    }

    private fun layoutOf(input: android.widget.TextView): TextInputLayout? {
        var parent: android.view.ViewParent? = input.parent
        while (parent != null) {
            if (parent is TextInputLayout) return parent
            parent = parent.parent
        }
        return null
    }

    private fun validateDates(show: Boolean): Boolean {
        val valid = expiry.isAfter(manufacture)
        if (show) layoutOf(binding.expiryInput)?.error = if (valid) null else "A validade deve ser posterior à fabricação."
        return valid
    }

    private fun validateForm(showErrors: Boolean): Boolean {
        var firstInvalid: android.widget.TextView? = null
        var valid = true
        fun fail(input: android.widget.TextView, message: String) {
            valid = false
            if (firstInvalid == null) firstInvalid = input
            if (showErrors) layoutOf(input)?.error = message
        }
        fun clear(input: android.widget.TextView) { if (showErrors) layoutOf(input)?.error = null }
        listOf(binding.nameInput, binding.costInput, binding.priceInput, binding.supplierInput,
            binding.lotCodeInput, binding.quantityInput, binding.discountInput).forEach(::clear)

        if (!modeNew) {
            if (selectedProduct == null) {
                valid = false
                if (showErrors) binding.existingLayout.error = "Selecione um produto existente."
            } else if (showErrors) binding.existingLayout.error = null
        } else {
            val name = binding.nameInput.text?.toString().orEmpty().trim()
            val category = binding.categoryInput.text?.toString().orEmpty().trim()
            val unit = binding.unitInput.text?.toString().orEmpty().trim()
            val cost = binding.costInput.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
            val price = binding.priceInput.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
            val supplier = binding.supplierInput.text?.toString().orEmpty().trim()
            if (name.length < 2) fail(binding.nameInput, "Informe o nome do produto.")
            if (category.length < 2) fail(binding.categoryInput, "Informe a categoria.")
            if (unit.isEmpty()) fail(binding.unitInput, "Informe a unidade.")
            if (cost == null || cost < 0) fail(binding.costInput, "Informe um custo válido.")
            if (price == null || price <= 0) fail(binding.priceInput, "Preço deve ser maior que zero.")
            if (supplier.length < 2) fail(binding.supplierInput, "Informe o fornecedor ou Produção Própria.")
        }
        val code = binding.lotCodeInput.text?.toString().orEmpty().trim()
        if (code.length < 2) fail(binding.lotCodeInput, "Código do lote é obrigatório.")
        else if (db.lotCodeExists(code)) fail(binding.lotCodeInput, "Código de lote já cadastrado.")
        val quantity = binding.quantityInput.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
        if (quantity == null || quantity <= 0) fail(binding.quantityInput, "Quantidade deve ser maior que zero.")
        val discount = binding.discountInput.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
        if (discount == null || discount !in 0.0..100.0) fail(binding.discountInput, "Desconto deve estar entre 0 e 100%.")
        if (!validateDates(showErrors)) {
            valid = false
            if (firstInvalid == null) firstInvalid = binding.expiryInput
        }
        if (!valid && showErrors) firstInvalid?.requestFocus()
        return valid
    }

    private fun save() {
        if (!validateForm(showErrors = true)) return
        val quantity = binding.quantityInput.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
        val discount = binding.discountInput.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
        try {
            val lotCode = binding.lotCodeInput.text?.toString().orEmpty()
            if (modeNew) {
                val cost = binding.costInput.text?.toString()?.replace(',', '.')?.toDoubleOrNull() ?: Double.NaN
                val price = binding.priceInput.text?.toString()?.replace(',', '.')?.toDoubleOrNull() ?: Double.NaN
                if (cost.isNaN() || price.isNaN()) throw IllegalArgumentException("Informe custo e preço de venda.")
                db.addProductAndLot(
                    name = binding.nameInput.text?.toString().orEmpty(),
                    category = binding.categoryInput.text?.toString().orEmpty(),
                    unit = binding.unitInput.text?.toString().orEmpty(),
                    cost = cost,
                    price = price,
                    supplier = binding.supplierInput.text?.toString().orEmpty(),
                    lotCode = lotCode,
                    manufactureDate = manufacture.format(ISO),
                    expiryDate = expiry.format(ISO),
                    initialQty = quantity ?: throw IllegalArgumentException("Informe a quantidade inicial."),
                    discount = discount ?: 30.0
                )
            } else {
                val product = selectedProduct ?: throw IllegalArgumentException("Selecione um produto existente.")
                db.addLotToProduct(
                    productId = product.id, lotCode = lotCode, manufactureDate = manufacture.format(ISO),
                    expiryDate = expiry.format(ISO), initialQty = quantity ?: throw IllegalArgumentException("Informe a quantidade inicial."),
                    discount = discount ?: 30.0
                )
            }
            Toast.makeText(requireContext(), "Lote cadastrado com sucesso.", Toast.LENGTH_SHORT).show()
            clearForm()
            setupExistingProducts()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), e.message ?: "Não foi possível cadastrar.", Toast.LENGTH_LONG).show()
        }
    }

    private fun clearForm() {
        binding.nameInput.setText("")
        binding.categoryInput.setText("")
        binding.unitInput.setText("un")
        binding.costInput.setText("")
        binding.priceInput.setText("")
        binding.supplierInput.setText("")
        binding.lotCodeInput.setText("")
        binding.quantityInput.setText("")
        binding.discountInput.setText(db.settings().defaultDiscount.toInt().toString())
        binding.existingProduct.setText("")
        selectedProduct = null
    }

    override fun onDestroyView() {
        if (::db.isInitialized) db.close()
        super.onDestroyView()
        _binding = null
    }
}
