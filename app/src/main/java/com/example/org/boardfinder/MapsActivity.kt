package com.example.org.boardfinder

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.transition.Visibility
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.org.boardfinder.LocationMonitoringService.Companion.serviceStartTime
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.*

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_maps.*
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var lastLocation: Location
    private var requestingLocationUpdates: Boolean = false

    private lateinit var locationCallback: LocationCallback

    private lateinit var locationRequest: LocationRequest
    private var locationUpdateState = false

    private var mAlreadyStartedService = false
    private var mMsgView: TextView? = null

    private var distance = 0.0
    private var averageSpeed = "0.0"

    private var mapReady = false

    /**
     * Return the availability of GooglePlayServices
     */
    val isGooglePlayServicesAvailable:Boolean
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

        private val TAG = MapsActivity::class.java!!.simpleName

        /**
         * Code used in requesting runtime permissions.
         */
        private val REQUEST_PERMISSIONS_REQUEST_CODE = 34

        var appState = "bst"
        // bst, run, pas, stp, mrk
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.putString(EXTRA_TEXT, msgView.text.toString())
        outState?.putString(EXTRA_STATE, appState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        if (savedInstanceState != null) {
            msgView.text = savedInstanceState.getString(EXTRA_TEXT)
            appState = savedInstanceState.getString(EXTRA_STATE)
            updateButtons()
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
            }
            "mrk" ->{
                btn_start_tracking.isEnabled = false
                btn_stop_tracking.isEnabled = false
                btn_mark_cul.isEnabled = false
                btn_report_found.isEnabled = true
                btn_report_found.visibility = View.VISIBLE
                btn_start_tracking.visibility = View.INVISIBLE
                targetImageView.visibility = View.INVISIBLE
            }
            "fnd" ->{
                btn_start_tracking.isEnabled = false
                btn_stop_tracking.isEnabled = false
                btn_mark_cul.isEnabled = false
            }
        }
    }


    private fun startLocationUpdates() {
        //1
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE)
            return
        }
        //2
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null /* Looper */)
    }

    private fun createLocationRequest() {
        // 1
        locationRequest = LocationRequest()
        // 2
        locationRequest.interval = 10000
        // 3
        locationRequest.fastestInterval = 5000
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        // 4
        val client = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())

        // 5
        task.addOnSuccessListener {
            locationUpdateState = true
            startLocationUpdates()
        }
        task.addOnFailureListener { e ->
            // 6
            if (e is ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    e.startResolutionForResult(this@MapsActivity,
                        REQUEST_CHECK_SETTINGS)
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            if (resultCode == Activity.RESULT_OK) {
                locationUpdateState = true
                startLocationUpdates()
            }
        }
    }

    // 2
    override fun onPause() {
        super.onPause()
        if (appState == "run") fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    // 3
    public override fun onResume() {
        super.onResume()

        if (appState == "run") {
            startLocationUpdates()
            startStep1()
            distance = 0.0
            averageSpeed = "0.0"
            lateinit var previousLocation: Location
            var firstLocation = true
            Toast.makeText(
                this,
                "${LocationMonitoringService.locations.count()} locations",
                Toast.LENGTH_LONG
            ).show()
            showTrack(true)
            val elapsedTimeSeconds = (System.currentTimeMillis() - serviceStartTime) / 1000.0
            showResults(distance, elapsedTimeSeconds)
            //Toast.makeText(this, "Distance = $distance", Toast.LENGTH_LONG).show()

            for (timeStamp in LocationMonitoringService.timeStamps) {

                val time = returnDateString(timeStamp)
                println("Time=$time")
            }
        }
    }

    fun showResults(distance: Double, time: Double) {
        val timeHours = time / 3600.0
        var timeString = ""
        if (timeHours >= 1) timeString += timeHours.toInt().toString() + ":"
        val timeMinutesString = ((time.toInt() % 3600) / 60).toString()
        if (timeMinutesString.length == 1 && timeString.isEmpty()) timeString += "0"
        timeString += "$timeMinutesString:"
        val secondsString = (time.toInt() % 60).toString()
        if (secondsString.length == 1) timeString += "0"
        timeString += secondsString
        averageSpeed = (distance / time * 3.6).toString()
        if (averageSpeed.length > 4) averageSpeed = averageSpeed.substring(0, 4)
        mMsgView!!.text = "  ${distance.toInt()} m   $averageSpeed km/h  $timeString"
    }

    fun returnDateString(isoString: String) : String {
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

        var firstTime = true
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                super.onLocationResult(p0)

                if (appState == "run") {
                    var prevLocation = p0.lastLocation
                    if (!firstTime) prevLocation = lastLocation
                    firstTime = false
                    lastLocation = p0.lastLocation
                    distance += prevLocation.distanceTo(lastLocation)

                    val elapsedTimeSeconds = (System.currentTimeMillis() - serviceStartTime) / 1000.0
                    showResults(distance, elapsedTimeSeconds)

                    //if (elapsedTimeSeconds > 20 && locationRequest.fastestInterval != 5000L) locationRequest.fastestInterval = 5000L

                    placeMarkerOnMap(
                        LatLng(prevLocation.latitude, prevLocation.longitude),
                        LatLng(lastLocation.latitude, lastLocation.longitude),
                        lastLocation.speed
                    )
                    map.animateCamera(
                        CameraUpdateFactory.newLatLng(
                            LatLng(
                                lastLocation.latitude,
                                lastLocation.longitude
                            )
                        )
                    )
                }
            }
        }
        mMsgView = findViewById(R.id.msgView) as TextView
        if (appState != "bst") startClicked(btn_start_tracking)
        else alertDialog("", "Click START when starting your activity. The app will keep track of your movement.")
    }

    private fun displayMarker(latLng: LatLng) {
        val markerOptions = MarkerOptions().position(latLng)
        map.addMarker(markerOptions)
    }

    private fun placeMarkerOnMap(fromLocation: LatLng, toLocation: LatLng, speed: Float) {
//        // 1
//        val markerOptions = MarkerOptions().position(location)
//        // 2
//        map.addMarker(markerOptions)
        val speedKmh = speed * 3.6
        val speedToDisplay = (speedKmh * 100).toInt() / 100.0
        val polyline1 = map.addPolyline(
            PolylineOptions().color(getColor(speedKmh).toInt())
                .clickable(true)
                .add(LatLng(fromLocation.latitude, fromLocation.longitude),
                    LatLng(toLocation.latitude, toLocation.longitude))
        )
        println("Placing a marker at lat ${toLocation.latitude} lon ${toLocation.longitude}")
        //Toast.makeText(this, "$speedToDisplay km/h", Toast.LENGTH_LONG).show()
    }

    fun getColor(speed: Double) : Long {
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

    fun work() {
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // 1
            map.isMyLocationEnabled = true

            // 2
            fusedLocationClient.lastLocation.addOnSuccessListener(this) { location ->
                // Got last known location. In some rare situations this can be null.
                // 3
                if (location != null) {
                    lastLocation = location
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    val lat = currentLatLng.latitude
                    val lon = currentLatLng.longitude
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 12f))
                    //Toast.makeText(this, "lat: $lat, lon: $lon", Toast.LENGTH_LONG).show()
                } else {
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
                } else {
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
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE
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
        Toast.makeText(this, "Map ready", Toast.LENGTH_LONG).show()

        // Do this in case it was not done in the onResume because the map was not ready yet
        if (appState != "bst") showTrack(false)

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

    fun showTrack(accumulateDistance: Boolean) {
        var firstLocation = true
        lateinit var previousLocation: Location
        for (location in LocationMonitoringService.locations) {
            if (firstLocation) previousLocation = location
            firstLocation = false
            val lat = location.latitude
            val lon = location.longitude
            val time = returnDateString(location.time.toString())
            println("Lat=$lat Lon=$lon time=$time")

            if (mapReady) placeMarkerOnMap(LatLng(previousLocation.latitude, previousLocation.longitude), LatLng(lat, lon), location.speed)
            if (accumulateDistance)distance += previousLocation.distanceTo(location)
            previousLocation = location
        }
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
            Toast.makeText(applicationContext, R.string.no_google_playservice_available, Toast.LENGTH_LONG).show()
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


        if (dialog != null)
        {
            dialog!!.dismiss()
        }

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
            android.Manifest.permission.ACCESS_FINE_LOCATION)

        val permissionState2 = ActivityCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_COARSE_LOCATION)

        return permissionState1 == PackageManager.PERMISSION_GRANTED && permissionState2 == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Start permissions requests.
     */
    private fun requestPermissions() {
        val shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(this,
            android.Manifest.permission.ACCESS_FINE_LOCATION)

        val shouldProvideRationale2 = ActivityCompat.shouldShowRequestPermissionRationale(this,
            Manifest.permission.ACCESS_COARSE_LOCATION)

        // Provide an additional rationale to the img_user. This would happen if the img_user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldProvideRationale || shouldProvideRationale2)
        {
            Log.i(TAG, "Displaying permission rationale to provide additional context.")
            showSnackbar(R.string.permission_rationale,
                android.R.string.ok, View.OnClickListener {
                    // Request permission
                    ActivityCompat.requestPermissions(this,
                        arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        REQUEST_PERMISSIONS_REQUEST_CODE)
                })
        }
        else
        {
            Log.i(TAG, "Requesting permission")
            // Request permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the img_user denied the permission
            // previously and checked "Never ask again".
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_PERMISSIONS_REQUEST_CODE)
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
        super.onDestroy()
    }

    fun stopClicked(view: View) {
        //Toast.makeText(this, "Finish clicked", Toast.LENGTH_LONG).show()
        //Stop location sharing service to app server.........
        stopService(Intent(this, LocationMonitoringService::class.java))
        mAlreadyStartedService = false
        appState = "stp"
        updateButtons()
        alertDialog("", "Use the colored track to speculate where you lost your board." +
                " Pan the map such that this point will be in the center of the target, then click MARK." +
                " Make sure to do it while still in the water with your kite!")
    }

    fun startClicked(view: View) {

        createLocationRequest()

        LocalBroadcastManager.getInstance(this).registerReceiver(
            object: BroadcastReceiver() {
                override fun onReceive(context: Context, intent:Intent) {
                    //Toast.makeText(applicationContext, "Broadcast receiver works.", Toast.LENGTH_LONG).show()
                    val latitude = intent.getStringExtra(LocationMonitoringService.EXTRA_LATITUDE)
                    val longitude = intent.getStringExtra(LocationMonitoringService.EXTRA_LONGITUDE)

                    if (latitude != null && longitude != null)
                    {
                        //mMsgView!!.text = getString(R.string.msg_location_service_started) + "\n Latitude : " + latitude + "\n Longitude: " + longitude
                    }
                }
            }, IntentFilter(LocationMonitoringService.ACTION_LOCATION_BROADCAST)
        )
        startStep1()

        appState = "run"
        updateButtons()
        alertDialog("", "Click FINISH when you are done with the activity. The app will stop keeping track of your movement.")
    }

    fun markClicked (view: View) {
        val centerLatLang = map.getProjection().getVisibleRegion().latLngBounds.getCenter();
        displayMarker(centerLatLang)
        appState = "mrk"
        updateButtons()
        alertDialog("", "The estimated location of where you lost your board is marked." +
        " You will soon see the estimated current location of your board. When you find it, please" +
        " click FOUND HERE such that the app can improve its locating algorithm. Thank you!")
    }

    fun foundClicked (view: View) {

    }

    fun alertDialog(title: String, message: String) {
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
}
