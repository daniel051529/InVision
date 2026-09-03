package com.invision.navigation2

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        statusText = TextView(this).apply {
            text = "Create a map by scanning the fixed wall QR code first. Then name the map and place AR landmarks."
            textSize = 16f
            setTextColor(0xFF17211B.toInt())
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(18.dp(), 16.dp(), 18.dp(), 16.dp())
        }

        val startButton = Button(this).apply {
            text = "Create Map"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF146C43.toInt())
            minHeight = 64.dp()
            setOnClickListener { scanQrForMapCreation() }
        }

        val savedMapsButton = Button(this).apply {
            text = "Saved Maps"
            setOnClickListener { showSavedMaps() }
        }

        val title = TextView(this).apply {
            text = "InVision 2.0"
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF17211B.toInt())
        }

        val subtitle = TextView(this).apply {
            text = "QR anchored AR maps for blind indoor navigation."
            textSize = 17f
            setTextColor(0xFF4F6357.toInt())
            setPadding(0, 8.dp(), 0, 0)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF7FAF7.toInt())
            setPadding(22.dp(), 54.dp(), 22.dp(), 28.dp())
            addView(title, matchWrap())
            addView(subtitle, matchWrap())
            addView(startButton, matchWrap(top = 30))
            addView(statusText, matchWrap(top = 18))
            addView(savedMapsButton, matchWrap(top = 22))
            addView(
                TextView(this@MainActivity).apply {
                    text = "Supported marker choices: T-Rex, Triceratops, Stegosaurus, Apatosaurus, Velociraptor, Parasaurolophus."
                    textSize = 14f
                    setTextColor(0xFF4F6357.toInt())
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(4.dp(), 20.dp(), 4.dp(), 0)
                },
                matchWrap()
            )
        }

        setContentView(root)
    }

    private fun showCreateMapNameDialog(qrMapInfo: QrMapInfo) {
        if (mapFile(qrMapInfo.mapId).exists()) {
            statusText.text = "${qrMapInfo.name} already exists. Opening saved map."
            openArMap(qrMapInfo)
            return
        }

        val input = EditText(this).apply {
            hint = "Map name, e.g. Main Lobby"
            setText(qrMapInfo.name.takeUnless { it == qrMapInfo.mapId }.orEmpty())
        }

        AlertDialog.Builder(this)
            .setTitle("Name This QR Map")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val mapName = input.text.toString().trim()
                if (mapName.isBlank()) {
                    Toast.makeText(this, "Map name cannot be empty.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val mapInfo = qrMapInfo.copy(name = mapName)
                createMap(mapInfo)
                openArMap(mapInfo)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createMap(mapInfo: QrMapInfo) {
        writeMapFile(mapInfo, JSONArray())
        statusText.text = "Created ${mapInfo.name}. AR mode is ready for marker placement."
    }

    private fun showSavedMaps() {
        val maps = loadSavedMaps()
        if (maps.isEmpty()) {
            Toast.makeText(this, "No saved maps yet.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Saved Maps")
            .setItems(maps.map { it.name }.toTypedArray()) { _, which ->
                showSavedMapActions(maps[which])
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showSavedMapActions(mapInfo: QrMapInfo) {
        val actions = arrayOf("Open AR", "Rename Map", "View Map File", "Show QR Payload", "Delete Map")
        AlertDialog.Builder(this)
            .setTitle(mapInfo.name)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> openArMap(mapInfo)
                    1 -> showRenameMapDialog(mapInfo)
                    2 -> showMapFile(mapInfo)
                    3 -> showQrPayload(mapInfo)
                    4 -> confirmDeleteMap(mapInfo)
                }
            }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun showRenameMapDialog(mapInfo: QrMapInfo) {
        val input = EditText(this).apply {
            setText(mapInfo.name)
            selectAll()
            hint = "Map name"
        }

        AlertDialog.Builder(this)
            .setTitle("Rename Map")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isBlank()) {
                    Toast.makeText(this, "Map name cannot be empty.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                renameMap(mapInfo, newName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renameMap(mapInfo: QrMapInfo, newName: String) {
        val file = mapFile(mapInfo.mapId)
        if (!file.exists()) {
            Toast.makeText(this, "Map file does not exist.", Toast.LENGTH_SHORT).show()
            return
        }

        runCatching {
            val json = JSONObject(file.readText())
            json.put("name", newName)
            json.put("updatedAt", System.currentTimeMillis())
            file.writeText(json.toString(2))
        }.onSuccess {
            statusText.text = "${mapInfo.name} renamed to $newName."
        }.onFailure {
            Toast.makeText(this, "Could not rename map.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteMap(mapInfo: QrMapInfo) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${mapInfo.name}?")
            .setMessage("This deletes the saved map file and all placed AR markers for this QR code.")
            .setPositiveButton("Delete") { _, _ ->
                if (mapFile(mapInfo.mapId).delete()) {
                    statusText.text = "${mapInfo.name} deleted."
                } else {
                    Toast.makeText(this, "Could not delete map.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMapFile(mapInfo: QrMapInfo) {
        val file = mapFile(mapInfo.mapId)
        val content = if (file.exists()) file.readText() else "Map file does not exist yet."
        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setMessage("Path:\n${file.absolutePath}\n\n$content")
            .setPositiveButton("Open AR") { _, _ -> openArMap(mapInfo) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showQrPayload(mapInfo: QrMapInfo) {
        AlertDialog.Builder(this)
            .setTitle("QR Payload")
            .setMessage(mapInfo.toQrPayload())
            .setPositiveButton("Open AR") { _, _ -> openArMap(mapInfo) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openArMap(mapInfo: QrMapInfo) {
        startActivity(
            Intent(this, ArNavigationActivity::class.java)
                .putExtra(ArNavigationActivity.EXTRA_MAP_ID, mapInfo.mapId)
                .putExtra(ArNavigationActivity.EXTRA_MAP_NAME, mapInfo.name)
        )
    }

    private fun scanQrForMapCreation() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()

        GmsBarcodeScanning.getClient(this, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                val mapInfo = QrMapInfo.parse(barcode.rawValue.orEmpty())
                if (mapInfo == null) {
                    Toast.makeText(this, "This is not an InVision 2.0 map QR code.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                statusText.text = "QR code scanned. Name this map before AR marker placement."
                showCreateMapNameDialog(mapInfo)
            }
            .addOnCanceledListener {
                Toast.makeText(this, "QR scan canceled.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "QR scan failed: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun matchWrap(top: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            if (top > 0) topMargin = top.dp()
        }
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun writeMapFile(mapInfo: QrMapInfo, markers: JSONArray) {
        mapFile(mapInfo.mapId).apply {
            parentFile?.mkdirs()
            writeText(
                JSONObject()
                    .put("mapId", mapInfo.mapId)
                    .put("name", mapInfo.name)
                    .put("updatedAt", System.currentTimeMillis())
                    .put("markers", markers)
                    .toString(2)
            )
        }
    }

    private fun loadSavedMaps(): List<QrMapInfo> {
        return mapDirectory()
            .listFiles { file -> file.isFile && file.extension == "json" }
            ?.mapNotNull { file ->
                runCatching {
                    val json = JSONObject(file.readText())
                    val mapId = json.optString("mapId", file.nameWithoutExtension)
                    QrMapInfo(mapId, json.optString("name", mapId))
                }.getOrNull()
            }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    }

    private fun mapFile(mapId: String): File {
        return File(mapDirectory(), "$mapId.json")
    }

    private fun mapDirectory(): File {
        return File(filesDir, MAP_DIRECTORY_NAME).also { it.mkdirs() }
    }

    private data class QrMapInfo(
        val mapId: String,
        val name: String
    ) {
        fun toQrPayload(): String {
            return JSONObject()
                .put("mapId", mapId)
                .put("name", name)
                .toString()
        }

        companion object {
            fun parse(raw: String): QrMapInfo? {
                val qrText = raw.trim()
                if (qrText.isBlank()) return null

                if (qrText.startsWith("{")) {
                    return runCatching {
                        val json = JSONObject(qrText)
                        val mapId = safeMapId(json.optString("mapId"))
                        if (mapId.isBlank()) null else QrMapInfo(mapId, json.optString("name", mapId))
                    }.getOrNull()
                }

                val uri = runCatching { Uri.parse(qrText) }.getOrNull()
                if (uri?.scheme == "invision" && uri.host == "map") {
                    val rawMapId = uri.pathSegments.firstOrNull().orEmpty()
                    val mapId = safeMapId(rawMapId)
                    if (mapId.isNotBlank()) return QrMapInfo(mapId, rawMapId.ifBlank { mapId })
                }

                val fallbackMapId = safeMapId(qrText)
                return if (fallbackMapId.isNotBlank()) QrMapInfo(fallbackMapId, qrText) else null
            }

            fun safeMapId(value: String): String {
                return value
                    .trim()
                    .lowercase()
                    .replace(Regex("[^a-z0-9_-]+"), "_")
                    .trim('_')
            }
        }
    }

    companion object {
        private const val MAP_DIRECTORY_NAME = "invision_ar_maps"
    }
}
