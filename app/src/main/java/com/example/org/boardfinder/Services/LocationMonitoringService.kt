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

class LocationMonitoringService : Service(), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{
    internal lateinit  var mLocationClient: GoogleApiClient
    internal var mLocationRequest = LocationRequest()
    internal var mLastLocation: Location? = null
    internal lateinit  var mFusedLocationClient: FusedLocationProviderClient

    var totalRemoved = 0
    var startIndexDilution = 0

    var comment = ""
    var passed100 = false

    var lastCommunicatedIndex = -1

    var lastPacketTrasmitted = false

    internal var mLocationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            if (locations.count() > 100) passed100 = true
            if (passed100 && locations.count() < 5) {
                val elapsedTime = (System.currentTimeMillis() - PrefUtil.getStartTime(applicationContext)) / 1000
                comment = "locations.count=${locations.count()} " +
                        "at time=${System.currentTimeMillis()} after $elapsedTime seconds from START clicked."
            }

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

                    if (appState != "bst") {
                        locations.add(mLastLocation!!)
                        if (!mapsActivityRunning && locations.count() >= MIN_SAMPLES_DILUTION &&
                            (locations.count() - startIndexDilution) % DILUTION_FREQUENCY == 0) {
                            println("\n dilution count before was ${locations.count()}")
                            dilution(startIndexDilution, locations.count() - 1)
                            startIndexDilution = locations.count() - 2
                            if (startIndexDilution < 0) startIndexDilution = 0
                            println("dilution count after is ${locations.count()} totalRemoved = $totalRemoved")
                        }
                        //println("dilution startIndexDilution=$startIndexDilution lastCommunicatedIndex=$lastCommunicatedIndex COMMUNICATION_PACKET_SIZE=$COMMUNICATION_PACKET_SIZE")
                        if (startIndexDilution > lastCommunicatedIndex + COMMUNICATION_PACKET_SIZE) {
                            val messageString = CommunicationService.getMessage(lastCommunicatedIndex + 1,
                                lastCommunicatedIndex + COMMUNICATION_PACKET_SIZE)
                            println("dilution messageString=$messageString")
                            CommunicationService.transmitLocationMessage(messageString)
                            lastCommunicatedIndex += COMMUNICATION_PACKET_SIZE
                        }
                        if (appState == "fnd" && !lastPacketTrasmitted && lastCommunicatedIndex < locations.count() - 1) {
                            val messageString = CommunicationService.getMessage(lastCommunicatedIndex + 1,
                                locations.count() - 1)
                            println("messageString=$messageString")
                            CommunicationService.transmitLocationMessage(messageString)
                            lastCommunicatedIndex = locations.count() - 1
                            lastPacketTrasmitted = true
                        }
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
        return START_STICKY
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
        intent.putExtra("EXTRA_COMMENT", comment)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        Log.d(TAG, "Failed to connect to Google API")
    }

    private fun dilution(fromIndex: Int, toIndex: Int) {
        val numberRemoved = trackFilter(fromIndex, toIndex)
        totalRemoved += numberRemoved
    }

    companion object {

        private val TAG = LocationMonitoringService::class.java.simpleName

        val ACTION_LOCATION_BROADCAST = LocationMonitoringService::class.java.name + "LocationBroadcast"
        val EXTRA_LOCATION = "extra_location"

        var locations = mutableListOf<Location>()

        var stopIndex = 0
    }
}
