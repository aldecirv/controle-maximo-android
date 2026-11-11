package com.controlemaximo.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.controlemaximo.app.data.AlertEngine
import com.controlemaximo.app.data.AlertLevel
import com.controlemaximo.app.data.InventoryDb
import com.controlemaximo.app.data.MovementType
import com.controlemaximo.app.data.todayIso
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BusinessRulesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: InventoryDb

    @Before fun setup() {
        context.deleteDatabase("controle_maximo.db")
        db = InventoryDb(context)
    }

    @After fun tearDown() {
        db.close()
        context.deleteDatabase("controle_maximo.db")
    }

    @Test fun alertBoundaries() {
        val today = LocalDate.of(2026, 1, 10)
        assertEquals(AlertLevel.ATTENTION, AlertEngine.level("2026-01-17", 7, 2, today))
        assertEquals(AlertLevel.URGENT, AlertEngine.level("2026-01-12", 7, 2, today))
        assertEquals(AlertLevel.EXPIRED, AlertEngine.level("2026-01-09", 7, 2, today))
        assertNull(AlertEngine.level("2026-01-18", 7, 2, today))
    }

    @Test fun blockedAboveFreeQuantityAndReservations() {
        val lot = db.lots().first { it.id == "l1" }
        assertEquals(16.0, lot.freeQty, 0.001)
        assertThrows(IllegalArgumentException::class.java) {
            db.registerMovement(lot.id, MovementType.SALE, 17.0, 10.0, "pix", null, null, null)
        }
        db.createReservation(lot.id, 2.0, "Cliente Teste", "11999998888")
        val reserved = db.lot(lot.id)!!
        assertEquals(4.0, reserved.reservedQty, 0.001)
        assertThrows(IllegalArgumentException::class.java) {
            db.registerMovement(lot.id, MovementType.SALE, 15.0, 10.0, "pix", null, null, null)
        }
    }

    @Test fun saleReducesAvailableAndAppearsInReport() {
        val lot = db.lots().first { it.id == "l3" }
        val before = lot.availableQty
        db.registerMovement(lot.id, MovementType.SALE, 2.0, 20.0, "pix", null, "venda teste", null)
        assertEquals(before - 2.0, db.lot(lot.id)!!.availableQty, 0.001)
        val report = db.report(System.currentTimeMillis() - 86_400_000L, System.currentTimeMillis() + 1000L)
        assertTrue(report.soldQty >= 2.0)
        assertTrue(report.soldValue >= 20.0)
    }

    @Test fun donationAndDisposalAreAppendOnly() {
        val before = db.movements().size
        val lot = db.lots().first { it.id == "l4" }
        db.registerMovement(lot.id, MovementType.DONATION, 1.0, null, null, null, "doação", null)
        db.registerMovement(lot.id, MovementType.DISPOSAL, 1.0, null, null, null, "vencido", null)
        assertEquals(before + 2, db.movements().size)
        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            db.writableDatabase.execSQL("UPDATE movimentacoes SET quantidade=0")
        }
    }

    @Test fun expiredReservationReturnsToFreeStock() {
        val lot = db.lots().first { it.id == "l3" }
        val freeBefore = lot.freeQty
        val r = db.createReservation(lot.id, 3.0, "Cliente Expira", "11977776666")
        assertEquals(freeBefore - 3.0, db.lot(lot.id)!!.freeQty, 0.001)
        db.writableDatabase.execSQL("UPDATE reservas SET expira_em=? WHERE id=?", arrayOf(System.currentTimeMillis() - 1000L, r.id))
        assertEquals(1, db.expireReservations())
        assertEquals("expirada", db.queryReservation(r.code)?.status)
        assertEquals(freeBefore, db.lot(lot.id)!!.freeQty, 0.001)
    }

    @Test fun reservationTtlAndCode() {
        val lot = db.lots().first { it.id == "l2" }
        val res = db.createReservation(lot.id, 2.0, "Maria Teste", "11988887777")
        assertTrue(res.code.startsWith("CMC-"))
        assertTrue(res.expiresAt > System.currentTimeMillis())
        assertEquals("ativa", db.queryReservation(res.code)?.status)
    }

    @Test fun dateValidationAndSettings() {
        assertThrows(IllegalArgumentException::class.java) {
            db.addProductAndLot("Teste", "Teste", "un", 1.0, 2.0, "Forn", "LT-X", "2026-01-10", "2026-01-09", 1.0, 10.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            db.saveSettings(db.settings().copy(attentionDays = 2, urgentDays = 5))
        }
    }

    @Test fun demoDataIsSeeded() {
        assertTrue(db.products().isNotEmpty())
        assertTrue(db.lots().isNotEmpty())
        assertTrue(db.movements().isNotEmpty())
        assertTrue(db.alerts().isNotEmpty())
    }

    @Test fun lotCodeIsUniqueAndCaseInsensitive() {
        val p = db.products().first()
        db.addLotToProduct(p.id, "LT-UNICO-01", "2026-01-01", "2026-02-01", 1.0, 10.0)
        assertThrows(IllegalArgumentException::class.java) {
            db.addLotToProduct(p.id, "lt-unico-01", "2026-01-01", "2026-02-01", 1.0, 10.0)
        }
    }

    @Test fun disposalRequiresReason() {
        val lot = db.lots().first { it.id == "l4" }
        assertThrows(IllegalArgumentException::class.java) {
            db.registerMovement(lot.id, MovementType.DISPOSAL, 1.0, null, null, null, null, null)
        }
        db.registerMovement(lot.id, MovementType.DISPOSAL, 1.0, null, null, null, "avariado", null)
    }

    @Test fun databaseRejectsInvalidLotesAndNegativeMovement() {
        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            db.writableDatabase.execSQL("INSERT INTO lotes (id,produto_id,codigo_lote,data_fabricacao,data_validade,quantidade_inicial,quantidade_disponivel,quantidade_reservada,desconto_percentual,status,criado_em) VALUES ('bad','p1','BAD','2026-02-01','2026-01-01',10,10,0,0,'ativo',0)")
        }
        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            db.writableDatabase.execSQL("INSERT INTO movimentacoes (id,lote_id,tipo,quantidade,criado_em) VALUES ('badmov','l1','venda',0,0)")
        }
    }

    @Test fun saoPauloDateHandlesUtcMidnight() {
        val instant = java.time.Instant.parse("2026-01-10T02:00:00Z") // 23h de 09/01 em São Paulo
        assertEquals(java.time.LocalDate.of(2026, 1, 9), com.controlemaximo.app.data.todayInSaoPaulo(instant))
    }

    @Test fun reportUsesRealMovementsAndCsvHasBom() {
        db.wipeAll()
        val lot = db.addProductAndLot(
            "Iogurte Teste", "Laticínios", "un", 5.0, 10.0, "Fornecedor Teste",
            "LT-REL-01", "2026-01-01", "2026-03-01", 100.0, 0.0
        )
        db.registerMovement(lot.id, MovementType.SALE, 6.0, 60.0, "pix", null, null, null)
        db.registerMovement(lot.id, MovementType.DONATION, 2.0, null, null, null, "doação", null)
        db.registerMovement(lot.id, MovementType.DISPOSAL, 2.0, null, null, null, "avariado", null)
        val now = System.currentTimeMillis()
        val r = db.report(now - 10_000L, now + 10_000L)
        assertEquals(6.0, r.soldQty, 0.001)
        assertEquals(60.0, r.soldValue, 0.001)
        assertEquals(2.0, r.donatedQty, 0.001)
        assertEquals(2.0, r.discardedQty, 0.001)
        assertEquals(80.0, r.utilizationPercent, 0.001)
        val csv = db.csv(now - 10_000L, now + 10_000L)
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("Venda"))
        assertTrue(csv.contains("Iogurte Teste"))
        assertTrue(db.report(now + 20_000L, now + 30_000L).processedQty == 0.0)
    }

    @Test fun showcaseBoundaryAndPublicPrice() {
        db.wipeAll()
        val p = db.addProductAndLot("Produto Vitrine", "Teste", "un", 5.0, 10.0, "Forn",
            "LT-SHOW-0", "2026-01-01", java.time.LocalDate.now(com.controlemaximo.app.data.SAO_PAULO_ZONE).plusDays(30).toString(), 10.0, 0.0)
        val today = java.time.LocalDate.now(com.controlemaximo.app.data.SAO_PAULO_ZONE)
        fun add(code: String, days: Long, qty: Double = 3.0) = db.addLotToProduct(
            p.productId, code, today.minusDays(1).toString(), today.plusDays(days).toString(), qty, 30.0
        )
        add("LT-D4", 4); add("LT-D5", 5); add("LT-D6", 6); add("LT-D10", 10)
        val zero = add("LT-ZERO", 4, 1.0)
        db.registerMovement(zero.id, MovementType.SALE, 1.0, 10.0, "pix", null, null, null)
        val offers = db.offers()
        assertTrue(offers.any { it.code == "LT-D4" })
        assertTrue(offers.any { it.code == "LT-D5" })
        assertFalse(offers.any { it.code == "LT-D6" })
        assertFalse(offers.any { it.code == "LT-D10" })
        assertFalse(offers.any { it.code == "LT-ZERO" })
        val d4 = offers.first { it.code == "LT-D4" }
        assertEquals(7.0, d4.finalPrice, 0.001)
        assertEquals(10.0, AlertEngine.discountedPrice(10.0, 0.0), 0.001)
        assertEquals(5.0, AlertEngine.discountedPrice(10.0, 50.0), 0.001)
    }

    @Test fun concurrentReservationsCannotOvershoot() {
        db.wipeAll()
        val today = com.controlemaximo.app.data.todayInSaoPaulo()
        val lot = db.addProductAndLot("Reserva concorrente", "Teste", "un", 5.0, 10.0, "Forn",
            "LT-CONC-RES", today.minusDays(1).toString(), today.plusDays(10).toString(), 10.0, 0.0)
        val start = java.util.concurrent.CountDownLatch(1)
        val done = java.util.concurrent.CountDownLatch(2)
        val success = java.util.concurrent.atomic.AtomicInteger()
        val errors = java.util.concurrent.atomic.AtomicInteger()
        repeat(2) { idx ->
            kotlin.concurrent.thread {
                start.await()
                runCatching { db.createReservation(lot.id, 7.0, "Cliente $idx", "1199999000$idx") }
                    .onSuccess { success.incrementAndGet() }
                    .onFailure { errors.incrementAndGet() }
                done.countDown()
            }
        }
        start.countDown(); done.await()
        assertEquals(1, success.get()); assertEquals(1, errors.get())
        assertEquals(7.0, db.lot(lot.id)!!.reservedQty, 0.001)
        assertEquals(3.0, db.lot(lot.id)!!.freeQty, 0.001)
    }

    @Test fun concurrentSalesCannotOversell() {
        db.wipeAll()
        val lot = db.addProductAndLot("Concorrência", "Teste", "un", 5.0, 10.0, "Forn",
            "LT-CONC-1", "2026-01-01", "2026-03-01", 10.0, 0.0)
        val start = java.util.concurrent.CountDownLatch(1)
        val done = java.util.concurrent.CountDownLatch(2)
        val success = java.util.concurrent.atomic.AtomicInteger()
        val errors = java.util.concurrent.atomic.AtomicInteger()
        repeat(2) {
            kotlin.concurrent.thread {
                start.await()
                runCatching { db.registerMovement(lot.id, MovementType.SALE, 7.0, 70.0, "pix", null, null, null) }
                    .onSuccess { success.incrementAndGet() }
                    .onFailure { errors.incrementAndGet() }
                done.countDown()
            }
        }
        start.countDown()
        done.await()
        assertEquals(1, success.get())
        assertEquals(1, errors.get())
        assertEquals(3.0, db.lot(lot.id)!!.availableQty, 0.001)
    }

    @Test fun adjustmentReferencesOriginal() {
        val lot = db.lots().first { it.id == "l4" }
        val sale = db.registerMovement(lot.id, MovementType.SALE, 2.0, 20.0, "pix", null, null, null)
        val before = db.lot(lot.id)!!.availableQty
        db.registerMovement(lot.id, MovementType.ADJUSTMENT, 1.0, null, null, null, "correção", null, sale.id)
        assertEquals(before - 1.0, db.lot(lot.id)!!.availableQty, 0.001)
        assertTrue(db.movements().any { it.referenceId == sale.id })
        assertThrows(IllegalArgumentException::class.java) {
            db.registerMovement(lot.id, MovementType.ADJUSTMENT, 1.0, null, null, null, "correção", null, "inexistente")
        }
    }

    @Test fun alertsAreDailyUniqueAndResolveWhenStockEnds() {
        val alerts = db.alerts()
        val keys = alerts.map { "${it.lot.id}|${it.level}|${it.date}" }
        assertEquals(keys.size, keys.toSet().size)
        val expired = alerts.first { it.level == AlertLevel.EXPIRED }
        db.registerMovement(expired.lot.id, MovementType.SALE, expired.lot.availableQty, 1.0, "pix", null, null, null)
        assertFalse(db.alerts().any { it.lot.id == expired.lot.id })
    }

    @Test fun settingsChangeAlertWindow() {
        db.saveSettings(db.settings().copy(attentionDays = 20, urgentDays = 10))
        val lot = db.lots().first { it.id == "l4" }
        val level = AlertEngine.level(lot.expiryDate, 20, 10)
        assertTrue(level == AlertLevel.URGENT || level == AlertLevel.ATTENTION)
    }

    @Test fun reportAggregationHandlesThousandMovements() {
        db.wipeAll()
        val lot = db.addProductAndLot("Volume", "Teste", "un", 1.0, 2.0, "Forn", "LT-1000", "2026-01-01", "2026-12-31", 2000.0, 0.0)
        val dbw = db.writableDatabase
        dbw.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            repeat(1000) { index ->
                dbw.insertOrThrow("movimentacoes", null, android.content.ContentValues().apply {
                    put("id", "perf-$index"); put("lote_id", lot.id); put("tipo", "venda")
                    put("quantidade", 1.0); put("valor_recebido", 1.0); put("forma_pagamento", "pix")
                    put("criado_em", now)
                })
            }
            dbw.setTransactionSuccessful()
        } finally { dbw.endTransaction() }
        val start = System.currentTimeMillis()
        val r = db.report(start - 60_000L, start + 60_000L)
        assertEquals(1000.0, r.soldQty, 0.001)
        assertTrue("Consulta lenta: ${System.currentTimeMillis() - start}ms", System.currentTimeMillis() - start < 3000)
    }

    @Test fun todayIsoIsValid() {
        assertTrue(todayIso().matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }
}
