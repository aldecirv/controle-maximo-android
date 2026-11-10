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
        }
        setupExistingProducts()
        setupCategorySuggestions()
        val categories = listOf("Laticínios", "Panificação", "Frios & Laticínios", "Bebidas", "Farmácia", "Confeitaria", "Hortifruti", "Mercearia", "Limpeza", "Outros")
        (binding.categoryInput as android.widget.AutoCompleteTextView).setAdapter(ArrayAdapter(requireContext(), R.layout.item_dropdown, categories))
        binding.manufactureInput.setText(formatDate(manufacture.format(ISO)))
        binding.expiryInput.setText(formatDate(expiry.format(ISO)))
        binding.manufactureInput.setOnClickListener { pickDate(manufacture) { manufacture = it; binding.manufactureInput.setText(formatDate(it.format(ISO))) } }
        binding.expiryInput.setOnClickListener { pickDate(expiry) { expiry = it; binding.expiryInput.setText(formatDate(it.format(ISO))) } }
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

    private fun save() {
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
