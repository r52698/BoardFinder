package com.example.org.boardfinder.Controller

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
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
import com.example.org.boardfinder.Services.CommunicationService
import com.example.org.boardfinder.Services.LocationMonitoringService.Companion.stopIndex
import com.example.org.boardfinder.Utilities.*
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
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

    private var mAlreadyStartedService = false
    private var mMsgView: TextView? = null

    private var distance = 0.0
    private var averageSpeed = "0.0"

    private var mapReady = false

    private var lastCurrentBoardLatLng = LatLng(32.0, 35.0)

    private var showingTrack = false

    var polylines = ArrayList<Polyline>()

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

        private val TAG = MapsActivity::class.java.simpleName

        /**
         * Code used in requesting runtime permissions.
         */
        private const val REQUEST_PERMISSIONS_REQUEST_CODE = 34

        var appState = "bst"
        // bst, run, pas, stp, mrk, fnd

        var lostLatLng = LatLng(32.0, 35.0)
        var landLatLng = LatLng(32.0, 35.0)
        var lostTimeStamp = 0L
        var landTimeStamp = 0L
        var mapsActivityRunning = false

        var firstTime = true
        lateinit var currentBoardLocationMarker: Marker
        lateinit var lostPositionMarker: Marker
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mMsgView = findViewById(R.id.msgView)
        when (appState) {
            "run" -> setUpService()
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.putString(EXTRA_TEXT, msgView.text.toString())
        outState?.putString(EXTRA_STATE,
            appState
        )
        outState?.putDouble(EXTRA_LOST_LAT, lostLatLng.latitude)
        outState?.putDouble(EXTRA_LOST_LNG, lostLatLng.longitude)
        outState?.putDouble(EXTRA_END_LAT, landLatLng.latitude)
        outState?.putDouble(EXTRA_END_LNG, landLatLng.longitude)
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
            landLatLng = LatLng(savedInstanceState.getDouble(EXTRA_END_LAT),
                savedInstanceState.getDouble(EXTRA_END_LNG))
            lastCurrentBoardLatLng = LatLng(savedInstanceState.getDouble(EXTRA_CURRENT_LAT),
                savedInstanceState.getDouble(EXTRA_CURRENT_LNG))
            PrefUtil.setZoomLevel(applicationContext, savedInstanceState.getFloat(EXTRA_ZOOM_LEVEL))
            //createLocationRequest()
        }
    }

    override fun onPause() {
        super.onPause()
        mapsActivityRunning = false
        if (mapReady) PrefUtil.setZoomLevel(applicationContext, map.cameraPosition.zoom)
        println("zoomLevel=${PrefUtil.getZoomLevel(applicationContext)}")
    }

    public override fun onResume() {
        super.onResume()

        mapsActivityRunning = true

        updateButtons()

        if (appState == "run") {
            startStep1()
        }

        showTrack()
        val elapsedTimeSeconds =
            if (appState == "bst") 0.0
            else (System.currentTimeMillis() - PrefUtil.getStartTime(applicationContext)) / 1000.0
        showResults(elapsedTimeSeconds)

        if (mapReady) {
            map.animateCamera(CameraUpdateFactory.zoomTo(PrefUtil.getZoomLevel(applicationContext)))
        }
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

    private fun placePolyline(fromLocation: LatLng, toLocation: LatLng, speed: Float) {

        val speedKmh = speed * 3.6f
        val polyline = map.addPolyline(
            PolylineOptions().color(getLineColor(speedKmh).toInt())
                .clickable(true)
                .add(LatLng(fromLocation.latitude, fromLocation.longitude),
                    LatLng(toLocation.latitude, toLocation.longitude))
        )
        polylines.add(polyline)
    }

    private fun getLineColor(speed: Float) : Long {
        // speed and speeds are in km/h
        val speeds = listOf(1, 2, 4, 7, 10, 15, 20, 30, Int.MAX_VALUE)
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
        var x = speeds.binarySearch(speed.toInt())
        if (x < 0) x = -x - 2
        return colors[x + 1]
    }

    private fun showCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            map.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener(this) { location ->
                // Got last known location. In some rare situations this can be null.
                if (location != null) {
                    lastLocation = location
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, PrefUtil.getZoomLevel(applicationContext)))
                    println("zoomLevel in mapReady = ${PrefUtil.getZoomLevel(applicationContext)}")
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
                    showCurrentLocation()
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
        showCurrentLocation()
        mapReady = true
        //Toast.makeText(this, "Map ready", Toast.LENGTH_LONG).show()

        // Do this in case it was not done in the onResume because the map was not ready yet
        if (appState != "bst") showTrack()
        if ((appState == "mrk" || appState == "fnd") && lostLatLng.latitude != 32.0 && lostLatLng.longitude != 35.0) {
            showMarkerInLostPosition()
            showMarkerInLandPosition()
            showMarkerInCurrentBoardPosition()
        }
    }

    private fun showTrack() {
        showingTrack = true
        distance = 0.0
        averageSpeed = "0.0"
        var firstLocation = true
        lateinit var previousLocation: Location
        for (polyline in polylines) {
            polyline.remove()
        }
        polylines.clear()
        val lastIndex =
            if (stopIndex > 0) stopIndex
            else locations.count() - 1
        for (i in 0..lastIndex) {
            val location = locations[i]
            if (firstLocation) previousLocation = location
            firstLocation = false
            val lat = location.latitude
            val lon = location.longitude
            val time = returnDateString(location.time.toString())
            println("Lat=$lat Lon=$lon time=$time")

            if (mapReady) placePolyline(LatLng(previousLocation.latitude, previousLocation.longitude), LatLng(lat, lon), location.speed)
            distance += previousLocation.distanceTo(location)
            previousLocation = location
        }
        if (locations.isNotEmpty()) lastLocation = locations[locations.count() - 1]
        println("distance = $distance")
        showingTrack = false
    }

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

    fun stopClicked(view: View) {
        stopIndex = locations.count() - 1
        println("stopIndex=$stopIndex time=${locations[stopIndex].time}")
        val lastTrackedLocation =
            if (locations.count() > 0) locations[stopIndex]
            else lastLocation
        landLatLng = LatLng(lastTrackedLocation.latitude, lastTrackedLocation.longitude)
        landTimeStamp = lastTrackedLocation.time
        CommunicationService.landLocationMessage = "Land ${lastTrackedLocation.latitude}" +
                " ${lastTrackedLocation.longitude} ${lastTrackedLocation.time} ${lastTrackedLocation.speed}"
        CommunicationService.transmitLandLocationMessage()
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

        LocalBroadcastManager.getInstance(this).registerReceiver(
            object: BroadcastReceiver() {
                override fun onReceive(context: Context, intent:Intent) {
                    //Toast.makeText(applicationContext, "Broadcast receiver works.", Toast.LENGTH_LONG).show()
                    val location = intent.getParcelableExtra<Location>(LocationMonitoringService.EXTRA_LOCATION)
                    val comment = intent.getStringExtra("EXTRA_COMMENT")
                    if (comment.isNotEmpty()) alertDialog("Error", comment)

                    if (location.latitude != null && location.longitude != null)
                    {
                        println("From service latitude=${location.latitude} longitude=${location.longitude} speed=${location.speed} time=${location.time}")

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
                                    placePolyline(
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

        val lostIndex = FindBoardService.getLostBoardIndex()
        lostTimeStamp = locations[lostIndex].time
        showMarkerInLandPosition()
        lastCurrentBoardLatLng = FindBoardService.getCurrentBoardPosition()
        showMarkerInCurrentBoardPosition()

        CommunicationService.lostLocationMessage = "Lost ${lostLatLng.latitude} ${lostLatLng.longitude} $lostTimeStamp ${locations[lostIndex].speed}"
        CommunicationService.transmitLostLocationMessage()
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

    private fun showMarkerInLandPosition() {
        if (SHOW_END_POSITION) {
            val markerOptions = MarkerOptions().position(landLatLng)
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
        CommunicationService.foundLocationMessage = "Found ${locations.count() - 1} ${lastLocation.latitude} ${lastLocation.longitude} ${lastLocation.time} ${lastLocation.speed}"
        CommunicationService.transmitFoundLocationMessage()
        appState = "fnd"
        updateButtons()
    }

    fun restartClicked (view: View) {
        stopIndex = 0
        appState = "bst"
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
        stopIndex = 0
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

    fun helpClicked (view: View) {
        when (appState) {
            "bst" -> alertDialog("How to start?", "Click START when starting your activity. The app will keep track of your movement.")
            "run" -> alertDialog("When landing your kite", "Click STOP when you land your kite. The app will stop keeping track of your movement.")
            "stp" -> alertDialog("Mark lost position", "Use the colored track to speculate where you lost your board." +
                    " Pan the map such that this point will be in the center of the target, then click MARK.")
            "mrk" -> alertDialog("Report when found", "The estimated location of where you lost your board is marked in red." +
                    " The estimated current location of your board is marked in orange. When you find it," +
                    " click FOUND such that the app can improve its locating algorithm. Thank you!")
            "fnd" -> alertDialog("Restart / Quit", "Click RESTART to restart the app or QUIT to quit.")
        }
    }
}
