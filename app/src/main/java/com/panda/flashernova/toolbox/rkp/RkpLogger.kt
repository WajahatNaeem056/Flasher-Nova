package com.panda.flashernova.toolbox.rkp

import android.util.Log

/** Log levels (correspond one to one with UI coloring) */
object RkpLevel {
    const val NORMAL = 0
    const val DIM = 1
    const val OK = 2
    const val ERR = 3
    const val WARN = 4
}

/**
 * Log output abstraction.
 *
 * - UI process: implementation appends lines to the Compose state list and persists them to disk
 * - rkpd child process (runs inside app_process): implementation writes to stdout (captured by the parent process's shell) + logcat
 */
interface RkpLogger {
    fun log(level: Int, line: String)

    fun log(line: String) = log(RkpLevel.NORMAL, line)

    fun text(level: Int, text: String?) {
        if (text.isNullOrEmpty()) return
        text.split("\n").forEach { log(level, it) }
    }
}

/** Writes to stdout + logcat: for the app_process child process */
class RkpStdoutLogger(private val tag: String = "TrueRkp") : RkpLogger {
    override fun log(level: Int, line: String) {
        println(line)
        val priority = when (level) {
            RkpLevel.ERR -> Log.ERROR
            RkpLevel.WARN -> Log.WARN
            RkpLevel.OK -> Log.INFO
            else -> Log.DEBUG
        }
        Log.println(priority, tag, line)
    }
}
