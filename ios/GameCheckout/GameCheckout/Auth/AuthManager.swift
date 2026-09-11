import Foundation
import CryptoKit

/// Mirrors the web app's CFP password gate: the raw password never leaves the
/// device, only its sha256 hash is stored and sent as the `x-cfp` header,
/// matching what the `CFP-Auth-Key` cookie holds on the web.
@MainActor
final class AuthManager: ObservableObject {
    static let validityInterval: TimeInterval = 60 * 60 * 24 * 365 // ~1 year

    @Published private(set) var isAuthenticated = false
    @Published var loginError: String?
    @Published private(set) var isValidating = false

    private(set) var authHeaderValue: String?

    private let service = "ws.chill.gamecheckout.auth"
    private let hashAccount = "cfp-hash"
    private let expiryAccount = "cfp-hash-expiry"

    init() {
        restoreSession()
    }

    /// Validates the password against the backend before granting access —
    /// only a hash the API actually accepts gets stored and treated as a
    /// valid session.
    func login(password: String) async {
        guard !password.isEmpty else { return }
        isValidating = true
        loginError = nil

        let hash = Self.sha256(password)
        do {
            _ = try await APIClient.shared.fetchBorrowed(authHeader: hash)

            let expiry = Date().addingTimeInterval(Self.validityInterval)
            KeychainStore.save(service: service, account: hashAccount, value: hash)
            KeychainStore.save(service: service, account: expiryAccount, value: ISO8601DateFormatter().string(from: expiry))

            authHeaderValue = hash
            isAuthenticated = true
        } catch APIError.unauthorized {
            loginError = "Incorrect password. Please try again."
        } catch {
            loginError = "Couldn't reach the server. Check your connection and try again."
        }

        isValidating = false
    }

    func signOut() {
        KeychainStore.delete(service: service, account: hashAccount)
        KeychainStore.delete(service: service, account: expiryAccount)
        authHeaderValue = nil
        isAuthenticated = false
    }

    /// Call when the backend rejects a request with 401/403 — the stored
    /// password is wrong or has expired, so drop back to the login screen.
    func handleUnauthorized() {
        signOut()
        loginError = "Incorrect password, or it's no longer valid. Please try again."
    }

    private func restoreSession() {
        guard
            let hash = KeychainStore.read(service: service, account: hashAccount),
            let expiryString = KeychainStore.read(service: service, account: expiryAccount),
            let expiry = ISO8601DateFormatter().date(from: expiryString),
            expiry > Date()
        else {
            authHeaderValue = nil
            isAuthenticated = false
            return
        }
        authHeaderValue = hash
        isAuthenticated = true
    }

    private static func sha256(_ string: String) -> String {
        SHA256.hash(data: Data(string.utf8))
            .map { String(format: "%02x", $0) }
            .joined()
    }
}
