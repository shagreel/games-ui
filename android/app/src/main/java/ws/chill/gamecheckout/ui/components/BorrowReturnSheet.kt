package ws.chill.gamecheckout.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ws.chill.gamecheckout.analytics.Tracker
import ws.chill.gamecheckout.data.Game

/**
 * Port of `Views/BorrowReturnSheet.swift`.
 *
 * Shows the current borrower for an already-borrowed game, or a prefilled
 * name/email form otherwise. On success the sheet dismisses; on failure it
 * stays open with the same generic error string iOS shows. iOS presents this at
 * the `.medium` detent, which maps to a partially-expanded bottom sheet here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorrowReturnSheet(
    game: Game,
    sheetState: SheetState,
    initialName: String,
    initialEmail: String,
    onDismiss: () -> Unit,
    onSubmitBorrow: suspend (name: String, email: String) -> String?,
    onSubmitReturn: suspend () -> String?,
) {
    val isBorrowed = game.borrowed != null
    val scope = rememberCoroutineScope()

    var name by rememberSaveable(game.id) { mutableStateOf(initialName) }
    var email by rememberSaveable(game.id) { mutableStateOf(initialEmail) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Mirrors `.onAppear { if !isBorrowed { Tracker.trackViewed(game:) } }`.
    LaunchedEffect(game.id) {
        if (!isBorrowed) Tracker.trackViewed(game.name)
    }

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    fun submit() {
        if (isSubmitting) return
        if (!isBorrowed && (name.isEmpty() || email.isEmpty())) return

        isSubmitting = true
        errorMessage = null
        scope.launch {
            val error = if (isBorrowed) {
                onSubmitReturn()
            } else {
                onSubmitBorrow(name, email)
            }

            if (error == null) {
                dismiss()
            } else {
                errorMessage = error
                isSubmitting = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "${if (isBorrowed) "Return" else "Borrow"} ${game.name}",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )

            val borrowed = game.borrowed
            if (borrowed != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledValue("Borrowed by", borrowed.name)
                    LabeledValue("Email", borrowed.email)
                    LabeledValue("Date", borrowed.date)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Full Name") },
                        singleLine = true,
                        enabled = !isSubmitting,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Email") },
                        singleLine = true,
                        enabled = !isSubmitting,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done,
                        ),
                    )
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage.orEmpty(),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { dismiss() },
                    enabled = !isSubmitting,
                ) {
                    Text("Cancel")
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = { submit() },
                    enabled = !isSubmitting && (isBorrowed || (name.isNotEmpty() && email.isNotEmpty())),
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(if (isBorrowed) "Return" else "Borrow")
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.width(110.dp),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontSize = 15.sp,
            fontFamily = FontFamily.Default,
        )
    }
}
