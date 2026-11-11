
package com.example.roadsync

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import android.location.Location

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

import androidx.core.content.ContextCompat

import com.google.android.gms.location.*

import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap

import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

import org.json.JSONArray
import org.json.JSONObject

data class SavedPoint(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

data class IncidentReport(
    val id: Long,
    val type: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)

class MainActivity : ComponentActivity() {

    private lateinit var mapView: MapView

    private lateinit var fusedLocationClient:
            FusedLocationProviderClient

    private var mapLibreMap: MapLibreMap? = null

    private var locationSource: GeoJsonSource? = null
    private var selectedSource: GeoJsonSource? = null
    private var savedSource: GeoJsonSource? = null
    private var incidentSource: GeoJsonSource? = null

    private var latestLocation: Location? = null
    private var selectedPoint: LatLng? = null

    private var isTracking = false

    companion object {
        private const val MAP_STYLE =
            "https://tiles.openfreemap.org/styles/liberty"

        private const val NASUGBU_LAT = 14.0667
        private const val NASUGBU_LNG = 120.6333
    }

    private val locationCallback =
        object : LocationCallback() {

            override fun onLocationResult(
                result: LocationResult
            ) {
                val location = result.lastLocation ?: return

                latestLocation = location
                updateLocationMarker(location)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        MapLibre.getInstance(this)

        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(this)

        setContent {

            val savedPoints = remember {
                mutableStateListOf<SavedPoint>().apply {
                    addAll(loadSavedPoints())
                }
            }

            val incidentReports = remember {
                mutableStateListOf<IncidentReport>().apply {
                    addAll(loadIncidentReports())
                }
            }

            var status by remember {
                mutableStateOf("Map ready - Nasugbu, Batangas")
            }

            LaunchedEffect(Unit) {
                FirebaseManager().signInAnonymously(
                    onSuccess = { uid ->
                        status = "Firebase connected! User: ${uid.take(8)}"
                    },
                    onError = { error ->
                        status = "Firebase sign-in failed: $error"

                        Toast.makeText(
                            this@MainActivity,
                            error,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }

// FIRESTORE LISTENER REGISTRATION
            val incidentListener = remember {
                mutableStateOf<com.google.firebase.firestore.ListenerRegistration?>(null)
            }

// REMOVE LISTENER WHEN SCREEN IS DISPOSED
            DisposableEffect(incidentListener.value) {
                val registration = incidentListener.value

                onDispose {
                    registration?.remove()
                }
            }

// FIREBASE AUTHENTICATION AND REAL-TIME LISTENER
            LaunchedEffect(Unit) {
                FirebaseManager().signInAnonymously(
                    onSuccess = { uid ->

                        status = "Firebase connected! User: ${uid.take(8)}"

                        // Start listening for online incident reports
                        incidentListener.value =
                            FirestoreManager().listenToIncidentReports(

                                onUpdate = { cloudReports ->

                                    // Combine local and online reports
                                    val combinedReports =
                                        (incidentReports.toList() + cloudReports)
                                            .distinctBy { it.id }

                                    incidentReports.clear()
                                    incidentReports.addAll(combinedReports)

                                    // Save and refresh markers
                                    saveIncidentReports(incidentReports)
                                    updateIncidentMarkers(incidentReports)

                                    status = "Firebase connected! Reports: ${incidentReports.size}"
                                },

                                onError = { error ->
                                    status = "Firestore listener failed: $error"
                                }
                            )
                    },

                    onError = { error ->

                        status = "Firebase sign-in failed: $error"

                        Toast.makeText(
                            this@MainActivity,
                            error,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }


            // INCIDENT FORM STATE

            var showReportDialog by remember {
                mutableStateOf(false)
            }

            var incidentType by remember {
                mutableStateOf("Accident")
            }

            var incidentDescription by remember {
                mutableStateOf("")
            }

            var categoryMenuExpanded by remember {
                mutableStateOf(false)
            }

            val incidentCategories = listOf(
                "Accident",
                "Pothole",
                "Road Damage",
                "Road Blockage",
                "Other"
            )

            // LOCATION PERMISSION

            val permissionLauncher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->

                    val granted =
                        permissions[
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ] == true ||
                                permissions[
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ] == true

                    if (granted) {
                        startLocationTracking()
                    } else {
                        status = "Location permission denied"

                        Toast.makeText(
                            this,
                            status,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            Box(
                modifier = Modifier.fillMaxSize()
            ) {

                // MAP VIEW

                AndroidView(
                    modifier = Modifier.fillMaxSize(),

                    factory = { context ->

                        MapView(context).also { view ->

                            mapView = view
                            view.onCreate(savedInstanceState)

                            view.getMapAsync { map ->

                                mapLibreMap = map

                                map.setStyle(MAP_STYLE) { style ->

                                    // BLUE GPS MARKER

                                    val gpsSource =
                                        GeoJsonSource("gps-source")

                                    style.addSource(gpsSource)
                                    locationSource = gpsSource

                                    val gpsLayer =
                                        CircleLayer(
                                            "gps-layer",
                                            "gps-source"
                                        ).withProperties(
                                            PropertyFactory.circleRadius(9f),
                                            PropertyFactory.circleColor("#4285F4"),
                                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                                            PropertyFactory.circleStrokeWidth(3f)
                                        )

                                    style.addLayer(gpsLayer)

                                    // ORANGE SELECTED LOCATION

                                    val selectSource =
                                        GeoJsonSource("selected-source")

                                    style.addSource(selectSource)
                                    selectedSource = selectSource

                                    val selectLayer =
                                        CircleLayer(
                                            "selected-layer",
                                            "selected-source"
                                        ).withProperties(
                                            PropertyFactory.circleRadius(10f),
                                            PropertyFactory.circleColor("#FF9800"),
                                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                                            PropertyFactory.circleStrokeWidth(3f)
                                        )

                                    style.addLayer(selectLayer)

                                    // RED SAVED POINTS

                                    val pointsSource =
                                        GeoJsonSource("saved-source")

                                    style.addSource(pointsSource)
                                    savedSource = pointsSource

                                    val pointsLayer =
                                        CircleLayer(
                                            "saved-layer",
                                            "saved-source"
                                        ).withProperties(
                                            PropertyFactory.circleRadius(7f),
                                            PropertyFactory.circleColor("#E53935"),
                                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                                            PropertyFactory.circleStrokeWidth(2f)
                                        )

                                    style.addLayer(pointsLayer)

                                    // PURPLE INCIDENT MARKERS

                                    val incidentGeoJsonSource =
                                        GeoJsonSource("incident-source")

                                    style.addSource(incidentGeoJsonSource)
                                    incidentSource = incidentGeoJsonSource

                                    val incidentLayer =
                                        CircleLayer(
                                            "incident-layer",
                                            "incident-source"
                                        ).withProperties(
                                            PropertyFactory.circleRadius(11f),
                                            PropertyFactory.circleColor("#9C27B0"),
                                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                                            PropertyFactory.circleStrokeWidth(3f)
                                        )

                                    style.addLayer(incidentLayer)

                                    // INITIAL NASUGBU POSITION

                                    map.cameraPosition =
                                        CameraPosition.Builder()
                                            .target(
                                                LatLng(
                                                    NASUGBU_LAT,
                                                    NASUGBU_LNG
                                                )
                                            )
                                            .zoom(13.5)
                                            .build()

                                    // RESTORE SAVED MARKERS

                                    updateSavedPoints(savedPoints)

                                    // RESTORE INCIDENT MARKERS

                                    updateIncidentMarkers(incidentReports)

                                    // TAP MAP TO SELECT LOCATION

                                    map.addOnMapClickListener { point ->

                                        selectedPoint = point

                                        selectedSource?.setGeoJson(
                                            Feature.fromGeometry(
                                                Point.fromLngLat(
                                                    point.longitude,
                                                    point.latitude
                                                )
                                            )
                                        )

                                        status =
                                            "Location selected: " +
                                                    "%.5f".format(point.latitude) +
                                                    ", " +
                                                    "%.5f".format(point.longitude)

                                        true
                                    }

                                    latestLocation?.let {
                                        updateLocationMarker(it)
                                    }
                                }
                            }
                        }
                    }
                )

                // MAP BUTTONS

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),

                    verticalArrangement =
                        Arrangement.spacedBy(8.dp),

                    horizontalAlignment = Alignment.End
                ) {

                    // GPS BUTTON

                    FloatingActionButton(
                        onClick = {

                            val fine =
                                ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED

                            val coarse =
                                ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED

                            if (fine || coarse) {
                                startLocationTracking()
                            } else {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        }
                    ) {
                        Text("GPS")
                    }

                    // SET LOCATION BUTTON

                    Button(
                        onClick = {

                            val center =
                                mapLibreMap?.cameraPosition?.target

                            if (center != null) {

                                selectedPoint = center

                                selectedSource?.setGeoJson(
                                    Feature.fromGeometry(
                                        Point.fromLngLat(
                                            center.longitude,
                                            center.latitude
                                        )
                                    )
                                )

                                status = "Map center selected!"

                            } else {
                                status = "Map is not ready yet"
                            }
                        }
                    ) {
                        Text("Set Location")
                    }

                    // SAVE POINT BUTTON

                    Button(
                        onClick = {

                            val point =
                                selectedPoint
                                    ?: mapLibreMap?.cameraPosition?.target

                            if (point != null) {

                                val newPoint = SavedPoint(
                                    name = "Point ${savedPoints.size + 1}",
                                    latitude = point.latitude,
                                    longitude = point.longitude
                                )

                                savedPoints.add(newPoint)

                                saveSavedPoints(savedPoints)
                                updateSavedPoints(savedPoints)

                                status =
                                    "${newPoint.name} saved successfully!"

                                Toast.makeText(
                                    this@MainActivity,
                                    status,
                                    Toast.LENGTH_SHORT
                                ).show()

                            } else {
                                status = "Select a location first"
                            }
                        }
                    ) {
                        Text("Save Point")
                    }

                    // REPORT INCIDENT BUTTON

                    Button(
                        onClick = {
                            incidentDescription = ""
                            incidentType = "Accident"
                            showReportDialog = true
                        }
                    ) {
                        Text("Report Incident")
                    }
                }

                // SAVED POINTS AND INCIDENT HISTORY

                Card(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .widthIn(max = 230.dp)
                        .heightIn(max = 300.dp),

                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 5.dp
                    )
                ) {

                    LazyColumn(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {

                        item {
                            Text(
                                "Saved Points (${savedPoints.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                            HorizontalDivider()
                        }

                        if (savedPoints.isEmpty()) {
                            item {
                                Text(
                                    "No saved points yet.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            items(savedPoints) { point ->

                                Column {
                                    Text(
                                        point.name,
                                        style = MaterialTheme.typography.titleSmall
                                    )

                                    Text(
                                        "Lat: %.5f".format(point.latitude),
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    Text(
                                        "Lng: %.5f".format(point.longitude),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                HorizontalDivider()
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                "Incident Reports (${incidentReports.size})",
                                style = MaterialTheme.typography.titleMedium
                            )

                            HorizontalDivider()
                        }

                        if (incidentReports.isEmpty()) {
                            item {
                                Text(
                                    "No incident reports yet.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            items(incidentReports) { report ->

                                Column {
                                    Text(
                                        report.type,
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.titleSmall
                                    )

                                    Text(
                                        report.description,
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    Text(
                                        "Lat: %.5f | Lng: %.5f".format(
                                            report.latitude,
                                            report.longitude
                                        ),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                HorizontalDivider()
                            }
                        }
                    }
                }

                // STATUS DISPLAY

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(10.dp),

                    color = MaterialTheme.colorScheme.surface.copy(
                        alpha = 0.90f
                    ),

                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = status,
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // INCIDENT REPORTING DIALOG

            if (showReportDialog) {

                AlertDialog(

                    onDismissRequest = {
                        showReportDialog = false
                    },

                    title = {
                        Text("Report an Incident")
                    },

                    text = {

                        Column(
                            verticalArrangement =
                                Arrangement.spacedBy(12.dp)
                        ) {

                            Text("Select incident type:")

                            Box {

                                OutlinedButton(
                                    onClick = {
                                        categoryMenuExpanded = true
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(incidentType)
                                }

                                DropdownMenu(
                                    expanded = categoryMenuExpanded,
                                    onDismissRequest = {
                                        categoryMenuExpanded = false
                                    }
                                ) {
                                    incidentCategories.forEach { category ->

                                        DropdownMenuItem(
                                            text = {
                                                Text(category)
                                            },
                                            onClick = {
                                                incidentType = category
                                                categoryMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = incidentDescription,
                                onValueChange = {
                                    incidentDescription = it
                                },
                                label = {
                                    Text("Description")
                                },
                                placeholder = {
                                    Text("Describe what happened...")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                maxLines = 4
                            )

                            val reportLocation =
                                selectedPoint
                                    ?: mapLibreMap?.cameraPosition?.target

                            Text(
                                text = if (reportLocation != null) {
                                    "Location: %.5f, %.5f".format(
                                        reportLocation.latitude,
                                        reportLocation.longitude
                                    )
                                } else {
                                    "Location: Not available"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )

                            Text(
                                "Tap the map to choose an incident location.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },

                    confirmButton = {

                        Button(
                            onClick = {

                                val point =
                                    selectedPoint
                                        ?: mapLibreMap?.cameraPosition?.target

                                if (incidentDescription.isBlank()) {

                                    Toast.makeText(
                                        this@MainActivity,
                                        "Please enter a description",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                } else if (point == null) {

                                    Toast.makeText(
                                        this@MainActivity,
                                        "Please select a location",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                } else {

                                    val report = IncidentReport(
                                        id = System.currentTimeMillis(),
                                        type = incidentType,
                                        description =
                                            incidentDescription.trim(),
                                        latitude = point.latitude,
                                        longitude = point.longitude,
                                        timestamp = System.currentTimeMillis()
                                    )

                                    incidentReports.add(report)

                                    saveIncidentReports(incidentReports)

                                    updateIncidentMarkers(incidentReports)

                                    // Upload incident report to Firebase Firestore
                                    FirestoreManager().uploadIncidentReport(
                                        report = report,
                                        onSuccess = {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Incident uploaded successfully!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        },
                                        onError = { error ->
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Upload failed: $error",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    )

                                    status =
                                        "$incidentType report submitted!"

                                    showReportDialog = false

                                    Toast.makeText(
                                        this@MainActivity,
                                        "Incident report saved!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        ) {
                            Text("Submit Report")
                        }
                    },

                    dismissButton = {

                        TextButton(
                            onClick = {
                                showReportDialog = false
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }

    // UPDATE BLUE GPS MARKER

    private fun updateLocationMarker(location: Location) {

        val point = Point.fromLngLat(
            location.longitude,
            location.latitude
        )

        locationSource?.setGeoJson(
            Feature.fromGeometry(point)
        )
    }

    // UPDATE SAVED MARKERS

    private fun updateSavedPoints(points: List<SavedPoint>) {

        val features = points.map { point ->

            Feature.fromGeometry(
                Point.fromLngLat(
                    point.longitude,
                    point.latitude
                )
            )
        }

        savedSource?.setGeoJson(
            FeatureCollection.fromFeatures(
                features.toTypedArray()
            )
        )
    }

    // UPDATE INCIDENT MARKERS

    private fun updateIncidentMarkers(
        reports: List<IncidentReport>
    ) {

        val features = reports.map { report ->

            Feature.fromGeometry(
                Point.fromLngLat(
                    report.longitude,
                    report.latitude
                )
            )
        }

        incidentSource?.setGeoJson(
            FeatureCollection.fromFeatures(
                features.toTypedArray()
            )
        )
    }

    // SAVE POINTS PERMANENTLY

    private fun saveSavedPoints(points: List<SavedPoint>) {

        val jsonArray = JSONArray()

        points.forEach { point ->

            val jsonObject = JSONObject()

            jsonObject.put("name", point.name)
            jsonObject.put("latitude", point.latitude)
            jsonObject.put("longitude", point.longitude)

            jsonArray.put(jsonObject)
        }

        getSharedPreferences(
            "roadsync_data",
            MODE_PRIVATE
        ).edit()
            .putString("saved_points", jsonArray.toString())
            .apply()
    }

    // LOAD SAVED POINTS

    private fun loadSavedPoints(): List<SavedPoint> {

        val preferences = getSharedPreferences(
            "roadsync_data",
            MODE_PRIVATE
        )

        val savedData = preferences.getString(
            "saved_points",
            "[]"
        ) ?: "[]"

        return try {

            val jsonArray = JSONArray(savedData)
            val points = mutableListOf<SavedPoint>()

            for (i in 0 until jsonArray.length()) {

                val item = jsonArray.getJSONObject(i)

                points.add(
                    SavedPoint(
                        name = item.getString("name"),
                        latitude = item.getDouble("latitude"),
                        longitude = item.getDouble("longitude")
                    )
                )
            }

            points

        } catch (e: Exception) {
            emptyList()
        }
    }

    // SAVE INCIDENT REPORTS

    private fun saveIncidentReports(
        reports: List<IncidentReport>
    ) {

        val jsonArray = JSONArray()

        reports.forEach { report ->

            val jsonObject = JSONObject()

            jsonObject.put("id", report.id)
            jsonObject.put("type", report.type)
            jsonObject.put("description", report.description)
            jsonObject.put("latitude", report.latitude)
            jsonObject.put("longitude", report.longitude)
            jsonObject.put("timestamp", report.timestamp)

            jsonArray.put(jsonObject)
        }

        getSharedPreferences(
            "roadsync_data",
            MODE_PRIVATE
        ).edit()
            .putString("incident_reports", jsonArray.toString())
            .apply()
    }

    // LOAD INCIDENT REPORTS

    private fun loadIncidentReports(): List<IncidentReport> {

        val preferences = getSharedPreferences(
            "roadsync_data",
            MODE_PRIVATE
        )

        val savedData = preferences.getString(
            "incident_reports",
            "[]"
        ) ?: "[]"

        return try {

            val jsonArray = JSONArray(savedData)
            val reports = mutableListOf<IncidentReport>()

            for (i in 0 until jsonArray.length()) {

                val item = jsonArray.getJSONObject(i)

                reports.add(
                    IncidentReport(
                        id = item.getLong("id"),
                        type = item.getString("type"),
                        description = item.getString("description"),
                        latitude = item.getDouble("latitude"),
                        longitude = item.getDouble("longitude"),
                        timestamp = item.getLong("timestamp")
                    )
                )
            }

            reports

        } catch (e: Exception) {
            emptyList()
        }
    }

    // START GPS TRACKING

    @SuppressLint("MissingPermission")
    private fun startLocationTracking() {

        val fine =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val coarse =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (!fine && !coarse) return

        if (isTracking) {

            latestLocation?.let { location ->

                mapLibreMap?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(
                            location.latitude,
                            location.longitude
                        ),
                        16.0
                    )
                )
            }

            return
        }

        isTracking = true

        val request =
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                3000L
            )
                .setMinUpdateIntervalMillis(1500L)
                .build()

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->

                if (location != null) {

                    latestLocation = location
                    updateLocationMarker(location)

                    mapLibreMap?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(
                                location.latitude,
                                location.longitude
                            ),
                            16.0
                        )
                    )
                }
            }

        Toast.makeText(
            this,
            "GPS tracking started",
            Toast.LENGTH_SHORT
        ).show()
    }

    // STOP GPS TRACKING

    private fun stopLocationTracking() {

        if (isTracking) {

            fusedLocationClient.removeLocationUpdates(
                locationCallback
            )

            isTracking = false
        }
    }

    // MAPVIEW LIFECYCLE

    override fun onStart() {
        super.onStart()

        if (::mapView.isInitialized) {
            mapView.onStart()
        }
    }

    override fun onResume() {
        super.onResume()

        if (::mapView.isInitialized) {
            mapView.onResume()
        }
    }

    override fun onPause() {

        if (::mapView.isInitialized) {
            mapView.onPause()
        }

        super.onPause()
    }

    override fun onStop() {

        stopLocationTracking()

        if (::mapView.isInitialized) {
            mapView.onStop()
        }

        super.onStop()
    }

    override fun onDestroy() {

        stopLocationTracking()

        if (::mapView.isInitialized) {
            mapView.onDestroy()
        }

        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()

        if (::mapView.isInitialized) {
            mapView.onLowMemory()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {

        super.onSaveInstanceState(outState)

        if (::mapView.isInitialized) {
            mapView.onSaveInstanceState(outState)
        }
    }
}
