package com.get.detail.rentdesk.lock

import android.os.SystemClock

object AppLockSession {
    private var unlocked = false
    private var backgroundedAt: Long? = null
    private var internalAuthentication = false

    fun markUnlocked() {
        unlocked = true
        backgroundedAt = null
    }

    fun markBackgrounded() {
        if (!internalAuthentication) {
            backgroundedAt = SystemClock.elapsedRealtime()
        }
    }

    fun markForegrounded() {
        backgroundedAt = null
    }

    fun requiresUnlock(timeoutMillis: Long): Boolean {
        if (!unlocked) return true
        val leftAt = backgroundedAt ?: return false
        return SystemClock.elapsedRealtime() - leftAt >= timeoutMillis
    }

    fun beginInternalAuthentication() {
        internalAuthentication = true
    }

    fun endInternalAuthentication() {
        internalAuthentication = false
    }

    fun isInternalAuthentication(): Boolean = internalAuthentication
}
