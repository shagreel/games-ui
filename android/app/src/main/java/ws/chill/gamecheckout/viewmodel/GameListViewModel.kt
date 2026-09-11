package ws.chill.gamecheckout.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ws.chill.gamecheckout.data.Game
import ws.chill.gamecheckout.di.AppContainer
import ws.chill.gamecheckout.repository.GameRepository

/**
 * Port of `GameListViewModel.swift`.
 *
 * Search matches the game name, the borrower's name, and the borrower's email —
 * the same three fields the web app hands to Fuse.js and the iOS port matches
 * with `localizedCaseInsensitiveContains`.
 */
class GameListViewModel(
    private val repository: GameRepository,
) : ViewModel() {

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText

    val isLoading: StateFlow<Boolean> = repository.isLoading
    val errorMessage: StateFlow<String?> = repository.errorMessage

    /** Prefill values for the borrow form, from the last successful borrow. */
    val borrowerName: String get() = repository.borrowerName
    val borrowerEmail: String get() = repository.borrowerEmail

    val filteredGames: StateFlow<List<Game>> =
        combine(repository.games, _searchText) { games, query -> GameSearch.filter(games, query) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Currently borrowed games, sorted exactly like the iOS port's
     * `borrowedGames` (`$0.borrowed!.date > $1.borrowed!.date`). Dates are
     * `yyyy-MM-dd`, so a descending lexicographic compare is a descending
     * chronological compare — newest borrow date first.
     */
    val borrowedGames: StateFlow<List<Game>> =
        repository.games
            .map { games ->
                games.filter { it.borrowed != null }
                    .sortedByDescending { it.borrowed?.date.orEmpty() }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun onSearchTextChange(value: String) {
        _searchText.value = value
    }

    fun load() {
        viewModelScope.launch { repository.load() }
    }

    /**
     * Returns null on success, or a user-facing error message. iOS surfaces the
     * same generic string from `BorrowReturnSheet`; the unauthorized case has
     * already dropped the app back to the login screen by then.
     */
    suspend fun borrow(game: Game, name: String, email: String): String? = try {
        repository.borrow(game, name, email)
        null
    } catch (e: Exception) {
        GENERIC_ERROR
    }

    suspend fun returnGame(game: Game): String? = try {
        repository.returnGame(game)
        null
    } catch (e: Exception) {
        GENERIC_ERROR
    }

    companion object {
        const val GENERIC_ERROR = "Something went wrong. Please try again."
    }
}

/**
 * Port of the `applyFilter()` half of `GameListViewModel.swift`.
 *
 * Kept as a standalone object (rather than a private method) so the search
 * semantics are unit-testable, and so they sit next to the Swift original in
 * review: same three fields, same case-insensitive substring match, blank
 * queries pass everything through.
 */
object GameSearch {

    fun filter(games: List<Game>, rawQuery: String): List<Game> {
        val query = rawQuery.trim()
        if (query.isEmpty()) return games

        return games.filter { game ->
            game.name.containsIgnoreCase(query) ||
                game.borrowed?.name?.containsIgnoreCase(query) == true ||
                game.borrowed?.email?.containsIgnoreCase(query) == true
        }
    }

    /**
     * Case-insensitive substring match, the closest stable equivalent of
     * `localizedCaseInsensitiveContains`.
     */
    private fun String.containsIgnoreCase(other: String): Boolean =
        indexOf(other, ignoreCase = true) >= 0
}

/**
 * Builds the view model with the process-wide [AppContainer] that
 * `MainActivity` owns.
 */
fun gameListViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { GameListViewModel(container.gameRepository) }
    }
