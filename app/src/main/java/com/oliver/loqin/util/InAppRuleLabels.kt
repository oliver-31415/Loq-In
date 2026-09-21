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

import android.content.Context
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.BlockingToggleKeys

/** User-facing labels for in-app rule keys, shared by the rules screen and the pending-change list. */
object InAppRuleLabels {

    private val LABELS: Map<String, Int> = mapOf(
        BlockingToggleKeys.KEY_BLOCK_YT_HOME to R.string.in_app_surface_home_label,
        BlockingToggleKeys.KEY_BLOCK_YT_SHORTS to R.string.in_app_surface_shorts_label,
        BlockingToggleKeys.KEY_BLOCK_YT_SUBSCRIPTIONS to R.string.in_app_surface_subscriptions_label,
        BlockingToggleKeys.KEY_BLOCK_YT_YOU to R.string.in_app_surface_you_label,
        BlockingToggleKeys.KEY_BLOCK_YT_MINI_PLAYER to R.string.in_app_surface_mini_player_label,
        BlockingToggleKeys.KEY_BLOCK_YT_PIP to R.string.in_app_surface_pip_label,
        BlockingToggleKeys.KEY_BLOCK_IG_REELS to R.string.in_app_surface_reels_label,
        BlockingToggleKeys.KEY_BLOCK_IG_SEARCH to R.string.in_app_surface_search_label,
        BlockingToggleKeys.KEY_BLOCK_IG_STORIES to R.string.in_app_surface_stories_label,
        BlockingToggleKeys.KEY_BLOCK_X_HOME to R.string.in_app_surface_home_label,
        BlockingToggleKeys.KEY_BLOCK_X_SEARCH to R.string.in_app_surface_search_label,
        BlockingToggleKeys.KEY_BLOCK_X_GROK to R.string.in_app_surface_grok_label,
        BlockingToggleKeys.KEY_BLOCK_X_NOTIFICATIONS to R.string.in_app_surface_notifications_label,
        BlockingToggleKeys.KEY_BLOCK_SNAP_SPOTLIGHT to R.string.in_app_surface_spotlight_label,
        BlockingToggleKeys.KEY_BLOCK_SNAP_STORIES to R.string.in_app_surface_stories_label,
        BlockingToggleKeys.KEY_BLOCK_SNAP_MAP to R.string.in_app_surface_map_label,
        BlockingToggleKeys.KEY_BLOCK_SNAP_FOLLOWING to R.string.in_app_surface_following_label,
        BlockingToggleKeys.KEY_BLOCK_FB_REELS to R.string.in_app_surface_reels_label,
        BlockingToggleKeys.KEY_BLOCK_FB_MARKETPLACE to R.string.in_app_surface_marketplace_label,
    )

    /** Returns the display label for a rule key, or null when the key is unknown. */
    fun label(context: Context, baseKey: String): String? =
        LABELS[baseKey]?.let(context::getString)
}
