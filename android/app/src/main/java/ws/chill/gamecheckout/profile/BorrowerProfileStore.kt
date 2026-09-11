package ws.chill.gamecheckout.profile

import android.content.Context

/**
 * Port of `BorrowerProfileStore.swift`.
 *
 * Remembers the last name/email used to borrow a game so the borrow form can
 * prefill itself next time. Not sensitive, so `SharedPreferences` (not
 * [ws.chill.gamecheckout.auth.SecureStore]) is fine here.
 */
class BorrowerProfileStore(context: Context) : BorrowerProfile {

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override val name: String
        get() = preferences.getString(KEY_NAME, "").orEmpty()

    override val email: String
        get() = preferences.getString(KEY_EMAIL, "").orEmpty()

    override fun save(name: String, email: String) {
        val previousName = this.name
        val previousEmail = this.email

        val editor = preferences.edit()
        if (name != previousName) editor.putString(KEY_NAME, name)
        if (email != previousEmail) editor.putString(KEY_EMAIL, email)
        editor.apply()
    }

    private companion object {
        const val PREFS_NAME = "borrower_profile"
        const val KEY_NAME = "borrowerProfile.name"
        const val KEY_EMAIL = "borrowerProfile.email"
    }
}
