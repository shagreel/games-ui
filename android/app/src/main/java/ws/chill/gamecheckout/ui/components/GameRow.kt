package ws.chill.gamecheckout.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import ws.chill.gamecheckout.data.Game
import ws.chill.gamecheckout.ui.GameDates
import ws.chill.gamecheckout.ui.theme.borrowedRowColor

/**
 * Port of `Views/GameRowView.swift`.
 *
 * A borrowed game's title is struck through and shown in blue, with the
 * borrower's name, email, and borrow date underneath in a monospaced footnote —
 * the same treatment the web app applies in `src/components/list.js`.
 */
@Composable
fun GameRow(
    game: Game,
    modifier: Modifier = Modifier,
) {
    val borrowed = game.borrowed
    val isBorrowed = borrowed != null
    val accent = borrowedRowColor

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GameCover(
            coverUrl = game.cover,
            contentDescription = game.name,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(6.dp)),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = game.name,
                fontSize = 20.sp,
                textDecoration = if (isBorrowed) TextDecoration.LineThrough else null,
                color = if (isBorrowed) accent else MaterialTheme.colorScheme.onSurface,
            )

            if (borrowed != null) {
                Text(
                    text = "${borrowed.name}  ${borrowed.email}",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = accent,
                )
                Text(
                    text = GameDates.formatted(borrowed.date),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = accent,
                )
            }
        }
    }
}

/**
 * Box art with the same three states as SwiftUI's `AsyncImage` phase switch:
 * loading spinner, the image, or a placeholder on failure.
 */
@Composable
fun GameCover(
    coverUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = coverUrl,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            loading = {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            },
            error = {
                Icon(
                    imageVector = Icons.Filled.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}
