package com.kenkern.engine.core

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Process-wide engine runtime.
 *
 * Responsibilities (Phase 0 scope + HOTFIX forensic layer):
 *  - one-time initialization from [com.kenkern.engine.KenkernApplication],
 *  - a global uncaught-exception hook that records KENKERN crash reports to
 *    app-private storage before delegating to the platform handler,
 *  - on every start, a forensic probe of how the previous process ended
 *    ([ExitReasonProbe], ApplicationExitInfo), so native crashes / ANR /
 *    low-memory kills that no Java handler can catch are still recorded,
 *  - a stable place for future engine-wide subsystem registration.
 */
object EngineRuntime {

    private const val SUBSYSTEM = "Runtime"
    private const val CRASH_DIR = "logs"

    @Volatile
    private var initialized = false

    /** Result of the startup exit probe, consumed by the home screen. */
    @Volatile
    var lastExit: ExitReasonProbe.LastExit? = null
        private set

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            CrashDiag.resetForNewSession()
            CrashDiag.step("RUNTIME_INIT", "version=${EngineConfig.VERSION} sdk=${Build.VERSION.SDK_INT} device=${Build.MANUFACTURER}/${Build.MODEL}")
            installCrashHandler(context.applicationContext)
            initialized = true
            // Forensic probe: how did the previous run die? (API 30+)
            lastExit = ExitReasonProbe.probe(context.applicationContext)
            EngineLogger.i(SUBSYSTEM, "${EngineConfig.ENGINE_NAME} engine runtime initialized (v${EngineConfig.VERSION})")
        }
    }

    private fun installCrashHandler(appContext: Context) {
        val platformHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                CrashDiag.stepFailed("UNCAUGHT_EXCEPTION", throwable)
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
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ABI ${Build.SUPPORTED_ABIS.firstOrNull()}")
            appendLine()
            appendLine(android.util.Log.getStackTraceString(throwable))
            appendLine()
            appendLine("--- Execution trail (KENKERN_CRASH_DIAG) ---")
            CrashDiag.snapshot().forEach { appendLine(it) }
            appendLine()
            appendLine("--- Recent engine log ---")
            EngineLogger.recentEntries().forEach { appendLine(it) }
        })
        // Chain file read by ExitReasonProbe so an exit record can point at
        // the newest Java crash report even across process restarts.
        try {
            File(dir, "crash-report-chain.txt").writeText(file.name)
        } catch (_: Exception) {
            // best effort
        }
    }
}
