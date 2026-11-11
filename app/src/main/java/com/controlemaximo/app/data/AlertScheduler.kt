package com.controlemaximo.app.data

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object AlertScheduler {
    const val WORK_NAME = "controle-maximo-alertas-7h"

    /** Próximo atraso até 07:00 em America/Sao_Paulo. */
    fun nextRunDelay(now: ZonedDateTime = ZonedDateTime.now(SAO_PAULO_ZONE)): Duration {
        var next = now.withHour(7).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    fun schedule(context: Context) {
        val delay = nextRunDelay()
        val request = PeriodicWorkRequestBuilder<AlertWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(false).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        Log.i("ControleMaximo", "Job diário de alertas agendado; próximo em ${delay.toMinutes()} min (07:00 America/Sao_Paulo).")
    }
}
