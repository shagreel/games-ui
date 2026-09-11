import Foundation

enum Config {
    /// Public board game catalog, same source the web app uses.
    static let catalogURL = URL(string: "https://public.chill.ws/games.json")!

    /// Same Adobe Experience Platform edge configuration (datastream) the
    /// web app's Alloy instance uses — see src/index.js. Not sensitive,
    /// it's already embedded in the public web bundle.
    static let edgeConfigId = "e8922806-0c73-4c26-a4f8-f102f34c9af6"

    /// Backend that tracks borrow/return state. Set the real value in
    /// Configs/Debug.xcconfig and Configs/Release.xcconfig.
    static var apiBaseURL: URL {
        guard
            let raw = Bundle.main.object(forInfoDictionaryKey: "API_BASE_URL") as? String,
            !raw.isEmpty,
            let url = URL(string: raw)
        else {
            fatalError("API_BASE_URL is missing or invalid. Set it in Configs/*.xcconfig.")
        }
        return url
    }
}
