package com.example.org.boardfinder.Services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
//import android.support.annotation.Nullable
//import android.support.v4.app.ActivityCompat
//import android.support.v4.app.NotificationCompat
//import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.org.boardfinder.Controller.MapsActivity
import com.example.org.boardfinder.Controller.MapsActivity.Companion.appState
import com.example.org.boardfinder.Controller.MapsActivity.Companion.mapsActivityRunning
import com.example.org.boardfinder.R
import com.example.org.boardfinder.Services.TrackFilter.trackFilter
import com.example.org.boardfinder.Utilities.*

import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import java.sql.Timestamp

//import com.puertosoft.eder.locationtrackerkotlin.R
//
//import com.puertosoft.eder.locationtrackerkotlin.settings.Constants

class LocationMonitoringService : Service(), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{
    internal lateinit  var mLocationClient: GoogleApiClient
    internal var mLocationRequest = LocationRequest()
    internal var mLastLocation: Location? = null
    internal lateinit  var mFusedLocationClient: FusedLocationProviderClient

    var totalRemoved = 0
    var startIndexDilution = 0

    internal var mLocationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val locationList = locationResult.locations
            if (locationList.size > 0) {
                //The last location in the list is the newest
                val location = locationList[locationList.size - 1]
                Log.i("MapsActivity", "Location: " + location.latitude + " " + location.longitude)
                mLastLocation = location

                if (mLastLocation != null) {
                    Log.d(TAG, "== location != null")

                    //Send result to activities
                    sendMessageToUI(mLastLocation!!)

                    if (appState == "run") {
                        locations.add(mLastLocation!!)
                        if (!mapsActivityRunning && locations.count() >= MIN_SAMPLES_DILUTION &&
                            (locations.count() - startIndexDilution) % DILUTION_FREQUENCY == 0) {
                            println("\n dilution count before was ${locations.count()}")
                            dilution(startIndexDilution, locations.count() - 1)
                            startIndexDilution = locations.count() - 2
                            if (startIndexDilution < 0) startIndexDilution = 0
                            println("dilution count after is ${locations.count()} totalRemoved = $totalRemoved")
                        }
                        timeStamps.add(Timestamp(System.currentTimeMillis()).toString())
                        //if (locations.count() == 20) mLocationRequest.setFastestInterval(5000)
                    }
                }
            }
        }
    }

    private fun createNotificationChannel(channelId: String, channelName: String): String{
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val description = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance)
            channel.description = description
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = getSystemService(NotificationManager::class.java)

            notificationManager!!.createNotificationChannel(channel)
        }
        return channelId
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ENTRA onCreate")
        val channelId = createNotificationChannel("my_service", "My Background Service")

        val notificationIntent =  Intent(this, MapsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Board Finder")
            .setContentText("Helping you find your board")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()

        //notification.setLatestEventInfo(this, getText(R.string.notification_title), getText(R.string.notification_message), pendingIntent)
        startForeground(ONGOING_NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {


        mLocationClient = GoogleApiClient.Builder(this)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .addApi(LocationServices.API)
            .build()

        mLocationRequest.setInterval(LOCATION_INTERVAL)
        mLocationRequest.setFastestInterval(FASTEST_LOCATION_INTERVAL)


        val priority = LocationRequest.PRIORITY_HIGH_ACCURACY //by default
        //PRIORITY_BALANCED_POWER_ACCURACY, PRIORITY_LOW_POWER, PRIORITY_NO_POWER are the other priority modes


        mLocationRequest.priority = priority
        mLocationClient.connect()

        Log.d(TAG, "mFusedLocationClient")
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        //Make it stick to the notification panel so it is less prone to get cancelled by the Operating System.
        return Service.START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    /*
     * LOCATION CALLBACKS
     */
    override fun onConnected(dataBundle: Bundle?) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.

            Log.d(TAG, "== Error On onConnected() Permission not granted")
            //Permission not granted by user so cancel the further execution.

            return
        }
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper())


        Log.d(TAG, "Connected to Google API")
    }

    /*
     * Called by Location Services if the connection to the
     * location client drops because of an error.
     */
    override fun onConnectionSuspended(i: Int) {
        Log.d(TAG, "Connection suspended")
    }

    private fun sendMessageToUI(location: Location) {

        Log.d(TAG, "Sending info... lat=${location.latitude} long=${location.longitude} speed=${location.speed} time=${location.time}")

        val intent = Intent(ACTION_LOCATION_BROADCAST)
        intent.putExtra(EXTRA_LOCATION, location)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        Log.d(TAG, "Failed to connect to Google API")
    }

    private fun dilution(fromIndex: Int, toIndex: Int) {
        val numberRemoved = trackFilter(fromIndex, toIndex)
        totalRemoved += numberRemoved
//        do {
//            var dilutionIndices = mutableListOf<Int>()
//
//            println("dilution locations.count = ${locations.count()}")
//            for (i in 0 until locations.count() - 2) {
//                //println("dilution checking $i")
//                if (isRedundant(i)) {
//                    println("dilution $i is redundant ")
//                    dilutionIndices.add(i + 1)
//                }
//            }
//            for (i in 0 until dilutionIndices.count()) {
//                var index = dilutionIndices[i] - i
//                //println("dilution remove index = $index")
//                locations.removeAt(index)
//                totalRemoved++
//                //println("dilution removed point $index")
//            }
//            println("dilution removed ${dilutionIndices.count()}")
//        } while (dilutionIndices.count() > 0)
    }

//    private fun isRedundant(index: Int): Boolean {
//        val results0 = FloatArray(3)
//        val results1 = FloatArray(3)
//        Location.distanceBetween(locations[index].latitude, locations[index].longitude,
//            locations[index + 1].latitude, locations[index + 1].longitude, results0)
//        Location.distanceBetween(locations[index].latitude, locations[index].longitude,
//            locations[index + 2].latitude, locations[index + 2].longitude, results1)
//        val speed1 = results0[0] / (locations[index + 1 ].time - locations[index].time) * 1000.0
//        println("speed = $speed1 ${locations[index].speed} ${locations[index+1].speed}")
//
//        val isVeryClose = results0[0] <= MIN_DISTANCE_BETWEEN_LOCATIONS
//        if (isVeryClose) return true
//
//        val bearing0 = results0[1]
//        val bearing1 = results1[1]
//        val isBearingSame = bearing0 != 0f && bearing1 != 0f && Math.abs(bearing0 - bearing1) < MAX_BEARING_DIFFERENCE
//        var redundant = false
//        if (isBearingSame) {
//            val speed0 = locations[index].speed
//            val speed1 = locations[index + 1].speed
//            val speed2 = locations[index + 2].speed
//            val mapsActivity = MapsActivity()
//            val color0 = mapsActivity.getLineColor(speed0)
//            val color1 = mapsActivity.getLineColor(speed1)
//            val color2 = mapsActivity.getLineColor(speed2)
//            redundant = color0 == color1 && color0 == color2
//        }
//        return redundant
//    }

    companion object {

        private val TAG = LocationMonitoringService::class.java!!.getSimpleName()

        val ACTION_LOCATION_BROADCAST = LocationMonitoringService::class.java!!.getName() + "LocationBroadcast"
        val EXTRA_LOCATION = "extra_location"

        var locations = mutableListOf<Location>()
        var timeStamps = mutableListOf<String>()

        //var zoomLevel = 12f
    }
}
