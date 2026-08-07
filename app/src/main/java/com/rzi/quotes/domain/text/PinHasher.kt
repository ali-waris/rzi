package com.rzi.quotes.domain.text

import java.security.MessageDigest

/**
 * Canonical identity of an admin PIN.
 *
 * The stored value is a SHA-256 hex digest of a salted PIN, never the PIN itself. The salt is a
 * fixed domain value: it stops raw hash-lookup collisions but does not defend against offline brute
 * force of a 4-digit space — the gate protects against casual changes on a shared device, not
 * against an adversary holding the app's data file (see the admin-pin-gate design, Key risks).
 */
object PinHasher {

    private const val SALT = "rzi-pin:"

    fun of(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest((SALT + pin).toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    /** Exactly four ASCII digits. */
    fun isValid(pin: String): Boolean =
        pin.length == 4 && pin.all { it in '0'..'9' }
}
