package com.invision.navigation2

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ArNavigationActivity : ComponentActivity() {

    private var mapId: String? = null
    private var mapName: String? = null

    private lateinit var requestCameraPermission: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestCameraPermission =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                showArContent()
            } else {
                Toast.makeText(this, "Camera permission is needed for AR navigation.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        mapId = intent.getStringExtra(EXTRA_MAP_ID)?.takeIf { it.isNotBlank() }
        mapName = intent.getStringExtra(EXTRA_MAP_NAME)?.takeIf { it.isNotBlank() } ?: mapId

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            showArContent()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun showArContent() {
        setContent {
            ArMapScreen(
                mapId = mapId,
                mapName = mapName ?: mapId ?: "Unnamed Map",
                onMapNameChanged = { mapName = it },
                onClose = { finish() }
            )
        }
    }

    @Composable
    private fun ArMapScreen(
        mapId: String?,
        mapName: String,
        onMapNameChanged: (String) -> Unit,
        onClose: () -> Unit
    ) {
        val activeMapId = mapId
        val engine = rememberEngine()
        val modelLoader = rememberModelLoader(engine)
        val placedMarkers = remember { mutableStateListOf<PlacedMarker>() }
        var selectedMarkerType by remember { mutableStateOf(MarkerType.T_REX) }
        var sessionRef by remember { mutableStateOf<Session?>(null) }
        val latestFrame = remember { arrayOfNulls<Frame>(1) }
        var loadedSavedMarkers by remember { mutableStateOf(false) }
        var statusText by remember {
            mutableStateOf("Move your phone until ARCore finds a surface. Tap a surface to place ${selectedMarkerType.label}.")
        }

        val tRexModel = rememberModelInstance(modelLoader, MarkerType.T_REX.assetPath)
        val triceratopsModel = rememberModelInstance(modelLoader, MarkerType.TRICERATOPS.assetPath)
        val stegosaurusModel = rememberModelInstance(modelLoader, MarkerType.STEGOSAURUS.assetPath)
        val apatosaurusModel = rememberModelInstance(modelLoader, MarkerType.APATOSAURUS.assetPath)
        val velociraptorModel = rememberModelInstance(modelLoader, MarkerType.VELOCIRAPTOR.assetPath)
        val parasaurolophusModel = rememberModelInstance(modelLoader, MarkerType.PARASAUROLOPHUS.assetPath)
        val modelInstances = mapOf(
            MarkerType.T_REX to tRexModel,
            MarkerType.TRICERATOPS to triceratopsModel,
            MarkerType.STEGOSAURUS to stegosaurusModel,
            MarkerType.APATOSAURUS to apatosaurusModel,
            MarkerType.VELOCIRAPTOR to velociraptorModel,
            MarkerType.PARASAUROLOPHUS to parasaurolophusModel
        )

        LaunchedEffect(sessionRef, activeMapId, loadedSavedMarkers) {
            val session = sessionRef ?: return@LaunchedEffect
            val id = activeMapId ?: return@LaunchedEffect
            if (loadedSavedMarkers) return@LaunchedEffect
            loadedSavedMarkers = true
            val loaded = loadSavedMarkers(id, session)
            onMapNameChanged(loaded.first ?: mapName)
            placedMarkers.addAll(loaded.second)
            statusText = "Loaded ${loaded.second.size} saved dinosaur markers."
        }

        Box(modifier = Modifier.fillMaxSize()) {
            ARSceneView(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                planeRenderer = true,
                sessionConfiguration = { session, config ->
                    config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                    config.depthMode =
                        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                            Config.DepthMode.AUTOMATIC
                        } else {
                            Config.DepthMode.DISABLED
                        }
                },
                onSessionUpdated = { session, frame ->
                    sessionRef = session
                    latestFrame[0] = frame
                },
                onGestureListener = rememberOnGestureListener(
                    onSingleTapConfirmed = { motionEvent: MotionEvent, _ ->
                        val frame = latestFrame[0] ?: return@rememberOnGestureListener
                        val hit = frame.hitTest(motionEvent.x, motionEvent.y).firstOrNull { hitResult: HitResult ->
                            val trackable = hitResult.trackable
                            trackable is Plane && trackable.isPoseInPolygon(hitResult.hitPose)
                        } ?: return@rememberOnGestureListener

                        if (placedMarkers.size >= MAX_MARKERS) {
                            val removed = placedMarkers.removeAt(0)
                            removed.anchor.detach()
                        }

                        placedMarkers.add(
                            PlacedMarker(
                                id = "marker_${System.currentTimeMillis()}",
                                type = selectedMarkerType,
                                anchor = hit.createAnchor()
                            )
                        )
                        saveMarkers(activeMapId, mapName, placedMarkers)
                        statusText = "Placed ${selectedMarkerType.label}. Markers: ${placedMarkers.size}"
                    }
                )
            ) {
                placedMarkers.forEach { marker ->
                    val modelInstance = modelInstances[marker.type]
                    AnchorNode(anchor = marker.anchor) {
                        modelInstance?.let {
                            ModelNode(
                                modelInstance = it,
                                scaleToUnits = marker.type.scaleToUnits,
                                centerOrigin = Position(x = 0f, y = -1f, z = 0f),
                                autoAnimate = true
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color(0xD9000000))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Map: $mapName",
                    color = Color(0xFFB7FFD0),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = statusText,
                    color = Color.White,
                    fontSize = 13.sp
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color(0xE6000000))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MarkerType.values().forEach { markerType ->
                        Button(
                            onClick = {
                                selectedMarkerType = markerType
                                statusText = "${markerType.label} selected."
                            }
                        ) {
                            Text(markerType.label)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val removed = placedMarkers.removeLastOrNull()
                            if (removed == null) {
                                statusText = "No markers to delete."
                            } else {
                                removed.anchor.detach()
                                saveMarkers(activeMapId, mapName, placedMarkers)
                                statusText = "Deleted last ${removed.type.label} marker."
                            }
                        }
                    ) {
                        Text("Delete Last Marker")
                    }
                    Button(onClick = onClose) {
                        Text("Close")
                    }
                }
            }
        }
    }

    private fun loadSavedMarkers(activeMapId: String, session: Session): Pair<String?, List<PlacedMarker>> {
        val file = mapFile(activeMapId)
        if (!file.exists()) return null to emptyList()

        return runCatching {
            val json = JSONObject(file.readText())
            val savedMarkers = json.optJSONArray("markers") ?: JSONArray()
            val markers = buildList {
                for (index in 0 until savedMarkers.length()) {
                    val marker = savedMarkers.getJSONObject(index)
                    add(
                        PlacedMarker(
                            id = marker.optString("id", "marker_$index"),
                            type = MarkerType.fromStorageKey(marker.optString("type")),
                            anchor = session.createAnchor(
                                Pose.makeTranslation(
                                    marker.optDouble("x", 0.0).toFloat(),
                                    marker.optDouble("y", 0.0).toFloat(),
                                    marker.optDouble("z", 0.0).toFloat()
                                )
                            )
                        )
                    )
                }
            }
            json.optString("name").takeIf { it.isNotBlank() } to markers
        }.getOrElse {
            null to emptyList()
        }
    }

    private fun saveMarkers(activeMapId: String?, activeMapName: String, markers: List<PlacedMarker>) {
        val id = activeMapId ?: return
        val markerArray = JSONArray()
        markers.forEach { marker ->
            val pose = marker.anchor.pose
            markerArray.put(
                JSONObject()
                    .put("id", marker.id)
                    .put("type", marker.type.storageKey)
                    .put("x", pose.tx())
                    .put("y", pose.ty())
                    .put("z", pose.tz())
            )
        }

        mapFile(id).apply {
            parentFile?.mkdirs()
            writeText(
                JSONObject()
                    .put("mapId", id)
                    .put("name", activeMapName)
                    .put("updatedAt", System.currentTimeMillis())
                    .put("markers", markerArray)
                    .toString(2)
            )
        }
    }

    private fun mapFile(activeMapId: String): File {
        return File(File(filesDir, MAP_DIRECTORY_NAME).also { it.mkdirs() }, "$activeMapId.json")
    }

    private data class PlacedMarker(
        val id: String,
        val type: MarkerType,
        val anchor: Anchor
    )

    private enum class MarkerType(
        val storageKey: String,
        val label: String,
        val assetPath: String,
        val scaleToUnits: Float
    ) {
        T_REX("t_rex", "T-Rex", "models/T-Rex.glb", 0.75f),
        TRICERATOPS("triceratops", "Triceratops", "models/Triceratops.glb", 0.75f),
        STEGOSAURUS("stegosaurus", "Stegosaurus", "models/Stegosaurus.glb", 0.75f),
        APATOSAURUS("apatosaurus", "Apatosaurus", "models/Apatosaurus.glb", 0.85f),
        VELOCIRAPTOR("velociraptor", "Velociraptor", "models/Velociraptor.glb", 0.65f),
        PARASAUROLOPHUS("parasaurolophus", "Parasaurolophus", "models/Parasaurolophus.glb", 0.75f);

        companion object {
            fun fromStorageKey(value: String?): MarkerType {
                return values().firstOrNull { it.storageKey == value } ?: T_REX
            }
        }
    }

    companion object {
        const val EXTRA_MAP_ID = "com.invision.navigation2.extra.MAP_ID"
        const val EXTRA_MAP_NAME = "com.invision.navigation2.extra.MAP_NAME"

        private const val MAX_MARKERS = 40
        private const val MAP_DIRECTORY_NAME = "invision_ar_maps"
    }
}
