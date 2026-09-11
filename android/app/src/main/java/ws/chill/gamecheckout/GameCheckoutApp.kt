package ws.chill.gamecheckout

import android.app.Application
import ws.chill.gamecheckout.analytics.Tracker
import ws.chill.gamecheckout.di.AppContainer

/**
 * Port of `GameCheckoutApp.swift`'s `init()`.
 *
 * The iOS app registers the Adobe Experience Platform extensions here and sends
 * a launch event. Analytics is out of scope for this port (see [Tracker]), so
 * this only builds the dependency graph and fires the same launch hook.
 */
class GameCheckoutApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Tracker.trackAppLaunch()
    }
}
