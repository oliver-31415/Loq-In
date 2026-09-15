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
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.oliver.loqin.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Shared setup for Loq In bottom sheets.
 *
 * Why this exists:
 * - Material's default `bottomSheetDialogTheme` runs a window enter animation
 *   (m3_bottom_sheet_slide_in: a 20% translate plus fade on an emphasized curve)
 *   at the same time as [BottomSheetBehavior] animates the sheet into place. The
 *   two motions are not synchronised, so the sheet rises, overshoots and slides
 *   again, and the moving surface can shift out from under an in-progress tap.
 *   The app theme points `bottomSheetDialogTheme` at a Loq In overlay that removes
 *   the window animation, so only the behavior animates.
 * - Material's edge-to-edge callback rewrites padding and the status-bar
 *   appearance on every slide frame, which adds jitter; the overlay disables it.
 * - Setting [BottomSheetBehavior.setState] to expanded *before* the dialog is shown
 *   cannot settle, because the container has not been laid out yet; the expand then
 *   animates after the dialog appears. Instead, set the target before `show()` so it
 *   becomes the initial state rather than an animated transition.
 */

/**
 * Applies Loq In's sheet chrome and opens [this] already expanded.
 *
 * Call after [BottomSheetDialog.setContentView] and before `show()`.
 */
fun BottomSheetDialog.prepareExpanded() {
    findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
        applyRoundedTopBackground(context, sheet)

        BottomSheetBehavior.from(sheet).apply {
            skipCollapsed = true
            isHideable = true
            // Set the target before the dialog is shown. Because the container is
            // not laid out yet, this becomes the initial state rather than an
            // animated transition, so the sheet opens fully expanded instead of
            // gliding up from the peek height.
            state = BottomSheetBehavior.STATE_EXPANDED
        }
        // Re-assert after the first layout pass: some devices resolve the initial
        // state to half-expanded before the content is measured, which would
        // otherwise leave a small unexplained settle.
        sheet.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int,
            ) {
                v.removeOnLayoutChangeListener(this)
                val behavior = BottomSheetBehavior.from(v)
                if (behavior.state != BottomSheetBehavior.STATE_EXPANDED) {
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                }
            }
        })
    }
}

/**
 * Rounded top corners + app surface color, matching the rest of Loq In's sheets.
 */
fun applyRoundedTopBackground(context: Context, sheet: View) {
    val topRadius = 24 * context.resources.displayMetrics.density + 0.5f
    sheet.background = GradientDrawable().apply {
        cornerRadii = floatArrayOf(
            topRadius, topRadius,
            topRadius, topRadius,
            0f, 0f,
            0f, 0f,
        )
        setColor(ContextCompat.getColor(context, R.color.foqos_surface))
    }
}

object LoqInBottomSheet {
    /**
     * Builds a sheet with [build], prepares it expanded, then shows it.
     */
    fun showExpanded(activity: Activity, build: (BottomSheetDialog) -> Unit): BottomSheetDialog {
        val dialog = BottomSheetDialog(activity)
        build(dialog)
        dialog.prepareExpanded()
        dialog.show()
        return dialog
    }
}
