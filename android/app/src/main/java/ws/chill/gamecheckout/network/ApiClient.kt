package ws.chill.gamecheckout.network

import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import ws.chill.gamecheckout.data.BorrowedEntry
import ws.chill.gamecheckout.data.BorrowedInfo
import ws.chill.gamecheckout.data.Game

/**
 * Port of `Networking/APIClient.swift`.
 *
 * Every `/games/...` call carries the `x-cfp` header (the sha256 of the shared
 * password); the catalog fetch is public and unauthenticated.
 */
open class ApiClient(
    private val client: OkHttpClient = defaultClient(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val gameListSerializer = ListSerializer(Game.serializer())
    private val borrowedEntryListSerializer = ListSerializer(BorrowedEntry.serializer())

    /** `GET {catalog}` — the public board game catalog, no auth needed. */
    open suspend fun fetchCatalog(): List<Game> {
        val request = Request.Builder().url(Config.catalogUrl).get().build()
        val body = execute(request)
        return json.decodeFromString(gameListSerializer, body)
    }

    /** `GET {api}/games/borrowed` */
    open suspend fun fetchBorrowed(authHeader: String): List<BorrowedEntry> {
        val request = Request.Builder()
            .url("${Config.apiBaseUrl}/games/borrowed")
            .header("x-cfp", authHeader)
            .get()
            .build()
        val body = execute(request)
        throwIfErrorBody(body)
        return json.decodeFromString(borrowedEntryListSerializer, body)
    }

    /** `PUT {api}/games/borrow` — returns the freshly created [BorrowedInfo]. */
    open suspend fun borrowGame(
        id: String,
        name: String,
        email: String,
        authHeader: String,
    ): BorrowedInfo? {
        val payload = buildJsonObject {
            put("id", id)
            put(
                "borrowed",
                buildJsonObject {
                    put("name", name)
                    put("email", email)
                    put("date", todayDateString())
                },
            )
        }
        val request = Request.Builder()
            .url("${Config.apiBaseUrl}/games/borrow")
            .header("x-cfp", authHeader)
            .put(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val body = execute(request)
        throwIfErrorBody(body)
        return json.decodeFromString(BorrowedEntry.serializer(), body).borrowed
    }

    /** `PUT {api}/games/return` — response body is ignored, as on iOS. */
    open suspend fun returnGame(id: String, authHeader: String) {
        val payload = buildJsonObject { put("id", id) }
        val request = Request.Builder()
            .url("${Config.apiBaseUrl}/games/return")
            .header("x-cfp", authHeader)
            .put(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val body = execute(request)
        throwIfErrorBody(body)
    }

    /** Runs the request off the main thread and returns the raw body. */
    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        val response: Response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw ApiException.InvalidResponse
        }
        response.use {
            validate(it.code)
            it.body?.string().orEmpty()
        }
    }

    private fun validate(statusCode: Int) {
        if (statusCode == 401 || statusCode == 403) throw ApiException.Unauthorized
        if (statusCode !in 200..299) throw ApiException.InvalidResponse
    }

    /**
     * Detects this backend's `{"id":"error", ...}` failure shape (returned with
     * HTTP 200) and surfaces it the same way a real 401/403 would.
     */
    private fun throwIfErrorBody(body: String) {
        val parsed = try {
            json.parseToJsonElement(body) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return

        val id = (parsed["id"] as? JsonPrimitive)?.content
        if (id == "error") throw ApiException.Unauthorized
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /**
         * Close to `URLSession.shared`'s default request timeout (60s), which
         * is ample for this app's small payloads.
         */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        /** Mirrors `APIClient.todayDateString()`: `yyyy-MM-dd` in the local zone. */
        fun todayDateString(now: Date = Date()): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
    }
}
