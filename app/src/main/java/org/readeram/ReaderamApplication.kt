package org.readeram

import android.app.Application
import org.readeram.data.AppContainer

class ReaderamApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
    }

    companion object {
        lateinit var instance: ReaderamApplication
            private set
    }
}
