package com.controlemaximo.app.data

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

val BR_LOCALE: Locale = Locale("pt", "BR")
val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

data class Product(
    val id: String,
    val name: String,
    val category: String,
    val unit: String,
    val cost: Double,
    val price: Double,
    val supplier: String
)

data class Lot(
    val id: String,
    val productId: String,
    val code: String,
    val manufactureDate: String,
    val expiryDate: String,
    val initialQty: Double,
    val availableQty: Double,
    val reservedQty: Double,
    val discount: Double,
    val status: String,
    val productName: String,
    val category: String,
    val unit: String,
    val cost: Double,
    val price: Double,
    val supplier: String
) {
    val freeQty: Double get() = (availableQty - reservedQty).coerceAtLeast(0.0)
    val isExpired: Boolean get() = status == "vencido" || runCatching { LocalDate.parse(expiryDate, ISO).isBefore(LocalDate.now()) }.getOrDefault(false)
}

enum class AlertLevel(val label: String) {
    ATTENTION("ATENÇÃO"),
    URGENT("URGENTE"),
    EXPIRED("VENCIDO")
}

data class AlertItem(
    val lot: Lot,
    val daysRemaining: Long,
    val level: AlertLevel,
    val date: String
)

enum class MovementType(val db: String, val label: String) {
    SALE("venda", "Venda"),
    DONATION("doacao", "Doação"),
    DISPOSAL("descarte", "Descarte"),
    ADJUSTMENT("ajuste", "Ajuste");

    companion object {
        fun fromDb(value: String): MovementType = entries.firstOrNull { it.db == value } ?: ADJUSTMENT
    }
}

data class Movement(
    val id: String,
    val lotId: String,
    val type: MovementType,
    val quantity: Double,
    val valueReceived: Double?,
    val paymentMethod: String?,
    val institutionId: String?,
    val institutionName: String?,
    val reason: String?,
    val note: String?,
    val createdAt: Long,
    val lotCode: String,
    val productName: String,
    val unit: String
) {
    val dateLabel: String get() = formatDateTime(createdAt)
}

data class DonationInstitution(
    val id: String,
    val name: String,
    val contact: String? = null,
    val phone: String? = null
)

data class Reservation(
    val id: String,
    val code: String,
    val lotId: String,
    val quantity: Double,
    val customerName: String,
    val customerPhone: String,
    val createdAt: Long,
    val expiresAt: Long,
    val status: String,
    val productName: String,
    val unit: String,
    val unitPrice: Double
) {
    val isExpired: Boolean get() = status == "expirada" || expiresAt <= System.currentTimeMillis()
    val minutesRemaining: Long get() = ((expiresAt - System.currentTimeMillis() + 59_999L) / 60_000L).coerceAtLeast(0L)
}

data class Settings(
    val storeName: String = "Mercado Controle Máximo",
    val attentionDays: Int = 7,
    val urgentDays: Int = 2,
    val showcaseDays: Int = 5,
    val defaultDiscount: Double = 30.0
)

data class Offer(
    val lot: Lot,
    val daysRemaining: Long,
    val discountedPrice: Double,
    val remainingQty: Double
)

data class CategoryReport(
    val category: String,
    val soldQty: Double,
    val donatedQty: Double,
    val discardedQty: Double,
    val soldValue: Double
)

data class PaymentReport(
    val method: String,
    val count: Int,
    val total: Double
)

data class Report(
    val start: Long,
    val end: Long,
    val soldQty: Double = 0.0,
    val soldValue: Double = 0.0,
    val donatedQty: Double = 0.0,
    val donatedValue: Double = 0.0,
    val discardedQty: Double = 0.0,
    val discardedValue: Double = 0.0,
    val categories: List<CategoryReport> = emptyList(),
    val payments: List<PaymentReport> = emptyList()
) {
    val processedQty: Double get() = soldQty + donatedQty + discardedQty
    val recoveredValue: Double get() = soldValue + donatedValue
    val utilizationPercent: Double get() = if (processedQty <= 0) 0.0 else ((soldQty + donatedQty) / processedQty) * 100.0
}

fun formatMoney(value: Double): String =
    NumberFormat.getCurrencyInstance(BR_LOCALE).format(value)

fun formatQty(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format(BR_LOCALE, "%.3f", value).trimEnd('0').trimEnd(',')

fun formatDateTime(epoch: Long): String =
    SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", BR_LOCALE).format(Date(epoch))

fun formatDate(epoch: Long): String =
    SimpleDateFormat("dd/MM/yyyy", BR_LOCALE).format(Date(epoch))

fun formatDate(iso: String): String = runCatching {
    LocalDate.parse(iso, ISO).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}.getOrDefault(iso)

fun todayIso(): String = LocalDate.now().format(ISO)

fun paymentLabel(value: String?): String = when (value) {
    "dinheiro" -> "Dinheiro"
    "pix" -> "Pix"
    "cartao_credito" -> "Cartão de crédito"
    "cartao_debito" -> "Cartão de débito"
    "outro" -> "Outro"
    else -> "Não informado"
}
