/*
 * Switchly
 * Copyright (C) 2025-2026 Saltyy
 * Copyright (C) 2026 Switchly Contributors
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

package at.saltyy.switchly.feature.picker

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * Small in-memory icon cache for App Picker lists.
 * PackageManager.getApplicationIcon() may perform a binder/package-manager lookup and must not run from RecyclerView.bind() on the main thread.
 * Cache hits stay synchronous; misses are resolved on a small worker pool and delivered back on the main thread. Pending requests for the same package are coalesced so fast scrolling cannot queue duplicate package-manager work.
 */
object AppIconCache {

    private val cache = LruCache<String, Drawable>(128)
    private val cacheLock = Any()
    private val pendingLock = Any()
    private val pending = HashMap<String, MutableList<(Drawable) -> Unit>>()

    private val executor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "SwitchlyAppIcons").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    fun getCached(context: Context, packageName: String): Drawable? {
        val cached = synchronized(cacheLock) { cache.get(packageName) } ?: return null
        return copyDrawable(context, cached)
    }

    fun placeholder(context: Context): Drawable {
        return ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
            ?: throw IllegalStateException("Missing default app icon")
    }

    fun load(context: Context, packageName: String, onLoaded: (Drawable) -> Unit) {
        getCached(context, packageName)?.let {
            onLoaded(it)
            return
        }

        val appContext = context.applicationContext
        val shouldStart = synchronized(pendingLock) {
            val callbacks = pending[packageName]
            if (callbacks != null) {
                callbacks += onLoaded
                false
            } else {
                pending[packageName] = mutableListOf(onLoaded)
                true
            }
        }
        if (!shouldStart) return

        executor.execute {
            val loaded = try {
                appContext.packageManager.getApplicationIcon(packageName)
            } catch (_: Throwable) {
                placeholder(appContext)
            }

            synchronized(cacheLock) {
                cache.put(packageName, loaded)
            }

            mainHandler.post {
                val callbacks = synchronized(pendingLock) {
                    pending.remove(packageName).orEmpty()
                }
                callbacks.forEach { callback ->
                    callback(copyDrawable(appContext, loaded))
                }
            }
        }
    }

    private fun copyDrawable(context: Context, drawable: Drawable): Drawable {
        return drawable.constantState
            ?.newDrawable(context.resources)
            ?.mutate()
            ?: drawable
    }
}
