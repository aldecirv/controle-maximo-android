package com.controlemaximo.app

import com.controlemaximo.app.data.AlertScheduler
import com.controlemaximo.app.data.SAO_PAULO_ZONE
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime

class AlertSchedulerTest {
    @Test fun nextRunIsSevenSaoPaulo() {
        val base = ZonedDateTime.of(2026, 1, 10, 6, 0, 0, 0, SAO_PAULO_ZONE)
        assertEquals(60, AlertScheduler.nextRunDelay(base).toMinutes())
        val after = ZonedDateTime.of(2026, 1, 10, 8, 0, 0, 0, SAO_PAULO_ZONE)
        assertEquals(23 * 60, AlertScheduler.nextRunDelay(after).toMinutes())
        val exactly = ZonedDateTime.of(2026, 1, 10, 7, 0, 0, 0, SAO_PAULO_ZONE)
        assertEquals(24 * 60, AlertScheduler.nextRunDelay(exactly).toMinutes())
    }
}
