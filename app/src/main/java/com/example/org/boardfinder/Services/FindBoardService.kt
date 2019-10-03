package com.example.org.boardfinder.Services

import android.location.Location
import com.example.org.boardfinder.Services.LocationMonitoringService.Companion.locations
import com.example.org.boardfinder.Controller.MapsActivity.Companion.landLatLng
import com.example.org.boardfinder.Controller.MapsActivity.Companion.landTimeStamp
import com.example.org.boardfinder.Controller.MapsActivity.Companion.lostLatLng
import com.example.org.boardfinder.Controller.MapsActivity.Companion.lostTimeStamp
import com.example.org.boardfinder.Utilities.BOARD_TO_KITE_DRIFT_RATIO
import com.google.android.gms.maps.model.LatLng

object FindBoardService {

    fun getCurrentBoardPosition() : LatLng {
        val lost2reportedTimeMillis = landTimeStamp - lostTimeStamp
        val now2reportedRatio = 1.0 *
            (System.currentTimeMillis() - lostTimeStamp) / lost2reportedTimeMillis
        println("ratio=$now2reportedRatio")
        val currentKiteLat =
            lostLatLng.latitude + (landLatLng.latitude - lostLatLng.latitude) * now2reportedRatio
        val currentKiteLng =
            lostLatLng.longitude + (landLatLng.longitude - lostLatLng.longitude) * now2reportedRatio
        val currentBoardLat =
            lostLatLng.latitude + (currentKiteLat - lostLatLng.latitude) * BOARD_TO_KITE_DRIFT_RATIO
        val currentBoardLng =
            lostLatLng.longitude + (currentKiteLng - lostLatLng.longitude) * BOARD_TO_KITE_DRIFT_RATIO
        return LatLng(currentBoardLat, currentBoardLng)
    }

    fun getLostBoardIndex(): Int {
        var minimumDistance = 40000.0F
        var lostIndex = 0
        for (i in 0 until locations.count()) {
            val results = FloatArray(3)
            Location.distanceBetween(lostLatLng.latitude, lostLatLng.longitude,
                locations[i].latitude, locations[i].longitude, results)
            if (results[0] < minimumDistance) {
                minimumDistance = results[0]
                lostIndex = i
            }
        }
        return lostIndex
    }
}