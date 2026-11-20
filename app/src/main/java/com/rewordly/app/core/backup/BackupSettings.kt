package com.rewordly.app.core.backup

import android.content.Context
import android.content.SharedPreferences

/** Plain SharedPreferences, not DataStore: the backup agent runs in restricted mode without Hilt. */
object BackupSettings {
    private const val FILE_NAME = "backup_settings"
    private const val KEY_ENABLED = "cloud_backup_enabled"

    fun isEnabled(context: Context): Boolean = enabledOrDefault(prefs(context).getBoolean(KEY_ENABLED, true))

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Absent means the feature is on, so existing users get persistence after the update. */
    internal fun enabledOrDefault(stored: Boolean?): Boolean = stored ?: true

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
}
