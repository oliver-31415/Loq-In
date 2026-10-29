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

import android.graphics.Bitmap
import android.graphics.Canvas

/**
 * Static snapshots of the Home hero blob ([HeroBlobDrawable]) for home-screen AppWidgets,
 * where live animation can't run.
 */
object HeroArtRenderer {

    /**
     * Renders a static snapshot of the hero artwork onto a Bitmap.
     * Ideal for RemoteViews / AppWidgets where live animators cannot run.
     */
    fun renderBitmap(
        widthPx: Int,
        heightPx: Int,
        accent: Int,
        radiusPx: Float,
    ): Bitmap {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val drawable = HeroBlobDrawable(accent, radiusPx, stillFrame = true)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bitmap
    }
}
