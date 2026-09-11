@file:OptIn(ExperimentalMaterial3Api::class)

package ws.chill.gamecheckout.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ws.chill.gamecheckout.data.Game
import ws.chill.gamecheckout.ui.components.BorrowReturnSheet
import ws.chill.gamecheckout.ui.components.GameRow
import ws.chill.gamecheckout.viewmodel.GameListViewModel

const val TAG_SEARCH = "game_list_search"

/**
 * Port of `Views/GameListView.swift`.
 *
 * The loading and "couldn't load games" states appear only while there is
 * nothing to display, exactly as the Swift view gates them on
 * `filteredGames.isEmpty`. Pull-to-refresh replaces `.refreshable`, and the
 * always-visible search field replaces
 * `.searchable(placement: .navigationBarDrawer(displayMode: .always))`.
 */
@Composable
fun GameListScreen(
    viewModel: GameListViewModel,
    onOpenBorrowed: () -> Unit,
    onSignOut: () -> Unit,
) {
    val games by viewModel.filteredGames.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val searchText by viewModel.searchText.collectAsStateWithLifecycle()

    var selectedGame by remember { mutableStateOf<Game?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Game Checkout", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onOpenBorrowed) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Borrowed Games")
                    }
                },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = viewModel::onSearchTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(TAG_SEARCH),
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchTextChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
            )

            HorizontalDivider()

            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = viewModel::load,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    isLoading && games.isEmpty() -> LoadingState()

                    errorMessage != null && games.isEmpty() ->
                        EmptyState(message = errorMessage.orEmpty(), showWifiOff = true)

                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(items = games, key = { it.id }) { game ->
                            GameRow(
                                game = game,
                                modifier = Modifier
                                    .clickable { selectedGame = game }
                                    .padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    val game = selectedGame
    if (game != null) {
        val sheetState = rememberModalBottomSheetState()
        BorrowReturnSheet(
            game = game,
            sheetState = sheetState,
            initialName = viewModel.borrowerName,
            initialEmail = viewModel.borrowerEmail,
            onDismiss = { selectedGame = null },
            onSubmitBorrow = { name, email -> viewModel.borrow(game, name, email) },
            onSubmitReturn = { viewModel.returnGame(game) },
        )
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text("Loading games…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyState(
    message: String,
    showWifiOff: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (showWifiOff) {
            Icon(
                imageVector = Icons.Filled.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            text = message,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
