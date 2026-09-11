package ws.chill.gamecheckout.network

import ws.chill.gamecheckout.BuildConfig

/**
 * Port of `Networking/Config.swift`.
 *
 * Both URLs come from `BuildConfig`, which `app/build.gradle.kts` fills from
 * `-PAPI_BASE_URL` / `-PCATALOG_URL`, `local.properties`, or the defaults below
 * — the Android equivalent of the iOS project's `Configs xcconfig files`.
 */
object Config {
    /** Public board game catalog, same source the web app uses. */
    val catalogUrl: String = BuildConfig.CATALOG_URL

    /**
     * Backend that tracks borrow/return state.
     *
     * iOS `fatalError`s when this is missing; on Android an unusable base URL
     * would take the whole process down at first request, so it degrades to
     * the default instead.
     */
    val apiBaseUrl: String = normalizeApiBaseUrl(BuildConfig.API_BASE_URL)
}

internal fun normalizeApiBaseUrl(raw: String): String {
    val trimmed = raw.trim()
    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "https://$trimmed"
    }
    return withScheme.trimEnd('/')
}
