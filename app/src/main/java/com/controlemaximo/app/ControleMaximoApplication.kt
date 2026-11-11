package com.controlemaximo.app

import android.app.Application
import com.controlemaximo.app.data.AlertScheduler

class ControleMaximoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { AlertScheduler.schedule(this) }
            .onFailure { android.util.Log.w("ControleMaximo", "Job de alertas não pôde ser agendado", it) }
    }
}
