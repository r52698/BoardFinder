package com.example.org.boardfinder

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.*

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import kotlinx.android.synthetic.main.activity_maps.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var lastLocation: Location
    private var requestingLocationUpdates: Boolean = false

    private lateinit var locationCallback: LocationCallback

    private lateinit var locationRequest: LocationRequest
    private var locationUpdateState = false

    var gpsService: BackgroundService? = null
    var mTracking = false

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val REQUEST_CHECK_SETTINGS = 2
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val name = className.className
            println("val name = '$name'")
            if (name.subSequence(name.length - 17, name.length - 1) == "BackgroundService") {
                //if (name.endsWith("BackgroundService")) {
                gpsService = (service as BackgroundService.LocationServiceBinder).service
                btn_start_tracking!!.isEnabled = true
                txt_status!!.text = "GPS Ready"
            }
        }

        override fun onServiceDisconnected(className: ComponentName) {
            if (className.className == "BackgroundService") {
                gpsService = null
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
        fusedLocationClient.removeLocationUpdates(locationCallback)
        //startLocationButtonClick()
        gpsService!!.startTracking()
        mTracking = true
    }

    // 3
    public override fun onResume() {
        super.onResume()
        println("onResume")
        //if (!locationUpdateState) {
            println("strtLocationUpdates")
            startLocationUpdates()
        //}
        //stopLocationButtonClick()
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

                var prevLocation = p0.lastLocation
                if (!firstTime) prevLocation = lastLocation
                firstTime = false
                lastLocation = p0.lastLocation
                placeMarkerOnMap(LatLng(prevLocation.latitude, prevLocation.longitude),
                    LatLng(lastLocation.latitude, lastLocation.longitude),
                    lastLocation.speed)
                map.animateCamera(CameraUpdateFactory.newLatLng(LatLng(lastLocation.latitude, lastLocation.longitude)))
            }
        }
        createLocationRequest()

        //ButterKnife.bind(this)

        val intent = Intent(this.application, BackgroundService::class.java)
        this.application.startService(intent)
        //        this.getApplication().startForegroundService(intent);
        this.application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        println("service connected")
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
        Toast.makeText(this, "$speedToDisplay km/h", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(this, "lat: $lat, lon: $lon", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "location is null", Toast.LENGTH_LONG).show()
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
        // Add polylines to the map.
        // Polylines are useful to show a route or some other connection between points.
        val polyline1 = googleMap.addPolyline(
            PolylineOptions()
                .clickable(true)
                .add(
                    LatLng(-35.016, 143.321),
                    LatLng(-34.747, 145.592),
                    LatLng(-34.364, 147.891),
                    LatLng(-33.501, 150.217),
                    LatLng(-32.306, 149.248),
                    LatLng(-32.491, 147.309)
                )
        )
    }

    fun startLocationButtonClick() {
        Toast.makeText(this, "start clicked", Toast.LENGTH_SHORT).show()
        Dexter.withActivity(this)
            .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object : PermissionListener {
                override fun onPermissionGranted(response: PermissionGrantedResponse) {
                    gpsService!!.startTracking()
                    mTracking = true
                    toggleButtons()
                }

                override fun onPermissionDenied(response: PermissionDeniedResponse) {
                    if (response.isPermanentlyDenied) {
                        openSettings()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(permission: PermissionRequest, token: PermissionToken) {
                    token.continuePermissionRequest()
                }
            }).check()
    }

    fun stopLocationButtonClick() {
        mTracking = false
        gpsService!!.stopTracking()
        toggleButtons()
    }

    private fun toggleButtons() {
        btn_start_tracking!!.isEnabled = !mTracking
        btn_stop_tracking!!.isEnabled = mTracking
        txt_status!!.text = if (mTracking) "TRACKING" else "GPS Ready"
    }

    private fun openSettings() {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        val uri = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
        intent.data = uri
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

//    private fun startLocationUpdates() {
//        var locationRequest : LocationRequest
//        fusedLocationClient.requestLocationUpdates(locationRequest,
//            locationCallback,
//            null /* Looper */)
//    }
}
