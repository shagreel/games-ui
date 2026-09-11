import Foundation

/// Remembers the last name/email used to borrow a game so the borrow form
/// can prefill itself next time. Not sensitive, so UserDefaults (not
/// Keychain) is fine here.
enum BorrowerProfileStore {
    private static let nameKey = "borrowerProfile.name"
    private static let emailKey = "borrowerProfile.email"

    static var name: String {
        UserDefaults.standard.string(forKey: nameKey) ?? ""
    }

    static var email: String {
        UserDefaults.standard.string(forKey: emailKey) ?? ""
    }

    static func save(name: String, email: String) {
        if name != Self.name {
            UserDefaults.standard.set(name, forKey: nameKey)
        }
        if email != Self.email {
            UserDefaults.standard.set(email, forKey: emailKey)
        }
    }
}
