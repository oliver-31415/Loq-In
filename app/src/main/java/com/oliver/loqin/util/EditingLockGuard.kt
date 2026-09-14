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

import android.app.Activity
import android.content.Context
import android.util.TypedValue
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import com.google.android.material.checkbox.MaterialCheckBox
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.SwitchModeStore
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.theme.CustomAccentApplier
import com.oliver.loqin.ui.dialog.showAccented
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object EditingLockGuard {
    private const val KEY_SUPPRESS_LOCK_DIALOGS = "suppress_lock_dialogs"

    fun isLocked(ctx: Context): Boolean {
        // Emergency Unlock temporarily pauses blocking, but it must not allow permanent changes to protection rules or settings.
        // Editing becomes available only after LoqIn is fully disabled.
        return SwitchModeStore.isEnabled(ctx) ||
            SwitchModeStore.isBaseEnabled(ctx) ||
            SwitchModeStore.hasActiveTemporaryOverride(ctx)
    }

    /** Global kill-switch for the "Loq In is active" explanation dialogs. */
    fun isSuppressed(ctx: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(ctx)
            .getBoolean(KEY_SUPPRESS_LOCK_DIALOGS, false)
    }

    fun setSuppressed(ctx: Context, suppressed: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit()
            .putBoolean(KEY_SUPPRESS_LOCK_DIALOGS, suppressed)
            .apply()
    }

    /**
     * Adds a "Don't show again" checkbox row to a lock dialog builder.
     * Uses an explicit checkbox view (not the dialog's built-in choice list,
     * which does not lay out next to a message on this theme) and tints it
     * like every other checkbox in the app.
     * Returns a callback that persists the choice — call it only when the
     * dialog is positively confirmed, so a bare dismiss doesn't opt out.
     */
    fun addDontShowAgain(builder: AlertDialog.Builder, ctx: Context): () -> Unit {
        var checked = false
        val density = ctx.resources.displayMetrics.density
        // Explicit text color: this view is built with the activity context
        // (light-pinned theme → dark text) but shown on the dark dialog,
        // so the default themed color would be invisible. Resolve day/night
        // from the live configuration, like the in-app pills do.
        val night = (ctx.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val checkBox = MaterialCheckBox(ctx).apply {
            text = ctx.getString(R.string.lock_warnings_dont_show_again)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(if (night) 0xFFF2F1EC.toInt() else 0xFF1B1B18.toInt())
            buttonTintList = CustomAccentApplier.buildCheckableTint(
                ctx,
                AccentColor.getAccentColorInt(ctx)
            )
            setOnCheckedChangeListener { _, isChecked -> checked = isChecked }
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val side = (24f * density).toInt()
            setPadding(side, (2f * density).toInt(), side, 0)
            addView(checkBox)
        }
        builder.setView(row)
        return { if (checked) setSuppressed(ctx, true) }
    }

    fun showLockedDialog(ctx: Context, @StringRes messageRes: Int) {
        if (isSuppressed(ctx)) return
        val hint = runCatching { ctx.getString(messageRes) }.getOrDefault("").trim()
        val body = if (hint.isBlank()) {
            ctx.getString(R.string.edit_locked_currently_active_body)
        } else {
            ctx.getString(R.string.edit_locked_currently_active_body_with_hint, hint)
        }
        val builder = MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.edit_locked_while_loqin_on_title)
            .setMessage(body)
            .setIcon(R.drawable.lock_24)
        val persistChoice = addDontShowAgain(builder, ctx)
        builder.setPositiveButton(R.string.ok) { _, _ -> persistChoice() }
            .showAccented()
    }

    fun blockWithDialog(activity: Activity, @StringRes messageRes: Int): Boolean {
        if (!isLocked(activity)) {
            return false
        }
        // Suppression only silences the explanation — the block itself stays.
        if (isSuppressed(activity)) {
            activity.finish()
            return true
        }

        val hint = runCatching { activity.getString(messageRes) }.getOrDefault("").trim()
        val body = if (hint.isBlank()) {
            activity.getString(R.string.edit_locked_currently_active_body)
        } else {
            activity.getString(R.string.edit_locked_currently_active_body_with_hint, hint)
        }
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.edit_locked_while_loqin_on_title)
            .setMessage(body)
            .setIcon(R.drawable.lock_24)
        val persistChoice = addDontShowAgain(builder, activity)
        builder.setPositiveButton(R.string.ok) { _, _ ->
                persistChoice()
                activity.finish()
            }
            .setOnCancelListener {
                activity.finish()
            }
            .showAccented()

        return true
    }
}
