package com.example.org.boardfinder.Utilities

import android.content.Context
import android.content.SharedPreferences
import com.android.volley.toolbox.Volley

class SharedPrefs(context: Context) {
    val PREFS_FILENAME = "prefs"
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILENAME, 0) // The 0 means private content

    val IS_LOGGED_IN = "isLoggedIng"
    val AUTH_TOKEN = "authToken"
    val USER_EMAIL = "userEmail"

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(IS_LOGGED_IN, value).apply()

    var authToken: String
        get() = prefs.getString(AUTH_TOKEN, "")
        set(value) = prefs.edit().putString(AUTH_TOKEN, value).apply()

    var userEmail: String
        get() = prefs.getString(USER_EMAIL, "")
        set(value) = prefs.edit().putString(USER_EMAIL, value).apply()

    val requestQueue = Volley.newRequestQueue(context)
}