package com.panda.flashernova.toolbox.rkp

import android.content.Context
import android.content.pm.PackageManager
import java.io.File

/**
 * Responsible for preparing rkpdapp.apk and assembling a launch command equivalent to the original tool's.
 *
 * Launch chain of the original tool (confirmed by testing):
 *   librkp.so  /system/bin/app_process -Djava.class.path='<APK>' / \
 *       --nice-name=com.android.rkpdapp rp.rkp.rkpd '<rkpdapp.apk>' [dump]
 *
 * This integration replaces the leading librkp.so with a choice of three (see [wrap]):
 *   1) Bundled libnsexec.so (in-house setns helper in the same jniLibs directory)
 *   2) The system toybox nsenter
 *   3) Direct connection (most root environments are already in init's mount namespace)
 */
object RkpLauncher {

    const val ASSET = "rkpdapp.apk"
    const val MAIN_CLASS = "com.panda.flashernova.toolbox.rkp.RkpMain"
    const val NICE_NAME = "com.android.rkpdapp"

    /** Package names under which rkpdapp may exist on the system */
    val CANDIDATE_PACKAGES = listOf(
        "com.android.rkpdapp",
        "com.google.android.rkpdapp",
    )

    /** Fallback hardcoded paths (prefer the real path found via PackageManager) */
    private val SYSTEM_RKPD = listOf(
        "/apex/com.android.rkpd/priv-app/rkpdapp/rkpdapp.apk",
        "/system/apex/com.android.rkpd/priv-app/rkpdapp/rkpdapp.apk",
        "/system/priv-app/rkpdapp/rkpdapp.apk",
    )

    /**
     * Obtain rkpdapp.apk, trying in order of reliability:
     *   1) Embedded assets/rkpdapp.apk (extracted to cacheDir)
     *   2) PackageManager query for the real APK path of the installed rkpdapp on the device
     *   3) Several hardcoded APEX / priv-app paths
     * Returns null if all attempts fail.
     */
    fun materialize(context: Context): File? {
        val dst = File(context.cacheDir, ASSET)
        try {
            context.assets.open(ASSET).use { input ->
                val len = input.available()
                if (dst.exists() && dst.length() == len.toLong()) return dst
                dst.outputStream().use { out -> input.copyTo(out) }
                return dst
            }
        } catch (_: Exception) {
            // Not included in assets, keep looking
        }
        installedRkpdApp(context)?.let { return it }
        return systemRkpdApp()
    }

    /** Ask PackageManager for the installed rkpdapp's APK path (ApplicationInfo.sourceDir) */
    fun installedRkpdApp(context: Context): File? {
        val pm = context.packageManager
        for (pkg in CANDIDATE_PACKAGES) {
            try {
                val ai = pm.getApplicationInfo(pkg, 0)
                val path = ai.sourceDir
                if (!path.isNullOrEmpty()) {
                    val f = File(path)
                    if (f.exists() && f.length() > 0) return f
                }
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed, try the next one
            } catch (_: Exception) {
                // Other exceptions are skipped as well
            }
        }
        return null
    }

    fun systemRkpdApp(): File? =
        SYSTEM_RKPD.map { File(it) }.firstOrNull { it.exists() }

    /** Path to the bundled setns helper (an executable packaged under a .so name in jniLibs) */
    fun nativeHelper(context: Context): String? {
        val f = File(context.applicationInfo.nativeLibraryDir, "libnsexec.so")
        return if (f.exists()) f.absolutePath else null
    }

    fun hasSystemNsenter(): Boolean = File("/system/bin/nsenter").exists()

    fun helperDescription(context: Context): String = when {
        nativeHelper(context) != null -> "libnsexec.so (bundled)"
        hasSystemNsenter() -> "nsenter (system toybox)"
        else -> "Direct (no namespace switch)"
    }

    /** The app_process command itself (without prefix) */
    fun appProcess(context: Context, rkpdApk: File?, extra: String?): String {
        val sb = StringBuilder(256)
        sb.append("/system/bin/app_process")
        sb.append(" -Djava.class.path=").append(RkpShell.q(context.applicationInfo.publicSourceDir))
        sb.append(" / --nice-name=").append(NICE_NAME).append(' ')
        sb.append(MAIN_CLASS)
        rkpdApk?.let { sb.append(' ').append(RkpShell.q(it.absolutePath)) }
        val e = extra?.trim().orEmpty()
        if (e.isNotEmpty()) sb.append(' ').append(RkpShell.q(e))
        return sb.toString()
    }

    /** Wrap with a namespace switch using whichever method is available */
    fun wrap(context: Context, inner: String): String {
        nativeHelper(context)?.let { return RkpShell.q(it) + " " + inner }
        if (hasSystemNsenter()) return "nsenter -t 1 -m -- $inner"
        return inner
    }

    /** Full command */
    fun fullCommand(context: Context, extra: String?): String =
        wrap(context, appProcess(context, materialize(context), extra))
}
