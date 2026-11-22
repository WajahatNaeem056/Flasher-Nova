package com.panda.flashernova.toolbox.rkp

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

/**
 * Config storage.
 * Key names follow the conventions of True RKP / the original RKPConfig (enable / hostname / strongbox / tee / timeout / su).
 */
class RkpPrefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun enable(): Boolean = sp.getBoolean(K_ENABLE, true)

    fun host(): String = sp.getString(K_HOST, defaultHost()) ?: defaultHost()

    fun strongbox(): Boolean = sp.getBoolean(K_STRONGBOX, false)

    fun tee(): Boolean = sp.getBoolean(K_TEE, false)

    /** Unit: seconds */
    fun timeout(): Int = sp.getInt(K_TIMEOUT, 10)

    fun su(): String = sp.getString(K_SU, "su") ?: "su"

    fun save(enable: Boolean, host: String, strongbox: Boolean, tee: Boolean, timeoutSec: Int) {
        sp.edit()
            .putBoolean(K_ENABLE, enable)
            .putString(K_HOST, host)
            .putBoolean(K_STRONGBOX, strongbox)
            .putBoolean(K_TEE, tee)
            .putInt(K_TIMEOUT, timeoutSec)
            .apply()
    }

    fun saveSu(path: String) {
        sp.edit().putString(K_SU, path).apply()
    }

    companion object {
        const val FILE = "rkp_config"

        const val K_ENABLE = "enable"
        const val K_HOST = "hostname"
        const val K_STRONGBOX = "strongbox"
        const val K_TEE = "tee"
        const val K_TIMEOUT = "timeout"
        const val K_SU = "su"

        /** Default server: Chinese locales use the GrapheneOS public RKP service, others use Google's official one */
        fun defaultHost(): String =
            if (Locale.getDefault().language.startsWith("zh")) {
                "remoteprovisioning.grapheneos.org"
            } else {
                "remoteprovisioning.googleapis.com"
            }
    }
}
