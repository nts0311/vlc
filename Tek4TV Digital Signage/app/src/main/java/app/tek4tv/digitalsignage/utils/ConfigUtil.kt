package app.tek4tv.digitalsignage.utils

import android.content.Context

object ConfigUtil {
    const val PREFERENCE_FILE_NAME = "vn.tek4tv.radioip"
    const val ACTION_SCHEDULED = "vn.tek4tv.radioip"
    fun putString(context: Context, key: String?, value: String?) {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putString(key, value)
        editor.apply()
    }

    fun getString(context: Context, key: String?, defaultValue: String?): String? {
        val sharedPref = context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        return sharedPref.getString(key, defaultValue)
    }
}