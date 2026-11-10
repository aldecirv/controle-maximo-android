package com.controlemaximo.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.controlemaximo.app.R
import com.controlemaximo.app.data.BR_LOCALE
import com.controlemaximo.app.data.InventoryDb
import com.controlemaximo.app.data.Report
import com.controlemaximo.app.data.formatDate
import com.controlemaximo.app.data.formatMoney
import com.controlemaximo.app.data.formatQty
import com.controlemaximo.app.data.paymentLabel
import com.controlemaximo.app.databinding.ActivityReportsBinding
import com.controlemaximo.app.util.ThemeUtils
import com.google.android.material.datepicker.MaterialDatePicker
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date

class ReportsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReportsBinding
    private lateinit var db: InventoryDb
    private var start: LocalDate = LocalDate.now().withDayOfMonth(1)
    private var end: LocalDate = LocalDate.now()
    private var report: Report? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtils.lightSystemBars(this)
        db = InventoryDb(this)
        binding = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        binding.back.setOnClickListener { finish() }
        binding.startInput.setOnClickListener { pick(start) { start = it; binding.startInput.setText(it.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))); load() } }
        binding.endInput.setOnClickListener { pick(end) { end = it; binding.endInput.setText(it.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))); load() } }
        binding.export.setOnClickListener { export() }
        binding.startInput.setText(start.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")))
        binding.endInput.setText(end.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")))
        load()
    }

    private fun pick(initial: LocalDate, callback: (LocalDate) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Selecionar data")
            .setSelection(initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            callback(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
        }
        picker.show(supportFragmentManager, "date")
    }

    private fun load() {
        if (start.isAfter(end)) {
            Toast.makeText(this, "Data inicial deve ser anterior à final.", Toast.LENGTH_SHORT).show()
            return
        }
        val zone = ZoneId.systemDefault()
        val startMs = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val r = db.report(startMs, endMs)
        report = r
        binding.sold.text = formatMoney(r.soldValue)
        binding.soldQty.text = "${formatQty(r.soldQty)} itens"
        binding.donated.text = formatMoney(r.donatedValue)
        binding.donatedQty.text = "${formatQty(r.donatedQty)} itens"
        binding.discarded.text = formatMoney(r.discardedValue)
        binding.discardedQty.text = "${formatQty(r.discardedQty)} itens"
        binding.utilization.text = String.format(BR_LOCALE, "%.1f%%", r.utilizationPercent)
        binding.recovered.text = "${formatMoney(r.recoveredValue)} recuperados"
        renderCategories(r)
        renderPayments(r)
    }

    private fun renderCategories(r: Report) {
        binding.categoriesContainer.removeAllViews()
        if (r.categories.isEmpty()) {
            binding.categoriesContainer.addView(empty("Nenhuma movimentação no período."))
            return
        }
        val maxValue = r.categories.maxOf { it.soldQty + it.donatedQty + it.discardedQty }.coerceAtLeast(1.0)
        r.categories.forEach { c ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
            val labels = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            labels.addView(TextView(this).apply {
                text = c.category
                setTextColor(color(R.color.text_primary)); textSize = 11f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            labels.addView(TextView(this).apply {
                text = "${formatQty(c.soldQty + c.donatedQty + c.discardedQty)} itens • ${formatMoney(c.soldValue)}"
                setTextColor(color(R.color.text_tertiary)); textSize = 9f
            })
            row.addView(labels)
            row.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                this.max = 100
                progress = (((c.soldQty + c.donatedQty + c.discardedQty) / maxValue) * 100).toInt()
                progressTintList = android.content.res.ColorStateList.valueOf(color(R.color.primary))
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(color(R.color.primary_soft))
            })
            binding.categoriesContainer.addView(row)
        }
    }

    private fun renderPayments(r: Report) {
        binding.paymentsContainer.removeAllViews()
        if (r.payments.isEmpty()) {
            binding.paymentsContainer.addView(empty("Nenhuma venda no período."))
            return
        }
        r.payments.forEach { p ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(5), 0, dp(5)) }
            row.addView(TextView(this).apply { text = paymentLabel(p.method); setTextColor(color(R.color.text_primary)); textSize = 11f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            row.addView(TextView(this).apply { text = "${p.count} venda(s) • ${formatMoney(p.total)}"; setTextColor(color(R.color.text_secondary)); textSize = 10f })
            binding.paymentsContainer.addView(row)
        }
    }

    private fun export() {
        val r = report ?: return
        runCatching {
            val dir = File(cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "movimentacoes_${start}_${end}.csv")
            file.writeText(db.csv(r.start, r.end))
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Exportar CSV"))
        }.onFailure { Toast.makeText(this, "Não foi possível exportar: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    private fun empty(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(color(R.color.text_tertiary))
        textSize = 11f
        setPadding(dp(2), dp(8), dp(2), dp(8))
    }
    private fun color(id: Int) = androidx.core.content.ContextCompat.getColor(this, id)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }
}
