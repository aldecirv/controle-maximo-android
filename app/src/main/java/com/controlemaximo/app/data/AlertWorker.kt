package com.controlemaximo.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class AlertWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return runCatching {
            val db = InventoryDb(applicationContext)
            try {
                val alerts = db.refreshAlerts()
                val urgent = alerts.count { it.level == AlertLevel.URGENT }
                val expired = alerts.count { it.level == AlertLevel.EXPIRED }
                val attention = alerts.count { it.level == AlertLevel.ATTENTION }
                Log.i("ControleMaximo", "Job 07:00 SP: ${alerts.size} alertas (atenção=$attention, urgente=$urgent, vencido=$expired).")
                if (alerts.isNotEmpty() && canNotify()) notify(alerts.size, urgent, expired)
            } finally {
                db.close()
            }
            Result.success()
        }.onFailure {
            Log.e("ControleMaximo", "Falha no job de alertas", it)
        }.getOrDefault(Result.retry())
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun notify(total: Int, urgent: Int, expired: Int) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel("validity_alerts", "Alertas de validade", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, "validity_alerts")
            .setSmallIcon(com.controlemaximo.app.R.drawable.ic_bell)
            .setContentTitle("Controle Máximo • alertas de validade")
            .setContentText("$total lote(s) em risco: $urgent urgente(s), $expired vencido(s).")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(PendingIntent.getActivity(
                applicationContext, 7001,
                Intent(applicationContext, Class.forName("com.controlemaximo.app.ui.MainActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            .setAutoCancel(true)
            .build()
        manager.notify(7001, notification)
    }
}
