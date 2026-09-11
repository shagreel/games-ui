import SwiftUI
import AEPCore
import AEPEdge
import AEPEdgeIdentity

@main
struct GameCheckoutApp: App {
    @StateObject private var authManager = AuthManager()

    init() {
        #if DEBUG
        MobileCore.setLogLevel(.debug)
        #endif
        MobileCore.registerExtensions([Edge.self, AEPEdgeIdentity.Identity.self]) {
            MobileCore.updateConfigurationWith(configDict: ["edge.configId": Config.edgeConfigId])
            Tracker.trackAppLaunch()
        }
    }

    var body: some Scene {
        WindowGroup {
            if authManager.isAuthenticated {
                GameListView(authManager: authManager)
            } else {
                LoginView(authManager: authManager)
            }
        }
    }
}
