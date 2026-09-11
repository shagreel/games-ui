package ws.chill.gamecheckout.analytics

import android.util.Log

/**
 * Analytics is deliberately **not** ported from iOS.
 *
 * The iOS app sends Adobe Experience Platform Edge events (`Analytics/Tracker.swift`,
 * edgeConfigId `e8922806-0c73-4c26-a4f8-f102f34c9af6`), but that app's own README
 * lists analytics as out of scope: "Not included (by design, for this first pass)".
 *
 * This object exists so every tracking call site in this port mirrors its Swift
 * counterpart one-for-one. To turn tracking on, add the AEP Edge + Edge Identity
 * Android SDKs (`com.adobe.marketing.mobile:sdk-bom`, `:edge`, `:edgeidentity`,
 * `:core`), register `Edge.EXTENSION` and `EdgeIdentity.EXTENSION`, configure the
 * datastream id, and implement the `send` calls below with the same XDM payloads
 * the Swift `Tracker` builds.
 */
object Tracker {

    const val EDGE_CONFIG_ID = "e8922806-0c73-4c26-a4f8-f102f34c9af6"

    private const val TAG = "GameCheckout/Analytics"

    /**
     * Stands in for iOS's `Bundle.main.bundleIdentifier` — the app identity
     * embedded in the page-view URL and server fields.
     */
    const val APP_IDENTIFIER = "ws.chill.gamecheckout"

    var enabled: Boolean = false

    fun trackAppLaunch() = send("application.launches") {
        mapOf("application" to mapOf("launches" to mapOf("value" to 1)))
    }

    fun trackPageView(name: String) = send("web.webPageDetails") {
        mapOf(
            "web" to mapOf(
                "webPageDetails" to mapOf(
                    "pageViews" to mapOf("value" to 1),
                    "name" to name,
                    "URL" to "app://$APP_IDENTIFIER/$name",
                    "server" to APP_IDENTIFIER,
                ),
            ),
        )
    }

    fun trackViewed(game: String) = send("viewed") {
        mapOf("_mobiledx" to mapOf("viewed" to 1, "gameName" to game))
    }

    fun trackBorrowed(game: String, name: String, email: String) = send("borrowed") {
        mapOf(
            "_mobiledx" to mapOf(
                "borrowed" to 1,
                "gameName" to game,
                "borrowerName" to name,
                "borrowerEmail" to email,
            ),
        )
    }

    fun trackReturned(game: String, name: String, email: String) = send("returned") {
        mapOf(
            "_mobiledx" to mapOf(
                "returned" to 1,
                "gameName" to game,
                "borrowerName" to name,
                "borrowerEmail" to email,
            ),
        )
    }

    private inline fun send(event: String, xdm: () -> Map<String, Any>) {
        if (!enabled) return
        Log.d(TAG, "sendEvent $event xdm=${xdm()}")
    }
}
