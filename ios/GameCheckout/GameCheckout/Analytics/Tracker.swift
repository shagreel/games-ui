import Foundation
import AEPEdge

/// Mirrors src/WebSdkContext.js — same events, same XDM field names, sent
/// through Adobe Experience Platform Edge Network instead of the Alloy Web SDK.
enum Tracker {
    private static let appIdentifier = Bundle.main.bundleIdentifier ?? "ws.chill.gamecheckout"

    static func trackAppLaunch() {
        send(xdm: [
            "application": [
                "launches": ["value": 1]
            ]
        ])
    }

    static func trackPageView(_ name: String) {
        send(xdm: [
            "web": [
                "webPageDetails": [
                    "pageViews": ["value": 1],
                    "name": name,
                    "URL": "app://\(appIdentifier)/\(name)",
                    "server": appIdentifier
                ]
            ]
        ])
    }

    static func trackViewed(game: String) {
        send(xdm: [
            "_mobiledx": [
                "viewed": 1,
                "gameName": game
            ]
        ])
    }

    static func trackBorrowed(game: String, name: String, email: String) {
        send(xdm: [
            "_mobiledx": [
                "borrowed": 1,
                "gameName": game,
                "borrowerName": name,
                "borrowerEmail": email
            ]
        ])
    }

    static func trackReturned(game: String, name: String, email: String) {
        send(xdm: [
            "_mobiledx": [
                "returned": 1,
                "gameName": game,
                "borrowerName": name,
                "borrowerEmail": email
            ]
        ])
    }

    private static func send(xdm: [String: Any]) {
        Edge.sendEvent(experienceEvent: ExperienceEvent(xdm: xdm))
    }
}
