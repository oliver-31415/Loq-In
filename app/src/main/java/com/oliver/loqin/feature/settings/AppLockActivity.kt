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

package com.oliver.loqin.feature.settings

import android.content.Context
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.AppLogStore
import com.oliver.loqin.security.AppLockManager
import com.oliver.loqin.security.AppLockStore
import com.oliver.loqin.theme.CustomAccentApplier
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.ui.dialog.PinEntryDialog
import com.oliver.loqin.util.ActivityTransitionCompat
import com.oliver.loqin.util.LocaleHelper

/**
 * App-lock challenge shown when a protected screen (or Loq In itself) is opened.
 * Presents the shared [PinEntryDialog] verify flow so it looks and behaves like
 * every other dialog in the app, with biometrics offered as an extra action.
 */
class AppLockActivity : AppCompatActivity() {

    private var pinDialog: AlertDialog? = null
    private var unlocked = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)

        if (!AppLockStore.isEnabled(this)) {
            AppLockManager.markUnlocked()
            finish()
            ActivityTransitionCompat.finishWithoutAnimation(this)
            return
        }

        setContentView(R.layout.activity_app_lock)
        CustomAccentApplier.applyIfNeeded(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (unlocked) {
                    finish()
                } else {
                    AppLockManager.clearPromptFlag()
                    finishAffinity()
                }
            }
        })

        val offerBiometric = AppLockStore.isBiometricEnabled(this) && isBiometricAvailable()

        pinDialog = PinEntryDialog.showVerify(
            activity = this,
            titleRes = R.string.app_lock_pin_hint_enter,
            subtitleRes = R.string.app_lock_unlock_message,
            incorrectRes = R.string.app_lock_pin_incorrect,
            verifyLength = AppLockStore.pinLength(this),
            validator = { AppLockStore.matchesPin(this, it) },
            neutralButtonRes = if (offerBiometric) R.string.app_lock_biometric_button else null,
            neutralAction = if (offerBiometric) ({ promptBiometric() }) else null,
            onCancel = {
                if (!unlocked) {
                    AppLockManager.clearPromptFlag()
                    finishAffinity()
                }
            },
        ) { unlockSuccess() }

        if (offerBiometric && savedInstanceState == null) {
            pinDialog?.window?.decorView?.post { promptBiometric() }
        }
    }

    private fun isBiometricAvailable(): Boolean {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        return BiometricManager.from(this).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun promptBiometric() {
        if (!isBiometricAvailable()) {
            AppLogStore.append(this, "AppLock", "Unlock failed reason=biometric_unavailable")
            return
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked = true
                    pinDialog?.dismiss()
                    unlockSuccess()
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_lock_biometric_prompt_title))
            .setSubtitle(getString(R.string.app_lock_biometric_prompt_subtitle))
            .setNegativeButtonText(getString(R.string.cancel))
            .build()

        prompt.authenticate(info)
    }

    private fun unlockSuccess() {
        unlocked = true
        AppLockManager.markUnlocked()
        setResult(RESULT_OK)
        finish()
        ActivityTransitionCompat.finishWithoutAnimation(this)
    }

    override fun onDestroy() {
        if (!unlocked) {
            AppLockManager.clearPromptFlag()
        }
        super.onDestroy()
    }
}
