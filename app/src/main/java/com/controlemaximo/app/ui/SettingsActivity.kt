package com.controlemaximo.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.controlemaximo.app.data.InventoryDb
import com.controlemaximo.app.data.Settings
import com.controlemaximo.app.databinding.ActivitySettingsBinding
import com.controlemaximo.app.util.ThemeUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var db: InventoryDb

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtils.lightSystemBars(this)
        db = InventoryDb(this)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        binding.back.setOnClickListener { finish() }
        load()
        binding.saveButton.setOnClickListener { save() }
        binding.resetDemo.setOnClickListener { restoreDemo() }
        binding.wipeData.setOnClickListener { wipeData() }
    }

    private fun load() {
        val s = db.settings()
        binding.storeInput.setText(s.storeName)
        binding.attentionInput.setText(s.attentionDays.toString())
        binding.urgentInput.setText(s.urgentDays.toString())
        binding.showcaseInput.setText(s.showcaseDays.toString())
        binding.discountInput.setText(s.defaultDiscount.toInt().toString())
    }

    private fun save() {
        val s = Settings(
            storeName = binding.storeInput.text?.toString().orEmpty(),
            attentionDays = binding.attentionInput.text?.toString()?.toIntOrNull() ?: 0,
            urgentDays = binding.urgentInput.text?.toString()?.toIntOrNull() ?: 0,
            showcaseDays = binding.showcaseInput.text?.toString()?.toIntOrNull() ?: 0,
            defaultDiscount = binding.discountInput.text?.toString()?.replace(',', '.')?.toDoubleOrNull() ?: -1.0
        )
        runCatching { db.saveSettings(s) }
            .onSuccess { Toast.makeText(this, "Ajustes salvos.", Toast.LENGTH_SHORT).show(); finish() }
            .onFailure { Toast.makeText(this, it.message ?: "Ajustes inválidos.", Toast.LENGTH_LONG).show() }
    }

    private fun restoreDemo() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Restaurar dados de exemplo?")
            .setMessage("Todos os dados atuais serão substituídos pelo conjunto de demonstração.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Restaurar") { _, _ ->
                db.wipeAll()
                db.seedDemo()
                load()
                Toast.makeText(this, "Dados de exemplo restaurados.", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun wipeData() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Apagar todos os dados?")
            .setMessage("Produtos, lotes, movimentações e reservas serão removidos. Não é possível desfazer.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Apagar tudo") { _, _ ->
                db.wipeAll()
                load()
                Toast.makeText(this, "Dados apagados.", Toast.LENGTH_SHORT).show()
            }.show()
    }

    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }
}
