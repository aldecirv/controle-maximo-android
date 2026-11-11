package com.controlemaximo.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.controlemaximo.app.data.AlertLevel
import com.controlemaximo.app.data.InventoryDb
import com.controlemaximo.app.data.MovementType
import com.controlemaximo.app.data.SAO_PAULO_ZONE
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AcceptanceScenarioTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: InventoryDb

    @Before fun setup() {
        context.deleteDatabase("controle_maximo.db")
        db = InventoryDb(context)
        db.wipeAll()
    }

    @After fun tearDown() {
        db.close()
        context.deleteDatabase("controle_maximo.db")
    }

    @Test fun completeMvpScenario() {
        // A configuração é ajustada para 6 dias apenas neste cenário; o padrão do produto é 5.
        db.saveSettings(db.settings().copy(showcaseDays = 6))
        val today = LocalDate.now(SAO_PAULO_ZONE)
        val lot = db.addProductAndLot(
            "Iogurte Morango 1L", "Laticínios", "un", 5.0, 10.0, "Fornecedor Teste",
            "LT-ACEITE-1", today.minusDays(20).toString(), today.plusDays(6).toString(), 50.0, 30.0
        )
        val alert = db.alerts().first { it.lot.id == lot.id }
        assertEquals(AlertLevel.ATTENTION, alert.level)

        val offer = db.offers().first { it.id == lot.id }
        assertEquals(7.0, offer.finalPrice, 0.001)

        val reservation = db.createReservation(lot.id, 5.0, "Cliente Aceite", "11999998888")
        assertTrue(reservation.code.startsWith("CMC-"))
        assertEquals(45.0, db.lot(lot.id)!!.freeQty, 0.001)

        db.writableDatabase.execSQL(
            "UPDATE reservas SET expira_em=? WHERE id=?",
            arrayOf(System.currentTimeMillis() - 1000L, reservation.id)
        )
        assertEquals(1, db.expireReservations())
        assertEquals(50.0, db.lot(lot.id)!!.freeQty, 0.001)

        val institution = db.addInstitution("Instituição Aceite", "Contato", "11988887777")
        db.registerMovement(lot.id, MovementType.SALE, 10.0, 70.0, "pix", null, null, null)
        db.registerMovement(lot.id, MovementType.DONATION, 5.0, null, null, institution.id, "doação", null)
        db.registerMovement(lot.id, MovementType.DISPOSAL, 3.0, null, null, null, "avariado", "teste")
        assertEquals(32.0, db.lot(lot.id)!!.availableQty, 0.001)
        assertTrue(db.lot(lot.id)!!.status != "esgotado")

        val report = db.report(System.currentTimeMillis() - 86_400_000L, System.currentTimeMillis() + 1000L)
        assertEquals(10.0, report.soldQty, 0.001)
        assertEquals(5.0, report.donatedQty, 0.001)
        assertEquals(3.0, report.discardedQty, 0.001)
        assertEquals(70.0, report.soldValue, 0.001)
        assertEquals(50.0, report.donatedValue, 0.001)

        val csv = db.csv(report.start, report.end)
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("Iogurte Morango 1L"))
        assertTrue(csv.contains("Venda"))
    }
}
