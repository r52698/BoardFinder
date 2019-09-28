package com.example.org.boardfinder.Utilities

import android.content.Context
import android.content.SharedPreferences

class SharedPrefs(context: Context) {
    val PREFS_FILENAME = "prefs"
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILENAME, 0) // The 0 means private content

    val MSG_VIEW_TEXT = "msgViewText"
    val APP_STATE = "appState"
    val LOST_LAT = "lostLat"
    val LOST_LNG = "lostLng"
    val END_LAT = "endLat"
    val END_LNG = "endLng"
    val ZOOM_LEVEL = "zoomLevel"
    //val USER_EMAIL = "userEmail"

    var msgViewText: String
        get() = prefs.getString(MSG_VIEW_TEXT, "")
        set(value) = prefs.edit().putString(MSG_VIEW_TEXT, value).apply()

    var appState: String
        get() = prefs.getString(APP_STATE, "bst")
        set(value) = prefs.edit().putString(APP_STATE, value).apply()

    var lostLat: Float
        get() = prefs.getFloat(LOST_LAT, 32f)
        set(value) = prefs.edit().putFloat(LOST_LAT, value).apply()

    var lostLng: Float
        get() = prefs.getFloat(LOST_LNG, 35f)
        set(value) = prefs.edit().putFloat(LOST_LNG, value).apply()

    var endLat: Float
        get() = prefs.getFloat(END_LAT, 32f)
        set(value) = prefs.edit().putFloat(END_LAT, value).apply()

    var endtLng: Float
        get() = prefs.getFloat(END_LNG, 35f)
        set(value) = prefs.edit().putFloat(END_LNG, value).apply()

    var zoomLevel: Float
        get() = prefs.getFloat(ZOOM_LEVEL, 12f)
        set(value) = prefs.edit().putFloat(ZOOM_LEVEL, value).apply()

//    var isLoggedIn: Boolean
//        get() = prefs.getBoolean(IS_LOGGED_IN, false)
//        set(value) = prefs.edit().putBoolean(IS_LOGGED_IN, value).apply()
//
//    var authToken: String
//        get() = prefs.getString(AUTH_TOKEN, "")
//        set(value) = prefs.edit().putString(AUTH_TOKEN, value).apply()
//
//    var userEmail: String
//        get() = prefs.getString(USER_EMAIL, "")
//        set(value) = prefs.edit().putString(USER_EMAIL, value).apply()
//
//    val requestQueue = Volley.newRequestQueue(context)
}