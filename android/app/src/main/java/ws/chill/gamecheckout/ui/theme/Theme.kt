package ws.chill.gamecheckout.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * The iOS app leans on the system accent color for its tint and the platform's
 * default blue for the "borrowed" styling. This mirrors that: a plain Material 3
 * scheme whose primary is the same blue used for borrowed rows.
 */

/** Same blue SwiftUI's `.blue` resolves to, used for strikethrough + borrowed text. */
val BorrowedBlue = Color(0xFF007AFF)

private val LightColors = lightColorScheme(
    primary = BorrowedBlue,
    onPrimary = Color.White,
    secondary = BorrowedBlue,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    secondary = Color(0xFF0A84FF),
)

/** The color used for a borrowed game's title, row details, and strikethrough. */
val borrowedRowColor: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF0A84FF) else BorrowedBlue

@Composable
fun GameCheckoutTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
