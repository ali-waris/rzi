package com.hc.rzi.data.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory admin session flag. Starts locked on every process start and is never persisted, so an
 * admin session ends on app restart by construction.
 */
@Singleton
class AdminSessionManager @Inject constructor() {

    private val _session = MutableStateFlow(false)
    val session: StateFlow<Boolean> = _session.asStateFlow()

    fun unlock() { _session.value = true }
    fun lock() { _session.value = false }
}
