package ws.chill.gamecheckout.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ws.chill.gamecheckout.data.BorrowedInfo
import ws.chill.gamecheckout.data.Game

/**
 * Covers the merge of `applyFilter()` in `GameListViewModel.swift`: a blank
 * query passes everything through, and otherwise the game name, borrower name,
 * and borrower email are all searched, case-insensitively.
 */
class GameSearchTest {

    private fun game(
        id: String,
        name: String,
        borrowerName: String? = null,
        borrowerEmail: String? = null,
    ) = Game(
        id = id,
        name = name,
        cover = "https://example.com/$id.png",
        borrowed = if (borrowerName == null && borrowerEmail == null) {
            null
        } else {
            BorrowedInfo(
                name = borrowerName.orEmpty(),
                email = borrowerEmail.orEmpty(),
                date = "2024-03-04",
            )
        },
    )

    private val catalog = listOf(
        game("1", "Catan"),
        game("2", "Ticket to Ride", borrowerName = "Ada Lovelace", borrowerEmail = "ada@example.com"),
        game("3", "Wingspan", borrowerName = "Grace Hopper", borrowerEmail = "grace@example.com"),
        game("4", "Azul"),
    )

    @Test
    fun `blank query returns every game in order`() {
        assertEquals(catalog, GameSearch.filter(catalog, ""))
        assertEquals(catalog, GameSearch.filter(catalog, "   "))
    }

    @Test
    fun `matches game name case-insensitively`() {
        assertEquals(listOf("1"), GameSearch.filter(catalog, "catan").map { it.id })
        assertEquals(listOf("1"), GameSearch.filter(catalog, "CATAN").map { it.id })
    }

    @Test
    fun `matches a substring anywhere in the name`() {
        assertEquals(listOf("2"), GameSearch.filter(catalog, "to rid").map { it.id })
    }

    @Test
    fun `matches the borrower name`() {
        assertEquals(listOf("3"), GameSearch.filter(catalog, "grace").map { it.id })
    }

    @Test
    fun `matches the borrower email`() {
        assertEquals(listOf("2"), GameSearch.filter(catalog, "ada@example").map { it.id })
    }

    @Test
    fun `does not match an unborrowed game on borrower fields`() {
        assertTrue(GameSearch.filter(catalog, "lovelace").none { it.id == "1" })
    }

    @Test
    fun `trims surrounding whitespace from the query`() {
        assertEquals(listOf("4"), GameSearch.filter(catalog, "  azul  ").map { it.id })
    }

    @Test
    fun `returns nothing when no game matches`() {
        assertTrue(GameSearch.filter(catalog, "monopoly").isEmpty())
    }
}
