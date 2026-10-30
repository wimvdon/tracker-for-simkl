package to.wim.watchlist.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MoveKeysMigrationTest {
    @get:Rule val folder = TemporaryFolder()

    private val theme = stringPreferencesKey("theme")
    private val hideSpecials = booleanPreferencesKey("hide_specials")
    private val token = stringPreferencesKey("access_token")

    private val old = PreferenceDataStoreFactory.create { folder.root.resolve("old.preferences_pb") }

    private fun target(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(migrations = listOf(MoveKeysMigration(old, setOf(theme, hideSpecials)))) {
            folder.root.resolve("new.preferences_pb")
        }

    @Test
    fun `moves the listed keys and leaves the rest`() =
        runBlocking {
            old.edit {
                it[theme] = "Dark"
                it[hideSpecials] = true
                it[token] = "secret"
            }

            val moved = target().data.first()

            assertEquals("Dark", moved[theme])
            assertEquals(true, moved[hideSpecials])
            assertNull(moved[token])
            val left = old.data.first()
            assertNull(left[theme])
            assertNull(left[hideSpecials])
            assertEquals("secret", left[token])
        }

    @Test
    fun `a fresh install starts empty`() =
        runBlocking {
            assertEquals(0, target().data.first().asMap().size)
        }

    @Test
    fun `a value already in the new file is kept`() =
        runBlocking {
            old.edit { it[theme] = "Dark" }

            val result = MoveKeysMigration(old, setOf(theme)).migrate(preferencesOf(theme to "Light"))

            assertEquals("Light", result[theme])
        }

    @Test
    fun `nothing to move after the first time`() =
        runBlocking {
            old.edit { it[token] = "secret" }

            assertFalse(MoveKeysMigration(old, setOf(theme, hideSpecials)).shouldMigrate(preferencesOf()))
        }
}
