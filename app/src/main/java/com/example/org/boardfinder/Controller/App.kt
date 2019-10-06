package com.example.org.boardfinder.Controller

import android.app.Application
import com.example.org.boardfinder.Utilities.SharedPrefs

class App : Application() {
    companion object {
        lateinit var prefs: SharedPrefs
    }

    override fun onCreate() {
        prefs = SharedPrefs(applicationContext)
        super.onCreate()
    }
}