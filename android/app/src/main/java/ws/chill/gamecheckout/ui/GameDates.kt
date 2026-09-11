package ws.chill.gamecheckout.ui

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Port of the date formatting in `GameRowView.swift` and the web app's list:
 * the API stores `yyyy-MM-dd` in UTC, and the list renders it as `MMM d`.
 * Unparseable input is returned unchanged, as on iOS.
 */
object GameDates {

    private val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = false
    }

    private val outputFormat = SimpleDateFormat("MMM d", Locale.US)

    @Synchronized
    fun formatted(isoDate: String): String {
        val parsed = try {
            inputFormat.parse(isoDate)
        } catch (e: Exception) {
            null
        } ?: return isoDate

        return outputFormat.format(parsed)
    }
}
