package com.panda.flashernova.toolbox.rkp

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * root shell execution layer (corresponds to True RKP's Sh.java).
 *
 * Why libsu is not used: this page keeps the "customizable su path" feature
 * (set by long-pressing "Apply config"), so the su executable must be specifiable;
 * Also needs to retrieve the stdout of several commands separately per command (reading 5 getprop values).
 *
 * Implementation: ProcessBuilder(su, "-c", script), with a sentinel line printed after each command,
 * so the merged stdout is split back into per-command results; stdout/stderr are collected separately, with timeout and per-line callback support.
 */
object RkpShell {

    /** Safe single-quote escaping: used when composing shell scripts, to avoid injection */
    fun q(s: String?): String = "'" + (s ?: "").replace("'", "'\\''") + "'"

    class Out {
        val stdout = mutableListOf<String>()
        val stderr = mutableListOf<String>()
        var code = -1
        var timeout = false
        var suDenied = false

        fun stdoutText(): String = stdout.joinToString("\n")
        fun stderrText(): String = stderr.joinToString("\n")
    }

    /**
     * Run commands in order as root.
     *
     * @param suPath    su executable (can be "su")
     * @param cmds      list of commands
     * @param timeoutMs total timeout
     * @param outSink   nullable: per-line stdout callback (background thread)
     * @param errSink   nullable: per-line stderr callback (background thread)
     */
    fun root(
        suPath: String?,
        cmds: List<String>,
        timeoutMs: Long,
        outSink: ((String) -> Unit)? = null,
        errSink: ((String) -> Unit)? = null,
    ): Out {
        val out = Out()
        val sentinel = "__TRKP_" + java.lang.Long.toHexString(System.nanoTime()) + "__"
        val script = buildString {
            cmds.forEach { cmd ->
                append(cmd).append('\n')
                append("echo ").append(sentinel).append('\n')
            }
        }

        val su = suPath?.trim().takeUnless { it.isNullOrEmpty() } ?: "su"
        val process = try {
            ProcessBuilder(su, "-c", script)
                .redirectErrorStream(false)
                .start()
        } catch (e: Exception) {
            out.suDenied = true
            out.stderr.add("Unable to launch $su: ${e.message}")
            return out
        }

        val tOut = pump(process.inputStream, out.stdout, sentinel, outSink, true)
        val tErr = pump(process.errorStream, out.stderr, null, errSink, false)

        val finished = try {
            process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!finished) {
            out.timeout = true
            process.destroyForcibly()
        }
        runCatching { tOut.join(2000) }
        runCatching { tErr.join(2000) }
        out.code = runCatching { process.exitValue() }.getOrDefault(-1)
        out.suDenied = looksDenied(out) && out.stdout.isEmpty()
        return out
    }

    fun root(suPath: String?, cmds: List<String>, timeoutMs: Long): Out =
        root(suPath, cmds, timeoutMs, null, null)

    private fun pump(
        input: InputStream,
        sink: MutableList<String>,
        sentinel: String?,
        cb: ((String) -> Unit)?,
        splitBySentinel: Boolean,
    ): Thread {
        val t = Thread({
            BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
                val pending = StringBuilder()
                try {
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (splitBySentinel && sentinel != null) {
                            if (line.startsWith(sentinel)) {
                                sink.add(pending.toString())
                                pending.setLength(0)
                                continue
                            }
                            if (pending.isNotEmpty()) pending.append('\n')
                            pending.append(line)
                            cb?.invoke(line)
                        } else {
                            sink.add(line)
                            cb?.invoke(line)
                        }
                    }
                    if (splitBySentinel && pending.isNotEmpty()) sink.add(pending.toString())
                } catch (_: Exception) {
                    // The stream is interrupted when the process is killed, which is normal
                }
            }
        }, if (splitBySentinel) "rkp-stdout" else "rkp-stderr")
        t.isDaemon = true
        t.start()
        return t
    }

    private fun looksDenied(out: Out): Boolean {
        for (line in out.stderr) {
            val l = line.lowercase()
            if (l.contains("permission denied") || l.contains("not allowed") ||
                l.contains("no such file") || l.contains("inaccessible") ||
                l.contains("su: ") || l.contains("not found")
            ) {
                return true
            }
        }
        return out.code != 0 && out.stdout.isEmpty()
    }

    /** Get the first line of the result of the command at index (used for getprop) */
    fun line(out: Out, index: Int): String =
        out.stdout.getOrNull(index)?.trim().orEmpty()
}
