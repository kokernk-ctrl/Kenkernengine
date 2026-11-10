package com.kenkern.engine

import android.app.Application
import com.kenkern.engine.core.EngineRuntime

/**
 * KENKERN application entry point.
 *
 * Keeps process-level initialization in one place. Heavy subsystems
 * (rendering, scene) are initialized lazily by their owners; only the
 * engine runtime (logging, crash handling) is prepared here.
 */
class KenkernApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        EngineRuntime.init(this)
    }
}
