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

    @Test fun todayIsoIsValid() {
        assertTrue(todayIso().matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }
}
