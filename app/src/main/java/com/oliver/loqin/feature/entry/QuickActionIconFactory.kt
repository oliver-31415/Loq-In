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

package com.oliver.loqin.feature.entry

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Icon
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.IconCompat
import com.oliver.loqin.R
import kotlin.math.roundToInt

object QuickActionIconFactory {

    @ColorInt
    private const val SHORTCUT_ICON_TINT: Int = 0xFF111827.toInt()

    @ColorInt
    private const val TILE_ICON_TINT: Int = Color.WHITE

    fun createShortcutIcon(context: Context, @DrawableRes drawableRes: Int): IconCompat {
        return IconCompat.createWithBitmap(
            createBitmap(
                context = context,
                drawableRes = drawableRes,
                tint = SHORTCUT_ICON_TINT,
                canvasSizeDp = 64,
                iconSizeDp = 28,
            )
        )
    }

    fun createTileIcon(context: Context, @DrawableRes drawableRes: Int): Icon {
        return Icon.createWithBitmap(
            createBitmap(
                context = context,
                drawableRes = drawableRes,
                tint = TILE_ICON_TINT,
                canvasSizeDp = 40,
                iconSizeDp = 24,
            )
        )
    }

    fun createWidgetBitmap(
        context: Context,
        @DrawableRes drawableRes: Int,
        @ColorInt tint: Int = ContextCompat.getColor(context, R.color.foqos_on_surface),
        canvasSizeDp: Int = 28,
        iconSizeDp: Int = 24,
    ): Bitmap {
        return createBitmap(
            context = context,
            drawableRes = drawableRes,
            tint = tint,
            canvasSizeDp = canvasSizeDp,
            iconSizeDp = iconSizeDp,
        )
    }

    private fun createBitmap(
        context: Context,
        @DrawableRes drawableRes: Int,
        @ColorInt tint: Int,
        canvasSizeDp: Int,
        iconSizeDp: Int,
    ): Bitmap {
        val themedContext = ContextThemeWrapper(context, R.style.Theme_LoqIn)
        val baseDrawable = AppCompatResources.getDrawable(themedContext, drawableRes)
            ?: AppCompatResources.getDrawable(context, drawableRes)
            ?: error("Drawable $drawableRes could not be loaded")

        val drawable = DrawableCompat.wrap(baseDrawable.mutate())
        DrawableCompat.setTint(drawable, tint)
        DrawableCompat.setTintMode(drawable, PorterDuff.Mode.SRC_IN)
        drawable.alpha = 255

        val canvasSizePx = dpToPx(context, canvasSizeDp)
        val iconSizePx = dpToPx(context, iconSizeDp)
        return Bitmap.createBitmap(canvasSizePx, canvasSizePx, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            val inset = ((canvasSizePx - iconSizePx) / 2).coerceAtLeast(0)
            drawable.setBounds(inset, inset, inset + iconSizePx, inset + iconSizePx)
            drawable.draw(canvas)
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    }
}
