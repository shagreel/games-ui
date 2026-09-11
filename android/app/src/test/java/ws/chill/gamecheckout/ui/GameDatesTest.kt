package ws.chill.gamecheckout.ui

import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Mirrors `GameRowView.formattedDate` in the iOS port: the API's `yyyy-MM-dd`
 * (UTC) renders as `MMM d`, and anything unparseable is passed through.
 */
class GameDatesTest {

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun captureTimeZone() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `formats an ISO date as month and day`() {
        assertEquals("Mar 4", GameDates.formatted("2024-03-04"))
    }

    @Test
    fun `formats single-digit months and days without padding`() {
        assertEquals("Jan 9", GameDates.formatted("2024-01-09"))
    }

    @Test
    fun `passes through unparseable input unchanged`() {
        assertEquals("not-a-date", GameDates.formatted("not-a-date"))
        assertEquals("", GameDates.formatted(""))
    }

    @Test
    fun `renders UTC dates without a local-timezone shift`() {
        assertEquals("Dec 31", GameDates.formatted("2024-12-31"))
    }
}
