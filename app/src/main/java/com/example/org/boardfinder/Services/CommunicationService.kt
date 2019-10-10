package com.example.org.boardfinder.Services

import android.location.Location
import android.view.View
import com.example.org.boardfinder.Controller.App
import com.example.org.boardfinder.Controller.MapsActivity.Companion.selectedChannel
import com.example.org.boardfinder.Controller.MapsActivity.Companion.socket
import java.util.*

object CommunicationService {

    //var locationMessages = ArrayList<String>()
    var lostLocationMessage = ""
    var landLocationMessage = ""
    var foundLocationMessage = ""

    /**
     * Generating the message to transmit for a packet of locations
     */
    fun getMessageToTransmit(startIndex: Int, endIndex: Int) : String {
        var loc1 = LocationMonitoringService.locations[startIndex]
        var speeds = loc1.speed.toString()
        var message = "$startIndex $endIndex ${loc1.latitude} ${loc1.longitude} ${loc1.time} ${loc1.speed}"
        for (i in startIndex + 1 until endIndex + 1) {
            val loc0 = loc1
            loc1 = LocationMonitoringService.locations[i]
            val dlat = ((loc1.latitude - loc0.latitude) * 1E5).toInt()
            val dlng = ((loc1.longitude - loc0.longitude) * 1E5).toInt()
            val dtime = ((loc1.time - loc0.time) * 1E-3).toInt()
            val dspeed = ((loc1.speed - loc0.speed) * 1E2).toInt()
            message += " $dlat $dlng $dtime $dspeed"
            speeds += " ${loc1.speed}"
        }
        println("SpeedsCheck $startIndex $endIndex $speeds")
        return message
    }

    fun transmitLocationMessage(message: String) {
        println("commMsg=$message")
        sendMessage(message)
    }

    fun transmitLostLocationMessage() {
        println("commMsg=$lostLocationMessage")
        sendMessage(lostLocationMessage)
    }

    fun transmitLandLocationMessage() {
        println("commMsg=$landLocationMessage")
        sendMessage(landLocationMessage)
    }

    fun transmitFoundLocationMessage() {
        println("commMsg=$foundLocationMessage")
        sendMessage(foundLocationMessage)
    }

    fun sendMessage(messageBody: String) {
        if (App.prefs.isLoggedIn && selectedChannel != null) {
            val userId = UserDataService.id
            val channelId = selectedChannel!!.id
            val userName = UserDataService.name
            val userAvatar = UserDataService.avatarName
            val userAvatarColor = UserDataService.avatarColor
            socket.emit("newMessage", messageBody, userId, channelId, userName, userAvatar, userAvatarColor)
        }
    }

    fun decodeMessage(message: String, receivedLocations : MutableList<Location>) : String {
        println("decoding message: $message")
        var type = "Locations"
        var isLocationsListMessage = false
        var firstItemString = ""
        val location: Location = Location("stam")
        val scanner = Scanner(message)
        if (scanner.hasNext()) {
            val dateString = scanner.next()
            val timeString = scanner.next()
            firstItemString = scanner.next()
            val firstChar = firstItemString.substring(0, 1)
            isLocationsListMessage = firstChar < "A" || firstChar > "Z"
        }
        if (isLocationsListMessage) {
            var speeds = ""
            val startIndex = firstItemString.toInt()
            if (scanner.hasNext()) {
                val endIndex = scanner.nextInt()
                var lat = scanner.nextDouble()
                var lng = scanner.nextDouble()
                var time = scanner.nextLong()
                val speedFactor =
                    if (time < 1570527567194) 1E-4f
                    else 1E-2f
                println("decodeMessage speedFactor=$speedFactor")
                var speed = scanner.nextFloat()
                location.latitude = lat
                location.longitude = lng
                location.time = time
                location.speed = speed
                speeds += " $speed"
                receivedLocations.add(location)
                while (scanner.hasNext()) {
                    val location: Location = Location("stam1")
                    val dlat = scanner.nextDouble() * 1E-5
                    val dlng = scanner.nextDouble() * 1E-5
                    val dtime = scanner.nextLong() * 1000
                    val dspeed = scanner.nextFloat() * speedFactor
                    lat += dlat
                    lng += dlng
                    time += dtime
                    speed += dspeed
                    speeds += " $speed"
                    location.latitude = lat
                    location.longitude = lng
                    location.time = time
                    location.speed = speed
                    receivedLocations.add(location)
                    println("MessageDecoder lat=$lat lng=$lng time=$time speed=$speed")
                }
            }
            println("SpeedsCheck $startIndex $speeds")
        } else {
            // Not locations list message. Can be one of:
            // Land 32.11748327338571 34.833606239408255 1570261371292 2.9360397
            // Lost 32.11748327338571 34.833606239408255 1570261371292 2.9360397
            // Found 98 32.117466 34.8336761 1570262708000 0.012124617
            type = firstItemString
            if (type == "Found") {val foundeIndex = scanner.nextInt()}
            val lat = scanner.nextDouble()
            val lng = scanner.nextDouble()
            val time = scanner.nextLong()
            val speed = scanner.nextFloat()
            location.latitude = lat
            location.longitude = lng
            location.time = time
            location.speed = speed
            receivedLocations.add(location)
        }
        return type
    }
}