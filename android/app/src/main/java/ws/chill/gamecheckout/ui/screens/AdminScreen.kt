package ws.chill.gamecheckout.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ws.chill.gamecheckout.analytics.Tracker
import ws.chill.gamecheckout.data.Game
import ws.chill.gamecheckout.viewmodel.GameListViewModel

/**
 * Port of `Views/AdminView.swift` — the `/borrowed` equivalent: game, borrower,
 * email, and date for everything currently checked out. It reads the same
 * already-loaded list as the catalog screen instead of making a second network
 * round-trip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: GameListViewModel,
    onBack: () -> Unit,
) {
    val borrowedGames by viewModel.borrowedGames.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { Tracker.trackPageView("Borrowed List") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Borrowed Games") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        if (borrowedGames.isEmpty()) {
            EmptyBorrowedState(modifier)
        } else {
            LazyColumn(modifier = modifier) {
                items(items = borrowedGames, key = { it.id }) { game ->
                    BorrowedRow(game)
                }
            }
        }
    }
}

@Composable
private fun BorrowedRow(game: Game) {
    val borrowed = game.borrowed ?: return
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = game.name,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = borrowed.name, fontSize = 15.sp)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = borrowed.date,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = borrowed.email,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable {
                // Equivalent of the SwiftUI Link(mailto:) — a no-op when no mail
                // client can handle the intent; the address stays on screen.
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${borrowed.email}"))
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    // No mail app installed.
                }
            },
        )
    }
}

@Composable
private fun EmptyBorrowedState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "No games are currently borrowed.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
