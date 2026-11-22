package com.panda.flashernova.toolbox.rkp

import java.lang.reflect.Method

/**
 * Lift hidden API restrictions (corresponds to True RKP's HiddenApi.java).
 *
 * Why it is needed: the rkpd entry needs ActivityThread.systemMain(), replacing the IPackageManager dynamic proxy,
 * Reflectively writing hidden members such as mPackageInfo.mApplication is blocked by default on Android 9+.
 *
 * Principle: use "reflection to obtain Class.getDeclaredMethod" to get VMRuntime.setHiddenApiExemptions,
 * Pass the prefix "L" (exempts all types) to avoid referencing hidden symbols directly.
 */
object RkpHiddenApi {

    fun exemptAll(): Boolean = try {
        // Note: Kotlin does not allow class literals for java.lang.Class or array types
        // (Class::class.java / Array<X>::class.java both fail to compile),
        // So Class.forName and emptyArray<...>().javaClass are used here to obtain the Class objects.
        val classClass = Class.forName("java.lang.Class")
        val classArrayClass = emptyArray<Class<*>>().javaClass      // Class<Array<Class<*>>>
        val stringArrayClass = emptyArray<String>().javaClass       // Class<Array<String>>
        val forName = classClass.getDeclaredMethod("forName", String::class.java)
        val getDeclaredMethod = classClass.getDeclaredMethod(
            "getDeclaredMethod", String::class.java, classArrayClass
        )
        val vmRuntime = forName.invoke(null, "dalvik.system.VMRuntime") as Class<*>
        val getRuntime = getDeclaredMethod.invoke(
            vmRuntime, "getRuntime", emptyArray<Class<*>>()
        ) as Method
        val setExemptions = getDeclaredMethod.invoke(
            vmRuntime, "setHiddenApiExemptions", arrayOf<Class<*>>(stringArrayClass)
        ) as Method
        val runtime = getRuntime.invoke(null)
        setExemptions.invoke(runtime, arrayOf("L"))
        true
    } catch (_: Throwable) {
        false
    }
}
