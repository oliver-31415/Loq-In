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

package com.oliver.loqin.feature.pause

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.oliver.loqin.R
import com.oliver.loqin.blocking.PauseGrants
import com.oliver.loqin.data.prefs.PauseRuleStore
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.ui.EdgeToEdgeUtils

/**
 * "Pause before opening": a countdown in front of an app the profile marked for soft friction.
 * Leave (the default) goes home; Open unlocks after the wait and grants the current visit.
 */
class PauseActivity : AppCompatActivity() {

    private var timer: CountDownTimer? = null
    private var decided = false
    private var remainingMs = 0L
    private var renderer: ((Int) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pause)
        EdgeToEdgeUtils.applyThemedSystemBars(this)

        val pkg = intent.getStringExtra(EXTRA_PKG).orEmpty()
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty().ifBlank { pkg }
        val waitSeconds = intent.getIntExtra(EXTRA_WAIT_SECONDS, PauseRuleStore.DEFAULT_BASE_SECONDS)
        val opensToday = intent.getIntExtra(EXTRA_OPENS_TODAY, 0)
        if (pkg.isBlank()) {
            finish()
            return
        }

        val accent = AccentColor.getAccentColorInt(this)
        runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull()
            ?.let { findViewById<ImageView>(R.id.pauseAppIcon).setImageDrawable(it) }
        findViewById<TextView>(R.id.pauseOpensToday).text = if (opensToday > 1) {
            resources.getQuantityString(R.plurals.pause_opens_today, opensToday, label, opensToday)
        } else {
            getString(R.string.pause_first_open_fmt, label)
        }
        val leftThisWeek = PauseRuleStore.outcomesForLastDays(this, PauseRuleStore.Outcome.LEFT)
        val continuedThisWeek = PauseRuleStore.outcomesForLastDays(this, PauseRuleStore.Outcome.CONTINUED)
        if (PauseRuleStore.shouldShowWeeklyOutcome(leftThisWeek, continuedThisWeek)) {
            findViewById<TextView>(R.id.pauseWeeklyOutcome).apply {
                text = resources.getQuantityString(
                    R.plurals.pause_weekly_outcome,
                    leftThisWeek + continuedThisWeek,
                    leftThisWeek,
                    leftThisWeek + continuedThisWeek,
                )
                isVisible = true
            }
        }
        val countdown = findViewById<TextView>(R.id.pauseCountdown).apply { setTextColor(accent) }
        val leave = findViewById<MaterialButton>(R.id.pauseLeave).apply {
            backgroundTintList = ColorStateList.valueOf(accent)
            setOnClickListener { leave() }
        }
        val open = findViewById<MaterialButton>(R.id.pauseContinue).apply {
            setTextColor(accent)
            setOnClickListener { openApp(pkg) }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })

        fun render(secondsLeft: Int) {
            countdown.text = secondsLeft.coerceAtLeast(0).toString()
            open.isEnabled = secondsLeft <= 0
            open.text = if (secondsLeft > 0) {
                getString(R.string.pause_continue_wait_fmt, label, secondsLeft)
            } else {
                getString(R.string.pause_continue_fmt, label)
            }
        }
        renderer = ::render
        remainingMs = savedInstanceState?.getLong(STATE_REMAINING_MS) ?: (waitSeconds * 1_000L)
        render(((remainingMs + 999) / 1_000).toInt())
        leave.requestFocus()
    }

    // The wait only counts down while the pause is actually on screen: apps that open their own
    // screens on launch (intro pages, account pickers) can cover it, and the countdown used to
    // finish underneath them.
    override fun onResume() {
        super.onResume()
        if (remainingMs <= 0L) return
        timer?.cancel()
        timer = object : CountDownTimer(remainingMs, 250L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMs = millisUntilFinished
                renderer?.invoke(((millisUntilFinished + 999) / 1_000).toInt())
            }

            override fun onFinish() {
                remainingMs = 0L
                renderer?.invoke(0)
            }
        }.start()
    }

    override fun onPause() {
        timer?.cancel()
        timer = null
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_REMAINING_MS, remainingMs)
    }

    private fun leave() {
        if (!decided) {
            decided = true
            PauseRuleStore.recordOutcome(this, PauseRuleStore.Outcome.LEFT)
        }
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        finish()
    }

    private fun openApp(pkg: String) {
        if (!decided) {
            decided = true
            PauseRuleStore.recordOutcome(this, PauseRuleStore.Outcome.CONTINUED)
        }
        PauseGrants.grant(pkg)
        // The app's task is right underneath; finishing returns to it.
        finish()
    }

    override fun onStart() {
        super.onStart()
        PauseGrants.onPauseVisible(intent.getStringExtra(EXTRA_PKG))
    }

    override fun onStop() {
        PauseGrants.onPauseVisible(null)
        super.onStop()
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_WAIT_SECONDS = "wait_seconds"
        private const val EXTRA_OPENS_TODAY = "opens_today"
        private const val STATE_REMAINING_MS = "remaining_ms"

        fun show(context: Context, pkg: String, label: String, waitSeconds: Int, opensToday: Int) {
            context.startActivity(
                Intent(context, PauseActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                    .putExtra(EXTRA_PKG, pkg)
                    .putExtra(EXTRA_LABEL, label)
                    .putExtra(EXTRA_WAIT_SECONDS, waitSeconds)
                    .putExtra(EXTRA_OPENS_TODAY, opensToday)
            )
        }
    }
}
