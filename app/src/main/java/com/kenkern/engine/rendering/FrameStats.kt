package com.kenkern.engine.rendering

import java.util.concurrent.atomic.AtomicLong

/**
 * Frame timing statistics for the rendering loop.
 *
 * The GL thread calls [onFrame]; the UI thread polls [snapshot] at a low
 * rate (a few times per second). No per-frame allocations occur.
 */
class FrameStats {

    private val lastSampleStartNanos = AtomicLong(System.nanoTime())
    private val framesSinceSample = AtomicLong(0)

    @Volatile
    var fps: Int = 0
        private set

    @Volatile
    var averageFrameMs: Float = 0f
        private set

    fun onFrame() {
        framesSinceSample.incrementAndGet()
        val now = System.nanoTime()
        val start = lastSampleStartNanos.get()
        val elapsed = now - start
        if (elapsed >= SAMPLE_INTERVAL_NANOS) {
            val frames = framesSinceSample.get()
            if (elapsed > 0 && frames > 0) {
                fps = (frames * 1_000_000_000L / elapsed).toInt()
                averageFrameMs = (elapsed / 1_000_000f) / frames
            }
            lastSampleStartNanos.set(now)
            framesSinceSample.set(0)
        }
    }

    /** Resets accumulators (used after resume so stale numbers do not show). */
    fun reset() {
        lastSampleStartNanos.set(System.nanoTime())
        framesSinceSample.set(0)
        fps = 0
        averageFrameMs = 0f
    }

    companion object {
        private const val SAMPLE_INTERVAL_NANOS: Long = 500_000_000L
    }
}
