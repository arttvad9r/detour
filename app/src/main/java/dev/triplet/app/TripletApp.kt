package dev.triplet.app

import android.app.Application
import dev.triplet.app.data.AppInventory
import dev.triplet.app.data.RoutesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TripletApp : Application() {
    lateinit var routesStore: RoutesStore
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        routesStore = RoutesStore(this)

        // PackageManager discovery is useful only on the routes screen, so warm
        // it off the main thread while the user is on the home screen.
        appScope.launch { AppInventory.load(this@TripletApp) }
    }
}
