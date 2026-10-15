package io.github.impca201.trackerforsimkl.data

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * Moves [keys] from [source] into the DataStore this migration is attached to, then deletes them
 * from [source]. Used once, when the settings moved to their own (backed-up) file. A value
 * already in the target wins over the old one.
 */
class MoveKeysMigration(
    private val source: DataStore<Preferences>,
    private val keys: Set<Preferences.Key<*>>,
) : DataMigration<Preferences> {
    private suspend fun old(): Preferences =
        try {
            source.data.first()
        } catch (e: IOException) {
            emptyPreferences()
        }

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = old().asMap().keys.any { it in keys }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val moved = currentData.toMutablePreferences()
        old().asMap().forEach { (key, value) ->
            if (key in keys && key !in moved) {
                @Suppress("UNCHECKED_CAST")
                moved[key as Preferences.Key<Any>] = value
            }
        }
        return moved.toPreferences()
    }

    override suspend fun cleanUp() {
        source.edit { prefs -> keys.forEach { prefs.remove(it) } }
    }
}
