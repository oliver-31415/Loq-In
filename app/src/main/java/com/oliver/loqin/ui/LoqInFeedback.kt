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

package com.oliver.loqin.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import com.oliver.loqin.theme.AccentColor
import com.google.android.material.snackbar.Snackbar

/**
 * Screen actions use Snackbars; scanner/background events remain Toasts.
 * Keep every Snackbar visually neutral and use the current LoqIn accent only for actions.
 *
 * Colors are chosen explicitly from the current night configuration so the pill
 * always matches the system theme. Resource/theme lookups are not used here:
 * theme attributes resolve through the Light-pinned base theme (always light),
 * and resource night qualifiers depend on the activity configuration.
 */
fun Snackbar.applyLoqInStyle(): Snackbar {
    val ctx = view.context
    val accent = AccentColor.getAccentColorInt(ctx)
    val night = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    // Warm paper in light mode, warm charcoal in dark mode (matches foqos tokens).
    val surface = if (night) 0xFF232320.toInt() else 0xFFF0EFEA.toInt()
    val onSurface = if (night) 0xFFF2F1EC.toInt() else 0xFF1B1B18.toInt()
    view.setBackgroundColor(surface)
    view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.apply {
        setTextColor(onSurface)
        maxLines = 3
    }
    setActionTextColor(accent)
    return this
}

fun View.showLoqInStatus(message: CharSequence, long: Boolean = false) {
    Snackbar.make(this, message, if (long) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT)
        .applyLoqInStyle()
        .show()
}

fun View.showLoqInStatus(@StringRes messageRes: Int, long: Boolean = false) {
    showLoqInStatus(context.getString(messageRes), long)
}

private var lastWarnPill: Snackbar? = null

/**
 * Warning pill rendered in-app as a floating rounded Snackbar.
 *
 * Rendered in-app (not a system Toast) with day/night colors chosen explicitly
 * from the live configuration, so the pill always matches the theme the user
 * is looking at. Dismisses the previous warning first so rapid taps replace
 * the pill instead of queueing a long line of them.
 */
fun View.showWarnPill(
    message: CharSequence,
    actionLabel: CharSequence? = null,
    onAction: (() -> Unit)? = null,
) {
    lastWarnPill?.takeIf { it.isShown }?.dismiss()
    val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    // Warm paper in light mode, warm charcoal in dark mode (matches foqos tokens).
    val surface = if (night) 0xFF323232.toInt() else 0xFFF5F4EF.toInt()
    val onSurface = if (night) 0xFFF5F2EA.toInt() else 0xFF1B1B18.toInt()
    val density = resources.displayMetrics.density
    val pill = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 28f * density
        setColor(surface)
    }
    lastWarnPill = Snackbar.make(this, message, Snackbar.LENGTH_LONG).apply {
        view.background = pill
        view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.apply {
            setTextColor(onSurface)
            maxLines = 4
            textSize = 14f
        }
        (view.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
            val side = (16f * density).toInt()
            setMargins(side, topMargin, side, (20f * density).toInt())
        }
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            setAction(actionLabel) { onAction() }
            setActionTextColor(AccentColor.getAccentColorInt(context))
        }
        addCallback(object : Snackbar.Callback() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                if (lastWarnPill === transientBottomBar) {
                    lastWarnPill = null
                }
            }
        })
        show()
    }
}

fun View.showWarnPill(
    @StringRes messageRes: Int,
    actionLabel: CharSequence? = null,
    onAction: (() -> Unit)? = null,
) {
    showWarnPill(resources.getString(messageRes), actionLabel, onAction)
}

private fun Activity.contentViewForPill(): View? =
    findViewById<View>(android.R.id.content) ?: window?.decorView

/** Screen-level pill; null-safe for detached/async contexts (shows nothing instead of crashing). */
fun Activity.showWarnPillOnContent(message: CharSequence) {
    contentViewForPill()?.showWarnPill(message)
}

fun Activity.showWarnPillOnContent(@StringRes messageRes: Int) {
    showWarnPillOnContent(getString(messageRes))
}

/**
 * Pill when a screen is available, system Toast otherwise (services, app context).
 */
fun Context.showWarnPillAnywhere(message: CharSequence) {
    val activity = this as? Activity
    if (activity != null) {
        activity.showWarnPillOnContent(message)
    } else {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}

fun Context.showWarnPillAnywhere(@StringRes messageRes: Int) {
    showWarnPillAnywhere(getString(messageRes))
}
