package com.example.org.boardfinder.Services

import android.location.Location
import com.example.org.boardfinder.Services.LocationMonitoringService.Companion.locations
import com.example.org.boardfinder.Services.LocationMonitoringService.Companion.stopIndex
import com.example.org.boardfinder.Utilities.EPSILON
import com.example.org.boardfinder.Utilities.MIN_DISTANCE_BETWEEN_LOCATIONS
import com.example.org.boardfinder.Utilities.TIME_TO_LATLNG_FACTOR

typealias Point = Triple<Double, Double, Double>

object TrackFilter {

    fun perpendicularDistance(pt: Point, lineStart: Point, lineEnd: Point): Double {
        var dx = lineEnd.first - lineStart.first
        var dy = lineEnd.second - lineStart.second
        var dz = lineEnd.third - lineStart.third

        // Normalize
        val mag = hypot(dx, dy, dz)
        if (mag > 0.0) { dx /= mag; dy /= mag; dz /= mag }
        val pvx = pt.first - lineStart.first
        val pvy = pt.second - lineStart.second
        val pvz = pt.third - lineStart.third

        // Get dot product (project pv onto normalized direction)
        val pvdot = dx * pvx + dy * pvy + dz * pvz

        // Scale line direction vector and substract it from pv
        val ax = pvx - pvdot * dx
        val ay = pvy - pvdot * dy
        val az = pvz - pvdot * dz

        println("ax=$ax ay=$ay az=$az")
        println("dx=$ax dy=$ay dz=$az")

        return hypot(ax, ay, az)
    }

    fun hypot(a: Double, b: Double, c: Double) : Double {
        return Math.sqrt(a * a + b * b + c * c)
    }

    fun RamerDouglasPeucker(pointList: List<Point>, epsilon: Double, out: MutableList<Point>): Int {
        if (pointList.size < 2) throw IllegalArgumentException("Not enough points to simplify")

        var dilutionIndices = mutableListOf<Int>()
        var keepIndices = mutableListOf<Int>()
        // Find the point with the maximum distance from line between start and end
        var dmax = 0.0
        var index = 0
        val end = pointList.size - 1
        for (i in 1 until end) {
            val d = perpendicularDistance(pointList[i], pointList[0], pointList[end])
            if (d > dmax) { index = i; dmax = d }
        }

        // If max distance is greater than epsilon, recursively simplify
        println("dilution max = $dmax")
        if (dmax > epsilon) {
            val recResults1 = mutableListOf<Point>()
            val recResults2 = mutableListOf<Point>()
            val firstLine = pointList.take(index + 1)
            val lastLine  = pointList.drop(index)
            RamerDouglasPeucker(firstLine, epsilon, recResults1)
            RamerDouglasPeucker(lastLine, epsilon, recResults2)

            // build the result list
            out.addAll(recResults1.take(recResults1.size - 1))
            out.addAll(recResults2)
            if (out.size < 2) throw RuntimeException("Problem assembling output")
        }
        else {
            // Just return start and end points
            out.clear()
            out.add(pointList.first())
            out.add(pointList.last())
        }
        return pointList.count() - out.count()
    }

    fun removeVeryClosePoints(startIndex: Int, endIndex: Int) : Int {
        var totalRemoved = 0
        for (i in startIndex until endIndex ) {
            val results = FloatArray(3)
            Location.distanceBetween(
                locations[i - totalRemoved].latitude, locations[i - totalRemoved].longitude,
                locations[i + 1 - totalRemoved].latitude, locations[i + 1 - totalRemoved].longitude, results)
            if (results[0] <= MIN_DISTANCE_BETWEEN_LOCATIONS && (stopIndex != i + 1 - totalRemoved)) {
                val removingIndex = i + 1 - totalRemoved
                locations.removeAt(removingIndex)
                println("dilution very close removing ${i+1}")
                if (stopIndex > removingIndex) stopIndex--
                println("stopIndex=$stopIndex time=${locations[stopIndex].time}")
                totalRemoved++
            }
        }
        return totalRemoved
    }

//    fun removeUnreliablePoints(startIndex: Int, endIndex: Int) : Int {
//        var totalRemoved = 0
//        for (i in startIndex until endIndex ) {
//            val acceleration = (locations[i + 1].speed - locations[i].speed) / (locations[i + 1].time - locations[i].time)
//            val accuracy0 = locations[i].accuracy
//            val accuracy1 = locations[i+1].accuracy
//            if (Math.abs(acceleration) > 1E-3 || accuracy0 > 40 || accuracy1 > 40) {
//                println("acceleration=$acceleration accuracy0=$accuracy0 accuracy1=$accuracy1 at point $i")
//            }
//        }
//        return totalRemoved
//    }

    fun trackFilter(startIndex: Int, endIndex: Int) : Int {
        println("dilution trackFiler startIndex = $startIndex  endIndex = $endIndex")
//        val x = removeUnreliablePoints(startIndex, endIndex)
        val numberRemoved = removeVeryClosePoints(startIndex, endIndex)
        val newEndIndex = endIndex - numberRemoved
        println ("dilution newEndIndex = $newEndIndex  numberRemoved = $numberRemoved")
        var numberRemoved1 = 0
        if (newEndIndex > startIndex) {
            val pointList = mutableListOf<Point>()
            val points2Indices = HashMap<Point, Int>()
            for (i in startIndex until newEndIndex + 1) {
                val point = Point(
                    locations[i].latitude,
                    locations[i].longitude,
                    timeToLatLngFactor(locations[i].time)
                )
                pointList.add(point)
                points2Indices[point] = i
            }
            val pointListOut = mutableListOf<Point>()
            val removedRDP = RamerDouglasPeucker(pointList, EPSILON, pointListOut)
            for (i in startIndex + 1 until newEndIndex) {
                val j = i - numberRemoved1
                val point = Point(
                    locations[j].latitude,
                    locations[j].longitude,
                    timeToLatLngFactor(locations[j].time)
                )
                if (!pointListOut.contains(point)) {
                    if (stopIndex != j) {
                        locations.removeAt(j)
                        println("dilution RDP removing $i")
                        numberRemoved1++
                        if (stopIndex > j) stopIndex--
                        println("stopIndex=$stopIndex time=${locations[stopIndex].time}")
                    }
                }
            }
            println ("dilution Started with ${pointList.count()}, should remove $removedRDP but actually removed $numberRemoved1, ended with ${pointListOut.count()}")
        }

        return numberRemoved + numberRemoved1
    }

    fun timeToLatLngFactor(time: Long) : Double {
        return time * TIME_TO_LATLNG_FACTOR
    }

//    fun main(args: Array<String>) {
//        val pointList = listOf(
//            Point(0.0, 0.0, 1.0),
//            Point(1.0, 0.1, 1.0),
//            Point(2.0, -0.1, 1.0),
//            Point(3.0, 5.0, 1.0),
//            Point(4.0, 6.0, 1.0),
//            Point(5.0, 7.0, 1.0),
//            Point(6.0, 8.1, 1.0),
//            Point(7.0, 9.0, 1.0),
//            Point(8.0, 9.0, 1.0),
//            Point(9.0, 9.0, 1.0)
//        )
//        val pointListOut = mutableListOf<Point>()
//        RamerDouglasPeucker(pointList, 1.0, pointListOut)
//        println("Points remaining after simplification:")
//        for (p in pointListOut) println(p)
//    }
}