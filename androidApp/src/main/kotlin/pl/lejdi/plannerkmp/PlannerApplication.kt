package pl.lejdi.plannerkmp

import android.app.Application
import org.koin.android.ext.koin.androidContext

class PlannerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@PlannerApplication)
        }
    }
}
