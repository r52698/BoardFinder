package com.example.org.boardfinder.Services

object CommunicationService {

    //var locationMessages = ArrayList<String>()
    var lostLocationMessage = ""
    var landLocationMessage = ""
    var foundLocationMessage = ""

    fun getMessage(startIndex: Int, endIndex: Int) : String {
        var loc1 = LocationMonitoringService.locations[startIndex]
        var message = "$startIndex $endIndex ${loc1.latitude} ${loc1.longitude} ${loc1.time} ${loc1.speed}"
        for (i in startIndex + 1 until endIndex + 1) {
            val loc0 = loc1
            loc1 = LocationMonitoringService.locations[i]
            val dlat = ((loc1.latitude - loc0.latitude) * 1E5).toInt()
            val dlng = ((loc1.longitude - loc0.longitude) * 1E5).toInt()
            val dtime = ((loc1.time - loc0.time) * 1E-3).toInt()
            val dspeed = ((loc1.speed - loc0.speed) * 1E1).toInt()
            message += " $dlat $dlng $dtime $dspeed"
        }
        return message
    }

    fun transmitLocationMessage(message: String) {
        println("commMsg=$message")
    }

    fun transmitLostLocationMessage() {
        println("commMsg=$lostLocationMessage")
    }

    fun transmitLandLocationMessage() {
        println("commMsg=$landLocationMessage")
    }

    fun transmitFoundLocationMessage() {
        println("commMsg=$foundLocationMessage")
    }
}