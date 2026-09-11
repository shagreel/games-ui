package ws.chill.gamecheckout.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ws.chill.gamecheckout.auth.AuthSession
import ws.chill.gamecheckout.data.BorrowedEntry
import ws.chill.gamecheckout.data.BorrowedInfo
import ws.chill.gamecheckout.data.Game
import ws.chill.gamecheckout.network.ApiClient
import ws.chill.gamecheckout.network.ApiException
import ws.chill.gamecheckout.profile.BorrowerProfile

/**
 * Covers the catalog/borrowed merge in `GameListViewModel.load()`:
 * `{id, borrowed}` entries are applied onto the catalog by id, a failing
 * borrowed request degrades to catalog-only, and a rejected credential signs
 * the user out.
 */
class GameRepositoryTest {

    private val catalog = listOf(
        Game(id = "1", name = "Catan", cover = "c1"),
        Game(id = "2", name = "Azul", cover = "c2"),
        Game(id = "3", name = "Wingspan", cover = "c3"),
    )

    private class FakeApi(
        private val catalog: List<Game>,
        private val borrowed: Result<List<BorrowedEntry>>,
    ) : ApiClient() {
        var borrowCalls = 0
        var returnCalls = 0

        override suspend fun fetchCatalog(): List<Game> = catalog

        override suspend fun fetchBorrowed(authHeader: String): List<BorrowedEntry> =
            borrowed.getOrThrow()

        override suspend fun borrowGame(
            id: String,
            name: String,
            email: String,
            authHeader: String,
        ): BorrowedInfo? {
            borrowCalls++
            return BorrowedInfo(name = name, email = email, date = "2024-05-06")
        }

        override suspend fun returnGame(id: String, authHeader: String) {
            returnCalls++
        }
    }

    private class FakeAuth(override var authHeaderValue: String? = "hash") : AuthSession {
        var unauthorizedHandled = false
        override fun handleUnauthorized() {
            unauthorizedHandled = true
            authHeaderValue = null
        }
    }

    private class FakeProfile : BorrowerProfile {
        override var name: String = ""
        override var email: String = ""
        var saveCalls = 0

        override fun save(name: String, email: String) {
            saveCalls++
            this.name = name
            this.email = email
        }
    }

    private fun repository(
        api: ApiClient,
        auth: AuthSession = FakeAuth(),
        profile: BorrowerProfile = FakeProfile(),
    ) = GameRepository(authManager = auth, api = api, profileStore = profile)

    @Test
    fun `merges borrowed entries onto the catalog by id`() = runTest {
        val api = FakeApi(
            catalog = catalog,
            borrowed = Result.success(
                listOf(
                    BorrowedEntry("2", BorrowedInfo("Ada", "ada@example.com", "2024-03-04")),
                    BorrowedEntry("3", null),
                ),
            ),
        )
        val repository = repository(api)

        repository.load()

        val byId = repository.games.value.associateBy { it.id }
        assertEquals("Ada", byId.getValue("2").borrowed?.name)
        assertNull(byId.getValue("1").borrowed)
        assertNull(byId.getValue("3").borrowed)
        assertFalse(repository.isLoading.value)
    }

    @Test
    fun `keeps the catalog when the borrowed request fails for another reason`() = runTest {
        val api = FakeApi(catalog, Result.failure(ApiException.InvalidResponse))
        val auth = FakeAuth()
        val repository = repository(api, auth)

        repository.load()

        assertEquals(3, repository.games.value.size)
        assertTrue(repository.games.value.all { it.borrowed == null })
        assertFalse(auth.unauthorizedHandled)
        assertNull(repository.errorMessage.value)
    }

    @Test
    fun `signs out and shows no catalog when the borrowed request is rejected`() = runTest {
        val api = FakeApi(catalog, Result.failure(ApiException.Unauthorized))
        val auth = FakeAuth()
        val repository = repository(api, auth)

        repository.load()

        assertTrue(auth.unauthorizedHandled)
        assertTrue(repository.games.value.isEmpty())
    }

    @Test
    fun `load is a no-op without a credential`() = runTest {
        val api = FakeApi(catalog, Result.success(emptyList()))
        val repository = repository(api, FakeAuth(authHeaderValue = null))

        repository.load()

        assertTrue(repository.games.value.isEmpty())
    }

    @Test
    fun `borrow stores the returned info, remembers the borrower, and updates locally`() =
        runTest {
            val api = FakeApi(catalog, Result.success(emptyList()))
            val profile = FakeProfile()
            val repository = repository(api, profile = profile)
            repository.load()

            val target = repository.games.value.first { it.id == "1" }
            repository.borrow(target, name = "Ada", email = "ada@example.com")

            val updated = repository.games.value.first { it.id == "1" }
            assertEquals("Ada", updated.borrowed?.name)
            assertEquals("ada@example.com", updated.borrowed?.email)
            assertEquals(1, api.borrowCalls)
            assertEquals(1, profile.saveCalls)
            assertEquals("Ada", profile.name)
        }

    @Test
    fun `return clears borrowed state locally`() = runTest {
        val api = FakeApi(
            catalog = catalog,
            borrowed = Result.success(
                listOf(BorrowedEntry("1", BorrowedInfo("Ada", "ada@example.com", "2024-03-04"))),
            ),
        )
        val repository = repository(api)
        repository.load()

        val target = repository.games.value.first { it.id == "1" }
        repository.returnGame(target)

        assertNull(repository.games.value.first { it.id == "1" }.borrowed)
        assertEquals(1, api.returnCalls)
    }
}
