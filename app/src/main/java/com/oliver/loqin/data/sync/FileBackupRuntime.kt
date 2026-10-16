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

package com.oliver.loqin.data.sync

import android.content.Context
import android.net.Uri
import com.oliver.loqin.BuildConfig
import com.oliver.loqin.data.prefs.AppLogStore
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** The picked file is not a Loq In backup (not JSON, another app's file, or too large). */
class NotALoqInBackupException(message: String) : IllegalArgumentException(message)

object FileBackupRuntime {

    private const val TAG = "FileBackupRuntime"
    private const val FIELD_APP_VERSION_CODE = "app_version_code"
    private const val FIELD_APP_VERSION_NAME = "app_version_name"
    private const val FIELD_EXPORTED_AT = "exported_at"
    private const val FIELD_PAYLOAD = "payload"
    private const val FIELD_SCHEMA = "schema"
    private const val SCHEMA = "loqin_backup_v1"

    // Builds before the rebrand wrote this schema name with the same layout.
    private val READABLE_SCHEMAS = setOf(SCHEMA, "switchly_backup_v1")
    private const val MAX_BACKUP_CHARS = 64 * 1024 * 1024

    fun writeLocalBackupToUri(ctx: Context, uri: Uri): Result<Unit> =
        writeLocalBackupToUri(ctx, uri, BackupSelection.full())

    fun writeLocalBackupToUri(ctx: Context, uri: Uri, selection: BackupSelection): Result<Unit> {
        return runCatching {
            val root = JSONObject()
                .put(FIELD_SCHEMA, SCHEMA)
                .put(FIELD_EXPORTED_AT, System.currentTimeMillis())
                .put(FIELD_APP_VERSION_NAME, BuildConfig.VERSION_NAME)
                .put(FIELD_APP_VERSION_CODE, BuildConfig.VERSION_CODE)
                .put(FIELD_PAYLOAD, toJsonValue(LocalBackupPayload.createLocalBackupPayload(ctx, selection)))

            ctx.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                    writer.write(root.toString(2))
                }
            } ?: error("Could not open backup file")
        }.onFailure { AppLogStore.append(ctx, TAG, "File backup failed", it) }
    }

    fun restoreBackupFromUri(ctx: Context, uri: Uri): Result<Unit> {
        return runCatching {
            LocalBackupPayload.applyBackupPayload(ctx, readBackupPayloadFromUri(ctx, uri).getOrThrow())
        }.onFailure { AppLogStore.appendRateLimited(ctx, TAG, "File restore failed", it) }
    }

    fun readBackupPayloadFromUri(ctx: Context, uri: Uri): Result<Map<*, *>> {
        return runCatching {
            val raw = ctx.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    readCapped(reader)
                }
            } ?: error("Could not open backup file")

            val root = try {
                JSONObject(raw)
            } catch (e: JSONException) {
                throw NotALoqInBackupException("Backup file is not JSON")
            }
            val schema = root.optString(FIELD_SCHEMA, "")
            if (schema.isNotEmpty() && schema !in READABLE_SCHEMAS) {
                throw NotALoqInBackupException("Unknown backup schema: $schema")
            }
            val payload = when {
                root.has(FIELD_PAYLOAD) -> fromJsonValue(root.get(FIELD_PAYLOAD)) as? Map<*, *>
                else -> fromJsonValue(root) as? Map<*, *>
            }
            if (payload == null || !LocalBackupPayload.hasBackupPayload(payload)) {
                throw NotALoqInBackupException("No Loq In backup data in file")
            }
            payload
        }.onFailure { AppLogStore.appendRateLimited(ctx, TAG, "Reading file backup preview failed", it) }
    }

    private fun readCapped(reader: BufferedReader): String {
        val out = StringBuilder()
        val buffer = CharArray(64 * 1024)
        while (true) {
            val read = reader.read(buffer)
            if (read < 0) break
            out.append(buffer, 0, read)
            if (out.length > MAX_BACKUP_CHARS) {
                throw NotALoqInBackupException("Backup file is too large")
            }
        }
        return out.toString()
    }

    fun restoreBackupPayload(ctx: Context, payload: Map<*, *>): Result<Unit> {
        return runCatching {
            LocalBackupPayload.applyBackupPayload(ctx, payload)
        }.onFailure { AppLogStore.appendRateLimited(ctx, TAG, "File restore failed", it) }
    }

    private fun toJsonValue(value: Any?): Any {
        return when (value) {
            null -> JSONObject.NULL
            is Boolean -> value
            is Number -> value
            is String -> value
            is Map<*, *> -> JSONObject().apply {
                for ((rawKey, rawValue) in value) {
                    val key = rawKey as? String ?: continue
                    put(key, toJsonValue(rawValue))
                }
            }
            is Iterable<*> -> JSONArray().apply {
                value.forEach { put(toJsonValue(it)) }
            }
            is Array<*> -> JSONArray().apply {
                value.forEach { put(toJsonValue(it)) }
            }
            else -> value.toString()
        }
    }

    private fun fromJsonValue(value: Any?): Any? {
        return when (value) {
            null, JSONObject.NULL -> null
            is JSONObject -> buildMap<String, Any?> {
                value.keys().forEach { key ->
                    put(key, fromJsonValue(value.get(key)))
                }
            }
            is JSONArray -> buildList {
                for (i in 0 until value.length()) {
                    add(fromJsonValue(value.get(i)))
                }
            }
            else -> value
        }
    }
}
