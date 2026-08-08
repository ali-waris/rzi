package com.hc.rzi.data.repository

import com.hc.rzi.data.prefs.AdminSettingsStore
import com.hc.rzi.data.session.AdminSessionManager
import com.hc.rzi.di.IoDispatcher
import com.hc.rzi.domain.model.PinError
import com.hc.rzi.domain.repository.AdminRepository
import com.hc.rzi.domain.text.PinHasher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepositoryImpl @Inject constructor(
    private val settings: AdminSettingsStore,
    private val sessionManager: AdminSessionManager,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AdminRepository {

    override val isPinSet: Flow<Boolean> = settings.pinHash.map { it != null }
    override val session: Flow<Boolean> = sessionManager.session

    override suspend fun setPin(pin: String): PinError? {
        if (!PinHasher.isValid(pin)) return PinError.INVALID_DIGITS
        withContext(io) { settings.savePinHash(PinHasher.of(pin)) }
        return null
    }

    override suspend fun changePin(oldPin: String, newPin: String): PinError? {
        if (!PinHasher.isValid(oldPin) || !PinHasher.isValid(newPin)) return PinError.INVALID_DIGITS
        val stored = settings.pinHash.first() ?: return PinError.INVALID_DIGITS
        return withContext(io) {
            if (PinHasher.of(oldPin) != stored) {
                PinError.INCORRECT
            } else {
                settings.savePinHash(PinHasher.of(newPin))
                null
            }
        }
    }

    override suspend fun unlock(pin: String): Boolean {
        if (!PinHasher.isValid(pin)) return false
        val stored = settings.pinHash.first() ?: return false
        val ok = withContext(io) { PinHasher.of(pin) == stored }
        if (ok) sessionManager.unlock()
        return ok
    }

    override fun lock() = sessionManager.lock()
}
