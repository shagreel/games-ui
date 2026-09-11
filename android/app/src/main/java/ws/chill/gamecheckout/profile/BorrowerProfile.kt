package ws.chill.gamecheckout.profile

/**
 * The name/email remembered between borrows. See [BorrowerProfileStore] for the
 * `SharedPreferences` implementation; the interface keeps
 * [ws.chill.gamecheckout.repository.GameRepository] free of `Context`.
 */
interface BorrowerProfile {
    val name: String
    val email: String

    fun save(name: String, email: String)
}
