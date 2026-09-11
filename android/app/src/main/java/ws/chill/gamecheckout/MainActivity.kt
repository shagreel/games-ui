package ws.chill.gamecheckout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import ws.chill.gamecheckout.di.AppContainer
import ws.chill.gamecheckout.ui.screens.AdminScreen
import ws.chill.gamecheckout.ui.screens.GameListScreen
import ws.chill.gamecheckout.ui.screens.LoginScreen
import ws.chill.gamecheckout.ui.theme.GameCheckoutTheme
import ws.chill.gamecheckout.viewmodel.GameListViewModel
import ws.chill.gamecheckout.viewmodel.gameListViewModelFactory

/**
 * Port of `GameCheckoutApp`'s `body`: the login screen until a stored credential
 * is validated, then the game list.
 *
 * The game list and the borrowed-games screen share one [GameListViewModel]
 * instance, which is what lets the iOS `AdminView` reuse the already-loaded
 * catalog instead of making a second round-trip.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as GameCheckoutApp).container

        setContent {
            GameCheckoutTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GameCheckoutRoot(container = container)
                }
            }
        }
    }
}

private const val ROUTE_GAME_LIST = "game_list"
private const val ROUTE_BORROWED = "borrowed"

@Composable
private fun GameCheckoutRoot(container: AppContainer) {
    val authState by container.authManager.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    if (!authState.isAuthenticated) {
        LoginScreen(
            isValidating = authState.isValidating,
            loginError = authState.loginError,
            onSubmit = { password -> scope.launch { container.authManager.login(password) } },
        )
        return
    }

    // Created once per authenticated session and shared by both destinations.
    val viewModel: GameListViewModel = viewModel(factory = gameListViewModelFactory(container))

    LaunchedEffect(viewModel) { viewModel.load() }

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ROUTE_GAME_LIST,
    ) {
        composable(ROUTE_GAME_LIST) {
            GameListScreen(
                viewModel = viewModel,
                onOpenBorrowed = { navController.navigateToBorrowed() },
                onSignOut = { container.authManager.signOut() },
            )
        }

        composable(ROUTE_BORROWED) {
            AdminScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun NavHostController.navigateToBorrowed() {
    navigate(ROUTE_BORROWED) { launchSingleTop = true }
}
