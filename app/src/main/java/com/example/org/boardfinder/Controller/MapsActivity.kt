package com.example.org.boardfinder.Controller

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.org.boardfinder.Services.FindBoardService
import com.example.org.boardfinder.Services.LocationMonitoringService
import com.example.org.boardfinder.Services.LocationMonitoringService.Companion.locations
import com.example.org.boardfinder.R
//import com.example.org.boardfinder.Services.LocationMonitoringService.Companion.zoomLevel
import com.example.org.boardfinder.Utilities.*
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*

import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_maps.*
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.exitProcess

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var lastLocation: Location

//    private lateinit var locationCallback: LocationCallback
//
//    private lateinit var locationRequest: LocationRequest
//    private var locationUpdateState = false

    private var mAlreadyStartedService = false
    private var mMsgView: TextView? = null

    private var distance = 0.0
    private var averageSpeed = "0.0"

    private var mapReady = false

    private var lastCurrentBoardLatLng = LatLng(32.0, 35.0)

    private var showingTrack = false

    /**
     * Return the availability of GooglePlayServices
     */
    private val isGooglePlayServicesAvailable:Boolean
        get() {
            val googleApiAvailability = GoogleApiAvailability.getInstance()
            val status = googleApiAvailability.isGooglePlayServicesAvailable(this)
            if (status != ConnectionResult.SUCCESS)
            {
                if (googleApiAvailability.isUserResolvableError(status))
                {
                    googleApiAvailability.getErrorDialog(this, status, 2404).show()
                }
                return false
            }
            return true
        }


    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val REQUEST_CHECK_SETTINGS = 2

        private val TAG = MapsActivity::class.java.simpleName

        /**
         * Code used in requesting runtime permissions.
         */
        private const val REQUEST_PERMISSIONS_REQUEST_CODE = 34

        var appState = "bst"
        // bst, run, pas, stp, mrk

        var lostLatLng = LatLng(32.0, 35.0)
        var endLatLng = LatLng(32.0, 35.0)
        var lostTimeStamp = 0L
        var endTimeStamp = 0L
        var mapsActivityRunning = false

        var firstTime = true
        lateinit var currentBoardLocationMarker: Marker
        lateinit var lostPositionMarker: Marker
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.putString(EXTRA_TEXT, msgView.text.toString())
        outState?.putString(EXTRA_STATE,
            appState
        )
        outState?.putDouble(EXTRA_LOST_LAT, lostLatLng.latitude)
        outState?.putDouble(EXTRA_LOST_LNG, lostLatLng.longitude)
        outState?.putDouble(EXTRA_END_LAT, endLatLng.latitude)
        outState?.putDouble(EXTRA_END_LNG, endLatLng.longitude)
        outState?.putDouble(EXTRA_CURRENT_LAT, lastCurrentBoardLatLng.latitude)
        outState?.putDouble(EXTRA_CURRENT_LNG, lastCurrentBoardLatLng.longitude)
        if (mapReady) PrefUtil.setZoomLevel(applicationContext, map.cameraPosition.zoom)
        outState?.putFloat(EXTRA_ZOOM_LEVEL, PrefUtil.getZoomLevel(applicationContext))
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        if (savedInstanceState != null) {
            msgView.text = savedInstanceState.getString(EXTRA_TEXT)
            appState = savedInstanceState.getString(EXTRA_STATE)
            updateButtons()
            lostLatLng = LatLng(savedInstanceState.getDouble(EXTRA_LOST_LAT),
                savedInstanceState.getDouble(EXTRA_LOST_LNG))
            endLatLng = LatLng(savedInstanceState.getDouble(EXTRA_END_LAT),
                savedInstanceState.getDouble(EXTRA_END_LNG))
            lastCurrentBoardLatLng = LatLng(savedInstanceState.getDouble(EXTRA_CURRENT_LAT),
                savedInstanceState.getDouble(EXTRA_CURRENT_LNG))
            PrefUtil.setZoomLevel(applicationContext, savedInstanceState.getFloat(EXTRA_ZOOM_LEVEL))
            //createLocationRequest()
        }
    }

    private fun updateButtons(){
        when (appState) {
            "bst" ->{
                btn_start_tracking.isEnabled = true
                btn_stop_tracking.isEnabled = false
                btn_mark_cul.isEnabled = false
                btn_report_found.isEnabled = false
            }
            "run" ->{
                btn_start_tracking.isEnabled = false
                btn_stop_tracking.isEnabled = true
                btn_mark_cul.isEnabled = false
                btn_report_found.isEnabled = false
            }
            "stp" ->{
                btn_start_tracking.isEnabled = false
                btn_stop_tracking.isEnabled = false
                btn_mark_cul.isEnabled = true
                btn_report_found.isEnabled = false
                targetImageView.visibility = View.VISIBLE
                btn_remark.visibility = View.INVISIBLE
            }
            "mrk" ->{
                btn_start_tracking.isEnabled = false
                btn_stop_tracking.isEnabled = false
                btn_mark_cul.isEnabled = false
                btn_report_found.isEnabled = true
                btn_report_found.visibility = View.VISIBLE
                btn_start_tracking.visibility = View.INVISIBLE
                targetImageView.visibility = View.INVISIBLE
                btn_remark.visibility = View.VISIBLE
                btn_stop_tracking.visibility = View.INVISIBLE
            }
            "fnd" ->{
                btn_start_tracking.isEnabled = false
                btn_stop_tracking.isEnabled = false
                btn_mark_cul.isEnabled = false
                btn_report_found.isEnabled = false
                btn_restart_app.visibility = View.VISIBLE
                btn_mark_cul.visibility = View.INVISIBLE
                btn_quit_app.visibility = View.VISIBLE
                btn_report_found.visibility = View.INVISIBLE
                btn_restart_app.isEnabled = true
                btn_remark.visibility = View.INVISIBLE
            }
        }
    }


//    private fun startLocationUpdates() {
//        //1
//        if (ActivityCompat.checkSelfPermission(this,
//                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this,
//                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
//                LOCATION_PERMISSION_REQUEST_CODE
//            )
//            return
//        }
//        //2
//        //fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null /* Looper */)
//    }

//    private fun createLocationRequest() {
//        // 1
//        locationRequest = LocationRequest()
//        // 2
//        locationRequest.interval = 10000
//        // 3
//        locationRequest.fastestInterval = 5000
//        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
//
//        val builder = LocationSettingsRequest.Builder()
//            .addLocationRequest(locationRequest)
//
//        // 4
//        val client = LocationServices.getSettingsClient(this)
//        val task = client.checkLocationSettings(builder.build())
//
//        // 5
//        task.addOnSuccessListener {
//            locationUpdateState = true
//            startLocationUpdates()
//        }
//        task.addOnFailureListener { e ->
//            // 6
//            if (e is ResolvableApiException) {
//                // Location settings are not satisfied, but this can be fixed
//                // by showing the user a dialog.
//                try {
//                    // Show the dialog by calling startResolutionForResult(),
//                    // and check the result in onActivityResult().
//                    e.startResolutionForResult(this@MapsActivity,
//                        REQUEST_CHECK_SETTINGS
//                    )
//                } catch (sendEx: IntentSender.SendIntentException) {
//                    // Ignore the error.
//                }
//            }
//        }
//    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            if (resultCode == Activity.RESULT_OK) {
//                locationUpdateState = true
//                startLocationUpdates()
            }
        }
    }

    // 2
    override fun onPause() {
        super.onPause()
        mapsActivityRunning = false
        //if (appState == "run") fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    // 3
    public override fun onResume() {
        super.onResume()

        mapsActivityRunning = true

        updateButtons()

        if (appState == "run") {
//            startLocationUpdates()
            startStep1()
        }
//            Toast.makeText(
//                this,
//                "${LocationMonitoringService.locations.count()} locations",
//                Toast.LENGTH_LONG
//            ).show()
        showTrack()
        val elapsedTimeSeconds =
            if (appState == "bst") 0.0
            else (System.currentTimeMillis() - PrefUtil.getStartTime(applicationContext)) / 1000.0
        showResults(elapsedTimeSeconds)
        //Toast.makeText(this, "Distance = $distance", Toast.LENGTH_LONG).show()

//        for (timeStamp in LocationMonitoringService.timeStamps) {
//
//            val time = returnDateString(timeStamp)
//            println("Time=$time")
//        }

    }

    fun showResults(time: Double) {
        val timeString = getTimeString(time)
        if (time > 0) averageSpeed = (distance / time * 3.6).toString()
        if (averageSpeed.length > 4) averageSpeed = averageSpeed.substring(0, 4)
        println("distance1 = $distance")
        mMsgView!!.text = "${distance.toInt()}m  ${averageSpeed}km/h $timeString"
    }

    private fun getTimeString(timeSeconds: Double) : String {
        val timeHours = timeSeconds / 3600.0
        var timeString = ""
        if (timeHours >= 1) timeString += timeHours.toInt().toString() + ":"
        val timeMinutesString = ((timeSeconds.toInt() % 3600) / 60).toString()
        if (timeMinutesString.length == 1 && timeString.isNotEmpty()) timeString += "0"
        timeString += "$timeMinutesString:"
        val secondsString = (timeSeconds.toInt() % 60).toString()
        if (secondsString.length == 1) timeString += "0"
        timeString += secondsString
        return timeString
    }

    private fun returnDateString(isoString: String) : String {
        // Convert from:
        // 2019-09-13T21:00:17.047Z
        // To:
        // Monday 11:00 PM
        val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        isoFormatter.timeZone = TimeZone.getTimeZone("UTC")
        var convertedDate = Date()
        try {
            convertedDate = isoFormatter.parse(isoString)
        } catch(e: ParseException) {
            Log.d("PARSE", "Cannot parse date '$isoString'")
        }
        val outDateString = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        return outDateString.format(convertedDate)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

//        var firstTime = true
//        var firstMark = true
//        lateinit var marker: Marker
//        locationCallback = object : LocationCallback() {
//            override fun onLocationResult(p0: LocationResult) {
//                super.onLocationResult(p0)
//
//                var prevLocation = p0.lastLocation
//                if (!firstTime) prevLocation = lastLocation
//                else if (mapReady) map.animateCamera(CameraUpdateFactory.newLatLngZoom
//                    (LatLng(prevLocation.latitude, prevLocation.longitude), zoomLevel))
//                firstTime = false
//                lastLocation = p0.lastLocation
//
//                if (appState == "run") {
//
//                    if (!showingTrack) {
//                        showingTrack = true
//                        distance += prevLocation.distanceTo(lastLocation)
//
//                        val elapsedTimeSeconds = (System.currentTimeMillis() - PrefUtil.getStartTime(applicationContext)) / 1000.0
//                        showResults(elapsedTimeSeconds)
//
//                        //if (elapsedTimeSeconds > 20 && locationRequest.fastestInterval != 5000L) locationRequest.fastestInterval = 5000L
//
//                        placeMarkerOnMap(
//                            LatLng(prevLocation.latitude, prevLocation.longitude),
//                            LatLng(lastLocation.latitude, lastLocation.longitude),
//                            lastLocation.speed
//                        )
//                        showingTrack = false
//                    }
//                    map.animateCamera(
//                        CameraUpdateFactory.newLatLng(
//                            LatLng(
//                                lastLocation.latitude,
//                                lastLocation.longitude
//                            )
//                        )
//                    )
////                    val markerOptions = MarkerOptions().position(LatLng(lastLocation.latitude, lastLocation.longitude))
////                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
////                        .title(lastLocation.accuracy.toString())
////                    map.addMarker(markerOptions)
//                }
//                else if (appState == "mrk") {
//                    println("displaying marker for current position lat=${lastCurrentBoardLatLng.latitude} lng=${lastCurrentBoardLatLng.longitude}")
//                    if (!firstMark) {
//                        marker.remove()
//                    }
//                    firstMark = false
//                    lastCurrentBoardLatLng =
//                        FindBoardService.getCurrentBoardPosition()
//                    val markerOptions = MarkerOptions().position(lastCurrentBoardLatLng)
//                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
//                        .title("Look here")
//                    marker = map.addMarker(markerOptions)
//                }
//            }
//        }
        mMsgView = findViewById(R.id.msgView)
        when (appState) {
            "run" -> setUpService()
        }
    }

    private fun placeMarkerOnMap(fromLocation: LatLng, toLocation: LatLng, speed: Float) {
//        // 1
//        val markerOptions = MarkerOptions().position(location)
//        // 2
//        map.addMarker(markerOptions)
        val speedKmh = speed * 3.6f
        //val speedToDisplay = (speedKmh * 100).toInt() / 100.0
        map.addPolyline(
            PolylineOptions().color(getLineColor(speedKmh).toInt())
                .clickable(true)
                .add(LatLng(fromLocation.latitude, fromLocation.longitude),
                    LatLng(toLocation.latitude, toLocation.longitude))
        )
        println("Placing a marker at lat ${toLocation.latitude} lon ${toLocation.longitude}")
        //Toast.makeText(this, "$speedToDisplay km/h", Toast.LENGTH_LONG).show()
    }

    fun getLineColor(speed: Float) : Long {
        // speed and speeds are in km/h
        val speeds = listOf(1, 2, 4, 7, 10, 15, 20, 30, 1000)
        val colors = listOf (
            COLOR_LIGHT_GREEN_ARGB,
            COLOR_GREEN_ARGB,
            COLOR_DARK_GREEN_ARGB,
            COLOR_YELLOW_ARGB,
            COLOR_YELLOW_ORANGE_ARGB,
            COLOR_ORANGE_ARGB,
            COLOR_RED_ARGB,
            COLOR_DARK_RED_ARGB,
            COLOR_MAGENTA_ARGB
        )
        var i = speeds.count() - 2
        while (i>=0 && speed < speeds[i]) i--
        return colors[i + 1]
    }

    private fun work() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // 1
            map.isMyLocationEnabled = true

            // 2
            fusedLocationClient.lastLocation.addOnSuccessListener(this) { location ->
                // Got last known location. In some rare situations this can be null.
                // 3
                if (location != null) {
                    lastLocation = location
                    val currentLatLng = LatLng(location.latitude, location.longitude)
//                    val lat = currentLatLng.latitude
//                    val lon = currentLatLng.longitude
                    //val cameraPosition = CameraPosition.Builder().target(currentLatLng).zoom(zoomLevel).build()
                    //map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, PrefUtil.getZoomLevel(applicationContext)))
                    println("zoomLevel in mapReady = ${PrefUtil.getZoomLevel(applicationContext)}")
                    //Toast.makeText(this, "lat: $lat, lon: $lon", Toast.LENGTH_LONG).show()
                //} else {
                    //Toast.makeText(this, "location is null", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    startStep3()
                    work()
                //} else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return
            }

            // Add other 'when' lines to check for other
            // permissions this app might request.
            else -> {
                // Ignore all other requests.
            }
        }
    }

    private fun setUpMap() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        setUpMap()
        work()
        mapReady = true
        //Toast.makeText(this, "Map ready", Toast.LENGTH_LONG).show()

        // Do this in case it was not done in the onResume because the map was not ready yet
        if (appState != "bst") showTrack()
        if ((appState == "mrk" || appState == "fnd") && lostLatLng.latitude != 32.0 && lostLatLng.longitude != 35.0) {
            showMarkerInLostPosition()
            showMarkerInEndPosition()
            showMarkerInCurrentBoardPosition()
        }

        // Add polylines to the map.
        // Polylines are useful to show a route or some other connection between points.
//        val polyline1 = googleMap.addPolyline(
//            PolylineOptions()
//                .clickable(true)
//                .add(
//                    LatLng(-35.016, 143.321),
//                    LatLng(-34.747, 145.592),
//                    LatLng(-34.364, 147.891),
//                    LatLng(-33.501, 150.217),
//                    LatLng(-32.306, 149.248),
//                    LatLng(-32.491, 147.309)
//                )
//        )
    }

    private fun showTrack() {
        showingTrack = true
        distance = 0.0
        averageSpeed = "0.0"
        var firstLocation = true
        lateinit var previousLocation: Location
        for (location in locations) {
            if (firstLocation) previousLocation = location
            firstLocation = false
            val lat = location.latitude
            val lon = location.longitude
            val time = returnDateString(location.time.toString())
            println("Lat=$lat Lon=$lon time=$time")

            if (mapReady) placeMarkerOnMap(LatLng(previousLocation.latitude, previousLocation.longitude), LatLng(lat, lon), location.speed)
            distance += previousLocation.distanceTo(location)
            previousLocation = location
        }
        if (locations.isNotEmpty()) lastLocation = locations[locations.count() - 1]
        println("distance = $distance")
        showingTrack = false
//        if (LocationMonitoringService.locations.count() > 5) {
//            var txt = ""
//            for (i in 0..5) {
//                //val tm = returnDateString(LocationMonitoringService.locations[i].time.toString())
//                //val tm = returnDateString(LocationMonitoringService.timeStamps[i])
//                val tm = System.currentTimeMillis() - LocationMonitoringService.locations[i].time
//                txt += "${LocationMonitoringService.locations[i].latitude}  ${LocationMonitoringService.locations[i].longitude}  $tm\n"
//            }
//            Toast.makeText(this,txt, Toast.LENGTH_LONG).show()
//        }
    }

//    private fun startLocationUpdates() {
//        var locationRequest : LocationRequest
//        fusedLocationClient.requestLocationUpdates(locationRequest,
//            locationCallback,
//            null /* Looper */)
//    }


    /**
     * Step 1: Check Google Play services
     */
    private fun startStep1() {
        //Check whether this user has installed Google play service which is being used by Location updates.
        if (isGooglePlayServicesAvailable)
        {
            //Passing null to indicate that it is executing for the first time.
            startStep2(null)
        }
        else
        {
            Toast.makeText(applicationContext,
                R.string.no_google_playservice_available, Toast.LENGTH_LONG).show()
        }
    }


    /**
     * Step 2: Check & Prompt Internet connection
     */
    private fun startStep2(dialog:DialogInterface?):Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetworkInfo = connectivityManager.activeNetworkInfo

        if (activeNetworkInfo == null || !activeNetworkInfo.isConnected)
        {
            promptInternetConnect()
            return false
        }

        dialog?.dismiss()

        //Yes there is active internet connection. Next check Location is granted by user or not.

        if (checkPermissions())
        { //Yes permissions are granted by the user. Go to the next step.
            startStep3()
        }
        else
        {  //No user has not granted the permissions yet. Request now.
            requestPermissions()
        }
        return true
    }

    /**
     * Show A Dialog with button to refresh the internet state.
     */
    private fun promptInternetConnect() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.title_alert_no_intenet)
        builder.setMessage(R.string.msg_alert_no_internet)

        val positiveText = getString(R.string.btn_label_refresh)
        builder.setPositiveButton(positiveText) { dialog, which ->
            //Block the Application Execution until user grants the permissions
            if (startStep2(dialog)) {
                //Now make sure about location permission.
                if (checkPermissions()) {
                    //Step 2: Start the Location Monitor Service
                    //Everything is there to start the service.
                    startStep3()
                } else if (!checkPermissions()) {
                    requestPermissions()
                }
            }
        }

        val dialog = builder.create()
        dialog.show()
    }

    /**
     * Step 3: Start the Location Monitor Service
     */
    private fun startStep3() {
        //And it will be keep running until you close the entire application from task manager.
        //This method will executed only once.
        if (!mAlreadyStartedService && mMsgView != null)
        {
            mMsgView!!.setText(R.string.msg_location_service_started)
            //Start location sharing service to app server.........
            val intent = Intent(this, LocationMonitoringService::class.java)
            startService(intent)

            mAlreadyStartedService = true //Ends................................................
        }
    }


    /**
     * Return the current state of the permissions needed.
     */
    private fun checkPermissions():Boolean {
        val permissionState1 = ActivityCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_FINE_LOCATION)

        val permissionState2 = ActivityCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_COARSE_LOCATION)

        return permissionState1 == PackageManager.PERMISSION_GRANTED && permissionState2 == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Start permissions requests.
     */
    private fun requestPermissions() {
        val shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(this,
            Manifest.permission.ACCESS_FINE_LOCATION)

        val shouldProvideRationale2 = ActivityCompat.shouldShowRequestPermissionRationale(this,
            Manifest.permission.ACCESS_COARSE_LOCATION)

        // Provide an additional rationale to the img_user. This would happen if the img_user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldProvideRationale || shouldProvideRationale2)
        {
            Log.i(TAG, "Displaying permission rationale to provide additional context.")
            showSnackbar(
                R.string.permission_rationale,
                android.R.string.ok, View.OnClickListener {
                    // Request permission
                    ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        REQUEST_PERMISSIONS_REQUEST_CODE
                    )
                })
        }
        else
        {
            Log.i(TAG, "Requesting permission")
            // Request permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the img_user denied the permission
            // previously and checked "Never ask again".
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_PERMISSIONS_REQUEST_CODE
            )
        }
    }


    /**
     * Shows a [Snackbar].
     *
     * @param mainTextStringId The id for the string resource for the Snackbar text.
     * @param actionStringId   The text of the action item.
     * @param listener         The listener associated with the Snackbar action.
     */
    private fun showSnackbar(mainTextStringId:Int, actionStringId:Int, listener: View.OnClickListener) {
        Snackbar.make(
            findViewById(android.R.id.content), getString(mainTextStringId),
            Snackbar.LENGTH_INDEFINITE
        ).setAction(getString(actionStringId), listener).show()
    }

    /**
     * Callback received when a permissions request has been completed.
     */
//    override fun onRequestPermissionsResult(requestCode:Int, permissions:Array<String>, grantResults:IntArray) {
//        Log.i(TAG, "onRequestPermissionResult")
//        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE)
//        {
//            if (grantResults.size <= 0)
//            {
//                // If img_user interaction was interrupted, the permission request is cancelled and you
//                // receive empty arrays.
//                Log.i(TAG, "User interaction was cancelled.")
//            }
//            else if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
//            {
//                Log.i(TAG, "Permission granted, updates requested, starting location updates")
//                startStep3()
//            }
//            else
//            {
//                // Permission denied.
//                // Notify the img_user via a SnackBar that they have rejected a core permission for the
//                // app, which makes the Activity useless. In a real app, core permissions would
//                // typically be best requested during a welcome-screen flow.
//
//                // Additionally, it is important to remember that a permission might have been
//                // rejected without asking the img_user for permission (device policy or "Never ask
//                // again" prompts). Therefore, a img_user interface affordance is typically implemented
//                // when permissions are denied. Otherwise, your app could appear unresponsive to
//                // touches or interactions which have required permissions.
//                showSnackbar(R.string.permission_denied_explanation,
//                    R.string.settings, object: View.OnClickListener {
//                        override fun onClick(view: View) {
//                            // Build intent that displays the App settings screen.
//                            val intent = Intent()
//                            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
//                            val uri = Uri.fromParts("package",
//                                BuildConfig.APPLICATION_ID, null)
//                            intent.data = uri
//                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
//                            startActivity(intent)
//                        }
//                    })
//            }
//        }
//    }


    public override fun onDestroy() {
        //Stop location sharing service to app server.........
        //stopService(Intent(this, LocationMonitoringService::class.java))
        //mAlreadyStartedService = false
        //Ends................................................
        if (mapReady) PrefUtil.setZoomLevel(applicationContext, map.cameraPosition.zoom)
        println("zoomLevel=${PrefUtil.getZoomLevel(applicationContext)}")
        super.onDestroy()
    }

    fun stopClicked(view: View) {
        //Toast.makeText(this, "Finish clicked", Toast.LENGTH_LONG).show()
        //Stop location sharing service to app server.........
//        stopService(Intent(this, LocationMonitoringService::class.java))
//        mAlreadyStartedService = false
        appState = "stp"
        updateButtons()
    }

    fun startClicked(view: View) {

        PrefUtil.setStartTime(applicationContext, System.currentTimeMillis())

        setUpService()

        appState = "run"
        updateButtons()
    }

    fun setUpService() {
        //createLocationRequest()

        LocalBroadcastManager.getInstance(this).registerReceiver(
            object: BroadcastReceiver() {
                override fun onReceive(context: Context, intent:Intent) {
                    //Toast.makeText(applicationContext, "Broadcast receiver works.", Toast.LENGTH_LONG).show()
                    val location = intent.getParcelableExtra<Location>(LocationMonitoringService.EXTRA_LOCATION)

                    if (location.latitude != null && location.longitude != null)
                    {
                        println("From service latitude=${location.latitude} longitude=${location.longitude} speed=${location.speed} time=${location.time}")
                        //mMsgView!!.text = getString(R.string.msg_location_service_started) + "\n Latitude : " + latitude + "\n Longitude: " + longitude

                        var prevLocation = location

                        if (!firstTime) prevLocation = lastLocation

                        else if (mapReady) map.animateCamera(CameraUpdateFactory.newLatLngZoom
                            (LatLng(prevLocation.latitude, prevLocation.longitude), PrefUtil.getZoomLevel(applicationContext)))
                        firstTime = false
                        lastLocation = location

                        if (mapReady) {
                            if (appState == "run") {

                                if (!showingTrack) {
                                    showingTrack = true
                                    distance += prevLocation.distanceTo(lastLocation)

                                    val elapsedTimeSeconds = (System.currentTimeMillis() - PrefUtil.getStartTime(applicationContext)) / 1000.0
                                    showResults(elapsedTimeSeconds)
                                    placeMarkerOnMap(
                                        LatLng(prevLocation.latitude, prevLocation.longitude),
                                        LatLng(lastLocation.latitude, lastLocation.longitude),
                                        lastLocation.speed
                                    )
                                    showingTrack = false
                                }
                                map.animateCamera(CameraUpdateFactory.newLatLng(LatLng(lastLocation.latitude, lastLocation.longitude)))
                            } else if (appState == "mrk") {
                                val timeString = getTimeString((System.currentTimeMillis() - lostTimeStamp) / 1000.0)
                                currentBoardLocationMarker.snippet = timeString
                                println("timeString=$timeString")
                                lastCurrentBoardLatLng = FindBoardService.getCurrentBoardPosition()
                                currentBoardLocationMarker.position = lastCurrentBoardLatLng
                                println("displaying marker for current position lat=${lastCurrentBoardLatLng.latitude} lng=${lastCurrentBoardLatLng.longitude}"+
                                " visibility=${currentBoardLocationMarker.isVisible}")
                            }
                        }
                    }
                }
            }, IntentFilter(LocationMonitoringService.ACTION_LOCATION_BROADCAST)
        )
        startStep1()
    }

    fun markClicked (view: View) {
        lostLatLng = map.getProjection().getVisibleRegion().latLngBounds.getCenter()
        showMarkerInLostPosition()

        val lastTrackedLocation =
            if (locations.count() > 0) locations[locations.count() - 1]
            else lastLocation
        endLatLng = LatLng(lastTrackedLocation.latitude, lastTrackedLocation.longitude)
        endTimeStamp = lastTrackedLocation.time
        lostTimeStamp = FindBoardService.getLostBoardTimeStamp()
        showMarkerInEndPosition()
        lastCurrentBoardLatLng = FindBoardService.getCurrentBoardPosition()
        showMarkerInCurrentBoardPosition()
        appState = "mrk"
        updateButtons()
    }

    fun remarkClicked (view: View) {
        lostPositionMarker.remove()
        currentBoardLocationMarker.remove()
        appState = "stp"
        updateButtons()
    }

    private fun showMarkerInCurrentBoardPosition() {
        val timeString = getTimeString((System.currentTimeMillis() - lostTimeStamp) / 1000.0)
        val markerOptions = MarkerOptions().position(lastCurrentBoardLatLng)
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
            .title("Look here")
            .snippet(timeString)
        currentBoardLocationMarker = map.addMarker(markerOptions)
        //currentBoardLocationMarker.showInfoWindow()
        println("placed marker in ${lastCurrentBoardLatLng.latitude}, ${lastCurrentBoardLatLng.longitude}")
    }

    private fun showMarkerInEndPosition() {
        if (SHOW_END_POSITION) {
            val markerOptions = MarkerOptions().position(endLatLng)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA))
                .title("Reported")
            map.addMarker(markerOptions)
        }
    }

    private fun showMarkerInLostPosition()
    {
        val markerOptions = MarkerOptions().position(lostLatLng).title("Lost")
        lostPositionMarker = map.addMarker(markerOptions)
    }

    fun foundClicked (view: View) {
        appState = "fnd"
        updateButtons()
    }

    fun restartClicked (view: View) {
        stopService()
        showResults(0.0)
        val intent = Intent(baseContext, this::class.java)
        val pendingIntentId = 101
        val pendingIntent = PendingIntent.getActivity(this, pendingIntentId,intent, PendingIntent.FLAG_CANCEL_CURRENT)
        val alarmManager = (this.getSystemService(Context.ALARM_SERVICE)) as AlarmManager
        alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 100, pendingIntent)
        exitProcess(0)
    }

    fun quitClicked (view: View) {
        appState = "bst"
        distance = 0.0
        locations.clear()
        stopService()
        this.finish()
    }

    fun stopService() {
        stopService(Intent(this, LocationMonitoringService::class.java))
        mAlreadyStartedService = false
    }

    private fun alertDialog(title: String, message: String) {
        // build alert dialog
        val dialogBuilder = AlertDialog.Builder(this)

        // set message of alert dialog
        dialogBuilder.setMessage(message)
            // if the dialog is cancelable
            .setCancelable(false)
            // positive button text and action
//            .setPositiveButton("OK", DialogInterface.OnClickListener {
//                    dialog, id -> finish()
//            })
            // negative button text and action
            .setNegativeButton("OK", DialogInterface.OnClickListener {
                    dialog, id -> dialog.cancel()
            })

        // create dialog box
        val alert = dialogBuilder.create()
        // set title for alert dialog box
        alert.setTitle(title)
        // show alert dialog
        alert.show()
    }

    fun helpClicked (view: View) {
        when (appState) {
            "bst" -> alertDialog("How to start?", "Click START when starting your activity. The app will keep track of your movement.")
            "run" -> alertDialog("When landing your kite", "Click STOP when you land your kite. The app will stop keeping track of your movement.")
            "stp" -> alertDialog("Mark lost position", "Use the colored track to speculate where you lost your board." +
                    " Pan the map such that this point will be in the center of the target, then click MARK.")
            "mrk" -> alertDialog("Report when found", "The estimated location of where you lost your board is marked in red." +
                    " The estimated current location of your board is marked in orange. When you find it," +
                    " click FOUND such that the app can improve its locating algorithm. Thank you!")
            "fnd" -> alertDialog("Restart", "Click RESTART to restart the app.")
        }
    }
}
