package com.rzi.quotes.ui.library

import com.google.common.truth.Truth.assertThat
import com.rzi.quotes.domain.text.PinHasher
import com.rzi.quotes.testutil.MainDispatcherRule
import com.rzi.quotes.testutil.TestAdminRepo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AdminPinViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repo = TestAdminRepo()

    private suspend fun viewModel(): AdminPinViewModel {
        repo.setPin("1234")
        return AdminPinViewModel(repo)
    }

    @Test
    fun `unlock with the correct pin emits Unlocked`() = runTest {
        val vm = viewModel()
        vm.start(AdminPinMode.UNLOCK)
        vm.onCurrentChange("1234")

        vm.submit()

        assertThat(vm.events.first()).isEqualTo(AdminPinEvent.Unlocked)
        assertThat(vm.state.value.currentPin).isEmpty()
        assertThat(vm.state.value.error).isNull()
    }

    @Test
    fun `unlock with a wrong pin shows an error and stays locked`() = runTest {
        val vm = viewModel()
        vm.start(AdminPinMode.UNLOCK)
        vm.onCurrentChange("9999")

        vm.submit()

        assertThat(vm.state.value.error).isEqualTo("Incorrect PIN")
        assertThat(vm.state.value.currentPin).isEmpty()
        assertThat(repo.session.first()).isFalse()
    }

    @Test
    fun `change with mismatched pins shows an error and never calls the repo`() = runTest {
        val vm = viewModel()
        vm.start(AdminPinMode.CHANGE)
        vm.onCurrentChange("1234")
        vm.onNewChange("5678")
        vm.onConfirmChange("9999")

        vm.submit()

        assertThat(vm.state.value.error).isEqualTo("PINs don't match")
        assertThat(repo.storedHash).isEqualTo(PinHasher.of("1234"))
    }

    @Test
    fun `change with a wrong current pin shows an error`() = runTest {
        val vm = viewModel()
        vm.start(AdminPinMode.CHANGE)
        vm.onCurrentChange("0000")
        vm.onNewChange("5678")
        vm.onConfirmChange("5678")

        vm.submit()

        assertThat(vm.state.value.error).isEqualTo("Incorrect PIN")
        assertThat(repo.storedHash).isEqualTo(PinHasher.of("1234"))
    }

    @Test
    fun `valid change emits Changed and the new pin is active`() = runTest {
        val vm = viewModel()
        vm.start(AdminPinMode.CHANGE)
        vm.onCurrentChange("1234")
        vm.onNewChange("5678")
        vm.onConfirmChange("5678")

        vm.submit()

        assertThat(vm.events.first()).isEqualTo(AdminPinEvent.Changed)
        assertThat(repo.unlock("5678")).isTrue()
    }
}
