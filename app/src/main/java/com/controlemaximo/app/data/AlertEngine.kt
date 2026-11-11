package com.controlemaximo.app.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object AlertEngine {

    fun daysRemaining(expiryIso: String, today: LocalDate = todayInSaoPaulo()): Long =
        runCatching { ChronoUnit.DAYS.between(today, LocalDate.parse(expiryIso, ISO)) }.getOrDefault(0)

    fun level(expiryIso: String, attentionDays: Int = 7, urgentDays: Int = 2, today: LocalDate = todayInSaoPaulo()): AlertLevel? {
        val days = daysRemaining(expiryIso, today)
        return when {
            days < 0 -> AlertLevel.EXPIRED
            days <= urgentDays -> AlertLevel.URGENT
            days <= attentionDays -> AlertLevel.ATTENTION
            else -> null
        }
    }

    fun classify(lots: List<Lot>, attentionDays: Int, urgentDays: Int): List<AlertItem> {
        val today = todayInSaoPaulo()
        return lots.asSequence()
            .filter { it.availableQty > 0 }
            .mapNotNull { lot ->
                val days = daysRemaining(lot.expiryDate, today)
                val lvl = level(lot.expiryDate, attentionDays, urgentDays, today) ?: return@mapNotNull null
                AlertItem(lot, days, lvl, today.format(ISO))
            }
            .sortedWith(compareBy<AlertItem> { it.daysRemaining }.thenBy { it.lot.productName })
            .toList()
    }

    fun discountedPrice(price: Double, discountPercent: Double): Double =
        Math.round(price * (1.0 - discountPercent / 100.0) * 100.0) / 100.0
}
