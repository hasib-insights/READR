package com.readr.app

import android.app.Application
import com.readr.app.di.AppContainer
import com.readr.app.util.CrashLogger

class ReadrApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CrashLogger.install(container.logsDir)
    }
}
