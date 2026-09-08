/*
 * Switchly
 * Copyright (C) 2025-2026 Saltyy
 * Copyright (C) 2026 Switchly Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package at.saltyy.switchly.feature.schedule

import android.content.Context
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.location.Geocoder.GeocodeListener
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import at.saltyy.switchly.R
import at.saltyy.switchly.theme.CustomAccentApplier
import at.saltyy.switchly.ui.EdgeToEdgeUtils
import at.saltyy.switchly.ui.ThemeUtils
import at.saltyy.switchly.ui.applySwitchlyStyle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class LocationMapPickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LATITUDE = "at.saltyy.switchly.extra.LATITUDE"
        const val EXTRA_LONGITUDE = "at.saltyy.switchly.extra.LONGITUDE"
        const val EXTRA_LABEL = "at.saltyy.switchly.extra.LABEL"

        private const val EXTRA_INITIAL_LATITUDE = "at.saltyy.switchly.extra.INITIAL_LATITUDE"
        private const val EXTRA_INITIAL_LONGITUDE = "at.saltyy.switchly.extra.INITIAL_LONGITUDE"
        private const val EXTRA_INITIAL_LABEL = "at.saltyy.switchly.extra.INITIAL_LABEL"
        private const val MAP_LOAD_TIMEOUT_MS = 15_000L

        fun createIntent(
            context: Context,
            initialLatitude: Double?,
            initialLongitude: Double?,
            initialLabel: String?
        ): Intent = Intent(context, LocationMapPickerActivity::class.java).apply {
            if (initialLatitude != null && initialLongitude != null) {
                putExtra(EXTRA_INITIAL_LATITUDE, initialLatitude)
                putExtra(EXTRA_INITIAL_LONGITUDE, initialLongitude)
            }
            if (!initialLabel.isNullOrBlank()) {
                putExtra(EXTRA_INITIAL_LABEL, initialLabel)
            }
        }
    }

    private lateinit var selectionView: TextView
    private lateinit var layoutQuery: TextInputLayout
    private lateinit var inputQuery: EditText
    private lateinit var btnSearch: MaterialButton
    private lateinit var btnUseSelection: MaterialButton

    private val mapLoadHandler = Handler(Looper.getMainLooper())
    private var googleMap: GoogleMap? = null
    private var mapLoaded = false
    private var pickedLatLng: LatLng? = null
    private var pickedLabel: String? = null

    private val mapLoadTimeout = Runnable {
        if (!mapLoaded && !isFinishing && !isDestroyed) {
            showMapUnavailableMessage()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_map_picker)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarLocationMapPicker)
        EdgeToEdgeUtils.setupClassic(activity = this, toolbar = toolbar)
        CustomAccentApplier.applyIfNeeded(this)
        toolbar.setNavigationOnClickListener { finish() }

        selectionView = findViewById(R.id.tvMapPickerSelection)
        layoutQuery = findViewById(R.id.layoutMapLocationQuery)
        inputQuery = findViewById(R.id.inputMapLocationQuery)
        btnSearch = findViewById(R.id.btnMapPickerSearch)
        btnUseSelection = findViewById(R.id.btnMapPickerUseSelection)

        val initialLatitude = intent.getDoubleExtra(EXTRA_INITIAL_LATITUDE, Double.NaN)
        val initialLongitude = intent.getDoubleExtra(EXTRA_INITIAL_LONGITUDE, Double.NaN)
        val initialLabel = intent.getStringExtra(EXTRA_INITIAL_LABEL)

        val hasInitialPoint = !initialLatitude.isNaN() && !initialLongitude.isNaN()
        if (hasInitialPoint) {
            pickedLatLng = LatLng(initialLatitude, initialLongitude)
            pickedLabel = initialLabel
        }
        if (!initialLabel.isNullOrBlank()) {
            inputQuery.setText(initialLabel)
        }
        updateSelectionLabel(pickedLatLng, pickedLabel)

        inputQuery.addTextChangedListener { layoutQuery.error = null }
        inputQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                runSearch()
                true
            } else {
                false
            }
        }
        btnSearch.setOnClickListener { runSearch() }
        btnUseSelection.setOnClickListener { finishWithSelection() }

        setupMap(hasInitialPoint)
    }

    override fun onDestroy() {
        mapLoadHandler.removeCallbacks(mapLoadTimeout)
        googleMap?.setOnMapClickListener(null)
        googleMap?.setOnMapLoadedCallback(null)
        super.onDestroy()
    }

    private fun setupMap(hasInitialPoint: Boolean) {
        val startLatLng = pickedLatLng ?: LatLng(48.2082, 16.3738)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as? SupportMapFragment

        if (mapFragment == null) {
            showMapUnavailableMessage()
            return
        }

        mapLoadHandler.postDelayed(mapLoadTimeout, MAP_LOAD_TIMEOUT_MS)

        mapFragment.getMapAsync { map ->
            googleMap = map
            map.mapType = GoogleMap.MAP_TYPE_NORMAL
            map.setMinZoomPreference(6f)

            with(map.uiSettings) {
                isZoomControlsEnabled = true
                isZoomGesturesEnabled = true
                isScrollGesturesEnabled = true
                isTiltGesturesEnabled = false
                isRotateGesturesEnabled = false
                isCompassEnabled = true
                isMapToolbarEnabled = false
                isMyLocationButtonEnabled = false
            }

            map.setOnMapLoadedCallback {
                mapLoaded = true
                mapLoadHandler.removeCallbacks(mapLoadTimeout)
            }

            map.moveCamera(CameraUpdateFactory.newLatLngZoom(startLatLng, if (hasInitialPoint) 17f else 12f))
            pickedLatLng?.let { renderMarker(it, pickedLabel, moveCamera = false) }

            map.setOnMapClickListener { point ->
                pickedLatLng = point
                renderMarker(point)
            }
        }
    }

    private fun showMapUnavailableMessage() {
        val root = findViewById<android.view.View>(R.id.locationMapPickerContent)
        Snackbar.make(
            root,
            R.string.schedules_location_map_picker_load_failed,
            Snackbar.LENGTH_LONG,
        ).applySwitchlyStyle().show()
    }

    private fun runSearch() {
        val query = inputQuery.text?.toString()?.trim().orEmpty()
        if (query.isBlank()) {
            layoutQuery.error = getString(R.string.schedules_location_picker_invalid)
            return
        }

        layoutQuery.error = null
        btnSearch.isEnabled = false
        lifecycleScope.launch {
            val resolved = resolveLocationQuery(query)
            btnSearch.isEnabled = true
            if (resolved == null) {
                layoutQuery.error = getString(R.string.schedules_location_picker_not_found)
                return@launch
            }

            val point = LatLng(resolved.latitude, resolved.longitude)
            pickedLatLng = point
            renderMarker(point, resolved.label, moveCamera = true)
        }
    }

    private fun finishWithSelection() {
        val point = pickedLatLng
        if (point == null) {
            selectionView.text = getString(R.string.schedules_location_map_picker_no_selection)
            return
        }

        val fallbackLabel = getString(
            R.string.schedules_location_coords_fmt,
            point.latitude,
            point.longitude
        )

        lifecycleScope.launch {
            val label = reverseGeocodeLabel(point.latitude, point.longitude, fallbackLabel)
            val result = Intent().apply {
                putExtra(EXTRA_LATITUDE, point.latitude)
                putExtra(EXTRA_LONGITUDE, point.longitude)
                putExtra(EXTRA_LABEL, label ?: fallbackLabel)
            }
            setResult(RESULT_OK, result)
            finish()
        }
    }

    private fun renderMarker(point: LatLng, label: String? = null, moveCamera: Boolean = false) {
        googleMap?.let { map ->
            map.clear()
            map.addMarker(MarkerOptions().position(point))
            if (moveCamera) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 17f))
            }
        }
        updateSelectionLabel(point, label)
    }

    private fun updateSelectionLabel(point: LatLng?, label: String? = null) {
        if (point == null) {
            selectionView.text = getString(R.string.schedules_location_map_picker_no_selection)
            return
        }

        val displayLabel = label ?: getString(
            R.string.schedules_location_coords_fmt,
            point.latitude,
            point.longitude
        )
        pickedLabel = displayLabel
        selectionView.text = getString(R.string.schedules_location_map_picker_selected, displayLabel)
    }

    private suspend fun resolveLocationQuery(query: String): ResolvedLocation? = withContext(Dispatchers.IO) {
        runCatching {
            val geocoder = Geocoder(this@LocationMapPickerActivity, Locale.getDefault())
            getFromLocationNameBlockingCompat(geocoder, query).firstOrNull()?.let { address ->
                ResolvedLocation(
                    latitude = address.latitude,
                    longitude = address.longitude,
                    label = formatGeocoderLabel(address) ?: query.trim()
                )
            }
        }.getOrNull()
    }

    private suspend fun reverseGeocodeLabel(
        latitude: Double,
        longitude: Double,
        fallback: String
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val geocoder = Geocoder(this@LocationMapPickerActivity, Locale.getDefault())
            getFromLocationBlockingCompat(geocoder, latitude, longitude).firstOrNull()?.let { address ->
                formatGeocoderLabel(address) ?: fallback
            }
        }.getOrNull()
    }

    private suspend fun getFromLocationNameBlockingCompat(
        geocoder: Geocoder,
        query: String
    ): List<Address> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocationName(query, 1, object : GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses)
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) continuation.resume(emptyList())
                    }
                })
            }
        }

        return runCatching {
            Geocoder::class.java
                .getMethod(
                    "getFromLocationName",
                    String::class.java,
                    Int::class.javaPrimitiveType
                )
                .invoke(geocoder, query, 1)
                .asAddressList()
        }.getOrDefault(emptyList())
    }

    private suspend fun getFromLocationBlockingCompat(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double
    ): List<Address> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(latitude, longitude, 1, object : GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses)
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) continuation.resume(emptyList())
                    }
                })
            }
        }

        return runCatching {
            Geocoder::class.java
                .getMethod(
                    "getFromLocation",
                    Double::class.javaPrimitiveType,
                    Double::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                .invoke(geocoder, latitude, longitude, 1)
                .asAddressList()
        }.getOrDefault(emptyList())
    }

    private fun Any?.asAddressList(): List<Address> {
        return (this as? List<*>)
            ?.filterIsInstance<Address>()
            .orEmpty()
    }

    private fun formatGeocoderLabelDetailed(address: Address): Pair<String?, String?> {
        val thoroughfare = address.thoroughfare?.trim()?.takeIf { it.isNotBlank() }
        val subThoroughfare = address.subThoroughfare?.trim()?.takeIf { it.isNotBlank() }
        val feature = address.featureName?.trim()?.takeIf { it.isNotBlank() }
        val line0 = address.getAddressLine(0)?.trim()?.takeIf { it.isNotBlank() }
        val subLocality = address.subLocality?.trim()?.takeIf { it.isNotBlank() }
        val locality = address.locality?.trim()?.takeIf { it.isNotBlank() }
        val adminArea = address.adminArea?.trim()?.takeIf { it.isNotBlank() }
        val countryName = address.countryName?.trim()?.takeIf { it.isNotBlank() }

        fun isOnlyNumber(s: String?): Boolean {
            return s != null && s.all { it.isDigit() || it.isWhitespace() || it == '-' || it == '/' }
        }

        val streetPart = when {
            thoroughfare != null -> {
                val num = subThoroughfare ?: feature?.takeIf { isOnlyNumber(it) || it.length <= 6 }
                if (num != null && !thoroughfare.contains(num)) {
                    "$num $thoroughfare"
                } else {
                    thoroughfare
                }
            }
            line0 != null -> {
                val parts = line0.split(",").map { it.trim() }.filter { it.isNotBlank() }
                val firstPart = parts.firstOrNull()
                if (firstPart != null && !isOnlyNumber(firstPart)) {
                    firstPart
                } else {
                    null
                }
            }
            else -> feature?.takeIf { !isOnlyNumber(it) }
        }

        val suburb = subLocality ?: locality ?: run {
            if (line0 != null) {
                val parts = line0.split(",").map { it.trim() }.filter { it.isNotBlank() }
                if (parts.size > 1) {
                    parts[1].replace(Regex("\\b[0-9]{4,6}\\b"), "").trim().takeIf { it.isNotBlank() } ?: parts[1]
                } else null
            } else null
        }

        val title = when {
            streetPart != null && suburb != null -> {
                if (streetPart.contains(suburb, ignoreCase = true)) {
                    streetPart
                } else {
                    "$streetPart, $suburb"
                }
            }
            streetPart != null -> streetPart
            suburb != null -> suburb
            else -> line0 ?: listOfNotNull(locality, adminArea).joinToString(", ").takeIf { it.isNotBlank() }
        }

        val subParts = mutableListOf<String>()
        if (locality != null && locality != suburb && title?.contains(locality, ignoreCase = true) == false) {
            subParts.add(locality)
        }
        if (adminArea != null && title?.contains(adminArea, ignoreCase = true) == false) {
            subParts.add(adminArea)
        }
        if (countryName != null && title?.contains(countryName, ignoreCase = true) == false) {
            subParts.add(countryName)
        }
        val subtitle = if (subParts.isNotEmpty()) {
            subParts.joinToString(", ")
        } else if (line0 != null && line0 != title) {
            line0
        } else {
            null
        }

        return title to subtitle
    }

    private fun formatGeocoderLabel(address: Address): String? {
        return formatGeocoderLabelDetailed(address).first
    }

    private data class ResolvedLocation(
        val latitude: Double,
        val longitude: Double,
        val label: String?
    )
}
