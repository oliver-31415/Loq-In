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

import com.oliver.loqin.data.prefs.UsageLimitResetStore

/**
 * Pure decision logic for the protection change gate.
 *
 * No Android dependencies: the Android shell ([ProtectionChangeGate]) supplies the lock state,
 * the configured delay and the current store values; this object only classifies edits and
 * decides whether they apply now, queue, or are denied.
 */
object ProtectionChangePolicy {

    val delayOptionsMinutes: List<Int> = listOf(0, 15, 60, 360, 1_440)
    const val MIN_CUSTOM_DELAY_MINUTES: Int = 1
    const val MAX_CUSTOM_DELAY_MINUTES: Int = 10_080

    enum class Direction {
        STRICTER,
        NEUTRAL,
        WEAKER,
        PROTECTED_STRUCTURAL,
    }

    enum class Decision {
        APPLY_NOW,
        QUEUE_DELAYED,
        DENY,
    }

    enum class Result {
        APPLIED,
        QUEUED,
        DENIED,
    }

    enum class SelectionAction {
        ADD,
        REMOVE,
    }

    fun isValidDelayMinutes(minutes: Int): Boolean =
        minutes in MIN_CUSTOM_DELAY_MINUTES..MAX_CUSTOM_DELAY_MINUTES || minutes == 0

    fun isPresetDelayMinutes(minutes: Int): Boolean = minutes in delayOptionsMinutes

    /** The delay itself is protection-sensitive: while locked it may only stay the same or grow. */
    fun canSetDelayMinutes(locked: Boolean, currentMinutes: Int, requestedMinutes: Int): Boolean {
        if (!isValidDelayMinutes(requestedMinutes)) return false
        if (!locked) return true
        return requestedMinutes >= currentMinutes
    }

    fun decision(locked: Boolean, delayMinutes: Int, direction: Direction): Decision {
        if (!locked) return Decision.APPLY_NOW
        return when (direction) {
            Direction.STRICTER,
            Direction.NEUTRAL -> Decision.APPLY_NOW

            Direction.WEAKER -> if (delayMinutes > 0) Decision.QUEUE_DELAYED else Decision.DENY

            Direction.PROTECTED_STRUCTURAL -> Decision.DENY
        }
    }

    // ---------------------------------------------------------------------------------------------
    // App selection
    // ---------------------------------------------------------------------------------------------

    fun appSelectionDirection(
        allowMode: Boolean,
        original: Set<String>,
        requested: Set<String>,
    ): Direction {
        if (original == requested) return Direction.NEUTRAL
        return if (allowMode) {
            // Fewer allowed exceptions is stricter; adding any new allowed app weakens protection.
            if (requested.all { it in original }) Direction.STRICTER else Direction.WEAKER
        } else {
            // A superset of blocked apps is stricter; removing any blocked app weakens protection.
            if (original.all { it in requested }) Direction.STRICTER else Direction.WEAKER
        }
    }

    data class SelectionPlan(
        val direction: Direction,
        val action: SelectionAction?,
        val applyNow: Set<String>,
        val queued: Set<String>,
    ) {
        val hasImmediate: Boolean get() = applyNow.isNotEmpty()
        val hasQueued: Boolean get() = queued.isNotEmpty()
    }

    /**
     * Splits a selection edit into the stricter half (apply now) and the weakening half (queue).
     * In block mode additions are stricter; in allow mode removals are stricter.
     */
    fun planAppSelection(
        allowMode: Boolean,
        original: Set<String>,
        requested: Set<String>,
    ): SelectionPlan {
        val direction = appSelectionDirection(allowMode, original, requested)
        val applyNow: Set<String>
        val queued: Set<String>
        val action: SelectionAction?
        if (allowMode) {
            applyNow = original - requested
            queued = requested - original
            action = if (queued.isEmpty()) null else SelectionAction.ADD
        } else {
            applyNow = requested - original
            queued = original - requested
            action = if (queued.isEmpty()) null else SelectionAction.REMOVE
        }
        return SelectionPlan(direction = direction, action = action, applyNow = applyNow, queued = queued)
    }

    // ---------------------------------------------------------------------------------------------
    // In-app rules and website rules
    // ---------------------------------------------------------------------------------------------

    fun inAppDirection(
        allowMode: Boolean,
        currentSelected: Boolean,
        requestedSelected: Boolean,
    ): Direction {
        if (currentSelected == requestedSelected) return Direction.NEUTRAL
        return if (allowMode) {
            if (currentSelected && !requestedSelected) Direction.STRICTER else Direction.WEAKER
        } else {
            if (!currentSelected && requestedSelected) Direction.STRICTER else Direction.WEAKER
        }
    }

    /** Removing a rule is stricter in allow mode (it was an exception) and weaker in block mode. */
    fun websiteRemovalDirection(allowMode: Boolean): Direction =
        if (allowMode) Direction.STRICTER else Direction.WEAKER

    /**
     * In block mode an enabled rule blocks, so enabling it is stricter.
     * In allow mode an enabled rule is an exception, so disabling it is stricter.
     */
    fun websiteEnabledDirection(allowMode: Boolean, requestedEnabled: Boolean): Direction =
        if (allowMode) {
            if (requestedEnabled) Direction.WEAKER else Direction.STRICTER
        } else {
            if (requestedEnabled) Direction.STRICTER else Direction.WEAKER
        }

    /**
     * Direction of a website limit edit (the website limit editor can switch between a hard block
     * and a time limit).
     * - switching to "block always" adds protection;
     * - switching away from "block always" reduces it;
     * - otherwise the numeric limit direction applies, inverted in allow mode where a limit is an
     *   allowed exception (adding/raising it widens access).
     */
    fun websiteLimitDirection(
        allowMode: Boolean,
        currentlyAlwaysBlocked: Boolean,
        currentMinutes: Int,
        requestedAlwaysBlock: Boolean,
        requestedMinutes: Int,
    ): Direction {
        if (requestedAlwaysBlock) {
            return if (currentlyAlwaysBlocked) Direction.NEUTRAL else Direction.STRICTER
        }
        if (currentlyAlwaysBlocked) return Direction.WEAKER
        val numeric = numericLimitDirection(currentMinutes, requestedMinutes)
        if (!allowMode) return numeric
        return when (numeric) {
            Direction.STRICTER -> Direction.WEAKER
            Direction.WEAKER -> Direction.STRICTER
            else -> Direction.NEUTRAL
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Limits
    // ---------------------------------------------------------------------------------------------

    fun numericLimitDirection(current: Int, requested: Int): Direction {
        val old = current.coerceAtLeast(0)
        val new = requested.coerceAtLeast(0)
        if (old == new) return Direction.NEUTRAL
        if (old == 0 && new > 0) return Direction.STRICTER
        if (old > 0 && new == 0) return Direction.WEAKER
        return if (new < old) Direction.STRICTER else Direction.WEAKER
    }

    fun resetModeDirection(current: String, requested: String, timeLimitExists: Boolean): Direction {
        if (!timeLimitExists || current == requested) return Direction.NEUTRAL
        // Per-day keeps one allowance for the whole day; per-session can reset it when a new session
        // begins, so DAY is the stricter of the two modes.
        return if (current == UsageLimitResetStore.MODE_SESSION && requested == UsageLimitResetStore.MODE_DAY) {
            Direction.STRICTER
        } else {
            Direction.WEAKER
        }
    }

    data class LimitChange(val from: Int, val to: Int)

    data class ResetModeChange(val from: String, val to: String)

    data class LimitsPlan(
        val direction: Direction,
        val applyNowTime: Int? = null,
        val applyNowAttempts: Int? = null,
        val applyNowPerVisit: Int? = null,
        val applyNowResetMode: String? = null,
        val queuedTime: LimitChange? = null,
        val queuedAttempts: LimitChange? = null,
        val queuedPerVisit: LimitChange? = null,
        val queuedResetMode: ResetModeChange? = null,
    ) {
        val hasImmediate: Boolean
            get() = applyNowTime != null || applyNowAttempts != null ||
                applyNowPerVisit != null || applyNowResetMode != null

        val hasQueued: Boolean
            get() = queuedTime != null || queuedAttempts != null ||
                queuedPerVisit != null || queuedResetMode != null
    }

    /**
     * Classifies every limit component independently. Stricter components are applied immediately,
     * weaker ones are queued with their previous values so a later stricter edit is never overwritten.
     */
    fun planAppLimits(
        currentTimeMinutes: Int,
        requestedTimeMinutes: Int,
        currentAttempts: Int,
        requestedAttempts: Int,
        currentPerVisitMinutes: Int,
        requestedPerVisitMinutes: Int,
        currentResetMode: String,
        requestedResetMode: String,
    ): LimitsPlan {
        val oldTime = currentTimeMinutes.coerceAtLeast(0)
        val newTime = requestedTimeMinutes.coerceAtLeast(0)
        val oldAttempts = currentAttempts.coerceAtLeast(0)
        val newAttempts = requestedAttempts.coerceAtLeast(0)
        val oldPerVisit = currentPerVisitMinutes.coerceAtLeast(0)
        val newPerVisit = requestedPerVisitMinutes.coerceAtLeast(0)
        val newReset = if (requestedResetMode == UsageLimitResetStore.MODE_SESSION) {
            UsageLimitResetStore.MODE_SESSION
        } else {
            UsageLimitResetStore.MODE_DAY
        }

        val timeDirection = numericLimitDirection(oldTime, newTime)
        val attemptsDirection = numericLimitDirection(oldAttempts, newAttempts)
        val perVisitDirection = numericLimitDirection(oldPerVisit, newPerVisit)
        val resetDirection = resetModeDirection(currentResetMode, newReset, oldTime > 0 && newTime > 0)

        if (timeDirection == Direction.NEUTRAL && attemptsDirection == Direction.NEUTRAL &&
            perVisitDirection == Direction.NEUTRAL && resetDirection == Direction.NEUTRAL
        ) {
            return LimitsPlan(direction = Direction.NEUTRAL)
        }

        val hasWeaker = timeDirection == Direction.WEAKER || attemptsDirection == Direction.WEAKER ||
            perVisitDirection == Direction.WEAKER || resetDirection == Direction.WEAKER

        if (!hasWeaker) {
            return LimitsPlan(
                direction = Direction.STRICTER,
                applyNowTime = newTime.takeIf { timeDirection != Direction.NEUTRAL },
                applyNowAttempts = newAttempts.takeIf { attemptsDirection != Direction.NEUTRAL },
                applyNowPerVisit = newPerVisit.takeIf { perVisitDirection != Direction.NEUTRAL },
                applyNowResetMode = newReset.takeIf {
                    resetDirection != Direction.NEUTRAL || (timeDirection != Direction.NEUTRAL && newTime > 0)
                },
            )
        }

        return LimitsPlan(
            direction = Direction.WEAKER,
            applyNowTime = newTime.takeIf { timeDirection == Direction.STRICTER },
            applyNowAttempts = newAttempts.takeIf { attemptsDirection == Direction.STRICTER },
            applyNowPerVisit = newPerVisit.takeIf { perVisitDirection == Direction.STRICTER },
            applyNowResetMode = newReset.takeIf {
                resetDirection == Direction.STRICTER || (oldTime == 0 && timeDirection == Direction.STRICTER)
            },
            queuedTime = if (timeDirection == Direction.WEAKER) LimitChange(oldTime, newTime) else null,
            queuedAttempts = if (attemptsDirection == Direction.WEAKER) LimitChange(oldAttempts, newAttempts) else null,
            queuedPerVisit = if (perVisitDirection == Direction.WEAKER) LimitChange(oldPerVisit, newPerVisit) else null,
            queuedResetMode = if (resetDirection == Direction.WEAKER && newTime > 0) {
                ResetModeChange(currentResetMode, newReset)
            } else {
                null
            },
        )
    }
}
