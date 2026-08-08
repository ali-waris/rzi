package com.hc.rzi.ui.onboarding

import com.google.common.truth.Truth.assertThat
import com.hc.rzi.testutil.MainDispatcherRule
import com.hc.rzi.testutil.TestAdminRepo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class PinSetupViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repo = TestAdminRepo()
    private val viewModel = PinSetupViewModel(repo)

    @Test
    fun `valid matching pin stores and unlocks the session`() = runTest {
        viewModel.onPinChange("1234")
        viewModel.onConfirmChange("1234")

        viewModel.createPin()

        assertThat(repo.storedHash).isNotNull()
        assertThat(repo.isPinSet.first()).isTrue()
        assertThat(repo.session.first()).isTrue()
        assertThat(viewModel.state.value.error).isNull()
    }

    @Test
    fun `mismatched pins surface an error and store nothing`() = runTest {
        viewModel.onPinChange("1234")
        viewModel.onConfirmChange("9999")

        viewModel.createPin()

        assertThat(viewModel.state.value.error).isEqualTo("PINs don't match")
        assertThat(repo.storedHash).isNull()
        assertThat(repo.isPinSet.first()).isFalse()
    }

    @Test
    fun `non four digit pin surfaces the digits error`() = runTest {
        viewModel.onPinChange("12")
        viewModel.onConfirmChange("12")
        viewModel.createPin()

        assertThat(viewModel.state.value.error).isEqualTo("PIN must be 4 digits")
        assertThat(repo.storedHash).isNull()
    }

    @Test
    fun `input is filtered to at most four digits`() {
        viewModel.onPinChange("12a4567")
        assertThat(viewModel.state.value.pin).isEqualTo("1245")
    }
}
