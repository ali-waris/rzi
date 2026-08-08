package com.hc.rzi.domain.repository

import com.hc.rzi.domain.model.PinError
import kotlinx.coroutines.flow.Flow

/**
 * Port for admin authentication state. The persisted PIN is exposed only as [isPinSet]; the
 * unlocked session is [session]. All validation and hashing happens behind this interface.
 */
interface AdminRepository {

    /** Whether a PIN has ever been created (drives the first-launch gate). */
    val isPinSet: Flow<Boolean>

    /** Whether the current process has an unlocked admin session. In-memory, resets on restart. */
    val session: Flow<Boolean>

    /** null on success, [PinError.INVALID_DIGITS] when [pin] is not exactly four digits. */
    suspend fun setPin(pin: String): PinError?

    /**
     * null on success; [PinError.INVALID_DIGITS] when either pin is malformed; [PinError.INCORRECT]
     * when [oldPin] does not match the stored hash.
     */
    suspend fun changePin(oldPin: String, newPin: String): PinError?

    /** True when [pin] matches and the session is now unlocked. False on wrong, malformed, or unset. */
    suspend fun unlock(pin: String): Boolean

    /** Ends the admin session. The stored PIN is untouched. */
    fun lock()
}
