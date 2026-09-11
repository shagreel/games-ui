package ws.chill.gamecheckout.auth

import android.content.Context
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ws.chill.gamecheckout.analytics.Tracker
import ws.chill.gamecheckout.network.ApiClient
import ws.chill.gamecheckout.network.ApiException

/**
 * Port of `Auth/AuthManager.swift`.
 *
 * Mirrors the web app's CFP password gate: the raw password never leaves the
 * device, only its sha256 hash is stored and sent as the `x-cfp` header,
 * matching what the `CFP-Auth-Key` cookie holds on the web.
 */
class AuthManager(
    context: Context,
    private val api: ApiClient = ApiClient(),
) : AuthSession {
    data class State(
        val isAuthenticated: Boolean = false,
        val isValidating: Boolean = false,
        val loginError: String? = null,
    )

    private val store = SecureStore(context, SERVICE)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Sent verbatim as the `x-cfp` header on every `/games/...` request. */
    @Volatile
    override var authHeaderValue: String? = null
        private set

    /**
     * Set when the backend rejects a request; the next successful login must
     * clear the stale "no longer valid" message. iOS gets this for free because
     * assigning `loginError` and reading it happen on the same run loop.
     */
    @Volatile
    private var needsReauth = false

    init {
        restoreSession()
    }

    /**
     * Validates the password against the backend before granting access — only
     * a hash the API actually accepts gets stored and treated as a valid session.
     */
    suspend fun login(password: String) {
        if (password.isEmpty()) return

        _state.value = State(
            isAuthenticated = false,
            isValidating = true,
            loginError = if (needsReauth) _state.value.loginError else null,
        )

        val hash = sha256(password)

        val (nextState, succeeded) = try {
            api.fetchBorrowed(hash)
            val expiry = Instant.now().plusSeconds(VALIDITY_SECONDS)
            store.save(ACCOUNT_HASH, hash)
            store.save(ACCOUNT_EXPIRY, DateTimeFormatter.ISO_INSTANT.format(expiry))

            authHeaderValue = hash
            needsReauth = false
            State(isAuthenticated = true, isValidating = false, loginError = null) to true
        } catch (e: ApiException.Unauthorized) {
            State(loginError = "Incorrect password. Please try again.") to false
        } catch (e: Exception) {
            State(loginError = "Couldn't reach the server. Check your connection and try again.") to false
        }

        if (succeeded) {
            _state.value = nextState
            Tracker.trackPageView("Login")
        } else {
            needsReauth = false
            _state.value = State(
                isAuthenticated = false,
                isValidating = false,
                loginError = nextState.loginError,
            )
        }
    }

    fun signOut() {
        store.delete(ACCOUNT_HASH)
        store.delete(ACCOUNT_EXPIRY)
        authHeaderValue = null
        _state.value = State()
    }

    /**
     * Call when the backend rejects a request with 401/403 — the stored
     * password is wrong or has expired, so drop back to the login screen.
     */
    override fun handleUnauthorized() {
        signOut()
        needsReauth = true
        _state.value = State(
            isAuthenticated = false,
            isValidating = false,
            loginError = "Incorrect password, or it's no longer valid. Please try again.",
        )
    }

    private fun restoreSession() {
        val hash = store.read(ACCOUNT_HASH)
        val expiryString = store.read(ACCOUNT_EXPIRY)
        val expiry = expiryString?.let {
            try {
                Instant.parse(it)
            } catch (e: Exception) {
                null
            }
        }

        if (hash == null || expiry == null || !expiry.isAfter(Instant.now())) {
            authHeaderValue = null
            _state.value = State(isAuthenticated = false)
            return
        }

        authHeaderValue = hash
        _state.value = State(isAuthenticated = true)
    }

    companion object {
        /** ~1 year, mirroring `AuthManager.validityInterval` on iOS. */
        const val VALIDITY_SECONDS: Long = 60L * 60L * 24L * 365L

        const val SERVICE = "ws.chill.gamecheckout.auth"
        const val ACCOUNT_HASH = "cfp-hash"
        const val ACCOUNT_EXPIRY = "cfp-hash-expiry"

        fun sha256(string: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(string.toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { "%02x".format(it) }
    }
}
