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

package com.oliver.loqin.ui.dialog

import android.app.Activity
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.EmergencyPinStore
import com.oliver.loqin.ui.showWarnPill
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Emergency Unlock PIN flows, built on the shared [PinEntryDialog] so the look
 * (square digit boxes, dynamic length up to 8) and the dialog presentation
 * match App lock and the rest of the app.
 */
object EmergencyPinDialog {

    /** Verify the current emergency PIN (e.g. before changing it or unlocking). */
    fun showEnterPin(
        activity: Activity,
        onSuccess: () -> Unit,
        @StringRes neutralButtonRes: Int? = null,
        neutralAction: (() -> Unit)? = null,
    ): AlertDialog {
        val storedPin = EmergencyPinStore.getPin(activity).orEmpty().trim()
        return PinEntryDialog.showVerify(
            activity = activity,
            titleRes = R.string.emergency_pin_enter_current_title,
            subtitleRes = R.string.emergency_pin_enter_current_message,
            incorrectRes = R.string.emergency_pin_incorrect,
            verifyLength = storedPin.length,
            validator = { EmergencyPinStore.matchesPin(activity, it) },
            neutralButtonRes = neutralButtonRes,
            neutralAction = neutralAction,
        ) { onSuccess() }
    }

    /** First-time setup: create the emergency PIN (with re-entry). */
    fun showSetPin(
        activity: Activity,
        onSuccess: () -> Unit = {}
    ): AlertDialog = showCreatePin(
        activity = activity,
        titleRes = R.string.emergency_pin_title,
        onRemove = null,
        onSuccess = onSuccess,
    )

    private fun showCreatePin(
        activity: Activity,
        @StringRes titleRes: Int,
        onRemove: (() -> Unit)?,
        onSuccess: () -> Unit,
        resetWithoutCurrentPin: Boolean = false,
    ): AlertDialog = PinEntryDialog.showCreate(
        activity = activity,
        titleRes = titleRes,
        subtitleRes = R.string.emergency_pin_message,
        tooShortRes = R.string.emergency_pin_too_short,
        mismatchRes = R.string.pin_mismatch,
        onRemove = onRemove,
    ) { pin ->
        val saved = if (resetWithoutCurrentPin) {
            EmergencyPinStore.resetPinWhenFullyDisabled(activity, pin)
        } else {
            EmergencyPinStore.setPin(activity, pin)
            true
        }
        val content = activity.findViewById<View>(android.R.id.content)
        if (!saved) {
            content.showWarnPill(R.string.emergency_pin_reset_requires_disabled)
            return@showCreate
        }
        content.showWarnPill(
            if (resetWithoutCurrentPin) R.string.emergency_pin_reset_done else R.string.emergency_pin_changed
        )
        onSuccess()
    }

    /**
     * Change flow: confirm the current PIN first; the new-PIN page also offers
     * "Remove PIN". While protection is fully off, a "Reset PIN" action skips
     * the current-PIN step for users who forgot it.
     */
    fun showChangePinFlow(
        activity: Activity,
        onComplete: () -> Unit = {}
    ) {
        if (EmergencyPinStore.hasPin(activity)) {
            val canReset = EmergencyPinStore.canResetWithoutCurrentPin(activity)
            showEnterPin(
                activity = activity,
                onSuccess = {
                    showCreatePin(
                        activity = activity,
                        titleRes = R.string.pref_change_emergency_pin_title,
                        onRemove = {
                            EmergencyPinStore.removePin(activity)
                            activity.findViewById<View>(android.R.id.content)
                                .showWarnPill(R.string.emergency_pin_removed)
                            onComplete()
                        },
                        onSuccess = onComplete,
                    )
                },
                neutralButtonRes = if (canReset) R.string.emergency_pin_reset_action else null,
                neutralAction = if (canReset) {
                    { showResetConfirmation(activity, onComplete) }
                } else {
                    null
                },
            )
        } else {
            showSetPin(activity, onComplete)
        }
    }

    private fun showResetConfirmation(
        activity: Activity,
        onComplete: () -> Unit,
    ) {
        if (!EmergencyPinStore.canResetWithoutCurrentPin(activity)) {
            activity.findViewById<View>(android.R.id.content)
                .showWarnPill(R.string.emergency_pin_reset_requires_disabled)
            return
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.emergency_pin_reset_title)
            .setMessage(R.string.emergency_pin_reset_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.emergency_pin_reset_action) { _, _ ->
                showCreatePin(
                    activity = activity,
                    titleRes = R.string.emergency_pin_title,
                    onRemove = null,
                    onSuccess = onComplete,
                    resetWithoutCurrentPin = true,
                )
            }
            .showAccented()
    }
}
