package ws.chill.gamecheckout.auth

/**
 * The slice of auth the network-facing layers need: the credential to send, and
 * a hook for when the backend rejects it.
 *
 * Splitting this out of [AuthManager] keeps [ws.chill.gamecheckout.repository.GameRepository]
 * free of `Context` and Android Keystore concerns, so its merge logic is
 * unit-testable with a fake session.
 */
interface AuthSession {
    /** Sent verbatim as the `x-cfp` header on every `/games/...` request. */
    val authHeaderValue: String?

    /**
     * Call when the backend rejects a request with 401/403 — the stored
     * credential is wrong or has expired, so drop back to the login screen.
     */
    fun handleUnauthorized()
}
