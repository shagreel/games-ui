package ws.chill.gamecheckout.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Port of `Models/Game.swift`.
 *
 * `cover` is the remote image URL of the box art; [borrowed] is null when the
 * game is on the shelf.
 */
@Serializable
data class Game(
    val id: String,
    val name: String,
    val cover: String,
    val borrowed: BorrowedInfo? = null,
)

@Serializable
data class BorrowedInfo(
    val name: String,
    val email: String,
    val date: String,
)

/**
 * Shape of each entry returned by `GET /games/borrowed` — and, with a single
 * object, by `PUT /games/borrow`.
 */
@Serializable
data class BorrowedEntry(
    val id: String,
    val borrowed: BorrowedInfo? = null,
)

/**
 * This backend signals failures — including a bad `x-cfp` credential — with
 * HTTP 200 and this body shape instead of a 4xx status, so every response
 * needs an explicit content check rather than relying on status codes.
 *
 * See `Networking/APIClient.swift`'s private `APIErrorBody`.
 */
@Serializable
internal data class ApiErrorBody(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
) {
    val isError: Boolean get() = id == "error"
}
