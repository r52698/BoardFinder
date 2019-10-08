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
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.org.boardfinder.Model.Channel
import com.example.org.boardfinder.Model.Message
import com.example.org.boardfinder.Services.LocationMonitoringService.Companion.locations
import com.example.org.boardfinder.R
import com.example.org.boardfinder.Services.*
import com.example.org.boardfinder.Services.LocationMonitoringService.Companion.stopIndex
import com.example.org.boardfinder.Services.MessageService.sessions
import com.example.org.boardfinder.Utilities.*
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*

import com.google.android.material.snackbar.Snackbar
import io.socket.client.IO
import io.socket.emitter.Emitter
import kotlinx.android.synthetic.main.activity_maps.*
import java.sql.Timestamp
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

    var channelSelected = false

    var sessionsTimestamps = ArrayList<String>()

    var stopIndexFound = false

    var firstTime = true
    lateinit var currentBoardLocationMarker: Marker
    lateinit var lostPositionMarker: Marker
    lateinit var foundLocationMarker: Marker
    lateinit var landLocationMarker: Marker

    var foundLatLng = LatLng(32.0, 35.0)
    var foundTimeStamp = 0L

    var selectedSessionTimestamp = ""
    lateinit var channelAdapter: ArrayAdapter<Channel>
    lateinit var sessionsAdapter: ArrayAdapter<String>

    private val expirationTimestamp = Timestamp(119, 9, 13, 21, 0, 0, 0)


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

        var selectedChannel : Channel? = null
        val socket = IO.socket(SOCKET_URL)
        var lostLatLng = LatLng(32.0, 35.0)
        var landLatLng = LatLng(32.0, 35.0)
        var lostTimeStamp = 0L
        var landTimeStamp = 0L

        var admin = false
        var mapsActivityRunning = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        spinner.visibility = View.INVISIBLE

        mMsgView = findViewById(R.id.msgView)
        when (appState) {
            "run" -> setUpService()
        }
        println("Checking App.prefs.isLoggedIn=${App.prefs.isLoggedIn}")
        if (App.prefs.isLoggedIn) {
            AuthService.findUserByEmail(this) {}
        }
        socket.connect()
        socket.on("channelCreated", onNewChannel)
        socket.on("messageCreated", onNewMessage)

        channelAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, MessageService.channels)
        channel_list.adapter = channelAdapter

        LocalBroadcastManager.getInstance(this).registerReceiver(userDataChangeReceiver,
            IntentFilter(BROADCAST_USER_DATA_CHANGE))

        channel_list.setOnItemClickListener { _, _, i, _ ->
            spinner.visibility = View.VISIBLE
            selectedChannel = MessageService.channels[i]
            MessageService.getMessages(selectedChannel!!.id) { getMessagesSuccess ->
                if (getMessagesSuccess) {
                    createSessionsList()
                    channelSelected = true
                    channel_list.visibility = View.INVISIBLE
                }
            }
            spinner.visibility = View.INVISIBLE
        }

        sessions_list.setOnItemClickListener { _, _, i, _ ->
            selectedSessionTimestamp = sessionsTimestamps[i]
            if (selectedSessionTimestamp.equals("Back to users list")) {
                channel_list.visibility = View.VISIBLE
            } else {
                MessageService.createSessions(selectedSessionTimestamp) // Gather all messages belong to the selected session
                reset()
                println("locations.count=${locations.count()} after clear")
                var i = 0
                for (message in sessions) {
                    handleMessage(message.message)
                    i++
                }
                println("Total $i messages handled.")
            }
            sessions_list.visibility = View.INVISIBLE
            showAll()
        }
    }

    fun showAll() {
        println("locations.count=${locations.count()}")
        clearAllMarkers()
        val lastIndex =
            if (stopIndex > 0) stopIndex
            else if (locations.isNotEmpty()) locations.count() - 1
            else 0
        val elapsedTimeSeconds =
            if (locations.isNotEmpty()) (locations[lastIndex].time - locations[0].time) / 1000.0
            else 0.0
        showTextResults(elapsedTimeSeconds)
        showTrack()
        if (mapReady && stopIndex > 0) {
            showMarkerInLandPosition()
            showMarkerInLostPosition()
            showMarkerInFoundPosition()
            showMarkerInCurrentBoardPosition()
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(lostLatLng, PrefUtil.getZoomLevel(applicationContext)))
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
        outState?.putDouble(EXTRA_FOUND_LAT, foundLatLng.latitude)
        outState?.putDouble(EXTRA_FOUND_LNG, foundLatLng.longitude)
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
            foundLatLng = LatLng(savedInstanceState.getDouble(EXTRA_FOUND_LAT),
                savedInstanceState.getDouble(EXTRA_FOUND_LNG))
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

        if (!App.prefs.isLoggedIn) appState = "lgn"

        mapsActivityRunning = true

        updateButtons()

        if (appState == "run") {
            startStep1()
        }

        if (admin) showAll() else showTrack()
        val elapsedTimeSeconds =
            if (appState == "bst") 0.0
            else (System.currentTimeMillis() - PrefUtil.getStartTime(applicationContext)) / 1000.0
        showTextResults(elapsedTimeSeconds)

        if (mapReady) {
            map.animateCamera(CameraUpdateFactory.zoomTo(PrefUtil.getZoomLevel(applicationContext)))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(userDataChangeReceiver)
        socket.disconnect()
    }

    fun showTextResults(time: Double) {
        println("showTextResults time=$time")
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

    private fun placePolyline(fromLocation: LatLng, toLocation: LatLng, speed: Float, thinLine: Boolean) {
        println("placing polyline")
        val speedKmh = speed * 3.6f
        val polyline = map.addPolyline(
            PolylineOptions().color(getLineColor(speedKmh).toInt())
                .clickable(true)
                .add(LatLng(fromLocation.latitude, fromLocation.longitude),
                    LatLng(toLocation.latitude, toLocation.longitude))
        )
        if (thinLine) {
//            val DOT = Dot()
//            val GAP = Gap(3f)
//            val PATTERN_POLYLINE_DOTTED = Arrays.asList(GAP, DOT)
//            polyline.setPattern(PATTERN_POLYLINE_DOTTED)
            polyline.width = 5f
        }
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
        println("Inside showCurrentLocation")
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            map.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener(this) { location ->
                // Got last known location. In some rare situations this can be null.
                if (location != null) {
                    lastLocation = location
                    println("lastLocation is $lastLocation")
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
        println("onMapReady")
        map = googleMap
        setUpMap()
        showCurrentLocation()
        mapReady = true
        //Toast.makeText(this, "Map ready", Toast.LENGTH_LONG).show()

        // Do this in case it was not done in the onResume because the map was not ready yet
        if (appState != "bst") showTrack()
        if (admin) showAll()
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
        clearAllPolylines()
        val lastIndex =
            if (!admin && stopIndex > 0) stopIndex
            else locations.count() - 1
        println("Showing track lastIndex=$lastIndex")
        if (locations.isNotEmpty()) {
            for (i in 0..lastIndex) {
                val location = locations[i]
                if (firstLocation) previousLocation = location
                firstLocation = false
                val lat = location.latitude
                val lon = location.longitude
                val time = returnDateString(location.time.toString())
                println("Lat=$lat Lon=$lon time=$time")

                val thinLine = admin && stopIndexFound && (i > stopIndex)
                if (mapReady) placePolyline(
                    LatLng(
                        previousLocation.latitude,
                        previousLocation.longitude
                    ), LatLng(lat, lon), location.speed, thinLine
                )
                if (i <= stopIndex) distance += previousLocation.distanceTo(location)
                previousLocation = location
            }
            lastLocation = locations[locations.count() - 1]
        }
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
            println("service started successfully in step3")
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
        if (locations.isNotEmpty()) {
            stopIndex = locations.count() - 1
            println("stopIndex=$stopIndex time=${locations[stopIndex].time}")
            val lastTrackedLocation = locations[stopIndex]

            landLatLng = LatLng(lastTrackedLocation.latitude, lastTrackedLocation.longitude)
            landTimeStamp = lastTrackedLocation.time
            val timestamp = Timestamp(PrefUtil.getStartTime(applicationContext))
            CommunicationService.landLocationMessage =
                "$timestamp Land ${lastTrackedLocation.latitude}" +
                        " ${lastTrackedLocation.longitude} ${lastTrackedLocation.time} ${lastTrackedLocation.speed}"
            CommunicationService.transmitLandLocationMessage()
            appState = "stp"
            updateButtons()
        }
    }

    fun startClicked(view: View) {

        if (System.currentTimeMillis() > expirationTimestamp.time) {
            alertDialog("License expired!", "This app can't be used in this version. Please request the latest version.")
        } else {

            PrefUtil.setStartTime(applicationContext, System.currentTimeMillis())

            setUpService()

            appState = "run"
            updateButtons()
        }
    }

    fun setUpService() {

        LocalBroadcastManager.getInstance(this).registerReceiver(
            object: BroadcastReceiver() {
                override fun onReceive(context: Context, intent:Intent) {
                    //Toast.makeText(applicationContext, "Broadcast receiver works.", Toast.LENGTH_LONG).show()
                    val location = intent.getParcelableExtra<Location>(LocationMonitoringService.EXTRA_LOCATION)
                    val comment = intent.getStringExtra("EXTRA_COMMENT")
                    if (comment.isNotEmpty()) {
                        if (comment.equals("Last packet transmitted")) {
                            stopService()
                            // After FOUND clicked, we are waiting for the service to stop, and only then we enable
                            // the quit and restart buttons
                            btn_quit_app.isEnabled = true
                            btn_restart_app.isEnabled = true
                            spinner.visibility = View.INVISIBLE
                        } else {
                            alertDialog("Error", comment)
                        }
                    }

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
                                    showTextResults(elapsedTimeSeconds)
                                    placePolyline(
                                        LatLng(prevLocation.latitude, prevLocation.longitude),
                                        LatLng(lastLocation.latitude, lastLocation.longitude),
                                        lastLocation.speed, false
                                    )
                                    showingTrack = false
                                }
                                map.animateCamera(CameraUpdateFactory.newLatLng(LatLng(lastLocation.latitude, lastLocation.longitude)))
                            } else if (appState == "mrk") {
                                val timeString = getTimeString((System.currentTimeMillis() - lostTimeStamp) / 1000.0)
                                currentBoardLocationMarker.snippet = timeString
                                println("timeString=$timeString")
                                lastCurrentBoardLatLng = FindBoardService.getCurrentBoardPosition(System.currentTimeMillis())
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
        lastCurrentBoardLatLng = FindBoardService.getCurrentBoardPosition(System.currentTimeMillis())
        showMarkerInCurrentBoardPosition()

        val timestamp = Timestamp(PrefUtil.getStartTime(applicationContext))
        CommunicationService.lostLocationMessage = "$timestamp Lost ${lostLatLng.latitude} ${lostLatLng.longitude} $lostTimeStamp ${locations[lostIndex].speed}"
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
        val currentTime =
            if (admin) foundTimeStamp
            else System.currentTimeMillis()
        val timeString = getTimeString((currentTime - lostTimeStamp) / 1000.0)
        val markerOptions = MarkerOptions().position(lastCurrentBoardLatLng)
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
            .title("Look here")
            .snippet(timeString)
        currentBoardLocationMarker = map.addMarker(markerOptions)
        //currentBoardLocationMarker.showInfoWindow()
        println("placed marker in ${lastCurrentBoardLatLng.latitude}, ${lastCurrentBoardLatLng.longitude}")
    }

    private fun showMarkerInLandPosition() {
        if (SHOW_END_POSITION || admin) {
            val snippet =
                if (admin) "${Timestamp(landTimeStamp)}"
                else ""
            val markerOptions = MarkerOptions().position(landLatLng)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA))
                .title("Reported").snippet(snippet)
            landLocationMarker = map.addMarker(markerOptions)
            println("showMarkerInLandPosition ${landLatLng.latitude} ${landLatLng.longitude}")
        }
    }

    private fun showMarkerInLostPosition()
    {
        val markerOptions = MarkerOptions().position(lostLatLng).title("Lost")
            .snippet("${Timestamp(lostTimeStamp)}")
        lostPositionMarker = map.addMarker(markerOptions)
    }

    private fun showMarkerInFoundPosition()
    {
        val timeString = Timestamp(foundTimeStamp).toString()
        val markerOptions = MarkerOptions().position(foundLatLng).title("Found")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
            .snippet(timeString)
        foundLocationMarker = map.addMarker(markerOptions)
    }

    fun foundClicked (view: View) {
        spinner.visibility = View.VISIBLE
        val timestamp = Timestamp(PrefUtil.getStartTime(applicationContext))
        CommunicationService.foundLocationMessage = "$timestamp Found ${locations.count() - 1} ${lastLocation.latitude}" +
                " ${lastLocation.longitude} ${lastLocation.time} ${lastLocation.speed}"
        CommunicationService.transmitFoundLocationMessage()
        appState = "fnd"
        updateButtons()
        btn_quit_app.isEnabled = false
        btn_restart_app.isEnabled = false
    }

    fun restartClicked (view: View) {
//        stopIndex = 0
//        appState = "bst"
//        println("appState change coming from here 3")
//        locations.clear()
//        if (mapReady) map.clear()
//        showTextResults(0.0)
        val intent = Intent(baseContext, this::class.java)
        val pendingIntentId = 101
        val pendingIntent = PendingIntent.getActivity(this, pendingIntentId,intent, PendingIntent.FLAG_CANCEL_CURRENT)
        val alarmManager = (this.getSystemService(Context.ALARM_SERVICE)) as AlarmManager
        alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 100, pendingIntent)
        exitProcess(0)
//        updateButtons()
    }

    fun quitClicked (view: View) {
        stopIndex = 0
        appState = "bst"
        println("appState change coming from here 2")
        distance = 0.0
        locations.clear()
        updateButtons()
        finish()
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
        println("updateButtons appState=$appState")
        when (appState) {
            "lgn" ->{
                btn_login.visibility = View.VISIBLE
                btn_start_tracking.visibility = View.INVISIBLE
                btn_stop_tracking.visibility = View.INVISIBLE
                btn_mark_cul.visibility = View.INVISIBLE
                btn_quit_app.visibility = View.INVISIBLE
                btn_restart_app.visibility = View.INVISIBLE
            }
            "bst" ->{
                btn_start_tracking.visibility = if (admin) View.INVISIBLE else View.VISIBLE
                btn_quit_app.visibility = View.INVISIBLE
                btn_restart_app.visibility = View.INVISIBLE
                btn_login.visibility = View.VISIBLE
            }
            "run" ->{
                btn_login.visibility = View.INVISIBLE
                btn_stop_tracking.visibility = View.VISIBLE
                btn_start_tracking.visibility = View.INVISIBLE
            }
            "stp" ->{
                btn_login.visibility = View.INVISIBLE
                btn_stop_tracking.visibility = View.INVISIBLE
                btn_mark_cul.visibility = View.VISIBLE
                targetImageView.visibility = View.VISIBLE
                btn_remark.visibility = View.INVISIBLE
                btn_report_found.visibility = View.INVISIBLE
            }
            "mrk" ->{
                btn_mark_cul.visibility = View.INVISIBLE
                btn_report_found.visibility = View.VISIBLE
                btn_start_tracking.visibility = View.INVISIBLE
                targetImageView.visibility = View.INVISIBLE
                btn_remark.visibility = View.VISIBLE
                btn_stop_tracking.visibility = View.INVISIBLE
            }
            "fnd" ->{
                btn_restart_app.visibility = View.VISIBLE
                btn_mark_cul.visibility = View.INVISIBLE
                //btn_quit_app.visibility = View.VISIBLE
                btn_report_found.visibility = View.INVISIBLE
                btn_remark.visibility = View.INVISIBLE
            }
        }
    }

    fun helpClicked (view: View) {
        println("helpClicked admin=$admin appState=$appState")
        if (admin) {
            if (channelSelected) {
                sessions_list.visibility = View.VISIBLE
                println("sessions_list.count=${sessions_list.count}")
                if (sessions_list.count > 0) {
                    //sessions_list.smoothScrollToPosition(sessions_list.count - 1)
                    sessions_list.setSelection(sessions_list.count - 1)
                }
            }
            else channel_list.visibility = View.VISIBLE
        } else {
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

    fun loginClicked(view: View) {
        println("Login handshake loginClicked App.prefs.isLoggedIn=${App.prefs.isLoggedIn}")
        if (App.prefs.isLoggedIn) {
            // Logout
            UserDataService.logout()
            btn_login.text = "Login"
            appState = "lgn"
            reset()
            LocalBroadcastManager.getInstance(this).unregisterReceiver(userDataChangeReceiver)
            socket.disconnect()
            updateButtons()
            admin = false
            stopIndex = 0
        } else {
            val loginIntent = Intent(this, LoginActivity::class.java)
            startActivity(loginIntent)
        }
    }

    private val userDataChangeReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            // We reach here when user data has been changed, e.g. user logged in
            println("Login handshake: The broadcast has been received in MainActivity App.prefs.isLoggedIn=${App.prefs.isLoggedIn}")
            if (App.prefs.isLoggedIn) {
                btn_login.text = "Logout"
                if (appState == "lgn") appState = "bst"
                println("appState change coming from here 1")
                updateButtons()

                MessageService.getChannels { getChannelsSuccess ->
                    if (getChannelsSuccess) {
                        if (MessageService.channels.count() > 0) {
                            //selectedChannel = MessageService.channels[0]
                            channelAdapter.notifyDataSetChanged()
                        }
                        val channelsNames = ArrayList<String>()
                        for (channel in MessageService.channels) {
                            channelsNames.add(channel.name)
                        }
                        println("Creating channel channelsNames.count=${channelsNames.count()}")
                        if (channelsNames.contains(UserDataService.name)) {
                            selectedChannel = MessageService.getChannelByName(UserDataService.name)
                            println("Creating channel contains ${UserDataService.name}")
                        } else {
                            val channelName = UserDataService.name
                            val channelDesc = UserDataService.name
                            socket.emit("newChannel", channelName, channelDesc)
                        }
                    } else {

                    }
                }
                admin = UserDataService.name == "admin^0545921611"
            }
        }
    }

    private val onNewChannel = Emitter.Listener { args ->
        runOnUiThread {
            if (App.prefs.isLoggedIn) {
                val channelName = args[0] as String
                val channelDescription = args[1] as String
                val channelId = args[2] as String

                val newChannel = Channel(channelName, channelDescription, channelId)
                MessageService.channels.add(newChannel)
                selectedChannel = newChannel
                println("onNewChannel selectedChannel=$selectedChannel")
//            println(newChannel.name)
//            println(newChannel.description)
//            println(newChannel.id)
            }
        }
    }

    private val onNewMessage = Emitter.Listener { args ->
        if (App.prefs.isLoggedIn) {
            runOnUiThread {
                println("onNewMessage")
                val channelId = args[2] as String
                if (channelId == selectedChannel?.id) {
                    val msgBody = args[0] as String
                    val userName = args[3] as String
                    val userAvatar = args[4] as String
                    val userAvatarColor = args[5] as String
                    val id = args[6] as String
                    val timeStamp = args[7] as String

                    val newMessage = Message(
                        msgBody,
                        userName,
                        channelId,
                        userAvatar,
                        userAvatarColor,
                        id,
                        timeStamp
                    )
                    MessageService.messages.add(newMessage)
                    println(newMessage.message)
                    handleMessage(newMessage.message)
                }
            }
        }
    }

    fun handleMessage(message: String) {
        if (admin) {
            println("Receiver About to decode $message")
            val locationsListOut = mutableListOf<Location>()
            val messageType =
                CommunicationService.decodeMessage(message, locationsListOut)
            println("Receiver isLocationsListMessage=$messageType locationsListOut.count()=${locationsListOut.count()}")
            if (messageType == "Locations") {
                for (location in locationsListOut) {
                    locations.add(location)
                }
                // Check if Land message has been received before and only now its location arrived
                if (landTimeStamp > 0 && locations[locations.count() - 1].time > landTimeStamp) {
                    stopIndex = findStopIndex()
                    stopIndexFound = true
                }
            } else if (messageType == "Land") {
                val lastTrackedLocation = locationsListOut[0]
                landLatLng = LatLng(lastTrackedLocation.latitude, lastTrackedLocation.longitude)
                landTimeStamp = lastTrackedLocation.time
                if (locations.isNotEmpty() && locations[locations.count() - 1].time > landTimeStamp) {
                    stopIndex = findStopIndex()
                    stopIndexFound = true
                }
            } else if (messageType == "Lost") {
                val lostLocation = locationsListOut[0]
                lostLatLng = LatLng(lostLocation.latitude, lostLocation.longitude)
                lostTimeStamp = lostLocation.time
            } else if (messageType == "Found") {
                val foundLocation = locationsListOut[0]
                foundLatLng = LatLng(foundLocation.latitude, foundLocation.longitude)
                foundTimeStamp = foundLocation.time
                // Calculate the estimated position where the board should have been found
                lastCurrentBoardLatLng = FindBoardService.getCurrentBoardPosition(foundTimeStamp)
            }
            showAll()
        }
    }

    fun findStopIndex(): Int {
        val timeStamps = ArrayList<Long>()
        for (location in locations) {
            timeStamps.add(location.time)
        }
        var timeIndex = timeStamps.binarySearch(landTimeStamp)
        if (timeIndex < 0) timeIndex = -timeIndex - 1
        return timeIndex
    }

    fun createSessionsList() {
        sessionsTimestamps.clear()
        val sessionsMessages = ArrayList<String>()
        for (message in MessageService.messages) {
            sessionsMessages.add(message.message)
        }
        for (message in MessageService.messages) {
            var sessionTimestamp = ""
            val scanner = Scanner(message.message)
            if (scanner.hasNext()) {
                sessionTimestamp = scanner.next() + " " + scanner.next()
                if (!sessionsTimestamps.contains(sessionTimestamp)) {
                    sessionsTimestamps.add(sessionTimestamp)
                }
            }
        }
        sessionsTimestamps.add("Back to users list")
        sessionsAdapter = ArrayAdapter(applicationContext, android.R.layout.simple_list_item_1, sessionsTimestamps)
        sessions_list.adapter = sessionsAdapter
    }

    fun reset() {
        locations.clear()
        clearAllMarkers()
        clearAllPolylines()
        /** Reset variables to their initial value
         */
        stopIndexFound = false
        landTimeStamp = 0L
        distance = 0.0
        averageSpeed = "0.0"
        stopIndex = 0
        showTextResults(0.0)
    }

    private fun clearAllPolylines() {
        if (polylines.isNotEmpty()) {
            for (polyline in polylines) {
                polyline.remove()
            }
        }
        polylines.clear()
    }

    private fun clearAllMarkers() {
        if (::lostPositionMarker.isInitialized) lostPositionMarker.remove()
        if (::landLocationMarker.isInitialized) landLocationMarker.remove()
        if (::foundLocationMarker.isInitialized) foundLocationMarker.remove()
        if (::currentBoardLocationMarker.isInitialized) currentBoardLocationMarker.remove()
    }
}
