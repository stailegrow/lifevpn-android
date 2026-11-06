package com.stailegrow.lifevpn.vpn

/** Serializes native state mutations, including synchronous service destruction. */
internal class TunnelLifecycle {
    private val lock = Any()
    private var generation = 0L
    private var destroyed = false

    fun advance(): Long = synchronized(lock) { ++generation }

    fun runIfCurrent(token: Long, action: () -> Unit): Boolean = synchronized(lock) {
        if (destroyed || token != generation) return false
        action()
        true
    }

    fun destroy(cleanup: () -> Unit) = synchronized(lock) {
        if (!destroyed) {
            destroyed = true
            generation++
            cleanup()
        }
    }
}
