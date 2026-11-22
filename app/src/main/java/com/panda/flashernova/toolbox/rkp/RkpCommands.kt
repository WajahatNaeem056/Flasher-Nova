package com.panda.flashernova.toolbox.rkp

/**
 * The single source of all shell commands.
 *
 * These commands correspond one to one with the original tool's behavior (all verified on a real device).
 */
object RkpCommands {

    const val P_ENABLE = "remote_provisioning.enable_rkpd"
    const val P_HOST = "remote_provisioning.hostname"
    const val P_STRONGBOX = "remote_provisioning.strongbox.rkp_only"
    const val P_TEE = "remote_provisioning.tee.rkp_only"
    const val P_TIMEOUT = "remote_provisioning.connect_timeout_millis"

    val PROPS = listOf(P_ENABLE, P_HOST, P_STRONGBOX, P_TEE, P_TIMEOUT)

    const val DUMP = "cmd remote_provisioning dump"
    const val CSR =
        "cmd remote_provisioning csr --challenge 00000000000000000000000000000000"
    const val CERTIFY = "cmd remote_provisioning certify"
    const val ENGINEER =
        "cmd activity start -n com.oplus.engineermode/.security.RPMBStatusActivity"

    val CLEAR_PACKAGES = listOf(
        "com.android.rkpdapp",
        "com.google.android.rkpdapp",
        "io.github.vvb2060.keyattestation",
    )

    /** Read config: 5 getprop calls */
    fun loadAll(): List<String> = PROPS.map { "getprop $it" }

    /**
     * Apply config: 5 setprop calls.
     * Consistent with the original tool's observed behavior: when a boolean is off, an empty string is written instead of false; when timeout <= 0, an empty string is written.
     */
    fun applyAll(
        enable: Boolean,
        host: String,
        strongbox: Boolean,
        tee: Boolean,
        timeoutSec: Int,
    ): List<String> {
        val millis = timeoutSec * 1000
        return listOf(
            "setprop $P_ENABLE " + if (enable) "true" else "''",
            "setprop $P_HOST " + RkpShell.q(host.trim()),
            "setprop $P_STRONGBOX " + if (strongbox) "true" else "''",
            "setprop $P_TEE " + if (tee) "true" else "''",
            "setprop $P_TIMEOUT " + if (millis > 0) millis.toString() else "''",
        )
    }

    fun clearPackages(): List<String> = CLEAR_PACKAGES.map { "cmd package clear $it" }

    data class Props(
        val enable: Boolean,
        val host: String,
        val strongbox: Boolean,
        val tee: Boolean,
        val timeoutSec: Int,
    ) {
        override fun toString(): String =
            "[$enable, $host, $strongbox, $tee, $timeoutSec]"
    }

    private fun truthy(v: String?): Boolean {
        val s = v?.trim().orEmpty()
        return s == "1" || s.equals("true", true) || s.equals("on", true) || s.equals("yes", true)
    }

    /** Parse the results of 5 getprop calls into structured data */
    fun parse(out: RkpShell.Out): Props {
        val timeoutRaw = RkpShell.line(out, 4)
        val timeoutSec = timeoutRaw.toIntOrNull()?.div(1000) ?: 0
        return Props(
            enable = truthy(RkpShell.line(out, 0)),
            host = RkpShell.line(out, 1),
            strongbox = truthy(RkpShell.line(out, 2)),
            tee = truthy(RkpShell.line(out, 3)),
            timeoutSec = timeoutSec,
        )
    }
}
