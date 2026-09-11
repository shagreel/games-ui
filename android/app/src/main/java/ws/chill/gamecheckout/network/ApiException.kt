package ws.chill.gamecheckout.network

/** Port of `enum APIError` in `Networking/APIClient.swift`. */
sealed class ApiException(message: String? = null) : Exception(message) {
    /** Non-HTTP response, or a status outside 200..<300. */
    data object InvalidResponse : ApiException()

    /** 401/403, or the backend's `{"id":"error"}` body. */
    data object Unauthorized : ApiException()
}
