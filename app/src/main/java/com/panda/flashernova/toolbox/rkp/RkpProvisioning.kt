package com.panda.flashernova.toolbox.rkp

import android.content.Context
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.time.Instant

/**
 * Main RKP key provisioning flow (corresponds to True RKP's Provisioning.java).
 *
 * The original tool's counterpart is rp.h0 (RkpProvisioner, 754 lines, log tag RkpdPeriodicProvisioner),
 * It directly references classes of the embedded AOSP rkpdapp. This implementation does not bundle AOSP classes into the APK, but uses **reflection** instead
 * Classes loaded at runtime from rkpdapp.apk, so it is more tolerant of AOSP version differences.
 *
 * Flow (matches the original tool's observed logs one to one):
 *   1. Settings.getUrl / getDefaultUrl            -- exit immediately if not configured
 *   2. ServiceManagerInterface.getAllInstances    -- enumerate IRPC (TEE / StrongBox)
 *   3. RkpdDatabase -> ProvisionedKeyDao          -- clean up expired keys
 *   4. ServerInterface.fetchGeekAndUpdate         -- fetch Geek (server-side config)
 *   5. numExtraAttestationKeys == 0               -- the server requires the key pool to be emptied
 *   6. Provisioner.clearBadAttestationKeys
 *   7. Run provisionKeys on each SystemInterface in turn
 *   8. Widevine certificate provisioning
 */
class RkpProvisioning(
    private val context: Context,
    private val classLoader: ClassLoader,
    private val log: RkpLogger?,
) {

    fun run() {
        try {
            doRun()
        } catch (t: Throwable) {
            line(RkpLevel.ERR, "RKP provisioning error: $t")
            t.cause?.let { line(RkpLevel.ERR, "  Cause: $it") }
        }
    }

    @Throws(Exception::class)
    private fun doRun() {
        val settings = classLoader.loadClass("com.android.rkpdapp.utils.Settings")
        val defUrl = invokeStatic(settings, "getDefaultUrl") as? String
        val url = invokeStatic(settings, "getUrl", context) as? String
        line("RKP server: ${if (url.isNullOrEmpty()) "(not configured)" else url}")
        if (defUrl.isNullOrEmpty() || url.isNullOrEmpty()) {
            line("No RKP server address configured on the system, skipping")
            return
        }

        val svcMgr = classLoader.loadClass("com.android.rkpdapp.interfaces.ServiceManagerInterface")
        val instances = invokeStatic(svcMgr, "getAllInstances") as? Array<*>
        if (instances.isNullOrEmpty()) {
            line("No usable IRPC HAL, skipping")
            return
        }
        line("Found ${instances.size} IRPC implementations")

        val attemptCls = classLoader.loadClass("com.android.rkpdapp.metrics.ProvisioningAttempt")
        val attempt = invokeStatic(attemptCls, "createScheduledAttemptMetrics", context)

        val dbCls = classLoader.loadClass("com.android.rkpdapp.database.RkpdDatabase")
        val db = invokeStatic(dbCls, "getDatabase", context)
        val dao = invoke(db, "provisionedKeyDao")
        val daoIface = classLoader.loadClass("com.android.rkpdapp.database.ProvisionedKeyDao")

        runCatching { invoke(dao, "deleteExpiringKeys", Instant.now()) }
            .onFailure { line(RkpLevel.WARN, "Failed to clean up expired keys: $it") }

        val serverCls = classLoader.loadClass("com.android.rkpdapp.interfaces.ServerInterface")
        val server = serverCls.getConstructor(Context::class.java, Boolean::class.javaPrimitiveType!!)
            .newInstance(context, true)
        val geek = try {
            invoke(server, "fetchGeekAndUpdate", attempt)
        } catch (t: Throwable) {
            line(RkpLevel.ERR, "Failed to fetch config from the RKP server: ${rootCause(t)}")
            return
        }

        val extra = (getField(geek, "numExtraAttestationKeys") as? Number)?.toInt() ?: 0
        line("Extra keys allowed by the server: $extra")
        if (extra == 0) {
            invoke(dao, "deleteAllKeys")
            closeQuietly(attempt)
            line(RkpLevel.WARN, "The server requires provisioning to be disabled and the key pool cleared")
            return
        }

        val provCls = classLoader.loadClass("com.android.rkpdapp.provisioner.Provisioner")
        val provisioner = provCls
            .getConstructor(Context::class.java, daoIface, Boolean::class.javaPrimitiveType!!)
            .newInstance(context, dao, true)

        runCatching { invoke(provisioner, "clearBadAttestationKeys", geek) }
            .onFailure { line(RkpLevel.WARN, "Failed to clean up abnormal attestation keys: ${rootCause(it)}") }

        var ok = 0
        for (iface in instances) {
            iface ?: continue
            line("Starting provisioning: $iface")
            try {
                invoke(provisioner, "provisionKeys", attempt, iface, geek)
                ok++
                line(RkpLevel.OK, "Provisioning succeeded: $iface")
            } catch (t: Throwable) {
                line(RkpLevel.ERR, "Provisioning failed: ${rootCause(t)}")
            }
        }
        closeQuietly(attempt)
        line(
            if (ok > 0) RkpLevel.OK else RkpLevel.WARN,
            "RKP provisioning finished: $ok/${instances.size} succeeded"
        )

        RkpWidevine(log).provision()
    }

    /** Read-only mode: print a key pool overview (corresponds to the original tool's dump branch) */
    fun dump() {
        try {
            val dbCls = classLoader.loadClass("com.android.rkpdapp.database.RkpdDatabase")
            val db = invokeStatic(dbCls, "getDatabase", context)
            val dao = invoke(db, "provisionedKeyDao")
            var keys: Any? = null
            for (name in listOf("getAll", "getAllKeys", "getAllForUser")) {
                keys = tryInvoke(dao, name)
                if (keys is List<*>) break
            }
            if (keys is List<*>) {
                line(RkpLevel.OK, "Provisioned key records: ${keys.size}")
                keys.take(40).forEach { line("  $it") }
                if (keys.size > 40) line("... rest omitted")
            } else {
                line("This rkpdapp version provides no readable key list method, skipping")
            }
        } catch (t: Throwable) {
            line(RkpLevel.WARN, "Failed to read the key store: ${rootCause(t)}")
        }
    }

    // ------------------------------------------------------------ Reflection helpers

    private fun invokeStatic(c: Class<*>, name: String, vararg args: Any?): Any? {
        val m = find(c, name, args, true)
            ?: throw NoSuchMethodException("${c.name}#$name")
        return m.invoke(null, *args)
    }

    private fun invoke(target: Any?, name: String, vararg args: Any?): Any? {
        val t = target ?: throw IllegalStateException("$name: reflection target is null")
        val m = find(t.javaClass, name, args, false)
            ?: throw NoSuchMethodException("${t.javaClass.name}#$name")
        return m.invoke(t, *args)
    }

    private fun tryInvoke(target: Any?, name: String, vararg args: Any?): Any? =
        runCatching { invoke(target, name, *args) }.getOrNull()

    @Throws(Exception::class)
    private fun getField(o: Any?, field: String): Any? {
        val target = o ?: throw IllegalStateException("$field: reflection target is null")
        var c: Class<*>? = target.javaClass
        while (c != null) {
            try {
                val f: Field = c.getDeclaredField(field)
                f.isAccessible = true
                return f.get(target)
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
        throw NoSuchFieldException(field)
    }

    /** Find a method by name + argument compatibility (including interfaces), avoiding hardcoded AOSP signatures */
    private fun find(
        c: Class<*>,
        name: String,
        args: Array<out Any?>,
        staticOnly: Boolean,
    ): Method? {
        var k: Class<*>? = c
        while (k != null) {
            for (m in k.declaredMethods) {
                if (m.name != name) continue
                if (staticOnly && !Modifier.isStatic(m.modifiers)) continue
                val ps = m.parameterTypes
                if (ps.size != args.size) continue
                var ok = true
                for (i in ps.indices) {
                    val a = args[i] ?: continue
                    if (!box(ps[i]).isAssignableFrom(a.javaClass)) {
                        ok = false
                        break
                    }
                }
                if (ok) {
                    m.isAccessible = true
                    return m
                }
            }
            for (itf in k.interfaces) {
                find(itf, name, args, staticOnly)?.let { return it }
            }
            k = k.superclass
        }
        return null
    }

    private fun box(c: Class<*>): Class<*> = when (c) {
        Int::class.java -> Int::class.javaObjectType
        Long::class.java -> Long::class.javaObjectType
        Boolean::class.java -> Boolean::class.javaObjectType
        Double::class.java -> Double::class.javaObjectType
        Float::class.java -> Float::class.javaObjectType
        Short::class.java -> Short::class.javaObjectType
        Byte::class.java -> Byte::class.javaObjectType
        Char::class.java -> Char::class.javaObjectType
        else -> c
    }

    private fun rootCause(t: Throwable): String {
        var c: Throwable = t
        while (c.cause != null && c.cause !== c) c = c.cause!!
        return c.toString()
    }

    private fun closeQuietly(o: Any?) {
        if (o == null) return
        runCatching { invoke(o, "close") }
    }

    private fun line(s: String) = line(RkpLevel.NORMAL, s)

    private fun line(level: Int, s: String) {
        log?.log(level, s)
    }
}
