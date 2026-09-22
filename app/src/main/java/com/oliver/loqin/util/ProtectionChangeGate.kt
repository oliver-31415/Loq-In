/*
 * Loq In
 * Copyright (C) 2026 Loq In Contributors
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

package com.oliver.loqin.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.oliver.loqin.blocking.BlockingRuntime
import com.oliver.loqin.data.prefs.AttemptLimitStore
import com.oliver.loqin.data.prefs.AppLogStore
import com.oliver.loqin.data.prefs.AutomationModeStore
import com.oliver.loqin.data.prefs.DiagnosticsTimelineStore
import com.oliver.loqin.data.prefs.DomainBlockStore
import com.oliver.loqin.data.prefs.DomainLimitStore
import com.oliver.loqin.data.prefs.EmergencyBypassStore
import com.oliver.loqin.data.prefs.InAppRuleStore
import com.oliver.loqin.data.prefs.LimitReachedStore
import com.oliver.loqin.data.prefs.OpenCountStore
import com.oliver.loqin.data.prefs.ProfileRuleModeStore
import com.oliver.loqin.data.prefs.ProfileStore
import com.oliver.loqin.data.prefs.SessionLimitStore
import com.oliver.loqin.data.prefs.SurfaceLimitStore
import com.oliver.loqin.data.prefs.SwitchModeStore
import com.oliver.loqin.data.prefs.UsageLimitResetStore
import com.oliver.loqin.data.prefs.UsageLimitStore
import com.oliver.loqin.data.prefs.UsageStore
import com.oliver.loqin.data.prefs.WebsiteRuleModeStore
import com.oliver.loqin.receiver.ProtectionChangeReceiver
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Central gate for changes that can affect active protection.
 *
 * Screens classify a requested change through [ProtectionChangePolicy] instead of implementing
 * their own lock semantics:
 * - stricter/neutral changes apply immediately;
 * - weakening changes are denied while protection is active unless a change delay is configured,
 *   in which case they are queued and applied by [ProtectionChangeReceiver];
 * - protected structural changes remain denied while protection is active.
 *
 * The pending queue is stored as deltas (never snapshots) so a stricter edit made while a change
 * waits for its delay is never overwritten.
 */
object ProtectionChangeGate {

    private const val PREF_DELAY_MINUTES = "protection_change_delay_minutes"
    private const val PREF_PENDING_JSON = "protection_pending_changes_json"
    private const val ALARM_REQUEST_CODE = 22901

    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    // ---------------------------------------------------------------------------------------------
    // Delay
    // ---------------------------------------------------------------------------------------------

    fun getDelayMinutes(context: Context): Int {
        val stored = prefs(context).getInt(PREF_DELAY_MINUTES, 0)
        return stored.takeIf { ProtectionChangePolicy.isValidDelayMinutes(it) } ?: 0
    }

    /** The delay itself is protection-sensitive: while locked it may only stay the same or grow. */
    fun setDelayMinutes(context: Context, requestedMinutes: Int): Boolean {
        val locked = EditingLockGuard.isLocked(context)
        if (!ProtectionChangePolicy.canSetDelayMinutes(locked, getDelayMinutes(context), requestedMinutes)) {
            return false
        }
        prefs(context).edit { putInt(PREF_DELAY_MINUTES, requestedMinutes) }
        return true
    }

    fun decision(context: Context, direction: ProtectionChangePolicy.Direction): ProtectionChangePolicy.Decision =
        ProtectionChangePolicy.decision(
            locked = EditingLockGuard.isLocked(context),
            delayMinutes = getDelayMinutes(context),
            direction = direction,
        )

    // ---------------------------------------------------------------------------------------------
    // App selection
    // ---------------------------------------------------------------------------------------------

    fun requestAppSelection(
        context: Context,
        profile: String,
        allowMode: Boolean,
        original: Set<String>,
        requested: Set<String>,
    ): ProtectionChangePolicy.Result {
        if (profile.isBlank()) return ProtectionChangePolicy.Result.DENIED
        val plan = ProtectionChangePolicy.planAppSelection(allowMode, original, requested)
        // Stricter packages cancel any pending weakening change for the same packages.
        if (plan.applyNow.isNotEmpty()) {
            prunePending(context) { pending ->
                PendingChangeQueue.pruneAppSelection(pending, profile, allowMode, plan.applyNow)
            }
        }
        return when (decision(context, plan.direction)) {
            ProtectionChangePolicy.Decision.APPLY_NOW -> {
                applySelection(context, profile, allowMode, plan.applyNow, remove = allowMode)
                ProtectionChangePolicy.Result.APPLIED
            }

            ProtectionChangePolicy.Decision.QUEUE_DELAYED -> {
                applySelection(context, profile, allowMode, plan.applyNow, remove = allowMode)
                if (plan.queued.isNotEmpty()) {
                    val data = JSONObject()
                        .put("profile", profile)
                        .put("allowMode", allowMode)
                    if (plan.action == ProtectionChangePolicy.SelectionAction.ADD) {
                        data.put("addPackages", JSONArray(plan.queued.sorted()))
                    } else {
                        data.put("removePackages", JSONArray(plan.queued.sorted()))
                    }
                    queue(context, PendingChangeType.APP_SELECTION, data)
                }
                ProtectionChangePolicy.Result.QUEUED
            }

            ProtectionChangePolicy.Decision.DENY -> ProtectionChangePolicy.Result.DENIED
        }
    }

    // ---------------------------------------------------------------------------------------------
    // In-app rules
    // ---------------------------------------------------------------------------------------------

    fun requestInAppSelection(
        context: Context,
        profile: String,
        packageName: String,
        baseKey: String,
        surfaceKey: String?,
        allowMode: Boolean,
        currentSelected: Boolean,
        requestedSelected: Boolean,
    ): ProtectionChangePolicy.Result {
        if (profile.isBlank() || baseKey.isBlank()) return ProtectionChangePolicy.Result.DENIED
        val direction = ProtectionChangePolicy.inAppDirection(allowMode, currentSelected, requestedSelected)
        return when (decision(context, direction)) {
            ProtectionChangePolicy.Decision.APPLY_NOW -> {
                applyInAppSelection(context, profile, packageName, baseKey, surfaceKey, allowMode, requestedSelected)
                if (direction == ProtectionChangePolicy.Direction.STRICTER) {
                    prunePending(context) { pending ->
                        PendingChangeQueue.pruneInAppSelections(pending, profile, setOf(baseKey))
                    }
                }
                ProtectionChangePolicy.Result.APPLIED
            }

            ProtectionChangePolicy.Decision.QUEUE_DELAYED -> {
                queue(
                    context = context,
                    type = PendingChangeType.IN_APP_SELECTION,
                    data = JSONObject()
                        .put("profile", profile)
                        .put("packageName", packageName)
                        .put("baseKey", baseKey)
                        .put("surfaceKey", surfaceKey.orEmpty())
                        .put("allowMode", allowMode)
                        .put("selected", requestedSelected),
                )
                ProtectionChangePolicy.Result.QUEUED
            }

            ProtectionChangePolicy.Decision.DENY -> ProtectionChangePolicy.Result.DENIED
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Website rules
    // ---------------------------------------------------------------------------------------------

    fun requestWebsiteRemoval(context: Context, profile: String, rule: String): ProtectionChangePolicy.Result {
        if (profile.isBlank() || rule.isBlank()) return ProtectionChangePolicy.Result.DENIED
        val allowMode = WebsiteRuleModeStore.isAllowMode(context, profile)
        val direction = ProtectionChangePolicy.websiteRemovalDirection(allowMode)
        return when (decision(context, direction)) {
            ProtectionChangePolicy.Decision.APPLY_NOW -> {
                applyWebsiteRemoval(context, profile, rule)
                if (direction == ProtectionChangePolicy.Direction.STRICTER) {
                    prunePending(context) { pending ->
                        PendingChangeQueue.pruneWebsiteEnabled(pending, profile, setOf(rule))
                    }
                }
                ProtectionChangePolicy.Result.APPLIED
            }

            ProtectionChangePolicy.Decision.QUEUE_DELAYED -> {
                queue(
                    context = context,
                    type = PendingChangeType.WEBSITE_REMOVE,
                    data = JSONObject().put("profile", profile).put("rule", rule),
                )
                ProtectionChangePolicy.Result.QUEUED
            }

            ProtectionChangePolicy.Decision.DENY -> ProtectionChangePolicy.Result.DENIED
        }
    }

    fun requestWebsiteEnabled(
        context: Context,
        profile: String,
        rule: String,
        currentEnabled: Boolean,
        requestedEnabled: Boolean,
    ): ProtectionChangePolicy.Result {
        if (profile.isBlank() || rule.isBlank()) return ProtectionChangePolicy.Result.DENIED
        if (currentEnabled == requestedEnabled) return ProtectionChangePolicy.Result.APPLIED
        val allowMode = WebsiteRuleModeStore.isAllowMode(context, profile)
        val direction = ProtectionChangePolicy.websiteEnabledDirection(allowMode, requestedEnabled)
        return when (decision(context, direction)) {
            ProtectionChangePolicy.Decision.APPLY_NOW -> {
                DomainBlockStore.setDomainEnabledForProfile(context, profile, rule, requestedEnabled)
                if (direction == ProtectionChangePolicy.Direction.STRICTER) {
                    prunePending(context) { pending ->
                        PendingChangeQueue.pruneWebsiteEnabled(pending, profile, setOf(rule))
                    }
                }
                ProtectionChangePolicy.Result.APPLIED
            }

            ProtectionChangePolicy.Decision.QUEUE_DELAYED -> {
                queue(
                    context = context,
                    type = PendingChangeType.WEBSITE_ENABLED,
                    data = JSONObject()
                        .put("profile", profile)
                        .put("rule", rule)
                        .put("enabled", requestedEnabled),
                )
                ProtectionChangePolicy.Result.QUEUED
            }

            ProtectionChangePolicy.Decision.DENY -> ProtectionChangePolicy.Result.DENIED
        }
    }

    // ---------------------------------------------------------------------------------------------
    // App limits
    // ---------------------------------------------------------------------------------------------

    fun requestAppLimits(
        context: Context,
        profile: String,
        packageName: String,
        requestedTimeMinutes: Int,
        requestedAttempts: Int,
        requestedPerVisitMinutes: Int,
        requestedResetMode: String,
    ): ProtectionChangePolicy.Result {
        if (profile.isBlank() || packageName.isBlank()) return ProtectionChangePolicy.Result.DENIED

        val oldTime = UsageLimitStore.getLimitMinutes(context, profile, packageName)
        val oldAttempts = AttemptLimitStore.getLimitAttempts(context, profile, packageName)
        val oldPerVisit = SessionLimitStore.getLimitMinutes(context, profile, packageName)
        val newTime = requestedTimeMinutes.coerceAtLeast(0)
        val newAttempts = requestedAttempts.coerceAtLeast(0)
        val newPerVisit = requestedPerVisitMinutes.coerceAtLeast(0)

        val allowMode = ProfileRuleModeStore.isAllowMode(context, profile)
        val selected = packageName in ProfileStore.getSelectedForProfileMode(context, profile)
        val currentHasLimit = oldTime > 0 || oldAttempts > 0 || oldPerVisit > 0
        val requestedHasLimit = newTime > 0 || newAttempts > 0 || newPerVisit > 0

        // Crossing the hard-block -> limited boundary changes what the limit means, so the whole
        // edit is treated as one change instead of the per-component split.
        if (currentHasLimit != requestedHasLimit) {
            val direction = ProtectionChangePolicy.appLimitSetDirection(
                allowMode = allowMode,
                selected = selected,
                currentHasLimit = currentHasLimit,
                requestedHasLimit = requestedHasLimit,
                componentDirection = ProtectionChangePolicy.Direction.NEUTRAL,
            )
            return when (decision(context, direction)) {
                ProtectionChangePolicy.Decision.APPLY_NOW -> {
                    applyLimitValues(
                        context, profile, packageName,
                        timeMinutes = newTime,
                        attempts = newAttempts,
                        perVisitMinutes = newPerVisit,
                        resetMode = requestedResetMode,
                    )
                    ProtectionChangePolicy.Result.APPLIED
                }

                ProtectionChangePolicy.Decision.DENY -> ProtectionChangePolicy.Result.DENIED

                ProtectionChangePolicy.Decision.QUEUE_DELAYED -> {
                    val data = JSONObject()
                        .put("profile", profile)
                        .put("packageName", packageName)
                        .put("fromTime", oldTime).put("toTime", newTime)
                        .put("fromAttempts", oldAttempts).put("toAttempts", newAttempts)
                        .put("fromPerVisit", oldPerVisit).put("toPerVisit", newPerVisit)
                    if (newTime > 0) {
                        data.put("fromReset", UsageLimitResetStore.getMode(context, profile, packageName))
                            .put("toReset", requestedResetMode)
                    }
                    queue(context, PendingChangeType.APP_LIMITS, data)
                    ProtectionChangePolicy.Result.QUEUED
                }
            }
        }

        val plan = ProtectionChangePolicy.planAppLimits(
            currentTimeMinutes = oldTime,
            requestedTimeMinutes = newTime,
            currentAttempts = oldAttempts,
            requestedAttempts = newAttempts,
            currentPerVisitMinutes = oldPerVisit,
            requestedPerVisitMinutes = newPerVisit,
            currentResetMode = UsageLimitResetStore.getMode(context, profile, packageName),
            requestedResetMode = requestedResetMode,
        )
        if (plan.direction == ProtectionChangePolicy.Direction.NEUTRAL) {
            return ProtectionChangePolicy.Result.APPLIED
        }

        return when (decision(context, plan.direction)) {
            ProtectionChangePolicy.Decision.APPLY_NOW -> {
                applyLimitValues(
                    context, profile, packageName,
                    timeMinutes = plan.applyNowTime,
                    attempts = plan.applyNowAttempts,
                    perVisitMinutes = plan.applyNowPerVisit,
                    resetMode = plan.applyNowResetMode,
                )
                ProtectionChangePolicy.Result.APPLIED
            }

            ProtectionChangePolicy.Decision.DENY -> ProtectionChangePolicy.Result.DENIED

            ProtectionChangePolicy.Decision.QUEUE_DELAYED -> {
                if (plan.hasImmediate) {
                    applyLimitValues(
                        context, profile, packageName,
                        timeMinutes = plan.applyNowTime,
                        attempts = plan.applyNowAttempts,
                        perVisitMinutes = plan.applyNowPerVisit,
                        resetMode = plan.applyNowResetMode,
                    )
                }
                if (plan.hasQueued) {
                    val data = JSONObject().put("profile", profile).put("packageName", packageName)
                    plan.queuedTime?.let { data.put("fromTime", it.from).put("toTime", it.to) }
                    plan.queuedAttempts?.let { data.put("fromAttempts", it.from).put("toAttempts", it.to) }
                    plan.queuedPerVisit?.let { data.put("fromPerVisit", it.from).put("toPerVisit", it.to) }
                    plan.queuedResetMode?.let { data.put("fromReset", it.from).put("toReset", it.to) }
                    queue(
                        context = context,
                        type = PendingChangeType.APP_LIMITS,
                        data = data,
                    )
                }
                ProtectionChangePolicy.Result.QUEUED
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Auto-block new apps
    // ---------------------------------------------------------------------------------------------

    /** Enabling auto-block adds protection (stricter); disabling it is a weakening change. */
    fun requestAutoBlockNewApps(context: Context, profile: String, enabled: Boolean): ProtectionChangePolicy.Result {
        if (profile.isBlank()) return ProtectionChangePolicy.Result.DENIED
        val direction = if (enabled) {
            ProtectionChangePolicy.Direction.STRICTER
        } else {
            ProtectionChangePolicy.Direction.WEAKER
        }
        return when (decision(context, direction)) {
            ProtectionChangePolicy.Decision.APPLY_NOW -> {
                applyAutoBlockNewApps(context, profile, enabled)
                if (enabled) {
                    prunePending(context) { pending ->
                        PendingChangeQueue.pruneAutoBlock(pending, profile)
                    }
                }
                ProtectionChangePolicy.Result.APPLIED
            }

            ProtectionChangePolicy.Decision.QUEUE_DELAYED -> {
                queue(
                    context = context,
                    type = PendingChangeType.AUTO_BLOCK_NEW_APPS,
                    data = JSONObject().put("profile", profile).put("enabled", enabled),
                )
                ProtectionChangePolicy.Result.QUEUED
            }

            ProtectionChangePolicy.Decision.DENY -> ProtectionChangePolicy.Result.DENIED
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Clearing limits / in-app rules for packages
    // ---------------------------------------------------------------------------------------------

    /**
     * Clears per-app limits and (optionally) in-app rules for [packages]. This is always treated as
     * a weakening change: removing a limit or rule loosens protection.
     */
    fun requestClearAppData(
        context: Context,
        profile: String,
        packages: Collection<String>,
        includeInAppRules: Boolean,
    ): ProtectionChangePolicy.Result {
        if (profile.isBlank() || packages.isEmpty()) return ProtectionChangePolicy.Result.APPLIED
        return when (decision(context, ProtectionChangePolicy.Direction.WEAKER)) {
            ProtectionChangePolicy.Decision.APPLY_NOW -> {
                applyClearAppData(context, profile, packages, includeInAppRules)
                ProtectionChangePolicy.Result.APPLIED
            }

            ProtectionChangePolicy.Decision.QUEUE_DELAYED -> {
                queue(
                    context = context,
                    type = PendingChangeType.CLEAR_APP_DATA,
                    data = JSONObject()
                        .put("profile", profile)
                        .put("packages", JSONArray(packages.toList().sorted()))
                        .put("includeInAppRules", includeInAppRules),
                )
                ProtectionChangePolicy.Result.QUEUED
            }

            ProtectionChangePolicy.Decision.DENY -> ProtectionChangePolicy.Result.DENIED
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Website limits
    // ---------------------------------------------------------------------------------------------

    /**
     * The website limit editor can switch a rule between "block always" and a time limit.
     * Adding protection applies immediately; reducing it queues (or is denied with delay 0).
     */
    fun requestWebsiteLimit(
        context: Context,
        profile: String,
        rule: String,
        currentlyAlwaysBlocked: Boolean,
        currentMinutes: Int,
        requestedAlwaysBlock: Boolean,
        requestedMinutes: Int,
    ): ProtectionChangePolicy.Result {
        if (profile.isBlank() || rule.isBlank()) return ProtectionChangePolicy.Result.DENIED
        val allowMode = WebsiteRuleModeStore.isAllowMode(context, profile)
        val direction = ProtectionChangePolicy.websiteLimitDirection(
            allowMode = allowMode,
            currentlyAlwaysBlocked = currentlyAlwaysBlocked,
            currentMinutes = currentMinutes,
            requestedAlwaysBlock = requestedAlwaysBlock,
            requestedMinutes = requestedMinutes,
        )
        if (direction == ProtectionChangePolicy.Direction.NEUTRAL) {
            return ProtectionChangePolicy.Result.APPLIED
        }
        return when (decision(context, direction)) {
            ProtectionChangePolicy.Decision.APPLY_NOW -> {
                applyWebsiteLimit(context, profile, rule, allowMode, requestedAlwaysBlock, requestedMinutes)
                ProtectionChangePolicy.Result.APPLIED
            }

            ProtectionChangePolicy.Decision.QUEUE_DELAYED -> {
                queue(
                    context = context,
                    type = PendingChangeType.WEBSITE_LIMIT,
                    data = JSONObject()
                        .put("profile", profile)
                        .put("rule", rule)
                        .put("allowMode", allowMode)
                        .put("fromAlwaysBlock", currentlyAlwaysBlocked)
                        .put("fromMinutes", currentMinutes)
                        .put("alwaysBlock", requestedAlwaysBlock)
                        .put("minutes", requestedMinutes),
                )
                ProtectionChangePolicy.Result.QUEUED
            }

            ProtectionChangePolicy.Decision.DENY -> ProtectionChangePolicy.Result.DENIED
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Control mode
    // ---------------------------------------------------------------------------------------------

    /**
     * Control-mode switches are structural because changing the selected control family can replace
     * one trusted disable path with another. They still require protection to be inactive and are
     * never delayed.
     */
    fun requestControlMode(context: Context, requestedMode: AutomationModeStore.Mode): ProtectionChangePolicy.Result {
        if (AutomationModeStore.getMode(context) == requestedMode) {
            return ProtectionChangePolicy.Result.APPLIED
        }
        return when (decision(context, ProtectionChangePolicy.Direction.PROTECTED_STRUCTURAL)) {
            ProtectionChangePolicy.Decision.APPLY_NOW -> {
                AutomationModeStore.setMode(context, requestedMode)
                ProtectionChangePolicy.Result.APPLIED
            }

            ProtectionChangePolicy.Decision.QUEUE_DELAYED,
            ProtectionChangePolicy.Decision.DENY -> ProtectionChangePolicy.Result.DENIED
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Pending queue
    // ---------------------------------------------------------------------------------------------

    fun pendingChanges(context: Context): List<PendingChange> =
        PendingChangeCodec.decode(prefs(context).getString(PREF_PENDING_JSON, null))

    fun pendingCount(context: Context): Int = pendingChanges(context).size

    /** Rule keys with a queued weakening in-app change: baseKey -> requested selected state. */
    fun pendingInAppSelections(context: Context, profile: String): Map<String, Boolean> {
        if (profile.isBlank()) return emptyMap()
        val out = linkedMapOf<String, Boolean>()
        pendingChanges(context).forEach { change ->
            if (change.type != PendingChangeType.IN_APP_SELECTION) return@forEach
            if (change.data.optString("profile") != profile) return@forEach
            val baseKey = change.data.optString("baseKey")
            if (baseKey.isNotBlank()) {
                out[baseKey] = change.data.optBoolean("selected", false)
            }
        }
        return out
    }

    /** Website rules with a queued enable/disable change: rule -> requested enabled state. */
    fun pendingWebsiteEnabled(context: Context, profile: String): Map<String, Boolean> {
        if (profile.isBlank()) return emptyMap()
        val out = linkedMapOf<String, Boolean>()
        pendingChanges(context).forEach { change ->
            if (change.type != PendingChangeType.WEBSITE_ENABLED) return@forEach
            if (change.data.optString("profile") != profile) return@forEach
            val rule = change.data.optString("rule")
            if (rule.isNotBlank()) {
                out[rule] = change.data.optBoolean("enabled", true)
            }
        }
        return out
    }

    /** Website rules with a queued removal for the given profile. */
    fun pendingWebsiteRemovals(context: Context, profile: String): Set<String> {
        if (profile.isBlank()) return emptySet()
        val out = linkedSetOf<String>()
        pendingChanges(context).forEach { change ->
            if (change.type != PendingChangeType.WEBSITE_REMOVE) return@forEach
            if (change.data.optString("profile") != profile) return@forEach
            change.data.optString("rule").takeIf { it.isNotBlank() }?.let(out::add)
        }
        return out
    }

    /**
     * Packages with a queued weakening app-selection change for the given profile and mode.
     * Block mode returns queued unblocks; allow mode returns queued additions.
     */
    fun pendingAppSelectionPackages(context: Context, profile: String, allowMode: Boolean): Set<String> {
        if (profile.isBlank()) return emptySet()
        val key = if (allowMode) "addPackages" else "removePackages"
        val out = linkedSetOf<String>()
        pendingChanges(context).forEach { change ->
            if (change.type != PendingChangeType.APP_SELECTION) return@forEach
            if (change.data.optString("profile") != profile) return@forEach
            if (change.data.optBoolean("allowMode") != allowMode) return@forEach
            out += stringSet(change.data.optJSONArray(key))
        }
        return out
    }

    fun cancelAllPending(context: Context) {
        prefs(context).edit { remove(PREF_PENDING_JSON) }
        cancelAlarm(context)
        recordEvent(context, "Pending changes discarded", "")
    }

    fun cancelPending(context: Context, id: String): Boolean {
        val all = pendingChanges(context)
        val remaining = PendingChangeQueue.removeById(all, id)
        if (remaining.size == all.size) return false
        savePending(context, remaining)
        scheduleNext(context, remaining)
        recordEvent(context, "Pending change discarded", "id=$id")
        return true
    }

    /**
     * Pending weakening changes may only be accepted early when Loq In is genuinely fully off.
     * Temporary disable/enable and Emergency Unlock are deliberately excluded so they cannot turn
     * an already-protected delayed change into an instant bypass.
     */
    fun canApplyPendingNow(context: Context): Boolean =
        !SwitchModeStore.isBaseEnabled(context) &&
            !SwitchModeStore.hasActiveTemporaryOverride(context) &&
            !EmergencyBypassStore.isActive(context) &&
            !EmergencyBypassStore.isPaused(context)

    fun applyPendingNow(context: Context, id: String): Boolean {
        if (!canApplyPendingNow(context)) return false
        val all = pendingChanges(context)
        val change = all.firstOrNull { it.id == id } ?: return false
        if (!applyPending(context, change)) return false
        val remaining = PendingChangeQueue.removeById(all, id)
        savePending(context, remaining)
        scheduleNext(context, remaining)
        return true
    }

    fun applyAllPendingNow(context: Context): Int {
        if (!canApplyPendingNow(context)) return 0
        val all = pendingChanges(context)
        if (all.isEmpty()) return 0
        var applied = 0
        for (change in all) {
            if (applyPending(context, change)) applied++
        }
        if (applied > 0) {
            savePending(context, emptyList())
            cancelAlarm(context)
        }
        return applied
    }

    fun applyDueChanges(context: Context, nowMs: Long = System.currentTimeMillis()): Int {
        val all = pendingChanges(context)
        if (all.isEmpty()) {
            cancelAlarm(context)
            return 0
        }
        val (due, remaining) = PendingChangeQueue.partitionDue(all, nowMs)
        var applied = 0
        for (change in due) {
            if (applyPending(context, change)) applied++
        }
        savePending(context, remaining)
        scheduleNext(context, remaining)
        if (applied > 0) {
            BlockingRuntime.ensureRunning(context)
            recordEvent(context, "Pending changes applied", "count=$applied")
        }
        return applied
    }

    fun reschedulePending(context: Context) {
        scheduleNext(context, pendingChanges(context))
    }

    private fun queue(context: Context, type: String, data: JSONObject) {
        val change = PendingChangeQueue.newChange(
            id = UUID.randomUUID().toString(),
            type = type,
            nowMs = System.currentTimeMillis(),
            delayMinutes = getDelayMinutes(context),
            data = data,
        )
        val updated = PendingChangeQueue.upsert(pendingChanges(context), change)
        savePending(context, updated)
        scheduleNext(context, updated)
        recordEvent(context, "Protection change queued", "type=$type profile=${data.optString("profile")}")
        logRateLimited(context, "queued type=$type")
    }

    /** Applies a pruning transform and persists/reschedules only when something changed. */
    private fun prunePending(context: Context, transform: (List<PendingChange>) -> List<PendingChange>) {
        val current = pendingChanges(context)
        val pruned = transform(current)
        if (PendingChangeCodec.encode(pruned) == PendingChangeCodec.encode(current)) return
        savePending(context, pruned)
        scheduleNext(context, pruned)
        recordEvent(context, "Pending change cancelled by stricter edit", "count=${current.size - pruned.size}")
    }

    private fun savePending(context: Context, changes: List<PendingChange>) {
        prefs(context).edit { putString(PREF_PENDING_JSON, PendingChangeCodec.encode(changes)) }
    }

    private fun applyPending(context: Context, change: PendingChange): Boolean = runCatching {
        when (change.type) {
            PendingChangeType.APP_SELECTION -> {
                val profile = change.data.getString("profile")
                if (profile !in ProfileStore.getProfiles(context)) return@runCatching false
                val allowMode = change.data.optBoolean("allowMode", false)
                if (allowMode) {
                    applySelection(
                        context, profile, allowMode = true,
                        packages = stringSet(change.data.optJSONArray("addPackages")),
                        remove = false,
                    )
                } else {
                    applySelection(
                        context, profile, allowMode = false,
                        packages = stringSet(change.data.optJSONArray("removePackages")),
                        remove = true,
                    )
                }
            }

            PendingChangeType.IN_APP_SELECTION -> {
                val profile = change.data.getString("profile")
                if (profile !in ProfileStore.getProfiles(context)) return@runCatching false
                applyInAppSelection(
                    context = context,
                    profile = profile,
                    packageName = change.data.optString("packageName"),
                    baseKey = change.data.getString("baseKey"),
                    surfaceKey = change.data.optString("surfaceKey").takeIf { it.isNotBlank() },
                    allowMode = change.data.optBoolean("allowMode", false),
                    selected = change.data.optBoolean("selected", false),
                )
            }

            PendingChangeType.WEBSITE_REMOVE -> {
                val profile = change.data.getString("profile")
                if (profile !in ProfileStore.getProfiles(context)) return@runCatching false
                applyWebsiteRemoval(context, profile, change.data.getString("rule"))
            }

            PendingChangeType.WEBSITE_ENABLED -> {
                val profile = change.data.getString("profile")
                if (profile !in ProfileStore.getProfiles(context)) return@runCatching false
                DomainBlockStore.setDomainEnabledForProfile(
                    context,
                    profile,
                    change.data.getString("rule"),
                    change.data.optBoolean("enabled", true),
                )
            }

            PendingChangeType.APP_LIMITS -> {
                val profile = change.data.getString("profile")
                val packageName = change.data.getString("packageName")
                if (profile !in ProfileStore.getProfiles(context)) return@runCatching false

                var time: Int? = null
                var attempts: Int? = null
                var perVisit: Int? = null
                var reset: String? = null
                if (change.data.has("fromTime") &&
                    UsageLimitStore.getLimitMinutes(context, profile, packageName) == change.data.optInt("fromTime")
                ) {
                    time = change.data.optInt("toTime").coerceAtLeast(0)
                }
                if (change.data.has("fromAttempts") &&
                    AttemptLimitStore.getLimitAttempts(context, profile, packageName) == change.data.optInt("fromAttempts")
                ) {
                    attempts = change.data.optInt("toAttempts").coerceAtLeast(0)
                }
                if (change.data.has("fromPerVisit") &&
                    SessionLimitStore.getLimitMinutes(context, profile, packageName) == change.data.optInt("fromPerVisit")
                ) {
                    perVisit = change.data.optInt("toPerVisit").coerceAtLeast(0)
                }
                if (change.data.has("fromReset") &&
                    UsageLimitResetStore.getMode(context, profile, packageName) == change.data.optString("fromReset")
                ) {
                    reset = change.data.optString("toReset")
                }
                if (time != null || attempts != null || perVisit != null || reset != null) {
                    applyLimitValues(context, profile, packageName, time, attempts, perVisit, reset)
                }
            }

            PendingChangeType.WEBSITE_LIMIT -> {
                val profile = change.data.getString("profile")
                if (profile !in ProfileStore.getProfiles(context)) return@runCatching false
                val rule = change.data.getString("rule")
                // Stricter edit since? Leave the current value alone.
                val currentMinutes = DomainLimitStore.getLimitMinutesForProfile(context, profile, rule)
                if (change.data.has("fromMinutes") && currentMinutes != change.data.optInt("fromMinutes")) {
                    return@runCatching true
                }
                applyWebsiteLimit(
                    context = context,
                    profile = profile,
                    rule = rule,
                    allowMode = change.data.optBoolean("allowMode", false),
                    alwaysBlock = change.data.optBoolean("alwaysBlock", false),
                    minutes = change.data.optInt("minutes", 0),
                )
            }

            PendingChangeType.AUTO_BLOCK_NEW_APPS -> {
                val profile = change.data.getString("profile")
                if (profile !in ProfileStore.getProfiles(context)) return@runCatching false
                applyAutoBlockNewApps(context, profile, change.data.optBoolean("enabled", false))
            }

            PendingChangeType.CLEAR_APP_DATA -> {
                val profile = change.data.getString("profile")
                if (profile !in ProfileStore.getProfiles(context)) return@runCatching false
                val packages = stringSet(change.data.optJSONArray("packages"))
                if (packages.isNotEmpty()) {
                    applyClearAppData(
                        context,
                        profile,
                        packages,
                        change.data.optBoolean("includeInAppRules", true),
                    )
                }
            }

            else -> return@runCatching false
        }
        true
    }.getOrDefault(false)

    // ---------------------------------------------------------------------------------------------
    // Apply helpers
    // ---------------------------------------------------------------------------------------------

    private fun applySelection(
        context: Context,
        profile: String,
        allowMode: Boolean,
        packages: Set<String>,
        remove: Boolean,
    ) {
        if (packages.isEmpty()) return
        if (allowMode) {
            val current = ProfileStore.getAllowedForProfile(context, profile)
            ProfileStore.setAllowedForProfile(
                context, profile,
                if (remove) current - packages else current + packages,
            )
        } else {
            val current = ProfileStore.getBlockedForProfile(context, profile)
            ProfileStore.setBlockedForProfile(
                context, profile,
                if (remove) current - packages else current + packages,
            )
        }
    }

    private fun applyInAppSelection(
        context: Context,
        profile: String,
        packageName: String,
        baseKey: String,
        surfaceKey: String?,
        allowMode: Boolean,
        selected: Boolean,
    ) {
        InAppRuleStore.setRuleSelected(context, profile, baseKey, selected)
        if (!surfaceKey.isNullOrBlank()) {
            if (allowMode) {
                SurfaceLimitStore.clear(context, profile, surfaceKey)
            } else if (selected) {
                SurfaceLimitStore.setRule(context, profile, surfaceKey, -1)
            } else {
                SurfaceLimitStore.clear(context, profile, surfaceKey)
            }
        }
        if (selected && ProfileRuleModeStore.isAllowMode(context, profile) && packageName.isNotBlank()) {
            val allowed = ProfileStore.getAllowedForProfile(context, profile)
            if (packageName !in allowed) ProfileStore.setAllowedForProfile(context, profile, allowed + packageName)
        }
    }

    private fun applyWebsiteLimit(
        context: Context,
        profile: String,
        rule: String,
        allowMode: Boolean,
        alwaysBlock: Boolean,
        minutes: Int,
    ) {
        when {
            alwaysBlock && minutes > 0 -> {
                DomainLimitStore.clearForProfile(context, profile, rule)
                DomainBlockStore.addDomainForProfile(context, profile, rule)
            }

            alwaysBlock -> DomainBlockStore.removeDomainForProfile(context, profile, rule)

            minutes <= 0 -> {
                DomainLimitStore.clearForProfile(context, profile, rule)
                DomainBlockStore.removeDomainForProfile(context, profile, rule)
            }

            else -> {
                if (allowMode) {
                    DomainBlockStore.addDomainForProfile(context, profile, rule)
                } else {
                    DomainBlockStore.removeDomainForProfile(context, profile, rule)
                }
                DomainLimitStore.setLimitMinutesForProfile(context, profile, rule, minutes)
            }
        }
        BlockingRuntime.ensureRunning(context)
    }

    private fun applyWebsiteRemoval(context: Context, profile: String, rule: String) {
        DomainBlockStore.removeDomainForProfile(context, profile, rule)
        clearLimitIfNoRulesRemainForHost(context, profile, rule)
    }

    /**
     * Path rules are independent of a host rule, and limits are stored per host. Clear the host
     * limit only when no enabled or disabled rule for that host remains.
     */
    private fun clearLimitIfNoRulesRemainForHost(context: Context, profile: String, removedRule: String) {
        val normalized = DomainBlockStore.normalize(removedRule) ?: return
        val host = DomainBlockStore.hostPart(normalized)?.takeIf { it.isNotBlank() } ?: return
        val remaining = DomainBlockStore.getDomainsForProfileAndMode(context, profile).any {
            DomainBlockStore.hostPart(it) == host
        } || DomainBlockStore.getDisabledDomainsForProfile(context, profile).any {
            DomainBlockStore.hostPart(it) == host
        }
        if (!remaining) {
            DomainLimitStore.clearForProfile(context, profile, host)
        }
    }

    private fun applyLimitValues(
        context: Context,
        profile: String,
        packageName: String,
        timeMinutes: Int? = null,
        attempts: Int? = null,
        perVisitMinutes: Int? = null,
        resetMode: String? = null,
    ) {
        var shouldEnsureManaged = false
        if (timeMinutes != null) {
            UsageLimitStore.setLimitMinutes(context, profile, packageName, timeMinutes)
            LimitReachedStore.clearToday(context, packageName)
            if (timeMinutes > 0) {
                shouldEnsureManaged = true
                if (resetMode != null) UsageLimitResetStore.setMode(context, profile, packageName, resetMode)
            } else {
                UsageLimitResetStore.clearMode(context, profile, packageName)
                UsageStore.setUsageMsToday(context, packageName, 0L)
            }
        } else if (resetMode != null && UsageLimitStore.getLimitMinutes(context, profile, packageName) > 0) {
            UsageLimitResetStore.setMode(context, profile, packageName, resetMode)
        }
        if (attempts != null) {
            AttemptLimitStore.setLimitAttempts(context, profile, packageName, attempts)
            if (attempts > 0) {
                shouldEnsureManaged = true
            } else {
                OpenCountStore.setToday(context, profile, packageName, 0)
            }
        }
        if (perVisitMinutes != null) {
            SessionLimitStore.setLimitMinutes(context, profile, packageName, perVisitMinutes)
            if (perVisitMinutes > 0) shouldEnsureManaged = true
        }
        if (shouldEnsureManaged) {
            val selected = ProfileStore.getSelectedForProfileMode(context, profile)
            if (packageName !in selected) {
                ProfileStore.setSelectedForProfileMode(context, profile, selected + packageName)
            }
        }
        BlockingRuntime.ensureRunning(context)
    }

    private fun applyAutoBlockNewApps(context: Context, profile: String, enabled: Boolean) {
        ProfileStore.setAutoBlockNewAppsEnabled(context, profile, enabled)
        if (enabled) {
            ProfileStore.setAutoBlockKnownPackages(context, profile, ProfileStore.getLaunchablePackages(context))
        }
        BlockingRuntime.ensureRunning(context)
    }

    private fun applyClearAppData(
        context: Context,
        profile: String,
        packages: Collection<String>,
        includeInAppRules: Boolean,
    ) {
        for (packageName in packages) {
            if (packageName.isBlank()) continue
            UsageLimitStore.setLimitMinutes(context, profile, packageName, 0)
            SessionLimitStore.setLimitMinutes(context, profile, packageName, 0)
            AttemptLimitStore.setLimitAttempts(context, profile, packageName, 0)
            OpenCountStore.setToday(context, profile, packageName, 0)
            if (includeInAppRules) {
                InAppRuleStore.clearRulesForPackage(context, profile, packageName)
            }
        }
        BlockingRuntime.ensureRunning(context)
    }

    private fun stringSet(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        val values = linkedSetOf<String>()
        for (index in 0 until array.length()) {
            array.optString(index).takeIf { it.isNotBlank() }?.let(values::add)
        }
        return values
    }

    // ---------------------------------------------------------------------------------------------
    // Alarm
    // ---------------------------------------------------------------------------------------------

    private fun scheduleNext(context: Context, changes: List<PendingChange>) {
        val next = PendingChangeQueue.earliestDueAtMs(changes)
        if (next == null) {
            cancelAlarm(context)
            return
        }
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, alarmIntent(context))
    }

    private fun cancelAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(context))
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        ALARM_REQUEST_CODE,
        Intent(context, ProtectionChangeReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun recordEvent(context: Context, event: String, details: String) {
        runCatching { DiagnosticsTimelineStore.record(context, "Protection", event, details) }
    }

    private fun logRateLimited(context: Context, message: String) {
        runCatching { AppLogStore.appendRateLimited(context, "ProtectionChange", message) }
    }
}
