package com.hc.rzi.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persisted admin settings. Reuses the app's single [DataStore] preferences file; the stored value
 * is the hashed PIN (see `PinHasher`), never the plaintext.
 */
@Singleton
class AdminSettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val pinHash: Flow<String?> = dataStore.data.map { prefs -> prefs[PIN_HASH] }

    suspend fun savePinHash(hash: String) {
        dataStore.edit { prefs -> prefs[PIN_HASH] = hash }
    }

    private companion object {
        val PIN_HASH = stringPreferencesKey("admin_pin_hash")
    }
}
