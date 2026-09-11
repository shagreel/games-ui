package ws.chill.gamecheckout.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ws.chill.gamecheckout.analytics.Tracker
import ws.chill.gamecheckout.auth.AuthSession
import ws.chill.gamecheckout.data.Game
import ws.chill.gamecheckout.network.ApiClient
import ws.chill.gamecheckout.network.ApiException
import ws.chill.gamecheckout.profile.BorrowerProfile

/**
 * Port of the loading and mutation half of `GameListViewModel.swift`: fetches
 * the catalog and the borrowed list concurrently, merges them, and applies
 * borrow/return results to the in-memory model.
 *
 * Holding this state above the screens mirrors the iOS layout, where a single
 * `GameListViewModel` instance is shared with `AdminView` so the borrowed list
 * reuses already-loaded data instead of a second network round-trip.
 */
class GameRepository(
    private val authManager: AuthSession,
    private val api: ApiClient,
    private val profileStore: BorrowerProfile,
) {
    private val _games = MutableStateFlow<List<Game>>(emptyList())
    val games: StateFlow<List<Game>> = _games.asStateFlow()

    /** Last name/email used on this device, used to prefill the borrow form. */
    val borrowerName: String get() = profileStore.name
    val borrowerEmail: String get() = profileStore.email

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Loads the catalog and borrowed state. On a 401/403 the caller is signed
     * out; any other failure of the borrowed request degrades gracefully and
     * shows the catalog without borrowed status.
     */
    suspend fun load() {
        val authHeader = authManager.authHeaderValue ?: return

        _isLoading.value = true
        _errorMessage.value = null

        val games: List<Game>? = try {
            coroutineScope {
                val catalogTask = async { api.fetchCatalog() }
                val borrowedTask = async { runCatching { api.fetchBorrowed(authHeader) } }

                val catalog = catalogTask.await()
                val borrowedResult = borrowedTask.await()

                val entries = borrowedResult.getOrNull()
                if (entries == null) {
                    if (borrowedResult.exceptionOrNull() is ApiException.Unauthorized) {
                        authManager.handleUnauthorized()
                        // Signal the caller to bail out; returning from inside
                        // coroutineScope would not be a local return.
                        null
                    } else {
                        // Degrade gracefully: catalog without borrowed status.
                        catalog
                    }
                } else {
                    val borrowedById = entries
                        .mapNotNull { entry -> entry.borrowed?.let { entry.id to it } }
                        .toMap()
                    catalog.map { it.copy(borrowed = borrowedById[it.id]) }
                }
            }
        } catch (e: Exception) {
            _errorMessage.value = "Couldn't load games. Pull to refresh to try again."
            _isLoading.value = false
            return
        }

        if (games == null) {
            _isLoading.value = false
            return
        }

        _games.value = games
        _isLoading.value = false
    }

    /** `PUT /games/borrow`, then reflects the result locally. */
    suspend fun borrow(game: Game, name: String, email: String) {
        val authHeader = authManager.authHeaderValue ?: throw ApiException.Unauthorized
        try {
            val info = api.borrowGame(
                id = game.id,
                name = name,
                email = email,
                authHeader = authHeader,
            )
            updateLocal(game.id) { it.copy(borrowed = info) }
            profileStore.save(name = name, email = email)
            if (info?.name == name) {
                Tracker.trackBorrowed(game = game.name, name = name, email = email)
            }
        } catch (e: ApiException.Unauthorized) {
            authManager.handleUnauthorized()
            throw e
        }
    }

    /** `PUT /games/return`, then clears the game's borrowed state locally. */
    suspend fun returnGame(game: Game) {
        val authHeader = authManager.authHeaderValue ?: throw ApiException.Unauthorized
        try {
            api.returnGame(id = game.id, authHeader = authHeader)
            game.borrowed?.let { borrowed ->
                Tracker.trackReturned(
                    game = game.name,
                    name = borrowed.name,
                    email = borrowed.email,
                )
            }
            updateLocal(game.id) { it.copy(borrowed = null) }
        } catch (e: ApiException.Unauthorized) {
            authManager.handleUnauthorized()
            throw e
        }
    }

    private fun updateLocal(id: String, mutate: (Game) -> Game) {
        _games.value = _games.value.map { if (it.id == id) mutate(it) else it }
    }
}
