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

package com.oliver.loqin.feature.websites

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Favicon cache for website rule tiles. Mirrors [com.oliver.loqin.feature.picker.AppIconCache]:
 * synchronous cache hits on the main thread, async misses resolved on a small worker pool with
 * per-host request coalescing. Icons are cached in memory and on disk under `filesDir/website_icons`.
 *
 * Lookup order per host: Google S2 favicon service, DuckDuckGo ip3, then a deterministic
 * monogram fallback so tiles never show a blank.
 */
object WebsiteIconCache {

    private const val DISK_DIR = "website_icons"
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000

    private val cache = LruCache<String, Drawable>(96)
    private val cacheLock = Any()
    private val pendingLock = Any()
    private val pending = HashMap<String, MutableList<(Drawable) -> Unit>>()
    private val failedHosts = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private val executor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "LoqInWebsiteIcons").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    fun getCached(context: Context, host: String): Drawable? {
        val key = key(host) ?: return null
        synchronized(cacheLock) { cache.get(key) }?.let { return it }
        return readFromDisk(context, key)?.also { synchronized(cacheLock) { cache.put(key, it) } }
    }

    fun load(context: Context, host: String, onLoaded: (Drawable) -> Unit) {
        val key = key(host) ?: run { onLoaded(placeholder(context, host)); return }
        getCached(context, host)?.let { onLoaded(it); return }

        val appContext = context.applicationContext
        val shouldStart = synchronized(pendingLock) {
            val callbacks = pending[key]
            if (callbacks != null) {
                callbacks += onLoaded
                false
            } else {
                pending[key] = mutableListOf(onLoaded)
                true
            }
        }
        if (!shouldStart) return

        executor.execute {
            val loaded = readFromDisk(appContext, key)
                ?: fetchRemote(appContext, key, host)
                ?: placeholder(appContext, host)
            synchronized(cacheLock) { cache.put(key, loaded) }
            mainHandler.post {
                val callbacks = synchronized(pendingLock) { pending.remove(key).orEmpty() }
                callbacks.forEach { it(loaded) }
            }
        }
    }

    fun placeholder(context: Context, host: String): Drawable {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val density = context.resources.displayMetrics.density

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = monogramColor(host)
        }
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(rect, 22f * density, 22f * density, bg)

        val letter = host.trim().lowercase().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = size * 0.46f
        }
        val baseline = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(letter, size / 2f, baseline, textPaint)

        return BitmapDrawable(context.resources, bitmap)
    }

    private fun key(host: String): String? {
        val clean = host.trim().lowercase()
            .removePrefix("www.")
            .substringBefore('/')
            .trimEnd('.')
        return clean.takeIf { it.isNotBlank() && '.' in it }
    }

    private fun fetchRemote(context: Context, key: String, host: String): Drawable? {
        if (!failedHosts.add(key)) return null
        val sources = listOf(
            "https://www.google.com/s2/favicons?domain=$key&sz=64",
            "https://icons.duckduckgo.com/ip3/$key.ico",
        )
        for (url in sources) {
            val bytes = download(url) ?: continue
            val bitmap = decodeBitmap(bytes) ?: continue
            val drawable = BitmapDrawable(context.resources, bitmap)
            writeToDisk(context, key, bytes)
            return drawable
        }
        return null
    }

    private fun download(url: String): ByteArray? {
        return runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "LoqIn/1.0")
            try {
                if (connection.responseCode != 200) return null
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private fun decodeBitmap(bytes: ByteArray): Bitmap? {
        if (bytes.isEmpty()) return null
        return runCatching {
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            if (raw.width <= 0 || raw.height <= 0) return null
            if (raw.width == 96 && raw.height == 96) return raw
            Bitmap.createScaledBitmap(raw, 96, 96, true).also {
                if (it !== raw) raw.recycle()
            }
        }.getOrNull()
    }

    private fun diskFile(context: Context, key: String): File {
        val dir = File(context.filesDir, DISK_DIR)
        if (!dir.exists()) dir.mkdirs()
        val safe = key.replace(Regex("[^a-z0-9._-]"), "_")
        return File(dir, "$safe.png")
    }

    private fun readFromDisk(context: Context, key: String): Drawable? {
        val file = diskFile(context, key)
        if (!file.exists()) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        val bitmap = decodeBitmap(bytes) ?: return null
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun writeToDisk(context: Context, key: String, bytes: ByteArray) {
        runCatching { FileOutputStream(diskFile(context, key)).use { it.write(bytes) } }
    }

    private fun monogramColor(host: String): Int {
        val palette = intArrayOf(
            0xFFE57373.toInt(), 0xFF64B5F6.toInt(), 0xFF81C784.toInt(),
            0xFFFFB74D.toInt(), 0xFFBA68C8.toInt(), 0xFF4DD0E1.toInt(),
            0xFFF06292.toInt(), 0xFFA1887F.toInt(),
        )
        val idx = (host.lowercase().hashCode() and 0x7FFFFFFF) % palette.size
        return palette[idx]
    }
}
