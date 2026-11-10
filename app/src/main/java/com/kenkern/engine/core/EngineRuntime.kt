package com.kenkern.engine.core

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Process-wide engine runtime.
 *
 * Responsibilities (Phase 0 scope):
 *  - one-time initialization from [com.kenkern.engine.KenkernApplication],
 *  - a global uncaught-exception hook that records KENKERN crash reports to
 *    app-private storage before delegating to the platform handler,
 *  - a stable place for future engine-wide subsystem registration.
 */
object EngineRuntime {

    private const val SUBSYSTEM = "Runtime"
    private const val CRASH_DIR = "logs"

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            installCrashHandler(context.applicationContext)
            initialized = true
            EngineLogger.i(SUBSYSTEM, "${EngineConfig.ENGINE_NAME} engine runtime initialized (v${EngineConfig.VERSION})")
        }
    }

    private fun installCrashHandler(appContext: Context) {
        val platformHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashReport(appContext, thread, throwable)
            } catch (_: Exception) {
                // Best effort only; never mask the original crash.
            }
            EngineLogger.e(SUBSYSTEM, "Uncaught exception on thread ${thread.name}", throwable)
            platformHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashReport(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, CRASH_DIR).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash-$stamp.txt")
        file.writeText(buildString {
            appendLine("${EngineConfig.ENGINE_NAME} crash report ${EngineConfig.VERSION}")
            appendLine("Thread: ${thread.name}")
            appendLine("Time: ${Date()}")
            appendLine()
            appendLine(android.util.Log.getStackTraceString(throwable))
            appendLine()
            appendLine("--- Recent engine log ---")
            EngineLogger.recentEntries().forEach { appendLine(it) }
        })
    }
}
