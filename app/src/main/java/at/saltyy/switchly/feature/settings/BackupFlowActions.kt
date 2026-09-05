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

package at.saltyy.switchly.feature.settings

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import at.saltyy.switchly.BuildConfig
import at.saltyy.switchly.R
import at.saltyy.switchly.auth.Auth
import at.saltyy.switchly.data.prefs.ActivityHistoryLogStore
import at.saltyy.switchly.data.statistics.StatsPersistence
import at.saltyy.switchly.data.sync.BackupCategory
import at.saltyy.switchly.data.sync.BackupCategoryFilter
import at.saltyy.switchly.data.sync.BackupSelection
import at.saltyy.switchly.data.sync.BackupSelectionStore
import at.saltyy.switchly.data.sync.CloudSyncRuntime
import at.saltyy.switchly.data.sync.FileBackupRuntime
import at.saltyy.switchly.theme.AccentColor
import at.saltyy.switchly.ui.MainActivity
import at.saltyy.switchly.ui.dialog.SwitchlyDialogOption
import at.saltyy.switchly.ui.dialog.showDestructiveAccented
import at.saltyy.switchly.ui.dialog.showSwitchlyMultiChoiceDialog
import at.saltyy.switchly.ui.dialog.showSwitchlyOptionDialog
import at.saltyy.switchly.ui.dialog.styleSwitchlyDialogButtons
import at.saltyy.switchly.util.SwitchlyAppAccessGuard
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup / restore / local-wipe flows shared by Settings and Account.
 * Extracted verbatim from SettingsFragment so both hubs run identical logic.
 *
 * Owns its activity-result launchers: instantiate as an activity property
 * (before onCreate finishes) so registration happens before STARTED.
 */
class BackupFlowActions(private val activity: AppCompatActivity) {

    /** Invoked after a successful backup so the host can refresh its own summaries. */
    var onLibraryChanged: (() -> Unit)? = null

    private var pendingFileBackupSelection: BackupSelection? = null
    // ------------------------------------------------------------------
    // Public entry points (Settings prefs and the Account hub).
    // ------------------------------------------------------------------

    fun isCloudAvailable(): Boolean =
        BuildConfig.SWITCHLY_FIREBASE_ENABLED && Auth.uid() != null

    /** Backup / restore option sheet for the Account hub (mirrors the Settings rows). */
    fun showBackupOptions() {
        if (isRestricted()) {
            Toast.makeText(activity, R.string.settings_restricted_action_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val options = mutableListOf<SwitchlyDialogOption>()
        val actions = mutableListOf<() -> Unit>()
        if (isCloudAvailable()) {
            options += SwitchlyDialogOption(
                title = activity.getString(R.string.pref_cloud_backup_title),
                summary = activity.getString(R.string.pref_cloud_backup_summary),
                iconRes = R.drawable.cloud_upload_24,
            )
            actions += ::cloudBackup
            options += SwitchlyDialogOption(
                title = activity.getString(R.string.pref_cloud_restore_title),
                summary = activity.getString(R.string.pref_cloud_restore_summary),
                iconRes = R.drawable.cloud_download_24,
            )
            actions += ::cloudRestore
        }
        options += SwitchlyDialogOption(
            title = activity.getString(R.string.pref_file_backup_title),
            summary = activity.getString(R.string.pref_file_backup_summary),
            iconRes = R.drawable.folder_24,
        )
        actions += ::fileBackup
        options += SwitchlyDialogOption(
            title = activity.getString(R.string.pref_file_restore_title),
            summary = activity.getString(R.string.pref_file_restore_summary),
            iconRes = R.drawable.description_24,
        )
        actions += ::fileRestore
        activity.showSwitchlyOptionDialog(
            title = activity.getString(R.string.settings_restore_title),
            options = options,
            compact = false,
            showCancelButton = true,
            widthFraction = 0.94f,
        ) { index -> actions.getOrNull(index)?.invoke() }
    }

    fun cloudBackup() {
        showBackupSelectionFlow { selection ->
            confirmAction(
                title = activity.getString(R.string.settings_confirm_backup_title),
                message = backupConfirmMessage(
                    selection = selection,
                    fullMessageRes = R.string.settings_confirm_backup_message_with_categories,
                    includedOnlyMessageRes = R.string.settings_confirm_backup_message_with_included_categories,
                ),
                positiveText = activity.getString(R.string.settings_confirm_backup_title),
            ) {
                val backupCtx = activity
                val loadingDialog = showProgressDialog(
                    backupCtx,
                    R.string.pref_cloud_backup_title,
                    R.string.cloud_backup_loading,
                )
                val startBackup = {
                    CloudSyncRuntime.pushLocalState(backupCtx, selection) { ok, err ->
                        if (!alive()) return@pushLocalState
                        if (loadingDialog.isShowing) loadingDialog.dismiss()
                        val msg = if (ok) {
                            PreferenceManager.getDefaultSharedPreferences(backupCtx).edit {
                                putLong("pref_last_backup_epoch_ms", System.currentTimeMillis())
                            }
                            onLibraryChanged?.invoke()
                            if (err.isNullOrBlank()) {
                                activity.getString(R.string.cloud_backup_ok)
                            } else {
                                activity.getString(R.string.cloud_backup_ok_cleanup_warning)
                            }
                        } else {
                            activity.getString(R.string.cloud_error_fmt, err ?: activity.getString(R.string.error_unknown))
                        }
                        Toast.makeText(backupCtx, msg, Toast.LENGTH_SHORT).show()
                    }
                }
                if (activity.window?.decorView?.post { startBackup() } != true) startBackup()
            }
        }
    }

    fun cloudRestore() {
        if (isRestricted()) {
            Toast.makeText(activity, R.string.settings_restricted_action_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        confirmAction(
            title = activity.getString(R.string.settings_confirm_restore_title),
            message = activity.getString(R.string.settings_confirm_restore_message),
            positiveText = activity.getString(R.string.settings_confirm_restore_title),
        ) {
            startRestoreFlowWithChoice()
        }
    }

    fun fileBackup() {
        showBackupSelectionFlow { selection ->
            confirmAction(
                title = activity.getString(R.string.settings_confirm_file_backup_title),
                message = backupConfirmMessage(
                    selection = selection,
                    fullMessageRes = R.string.settings_confirm_file_backup_message_with_categories,
                    includedOnlyMessageRes = R.string.settings_confirm_file_backup_message_with_included_categories,
                ),
                positiveText = activity.getString(R.string.settings_confirm_file_backup_title),
            ) {
                pendingFileBackupSelection = selection
                createBackupFileLauncher.launch(defaultBackupFileName())
            }
        }
    }

    fun fileRestore() {
        if (isRestricted()) {
            Toast.makeText(activity, R.string.settings_restricted_action_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        confirmAction(
            title = activity.getString(R.string.settings_confirm_file_restore_title),
            message = activity.getString(R.string.settings_confirm_file_restore_message),
            positiveText = activity.getString(R.string.settings_confirm_file_restore_title),
        ) {
            restoreBackupFileLauncher.launch(
                arrayOf("application/json", "text/json", "text/plain", "*/*"),
            )
        }
    }

    fun confirmReset() {
        if (isRestricted()) {
            Toast.makeText(activity, R.string.settings_restricted_action_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        showResetAllDataDialog()
    }

    private fun alive(): Boolean = !activity.isFinishing && !activity.isDestroyed

    private fun isRestricted(): Boolean = SwitchlyAppAccessGuard.isLocked(activity)

    private val createBackupFileLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) {
            pendingFileBackupSelection = null
            return@registerForActivityResult
        }
        writeBackupFile(uri)
    }

    private val restoreBackupFileLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        restoreBackupFile(uri)
    }

    private fun backupConfirmMessage(
        selection: BackupSelection,
        fullMessageRes: Int,
        includedOnlyMessageRes: Int,
    ): String {
        return if (selection.hasExcludedCategories()) {
            activity.getString(fullMessageRes, selection.includedNames(), selection.excludedNames())
        } else {
            activity.getString(includedOnlyMessageRes, selection.includedNames())
        }
    }

    private fun showBackupSelectionFlow(onSelected: (BackupSelection) -> Unit) {
        val ctx = activity
        val presets = listOf(
            Triple(activity.getString(R.string.backup_preset_full), activity.getString(R.string.backup_preset_full_summary), BackupSelection.full()),
            Triple(activity.getString(R.string.backup_preset_privacy), activity.getString(R.string.backup_preset_privacy_summary), BackupSelection.privacyFocused()),
            Triple(activity.getString(R.string.backup_preset_profiles_only), activity.getString(R.string.backup_preset_profiles_only_summary), BackupSelection.profilesOnly()),
            Triple(activity.getString(R.string.backup_preset_manual_custom), activity.getString(R.string.backup_preset_manual_custom_summary), BackupSelectionStore.load(ctx)),
        )

        ctx.showSwitchlyOptionDialog(
            title = activity.getString(R.string.backup_select_preset_title),
            options = presets.mapIndexed { index, preset ->
                SwitchlyDialogOption(
                    title = preset.first,
                    summary = preset.second,
                    iconRes = when (index) {
                        0 -> R.drawable.cloud_upload_24
                        1 -> R.drawable.security_24
                        2 -> R.drawable.switch_account_24
                        else -> R.drawable.tune_24
                    },
                )
            },
            compact = false,
            showCancelButton = true,
            widthFraction = 0.94f,
        ) { index ->
            showBackupCategoryDialog(presets[index].third, onSelected)
        }
    }

    private fun showBackupCategoryDialog(initial: BackupSelection, onSelected: (BackupSelection) -> Unit) {
        val ctx = activity
        val categories = BackupCategory.values()
        val checked = categories.map { it.id in initial.categoryIds }.toBooleanArray()
        val options = categories.map { category ->
            val suffix = if (category.sensitive) activity.getString(R.string.backup_category_sensitive_suffix) else ""
            SwitchlyDialogOption(
                title = "${category.displayName}$suffix",
                summary = category.description,
                iconRes = backupCategoryIconRes(category)
            )
        }

        ctx.showSwitchlyMultiChoiceDialog(
            title = activity.getString(R.string.backup_select_categories_title),
            options = options,
            checked = checked,
            positiveTextRes = R.string.backup_create_with_selection,
            compact = false,
            widthFraction = 0.94f,
        ) { states ->
            val selected = categories
                .filterIndexed { index, _ -> states.getOrNull(index) == true }
                .map { it.id }
                .toSet()
            val selection = BackupSelection.fromIds(selected)
            if (selection.categoryIds.isEmpty()) {
                Toast.makeText(ctx, activity.getString(R.string.backup_select_at_least_one), Toast.LENGTH_SHORT).show()
                return@showSwitchlyMultiChoiceDialog
            }
            BackupSelectionStore.save(ctx, selection)
            onSelected(selection)
        }
    }

    private fun backupCategoryIconRes(category: BackupCategory): Int = when (category) {
        BackupCategory.PROFILES -> R.drawable.switch_account_24
        BackupCategory.BLOCKED_APPS -> R.drawable.apps_24
        BackupCategory.WEBSITE_RULES -> R.drawable.language_24
        BackupCategory.WEBSITE_BROWSER_SETTINGS -> R.drawable.language_24
        BackupCategory.NOTIFICATION_BLOCKING -> R.drawable.notifications_24
        BackupCategory.IN_APP_BLOCKING -> R.drawable.app_blocking_black_24
        BackupCategory.SCHEDULES -> R.drawable.schedule_24
        BackupCategory.LOCATION_SCHEDULES -> R.drawable.location_on_24
        BackupCategory.WIFI_SCHEDULES -> R.drawable.wifi_24
        BackupCategory.BLUETOOTH_SCHEDULES -> R.drawable.bluetooth_24
        BackupCategory.KEYS -> R.drawable.nfc_24
        BackupCategory.CONTROL_SETTINGS -> R.drawable.tune_24
        BackupCategory.STRICT_PROTECTION -> R.drawable.lock_24
        BackupCategory.STATISTICS -> R.drawable.bar_chart_24
        BackupCategory.APP_PREFERENCES -> R.drawable.account_box_24
    }

    private fun defaultBackupFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "switchly-backup-$stamp.json"
    }

    private fun writeBackupFile(uri: Uri) {
        val activeCtx = activity
        val selection = pendingFileBackupSelection ?: BackupSelectionStore.load(activeCtx)
        pendingFileBackupSelection = null
        val loadingDialog = showProgressDialog(
            activeCtx,
            R.string.settings_confirm_file_backup_title,
            R.string.file_backup_loading
        )
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                FileBackupRuntime.writeLocalBackupToUri(activeCtx, uri, selection)
            }
            if (!alive()) return@launch
            if (loadingDialog.isShowing) loadingDialog.dismiss()
            val msg = result.fold(
                onSuccess = {
                    PreferenceManager.getDefaultSharedPreferences(activeCtx).edit {
                        putLong("pref_last_backup_epoch_ms", System.currentTimeMillis())
                    }
                    onLibraryChanged?.invoke()
                    activity.getString(R.string.file_backup_ok)
                },
                onFailure = { e ->
                    activity.getString(R.string.file_backup_error_fmt, e.localizedMessage ?: activity.getString(R.string.error_unknown))
                }
            )
            Toast.makeText(activeCtx, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreBackupFile(uri: Uri) {
        val activeCtx = activity
        val loadingDialog = showProgressDialog(
            activeCtx,
            R.string.settings_confirm_file_restore_title,
            R.string.file_restore_loading,
        )
        activity.lifecycleScope.launch {
            val payloadResult = withContext(Dispatchers.IO) {
                FileBackupRuntime.readBackupPayloadFromUri(activeCtx, uri)
            }
            if (loadingDialog.isShowing) {
                loadingDialog.dismiss()
            }
            if (!alive()) {
                return@launch
            }
            payloadResult
                .onFailure { error ->
                    Toast.makeText(
                        activeCtx,
                        activity.getString(
                            R.string.file_restore_error_fmt,
                            error.localizedMessage ?: activity.getString(R.string.error_unknown),
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                .onSuccess { payload ->
                    showRestoreSelectionDialog(activeCtx, payload) { selectedPayload ->
                        showBackupCompatibilityWarningIfNeeded(activeCtx, selectedPayload) {
                            val restoreDialog = showProgressDialog(
                                activeCtx,
                                R.string.settings_confirm_file_restore_title,
                                R.string.restore_applying,
                            )
                            activity.lifecycleScope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    FileBackupRuntime.restoreBackupPayload(activeCtx, selectedPayload)
                                }
                                if (restoreDialog.isShowing) {
                                    restoreDialog.dismiss()
                                }
                                if (!alive()) {
                                    return@launch
                                }
                                val message = result.fold(
                                    onSuccess = { activity.getString(R.string.file_restore_ok_restart) },
                                    onFailure = { error ->
                                        activity.getString(
                                            R.string.file_restore_error_fmt,
                                            error.localizedMessage ?: activity.getString(R.string.error_unknown),
                                        )
                                    },
                                )
                                Toast.makeText(activeCtx, message, Toast.LENGTH_SHORT).show()
                                if (result.isSuccess) {
                                    restartAppTask()
                                }
                            }
                        }
                    }
                }
        }
    }

    private fun startRestoreFlowWithChoice() {
        val initialCtx = activity
        val loadingDialog = showProgressDialog(
            initialCtx,
            R.string.pref_cloud_restore_title,
            R.string.cloud_restore_loading,
        )

        CloudSyncRuntime.listBackups(initialCtx) { ok, err, backups ->
            val activeCtx = activity
            if (!alive()) return@listBackups
            if (loadingDialog.isShowing) loadingDialog.dismiss()
            if (!ok) {
                Toast.makeText(
                    activeCtx,
                    activity.getString(R.string.cloud_error_fmt, err ?: activity.getString(R.string.error_unknown)),
                    Toast.LENGTH_SHORT
                ).show()
                return@listBackups
            }

            val list = backups ?: emptyList()
            if (list.isEmpty()) {
                val restoreDialog = showProgressDialog(
                    activeCtx,
                    R.string.pref_cloud_restore_title,
                    R.string.restore_applying,
                )
                CloudSyncRuntime.pullRemoteState(activeCtx) { ok2, err2 ->
                    if (restoreDialog.isShowing) {
                        restoreDialog.dismiss()
                    }
                    val restoreCtx = activity
                    if (!alive()) return@pullRemoteState
                    if (ok2) {
                        Toast.makeText(restoreCtx, activity.getString(R.string.cloud_restore_ok_restart), Toast.LENGTH_SHORT).show()
                        restartAppTask()
                    } else {
                        Toast.makeText(
                            restoreCtx,
                            activity.getString(R.string.cloud_error_fmt, err2 ?: activity.getString(R.string.error_unknown)),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                return@listBackups
            }

            val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            val labels = list.map { meta -> df.format(Date(meta.createdAt)) }.toTypedArray()

            activeCtx.showSwitchlyOptionDialog(
                title = activity.getString(R.string.settings_restore_choose_title),
                options = labels.map { SwitchlyDialogOption(title = it) }
            ) { which ->
                val meta = list[which]
                val payloadDialog = showProgressDialog(
                    activeCtx,
                    R.string.pref_cloud_restore_title,
                    R.string.cloud_restore_loading,
                )
                CloudSyncRuntime.loadBackupPayload(activeCtx, meta.id) { ok3, err3, payload ->
                    if (payloadDialog.isShowing) {
                        payloadDialog.dismiss()
                    }
                    val restoreCtx = activity
                    if (!alive()) return@loadBackupPayload
                    if (!ok3 || payload == null) {
                        Toast.makeText(
                            restoreCtx,
                            activity.getString(R.string.cloud_error_fmt, err3 ?: activity.getString(R.string.error_unknown)),
                            Toast.LENGTH_SHORT
                        ).show()
                        return@loadBackupPayload
                    }

                    showRestoreSelectionDialog(restoreCtx, payload) { selectedPayload ->
                        showBackupCompatibilityWarningIfNeeded(restoreCtx, selectedPayload) {
                            val restoreDialog = showProgressDialog(
                                restoreCtx,
                                R.string.settings_confirm_restore_title,
                                R.string.restore_applying,
                            )
                            CloudSyncRuntime.applyBackupPayloadAsync(restoreCtx, selectedPayload) { result ->
                                if (restoreDialog.isShowing) {
                                    restoreDialog.dismiss()
                                }
                                if (!alive()) {
                                    return@applyBackupPayloadAsync
                                }
                                result.fold(
                                    onSuccess = {
                                        Toast.makeText(
                                            restoreCtx,
                                            activity.getString(R.string.cloud_restore_ok_restart),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        restartAppTask()
                                    },
                                    onFailure = { error ->
                                        Toast.makeText(
                                            restoreCtx,
                                            activity.getString(
                                                R.string.cloud_error_fmt,
                                                error.localizedMessage ?: activity.getString(R.string.error_unknown),
                                            ),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showBackupCompatibilityWarningIfNeeded(
        ctx: Context,
        payload: Map<*, *>,
        onContinue: () -> Unit,
    ) {
        val compatibility = CloudSyncRuntime.inspectBackupCompatibility(payload)
        if (!compatibility.shouldWarn) {
            onContinue()
            return
        }

        val versionLine = compatibility.createdWithVersion?.let { version ->
            activity.getString(R.string.restore_compatibility_version_fmt, version)
        } ?: activity.getString(R.string.restore_compatibility_version_unknown)
        val message = buildString {
            append(versionLine)
            append("\n\n")
            append(activity.getString(R.string.restore_compatibility_warning_body))
            if (compatibility.legacyStatistics) {
                append("\n\n")
                append(activity.getString(R.string.restore_compatibility_warning_statistics))
            }
        }

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.restore_compatibility_warning_title)
            .setMessage(message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.settings_confirm_restore_apply) { _, _ -> onContinue() }
            .create()
        dialog.setOnShowListener { dialog.styleSwitchlyDialogButtons() }
        dialog.show()
    }

    private fun showRestoreSelectionDialog(
        ctx: Context,
        payload: Map<*, *>,
        onConfirm: (Map<*, *>) -> Unit
    ) {
        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.restore_contents_preview_title)
            .setMessage(buildRestoreContentsPreview(payload))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.restore_contents_preview_continue) { _, _ ->
                showRestoreSelectionChoices(ctx, payload, onConfirm)
            }
            .create()
        dialog.setOnShowListener { dialog.styleSwitchlyDialogButtons() }
        dialog.show()
    }

    private fun buildRestoreContentsPreview(payload: Map<*, *>): String {
        fun mapAt(key: String): Map<*, *> = payload[key] as? Map<*, *> ?: emptyMap<Any, Any>()
        fun valueCount(value: Any?): Int = when (value) {
            is Collection<*> -> value.size
            is Array<*> -> value.size
            else -> 0
        }
        val internalPrefs = mapAt("switchly_prefs")
        val defaultPrefs = mapAt("prefs")
        val schedulePrefs = mapAt("schedules_prefs")
        val profileCount = valueCount(internalPrefs["profiles"])
        val appRuleCount = internalPrefs.entries.filter { (key, _) ->
            val name = key?.toString().orEmpty()
            name.startsWith("blocked_apps_") || name.startsWith("allowed_apps_")
        }.sumOf { (_, value) -> valueCount(value) }
        val websiteRuleCount = defaultPrefs.entries.filter { (key, _) ->
            val name = key?.toString().orEmpty()
            name.startsWith("domain_block_domains__p__") || name.startsWith("domain_allowed_domains__p__")
        }.sumOf { (_, value) -> valueCount(value) }
        val scheduleCount = runCatching {
            JSONArray(schedulePrefs["items"]?.toString().orEmpty()).length()
        }.getOrDefault(0)
        val included = BackupCategoryFilter.includedCategoryIdsFromPayload(payload)
        val statisticsIncluded = included == null || BackupCategory.STATISTICS.id in included
        return activity.getString(
            R.string.restore_contents_preview_body,
            profileCount,
            appRuleCount,
            websiteRuleCount,
            scheduleCount,
            activity.getString(if (statisticsIncluded) R.string.restore_contents_statistics_included else R.string.restore_contents_statistics_not_included),
        )
    }

    private fun showRestoreSelectionChoices(
        ctx: Context,
        payload: Map<*, *>,
        onConfirm: (Map<*, *>) -> Unit
    ) {
        val includedIds = BackupCategoryFilter.includedCategoryIdsFromPayload(payload)
        val categories = BackupCategory.values()
            .filter { category -> includedIds == null || category.id in includedIds }
            .ifEmpty { BackupCategory.values().toList() }
        val checked = categories.map { true }.toBooleanArray()
        val options = categories.map { category ->
            SwitchlyDialogOption(
                title = category.displayName,
                summary = category.description,
                iconRes = backupCategoryIconRes(category)
            )
        }

        ctx.showSwitchlyMultiChoiceDialog(
            title = activity.getString(R.string.restore_select_categories_title),
            options = options,
            checked = checked,
            positiveTextRes = R.string.settings_confirm_restore_apply,
            compact = false,
            widthFraction = 0.94f,
        ) { states ->
            val selected = categories
                .filterIndexed { index, _ -> states.getOrNull(index) == true }
                .map { it.id }
                .toSet()
            val selection = BackupSelection.fromIds(selected)
            if (selection.categoryIds.isEmpty()) {
                Toast.makeText(ctx, activity.getString(R.string.restore_select_at_least_one), Toast.LENGTH_SHORT).show()
                return@showSwitchlyMultiChoiceDialog
            }
            onConfirm(BackupCategoryFilter.filterPayloadForRestore(payload, selection))
        }
    }

    private fun showProgressDialog(ctx: Context, titleRes: Int, messageRes: Int): AlertDialog {
        val content = LayoutInflater.from(ctx).inflate(R.layout.dialog_progress, null, false)
        content.findViewById<TextView>(R.id.progressMessage).setText(messageRes)
        content.findViewById<CircularProgressIndicator>(R.id.progressIndicator)
            .setIndicatorColor(AccentColor.getAccentColorInt(ctx))

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(titleRes)
            .setView(content)
            .setCancelable(false)
            .create()

        dialog.show()
        return dialog
    }

    private fun confirmAction(title: String, message: String, positiveText: String, onConfirm: () -> Unit) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveText) { _, _ -> onConfirm() }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener { dialog.styleSwitchlyDialogButtons() }
        dialog.show()
    }

    private fun restartAppTask() {
        val i = Intent(activity, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        activity.startActivity(i)
        activity.finish()
    }

    private fun showResetAllDataDialog() {
        val ctx = activity
        MaterialAlertDialogBuilder(ctx)
            .setTitle(activity.getString(R.string.pref_reset_app_data_confirm_title))
            .setMessage(activity.getString(R.string.pref_reset_app_data_confirm_message) + "\n\n" + activity.getString(R.string.destructive_cannot_be_undone))
            .setPositiveButton(activity.getString(R.string.delete)) { _, _ ->
                resetAllAppDataNow()
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .showDestructiveAccented()
    }

    private fun resetAllAppDataNow() {
        val ctx = activity
        val ok = runCatching {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.clearApplicationUserData()
        }.getOrDefault(false)

        if (!ok) {
            // Fallback for OEMs where clearApplicationUserData may fail silently.
            runCatching {
                StatsPersistence.prepareForFullDataDeletion(ctx)
                try {
                    PreferenceManager.getDefaultSharedPreferences(ctx).edit(commit = true) { clear() }
                    ctx.getSharedPreferences("switchly_prefs", Context.MODE_PRIVATE).edit(commit = true) { clear() }
                    ctx.getSharedPreferences("switchly_prefs_schedules", Context.MODE_PRIVATE).edit(commit = true) { clear() }
                    ctx.getSharedPreferences("switchly_ui_hints", Context.MODE_PRIVATE).edit(commit = true) { clear() }
                    ctx.getSharedPreferences(ActivityHistoryLogStore.PREFS_NAME, Context.MODE_PRIVATE).edit(commit = true) { clear() }
                    ctx.databaseList().forEach { databaseName ->
                        ctx.deleteDatabase(databaseName)
                    }
                    ctx.cacheDir?.deleteRecursively()
                    ctx.filesDir?.listFiles()?.forEach { it.deleteRecursively() }
                } finally {
                    StatsPersistence.resumeAfterFullDataDeletion(ctx)
                }
            }
            Toast.makeText(ctx, activity.getString(R.string.pref_reset_app_data_done), Toast.LENGTH_LONG).show()
            restartAppTask()
        }
    }



}
