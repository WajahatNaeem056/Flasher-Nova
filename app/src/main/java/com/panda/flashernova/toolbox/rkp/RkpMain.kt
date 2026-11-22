package com.panda.flashernova.toolbox.rkp

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.os.MessageQueue
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import android.util.Log
import dalvik.system.PathClassLoader
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * app_process entry class -- corresponds to True RKP's RkpMain.java / the original tool's rp.rkp.rkpd.
 *
 * Launch method (consistent with the original tool's command observed in testing):
 *   <helper> /system/bin/app_process -Djava.class.path='<APK>' / \
 *        --nice-name=com.android.rkpdapp com.panda.flashernova.toolbox.rkp.RkpMain '<rkpdapp.apk>' [dump]
 *
 * What it does:
 *   1. Lift hidden API restrictions
 *   2. ActivityThread.systemMain() to get the system context (requires root)
 *   3. Find the Context of the installed com.android.rkpdapp / com.google.android.rkpdapp on the device
 *   4. Replace IPackageManager with a dynamic proxy so hasSystemFeature("cn.google.services") is always false
 *      -- This is a compatibility measure the original tool made for devices without GMS
 *   5. Attach itself as that package's Application
 *   6. Build a PathClassLoader from rkpdapp.apk (the APEX package's Context ClassLoader has an empty DexPathList,
 *      Must be self-built, otherwise ClassNotFoundException)
 *   7. After the main thread is idle, run RKP provisioning + Widevine provisioning on a worker thread, then chown the data directory and exit
 */
class RkpMain : Application() {

    /** Exposed for the companion to use (attachBaseContext is protected) */
    fun attach(ctx: Context) {
        attachBaseContext(ctx)
    }

    companion object {
        private const val TAG = "TrueRkp"
        private val CANDIDATES = listOf(
            "com.android.rkpdapp",
            "com.google.android.rkpdapp",
        )

        @JvmStatic
        @Suppress("DEPRECATION")
        fun main(args: Array<String>) {
            val log = RkpStdoutLogger(TAG)

            try {
                Looper.prepareMainLooper()
                log.log(
                    if (RkpHiddenApi.exemptAll()) "Hidden API restrictions exempted"
                    else "Failed to exempt hidden APIs (continuing)"
                )

                val activityThread = Class.forName("android.app.ActivityThread")
                val thread = activityThread.getDeclaredMethod("systemMain").invoke(null)
                val sysCtx = activityThread.getDeclaredMethod("getSystemContext")
                    .invoke(thread) as Context

                var pkgCtx: Context? = null
                for (name in CANDIDATES) {
                    try {
                        pkgCtx = sysCtx.createPackageContext(name, 0)
                        break
                    } catch (_: PackageManager.NameNotFoundException) {
                        // Try the next one
                    }
                }
                if (pkgCtx == null) {
                    log.log(RkpLevel.ERR, "com.android.rkpdapp / com.google.android.rkpdapp not found on the device, cannot continue")
                    System.exit(2)
                    return
                }
                log.log("Using the context of ${pkgCtx.packageName}")

                installPackageManagerProxy(activityThread, log)

                val self = RkpMain()
                self.attach(pkgCtx)
                swapApplication(pkgCtx, self, log)

                val rkpdApk = args.firstOrNull()?.let { File(it) }
                if (rkpdApk == null || !rkpdApk.exists()) {
                    log.log(RkpLevel.ERR, "Missing a valid rkpdapp.apk path argument")
                    log.log(RkpLevel.ERR, "Put rkpdapp.apk into assets/ and rebuild, or install com.android.rkpdapp on the device")
                    System.exit(3)
                    return
                }
                log.log("rkpdapp.apk: ${rkpdApk.absolutePath}")

                // Key point: you cannot load rkpdapp classes directly with pkgCtx.classLoader.
                // When rkpdapp comes from an APEX, the ClassLoader obtained by createPackageContext()
                // has an empty DexPathList (an error observed in testing: DexPathList[[], ...]),
                // must be a PathClassLoader built by ourselves from the rkpdapp.apk path. The original tool does this too.
                val cl: ClassLoader = try {
                    PathClassLoader(rkpdApk.absolutePath, pkgCtx.classLoader).also {
                        log.log("PathClassLoader created for rkpdapp")
                    }
                } catch (t: Throwable) {
                    log.log(RkpLevel.WARN, "Failed to create PathClassLoader, falling back to the package context ClassLoader: $t")
                    pkgCtx.classLoader
                }

                val dumpMode = args.size > 1 && args[1] == "dump"
                if (dumpMode) {
                    Thread({
                        RkpProvisioning(pkgCtx, cl, log).dump()
                        chownData(pkgCtx, log)
                        log.log("dump finished")
                        System.exit(0)
                    }, "rkp-dump").start()
                } else {
                    Looper.getMainLooper().queue.addIdleHandler(
                        MessageQueue.IdleHandler {
                            Thread({ work(pkgCtx, cl, log) }, "rkp-work").start()
                            false
                        }
                    )
                }
                Looper.loop()
            } catch (t: Throwable) {
                Log.e(TAG, "rkpd entry failed", t)
                System.err.println("rkpd entry failed: $t")
                System.exit(1)
            }
        }

        private fun work(ctx: Context, cl: ClassLoader, log: RkpLogger) {
            try {
                log.log("Starting RKP key provisioning")
                RkpProvisioning(ctx, cl, log).run()
            } catch (t: Throwable) {
                log.log(RkpLevel.ERR, "Provisioning flow error: $t")
            }
            chownData(ctx, log)
            log.log(RkpLevel.OK, "Flow finished")
            System.exit(0)
        }

        /** Replace IPackageManager so hasSystemFeature("cn.google.services") returns false */
        private fun installPackageManagerProxy(activityThread: Class<*>, log: RkpLogger) {
            try {
                val f = findField(activityThread, "sPackageManager")
                f.isAccessible = true
                val original = f.get(null)
                val ipm = Class.forName("android.content.pm.IPackageManager")
                // Use an explicit SAM constructor to avoid lambda label/return type inference pitfalls
                val handler = InvocationHandler { _, method, args ->
                    if (method.name == "hasSystemFeature" &&
                        args != null && args.isNotEmpty() && args[0] == "cn.google.services"
                    ) {
                        false
                    } else {
                        try {
                            method.invoke(original, *(args ?: emptyArray<Any?>()))
                        } catch (e: InvocationTargetException) {
                            throw e.cause ?: e
                        }
                    }
                }
                val proxy = Proxy.newProxyInstance(activityThread.classLoader, arrayOf(ipm), handler)
                f.set(null, proxy)
                log.log("IPackageManager taken over: hasSystemFeature(\"cn.google.services\") -> false")
            } catch (t: Throwable) {
                log.log(RkpLevel.WARN, "Failed to take over IPackageManager: $t")
            }
        }

        /** Replace the rkpdapp package's Application with our own, so it does not get null */
        private fun swapApplication(pkgCtx: Context, app: Application, log: RkpLogger) {
            try {
                val mpi = findField(pkgCtx.javaClass, "mPackageInfo")
                mpi.isAccessible = true
                val loadedApk = mpi.get(pkgCtx)
                val mApp = findField(loadedApk.javaClass, "mApplication")
                mApp.isAccessible = true
                mApp.set(loadedApk, app)
                log.log("Application attached")
            } catch (t: Throwable) {
                log.log(RkpLevel.WARN, "Failed to attach Application (non-fatal): $t")
            }
        }

        /** Change the owner of data files written by root back to rkpdapp's uid (the original tool does this too) */
        private fun chownData(ctx: Context, log: RkpLogger) {
            try {
                val uid = ctx.applicationInfo.uid
                if (uid <= 0) return
                chownRecursive(ctx.dataDir, uid)
                ctx.createDeviceProtectedStorageContext()?.let { chownRecursive(it.dataDir, uid) }
                log.log("Data directory owner changed back to $uid")
            } catch (t: Throwable) {
                log.log(RkpLevel.WARN, "chown failed: $t")
            }
        }

        private fun chownRecursive(f: File, uid: Int) {
            try {
                val path = f.path
                val st: StructStat = Os.stat(path)
                if (st.st_uid == 0) Os.chown(path, uid, uid)
                if (OsConstants.S_ISDIR(st.st_mode)) {
                    f.listFiles()?.forEach { chownRecursive(it, uid) }
                }
            } catch (_: Throwable) {
                // A single file failing does not affect the whole
            }
        }

        @Throws(NoSuchFieldException::class)
        private fun findField(c: Class<*>, name: String): Field {
            var k: Class<*>? = c
            while (k != null) {
                try {
                    return k.getDeclaredField(name)
                } catch (_: NoSuchFieldException) {
                    k = k.superclass
                }
            }
            throw NoSuchFieldException(name)
        }
    }
}
