package cz.slovnifotbal

import android.app.Application
import cz.slovnifotbal.data.AppContainer

class SlovniFotbalApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
