package com.readr.app.util

import android.util.Log
import java.io.File

/**
 * Writes uncaught crashes to app-private storage so users can share a log
 * when reporting a problem. READR has no analytics — nothing leaves the
 * device automatically.
 */
object CrashLogger {

    fun install(logsDir: File) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val file = File(logsDir, "crash_${System.currentTimeMillis()}.txt")
                file.writeText(
                    buildString {
                        append("READR crash log\n")
                        append("thread: ").append(thread.name).append('\n')
                        append(Log.getStackTraceString(throwable))
                    }
                )
                // Keep at most 5 logs
                logsDir.listFiles()?.sortedByDescending { it.name }?.drop(5)?.forEach { it.delete() }
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
