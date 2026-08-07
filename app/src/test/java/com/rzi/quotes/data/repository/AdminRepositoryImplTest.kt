package com.rzi.quotes.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.rzi.quotes.data.prefs.AdminSettingsStore
import com.rzi.quotes.data.session.AdminSessionManager
import com.rzi.quotes.domain.model.PinError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdminRepositoryImplTest {

    @get:Rule val temp = TemporaryFolder()

    private lateinit var store: AdminSettingsStore
    private lateinit var session: AdminSessionManager

    @org.junit.Before
    fun setUp() {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO),
            produceFile = { File(temp.newFolder("prefs"), "admin.preferences_pb") },
        )
        store = AdminSettingsStore(dataStore)
        session = AdminSessionManager()
    }

    private fun repo(): AdminRepositoryImpl {
        val io: CoroutineDispatcher = UnconfinedTestDispatcher()
        return AdminRepositoryImpl(store, session, io)
    }

    @Test
    fun `set pin then unlock round trips and unlocks the session`() = runTest {
        val repo = repo()
        assertThat(repo.setPin("1234")).isNull()
        assertThat(repo.isPinSet.first()).isTrue()
        assertThat(repo.session.first()).isFalse()

        assertThat(repo.unlock("1234")).isTrue()
        assertThat(repo.session.first()).isTrue()
    }

    @Test
    fun `wrong pin returns false and does not unlock`() = runTest {
        val repo = repo()
        repo.setPin("1234")

        assertThat(repo.unlock("9999")).isFalse()
        assertThat(repo.session.first()).isFalse()
    }

    @Test
    fun `change pin rejects a wrong current pin`() = runTest {
        val repo = repo()
        repo.setPin("1234")

        assertThat(repo.changePin("0000", "5678")).isEqualTo(PinError.INCORRECT)
        assertThat(repo.unlock("1234")).isTrue()
        assertThat(repo.unlock("5678")).isFalse()
    }

    @Test
    fun `change pin with the correct current pin succeeds`() = runTest {
        val repo = repo()
        repo.setPin("1234")

        assertThat(repo.changePin("1234", "5678")).isNull()
        assertThat(repo.unlock("5678")).isTrue()
        assertThat(repo.unlock("1234")).isFalse()
    }

    @Test
    fun `lock ends the session but keeps the pin`() = runTest {
        val repo = repo()
        repo.setPin("1234")
        repo.unlock("1234")
        assertThat(repo.session.first()).isTrue()

        repo.lock()

        assertThat(repo.session.first()).isFalse()
        assertThat(repo.isPinSet.first()).isTrue()
        assertThat(repo.unlock("1234")).isTrue()
    }

    @Test
    fun `unlock with no pin set returns false`() = runTest {
        val repo = repo()
        assertThat(repo.isPinSet.first()).isFalse()
        assertThat(repo.unlock("1234")).isFalse()
        assertThat(repo.session.first()).isFalse()
    }

    @Test
    fun `malformed pins are rejected without touching state`() = runTest {
        val repo = repo()
        assertThat(repo.setPin("12a4")).isEqualTo(PinError.INVALID_DIGITS)
        assertThat(repo.setPin("123")).isEqualTo(PinError.INVALID_DIGITS)
        assertThat(repo.isPinSet.first()).isFalse()
    }
}
