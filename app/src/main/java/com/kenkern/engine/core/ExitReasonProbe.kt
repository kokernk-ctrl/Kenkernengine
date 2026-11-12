package com.kenkern.engine.core

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.kenkern.engine.R
// note: java.io.File / text / Date / Locale used below
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Forensic probe for HOW the previous KENKERN process died.
 *
 * Uses [android.app.ApplicationExitInfo] (API 30+; Android 13 devices fully
 * supported) to read the recorded exit record of the previous run of this
 * application. This is the authoritative answer to "why did the app close"
 * for process deaths that no Java handler can catch: native crashes in the
 * GL driver, ANR kills, low-memory kills, signal deaths.
 *
 * The result is:
 *  - logged under [CrashDiag.TAG],
 *  - persisted to `files/logs/last-exit.txt` (plus the crash trace when the
 *    platform provides one),
 *  - exposed to the home screen so the user can read the reason on the
 *    device itself and report it back — no adb needed.
 *
 * Normal exits (user swipes the app away, exits by request) are recorded but
 * do not raise the diagnostic banner.
 */
object ExitReasonProbe {

    /** Why the previous run ended, in a form the UI can present. */
    data class LastExit(
        val reasonCode: Int,
        val reasonTag: String,
        val abnormal: Boolean,
        val description: String,
        val endedAtMillis: Long,
        val pid: Int
    )

    /** Human-readable, non-localized reason tag (used in files and logs). */
    fun reasonTag(code: Int): String = when (code) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        else -> "UNKNOWN($code)"
    }

    /** Localized UI label for the reason (falls back to the raw tag). */
    fun reasonLabel(context: Context, code: Int): String = when (code) {
        ApplicationExitInfo.REASON_CRASH -> context.getString(R.string.exit_reason_crash)
        ApplicationExitInfo.REASON_CRASH_NATIVE -> context.getString(R.string.exit_reason_crash_native)
        ApplicationExitInfo.REASON_ANR -> context.getString(R.string.exit_reason_anr)
        ApplicationExitInfo.REASON_LOW_MEMORY -> context.getString(R.string.exit_reason_low_memory)
        ApplicationExitInfo.REASON_SIGNALED -> context.getString(R.string.exit_reason_signaled)
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> context.getString(R.string.exit_reason_excessive_usage)
        else -> reasonTag(code)
    }

    fun isAbnormal(code: Int): Boolean = when (code) {
        ApplicationExitInfo.REASON_EXIT_SELF,
        ApplicationExitInfo.REASON_USER_REQUESTED,
        ApplicationExitInfo.REASON_USER_STOPPED -> false

        else -> true
    }

    /**
     * Reads the previous run's exit record (API 30+) and persists a report.
     * Returns the record, or null when unavailable (first run, API < 30, or
     * only normal exits that were already reported).
     */
    fun probe(context: Context): LastExit? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            CrashDiag.step("EXIT_PROBE", "skipped: API ${Build.VERSION.SDK_INT} < 30")
            return null
        }
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val records = am.getHistoricalProcessExitReasons(context.packageName, 0, 8)
            CrashDiag.step("EXIT_PROBE", "records=${records.size}")
            val latest = records.maxByOrNull { it.timestamp } ?: return null

            val record = LastExit(
                reasonCode = latest.reason,
                reasonTag = reasonTag(latest.reason),
                abnormal = isAbnormal(latest.reason),
                description = latest.description ?: "",
                endedAtMillis = latest.timestamp,
                pid = latest.pid
            )

            CrashDiag.step(
                "EXIT_PROBE",
                "previous run ended: ${record.reasonTag} pid=${record.pid} desc='${record.description}'"
            )

            persistReport(context, latest, record)
            if (record.abnormal) saveCrashTrace(context, latest, record)

            record
        } catch (t: Throwable) {
            CrashDiag.stepFailed("EXIT_PROBE", t)
            null
        }
    }

    private fun persistReport(context: Context, info: ApplicationExitInfo, record: LastExit) {
        try {
            val dir = File(context.filesDir, "logs").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(record.endedAtMillis))
            File(dir, "last-exit.txt").writeText(
                buildString {
                    appendLine("KENKERN exit-reason report (probe at ${Date()})")
                    appendLine("Previous run ended at: ${Date(record.endedAtMillis)}")
                    appendLine("Reason: ${record.reasonTag} (code=${record.reasonCode}, pid=${record.pid})")
                    appendLine("Description: ${record.description}")
                    appendLine("Process: ${info.processName}")
                    appendLine()
                    appendLine("--- Execution trail of the affected run (when available, captured by its own crash report) ---")
                    val prior = File(dir, "crash-report-chain.txt")
                    if (prior.isFile) prior.forEachLine { appendLine(it) }
                }
            )
        } catch (t: Throwable) {
            CrashDiag.stepFailed("EXIT_PROBE_PERSIST", t)
        }
    }

    /** Saves the platform-provided trace (Java stack or native tombstone) when present. */
    private fun saveCrashTrace(context: Context, info: ApplicationExitInfo, record: LastExit) {
        try {
            val trace = info.traceInputStream ?: run {
                CrashDiag.step("EXIT_PROBE", "no platform trace attached for ${record.reasonTag}")
                return
            }
            val dir = File(context.filesDir, "logs").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(record.endedAtMillis))
            val target = File(dir, "exit-trace-$stamp-${record.reasonTag}.txt")
            trace.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            CrashDiag.step("EXIT_PROBE", "platform trace saved: ${target.name} (${target.length()} bytes)")
        } catch (t: Throwable) {
            CrashDiag.stepFailed("EXIT_PROBE_TRACE", t)
        }
    }
}
