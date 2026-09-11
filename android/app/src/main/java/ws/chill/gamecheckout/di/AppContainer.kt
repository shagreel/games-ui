package ws.chill.gamecheckout.di

import android.content.Context
import ws.chill.gamecheckout.auth.AuthManager
import ws.chill.gamecheckout.network.ApiClient
import ws.chill.gamecheckout.profile.BorrowerProfileStore
import ws.chill.gamecheckout.repository.GameRepository

/**
 * Tiny hand-rolled dependency graph. The app has exactly one of each of these,
 * mirroring the single `AuthManager` / `GameListViewModel` instances the iOS
 * `@StateObject` properties hold for the process lifetime.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val apiClient: ApiClient by lazy { ApiClient() }

    val authManager: AuthManager by lazy { AuthManager(appContext, apiClient) }

    val borrowerProfileStore: BorrowerProfileStore by lazy {
        BorrowerProfileStore(appContext)
    }

    val gameRepository: GameRepository by lazy {
        GameRepository(
            authManager = authManager,
            api = apiClient,
            profileStore = borrowerProfileStore,
        )
    }
}
