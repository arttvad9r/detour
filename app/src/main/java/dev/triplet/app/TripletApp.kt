package dev.triplet.app

import android.app.Application
import dev.triplet.app.data.RoutesStore

class TripletApp : Application() {
    lateinit var routesStore: RoutesStore
        private set
    override fun onCreate() {
        super.onCreate()
        routesStore = RoutesStore(this)
    }
}
