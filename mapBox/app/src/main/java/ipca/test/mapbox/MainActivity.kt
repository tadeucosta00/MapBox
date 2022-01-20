package ipca.test.mapbox

import android.Manifest
import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.maps.MapView
import android.widget.Toast
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.PersistableBundle
import android.util.Log
import android.view.View
import android.widget.Button
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineListener
import com.mapbox.android.core.location.LocationEnginePriority
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.constants.Style.*
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.CameraMode
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.RenderMode
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute



class MainActivity : AppCompatActivity(), PermissionsListener, LocationEngineListener, MapboxMap.OnMapClickListener {

    private lateinit var mapView : MapView
    private lateinit var startButton: Button
    private lateinit var map: MapboxMap
    private lateinit var permissionManager: PermissionsManager
    private lateinit var originLocation: Location
    private lateinit var originPosition: Point
    private lateinit var destinationPosition: Point
    private var destinationMarker: Marker? = null
    private var navigationMapRoute: NavigationMapRoute? = null

    class String{
        companion object{
            const val TAG = "MainActivity"
        }
    }


    private var locationEngine : LocationEngine? = null
    private var locationLayerPlugin: LocationLayerPlugin? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        var mapa = LIGHT


        var mudarmapa = findViewById<Button>(R.id.mudarMapa)

        mudarmapa.setOnClickListener(){
            when(mapa){

                LIGHT -> {
                    mapa = OUTDOORS
                }
                OUTDOORS -> {
                    mapa = DARK
                }
                DARK -> {
                    mapa = SATELLITE
                }
                SATELLITE -> {
                    mapa = SATELLITE_STREETS
                }
                SATELLITE_STREETS -> {
                    mapa = TRAFFIC_DAY
                }
                TRAFFIC_DAY -> {
                    mapa = TRAFFIC_NIGHT
                }
                TRAFFIC_NIGHT -> {
                    mapa = LIGHT
                }

            }
            mapView.setStyleUrl(mapa)
        }

        supportActionBar?.hide()


        Mapbox.getInstance(applicationContext, getString(R.string.access_token))
        mapView = findViewById(R.id.mapView)
        startButton = findViewById(R.id.startButton)
        mapView.onCreate(savedInstanceState)

        mapView.getMapAsync { mapboxMap ->
            map = mapboxMap
            map.addOnMapClickListener(this)
            enableLocation()
        }

        startButton.setOnClickListener(object: View.OnClickListener{

            override fun onClick(v : View){
                 var options: NavigationLauncherOptions? = NavigationLauncherOptions.builder()
                     .origin(originPosition)
                     .destination(destinationPosition)
                     .build()
                    NavigationLauncher.startNavigation(this@MainActivity, options)
            }
        })
    }

    fun enableLocation(){
        if(PermissionsManager.areLocationPermissionsGranted(this)){
            initializeLocationEngine()
            initializeLocationLayer()
        } else {
            permissionManager = PermissionsManager(this)
            permissionManager.requestLocationPermissions(this)
        }
    }


    @SuppressWarnings("MissingPermission")
    private fun initializeLocationEngine(){
        locationEngine = LocationEngineProvider(this).obtainBestLocationEngineAvailable()
        locationEngine?.priority = LocationEnginePriority.HIGH_ACCURACY
        locationEngine?.activate()

        val lastLocation = locationEngine?.lastLocation
        if(lastLocation != null){
            originLocation = lastLocation
            setCameraPosition(lastLocation)

        }else{
            locationEngine?.addLocationEngineListener(this)
        }
    }

    @SuppressWarnings("MissingPermission")
    private fun initializeLocationLayer(){
        locationLayerPlugin = LocationLayerPlugin(mapView, map, locationEngine)
        locationLayerPlugin?.setLocationLayerEnabled(true)
        locationLayerPlugin?.cameraMode = CameraMode.TRACKING
        locationLayerPlugin?.renderMode = RenderMode.NORMAL
    }


    private fun setCameraPosition(location: Location){
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(
            LatLng(location.latitude, location.longitude), 13.0))
    }

    @SuppressLint("ResourceAsColor")
    override fun onMapClick(point: LatLng) {

        if(destinationMarker != null){
            map.removeMarker(destinationMarker!!)
        }else{
            println("miau")
        }

        destinationMarker = map.addMarker(MarkerOptions().position(point))

        destinationPosition = Point.fromLngLat(point.longitude, point.latitude)
        originPosition = Point.fromLngLat(originLocation.longitude, originLocation.latitude)
        getRoute(originPosition, destinationPosition)

        startButton.setEnabled(true)
        startButton.setTextColor(R.color.black)


    }

    private fun getRoute(origin:Point,destination:Point) {
        NavigationRoute.builder()
            .accessToken(getString(R.string.access_token))
            .origin(origin)
            .destination(destination)
            .build()
            .getRoute(object : retrofit2.Callback<DirectionsResponse> {
                override fun onResponse(call: retrofit2.Call<DirectionsResponse>, response: retrofit2.Response<DirectionsResponse>) {

                    val routeResponse = response ?: return
                    val body = routeResponse.body() ?: return
                    if (body.routes().count() == 0){
                        Log.i("LOGLOG","Sem rotas encontradas ___onResponse")
                        return
                    }

                    if (navigationMapRoute != null){
                        navigationMapRoute?.removeRoute()
                    }else{
                        navigationMapRoute = NavigationMapRoute(null,mapView,map)
                        navigationMapRoute?.addRoute(body.routes().first())
                    }
                }

                override fun onFailure(call: retrofit2.Call<DirectionsResponse>, t: Throwable) {
                    Log.i("LOGLOG","${t.message} ___onFailure")
                }
            })
    }

    override fun onExplanationNeeded(permissionsToExplain: MutableList<kotlin.String>?) {
        Toast.makeText(applicationContext, "A permissão deve ser Aceita", Toast.LENGTH_SHORT).show()
    }



    override fun onPermissionResult(granted: Boolean) {
        if(granted){
          enableLocation()
        }
    }




    override fun onLocationChanged(location: Location?) {
        location?.let{
            originLocation = location
            setCameraPosition(location)
        }
    }

    @SuppressWarnings("MissingPermission")
    override fun onConnected() {
        locationEngine?.requestLocationUpdates()
    }



    override fun onResume(){
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        locationEngine?.removeLocationUpdates()
        locationLayerPlugin?.onStop()
        mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        locationEngine?.deactivate()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        if(outState != null){
            mapView.onSaveInstanceState(outState)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    @SuppressWarnings("MissingPermission")
    override fun onStart() {
        super.onStart()
        if(PermissionsManager.areLocationPermissionsGranted(this)){
            locationEngine?.requestLocationUpdates()
            locationLayerPlugin?.onStart()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val res = checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
            if (res != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), 123)
            }
        }
        mapView.onStart()
    }

    private val REQUEST_CODE_ASK_PERMISSIONS = 1002

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out kotlin.String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_ASK_PERMISSIONS -> {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(
                        applicationContext,
                        "Acesso Negado!",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                } else {
                }
            }
            else -> permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}
