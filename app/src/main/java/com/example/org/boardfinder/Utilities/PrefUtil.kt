package com.example.org.boardfinder.Utilities

import android.content.Context
import android.preference.PreferenceManager

class PrefUtil {
    companion object {
        private const val START_TIME = "com.example.org.boardfinder.start_time"

        fun getStartTime(context: Context): Long {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            return preferences.getLong(START_TIME, 0)
        }

        fun setStartTime(context: Context, value: Long) {
            println("setting start time")
            val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
            editor.putLong(START_TIME, value)
            editor.apply()
        }
    }
}