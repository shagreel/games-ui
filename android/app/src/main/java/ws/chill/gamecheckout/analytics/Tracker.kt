package ws.chill.gamecheckout.analytics

import com.adobe.marketing.mobile.Edge
import com.adobe.marketing.mobile.ExperienceEvent

/**
 * Port of `Analytics/Tracker.swift` — same events, same XDM field names, sent
 * through Adobe Experience Platform Edge Network.
 *
 * Events leave the device via the Edge extension, so [init] must have run: the
 * extension is registered and pointed at [EDGE_CONFIG_ID] by `GameCheckoutApp`.
 * Sending before that completes is not an error, but the event will not reach
 * the Edge Network.
 */
object Tracker {

    /**
     * Same Adobe Experience Platform edge configuration (datastream) the web
     * app's Alloy instance and the iOS app use. Not sensitive — it is already
     * embedded in the public web bundle.
     */
    const val EDGE_CONFIG_ID = "e8922806-0c73-4c26-a4f8-f102f34c9af6"

    /**
     * Stands in for iOS's `Bundle.main.bundleIdentifier` — the app identity
     * embedded in the page-view URL and server fields.
     */
    const val APP_IDENTIFIER = "ws.chill.gamecheckout"

    /**
     * Set once the SDK has finished registering its extensions. Events raised
     * before that are dropped rather than queued, matching the iOS behaviour
     * where `Tracker.trackAppLaunch()` runs from the registration callback.
     */
    @Volatile
    var ready: Boolean = false
        private set

    /** Called from the extension-registration callback in `GameCheckoutApp`. */
    internal fun markReady() {
        ready = true
    }

    fun trackAppLaunch() = send(
        mapOf("application" to mapOf("launches" to mapOf("value" to 1))),
    )

    fun trackPageView(name: String) = send(
        mapOf(
            "web" to mapOf(
                "webPageDetails" to mapOf(
                    "pageViews" to mapOf("value" to 1),
                    "name" to name,
                    "URL" to "app://$APP_IDENTIFIER/$name",
                    "server" to APP_IDENTIFIER,
                ),
            ),
        ),
    )

    fun trackViewed(game: String) = send(
        mapOf("_mobiledx" to mapOf("viewed" to 1, "gameName" to game)),
    )

    fun trackBorrowed(game: String, name: String, email: String) = send(
        mapOf(
            "_mobiledx" to mapOf(
                "borrowed" to 1,
                "gameName" to game,
                "borrowerName" to name,
                "borrowerEmail" to email,
            ),
        ),
    )

    fun trackReturned(game: String, name: String, email: String) = send(
        mapOf(
            "_mobiledx" to mapOf(
                "returned" to 1,
                "gameName" to game,
                "borrowerName" to name,
                "borrowerEmail" to email,
            ),
        ),
    )

    private fun send(xdm: Map<String, Any>) {
        if (!ready) return
        val event = ExperienceEvent.Builder()
            .setXdmSchema(xdm)
            .build()
        // Null callback: this app does not consume the Edge Network response,
        // matching the iOS `Edge.sendEvent(experienceEvent:)` with no completion.
        Edge.sendEvent(event, null)
    }
}
