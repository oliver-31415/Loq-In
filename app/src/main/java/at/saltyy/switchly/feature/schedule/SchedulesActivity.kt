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

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.graphics.drawable.GradientDrawable
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.saltyy.switchly.BuildConfig
import at.saltyy.switchly.R
import at.saltyy.switchly.blocking.BlockingRuntime
import at.saltyy.switchly.data.prefs.AutomationModeStore
import at.saltyy.switchly.data.prefs.EmergencyBypassStore
import at.saltyy.switchly.data.prefs.ExactAlarmPermissionSync
import at.saltyy.switchly.data.prefs.ProfileStore
import at.saltyy.switchly.data.prefs.ScheduleInsights
import at.saltyy.switchly.data.prefs.SchedulePlanner
import at.saltyy.switchly.data.prefs.ScheduleRuntimeStore
import at.saltyy.switchly.data.prefs.ScheduleStore
import at.saltyy.switchly.data.prefs.ScheduleStore.Days
import at.saltyy.switchly.data.prefs.SwitchModeStore
import at.saltyy.switchly.feature.settings.PermissionsActivity
import at.saltyy.switchly.feature.settings.ToggleOptionsActivity
import at.saltyy.switchly.platform.receiver.location.LocationTriggerMonitor
import at.saltyy.switchly.platform.receiver.schedule.ScheduleReceiver
import at.saltyy.switchly.premium.PremiumManager
import at.saltyy.switchly.theme.AccentColor
import at.saltyy.switchly.theme.CustomAccentApplier
import at.saltyy.switchly.ui.EdgeToEdgeUtils
import at.saltyy.switchly.ui.SwitchlyDropdownAdapter
import at.saltyy.switchly.ui.ThemeUtils
import at.saltyy.switchly.ui.applySwitchlyStyle
import at.saltyy.switchly.ui.attachEditDeleteSwipe
import at.saltyy.switchly.ui.updateSelectionSubtitle
import at.saltyy.switchly.ui.dialog.showAccented
import at.saltyy.switchly.ui.dialog.showDestructiveAccented
import at.saltyy.switchly.ui.dialog.styleSwitchlyDestructivePositiveButton
import at.saltyy.switchly.ui.dialog.SwitchlyDialogOption
import at.saltyy.switchly.ui.dialog.showSwitchlyOptionDialog
import at.saltyy.switchly.ui.dialog.styleSwitchlyDialogButtons
import at.saltyy.switchly.ui.dialog.SwitchlyInfoRow
import at.saltyy.switchly.ui.dialog.showSwitchlyInfoDialog
import at.saltyy.switchly.ui.dialog.applySwitchlyDialogWidth
import at.saltyy.switchly.util.BatteryOptimizationCompat
import at.saltyy.switchly.util.EditingLockGuard
import at.saltyy.switchly.util.TimeFormatPrefs
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.net.InetAddress
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class SchedulesActivity : AppCompatActivity() {

    private enum class NewScheduleMode { TIME, WIFI, BT, LOCATION }
    private enum class Kind { TIME, WIFI, BT, LOCATION }
    private enum class TimeMode { SINGLE, TIME_RANGE, DATE_RANGE }

    companion object {
        const val EXTRA_PROFILE_NAME = "extra_profile_name"
        const val EXTRA_OPEN_ADD_TIME = "extra_open_add_time"
        private const val PREFS_SCHEDULE_HEALTH = "switchly_schedule_health"
        const val KEY_BATTERY_OPTIMIZATION_CONFIRMED_MAX_AVAILABLE = "battery_optimization_confirmed_max_available"
        const val GOOGLE_MAPS_DNS_HOST = "clients4.google.com"
        const val GOOGLE_MAPS_REACHABILITY_TIMEOUT_MS = 2_500L
    }

    private data class ResolvedLocation(
        val latitude: Double,
        val longitude: Double,
        val label: String?,
        val subtitle: String? = null
    )

    private lateinit var adapter: ScheduleAdapter
    private lateinit var toolbar: MaterialToolbar
    private lateinit var emptyState: View
    private lateinit var cardScheduleHealth: View
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvStatusBody: TextView
    private lateinit var tvStatusFooter: TextView
    private lateinit var btnStatusAction: MaterialButton
    private lateinit var rowStatusAction: View
    private lateinit var btnStatusInfo: View
    private lateinit var ivStatusIcon: ImageView
    private lateinit var tvStatusActionTitle: TextView
    private lateinit var dividerStatus: View

    private val targetProfile: String?
        get() = intent.getStringExtra(EXTRA_PROFILE_NAME)?.trim()?.takeIf { it.isNotBlank() }

    private fun matchesTargetProfile(schedule: ScheduleStore.Schedule): Boolean {
        val target = targetProfile ?: return true
        val schedProf = schedule.profile.trim()
        val effectiveProfile = if (schedProf.isBlank()) {
            ProfileStore.getCurrent(this) ?: "Default"
        } else {
            schedProf
        }
        return effectiveProfile.equals(target, ignoreCase = true)
    }

    private var isScheduleUiReadOnly = false

    private var isSelectionMode = false
    private val selectedScheduleIds = linkedSetOf<Int>()

    private var pendingAfterLocationGrant: (() -> Unit)? = null
    private var pendingAfterFineLocationGrant: (() -> Unit)? = null
    private var pendingAfterBluetoothGrant: (() -> Unit)? = null

    private var pendingMapPickerCallback: ((ResolvedLocation) -> Unit)? = null

    private val locationMapPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = pendingMapPickerCallback
            pendingMapPickerCallback = null

            if (result.resultCode != RESULT_OK || callback == null) return@registerForActivityResult

            val data = result.data ?: return@registerForActivityResult
            val latitude = data.getDoubleExtra(LocationMapPickerActivity.EXTRA_LATITUDE, Double.NaN)
            val longitude = data.getDoubleExtra(LocationMapPickerActivity.EXTRA_LONGITUDE, Double.NaN)
            if (latitude.isNaN() || longitude.isNaN()) return@registerForActivityResult

            callback(
                ResolvedLocation(
                    latitude = latitude,
                    longitude = longitude,
                    label = data.getStringExtra(LocationMapPickerActivity.EXTRA_LABEL)
                )
            )
        }

    private val nfcLockedActions = setOf(
        ScheduleStore.Action.DISABLE,
        ScheduleStore.Action.TOGGLE,
        ScheduleStore.Action.ENABLE_AND_DISABLE,
        ScheduleStore.Action.DISABLE_AND_ENABLE
    )

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val action = pendingAfterLocationGrant
            pendingAfterLocationGrant = null
            if (granted) {
                action?.invoke()
            } else {
                showScheduleMessage(R.string.perm_location_denied_wifi_schedule)
            }
            refreshList()
        }

    private val requestFineLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val action = pendingAfterFineLocationGrant
            pendingAfterFineLocationGrant = null
            if (granted) {
                action?.invoke()
            } else {
                showScheduleMessage(R.string.perm_location_denied_geofence_schedule)
            }
            refreshList()
        }

    private val requestBluetoothPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val action = pendingAfterBluetoothGrant
            pendingAfterBluetoothGrant = null
            if (granted) {
                action?.invoke()
            } else {
                showScheduleMessage(R.string.perm_bt_denied_schedule)
            }
            refreshList()
        }

    private fun isNfcLockActiveForSchedules(): Boolean {
        return AutomationModeStore.isNfcAllowed(this) &&
            SwitchModeStore.isEnabled(this) &&
            SwitchModeStore.isNfcRequiredForDisable(this)
    }

    private fun isScheduleAutomationAllowed(): Boolean {
        return AutomationModeStore.isScheduleAllowed(this)
    }

    private fun isScheduleEditingLocked(): Boolean {
        return EditingLockGuard.isLocked(this)
    }

    private fun canEditSchedules(): Boolean {
        return isScheduleAutomationAllowed() && !isScheduleEditingLocked()
    }

    private fun currentAutomationModeLabel(): String {
        return when (AutomationModeStore.getMode(this)) {
            AutomationModeStore.Mode.SCHEDULE -> getString(R.string.pref_mode_schedule_title)
            AutomationModeStore.Mode.NFC -> getString(R.string.pref_mode_nfc_title)
            AutomationModeStore.Mode.QR -> getString(R.string.pref_mode_qr_title)
            AutomationModeStore.Mode.BARCODE -> getString(R.string.pref_mode_barcode_title)
            AutomationModeStore.Mode.MIXED -> getString(R.string.pref_mode_mixed_title)
        }
    }

    private fun openProtectionControls() {
        startActivity(
            Intent(this, ToggleOptionsActivity::class.java).apply {
                putExtra(
                    ToggleOptionsActivity.EXTRA_SCROLL_TO_SECTION,
                    ToggleOptionsActivity.SECTION_BLOCKING
                )
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedules)

        toolbar = findViewById(R.id.toolbar)

        EdgeToEdgeUtils.setupClassic(
            activity = this,
            toolbar = toolbar
        )
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        setSupportActionBar(toolbar)
        toolbar.subtitle = targetProfile ?: getString(R.string.schedules_profile_subtitle)
        toolbar.setNavigationOnClickListener {
            if (isSelectionMode) {
                exitSelectionMode()
            } else {
                finish()
            }
        }
        onBackPressedDispatcher.addCallback(this) {
            if (isSelectionMode) {
                exitSelectionMode()
            } else {
                finish()
            }
        }
        toolbar.setBackgroundColor(AccentColor.getToolbarColor(this))

        val recycler = findViewById<RecyclerView>(R.id.recyclerSchedules)
        recycler.layoutManager = LinearLayoutManager(this)
        emptyState = findViewById(R.id.tvEmpty)

        cardScheduleHealth = findViewById(R.id.cardScheduleHealth)
        tvStatusTitle = cardScheduleHealth.findViewById(R.id.tvStatusTitle)
        tvStatusBody = cardScheduleHealth.findViewById(R.id.tvStatusBody)
        tvStatusFooter = cardScheduleHealth.findViewById(R.id.tvStatusFooter)
        btnStatusAction = cardScheduleHealth.findViewById(R.id.btnStatusAction)
        rowStatusAction = cardScheduleHealth.findViewById(R.id.rowStatusAction)
        btnStatusInfo = cardScheduleHealth.findViewById(R.id.btnStatusInfo)
        ivStatusIcon = cardScheduleHealth.findViewById(R.id.ivStatusIcon)
        tvStatusActionTitle = cardScheduleHealth.findViewById(R.id.tvStatusActionTitle)
        dividerStatus = cardScheduleHealth.findViewById(R.id.dividerStatus)

        ivStatusIcon.setImageResource(R.drawable.schedule_24)
        tvStatusActionTitle.setText(R.string.schedules_health_action_title)
        btnStatusInfo.visibility = View.VISIBLE
        btnStatusInfo.setOnClickListener { showScheduleHealthInfoDialog() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SwitchModeStore.enabledFlow.collect {
                    runOnUiThread { refreshList() }
                }
            }
        }

        adapter = ScheduleAdapter(
            onToggleEnabled = { schedule, enabled ->
                if (canEditSchedules()) {
                    val list = ScheduleStore.getAll(this).map {
                        if (it.id == schedule.id) it.copy(enabled = enabled) else it
                    }
                    ScheduleStore.saveAll(this, list)
                    LocationTriggerMonitor.syncAsync(this)
                    reapplySchedulesNow()
                    SchedulePlanner.updateNextAlarm(this)
                    SchedulePlanner.notifyNextChanged(this)
                    if (enabled) {
                        showEnabledScheduleOverlapWarning(schedule.id, list)
                    }
                }
            },
            canInteract = { canEditSchedules() },
            isSelectionMode = { isSelectionMode },
            isSelected = { id -> selectedScheduleIds.contains(id) },
            onToggleSelection = { id -> toggleSelection(id) },
            onEnterSelection = { preselectId -> enterSelectionMode(preselectId) },
            onEdit = { schedule ->
                if (canEditSchedules()) {
                    showScheduleDialog(existing = schedule, preselectedMode = null)
                }
            },
            onTest = { schedule -> showScheduleTest(schedule) },
            getTargetProfile = { targetProfile },
        )
        recycler.adapter = adapter
        recycler.attachEditDeleteSwipe(
            canSwipe = { !isSelectionMode && canEditSchedules() },
            onEdit = { position ->
                adapter.itemAt(position)?.let { schedule ->
                    showScheduleDialog(existing = schedule, preselectedMode = null)
                }
            },
            onDelete = { position ->
                adapter.itemAt(position)?.let(::confirmDeleteSchedule)
            }
        )

        val addScheduleClick = View.OnClickListener {
            if (!canEditSchedules()) {
                return@OnClickListener
            }
            showNewScheduleTypeDialog()
        }
        val accent = AccentColor.getAccentColorInt(this)
        findViewById<ImageView>(R.id.ivEmptyScheduleIcon)?.imageTintList = ColorStateList.valueOf(accent)
        (findViewById<View>(R.id.btnEmptyAddSchedule) as? MaterialButton)?.apply {
            strokeColor = ColorStateList.valueOf(accent)
            setTextColor(accent)
            iconTint = ColorStateList.valueOf(accent)
            rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 0x26))
        }

        findViewById<View>(R.id.fabAdd).setOnClickListener(addScheduleClick)
        findViewById<View>(R.id.btnEmptyAddSchedule).setOnClickListener(addScheduleClick)

        refreshList()

        if (intent.getBooleanExtra(EXTRA_OPEN_ADD_TIME, false)) {
            intent.removeExtra(EXTRA_OPEN_ADD_TIME)
            window.decorView.post {
                if (canEditSchedules()) {
                    showScheduleDialog(null, NewScheduleMode.TIME)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_schedules, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val canInteract = canEditSchedules()
        val hasItems = adapter.itemCount > 0
        menu.findItem(R.id.action_select)?.isVisible = canInteract && !isSelectionMode && hasItems
        menu.findItem(R.id.action_cancel_selection)?.isVisible = canInteract && isSelectionMode
        menu.findItem(R.id.action_delete_selected)?.isVisible = canInteract && isSelectionMode

        val del = menu.findItem(R.id.action_delete_selected)
        val canDelete = canInteract && selectedScheduleIds.isNotEmpty()
        del?.isEnabled = canDelete
        del?.alphaCompat(if (canDelete) 1f else 0.4f)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (isSelectionMode) {
                    exitSelectionMode()
                    true
                } else {
                    finish()
                    true
                }
            }
            R.id.action_select -> {
                if (canEditSchedules()) {
                    enterSelectionMode(null)
                }
                true
            }
            R.id.action_cancel_selection -> {
                exitSelectionMode()
                true
            }
            R.id.action_delete_selected -> {
                if (canEditSchedules()) {
                    confirmDeleteSelectedSchedules()
                }
                true
            }
            R.id.action_info -> {
                showScheduleActionInfoDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun MenuItem.alphaCompat(alpha: Float) {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        icon?.mutate()?.alpha = a
    }

    private fun updateMenuState() {
        invalidateOptionsMenu()
        val canInteract = canEditSchedules()
        findViewById<View>(R.id.fabAdd)?.apply {
            (this as? com.google.android.material.floatingactionbutton.FloatingActionButton)?.backgroundTintList =
                ColorStateList.valueOf(AccentColor.getAccentColorInt(this@SchedulesActivity))
            visibility = if (isSelectionMode) View.GONE else View.VISIBLE
            isEnabled = canInteract
            isClickable = canInteract
            alpha = if (canInteract) 1f else 0.45f
        }
        findViewById<View>(R.id.btnEmptyAddSchedule)?.apply {
            isEnabled = canInteract
            isClickable = canInteract
            alpha = if (canInteract) 1f else 0.45f
            (this as? MaterialButton)?.apply {
                val accentColor = AccentColor.getAccentColorInt(this@SchedulesActivity)
                strokeColor = ColorStateList.valueOf(accentColor)
                setTextColor(accentColor)
                iconTint = ColorStateList.valueOf(accentColor)
            }
        }
        if (::toolbar.isInitialized) {
            toolbar.updateSelectionSubtitle(
                isSelectionMode,
                selectedScheduleIds.size,
                targetProfile ?: getString(R.string.schedules_profile_subtitle)
            )
        }
    }

    private fun enterSelectionMode(preselectId: Int?) {
        if (!canEditSchedules()) {
            return
        }
        isSelectionMode = true
        selectedScheduleIds.clear()
        preselectId?.let { selectedScheduleIds.add(it) }
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
        updateMenuState()
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedScheduleIds.clear()
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
        updateMenuState()
    }

    private fun toggleSelection(id: Int) {
        if (!isSelectionMode) {
            return
        }
        if (selectedScheduleIds.contains(id)) {
            selectedScheduleIds.remove(id)
        } else {
            selectedScheduleIds.add(id)
        }
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
        updateMenuState()
    }

    private fun confirmDeleteSelectedSchedules() {
        if (!canEditSchedules()) {
            return
        }
        if (selectedScheduleIds.isEmpty()) {
            return
        }
        val count = selectedScheduleIds.size
        val dlg = AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete))
            .setMessage(resources.getQuantityString(R.plurals.delete_schedules_confirm, count, count) + "\n\n" + getString(R.string.destructive_cannot_be_undone))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete, null)
            .create()

        dlg.setOnShowListener {
            dlg.styleSwitchlyDestructivePositiveButton()
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                deleteScheduleIds(selectedScheduleIds)
                exitSelectionMode()
                refreshList()
                dlg.dismiss()
            }
        }
        dlg.show()
    }

    private fun confirmDeleteSchedule(schedule: ScheduleStore.Schedule) {
        if (!canEditSchedules()) {
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(
                resources.getQuantityString(R.plurals.delete_schedules_confirm, 1, 1) +
                    "\n\n" +
                    getString(R.string.destructive_cannot_be_undone)
            )
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteScheduleIds(setOf(schedule.id))
                refreshList()
            }
            .showDestructiveAccented()
    }

    private fun deleteScheduleIds(ids: Set<Int>) {
        if (!canEditSchedules()) {
            return
        }
        if (ids.isEmpty()) {
            return
        }
        val remaining = ScheduleStore.getAll(this).filterNot { it.id in ids }
        ScheduleStore.saveAll(this, remaining)
        LocationTriggerMonitor.syncAsync(this)
        SchedulePlanner.updateNextAlarm(this)
        SchedulePlanner.notifyNextChanged(this)
    }

    private fun rootContentView(): View? {
        val content = findViewById<ViewGroup>(android.R.id.content)
        return content?.getChildAt(0)
    }

    override fun onResume() {
        super.onResume()

        ExactAlarmPermissionSync.syncAndReschedule(this, reason = "schedules_resume")

        refreshList()

        if (!isBatteryOptimizationLikelyActive() &&
            isBatteryOptimizationUserConfirmedMaxAvailable()
        ) {
            setBatteryOptimizationUserConfirmedMaxAvailable(false)
        }

        val wifiPrefs = getSharedPreferences("switchly_wifi_cache", MODE_PRIVATE)
        val needsLocationHint = wifiPrefs.getBoolean("wifi_needs_location_hint", false)
        if (needsLocationHint) {
            Snackbar.make(
                rootContentView() ?: findViewById(android.R.id.content),
                getString(R.string.schedules_wifi_location_hint),
                Snackbar.LENGTH_LONG
            ).applySwitchlyStyle().show()

            wifiPrefs.edit {
                putBoolean("wifi_needs_location_hint", false)
            }
        }
    }

    private fun showNewScheduleTypeDialog() {
        if (!canEditSchedules()) {
            return
        }

        val isPremium = PremiumManager.isPremium(this)
        val nfcLockOn = isNfcLockActiveForSchedules()

        data class TypeItem(
            val label: String,
            val description: String,
            val iconRes: Int,
            val mode: NewScheduleMode
        )

        val items = buildList {
            add(
                TypeItem(
                    getString(R.string.schedules_type_time),
                    getString(R.string.schedules_type_time_desc),
                    R.drawable.alarm_24,
                    NewScheduleMode.TIME
                )
            )
            if (isPremium && !nfcLockOn) {
                add(
                    TypeItem(
                        getString(R.string.schedules_type_wifi),
                        getString(R.string.schedules_type_wifi_desc),
                        R.drawable.wifi_24,
                        NewScheduleMode.WIFI
                    )
                )
                add(
                    TypeItem(
                        getString(R.string.schedules_type_bt),
                        getString(R.string.schedules_type_bt_desc),
                        R.drawable.bluetooth_24,
                        NewScheduleMode.BT
                    )
                )
                add(
                    TypeItem(
                        getString(R.string.schedules_type_location),
                        getString(R.string.schedules_type_location_desc),
                        R.drawable.location_on_24,
                        NewScheduleMode.LOCATION
                    )
                )
            }
        }

        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density + 0.5f).toInt()
        val accent = AccentColor.getAccentColorInt(this)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        if (nfcLockOn) {
            container.addView(
                TextView(this).apply {
                    text = getString(R.string.schedules_nfc_lock_add_dialog_hint)
                    textSize = 13f
                    setTextColor(
                        MaterialColors.getColor(
                            this@apply,
                            android.R.attr.textColorSecondary
                        )
                    )
                    setPadding(0, 0, 0, dp(12))
                }
            )
        }

        val grid = GridLayout(this).apply {
            columnCount = 2
            useDefaultMargins = false
        }
        container.addView(
            grid,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        var typeDialog: AlertDialog? = null
        fun openMode(mode: NewScheduleMode) {
            typeDialog?.dismiss()
            when (mode) {
                NewScheduleMode.TIME -> showScheduleDialog(null, NewScheduleMode.TIME)
                NewScheduleMode.WIFI -> showScheduleDialog(null, NewScheduleMode.WIFI)
                NewScheduleMode.BT -> showScheduleDialog(null, NewScheduleMode.BT)
                NewScheduleMode.LOCATION -> showScheduleDialog(null, NewScheduleMode.LOCATION)
            }
        }

        val typeCards = mutableListOf<MaterialCardView>()
        items.forEachIndexed { index, item ->
            val row = index / 2
            val col = index % 2
            // A lone trailing card spans both columns.
            val lastRowLone = index == items.lastIndex && items.size % 2 == 1
            val colSpec = if (lastRowLone) GridLayout.spec(col, 2, 1f) else GridLayout.spec(col, 1f)
            val card = MaterialCardView(this).apply {
                cardElevation = 0f
                radius = dp(16).toFloat()
                strokeWidth = 0
                setCardBackgroundColor(
                    ContextCompat.getColor(this@SchedulesActivity, R.color.foqos_surface_variant)
                )
                isClickable = true
                isFocusable = true
                // Resolve selectableItemBackground against the activity theme.
                val ripple = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
                foreground = ContextCompat.getDrawable(this@SchedulesActivity, ripple.resourceId)
                setOnClickListener { openMode(item.mode) }
            }
            val cardInner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setPadding(dp(12), dp(16), dp(12), dp(16))
            }
            cardInner.addView(
                ImageView(this).apply {
                    setImageResource(item.iconRes)
                    imageTintList = ColorStateList.valueOf(accent)
                    layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                        gravity = android.view.Gravity.CENTER_HORIZONTAL
                    }
                }
            )
            cardInner.addView(
                TextView(this).apply {
                    text = item.label
                    textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = android.view.Gravity.CENTER
                    setTextColor(MaterialColors.getColor(this@apply, com.google.android.material.R.attr.colorOnSurface))
                    setPadding(0, dp(10), 0, 0)
                }
            )
            cardInner.addView(
                TextView(this).apply {
                    text = item.description
                    textSize = 12f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(
                        MaterialColors.getColor(
                            this@apply,
                            android.R.attr.textColorSecondary
                        )
                    )
                    setPadding(0, dp(2), 0, 0)
                }
            )
            card.addView(
                cardInner,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            val margin = dp(3)
            val params = GridLayout.LayoutParams(
                GridLayout.spec(row),
                colSpec
            ).apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                setMargins(margin, margin, margin, margin)
            }
            grid.addView(card, params)
            typeCards.add(card)
        }
        // Equalize all card heights so every box is the same size.
        grid.post {
            if (typeCards.isEmpty()) return@post
            val maxH = typeCards.maxOf { it.measuredHeight }
            if (maxH <= 0) return@post
            typeCards.forEach { c ->
                val lp = c.layoutParams as GridLayout.LayoutParams
                if (lp.height != maxH) {
                    lp.height = maxH
                    c.layoutParams = lp
                }
            }
        }

        typeDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.schedules_choose_type)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .create()
            .also { it.show() }
        typeDialog?.styleSwitchlyDialogButtons()
    }

    private fun showScheduleActionInfoDialog() {
        val bodyView = TextView(this).apply {
            text = buildScheduleActionInfoBody()
            textSize = 14f
            setLineSpacing(0f, 1.18f)
        }

        val scroll = ScrollView(this).apply {
            val padH = (20 * resources.displayMetrics.density).toInt()
            val padV = (8 * resources.displayMetrics.density).toInt()
            setPadding(padH, padV, padH, padV)
            addView(
                bodyView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            )
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.schedules_action_info_title)
            .setView(scroll)
            .setPositiveButton(R.string.ok, null)
            .showAccented()
    }

    private fun showEnabledScheduleOverlapWarning(scheduleId: Int, schedules: List<ScheduleStore.Schedule>) {
        val overlap = ScheduleInsights.detectOverlaps(schedules).firstOrNull {
            it.first.id == scheduleId || it.second.id == scheduleId
        } ?: return
        val other = if (overlap.first.id == scheduleId) overlap.second else overlap.first
        AlertDialog.Builder(this)
            .setTitle(R.string.schedules_overlap_warning_title)
            .setMessage(
                getString(
                    R.string.schedules_overlap_enabled_message_fmt,
                    ScheduleInsights.scheduleDisplayName(other)
                )
            )
            .setPositiveButton(R.string.ok, null)
            .showAccented()
    }

    private fun buildScheduleActionInfoBody(): CharSequence {
        val sb = SpannableStringBuilder()

        fun addItem(title: String, desc: String) {
            val titleStart = sb.length
            sb.append(title)
            sb.setSpan(
                StyleSpan(Typeface.BOLD),
                titleStart,
                titleStart + title.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            sb.append('\n')
            sb.append(desc.trim()).append("\n\n")
        }

        addItem(
            getString(R.string.schedules_action_enable),
            getString(R.string.schedules_action_info_enable_body)
        )
        addItem(
            getString(R.string.schedules_action_disable),
            getString(R.string.schedules_action_info_disable_body)
        )
        addItem(
            getString(R.string.schedules_action_toggle),
            getString(R.string.schedules_action_info_toggle_body)
        )
        addItem(
            getString(R.string.schedules_action_enable_disable),
            getString(R.string.schedules_action_info_enable_disable_body)
        )
        addItem(
            getString(R.string.schedules_action_disable_enable),
            getString(R.string.schedules_action_info_disable_enable_body)
        )

        sb.append(getString(R.string.schedules_action_info_tip))
        sb.append("\n\n")
        sb.append(getString(R.string.schedules_priority_info_body))
        return sb
    }

    private fun tintPickButton(button: MaterialButton) {
        val accent = AccentColor.getAccentColorInt(this)
        button.strokeColor = ColorStateList.valueOf(accent)
        button.setTextColor(accent)
        button.iconTint = ColorStateList.valueOf(accent)
    }

    private fun tintSwitchCompat(switch: SwitchCompat) {
        val accent = AccentColor.getAccentColorInt(this)
        val thumbOff = Color.WHITE
        val thumbDisabled = Color.LTGRAY
        val trackOn = ColorUtils.setAlphaComponent(accent, 0x88)
        val trackOff = ColorUtils.setAlphaComponent(Color.DKGRAY, 0x44)
        val trackOffDisabled = ColorUtils.setAlphaComponent(Color.GRAY, 0x33)

        switch.thumbTintList = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(thumbDisabled, accent, thumbOff)
        )

        switch.trackTintList = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(trackOffDisabled, trackOn, trackOff)
        )
    }

    private fun refreshList() {
        val list = ScheduleStore.getAll(this).filter { matchesTargetProfile(it) }
        val sorted = list.sortedWith(scheduleDisplayComparator())
        val readOnlyNow = !canEditSchedules()
        val readOnlyChanged = isScheduleUiReadOnly != readOnlyNow
        isScheduleUiReadOnly = readOnlyNow

        emptyState.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
        adapter.submitList(sorted) {
            if (readOnlyChanged) {
                adapter.notifyItemRangeChanged(0, adapter.itemCount)
            }
        }

        val ids = list.map { it.id }.toSet()
        selectedScheduleIds.retainAll(ids)
        if (isSelectionMode && (selectedScheduleIds.isEmpty() || isScheduleUiReadOnly)) {
            exitSelectionMode()
        } else {
            updateMenuState()
        }
        updateScheduleHealthBanner()
    }

    private fun scheduleDisplayComparator(): Comparator<ScheduleStore.Schedule> {
        return compareBy<ScheduleStore.Schedule>(
            { it.startMinutes.coerceAtLeast(0) },
            { if (it.type == ScheduleStore.Type.ONE_TIME) 0 else 1 },
            { if (it.type == ScheduleStore.Type.ONE_TIME) it.startDate else Int.MAX_VALUE },
            { if (it.type == ScheduleStore.Type.WEEKLY) weeklySortKey(it.daysMask) else Int.MAX_VALUE },
            { it.title.lowercase() },
            { it.id }
        )
    }

    private fun weeklySortKey(daysMask: Int): Int {
        val order = listOf(
            Days.MON,
            Days.TUE,
            Days.WED,
            Days.THU,
            Days.FRI,
            Days.SAT,
            Days.SUN,
        )
        return order.indexOfFirst { daysMask and it != 0 }
            .takeIf { it >= 0 } ?: Int.MAX_VALUE
    }

    private fun canScheduleExactAlarms(): Boolean {
        return ExactAlarmPermissionSync.canScheduleExactAlarms(this)
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }
        runCatching {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = "package:$packageName".toUri()
            })
        }
    }

    private fun isBatteryOptimizationLikelyActive(): Boolean {
        return BatteryOptimizationCompat.isLikelyStillRestricted(this)
    }

    private fun isBatteryOptimizationUserConfirmedMaxAvailable(): Boolean {
        return BatteryOptimizationCompat.isUserConfirmedMaxAvailable(this)
    }

    private fun setBatteryOptimizationUserConfirmedMaxAvailable(value: Boolean) {
        getSharedPreferences(PREFS_SCHEDULE_HEALTH, MODE_PRIVATE).edit {
            putBoolean(KEY_BATTERY_OPTIMIZATION_CONFIRMED_MAX_AVAILABLE, value)
        }
    }

    private fun updateScheduleHealthBanner() {
        val schedules = ScheduleStore.getAll(this).filter { matchesTargetProfile(it) }
        val enabledSchedules = schedules.filter { it.enabled }

        if (!isScheduleAutomationAllowed()) {
            val modeLabel = currentAutomationModeLabel()
            cardScheduleHealth.isVisible = true
            btnStatusInfo.isVisible = false
            tvStatusTitle.text = getString(R.string.schedules_disabled_title)
            tvStatusBody.isVisible = true
            tvStatusBody.text = getString(R.string.schedules_disabled_body)
            tvStatusFooter.isVisible = true
            tvStatusFooter.text = if (AutomationModeStore.getMode(this) == AutomationModeStore.Mode.MIXED) {
                getString(R.string.schedules_disabled_footer_mixed_off, modeLabel)
            } else {
                getString(R.string.schedules_disabled_footer_mode, modeLabel)
            }
            btnStatusAction.isVisible = true
            btnStatusAction.setText(R.string.schedules_disabled_action_open_controls)
            btnStatusAction.setOnClickListener { openProtectionControls() }
            rowStatusAction.isVisible = true
            dividerStatus.isVisible = true
            return
        }

        btnStatusInfo.isVisible = true

        if (enabledSchedules.isEmpty()) {
            cardScheduleHealth.isVisible = false
            return
        }

        val nfcLockActiveForSchedules = isNfcLockActiveForSchedules()
        val nfcConflict = nfcLockActiveForSchedules && enabledSchedules.any { it.action in nfcLockedActions }

        val blockedAt = ScheduleRuntimeStore.getLastDisableBlockedByNfcMs(this)
        val nfcBlockedRecently = nfcLockActiveForSchedules && blockedAt > 0L && (System.currentTimeMillis() - blockedAt) < 24L * 60L * 60L * 1000L

        // Missing trigger permissions are surfaced per schedule through its info action and in Setup Health.
        // Avoid a second large warning card at the top of this screen.
        val hasAnyIssue = nfcConflict || nfcBlockedRecently
        val overlaps = ScheduleInsights.detectOverlaps(enabledSchedules)
        val activeRangeSummary = ScheduleInsights.activeRangeSummary(this, enabledSchedules)
        val insightBody = buildScheduleInsightBody(overlaps, activeRangeSummary)

        if (!hasAnyIssue && insightBody.isBlank()) {
            cardScheduleHealth.isVisible = false
            return
        }

        val lastExecMs = ScheduleRuntimeStore.getLastExecutionMs(this)
        val lastExecText = if (lastExecMs > 0L) {
            runCatching {
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(lastExecMs))
            }.getOrDefault(DateFormat.getDateTimeInstance().format(Date(lastExecMs)))
        } else {
            getString(R.string.schedules_health_last_exec_never)
        }

        cardScheduleHealth.isVisible = true
        tvStatusTitle.text = when {
            overlaps.isNotEmpty() -> getString(R.string.schedules_insights_overlap_title)
            hasAnyIssue -> getString(R.string.schedules_health_generic_issue)
            else -> getString(R.string.schedules_insights_title)
        }
        tvStatusBody.isVisible = insightBody.isNotBlank()
        tvStatusBody.text = insightBody
        tvStatusFooter.isVisible = true
        tvStatusFooter.text = getString(R.string.schedules_health_last_exec_compact, lastExecText)

        when {
            nfcConflict || nfcBlockedRecently -> {
                btnStatusAction.isVisible = true
                btnStatusAction.setText(R.string.schedules_health_action_toggle_options)
                btnStatusAction.setOnClickListener {
                    startActivity(Intent(this, ToggleOptionsActivity::class.java))
                }
            }
            else -> {
                btnStatusAction.isVisible = false
                btnStatusAction.setOnClickListener(null)
            }
        }

        rowStatusAction.isVisible = btnStatusAction.isVisible
        dividerStatus.isVisible = rowStatusAction.isVisible
    }

    private fun buildScheduleInsightBody(
        overlaps: List<ScheduleInsights.Overlap>,
        activeRangeSummary: String?
    ): String {
        val parts = mutableListOf<String>()
        val firstOverlap = overlaps.firstOrNull()
        if (firstOverlap != null) {
            parts += getString(
                R.string.schedules_insights_overlap_body_fmt,
                ScheduleInsights.scheduleDisplayName(firstOverlap.second)
            )
        }
        if (!activeRangeSummary.isNullOrBlank()) {
            parts += activeRangeSummary
        }
        return parts.joinToString(separator = "\n")
    }

    private fun showScheduleHealthInfoDialog() {
        if (!isScheduleAutomationAllowed()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.schedules_disabled_title)
                .setMessage(R.string.schedules_disabled_body)
                .setPositiveButton(R.string.ok, null)
                .setNeutralButton(R.string.schedules_disabled_action_open_controls) { _, _ ->
                    openProtectionControls()
                }
                .showAccented()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.schedules_health_info_title)
            .setMessage(R.string.schedules_health_info_body)
            .setPositiveButton(R.string.ok, null)
            .setNeutralButton(R.string.schedules_health_action_permissions) { _, _ ->
                openPermissionsOverview()
            }
            .showAccented()
    }

    private fun wifiSsidPermissionName(): String {
        return if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
    }

    private fun hasNearbyWifiPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 33) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.NEARBY_WIFI_DEVICES
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasLocationSchedulePermission(): Boolean {
        return hasFineLocationPermission() && hasBackgroundLocationPermission()
    }

    private fun hasWifiSsidPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            hasNearbyWifiPermission() || hasFineLocationPermission()
        } else {
            hasFineLocationPermission()
        }
    }

    private data class ScheduleTestSnapshot(
        val conditionMet: Boolean,
        val detected: String,
        val missingPermission: String? = null,
    )

    private fun showScheduleTest(schedule: ScheduleStore.Schedule) {
        val missingPermission = when {
            !BlockingRuntime.isAccessibilityActive(this) && !BlockingRuntime.isAccessibilityEnabledInSettings(this) ->
                getString(R.string.schedules_test_permission_accessibility)
            !schedule.wifiSsid.isNullOrBlank() && !hasWifiSsidPermission() ->
                getString(R.string.schedules_test_permission_wifi)
            (!schedule.btDeviceName.isNullOrBlank() || !schedule.btDeviceAddress.isNullOrBlank()) && !hasBluetoothConnectPermission() ->
                getString(R.string.schedules_test_permission_bluetooth)
            schedule.isLocationSchedule() && !hasLocationSchedulePermission() ->
                getString(R.string.schedules_test_permission_location)
            else -> null
        }

        if (schedule.isLocationSchedule() && missingPermission == null) {
            val fineGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            val coarseGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            if (!fineGranted && !coarseGranted) {
                showScheduleTestDialog(
                    schedule,
                    evaluateScheduleNow(
                        schedule,
                        null,
                        getString(R.string.schedules_test_permission_location),
                    ),
                )
                return
            }
            requestCurrentLocationForScheduleTest(schedule)
            return
        }

        showScheduleTestDialog(schedule, evaluateScheduleNow(schedule, null, missingPermission))
    }

    private fun requestCurrentLocationForScheduleTest(schedule: ScheduleStore.Schedule) {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            showScheduleTestDialog(
                schedule,
                evaluateScheduleNow(
                    schedule,
                    null,
                    getString(R.string.schedules_test_permission_location),
                ),
            )
            return
        }

        val client = LocationServices.getFusedLocationProviderClient(this)
        val token = CancellationTokenSource()
        try {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token)
                .addOnSuccessListener { location ->
                    showScheduleTestDialog(schedule, evaluateScheduleNow(schedule, location, null))
                }
                .addOnFailureListener {
                    showScheduleTestDialog(schedule, evaluateScheduleNow(schedule, null, null))
                }
        } catch (_: SecurityException) {
            showScheduleTestDialog(
                schedule,
                evaluateScheduleNow(
                    schedule,
                    null,
                    getString(R.string.schedules_test_permission_location),
                ),
            )
        } catch (_: Throwable) {
            showScheduleTestDialog(schedule, evaluateScheduleNow(schedule, null, null))
        }
    }

    private fun evaluateScheduleNow(
        schedule: ScheduleStore.Schedule,
        location: Location?,
        missingPermissionOverride: String?,
    ): ScheduleTestSnapshot {
        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val todayBit = Days.fromCalendarDay(now.get(Calendar.DAY_OF_WEEK))
        val ymd = now.get(Calendar.YEAR) * 10000 + (now.get(Calendar.MONTH) + 1) * 100 + now.get(Calendar.DAY_OF_MONTH)
        val dayMatches = when (schedule.type) {
            ScheduleStore.Type.WEEKLY -> schedule.daysMask and todayBit != 0
            ScheduleStore.Type.ONE_TIME -> schedule.startDate > 0 && schedule.endDate > 0 && ymd in schedule.startDate..schedule.endDate
        }
        val allDay = schedule.startMinutes == 0 && schedule.endMinutes >= 1439
        val rangeAction = schedule.action == ScheduleStore.Action.ENABLE_AND_DISABLE ||
            schedule.action == ScheduleStore.Action.DISABLE_AND_ENABLE
        val hasConnectionOrLocation = !schedule.wifiSsid.isNullOrBlank() ||
            !schedule.btDeviceName.isNullOrBlank() || !schedule.btDeviceAddress.isNullOrBlank() ||
            schedule.isLocationSchedule()
        val timeMatches = when {
            allDay -> true
            rangeAction || hasConnectionOrLocation -> inScheduleTimeRange(nowMinutes, schedule.startMinutes, schedule.endMinutes)
            else -> nowMinutes == schedule.startMinutes
        }

        val missingPermission = missingPermissionOverride ?: when {
            !BlockingRuntime.isAccessibilityActive(this) && !BlockingRuntime.isAccessibilityEnabledInSettings(this) ->
                getString(R.string.schedules_test_permission_accessibility)
            !schedule.wifiSsid.isNullOrBlank() && !hasWifiSsidPermission() ->
                getString(R.string.schedules_test_permission_wifi)
            (!schedule.btDeviceName.isNullOrBlank() || !schedule.btDeviceAddress.isNullOrBlank()) && !hasBluetoothConnectPermission() ->
                getString(R.string.schedules_test_permission_bluetooth)
            schedule.isLocationSchedule() && !hasLocationSchedulePermission() ->
                getString(R.string.schedules_test_permission_location)
            else -> null
        }

        if (!schedule.wifiSsid.isNullOrBlank()) {
            val ssid = currentWifiSsidForTest()
            val matches = !ssid.isNullOrBlank() && ssid.equals(schedule.wifiSsid, ignoreCase = true)
            return ScheduleTestSnapshot(
                conditionMet = dayMatches && timeMatches && matches && missingPermission == null,
                detected = if (ssid.isNullOrBlank()) getString(R.string.schedules_test_status_wifi_none)
                else getString(R.string.schedules_test_status_wifi_fmt, ssid),
                missingPermission = missingPermission,
            )
        }

        if (!schedule.btDeviceName.isNullOrBlank() || !schedule.btDeviceAddress.isNullOrBlank()) {
            val connected = connectedBluetoothForTest()
            val match = connected.firstOrNull { (name, address) ->
                val addressOk = schedule.btDeviceAddress.isNullOrBlank() || schedule.btDeviceAddress.equals(address, ignoreCase = true)
                val nameOk = schedule.btDeviceName.isNullOrBlank() || schedule.btDeviceName.equals(name, ignoreCase = true)
                addressOk && nameOk
            }
            return ScheduleTestSnapshot(
                conditionMet = dayMatches && timeMatches && match != null && missingPermission == null,
                detected = match?.let { (name, address) ->
                    getString(R.string.schedules_test_status_bt_fmt, name ?: address ?: "-")
                } ?: getString(R.string.schedules_test_status_bt_none),
                missingPermission = missingPermission,
            )
        }

        if (schedule.isLocationSchedule()) {
            val lat = schedule.locationLat
            val lng = schedule.locationLng
            if (location == null || lat == null || lng == null) {
                return ScheduleTestSnapshot(
                    conditionMet = false,
                    detected = getString(R.string.schedules_test_status_location_unknown),
                    missingPermission = missingPermission,
                )
            }
            val result = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, lat, lng, result)
            val meters = result[0].coerceAtLeast(0f).toInt()
            val inside = meters <= schedule.locationRadiusMeters
            val triggerMatches = when (schedule.locationTrigger) {
                ScheduleStore.LocationTrigger.ENTER -> inside
                ScheduleStore.LocationTrigger.EXIT -> !inside
                ScheduleStore.LocationTrigger.ENTER_EXIT, null -> true
            }
            return ScheduleTestSnapshot(
                conditionMet = dayMatches && timeMatches && triggerMatches && missingPermission == null,
                detected = getString(
                    if (inside) R.string.schedules_test_status_location_inside_fmt else R.string.schedules_test_status_location_outside_fmt,
                    meters,
                ),
                missingPermission = missingPermission,
            )
        }

        return ScheduleTestSnapshot(
            conditionMet = dayMatches && timeMatches && missingPermission == null,
            detected = getString(R.string.schedules_test_status_time_fmt, TimeFormatPrefs.formatMinutesOfDay(this, nowMinutes)),
            missingPermission = missingPermission,
        )
    }

    private fun showScheduleTestDialog(schedule: ScheduleStore.Schedule, snapshot: ScheduleTestSnapshot) {
        val actionLabel = scheduleActionLabel(schedule.action)
        val profileLabel = schedule.profile.ifBlank { ProfileStore.getCurrent(this).orEmpty() }
        val currentBaseEnabled = SwitchModeStore.isBaseEnabled(this)
        val targetEnabled = when (schedule.action) {
            ScheduleStore.Action.ENABLE, ScheduleStore.Action.ENABLE_AND_DISABLE, ScheduleStore.Action.DISCONNECT_ENABLE -> true
            ScheduleStore.Action.DISABLE, ScheduleStore.Action.DISABLE_AND_ENABLE, ScheduleStore.Action.DISCONNECT_DISABLE -> false
            ScheduleStore.Action.TOGGLE -> !currentBaseEnabled
        }
        val currentEffectiveEnabled = SwitchModeStore.isEnabled(this)
        val temporaryOverrideActive = SwitchModeStore.hasActiveTemporaryOverride(this)
        val currentProfile = ProfileStore.getCurrent(this)
        val profileWouldChange = currentProfile != profileLabel && profileLabel.isNotBlank()
        val result = when {
            !schedule.enabled -> getString(R.string.schedules_test_result_schedule_disabled)
            !isScheduleAutomationAllowed() -> getString(R.string.schedules_test_result_control_mode)
            EmergencyBypassStore.isActive(this) -> getString(R.string.schedules_test_result_emergency)
            snapshot.missingPermission != null -> getString(R.string.schedules_test_result_permission_missing)
            !snapshot.conditionMet -> getString(R.string.schedules_test_result_waiting)
            !targetEnabled && currentEffectiveEnabled && isNfcLockActiveForSchedules() -> getString(R.string.schedules_test_result_nfc_lock)
            temporaryOverrideActive && (targetEnabled != currentBaseEnabled || profileWouldChange) ->
                getString(R.string.schedules_test_result_temp_override)
            profileWouldChange -> getString(R.string.schedules_test_result_profile_change)
            targetEnabled == currentBaseEnabled -> getString(
                if (currentBaseEnabled) R.string.schedules_test_result_already_enabled else R.string.schedules_test_result_already_disabled
            )
            else -> getString(R.string.schedules_test_result_would_change)
        }

        val actionValue = listOf(actionLabel, profileLabel)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        showSwitchlyInfoDialog(
            title = getString(R.string.schedules_test_title),
            rows = listOf(
                SwitchlyInfoRow(
                    getString(R.string.schedules_test_field_schedule),
                    getString(if (schedule.enabled) R.string.schedules_test_value_enabled else R.string.schedules_test_value_disabled),
                ),
                SwitchlyInfoRow(
                    getString(R.string.schedules_test_field_condition),
                    getString(if (snapshot.conditionMet) R.string.schedules_test_value_condition_met else R.string.schedules_test_value_condition_not_met),
                ),
                SwitchlyInfoRow(
                    getString(R.string.schedules_test_field_permissions),
                    snapshot.missingPermission?.let {
                        getString(R.string.schedules_test_value_permission_missing_fmt, it)
                    } ?: getString(R.string.schedules_test_value_permissions_ready),
                ),
                SwitchlyInfoRow(
                    getString(R.string.schedules_test_field_detected),
                    snapshot.detected,
                ),
                SwitchlyInfoRow(
                    getString(R.string.schedules_test_field_action),
                    actionValue,
                ),
                SwitchlyInfoRow(
                    getString(R.string.schedules_test_field_result),
                    result,
                    emphasized = true,
                ),
            ),
        )
    }

    private fun scheduleActionLabel(action: ScheduleStore.Action): String = when (action) {
        ScheduleStore.Action.ENABLE -> getString(R.string.schedules_action_enable)
        ScheduleStore.Action.DISABLE -> getString(R.string.schedules_action_disable)
        ScheduleStore.Action.TOGGLE -> getString(R.string.schedules_action_toggle)
        ScheduleStore.Action.ENABLE_AND_DISABLE -> getString(R.string.schedules_action_enable_disable)
        ScheduleStore.Action.DISABLE_AND_ENABLE -> getString(R.string.schedules_action_disable_enable)
        ScheduleStore.Action.DISCONNECT_ENABLE -> getString(R.string.schedules_action_disconnect_enable)
        ScheduleStore.Action.DISCONNECT_DISABLE -> getString(R.string.schedules_action_disconnect_disable)
    }

    private fun inScheduleTimeRange(nowMinutes: Int, startMinutes: Int, endMinutes: Int): Boolean {
        return if (startMinutes <= endMinutes) {
            nowMinutes in startMinutes..endMinutes
        } else {
            nowMinutes >= startMinutes || nowMinutes <= endMinutes
        }
    }

    private fun currentWifiSsidForTest(): String? {
        if (!hasWifiSsidPermission()) return null

        val raw = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                currentWifiSsidModern()
            } else {
                // API 27/28 have no NetworkCapabilities transportInfo/WifiInfo path yet.
                currentWifiSsidLegacyApi27To28()
            }
        }.getOrNull()?.trim().orEmpty()

        val clean = raw.removePrefix("\"").removeSuffix("\"")
        return clean.takeIf {
            it.isNotBlank() &&
                !it.equals("<unknown ssid>", ignoreCase = true) &&
                !it.equals("unknown ssid", ignoreCase = true)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun currentWifiSsidModern(): String? {
        // WifiTriggerService maintains this cache from a TRANSPORT_WIFI NetworkCallback, so it still works when the default network is a VPN or cellular connection.
        val cached = getSharedPreferences("switchly_wifi_cache", MODE_PRIVATE)
            .getString("last_ssid", null)
            ?.trim()
            ?.removePrefix("\"")
            ?.removeSuffix("\"")
            ?.takeIf {
                it.isNotBlank() &&
                    !it.equals("<unknown ssid>", ignoreCase = true) &&
                    !it.equals("unknown ssid", ignoreCase = true)
            }
        if (cached != null) return cached

        // Fallback for the short window before the Wi-Fi callback cache is populated.
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return null
        val network = connectivity.activeNetwork ?: return null
        val caps = connectivity.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        return (caps.transportInfo as? WifiInfo)?.ssid
    }

    /**
     * Compatibility path for Android 8.1/9 only (API 27/28).
     * getConnectionInfo() was the platform API on those releases;
     * reflection keeps the deprecated symbol out of the modern source path while preserving support for Switchly's minSdk.
     */
    private fun currentWifiSsidLegacyApi27To28(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return null
        val wifiManager = getSystemService(WIFI_SERVICE) as? WifiManager ?: return null
        return runCatching {
            wifiManager.javaClass
                .getMethod("getConnectionInfo")
                .invoke(wifiManager) as? WifiInfo
        }.getOrNull()?.ssid
    }

    private fun connectedBluetoothForTest(): List<Pair<String?, String?>> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return emptyList()
            }
        } else if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
        val manager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager ?: return emptyList()
        val adapter = manager.adapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        val out = linkedSetOf<Pair<String?, String?>>()
        val connectedMethod = runCatching {
            Class.forName("android.bluetooth.BluetoothDevice").getMethod("isConnected")
        }.getOrNull()
        runCatching { adapter.bondedDevices }.getOrDefault(emptySet()).forEach { device ->
            val connected = runCatching { (connectedMethod?.invoke(device) as? Boolean) ?: false }.getOrDefault(false)
            if (connected) {
                out += runCatching { device.name }.getOrNull() to runCatching { device.address }.getOrNull()
            }
        }
        runCatching { manager.getConnectedDevices(BluetoothProfile.GATT) }.getOrDefault(emptyList()).forEach { device ->
            out += runCatching { device.name }.getOrNull() to runCatching { device.address }.getOrNull()
        }
        return out.toList()
    }

    private fun reapplySchedulesNow() {
        ScheduleRuntimeStore.resetActiveScheduleState(this)
        sendBroadcast(
            Intent(this, ScheduleReceiver::class.java).apply {
                action = ScheduleReceiver.ACTION_TICK
                putExtra("alarm_reason", "schedule_edit")
            }
        )
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openAppSettings() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            )
        }
    }

    private fun openPermissionsOverview() {
        runCatching {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }.onFailure {
            openAppSettings()
        }
    }

    private fun showWhyLocationDialogForWifi() {
        AlertDialog.Builder(this)
            .setTitle(R.string.perm_location_title)
            .setMessage(R.string.perm_location_why_wifi_ssid)
            .setPositiveButton(R.string.ok) { _, _ ->
                requestLocationPermission.launch(wifiSsidPermissionName())
            }
            .setNeutralButton(R.string.schedules_health_action_permissions) { _, _ ->
                openPermissionsOverview()
            }
            .setNegativeButton(R.string.cancel, null)
            .showAccented()
    }

    private fun showWhyLocationDialogForGeofence(onGranted: (() -> Unit)? = null) {
        AlertDialog.Builder(this)
            .setTitle(R.string.perm_location_title)
            .setMessage(R.string.perm_location_why_geofence)
            .setPositiveButton(R.string.ok) { _, _ ->
                pendingAfterFineLocationGrant = onGranted
                requestFineLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            .setNeutralButton(R.string.schedules_health_action_permissions) { _, _ ->
                openPermissionsOverview()
            }
            .setNegativeButton(R.string.cancel, null)
            .showAccented()
    }

    private fun showWhyBackgroundLocationDialogForGeofence() {
        AlertDialog.Builder(this)
            .setTitle(R.string.perm_location_background_title)
            .setMessage(R.string.perm_location_background_why_geofence)
            .setPositiveButton(R.string.schedules_health_action_permissions) { _, _ ->
                openPermissionsOverview()
            }
            .setNegativeButton(R.string.cancel, null)
            .showAccented()
    }

    private fun showWhyBluetoothDialogForSchedules() {
        AlertDialog.Builder(this)
            .setTitle(R.string.perm_bt_title)
            .setMessage(R.string.perm_bt_why_needed)
            .setPositiveButton(R.string.ok) { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    requestBluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }
            .setNeutralButton(R.string.schedules_health_action_permissions) { _, _ ->
                openPermissionsOverview()
            }
            .setNegativeButton(R.string.cancel, null)
            .showAccented()
    }

    private fun showSnack(msgRes: Int) {
        val root = findViewById<View?>(android.R.id.content)
        if (root != null) Snackbar.make(root, msgRes, Snackbar.LENGTH_LONG).applySwitchlyStyle().show()
        else Toast.makeText(this, getString(msgRes), Toast.LENGTH_LONG).show()
    }

    private fun showScheduleMessage(msgRes: Int) {
        showSnack(msgRes)
    }

    private fun showScheduleDialog(
        existing: ScheduleStore.Schedule?,
        preselectedMode: NewScheduleMode? = null
    ) {
        if (!canEditSchedules()) {
            return
        }
        val view = LayoutInflater.from(this)
            .inflate(R.layout.dialog_schedule_add, FrameLayout(this), false)

        val isCustomAccent = CustomAccentApplier.isCustomAccentEnabled(this)
        if (isCustomAccent) {
            CustomAccentApplier.applyToView(view, this)
        }

        val accent = AccentColor.getAccentColorInt(this)
        val dp1 = (1 * resources.displayMetrics.density).toInt()

        // Title & Note
        val layoutTitle = view.findViewById<TextInputLayout>(R.id.layoutTitle)
        val editTitle = view.findViewById<EditText>(R.id.editTitle)
        val layoutProfile = view.findViewById<TextInputLayout>(R.id.layoutProfile)
        val spinnerProfile = view.findViewById<android.widget.AutoCompleteTextView>(R.id.spinnerProfile)
        val layoutNote = view.findViewById<TextInputLayout>(R.id.layoutNote)
        val editNote = view.findViewById<EditText>(R.id.editNote)

        // Wi-Fi
        val groupWifi = view.findViewById<View>(R.id.groupWifi)
        val layoutWifiSsid = view.findViewById<TextInputLayout>(R.id.layoutWifiSsid)
        val inputWifiSsid = view.findViewById<EditText>(R.id.inputWifiSsid)
        val btnScanWifi = view.findViewById<MaterialButton>(R.id.btnScanWifi)

        // Bluetooth
        val groupBt = view.findViewById<View>(R.id.groupBt)
        val layoutBtName = view.findViewById<TextInputLayout>(R.id.layoutBtName)
        val inputBtName = view.findViewById<EditText>(R.id.inputBtName)
        val btnUseConnectedBt = view.findViewById<MaterialButton>(R.id.btnUseConnectedBt)
        val btnPickPairedBt = view.findViewById<MaterialButton>(R.id.btnPickPairedBt)

        // Location (single box: type/search/pick/current, no extra popup)
        val groupLocation = view.findViewById<View>(R.id.groupLocation)
        val textLocationSummary = view.findViewById<TextView>(R.id.textLocationSummary)
        val layoutLocationQuery = view.findViewById<TextInputLayout>(R.id.layoutLocationQuery)
        val inputLocationQuery = view.findViewById<EditText>(R.id.inputLocationQuery)
        val btnLocationSearchInline = view.findViewById<MaterialButton>(R.id.btnLocationSearchInline)
        val btnUseCurrentLocation = view.findViewById<MaterialButton>(R.id.btnUseCurrentLocation)
        val btnOpenMapPicker = view.findViewById<MaterialButton>(R.id.btnOpenMapPicker)
        val progressLocationSearchInline = view.findViewById<ProgressBar>(R.id.progressLocationSearchInline)
        val tvLocationSearchStatusInline = view.findViewById<TextView>(R.id.tvLocationSearchStatusInline)
        val rvLocationResultsInline = view.findViewById<RecyclerView>(R.id.rvLocationResultsInline)
        val chipRadius100 = view.findViewById<Chip>(R.id.chipRadius100)
        val chipRadius250 = view.findViewById<Chip>(R.id.chipRadius250)
        val chipRadius500 = view.findViewById<Chip>(R.id.chipRadius500)
        val layoutLocationTrigger = view.findViewById<TextInputLayout>(R.id.layoutLocationTrigger)
        val spinnerLocationTrigger = view.findViewById<android.widget.AutoCompleteTextView>(R.id.spinnerLocationTrigger)
        val layoutLocationCooldown = view.findViewById<TextInputLayout>(R.id.layoutLocationCooldown)
        val spinnerLocationCooldown = view.findViewById<android.widget.AutoCompleteTextView>(R.id.spinnerLocationCooldown)

        // Time Mode
        val groupTimeMode = view.findViewById<View>(R.id.groupTimeMode)
        val layoutTimeMode = view.findViewById<TextInputLayout>(R.id.layoutTimeMode)
        val spinnerTimeMode = view.findViewById<android.widget.AutoCompleteTextView>(R.id.spinnerTimeMode)

        // Standard Action (Time & Location)
        val groupStandardAction = view.findViewById<View>(R.id.groupStandardAction)
        val layoutAction = view.findViewById<TextInputLayout>(R.id.layoutAction)
        val spinnerAction = view.findViewById<android.widget.AutoCompleteTextView>(R.id.spinnerAction)
        val textActionNfcHint = view.findViewById<TextView>(R.id.textActionNfcHint)

        // Attached Profile Card (when locked to profile)
        val cardAttachedProfile = view.findViewById<MaterialCardView>(R.id.cardAttachedProfile)
        val ivAttachedProfile = view.findViewById<ImageView>(R.id.ivAttachedProfile)
        val tvAttachedProfileName = view.findViewById<TextView>(R.id.tvAttachedProfileName)

        // Wi-Fi & BT Connection Behavior
        val groupConnActions = view.findViewById<View>(R.id.groupConnActions)
        val layoutConnActionConnect = view.findViewById<TextInputLayout>(R.id.layoutConnActionConnect)
        val spinnerConnActionConnect = view.findViewById<android.widget.AutoCompleteTextView>(R.id.spinnerConnActionConnect)
        val layoutConnActionDisconnect = view.findViewById<TextInputLayout>(R.id.layoutConnActionDisconnect)
        val spinnerConnActionDisconnect = view.findViewById<android.widget.AutoCompleteTextView>(R.id.spinnerConnActionDisconnect)
        val tvConnSummary = view.findViewById<TextView>(R.id.tvConnSummary)

        // Unified Timing controls
        val cardTimingContainer = view.findViewById<MaterialCardView>(R.id.cardTimingContainer)
        val row247 = view.findViewById<View>(R.id.row247)
        val iv247Icon = view.findViewById<ImageView>(R.id.iv247Icon)
        val tv247Title = view.findViewById<TextView>(R.id.tv247Title)
        val tv247Subtitle = view.findViewById<TextView>(R.id.tv247Subtitle)
        val switch247 = view.findViewById<SwitchCompat>(R.id.switch247)
        val divider247 = view.findViewById<View>(R.id.divider247)
        val rowAllDay = view.findViewById<View>(R.id.rowAllDay)
        val ivAllDayIcon = view.findViewById<ImageView>(R.id.ivAllDayIcon)
        val tvAllDayTitle = view.findViewById<TextView>(R.id.tvAllDayTitle)
        val tvAllDaySubtitle = view.findViewById<TextView>(R.id.tvAllDaySubtitle)
        val switchAllDay = view.findViewById<SwitchCompat>(R.id.switchAllDay)
        val dividerTime = view.findViewById<View>(R.id.dividerTime)
        val groupTimeRow = view.findViewById<View>(R.id.groupTimeRow)
        val cardStartTime = view.findViewById<MaterialCardView>(R.id.cardStartTime)
        val textStartTime = view.findViewById<TextView>(R.id.textStartTime)
        val ivStartTimeIcon = view.findViewById<ImageView>(R.id.ivStartTimeIcon)
        val ivTimeArrow = view.findViewById<ImageView>(R.id.ivTimeArrow)
        val cardEndTime = view.findViewById<MaterialCardView>(R.id.cardEndTime)
        val textEndTime = view.findViewById<TextView>(R.id.textEndTime)
        val ivEndTimeIcon = view.findViewById<ImageView>(R.id.ivEndTimeIcon)

        val groupWeekly = view.findViewById<View>(R.id.groupWeekly)
        val chipMon = view.findViewById<MaterialButton>(R.id.chipMon)
        val chipTue = view.findViewById<MaterialButton>(R.id.chipTue)
        val chipWed = view.findViewById<MaterialButton>(R.id.chipWed)
        val chipThu = view.findViewById<MaterialButton>(R.id.chipThu)
        val chipFri = view.findViewById<MaterialButton>(R.id.chipFri)
        val chipSat = view.findViewById<MaterialButton>(R.id.chipSat)
        val chipSun = view.findViewById<MaterialButton>(R.id.chipSun)
        val chipWeekdays = view.findViewById<MaterialButton>(R.id.chipWeekdays)
        val chipWeekend = view.findViewById<MaterialButton>(R.id.chipWeekend)
        val chipToday = view.findViewById<MaterialButton>(R.id.chipToday)

        val groupOnce = view.findViewById<View>(R.id.groupOnce)
        val cardStartDate = view.findViewById<MaterialCardView>(R.id.cardStartDate)
        val textStartDate = view.findViewById<TextView>(R.id.textStartDate)
        val ivStartDateIcon = view.findViewById<ImageView>(R.id.ivStartDateIcon)
        val cardEndDate = view.findViewById<MaterialCardView>(R.id.cardEndDate)
        val textEndDate = view.findViewById<TextView>(R.id.textEndDate)
        val ivEndDateIcon = view.findViewById<ImageView>(R.id.ivEndDateIcon)

        // Tint all icons and switches with accent
        ivAttachedProfile.imageTintList = ColorStateList.valueOf(accent)
        iv247Icon.imageTintList = ColorStateList.valueOf(accent)
        ivAllDayIcon.imageTintList = ColorStateList.valueOf(accent)
        ivStartTimeIcon.imageTintList = ColorStateList.valueOf(accent)
        ivEndTimeIcon.imageTintList = ColorStateList.valueOf(accent)
        ivStartDateIcon.imageTintList = ColorStateList.valueOf(accent)
        ivEndDateIcon.imageTintList = ColorStateList.valueOf(accent)
        tintSwitchCompat(switch247)
        tintSwitchCompat(switchAllDay)

        fun applyRadiusChipColors(chip: Chip) {
            val primary = accent
            val onPrimary = if (ColorUtils.calculateLuminance(accent) > 0.5) Color.BLACK else Color.WHITE
            val onSurface = MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnSurface)
            val bgUnchecked = MaterialColors.compositeARGBWithAlpha(onSurface, 0x14)

            chip.chipStrokeWidth = 0f
            chip.chipStrokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
            chip.chipBackgroundColor = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(primary, bgUnchecked)
            )
            chip.setTextColor(
                ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(onPrimary, onSurface)
                )
            )
        }
        listOf(chipRadius100, chipRadius250, chipRadius500).forEach(::applyRadiusChipColors)

        val dayButtons = listOf(chipMon, chipTue, chipWed, chipThu, chipFri, chipSat, chipSun)
        val surfaceColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurface, Color.LTGRAY)
        val onSurfaceColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface, Color.DKGRAY)
        val onAccent = if (ColorUtils.calculateLuminance(accent) > 0.5) Color.BLACK else Color.WHITE

        fun setDayButtonChecked(btn: MaterialButton, checked: Boolean) {
            btn.isChecked = checked
            if (checked) {
                btn.backgroundTintList = ColorStateList.valueOf(accent)
                btn.setTextColor(onAccent)
            } else {
                btn.backgroundTintList = ColorStateList.valueOf(surfaceColor)
                btn.setTextColor(onSurfaceColor)
            }
        }

        dayButtons.forEach { btn ->
            // Checkable MaterialButtons auto-toggle isChecked before onClick runs,
            // so just refresh the visuals from the already-toggled state.
            btn.setOnClickListener {
                setDayButtonChecked(btn, btn.isChecked)
            }
        }

        chipWeekdays.setOnClickListener {
            setDayButtonChecked(chipMon, true)
            setDayButtonChecked(chipTue, true)
            setDayButtonChecked(chipWed, true)
            setDayButtonChecked(chipThu, true)
            setDayButtonChecked(chipFri, true)
            setDayButtonChecked(chipSat, false)
            setDayButtonChecked(chipSun, false)
        }

        chipWeekend.setOnClickListener {
            setDayButtonChecked(chipMon, false)
            setDayButtonChecked(chipTue, false)
            setDayButtonChecked(chipWed, false)
            setDayButtonChecked(chipThu, false)
            setDayButtonChecked(chipFri, false)
            setDayButtonChecked(chipSat, true)
            setDayButtonChecked(chipSun, true)
        }

        chipToday.setOnClickListener {
            val allChecked = dayButtons.all { it.isChecked }
            dayButtons.forEach { setDayButtonChecked(it, !allChecked) }
        }

        var selectedConnConnectAction: ScheduleStore.Action? = ScheduleStore.Action.ENABLE
        var selectedConnDisconnectAction: ScheduleStore.Action? = ScheduleStore.Action.DISABLE

        val connectChoices = listOf(
            ScheduleStore.Action.ENABLE to getString(R.string.schedules_conn_action_block),
            ScheduleStore.Action.DISABLE to getString(R.string.schedules_conn_action_unblock),
            null to getString(R.string.schedules_conn_action_nothing)
        )
        val disconnectChoices = listOf(
            ScheduleStore.Action.DISABLE to getString(R.string.schedules_conn_action_unblock),
            ScheduleStore.Action.ENABLE to getString(R.string.schedules_conn_action_block),
            null to getString(R.string.schedules_conn_action_nothing)
        )

        fun updateConnSummary() {
            val summaryRes = when {
                selectedConnConnectAction == ScheduleStore.Action.ENABLE && selectedConnDisconnectAction == ScheduleStore.Action.DISABLE ->
                    R.string.schedules_conn_summary_block_unblock
                selectedConnConnectAction == ScheduleStore.Action.DISABLE && selectedConnDisconnectAction == ScheduleStore.Action.ENABLE ->
                    R.string.schedules_conn_summary_unblock_block
                selectedConnConnectAction == ScheduleStore.Action.ENABLE && selectedConnDisconnectAction == null ->
                    R.string.schedules_conn_summary_block_nothing
                selectedConnConnectAction == ScheduleStore.Action.DISABLE && selectedConnDisconnectAction == null ->
                    R.string.schedules_conn_summary_unblock_nothing
                selectedConnConnectAction == null && selectedConnDisconnectAction == ScheduleStore.Action.ENABLE ->
                    R.string.schedules_conn_summary_nothing_block
                selectedConnConnectAction == null && selectedConnDisconnectAction == ScheduleStore.Action.DISABLE ->
                    R.string.schedules_conn_summary_nothing_unblock
                selectedConnConnectAction == ScheduleStore.Action.ENABLE && selectedConnDisconnectAction == ScheduleStore.Action.ENABLE ->
                    R.string.schedules_conn_summary_block_nothing
                selectedConnConnectAction == ScheduleStore.Action.DISABLE && selectedConnDisconnectAction == ScheduleStore.Action.DISABLE ->
                    R.string.schedules_conn_summary_unblock_nothing
                else -> R.string.schedules_conn_summary_none
            }
            tvConnSummary.setText(summaryRes)
        }

        fun setupConnDropdowns() {
            val nfcLocked = isNfcLockActiveForSchedules()
            val filteredConnect = if (nfcLocked) {
                connectChoices.filter { it.first != ScheduleStore.Action.DISABLE }
            } else connectChoices

            val filteredDisconnect = if (nfcLocked) {
                disconnectChoices.filter { it.first != ScheduleStore.Action.DISABLE }
            } else disconnectChoices

            val connAdapter = SwitchlyDropdownAdapter(this@SchedulesActivity, filteredConnect.map { it.second })
            spinnerConnActionConnect.setAdapter(connAdapter)
            val currentConn = filteredConnect.firstOrNull { it.first == selectedConnConnectAction }
                ?: filteredConnect.first()
            selectedConnConnectAction = currentConn.first
            spinnerConnActionConnect.setText(currentConn.second, false)

            spinnerConnActionConnect.setOnItemClickListener { _, _, pos, _ ->
                selectedConnConnectAction = filteredConnect[pos].first
                updateConnSummary()
            }

            val disconnAdapter = SwitchlyDropdownAdapter(this@SchedulesActivity, filteredDisconnect.map { it.second })
            spinnerConnActionDisconnect.setAdapter(disconnAdapter)
            val currentDisconn = filteredDisconnect.firstOrNull { it.first == selectedConnDisconnectAction }
                ?: filteredDisconnect.first()
            selectedConnDisconnectAction = currentDisconn.first
            spinnerConnActionDisconnect.setText(currentDisconn.second, false)

            spinnerConnActionDisconnect.setOnItemClickListener { _, _, pos, _ ->
                selectedConnDisconnectAction = filteredDisconnect[pos].first
                updateConnSummary()
            }

            updateConnSummary()
        }

        val initialAction = existing?.action ?: ScheduleStore.Action.ENABLE_AND_DISABLE
        when (initialAction) {
            ScheduleStore.Action.ENABLE_AND_DISABLE -> {
                selectedConnConnectAction = ScheduleStore.Action.ENABLE
                selectedConnDisconnectAction = ScheduleStore.Action.DISABLE
            }
            ScheduleStore.Action.DISABLE_AND_ENABLE -> {
                selectedConnConnectAction = ScheduleStore.Action.DISABLE
                selectedConnDisconnectAction = ScheduleStore.Action.ENABLE
            }
            ScheduleStore.Action.ENABLE -> {
                selectedConnConnectAction = ScheduleStore.Action.ENABLE
                selectedConnDisconnectAction = null
            }
            ScheduleStore.Action.DISABLE -> {
                selectedConnConnectAction = ScheduleStore.Action.DISABLE
                selectedConnDisconnectAction = null
            }
            ScheduleStore.Action.DISCONNECT_ENABLE -> {
                selectedConnConnectAction = null
                selectedConnDisconnectAction = ScheduleStore.Action.ENABLE
            }
            ScheduleStore.Action.DISCONNECT_DISABLE -> {
                selectedConnConnectAction = null
                selectedConnDisconnectAction = ScheduleStore.Action.DISABLE
            }
            else -> {
                selectedConnConnectAction = ScheduleStore.Action.ENABLE
                selectedConnDisconnectAction = ScheduleStore.Action.DISABLE
            }
        }
        setupConnDropdowns()

        fun getConnAction(): ScheduleStore.Action? {
            val onConnect = selectedConnConnectAction
            val onDisconnect = selectedConnDisconnectAction

            return when {
                onConnect == ScheduleStore.Action.ENABLE && onDisconnect == ScheduleStore.Action.DISABLE ->
                    ScheduleStore.Action.ENABLE_AND_DISABLE
                onConnect == ScheduleStore.Action.DISABLE && onDisconnect == ScheduleStore.Action.ENABLE ->
                    ScheduleStore.Action.DISABLE_AND_ENABLE
                onConnect == ScheduleStore.Action.ENABLE && onDisconnect == null ->
                    ScheduleStore.Action.ENABLE
                onConnect == ScheduleStore.Action.DISABLE && onDisconnect == null ->
                    ScheduleStore.Action.DISABLE
                onConnect == null && onDisconnect == ScheduleStore.Action.ENABLE ->
                    ScheduleStore.Action.DISCONNECT_ENABLE
                onConnect == null && onDisconnect == ScheduleStore.Action.DISABLE ->
                    ScheduleStore.Action.DISCONNECT_DISABLE
                onConnect == ScheduleStore.Action.ENABLE && onDisconnect == ScheduleStore.Action.ENABLE ->
                    ScheduleStore.Action.ENABLE
                onConnect == ScheduleStore.Action.DISABLE && onDisconnect == ScheduleStore.Action.DISABLE ->
                    ScheduleStore.Action.DISABLE
                else -> null
            }
        }

        val isPremium = PremiumManager.isPremium(this)

        val kind: Kind = when {
            preselectedMode == NewScheduleMode.WIFI -> Kind.WIFI
            preselectedMode == NewScheduleMode.BT -> Kind.BT
            preselectedMode == NewScheduleMode.LOCATION -> Kind.LOCATION
            preselectedMode == NewScheduleMode.TIME -> Kind.TIME
            existing?.wifiSsid?.isNotBlank() == true -> Kind.WIFI
            (existing?.btDeviceName?.isNotBlank() == true || existing?.btDeviceAddress?.isNotBlank() == true) -> Kind.BT
            existing?.isLocationSchedule() == true -> Kind.LOCATION
            else -> Kind.TIME
        }

        val isConnKind = kind == Kind.WIFI || kind == Kind.BT
        val isLocationKind = kind == Kind.LOCATION

        fun applyKindVisibility() {
            val isTime = kind == Kind.TIME
            groupTimeMode.isVisible = isTime
            groupStandardAction.isVisible = isTime || isLocationKind
            groupConnActions.isVisible = isConnKind

            val showWifi = isPremium && isConnKind && kind == Kind.WIFI
            val showBt = isPremium && isConnKind && kind == Kind.BT
            val showLocation = isPremium && isLocationKind

            groupWifi.isVisible = showWifi
            groupBt.isVisible = showBt
            groupLocation.isVisible = showLocation

            // Timing container is always visible
        }

        fun requestWifiPermissionThenRetry(action: () -> Unit) {
            pendingAfterLocationGrant = action
            requestLocationPermission.launch(wifiSsidPermissionName())
        }

        fun requestFineLocationPermissionThenRetry(action: () -> Unit) {
            pendingAfterFineLocationGrant = action
            requestFineLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        fun requestBluetoothPermissionThenRetry(action: () -> Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                pendingAfterBluetoothGrant = action
                requestBluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                action()
            }
        }

        fun isLocationEnabled(): Boolean {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            return runCatching {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }.getOrDefault(false)
        }

        fun cleanSsid(raw: String?): String? {
            if (raw.isNullOrBlank()) {
                return null
            }
            val s = raw.trim().removePrefix("\"").removeSuffix("\"")
            if (s.equals("<unknown ssid>", ignoreCase = true)) {
                return null
            }
            return s
        }

        fun scanResultSsid(scan: ScanResult): String? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                scan.wifiSsid?.toString()
            } else {
                runCatching { scan.javaClass.getField("SSID").get(scan) as? String }.getOrNull()
            }
        }

        fun markNeedsLocationHintOnce() {
            getSharedPreferences("switchly_wifi_cache", MODE_PRIVATE).edit {
                putBoolean("wifi_needs_location_hint", true)
            }
        }

        fun openLocationSettings() {
            runCatching { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
        }

        fun openWifiPickerPanel() {
            runCatching { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        }

        fun openWifiPickerOrPermissions() {
            if (!hasWifiSsidPermission()) {
                openPermissionsOverview()
            } else {
                openWifiPickerPanel()
            }
        }

        fun showWifiPickerFromResults(results: List<ScanResult>) {
            val ssids = results
                .mapNotNull { cleanSsid(scanResultSsid(it)) }
                .distinct()
                .sorted()

            if (ssids.isEmpty()) {
                val positiveActionLabel =
                    if (!hasWifiSsidPermission()) R.string.schedules_health_action_permissions
                    else R.string.schedules_wifi_open_picker

                AlertDialog.Builder(this)
                    .setTitle(R.string.schedules_wifi_scan_empty)
                    .setMessage(R.string.schedules_wifi_scan_empty_hint)
                    .setPositiveButton(positiveActionLabel) { _, _ ->
                        openWifiPickerOrPermissions()
                    }
                    .setNeutralButton(R.string.schedules_wifi_enter_manually) { _, _ ->
                        inputWifiSsid.requestFocus()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .showAccented()
                return
            }

            showSwitchlyOptionDialog(
                title = getString(R.string.schedules_wifi_pick_title),
                options = ssids.map { SwitchlyDialogOption(title = it, iconRes = R.drawable.wifi_24) }
            ) { which ->
                inputWifiSsid.setText(ssids[which])
            }
        }

        fun scanWifi() {
            if (!hasWifiSsidPermission()) {
                requestWifiPermissionThenRetry { scanWifi() }
                return
            }
            if (!isLocationEnabled()) {
                markNeedsLocationHintOnce()
                showScheduleMessage(R.string.schedules_wifi_location_required)
                openLocationSettings()
                return
            }

            val wifi = getSystemService(WIFI_SERVICE) as WifiManager
            if (!wifi.isWifiEnabled) {
                showScheduleMessage(R.string.schedules_wifi_enable_wifi)
                openWifiPickerPanel()
                return
            }

            val hasFineLocation =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            val hasNearbyWifi = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) == PackageManager.PERMISSION_GRANTED
            val canReadWifi = if (Build.VERSION.SDK_INT >= 33) hasNearbyWifi else hasFineLocation

            if (!canReadWifi) {
                showWhyLocationDialogForWifi()
                return
            }

            val cached = try {
                if (canReadWifi) wifi.scanResults else emptyList()
            } catch (_: SecurityException) {
                emptyList()
            }

            if (cached.isNotEmpty()) {
                showWifiPickerFromResults(cached)
                return
            }

            showScheduleMessage(R.string.schedules_wifi_scanning)
            val started = try {
                false
            } catch (_: SecurityException) {
                false
            }

            Handler(Looper.getMainLooper()).postDelayed({
                val fresh = try {
                    if (canReadWifi) wifi.scanResults else emptyList()
                } catch (_: SecurityException) {
                    emptyList()
                }
                if (fresh.isNotEmpty()) {
                    showWifiPickerFromResults(fresh)
                } else {
                    val positiveActionLabel =
                        if (!hasWifiSsidPermission()) R.string.schedules_health_action_permissions
                        else R.string.schedules_wifi_open_picker

                    AlertDialog.Builder(this)
                        .setTitle(R.string.schedules_wifi_scan_empty)
                        .setMessage(R.string.schedules_wifi_fix_message)
                        .setPositiveButton(positiveActionLabel) { _, _ ->
                            openWifiPickerOrPermissions()
                        }
                        .setNeutralButton(R.string.schedules_wifi_retry_scan) { _, _ ->
                            scanWifi()
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .showAccented()
                }
            }, if (started) 1400L else 200L)
        }

        fun useConnectedBt() {
            if (!hasBluetoothConnectPermission()) {
                requestBluetoothPermissionThenRetry { useConnectedBt() }
                return
            }

            val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = manager.adapter
            if (adapter == null || !adapter.isEnabled) {
                showScheduleMessage(R.string.schedules_bt_enable_bt)
                runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                return
            }

            val hasBtConnect = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED

            if (!hasBtConnect) {
                requestBluetoothPermissionThenRetry { useConnectedBt() }
                return
            }

            val names = linkedSetOf<String>()

            val bonded = runCatching { adapter.bondedDevices }.getOrDefault(emptySet())
            val isConnectedMethod = runCatching {
                Class.forName("android.bluetooth.BluetoothDevice").getMethod("isConnected")
            }.getOrNull()

            bonded.forEach { device ->
                val connected = runCatching {
                    (isConnectedMethod?.invoke(device) as? Boolean) ?: false
                }.getOrDefault(false)

                if (connected) {
                    val n = runCatching { device.name }
                        .getOrNull()
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                    if (n != null) names += n
                }
            }

            val bleProfiles = intArrayOf(BluetoothProfile.GATT)
            for (profile in bleProfiles) {
                val devices = try {
                    manager.getConnectedDevices(profile)
                } catch (_: IllegalArgumentException) {
                    emptyList()
                } catch (_: SecurityException) {
                    emptyList()
                } catch (_: Throwable) {
                    emptyList()
                }

                for (d in devices) {
                    val n = try {
                        d.name?.trim()?.takeIf { it.isNotEmpty() }
                    } catch (_: Throwable) {
                        null
                    }
                    if (n != null) names += n
                }
            }

            val selected = names.firstOrNull()
            if (selected.isNullOrBlank()) {
                showScheduleMessage(R.string.schedules_bt_no_connected)
                btnPickPairedBt.performClick()
                return
            }

            inputBtName.setText(selected)
        }

        fun pickPairedBt() {
            if (!hasBluetoothConnectPermission()) {
                requestBluetoothPermissionThenRetry { pickPairedBt() }
                return
            }
            val adapter = (getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            if (adapter == null || !adapter.isEnabled) {
                showScheduleMessage(R.string.schedules_bt_enable_bt)
                runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                return
            }

            val hasBtConnect = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED

            if (!hasBtConnect) {
                requestBluetoothPermissionThenRetry { pickPairedBt() }
                return
            }

            val bonded = try {
                if (hasBtConnect) adapter.bondedDevices else emptySet()
            } catch (_: SecurityException) {
                emptySet()
            }

            val names = bonded
                .mapNotNull { d ->
                    runCatching { d.name }
                        .getOrNull()
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                }
                .distinct()
                .sorted()

            if (names.isEmpty()) {
                showScheduleMessage(R.string.schedules_bt_no_paired)
                runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                return
            }

            showSwitchlyOptionDialog(
                title = getString(R.string.schedules_bt_pick_paired),
                options = names.map { SwitchlyDialogOption(title = it, iconRes = R.drawable.bluetooth_24) }
            ) { which ->
                inputBtName.setText(names[which])
            }
        }

        btnScanWifi.setOnClickListener { scanWifi() }
        btnUseConnectedBt.setOnClickListener {
            try {
                useConnectedBt()
            } catch (_: Throwable) {
                showScheduleMessage(R.string.schedules_bt_no_connected)
                btnPickPairedBt.performClick()
            }
        }
        btnPickPairedBt.setOnClickListener { pickPairedBt() }

        var locationLat: Double? = existing?.locationLat
        var locationLng: Double? = existing?.locationLng
        var locationRadiusMeters = existing?.locationRadiusMeters ?: 250
        var locationTrigger = existing?.locationTrigger ?: ScheduleStore.LocationTrigger.ENTER
        val cooldownOptions = listOf(0, 5, 15, 30)
        var selectedCooldownMinutes = existing?.locationCooldownMinutes ?: 15
        var refreshActionUi: () -> Unit = {}

        fun selectedRadiusMeters(): Int = when {
            chipRadius100.isChecked -> 100
            chipRadius500.isChecked -> 500
            else -> 250
        }

        fun syncRadiusChips() {
            when (locationRadiusMeters) {
                100 -> chipRadius100.isChecked = true
                500 -> chipRadius500.isChecked = true
                else -> chipRadius250.isChecked = true
            }
        }

        var suppressLocationQueryWatcher = false

        fun currentLocationLabelText(): String {
            val manual = inputLocationQuery.text?.toString()?.trim().orEmpty()
            if (manual.isNotBlank()) {
                return manual
            }
            val lat = locationLat
            val lng = locationLng
            if (lat == null || lng == null) {
                return getString(R.string.schedules_location_not_set)
            }
            return getString(R.string.schedules_location_coords_fmt, lat, lng)
        }

        fun updateLocationSummary() {
            val lat = locationLat
            val lng = locationLng
            if (lat == null || lng == null) {
                textLocationSummary.text = getString(R.string.schedules_location_not_set)
            } else {
                val label = currentLocationLabelText()
                textLocationSummary.text = getString(
                    R.string.schedules_location_selected_fmt,
                    label,
                    selectedRadiusMeters()
                )
            }
        }

        fun applyPickedLocation(latitude: Double, longitude: Double, suggestedLabel: String?) {
            locationLat = latitude
            locationLng = longitude
            val replacementLabel = suggestedLabel?.trim().orEmpty()
            val targetLabel = if (replacementLabel.isNotBlank()) {
                replacementLabel
            } else {
                getString(R.string.schedules_location_coords_fmt, latitude, longitude)
            }
            suppressLocationQueryWatcher = true
            inputLocationQuery.setText(targetLabel)
            inputLocationQuery.setSelection(targetLabel.length)
            suppressLocationQueryWatcher = false
            inputLocationQuery.error = null
            layoutLocationQuery.error = null
            updateLocationSummary()
        }

        fun setupLocationTriggerSpinner() {
            val triggerOptions = listOf(
                ScheduleStore.LocationTrigger.ENTER,
                ScheduleStore.LocationTrigger.EXIT,
                ScheduleStore.LocationTrigger.ENTER_EXIT
            )
            val labels = triggerOptions.map {
                when (it) {
                    ScheduleStore.LocationTrigger.ENTER -> getString(R.string.schedules_location_trigger_enter)
                    ScheduleStore.LocationTrigger.EXIT -> getString(R.string.schedules_location_trigger_exit)
                    ScheduleStore.LocationTrigger.ENTER_EXIT -> getString(R.string.schedules_location_trigger_both)
                }
            }
            spinnerLocationTrigger.setAdapter(SwitchlyDropdownAdapter(this, labels))
            val idx = triggerOptions.indexOf(locationTrigger).takeIf { it >= 0 } ?: 0
            spinnerLocationTrigger.setText(labels[idx], false)
            spinnerLocationTrigger.setOnItemClickListener { _, _, position, _ ->
                locationTrigger = triggerOptions.getOrElse(position) { ScheduleStore.LocationTrigger.ENTER }
                refreshActionUi()
            }
        }

        fun setupLocationCooldownSpinner() {
            val labels = cooldownOptions.map {
                when (it) {
                    0 -> getString(R.string.schedules_location_cooldown_none)
                    5 -> getString(R.string.schedules_location_cooldown_5)
                    15 -> getString(R.string.schedules_location_cooldown_15)
                    else -> getString(R.string.schedules_location_cooldown_30)
                }
            }
            spinnerLocationCooldown.setAdapter(SwitchlyDropdownAdapter(this, labels))
            val idx = cooldownOptions.indexOf(selectedCooldownMinutes).takeIf { it >= 0 } ?: 2
            selectedCooldownMinutes = cooldownOptions[idx]
            spinnerLocationCooldown.setText(labels[idx], false)
            spinnerLocationCooldown.setOnItemClickListener { _, _, position, _ ->
                selectedCooldownMinutes = cooldownOptions.getOrElse(position) { 15 }
            }
        }

        fun useCurrentLocation() {
            if (!hasFineLocationPermission()) {
                showWhyLocationDialogForGeofence { useCurrentLocation() }
                return
            }
            if (!isLocationEnabled()) {
                showScheduleMessage(R.string.schedules_wifi_location_required)
                openLocationSettings()
                return
            }

            showScheduleMessage(R.string.schedules_location_fetching)
            val client = LocationServices.getFusedLocationProviderClient(this)
            val cts = CancellationTokenSource()
            runCatching {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            val fallbackLabel = getString(
                                R.string.schedules_location_coords_fmt,
                                loc.latitude,
                                loc.longitude
                            )
                            reverseGeocodeLabel(loc.latitude, loc.longitude, fallbackLabel) { label ->
                                applyPickedLocation(loc.latitude, loc.longitude, label ?: fallbackLabel)
                            }
                        } else {
                            showScheduleMessage(R.string.schedules_location_not_set)
                        }
                    }
                    .addOnFailureListener {
                        showScheduleMessage(R.string.schedules_location_not_set)
                    }
            }.onFailure {
                showScheduleMessage(R.string.schedules_location_not_set)
            }
        }

        fun openVisualLocationPickerDialog() {
            if (!BuildConfig.SWITCHLY_HAS_MAPS_API_KEY) {
                showScheduleMessage(R.string.schedules_location_map_picker_unavailable)
                inputLocationQuery.requestFocus()
                return
            }

            lifecycleScope.launch {
                val mapsReachable = canResolveGoogleMapsHost()
                if (!mapsReachable) {
                    showScheduleMessage(R.string.schedules_location_map_picker_load_failed)
                    inputLocationQuery.requestFocus()
                    return@launch
                }

                showLocationMapPickerDialog(
                    initialLatitude = locationLat,
                    initialLongitude = locationLng,
                    initialLabel = inputLocationQuery.text?.toString()?.trim().orEmpty().takeIf { it.isNotBlank() }
                ) { picked ->
                    applyPickedLocation(picked.latitude, picked.longitude, picked.label)
                }
            }
        }

        val inlineLocationResults = mutableListOf<ResolvedLocation>()
        val inlineLocationAdapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val itemView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_location_search_result, parent, false)
                return object : RecyclerView.ViewHolder(itemView) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val item = inlineLocationResults[position]
                val v = holder.itemView
                val tvTitle = v.findViewById<TextView>(R.id.tvResultTitle)
                val tvSubtitle = v.findViewById<TextView>(R.id.tvResultSubtitle)
                val ivIcon = v.findViewById<ImageView>(R.id.ivResultIcon)

                tvTitle.text = item.label
                if (!item.subtitle.isNullOrBlank()) {
                    tvSubtitle.text = item.subtitle
                    tvSubtitle.visibility = View.VISIBLE
                } else {
                    tvSubtitle.visibility = View.GONE
                }
                val accent = AccentColor.getAccentColorInt(this@SchedulesActivity)
                ivIcon.imageTintList = ColorStateList.valueOf(accent)

                v.setOnClickListener {
                    applyPickedLocation(item.latitude, item.longitude, item.label)
                    rvLocationResultsInline.visibility = View.GONE
                    tvLocationSearchStatusInline.visibility = View.GONE
                }
            }

            override fun getItemCount(): Int = inlineLocationResults.size
        }
        rvLocationResultsInline.layoutManager = LinearLayoutManager(this)
        rvLocationResultsInline.adapter = inlineLocationAdapter

        fun doInlineLocationSearch() {
            val query = inputLocationQuery.text?.toString()?.trim().orEmpty()
            if (query.isBlank()) {
                layoutLocationQuery.error = getString(R.string.schedules_location_picker_invalid)
                return
            }
            layoutLocationQuery.error = null
            tvLocationSearchStatusInline.visibility = View.GONE
            rvLocationResultsInline.visibility = View.GONE
            progressLocationSearchInline.visibility = View.VISIBLE
            btnLocationSearchInline.isEnabled = false

            searchLocations(query) { results ->
                progressLocationSearchInline.visibility = View.GONE
                btnLocationSearchInline.isEnabled = true
                if (results.isEmpty()) {
                    tvLocationSearchStatusInline.text = getString(R.string.schedules_location_picker_not_found)
                    tvLocationSearchStatusInline.visibility = View.VISIBLE
                    rvLocationResultsInline.visibility = View.GONE
                } else {
                    tvLocationSearchStatusInline.visibility = View.GONE
                    inlineLocationResults.clear()
                    inlineLocationResults.addAll(results)
                    inlineLocationAdapter.notifyDataSetChanged()
                    rvLocationResultsInline.visibility = View.VISIBLE
                }
            }
        }

        // Single box: manual edits invalidate the picked coordinates until a new
        // result (or current location) is chosen.
        inputLocationQuery.addTextChangedListener {
            if (suppressLocationQueryWatcher) {
                updateLocationSummary()
                return@addTextChangedListener
            }
            locationLat = null
            locationLng = null
            layoutLocationQuery.error = null
            rvLocationResultsInline.visibility = View.GONE
            tvLocationSearchStatusInline.visibility = View.GONE
            updateLocationSummary()
        }
        // Prefill the single box when editing an existing location schedule.
        val existingLocationLabel = existing?.locationLabel?.trim().orEmpty()
        if (isLocationKind && (locationLat != null && locationLng != null)) {
            val seed = existingLocationLabel.ifBlank {
                getString(
                    R.string.schedules_location_coords_fmt,
                    locationLat!!,
                    locationLng!!
                )
            }
            suppressLocationQueryWatcher = true
            inputLocationQuery.setText(seed)
            suppressLocationQueryWatcher = false
        }
        btnLocationSearchInline.setOnClickListener { doInlineLocationSearch() }
        inputLocationQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                doInlineLocationSearch()
                true
            } else {
                false
            }
        }
        btnUseCurrentLocation.setOnClickListener { useCurrentLocation() }
        btnOpenMapPicker.isVisible = BuildConfig.SWITCHLY_HAS_MAPS_API_KEY
        btnOpenMapPicker.setOnClickListener { openVisualLocationPickerDialog() }
        chipRadius100.setOnCheckedChangeListener { _, checked -> if (checked) { locationRadiusMeters = 100; updateLocationSummary() } }
        chipRadius250.setOnCheckedChangeListener { _, checked -> if (checked) { locationRadiusMeters = 250; updateLocationSummary() } }
        chipRadius500.setOnCheckedChangeListener { _, checked -> if (checked) { locationRadiusMeters = 500; updateLocationSummary() } }
        syncRadiusChips()
        setupLocationTriggerSpinner()
        setupLocationCooldownSpinner()
        updateLocationSummary()

        val profiles = ProfileStore.getProfiles(this)
        val profileList: List<String> =
            if (existing != null && existing.profile !in profiles) {
                listOf(existing.profile) + profiles
            } else {
                profiles.toList()
            }

        val profileAdapter = SwitchlyDropdownAdapter(this, profileList)
        spinnerProfile.setAdapter(profileAdapter)

        var selectedProfileIndex = 0
        spinnerProfile.setOnItemClickListener { _, _, position, _ ->
            selectedProfileIndex = position
        }

        fun selectProfile(name: String?) {
            if (name == null) {
                return
            }
            val idx = profileList.indexOf(name)
            if (idx >= 0) {
                selectedProfileIndex = idx
                spinnerProfile.setText(profileList[idx], false)
            }
        }

        if (targetProfile != null) {
            cardAttachedProfile.visibility = View.VISIBLE
            layoutProfile.visibility = View.GONE
            tvAttachedProfileName.text = targetProfile
        } else {
            cardAttachedProfile.visibility = View.GONE
            layoutProfile.visibility = View.VISIBLE
        }

        val defaultProfile = targetProfile ?: existing?.profile ?: ProfileStore.getCurrent(this) ?: "Default"
        selectProfile(defaultProfile)

        var startMinutes = if (kind == Kind.TIME) 8 * 60 else 0
        var endMinutes = if (kind == Kind.TIME) 17 * 60 else 24 * 60 - 1
        var savedCustomStart = 8 * 60
        var savedCustomEnd = 17 * 60
        var startDateYmd = 0
        var endDateYmd = 0
        var selectedTimeModeIndex = 0

        fun currentTimeMode(): TimeMode {
            if (kind != Kind.TIME) {
                return TimeMode.SINGLE
            }
            return when (selectedTimeModeIndex) {
                1 -> TimeMode.TIME_RANGE
                2 -> TimeMode.DATE_RANGE
                else -> TimeMode.SINGLE
            }
        }

        fun setupTimeModeSpinner() {
            if (kind != Kind.TIME) {
                return
            }

            val nfcLockOn = isNfcLockActiveForSchedules()
            val labels = if (nfcLockOn) {
                listOf(getString(R.string.schedules_time_mode_single))
            } else {
                listOf(
                    getString(R.string.schedules_time_mode_single),
                    getString(R.string.schedules_time_mode_range),
                    getString(R.string.schedules_time_mode_date_range)
                )
            }

            val timeModeAdapter = SwitchlyDropdownAdapter(this, labels)
            spinnerTimeMode.setAdapter(timeModeAdapter)

            val sel = if (nfcLockOn) {
                0
            } else {
                when {
                    existing?.type == ScheduleStore.Type.ONE_TIME -> 2
                    existing?.action == ScheduleStore.Action.ENABLE_AND_DISABLE ||
                        existing?.action == ScheduleStore.Action.DISABLE_AND_ENABLE -> 1
                    else -> 0
                }
            }
            selectedTimeModeIndex = sel
            spinnerTimeMode.setText(
                labels.getOrNull(sel) ?: labels.firstOrNull().orEmpty(),
                false
            )
        }

        var actionOptions: List<ScheduleStore.Action> = emptyList()
        var selectedActionIndex = 0

        fun actionLabel(a: ScheduleStore.Action): String = when (a) {
            ScheduleStore.Action.ENABLE -> getString(R.string.schedules_action_enable)
            ScheduleStore.Action.DISABLE -> getString(R.string.schedules_action_disable)
            ScheduleStore.Action.TOGGLE -> getString(R.string.schedules_action_toggle)
            ScheduleStore.Action.ENABLE_AND_DISABLE -> getString(R.string.schedules_action_enable_disable)
            ScheduleStore.Action.DISABLE_AND_ENABLE -> getString(R.string.schedules_action_disable_enable)
            ScheduleStore.Action.DISCONNECT_ENABLE -> getString(R.string.schedules_action_disconnect_enable)
            ScheduleStore.Action.DISCONNECT_DISABLE -> getString(R.string.schedules_action_disconnect_disable)
        }

        fun setActionOptions(options: List<ScheduleStore.Action>, prefer: ScheduleStore.Action?) {
            actionOptions = options
            val labels = options.map { actionLabel(it) }
            val actionAdapter = SwitchlyDropdownAdapter(this, labels)
            spinnerAction.setAdapter(actionAdapter)

            val pref = prefer ?: options.firstOrNull()
            val idx = if (pref != null) options.indexOf(pref) else 0
            selectedActionIndex = if (idx >= 0) idx else 0
            spinnerAction.setText(
                labels.getOrNull(selectedActionIndex) ?: labels.firstOrNull().orEmpty(),
                false
            )
        }

        fun selectedAction(): ScheduleStore.Action {
            return actionOptions.getOrNull(selectedActionIndex) ?: ScheduleStore.Action.ENABLE
        }

        fun filterActionsForNfc(options: List<ScheduleStore.Action>): List<ScheduleStore.Action> {
            if (!isNfcLockActiveForSchedules()) {
                return options
            }
            val filtered = options.filterNot { it in nfcLockedActions }
            return if (filtered.isNotEmpty()) {
                filtered
            } else {
                listOf(ScheduleStore.Action.ENABLE)
            }
        }

        fun updateActionNfcHint() {
            val nfcLocked = isNfcLockActiveForSchedules()
            if (!nfcLocked) {
                textActionNfcHint.isVisible = false
                return
            }

            val actionBlocked = selectedAction() in nfcLockedActions
            textActionNfcHint.text = if (actionBlocked) {
                getString(R.string.schedules_dialog_nfc_lock_hint)
            } else {
                getString(R.string.schedules_dialog_nfc_lock_filtered_hint)
            }
            textActionNfcHint.isVisible = true
        }

        fun formatMinutes(m: Int): String {
            return TimeFormatPrefs.formatMinutesOfDay(this, m)
        }

        fun formatYmd(ymd: Int): String {
            if (ymd <= 0) {
                return getString(R.string.schedules_date_not_set)
            }
            val y = ymd / 10000
            val mo = (ymd / 100) % 100
            val d = ymd % 100
            val c = Calendar.getInstance().apply {
                set(Calendar.YEAR, y)
                set(Calendar.MONTH, mo - 1)
                set(Calendar.DAY_OF_MONTH, d)
            }
            return DateFormat.getDateInstance(DateFormat.MEDIUM).format(c.time)
        }

        fun updateActionUi() {
            when (kind) {
                Kind.TIME -> {
                    when (currentTimeMode()) {
                        TimeMode.SINGLE -> {
                            setActionOptions(
                                filterActionsForNfc(
                                    listOf(
                                        ScheduleStore.Action.ENABLE,
                                        ScheduleStore.Action.DISABLE,
                                        ScheduleStore.Action.TOGGLE
                                    )
                                ),
                                existing?.action?.takeIf {
                                    it == ScheduleStore.Action.ENABLE ||
                                        it == ScheduleStore.Action.DISABLE ||
                                        it == ScheduleStore.Action.TOGGLE
                                } ?: ScheduleStore.Action.ENABLE
                            )
                        }

                        TimeMode.TIME_RANGE -> {
                            setActionOptions(
                                filterActionsForNfc(
                                    listOf(
                                        ScheduleStore.Action.ENABLE_AND_DISABLE,
                                        ScheduleStore.Action.DISABLE_AND_ENABLE
                                    )
                                ),
                                existing?.action?.takeIf {
                                    it == ScheduleStore.Action.ENABLE_AND_DISABLE ||
                                        it == ScheduleStore.Action.DISABLE_AND_ENABLE
                                } ?: ScheduleStore.Action.ENABLE_AND_DISABLE
                            )
                        }

                        TimeMode.DATE_RANGE -> {
                            setActionOptions(
                                filterActionsForNfc(listOf(ScheduleStore.Action.ENABLE_AND_DISABLE)),
                                ScheduleStore.Action.ENABLE_AND_DISABLE
                            )
                        }
                    }
                }

                Kind.WIFI, Kind.BT -> {
                    // Handled by connection dropdowns
                }

                Kind.LOCATION -> {
                    val options = when (locationTrigger) {
                        ScheduleStore.LocationTrigger.ENTER,
                        ScheduleStore.LocationTrigger.EXIT -> listOf(
                            ScheduleStore.Action.ENABLE,
                            ScheduleStore.Action.DISABLE,
                            ScheduleStore.Action.TOGGLE
                        )
                        ScheduleStore.LocationTrigger.ENTER_EXIT -> listOf(
                            ScheduleStore.Action.ENABLE_AND_DISABLE,
                            ScheduleStore.Action.DISABLE_AND_ENABLE
                        )
                    }

                    val preferred = existing?.action?.takeIf { it in options } ?: options.first()
                    setActionOptions(filterActionsForNfc(options), preferred)
                }
            }
            updateActionNfcHint()
        }

        refreshActionUi = { updateActionUi() }

        fun updateVisibilityForMode() {
            applyKindVisibility()

            when (kind) {
                Kind.TIME -> {
                    when (currentTimeMode()) {
                        TimeMode.SINGLE -> {
                            row247.isVisible = false
                            divider247.isVisible = false
                            rowAllDay.isVisible = false
                            dividerTime.isVisible = false
                            groupTimeRow.isVisible = true
                            cardStartTime.isVisible = true
                            cardEndTime.isVisible = false
                            ivTimeArrow.isVisible = false
                            groupWeekly.isVisible = true
                            groupOnce.isVisible = false
                        }

                        TimeMode.TIME_RANGE -> {
                            row247.isVisible = true
                            tv247Subtitle.setText(R.string.schedules_247_subtitle_time)
                            val is247 = switch247.isChecked
                            divider247.isVisible = !is247
                            rowAllDay.isVisible = !is247
                            val isAllDay = !is247 && switchAllDay.isChecked
                            dividerTime.isVisible = !is247 && !isAllDay
                            groupTimeRow.isVisible = !is247 && !isAllDay
                            cardStartTime.isVisible = !is247 && !isAllDay
                            cardEndTime.isVisible = !is247 && !isAllDay
                            ivTimeArrow.isVisible = !is247 && !isAllDay
                            groupWeekly.isVisible = !is247
                            groupOnce.isVisible = false
                        }

                        TimeMode.DATE_RANGE -> {
                            row247.isVisible = false
                            divider247.isVisible = false
                            rowAllDay.isVisible = false
                            dividerTime.isVisible = false
                            groupTimeRow.isVisible = false
                            groupWeekly.isVisible = false
                            groupOnce.isVisible = true
                        }
                    }
                }

                Kind.WIFI, Kind.BT, Kind.LOCATION -> {
                    row247.isVisible = true
                    tv247Subtitle.setText(
                        if (kind == Kind.LOCATION) R.string.schedules_247_subtitle_loc
                        else R.string.schedules_247_subtitle_conn
                    )
                    val is247 = switch247.isChecked
                    divider247.isVisible = !is247
                    rowAllDay.isVisible = !is247
                    val isAllDay = !is247 && switchAllDay.isChecked
                    dividerTime.isVisible = !is247 && !isAllDay
                    groupTimeRow.isVisible = !is247 && !isAllDay
                    cardStartTime.isVisible = !is247 && !isAllDay
                    cardEndTime.isVisible = !is247 && !isAllDay
                    ivTimeArrow.isVisible = !is247 && !isAllDay
                    groupWeekly.isVisible = !is247
                    groupOnce.isVisible = false
                }
            }
        }

        fun updateLabels() {
            if (groupTimeRow.isVisible) {
                textStartTime.text = formatMinutes(startMinutes)
                if (cardEndTime.isVisible) {
                    textEndTime.text = formatMinutes(endMinutes)
                }
            }

            if (groupOnce.isVisible) {
                textStartDate.text = formatYmd(startDateYmd)
                textEndDate.text = formatYmd(endDateYmd)
            }
        }

        if (existing != null) {
            val exTitle = existing.title.trim()
            if (!exTitle.equals(existing.profile.trim(), ignoreCase = true)) {
                editTitle.setText(exTitle)
            } else {
                editTitle.setText("")
            }
            editNote.setText(existing.note)
            selectProfile(existing.profile)

            startMinutes = existing.startMinutes
            endMinutes = existing.endMinutes
            if (startMinutes != 0 || endMinutes < 24 * 60 - 1) {
                savedCustomStart = startMinutes
                savedCustomEnd = endMinutes
            }

            if (existing.type == ScheduleStore.Type.ONE_TIME) {
                startDateYmd = existing.startDate
                endDateYmd = existing.endDate
            }

            val dm = existing.daysMask
            setDayButtonChecked(chipMon, (dm and Days.MON) != 0)
            setDayButtonChecked(chipTue, (dm and Days.TUE) != 0)
            setDayButtonChecked(chipWed, (dm and Days.WED) != 0)
            setDayButtonChecked(chipThu, (dm and Days.THU) != 0)
            setDayButtonChecked(chipFri, (dm and Days.FRI) != 0)
            setDayButtonChecked(chipSat, (dm and Days.SAT) != 0)
            setDayButtonChecked(chipSun, (dm and Days.SUN) != 0)

            if (kind == Kind.WIFI && isPremium) inputWifiSsid.setText(existing.wifiSsid.orEmpty())
            if (kind == Kind.BT && isPremium) inputBtName.setText(existing.btDeviceName ?: existing.btDeviceAddress.orEmpty())
            if (kind == Kind.LOCATION && isPremium) {
                suppressLocationQueryWatcher = true
                inputLocationQuery.setText(existing.locationLabel.orEmpty())
                suppressLocationQueryWatcher = false
                locationLat = existing.locationLat
                locationLng = existing.locationLng
                locationRadiusMeters = existing.locationRadiusMeters
                locationTrigger = existing.locationTrigger ?: ScheduleStore.LocationTrigger.ENTER
                selectedCooldownMinutes = existing.locationCooldownMinutes
                syncRadiusChips()
                setupLocationTriggerSpinner()
                setupLocationCooldownSpinner()
                updateLocationSummary()
            }
        } else {
            val initialProfile = targetProfile ?: ProfileStore.getCurrent(this)
            selectProfile(initialProfile)
            dayButtons.forEach { setDayButtonChecked(it, true) }
        }

        val initialIs247: Boolean
        val initialIsAllDay: Boolean
        if (existing != null) {
            val isFullDay = startMinutes == 0 && endMinutes >= 24 * 60 - 1
            val isAllWeek = existing.daysMask == 0x7F || existing.daysMask == 0
            if (isFullDay && isAllWeek) {
                initialIs247 = true
                initialIsAllDay = false
            } else if (isFullDay) {
                initialIs247 = false
                initialIsAllDay = true
            } else {
                initialIs247 = false
                initialIsAllDay = false
            }
        } else {
            if (isConnKind || isLocationKind) {
                initialIs247 = true
                initialIsAllDay = false
            } else {
                initialIs247 = false
                initialIsAllDay = false
            }
        }
        switch247.isChecked = initialIs247
        switchAllDay.isChecked = initialIsAllDay

        applyKindVisibility()
        setupTimeModeSpinner()
        updateActionUi()
        updateVisibilityForMode()
        updateLabels()
        updateActionNfcHint()

        switch247.setOnCheckedChangeListener { _, is247 ->
            if (is247) {
                if (startMinutes != 0 || endMinutes < 24 * 60 - 1) {
                    savedCustomStart = startMinutes
                    savedCustomEnd = endMinutes
                }
                startMinutes = 0
                endMinutes = 24 * 60 - 1
            } else {
                if (switchAllDay.isChecked) {
                    startMinutes = 0
                    endMinutes = 24 * 60 - 1
                } else {
                    startMinutes = savedCustomStart
                    endMinutes = savedCustomEnd
                }
            }
            updateVisibilityForMode()
            updateLabels()
        }

        row247.setOnClickListener {
            switch247.toggle()
        }

        switchAllDay.setOnCheckedChangeListener { _, isAllDay ->
            if (isAllDay) {
                if (startMinutes != 0 || endMinutes < 24 * 60 - 1) {
                    savedCustomStart = startMinutes
                    savedCustomEnd = endMinutes
                }
                startMinutes = 0
                endMinutes = 24 * 60 - 1
            } else {
                startMinutes = savedCustomStart
                endMinutes = savedCustomEnd
            }
            updateVisibilityForMode()
            updateLabels()
        }

        rowAllDay.setOnClickListener {
            switchAllDay.toggle()
        }

        spinnerAction.setOnItemClickListener { _, _, pos, _ ->
            selectedActionIndex = pos
            updateActionNfcHint()
        }

        spinnerTimeMode.setOnItemClickListener { _, _, pos, _ ->
            if (kind != Kind.TIME) return@setOnItemClickListener
            selectedTimeModeIndex = pos
            updateActionUi()
            updateVisibilityForMode()
            updateLabels()
        }

        fun pickTime(initial: Int, onPicked: (Int) -> Unit) {
            val h = (initial / 60).coerceIn(0, 23)
            val m = (initial % 60).coerceIn(0, 59)

            val picker = MaterialTimePicker.Builder()
                .setTheme(AccentColor.getTimePickerTheme(this@SchedulesActivity))
                .setTimeFormat(if (TimeFormatPrefs.is24Hour(this)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
                .setHour(h)
                .setMinute(m)
                .build()

            picker.addOnPositiveButtonClickListener {
                onPicked(picker.hour * 60 + picker.minute)
                updateLabels()
            }

            val tag = "switchly_timepicker_${SystemClock.uptimeMillis()}"
            picker.show(supportFragmentManager, tag)
        }

        fun pickDate(initialYmd: Int, onPicked: (Int) -> Unit) {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
            }
            if (initialYmd > 0) {
                val y = initialYmd / 10000
                val mo = (initialYmd / 100) % 100
                val d = initialYmd % 100
                cal.set(y, mo - 1, d)
            } else {
                val today = Calendar.getInstance()
                cal.set(
                    today.get(Calendar.YEAR),
                    today.get(Calendar.MONTH),
                    today.get(Calendar.DAY_OF_MONTH)
                )
            }

            val picker = MaterialDatePicker.Builder.datePicker()
                .setTheme(at.saltyy.switchly.theme.AccentColor.getDatePickerTheme(this@SchedulesActivity))
                .setSelection(cal.timeInMillis)
                .build()
            picker.addOnPositiveButtonClickListener { millis ->
                val selected = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = millis
                }
                val ymd = selected.get(Calendar.YEAR) * 10000 +
                    (selected.get(Calendar.MONTH) + 1) * 100 +
                    selected.get(Calendar.DAY_OF_MONTH)
                onPicked(ymd)
                updateLabels()
            }
            val shown = runCatching {
                picker.show(
                    supportFragmentManager,
                    "switchly_datepicker_${SystemClock.uptimeMillis()}"
                )
            }.isSuccess
            if (!shown) {
                return
            }
            if (CustomAccentApplier.isCustomAccentEnabled(this)) {
                window.decorView.post {
                    picker.dialog?.window?.decorView?.let { decor ->
                        CustomAccentApplier.applyToView(decor, this)
                        longArrayOf(120L, 360L).forEach { delay ->
                            decor.postDelayed(
                                { runCatching { CustomAccentApplier.applyToView(decor, this) } },
                                delay
                            )
                        }
                    }
                }
            }
        }

        cardStartTime.setOnClickListener {
            if (groupTimeRow.isVisible) {
                pickTime(startMinutes) { startMinutes = it }
            }
        }
        cardEndTime.setOnClickListener {
            if (groupTimeRow.isVisible && cardEndTime.isVisible) {
                pickTime(endMinutes) { endMinutes = it }
            }
        }

        cardStartDate.setOnClickListener {
            if (groupOnce.isVisible) {
                pickDate(
                    if (startDateYmd > 0) startDateYmd else ScheduleStore.todayYmd()
                ) { startDateYmd = it }
            }
        }
        cardEndDate.setOnClickListener {
            if (groupOnce.isVisible) {
                pickDate(
                    if (endDateYmd > 0) endDateYmd else ScheduleStore.todayYmd()
                ) { endDateYmd = it }
            }
        }

        val dialog = MaterialAlertDialogBuilder(this, AccentColor.getDialogTheme(this))
            .setTitle(if (existing == null) R.string.schedules_add else R.string.schedules_edit)
            .setView(view)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.styleSwitchlyDialogButtons()
            dialog.applySwitchlyDialogWidth(0.94f)
            val btnPos = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val btnNeg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            btnPos.setText(if (existing == null) R.string.create else R.string.save)
            btnPos.setTextColor(accent)
            btnNeg.setTextColor(accent)
            tintSwitchCompat(switch247)
        tintSwitchCompat(switchAllDay)

            btnPos.setOnClickListener {
                if (!canEditSchedules()) {
                    dialog.dismiss()
                    refreshList()
                    return@setOnClickListener
                }
                layoutWifiSsid.error = null
                layoutBtName.error = null
                layoutLocationQuery.error = null
                inputWifiSsid.error = null
                inputBtName.error = null
                inputLocationQuery.error = null

                val profile = (if (targetProfile != null) {
                    targetProfile
                } else {
                    profileList.getOrNull(selectedProfileIndex)
                        ?: spinnerProfile.text?.toString().orEmpty().ifBlank {
                            ProfileStore.getCurrent(this@SchedulesActivity) ?: "Default"
                        }
                }) ?: "Default"
                if (profile.isBlank()) {
                    showSnack(R.string.schedules_error_no_profile)
                    return@setOnClickListener
                }

                val wifiSsid: String? = if (kind == Kind.WIFI && isPremium) {
                    inputWifiSsid.text.toString().trim().ifEmpty { null }
                } else {
                    null
                }

                val btName: String? = if (kind == Kind.BT && isPremium) {
                    inputBtName.text.toString().trim().ifEmpty { null }
                } else {
                    null
                }

                val locationQueryText: String = if (kind == Kind.LOCATION && isPremium) {
                    inputLocationQuery.text.toString().trim()
                } else {
                    ""
                }
                // Accept pasted coordinates even without an explicit search+pick.
                if (kind == Kind.LOCATION && isPremium && (locationLat == null || locationLng == null)) {
                    parseLocationCoordinateQuery(locationQueryText)?.let { (lat, lng) ->
                        locationLat = lat
                        locationLng = lng
                        updateLocationSummary()
                    }
                }
                val locationLabel: String? = if (kind == Kind.LOCATION && isPremium) {
                    locationQueryText.ifEmpty { null }
                } else {
                    null
                }

                if (kind == Kind.WIFI && isPremium && wifiSsid.isNullOrBlank()) {
                    layoutWifiSsid.error = getString(R.string.schedules_error_wifi_required)
                    inputWifiSsid.error = getString(R.string.schedules_error_wifi_required)
                    inputWifiSsid.requestFocus()
                    showSnack(R.string.schedules_error_wifi_required)
                    return@setOnClickListener
                }

                if (kind == Kind.BT && isPremium && btName.isNullOrBlank()) {
                    layoutBtName.error = getString(R.string.schedules_error_bt_required)
                    inputBtName.error = getString(R.string.schedules_error_bt_required)
                    inputBtName.requestFocus()
                    showSnack(R.string.schedules_error_bt_required)
                    return@setOnClickListener
                }

                if (kind == Kind.LOCATION && isPremium && (locationLat == null || locationLng == null)) {
                    layoutLocationQuery.error = getString(R.string.schedules_error_location_required)
                    inputLocationQuery.error = getString(R.string.schedules_error_location_required)
                    inputLocationQuery.requestFocus()
                    showSnack(R.string.schedules_error_location_required)
                    return@setOnClickListener
                }

                if (kind == Kind.WIFI && isPremium && !wifiSsid.isNullOrBlank() && !hasWifiSsidPermission()) {
                    showWhyLocationDialogForWifi()
                    return@setOnClickListener
                }

                if (kind == Kind.BT && isPremium && !btName.isNullOrBlank() && !hasBluetoothConnectPermission()) {
                    showWhyBluetoothDialogForSchedules()
                    return@setOnClickListener
                }

                if (kind == Kind.LOCATION && isPremium && !hasFineLocationPermission()) {
                    showWhyLocationDialogForGeofence()
                    return@setOnClickListener
                }
                if (kind == Kind.LOCATION && isPremium && !hasBackgroundLocationPermission()) {
                    showWhyBackgroundLocationDialogForGeofence()
                    return@setOnClickListener
                }

                val connAction: ScheduleStore.Action? = if (isConnKind) {
                    val resolved = getConnAction()
                    if (resolved == null) {
                        showSnack(R.string.schedules_conn_summary_none)
                        return@setOnClickListener
                    }
                    resolved
                } else null

                var daysMask = 0
                val isDateRange = kind == Kind.TIME && currentTimeMode() == TimeMode.DATE_RANGE

                if (switch247.isChecked) {
                    daysMask = 0x7F
                } else if (!isDateRange) {
                    if (chipMon.isChecked) daysMask = daysMask or Days.MON
                    if (chipTue.isChecked) daysMask = daysMask or Days.TUE
                    if (chipWed.isChecked) daysMask = daysMask or Days.WED
                    if (chipThu.isChecked) daysMask = daysMask or Days.THU
                    if (chipFri.isChecked) daysMask = daysMask or Days.FRI
                    if (chipSat.isChecked) daysMask = daysMask or Days.SAT
                    if (chipSun.isChecked) daysMask = daysMask or Days.SUN

                    if (daysMask == 0) {
                        showSnack(R.string.schedules_error_no_days)
                        return@setOnClickListener
                    }
                }

                if (isDateRange) {
                    if (startDateYmd <= 0 || endDateYmd <= 0) {
                        showSnack(R.string.schedules_error_no_dates)
                        return@setOnClickListener
                    }
                    if (endDateYmd < startDateYmd) {
                        showSnack(R.string.schedules_error_date_order)
                        return@setOnClickListener
                    }
                }

                val action: ScheduleStore.Action = when {
                    isConnKind -> connAction!!
                    isLocationKind -> selectedAction()
                    currentTimeMode() == TimeMode.TIME_RANGE -> selectedAction()
                    currentTimeMode() == TimeMode.DATE_RANGE -> ScheduleStore.Action.ENABLE_AND_DISABLE
                    else -> selectedAction()
                }

                val normalizedStart: Int
                val normalizedEnd: Int

                if (switch247.isChecked || switchAllDay.isChecked || isDateRange) {
                    normalizedStart = 0
                    normalizedEnd = 24 * 60 - 1
                } else {
                    normalizedStart = startMinutes
                    normalizedEnd = when (action) {
                        ScheduleStore.Action.ENABLE_AND_DISABLE,
                        ScheduleStore.Action.DISABLE_AND_ENABLE -> endMinutes
                        else -> startMinutes
                    }
                }

                val isFixedRange = !switch247.isChecked && !switchAllDay.isChecked && !isDateRange
                if (isFixedRange && (isConnKind || isLocationKind || action == ScheduleStore.Action.ENABLE_AND_DISABLE || action == ScheduleStore.Action.DISABLE_AND_ENABLE)) {
                    if (normalizedEnd == normalizedStart) {
                        showSnack(R.string.schedules_error_time_range_empty)
                        return@setOnClickListener
                    }
                }

                val type: ScheduleStore.Type = when {
                    isConnKind || isLocationKind -> ScheduleStore.Type.WEEKLY
                    isDateRange -> ScheduleStore.Type.ONE_TIME
                    else -> ScheduleStore.Type.WEEKLY
                }

                val newSchedule = ScheduleStore.Schedule(
                    id = existing?.id ?: ScheduleStore.nextId(ScheduleStore.getAll(this@SchedulesActivity)),
                    enabled = existing?.enabled ?: true,
                    profile = profile,
                    title = editTitle.text.toString().trim(),
                    note = editNote.text.toString().trim(),
                    type = type,
                    daysMask = if (type == ScheduleStore.Type.WEEKLY) daysMask else 0,
                    startMinutes = normalizedStart,
                    endMinutes = normalizedEnd,
                    startDate = if (type == ScheduleStore.Type.ONE_TIME) startDateYmd else 0,
                    endDate = if (type == ScheduleStore.Type.ONE_TIME) endDateYmd else 0,
                    wifiSsid = wifiSsid,
                    btDeviceName = btName,
                    locationLabel = locationLabel,
                    locationLat = if (kind == Kind.LOCATION && isPremium) locationLat else null,
                    locationLng = if (kind == Kind.LOCATION && isPremium) locationLng else null,
                    locationRadiusMeters = if (kind == Kind.LOCATION && isPremium) selectedRadiusMeters() else 250,
                    locationTrigger = if (kind == Kind.LOCATION && isPremium) locationTrigger else null,
                    locationCooldownMinutes = if (kind == Kind.LOCATION && isPremium) selectedCooldownMinutes else 15,
                    action = action
                )

                val oldList = ScheduleStore.getAll(this@SchedulesActivity)
                val newList = if (existing == null) {
                    oldList + newSchedule
                } else {
                    oldList.map { if (it.id == existing.id) newSchedule else it }
                }

                fun persistSchedule() {
                    ScheduleStore.saveAll(this@SchedulesActivity, newList)
                    LocationTriggerMonitor.syncAsync(this@SchedulesActivity)
                    reapplySchedulesNow()
                    SchedulePlanner.updateNextAlarm(this@SchedulesActivity)
                    SchedulePlanner.notifyNextChanged(this@SchedulesActivity)
                    refreshList()
                    dialog.dismiss()
                }

                val overlap = ScheduleInsights.detectOverlaps(newList).firstOrNull {
                    it.first.id == newSchedule.id || it.second.id == newSchedule.id
                }
                if (overlap != null) {
                    val other = if (overlap.first.id == newSchedule.id) overlap.second else overlap.first
                    AlertDialog.Builder(this@SchedulesActivity)
                        .setTitle(R.string.schedules_overlap_warning_title)
                        .setMessage(
                            getString(
                                R.string.schedules_overlap_warning_message_fmt,
                                ScheduleInsights.scheduleDisplayName(other)
                            )
                        )
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.schedules_overlap_warning_save) { _, _ ->
                            persistSchedule()
                        }
                        .showAccented()
                    return@setOnClickListener
                }

                persistSchedule()
            }
        }

        dialog.show()
    }

    private fun parseLocationCoordinateQuery(raw: String): Pair<Double, Double>? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return null
        }

        val patterns = listOf(
            Regex("""^\s*([+-]?\d+(?:[.,]\d+)?)\s*[,;]\s*([+-]?\d+(?:[.,]\d+)?)\s*$"""),
            Regex("""^\s*([+-]?\d+(?:[.,]\d+)?)\s+([+-]?\d+(?:[.,]\d+)?)\s*$""")
        )

        for (pattern in patterns) {
            val match = pattern.matchEntire(trimmed) ?: continue
            val lat = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: continue
            val lng = match.groupValues[2].replace(',', '.').toDoubleOrNull() ?: continue
            if (lat in -90.0..90.0 && lng in -180.0..180.0) {
                return lat to lng
            }
        }

        return null
    }

    private fun formatGeocoderLabelDetailed(address: android.location.Address?): Pair<String?, String?> {
        if (address == null) return null to null

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

    private fun formatGeocoderLabel(address: android.location.Address?): String? {
        return formatGeocoderLabelDetailed(address).first
    }

    private fun reverseGeocodeLabel(
        latitude: Double,
        longitude: Double,
        fallback: String? = null,
        onResult: (String?) -> Unit
    ) {
        if (!Geocoder.isPresent()) {
            onResult(fallback)
            return
        }

        val geocoder = Geocoder(this, Locale.getDefault())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(latitude, longitude, 3) { addresses ->
                val label = addresses.firstNotNullOfOrNull { formatGeocoderLabel(it) } ?: fallback
                runOnUiThread { onResult(label) }
            }
        } else {
            lifecycleScope.launch {
                val label = withContext(Dispatchers.IO) {
                    runCatching {
                        getFromLocationBlockingCompat(geocoder, latitude, longitude)
                    }.getOrNull().orEmpty().firstNotNullOfOrNull { formatGeocoderLabel(it) } ?: fallback
                }
                onResult(label)
            }
        }
    }

    private fun getFromLocationBlockingCompat(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double
    ): List<android.location.Address> {
        return runCatching {
            val method = Geocoder::class.java.getMethod(
                "getFromLocation",
                java.lang.Double.TYPE,
                java.lang.Double.TYPE,
                Integer.TYPE
            )
            val result = method.invoke(geocoder, latitude, longitude, 3)
            (result as? List<*>)?.filterIsInstance<android.location.Address>().orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun getFromLocationNameBlockingCompat(
        geocoder: Geocoder,
        query: String,
        maxResults: Int = 8
    ): List<android.location.Address> {
        return runCatching {
            val method = Geocoder::class.java.getMethod(
                "getFromLocationName",
                String::class.java,
                Integer.TYPE
            )
            val result = method.invoke(geocoder, query, maxResults)
            (result as? List<*>)?.filterIsInstance<android.location.Address>().orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun searchLocations(query: String, onResults: (List<ResolvedLocation>) -> Unit) {
        val coordinateMatch = parseLocationCoordinateQuery(query)
        if (coordinateMatch != null) {
            val (latitude, longitude) = coordinateMatch
            val fallbackLabel = getString(R.string.schedules_location_coords_fmt, latitude, longitude)
            reverseGeocodeLabel(latitude, longitude, fallbackLabel) { label ->
                onResults(listOf(ResolvedLocation(latitude, longitude, label ?: fallbackLabel)))
            }
            return
        }

        if (!Geocoder.isPresent()) {
            onResults(emptyList())
            return
        }

        val geocoder = Geocoder(this, Locale.getDefault())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocationName(query, 8) { addresses ->
                val results = addresses.mapNotNull { addr ->
                    val (title, subtitle) = formatGeocoderLabelDetailed(addr)
                    val label = title ?: query.trim()
                    ResolvedLocation(addr.latitude, addr.longitude, label, subtitle)
                }
                runOnUiThread { onResults(results) }
            }
        } else {
            lifecycleScope.launch {
                val results = withContext(Dispatchers.IO) {
                    runCatching {
                        getFromLocationNameBlockingCompat(geocoder, query, 8)
                    }.getOrNull().orEmpty().mapNotNull { addr ->
                        val (title, subtitle) = formatGeocoderLabelDetailed(addr)
                        val label = title ?: query.trim()
                        ResolvedLocation(addr.latitude, addr.longitude, label, subtitle)
                    }
                }
                onResults(results)
            }
        }
    }

    private fun resolveLocationQuery(query: String, onResult: (ResolvedLocation?) -> Unit) {
        searchLocations(query) { results ->
            onResult(results.firstOrNull())
        }
    }

    private suspend fun canResolveGoogleMapsHost(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            withTimeout(GOOGLE_MAPS_REACHABILITY_TIMEOUT_MS) {
                InetAddress.getByName(GOOGLE_MAPS_DNS_HOST)
            }
            true
        }.getOrDefault(false)
    }

    private fun showLocationMapPickerDialog(
        initialLatitude: Double?,
        initialLongitude: Double?,
        initialLabel: String?,
        onPicked: (ResolvedLocation) -> Unit
    ) {
        pendingMapPickerCallback = onPicked
        locationMapPickerLauncher.launch(
            LocationMapPickerActivity.createIntent(
                context = this,
                initialLatitude = initialLatitude,
                initialLongitude = initialLongitude,
                initialLabel = initialLabel
            )
        )
    }

}

private class ScheduleAdapter(
    private val onToggleEnabled: (ScheduleStore.Schedule, Boolean) -> Unit,
    private val canInteract: () -> Boolean,
    private val isSelectionMode: () -> Boolean,
    private val isSelected: (Int) -> Boolean,
    private val onToggleSelection: (Int) -> Unit,
    private val onEnterSelection: (Int) -> Unit,
    private val onEdit: (ScheduleStore.Schedule) -> Unit,
    private val onTest: (ScheduleStore.Schedule) -> Unit,
    private val getTargetProfile: () -> String?,
) : androidx.recyclerview.widget.ListAdapter<ScheduleStore.Schedule, ScheduleViewHolder>(DIFF) {

    fun itemAt(position: Int): ScheduleStore.Schedule? = currentList.getOrNull(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule, parent, false)
        return ScheduleViewHolder(
            v,
            onToggleEnabled,
            canInteract,
            isSelectionMode,
            isSelected,
            onToggleSelection,
            onEnterSelection,
            onEdit,
            onTest,
            getTargetProfile,
        )
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF =
            object : androidx.recyclerview.widget.DiffUtil.ItemCallback<ScheduleStore.Schedule>() {
                override fun areItemsTheSame(
                    oldItem: ScheduleStore.Schedule,
                    newItem: ScheduleStore.Schedule
                ) = oldItem.id == newItem.id

                override fun areContentsTheSame(
                    oldItem: ScheduleStore.Schedule,
                    newItem: ScheduleStore.Schedule
                ) = oldItem == newItem
            }
    }
}

private class ScheduleViewHolder(
    itemView: View,
    private val onToggleEnabled: (ScheduleStore.Schedule, Boolean) -> Unit,
    private val canInteract: () -> Boolean,
    private val isSelectionMode: () -> Boolean,
    private val isSelected: (Int) -> Boolean,
    private val onToggleSelection: (Int) -> Unit,
    private val onEnterSelection: (Int) -> Unit,
    private val onEdit: (ScheduleStore.Schedule) -> Unit,
    private val onTest: (ScheduleStore.Schedule) -> Unit,
    private val getTargetProfile: () -> String?,
) : RecyclerView.ViewHolder(itemView) {

    private val kindIcon = itemView.findViewById<ImageView>(R.id.imgKind)
    private val title = itemView.findViewById<TextView>(R.id.textTitle)
    private val subtitle = itemView.findViewById<TextView>(R.id.textSubtitle)
    private val note = itemView.findViewById<TextView>(R.id.textNote)
    private val switchEnabled = itemView.findViewById<SwitchCompat>(R.id.switchEnabled)
    private val btnTest = itemView.findViewById<ImageButton>(R.id.btnTestScheduleInfo)
    private val checkSelect =
        itemView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.checkSelect)
    private val cardRoot = itemView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardRoot)

    private var current: ScheduleStore.Schedule? = null
    private var binding = false

    private fun tintEnabledSwitch() {
        val ctx = itemView.context
        val accent = AccentColor.getAccentColorInt(ctx)
        val thumbOff = Color.WHITE
        val thumbDisabled = Color.LTGRAY
        val trackOn = ColorUtils.setAlphaComponent(accent, 0x88)
        val trackOff = ColorUtils.setAlphaComponent(Color.DKGRAY, 0x44)
        val trackOffDisabled = ColorUtils.setAlphaComponent(Color.GRAY, 0x33)

        switchEnabled.thumbTintList = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(thumbDisabled, accent, thumbOff)
        )

        switchEnabled.trackTintList = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(trackOffDisabled, trackOn, trackOff)
        )
    }

    init {
        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (!binding && !isSelectionMode() && canInteract()) current?.let { onToggleEnabled(it, isChecked) }
        }

        cardRoot.setOnClickListener {
            val s = current ?: return@setOnClickListener
            if (isSelectionMode()) {
                onToggleSelection(s.id)
            } else {
                onEdit(s)
            }
        }

        btnTest.setOnClickListener {
            current?.let(onTest)
        }

        cardRoot.setOnLongClickListener {
            val s = current ?: return@setOnLongClickListener true
            if (!isSelectionMode() && canInteract()) {
                onEnterSelection(s.id)
            } else if (!canInteract()) {
                onEdit(s)
            }
            true
        }
    }

    private fun dp(value: Int): Int =
        (value * itemView.resources.displayMetrics.density + 0.5f).toInt()

    private fun fmtMinutes(m: Int): String {
        val h = m / 60
        val mm = m % 60
        return String.format(Locale.getDefault(), "%02d:%02d", h, mm)
    }

    fun bind(s: ScheduleStore.Schedule) {
        current = s

        val canInteractNow = canInteract()

        binding = true
        switchEnabled.isChecked = s.enabled
        switchEnabled.isEnabled = canInteractNow && !isSelectionMode()
        tintEnabledSwitch()
        binding = false

        val selecting = isSelectionMode()
        val selected = selecting && isSelected(s.id)
        checkSelect.visibility = if (selecting) View.VISIBLE else View.GONE
        checkSelect.isChecked = selected
        btnTest.visibility = if (selecting) View.GONE else View.VISIBLE
        cardRoot.isClickable = canInteractNow || selecting
        cardRoot.isLongClickable = canInteractNow
        val ctx = itemView.context
        val accent = AccentColor.getAccentColorInt(ctx)
        if (selected) {
            cardRoot.strokeWidth = dp(2)
            cardRoot.strokeColor = accent
            cardRoot.setCardBackgroundColor(ColorUtils.setAlphaComponent(accent, 0x22))
        } else {
            cardRoot.strokeWidth = dp(1)
            cardRoot.strokeColor = ContextCompat.getColor(ctx, R.color.foqos_outline_variant)
            cardRoot.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.foqos_surface))
        }

        val hasWifi = !s.wifiSsid.isNullOrBlank()
        val hasBt = (!s.btDeviceName.isNullOrBlank() || !s.btDeviceAddress.isNullOrBlank())
        val hasLocation = s.isLocationSchedule()

        val iconRes = when {
            hasLocation -> R.drawable.location_on_24
            hasWifi && hasBt -> R.drawable.layers_24
            hasWifi -> R.drawable.wifi_24
            hasBt -> R.drawable.bluetooth_24
            else -> R.drawable.alarm_24
        }
        val tintedIcon = ContextCompat.getDrawable(ctx, iconRes)?.mutate()?.apply {
            setTint(accent)
        }
        if (tintedIcon != null) {
            kindIcon.setImageDrawable(tintedIcon)
        } else {
            kindIcon.setImageResource(iconRes)
        }
        kindIcon.imageTintList = ColorStateList.valueOf(accent)
        kindIcon.setColorFilter(accent)
        kindIcon.isEnabled = true

        val a = when {
            !canInteractNow && s.enabled -> 0.72f
            !canInteractNow -> 0.45f
            s.enabled -> 1f
            else -> 0.5f
        }
        kindIcon.alpha = 1f
        title.alpha = a
        subtitle.alpha = a
        note.alpha = a

        val customTitle = s.title.trim()
        val hasExplicitTitle = customTitle.isNotBlank() &&
            !customTitle.equals(s.profile.trim(), ignoreCase = true)

        val hasWindow = !(s.startMinutes == 0 && s.endMinutes >= 24 * 60 - 1)
        val timeWindowStr = if (hasWindow) {
            ctx.getString(
                R.string.schedules_time_range_fmt,
                fmtMinutes(s.startMinutes),
                fmtMinutes(s.endMinutes)
            )
        } else null

        val actionLabel = when (s.action) {
            ScheduleStore.Action.ENABLE -> ctx.getString(R.string.schedules_action_enable)
            ScheduleStore.Action.DISABLE -> ctx.getString(R.string.schedules_action_disable)
            ScheduleStore.Action.TOGGLE -> ctx.getString(R.string.schedules_action_toggle)
            ScheduleStore.Action.ENABLE_AND_DISABLE -> ctx.getString(R.string.schedules_action_enable_disable)
            ScheduleStore.Action.DISABLE_AND_ENABLE -> ctx.getString(R.string.schedules_action_disable_enable)
            ScheduleStore.Action.DISCONNECT_ENABLE -> ctx.getString(R.string.schedules_action_disconnect_enable)
            ScheduleStore.Action.DISCONNECT_DISABLE -> ctx.getString(R.string.schedules_action_disconnect_disable)
        }

        val daysLabel = when (s.type) {
            ScheduleStore.Type.WEEKLY -> {
                val parts = mutableListOf<String>()
                if (s.daysMask and Days.MON != 0) parts += ctx.getString(R.string.day_short_mon)
                if (s.daysMask and Days.TUE != 0) parts += ctx.getString(R.string.day_short_tue)
                if (s.daysMask and Days.WED != 0) parts += ctx.getString(R.string.day_short_wed)
                if (s.daysMask and Days.THU != 0) parts += ctx.getString(R.string.day_short_thu)
                if (s.daysMask and Days.FRI != 0) parts += ctx.getString(R.string.day_short_fri)
                if (s.daysMask and Days.SAT != 0) parts += ctx.getString(R.string.day_short_sat)
                if (s.daysMask and Days.SUN != 0) parts += ctx.getString(R.string.day_short_sun)
                parts.joinToString(" ")
            }

            ScheduleStore.Type.ONE_TIME -> {
                ctx.getString(
                    R.string.schedules_once_range_fmt,
                    s.startDate.toString(),
                    s.endDate.toString()
                )
            }
        }

        val (displayTitle, triggerSummary) = when {
            hasLocation -> {
                val label = s.locationLabel?.trim()?.takeIf { it.isNotBlank() } ?: run {
                    val lat = s.locationLat
                    val lng = s.locationLng
                    if (lat != null && lng != null) {
                        String.format(Locale.getDefault(), "%.5f, %.5f", lat, lng)
                    } else {
                        null
                    }
                }
                val t = if (hasExplicitTitle) customTitle else (label ?: ctx.getString(R.string.schedules_type_location))
                val trig = if (hasExplicitTitle) {
                    ctx.getString(R.string.schedules_conn_location_fmt, "${label ?: "-"} · ${s.locationRadiusMeters}m")
                } else {
                    "${ctx.getString(R.string.schedules_type_location)} (${s.locationRadiusMeters}m)"
                }
                t to trig
            }
            hasWifi && hasBt -> {
                val wifiName = s.wifiSsid.orEmpty()
                val btName = (s.btDeviceName ?: s.btDeviceAddress).orEmpty()
                val t = if (hasExplicitTitle) customTitle else "$wifiName + $btName"
                val trig = ctx.getString(R.string.schedules_conn_wifi_bt_fmt, wifiName, btName)
                t to trig
            }
            hasWifi -> {
                val wifiName = s.wifiSsid?.trim().orEmpty()
                val t = if (hasExplicitTitle) customTitle else wifiName.ifBlank { ctx.getString(R.string.schedules_type_wifi) }
                val trig = if (hasExplicitTitle) ctx.getString(R.string.schedules_conn_wifi_fmt, wifiName) else ctx.getString(R.string.schedules_type_wifi)
                t to trig
            }
            hasBt -> {
                val btName = (s.btDeviceName ?: s.btDeviceAddress)?.trim().orEmpty()
                val t = if (hasExplicitTitle) customTitle else btName.ifBlank { ctx.getString(R.string.schedules_type_bt) }
                val trig = if (hasExplicitTitle) ctx.getString(R.string.schedules_conn_bt_fmt, btName) else ctx.getString(R.string.schedules_type_bt)
                t to trig
            }
            else -> {
                val timeStr = timeWindowStr ?: if (s.startMinutes > 0) fmtMinutes(s.startMinutes) else ctx.getString(R.string.schedules_conn_time_all_day)
                val t = if (hasExplicitTitle) customTitle else timeStr
                val trig = if (hasExplicitTitle) timeStr else null
                t to trig
            }
        }

        title.text = displayTitle

        val subtitleParts = mutableListOf<String>()
        if (daysLabel.isNotBlank()) {
            subtitleParts += daysLabel
        }
        if (triggerSummary != null) {
            subtitleParts += triggerSummary
        }
        if (timeWindowStr != null && (hasWifi || hasBt || hasLocation)) {
            subtitleParts += timeWindowStr
        }
        subtitleParts += actionLabel
        val activeTargetProfile = getTargetProfile()
        if (activeTargetProfile == null && s.profile.isNotBlank()) {
            subtitleParts += s.profile
        }
        subtitle.text = subtitleParts.joinToString(" · ")

        if (s.note.isNotBlank()) {
            note.visibility = View.VISIBLE
            note.text = s.note
        } else {
            note.visibility = View.GONE
        }
    }
}
