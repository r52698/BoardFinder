package com.example.org.boardfinder.Services

import android.graphics.Color
import com.example.org.boardfinder.Controller.App
import java.util.*

object UserDataService {
    var id = ""
    var avatarColor = ""
    var avatarName = ""
    var email = ""
    var name = ""

    fun returnAvatarColor(components: String) : Int {
        val strippedRgbString = components.replace("[", "").replace("]", "").replace(",", "")
//        val rgbArray = strippedRgbString.split(" ")
//        val rd = rgbArray[0].toDouble()
//        val gd = rgbArray[1].toDouble()
//        val bd = rgbArray[2].toDouble()
//        val r = (rd * 256).toInt()
//        val g = (gd * 256).toInt()
//        val b = (bd * 256).toInt()

        var r = 0
        var g = 0
        var b = 0
        val scanner = Scanner(strippedRgbString)
        if (scanner.hasNext()) {
            r = (scanner.nextDouble() * 256).toInt()
            g = (scanner.nextDouble() * 256).toInt()
            b = (scanner.nextDouble() * 256).toInt()
        }
        return Color.rgb(r, g, b)
    }

    fun logout() {
        id = ""
        avatarColor = ""
        avatarName = ""
        email = ""
        name = ""
        App.prefs.authToken = ""
        App.prefs.userEmail = ""
        App.prefs.isLoggedIn = false
        MessageService.clearMessages()
        MessageService.clearChannels()
    }
}