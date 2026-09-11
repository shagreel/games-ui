package ws.chill.gamecheckout

import android.app.Application
import com.adobe.marketing.mobile.Edge
import com.adobe.marketing.mobile.LoggingMode
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.mobile.edge.identity.Identity
import ws.chill.gamecheckout.analytics.Tracker
import ws.chill.gamecheckout.di.AppContainer

/**
 * Port of `GameCheckoutApp.swift`'s `init()`.
 *
 * Registers the Adobe Experience Platform extensions, points them at the same
 * datastream the iOS app and web app use, then sends a launch event from the
 * registration callback.
 */
class GameCheckoutApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Must precede every other Mobile SDK call.
        MobileCore.setApplication(this)

        if (BuildConfig.DEBUG) {
            MobileCore.setLogLevel(LoggingMode.DEBUG)
        }

        // Edge and Edge Identity only — deliberately *no* `edgeconsent`.
        //
        // This is an internal tool with no per-user consent requirement, and
        // omitting the Consent extension is what makes that explicit: Edge falls
        // back to its built-in default of collect-consent `yes` and sends
        // unconditionally. Registering Consent and calling `Consent.update` is
        // what would introduce a consent gate.
        //
        // The SDK still logs "Collect consent is pending, suspending the Edge
        // queue" at boot and then "Consent extension is not registered yet, using
        // default collect status (yes)" before resuming the queue. That pair is
        // expected and harmless — do not "fix" it by adding the extension.
        MobileCore.registerExtensions(
            listOf(Edge.EXTENSION, Identity.EXTENSION),
        ) {
            // Datastream configuration, equivalent to the iOS call
            // `MobileCore.updateConfigurationWith(configDict: ["edge.configId": …])`.
            val config = mapOf<String, Any>("edge.configId" to Tracker.EDGE_CONFIG_ID)
            MobileCore.updateConfiguration(config)

            Tracker.markReady()
            Tracker.trackAppLaunch()
        }
    }
}
