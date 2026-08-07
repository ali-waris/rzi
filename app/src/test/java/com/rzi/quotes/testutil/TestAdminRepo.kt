package com.rzi.quotes.testutil

import com.rzi.quotes.domain.model.PinError
import com.rzi.quotes.domain.repository.AdminRepository
import com.rzi.quotes.domain.text.PinHasher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory fake of [AdminRepository] for ViewModel tests. Behaves like the real impl. */
class TestAdminRepo : AdminRepository {

    private val _isPinSet = MutableStateFlow(false)
    override val isPinSet: Flow<Boolean> = _isPinSet

    private val _session = MutableStateFlow(false)
    override val session: Flow<Boolean> = _session

    var storedHash: String? = null
        private set
    var unlockCalls: Int = 0
        private set

    override suspend fun setPin(pin: String): PinError? {
        if (!PinHasher.isValid(pin)) return PinError.INVALID_DIGITS
        storedHash = PinHasher.of(pin)
        _isPinSet.value = true
        return null
    }

    override suspend fun changePin(oldPin: String, newPin: String): PinError? {
        if (!PinHasher.isValid(oldPin) || !PinHasher.isValid(newPin)) return PinError.INVALID_DIGITS
        if (PinHasher.of(oldPin) != storedHash) return PinError.INCORRECT
        storedHash = PinHasher.of(newPin)
        return null
    }

    override suspend fun unlock(pin: String): Boolean {
        unlockCalls++
        val ok = storedHash != null && PinHasher.of(pin) == storedHash
        if (ok) _session.value = true
        return ok
    }

    override fun lock() {
        _session.value = false
    }
}
