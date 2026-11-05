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

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import com.oliver.loqin.theme.AccentColor

/**
 * Section headers and row icons tinted with the raw accent are too pale on the page for soft
 * accents (Soft Blue, Amber). This swaps that exact accent for the readable one, in place.
 */
object AccentContrast {
    fun strengthen(root: View) {
        val ctx = root.context
        val accent = AccentColor.getAccentColorInt(ctx) or 0xFF000000.toInt()
        val strong = RuleTileStyle.strongAccent(ctx)
        if (strong == accent) return
        fun walk(v: View) {
            when (v) {
                is TextView -> if (v.currentTextColor or 0xFF000000.toInt() == accent) v.setTextColor(strong)
                is ImageView -> {
                    val tint = ImageViewCompat.getImageTintList(v)?.defaultColor
                    if (tint != null && tint or 0xFF000000.toInt() == accent) {
                        ImageViewCompat.setImageTintList(v, android.content.res.ColorStateList.valueOf(strong))
                    }
                }
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
    }
}
