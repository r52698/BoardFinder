package com.example.org.boardfinder

import android.location.Location
import com.example.org.boardfinder.LocationMonitoringService.Companion.locations
import com.example.org.boardfinder.MapsActivity.Companion.endLatLng
import com.example.org.boardfinder.MapsActivity.Companion.endTimeStamp
import com.example.org.boardfinder.MapsActivity.Companion.lostLatLng
import com.example.org.boardfinder.MapsActivity.Companion.lostTimeStamp
import com.google.android.gms.maps.model.LatLng

object FindBoardService {

    fun getCurrentBoardPosition() : LatLng {
        val lost2reportedTimeMillis = endTimeStamp - lostTimeStamp
        val now2reportedRatio =
            (System.currentTimeMillis() - lostTimeStamp) / lost2reportedTimeMillis
        val currentKiteLat =
            lostLatLng.latitude + (endLatLng.latitude - lostLatLng.latitude) * now2reportedRatio
        val currentKiteLng =
            lostLatLng.longitude + (endLatLng.longitude - lostLatLng.longitude) * now2reportedRatio
        val currentBoardLat =
            lostLatLng.latitude + (currentKiteLat - lostLatLng.latitude) * BOARD_TO_KITE_DRIFT_RATIO
        val currentBoardLng =
            lostLatLng.longitude + (currentKiteLng - lostLatLng.longitude) * BOARD_TO_KITE_DRIFT_RATIO
        return LatLng(currentBoardLat, currentBoardLng)
    }

    fun getLostBoardTimeStamp(): Long {
        var minimumDistance = 40000.0F
        var lostTimeStamp = 0L
        for (i in 0 until locations.count() - 1) {
            val results = FloatArray(3)
            val calcDistance = Location.distanceBetween(lostLatLng.latitude, lostLatLng.longitude,
                locations[i].latitude, locations[i].longitude, results)
            if (results[0] < minimumDistance) {
                minimumDistance = results[0]
                lostTimeStamp = locations[i].time
            }
        }
        return lostTimeStamp
    }
}