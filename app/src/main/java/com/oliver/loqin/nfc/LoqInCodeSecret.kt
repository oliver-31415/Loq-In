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

package com.oliver.loqin.nfc

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Per-install secret carried by printable Loq In codes (QR and direct `loqin://` barcodes).
 *
 * `loqin://` links are public, so without a secret anyone could make a working "disable" code
 * with any QR generator or a web link. Codes created by this install carry `k=<secret>`;
 * unmanaged scans and external links without it are rejected. The secret lives in
 * `loqin_prefs` under a `qr_` key, so backups (Keys and codes) carry it to a restored device.
 */
object LoqInCodeSecret {

    const val QUERY_KEY = "k"

    private const val PREFS = "loqin_prefs"
    private const val KEY_SECRET = "qr_code_secret"
    private const val SECRET_BYTES = 16

    /** Returns the secret, creating it on first use. */
    fun getOrCreate(context: Context): String {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.getString(KEY_SECRET, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val bytes = ByteArray(SECRET_BYTES).also { SecureRandom().nextBytes(it) }
        val secret = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        sp.edit { putString(KEY_SECRET, secret) }
        return secret
    }

    /** Adds (or replaces) the secret on a canonical `loqin://` command URI. */
    fun sign(context: Context, uri: String): String {
        val parsed = Uri.parse(uri)
        val builder = parsed.buildUpon().clearQuery()
        parsed.queryParameterNames
            .filter { it != QUERY_KEY }
            .forEach { name -> parsed.getQueryParameters(name).forEach { builder.appendQueryParameter(name, it) } }
        return builder.appendQueryParameter(QUERY_KEY, getOrCreate(context)).build().toString()
    }

    /** True when [uri] carries this install's secret. Never creates a secret. */
    fun isSigned(context: Context, uri: Uri?): Boolean {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SECRET, null)
        val presented = runCatching { uri?.getQueryParameter(QUERY_KEY) }.getOrNull()
        return matches(stored, presented)
    }

    /** Constant-time comparison; a missing secret on either side never matches. */
    fun matches(stored: String?, presented: String?): Boolean {
        if (stored.isNullOrBlank() || presented.isNullOrBlank()) return false
        return MessageDigest.isEqual(stored.toByteArray(Charsets.UTF_8), presented.toByteArray(Charsets.UTF_8))
    }
}
