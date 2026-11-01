package dev.jasper.ishav2

import android.app.Application
import dev.jasper.ishav2.agent.AppContainer

class IshaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
