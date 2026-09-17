package pl.lejdi.plannerkmp

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication

/**
 * Owns the app's object graph.
 *
 * Held as a property rather than installed into Koin's global context: `initKoin` returns the
 * application, and [MainActivity] hands it to `App()`. That keeps the graph an ordinary value with
 * a visible owner and a visible lifetime — the thing a global `startKoin` gives away.
 */
class PlannerApplication : Application() {

    lateinit var koinApplication: KoinApplication
        private set

    override fun onCreate() {
        super.onCreate()
        koinApplication = initKoin {
            androidContext(this@PlannerApplication)
        }
    }
}
