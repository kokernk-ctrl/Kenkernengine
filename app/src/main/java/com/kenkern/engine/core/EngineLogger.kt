package com.kenkern.engine.core

import android.util.Log
import java.util.ArrayDeque

/**
 * Centralized logging facade for the engine.
 *
 * All engine subsystems log through this object instead of calling
 * [Log] directly, so that:
 *  - a consistent `KENKERN.` tag namespace is used,
 *  - a bounded ring buffer of recent entries is kept for on-device
 *    diagnostics (used by the editor and by crash reports).
 */
object EngineLogger {

    private const val BASE_TAG = "KENKERN"
    private const val RING_CAPACITY = 250

    private val ring = ArrayDeque<String>(RING_CAPACITY)
    private val ringLock = Any()

    fun i(subsystem: String, message: String) = log(Log.INFO, subsystem, message, null)

    fun w(subsystem: String, message: String) = log(Log.WARN, subsystem, message, null)

    fun e(subsystem: String, message: String, throwable: Throwable? = null) =
        log(Log.ERROR, subsystem, message, throwable)

    private fun log(priority: Int, subsystem: String, message: String, throwable: Throwable?) {
        val tag = "$BASE_TAG.$subsystem"
        try {
            when (priority) {
                Log.ERROR -> Log.e(tag, message, throwable)
                Log.WARN -> Log.w(tag, message, throwable)
                else -> Log.i(tag, message, throwable)
            }
        } catch (_: Exception) {
            // Logging must never crash the engine, even in exotic runtimes.
        }
        val line = when {
            throwable != null -> "[${levelLabel(priority)}/$subsystem] $message :: ${throwable.javaClass.simpleName}: ${throwable.message}"
            else -> "[${levelLabel(priority)}/$subsystem] $message"
        }
        synchronized(ringLock) {
            if (ring.size >= RING_CAPACITY) ring.removeFirst()
            ring.addLast(line)
        }
    }

    /** Returns a snapshot of the most recent log lines, oldest first. */
    fun recentEntries(): List<String> = synchronized(ringLock) { ring.toList() }

    private fun levelLabel(priority: Int) = when (priority) {
        Log.ERROR -> "E"
        Log.WARN -> "W"
        else -> "I"
    }
}
