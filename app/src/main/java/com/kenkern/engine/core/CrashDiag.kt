package com.kenkern.engine.core

import android.os.SystemClock
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Forensic instrumentation for the scene-entry crash investigation.
 *
 * Every stage of the open-project chain (navigation, lifecycle, orientation,
 * EGL, renderer, shaders, meshes, first frame) reports here under a single
 * stable logcat tag so a device-side failure can be pinned to an exact step
 * with wall-clock timestamp and thread name — no adb required.
 *
 * The ring buffer is embedded into crash reports and exit-reason reports, so
 * even a process death that cannot be caught (native crash, LMK kill) leaves
 * the complete execution trail in `files/logs/`.
 *
 * IMPORTANT: this class only RECORDS. It never swallows exceptions and never
 * alters control flow — a failing stage still fails exactly as before.
 */
object CrashDiag {

    /** Consistent logcat tag for the forensic investigation. */
    const val TAG: String = "KENKERN_CRASH_DIAG"

    private const val RING_CAPACITY = 300

    private val ring = ArrayDeque<String>(RING_CAPACITY)
    private val lock = Any()

    /** Boot-relative clock for ordering; wall clock for correlation. */
    @Volatile
    private var startRealtime: Long = SystemClock.elapsedRealtime()

    fun resetForNewSession() {
        synchronized(lock) { ring.clear() }
        startRealtime = SystemClock.elapsedRealtime()
        step("DIAG_RESET")
    }

    /**
     * Records a stage marker. Never throws, never changes control flow.
     * Example: `CrashDiag.step("EGL_PREFLIGHT", "ok=1 configs=3")`
     */
    fun step(stage: String, detail: String = "") {
        val elapsedMs = SystemClock.elapsedRealtime() - startRealtime
        val wall = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val thread = Thread.currentThread().name
        val line = buildString {
            append(wall)
            append(" +")
            append(elapsedMs)
            append("ms [")
            append(thread)
            append("] ")
            append(stage)
            if (detail.isNotEmpty()) {
                append(" | ")
                append(detail)
            }
        }
        try {
            Log.i(TAG, line)
        } catch (_: Exception) {
            // Logging must never alter the failure being investigated.
        }
        synchronized(lock) {
            if (ring.size >= RING_CAPACITY) ring.removeFirst()
            ring.addLast(line)
        }
    }

    /** Records a stage together with the thrown failure (full stack in logcat). */
    fun stepFailed(stage: String, throwable: Throwable) {
        step(stage, "FAILED ${throwable.javaClass.name}: ${throwable.message}")
        try {
            Log.e(TAG, "$stage FAILED — full stack", throwable)
        } catch (_: Exception) {
            // never alter the failure
        }
        synchronized(lock) {
            val stack = Log.getStackTraceString(throwable)
            if (ring.isNotEmpty() && ring.last().endsWith("FAILED ${throwable.javaClass.name}")) {
                // attach condensed stack (first frames) to the ring
                val head = stack.lineSequence().take(12).joinToString("\n")
                if (ring.size >= RING_CAPACITY) ring.removeFirst()
                ring.addLast(head)
            }
        }
    }

    /** Snapshot of the most recent diag entries (oldest first). */
    fun snapshot(): List<String> = synchronized(lock) { ring.toList() }
}
