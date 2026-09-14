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

package com.oliver.loqin.feature.inbox

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.oliver.loqin.R
import com.oliver.loqin.util.EditingLockGuard
import android.widget.Toast
import com.oliver.loqin.data.prefs.BlockedInboxStore
import com.oliver.loqin.data.prefs.SessionMissedNotificationsStore
import com.oliver.loqin.data.prefs.BlockedNotificationEvent
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.theme.CustomAccentApplier
import com.oliver.loqin.ui.EdgeToEdgeUtils
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.ui.showWarnPillOnContent
import com.oliver.loqin.ui.attachEditDeleteSwipe
import com.oliver.loqin.ui.updateSelectionSubtitle
import com.oliver.loqin.ui.dialog.showDestructiveAccented
import com.oliver.loqin.ui.dialog.styleLoqInDialogButtons
import com.oliver.loqin.widget.BlockedNotificationsWidgetProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.DateFormat
import java.util.Date

class BlockedInboxActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var empty: View
    private lateinit var toolbar: MaterialToolbar

    private val prefs by lazy { getSharedPreferences("loqin_prefs", MODE_PRIVATE) }

    private var allItems: List<BlockedNotificationEvent> = emptyList()
    private var visibleItems: List<BlockedNotificationEvent> = emptyList()

    private var sortNewestFirst: Boolean = true
    private var appFilter: String? = null

    private var selectionMode: Boolean = false
    private val selectedKeys: MutableSet<String> = linkedSetOf()

    private val dateFmt by lazy { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked_inbox)

        // Ensure selection checkboxes and other widgets never fall back to OEM green in CUSTOM accent mode.
        CustomAccentApplier.applyIfNeeded(this)

        toolbar = findViewById(R.id.toolbar)
        EdgeToEdgeUtils.setupClassic(activity = this, toolbar = toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setBackgroundColor(AccentColor.getToolbarColor(this))

        sortNewestFirst = prefs.getBoolean(KEY_SORT_NEWEST, true)
        appFilter = prefs.getString(KEY_APP_FILTER, null)?.takeIf { it.isNotBlank() }
        intent.getStringExtra(EXTRA_APP_FILTER)?.takeIf { it.isNotBlank() }?.let { forced ->
            appFilter = forced
        }

        recycler = findViewById(R.id.recyclerInbox)
        empty = findViewById(R.id.tvEmpty)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.attachEditDeleteSwipe(
            canSwipe = { !selectionMode && !isReadOnly() },
            editIconRes = R.drawable.open_in_new_24,
            onEdit = { position -> visibleItems.getOrNull(position)?.let(::showDetailDialog) },
            onDelete = { position -> visibleItems.getOrNull(position)?.let(::confirmDeleteSingle) }
        )

        setupSessionMissedSwitch()
        load()
    }

    /**
     * The "show missed notifications after a session" setting used to live in the
     * overflow menu where it was easy to miss. Surface it as a visible switch at
     * the top of the inbox.
     */
    private fun setupSessionMissedSwitch() {
        val switch = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchSessionMissed)
            ?: return
        switch.isChecked = SessionMissedNotificationsStore.isFeatureEnabled(this)
        switch.setOnCheckedChangeListener { _, checked ->
            SessionMissedNotificationsStore.setFeatureEnabled(this, checked)
            val msg = if (checked) {
                R.string.pref_show_session_missed_notifications_title
            } else {
                R.string.session_missed_notifications_disabled_hint
            }
            showWarnPillOnContent(msg)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isReadOnly() && selectionMode) {
            exitSelectionMode()
        }
        invalidateOptionsMenu()
    }

    private fun isReadOnly(): Boolean {
        return EditingLockGuard.isLocked(this)
    }

    private fun load() {
        allItems = runCatching { BlockedInboxStore.getAll(this) }.getOrDefault(emptyList())
        applyFilterSort()
    }

    private fun applyFilterSort() {
        val filtered = if (appFilter.isNullOrBlank()) {
            allItems
        } else {
            allItems.filter { it.pkg == appFilter }
        }

        val sorted = if (sortNewestFirst) {
            filtered.sortedByDescending { it.timeMillis }
        } else {
            filtered.sortedBy { it.timeMillis }
        }

        visibleItems = sorted
        selectedKeys.retainAll(visibleItems.map { eventKey(it) }.toSet())
        if (selectionMode && selectedKeys.isEmpty()) {
            selectionMode = false
        }
        recycler.adapter = InboxAdapter(
            items = visibleItems,
            isSelectionMode = { selectionMode },
            isSelected = { e -> selectedKeys.contains(eventKey(e)) },
            onRowClick = { e ->
                if (selectionMode) {
                    toggleSelected(e)
                } else {
                    showDetailDialog(e)
                }
            },
            onRowLongPress = { e ->
                if (isReadOnly()) {
                    false
                } else if (!selectionMode) {
                    enterSelectionMode(preselect = e)
                    true
                } else {
                    false
                }
            }
        )

        empty.visibility = if (visibleItems.isEmpty()) View.VISIBLE else View.GONE

        toolbar.updateSelectionSubtitle(selectionMode, selectedKeys.size, null)
        renderFilterChips()
        invalidateOptionsMenu()
    }

    private fun enterSelectionMode(preselect: BlockedNotificationEvent? = null) {
        if (isReadOnly()) {
            return
        }
        selectionMode = true
        selectedKeys.clear()
        preselect?.let { selectedKeys.add(eventKey(it)) }
        toolbar.updateSelectionSubtitle(true, selectedKeys.size)
        invalidateOptionsMenu()
        recycler.adapter?.let { it.notifyItemRangeChanged(0, it.itemCount) }
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selectedKeys.clear()
        toolbar.updateSelectionSubtitle(false, 0, null)
        invalidateOptionsMenu()
        recycler.adapter?.let { it.notifyItemRangeChanged(0, it.itemCount) }
    }

    private fun toggleSelected(e: BlockedNotificationEvent) {
        val k = eventKey(e)
        if (selectedKeys.contains(k)) selectedKeys.remove(k) else selectedKeys.add(k)
        recycler.adapter?.let { it.notifyItemRangeChanged(0, it.itemCount) }

        // keep subtitle useful in selection mode
        if (selectionMode) {
            toolbar.updateSelectionSubtitle(true, selectedKeys.size)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun showDetailDialog(e: BlockedNotificationEvent) {
        val v = layoutInflater.inflate(R.layout.dialog_blocked_inbox_detail, FrameLayout(this), false)
        val tv = v.findViewById<TextView>(R.id.tvDetail)
        val detailIcon = v.findViewById<ImageView>(R.id.imgDetailIcon)
        val detailTitle = v.findViewById<TextView>(R.id.tvDetailTitle)
        val detailSubtitle = v.findViewById<TextView>(R.id.tvDetailSubtitle)

        fun line(label: String, value: String?): String {
            val clean = value?.trim()?.takeIf { it.isNotBlank() } ?: "-"
            return "$label: $clean"
        }

        val appName = appLabel(e.pkg)
        detailTitle.text = appName
        detailSubtitle.text = dateFmt.format(Date(e.timeMillis))
        appIcon(e.pkg)?.let { detailIcon.setImageDrawable(it) } ?: detailIcon.setImageResource(R.drawable.notifications_24)

        val profile = e.profile.takeIf { it.isNotBlank() }
        val title = e.title.takeIf { it.isNotBlank() }
        val text = e.text.takeIf { it.isNotBlank() }
        val bigText = e.bigText.takeIf { it.isNotBlank() }
        val subText = e.subText.takeIf { it.isNotBlank() }
        val summaryText = e.summaryText.takeIf { it.isNotBlank() }

        val lines = mutableListOf<String>()
        profile?.let { lines += line(getString(R.string.blocked_inbox_field_profile), it) }
        lines += line(getString(R.string.blocked_inbox_field_reason), e.reason)
        lines += line(getString(R.string.blocked_inbox_field_title), title)
        lines += line(getString(R.string.blocked_inbox_field_text), text)

        bigText?.let { lines += line(getString(R.string.blocked_inbox_field_big_text), it) }
        subText?.let { lines += line(getString(R.string.blocked_inbox_field_sub_text), it) }
        summaryText?.let { lines += line(getString(R.string.blocked_inbox_field_summary_text), it) }

        tv.text = lines.joinToString("\n\n")

        val detailDialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.blocked_inbox_detail_title))
            .setView(v)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        detailDialog.setOnShowListener {
            detailDialog.styleLoqInDialogButtons()
        }
        detailDialog.show()
    }

    private fun confirmDeleteSingle(event: BlockedNotificationEvent) {
        if (isReadOnly()) {
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete)
            .setMessage(getString(R.string.destructive_cannot_be_undone))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                if (isReadOnly()) {
                    return@setPositiveButton
                }
                BlockedInboxStore.remove(this, event)
                BlockedNotificationsWidgetProvider.refreshAll(this)
                load()
            }
            .showDestructiveAccented()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_blocked_inbox, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val readOnly = isReadOnly()
        menu.findItem(R.id.action_cancel_select)?.isVisible = selectionMode && !readOnly
        menu.findItem(R.id.action_delete)?.isVisible = !readOnly
        // In selection mode, delete action is "Delete selected". Outside, it enters selection mode.
        menu.findItem(R.id.action_delete)?.title =
            if (selectionMode) getString(R.string.delete) else getString(R.string.select)
        menu.findItem(R.id.action_clear_all)?.isVisible = !readOnly && !selectionMode && allItems.isNotEmpty()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (selectionMode) {
                    exitSelectionMode(); true
                } else {
                    finish(); true
                }
            }

            R.id.action_cancel_select -> {
                exitSelectionMode(); true
            }

            R.id.action_delete -> {
                if (isReadOnly()) {
                    return true
                }
                if (selectionMode) {
                    confirmDeleteSelected(); true
                } else {
                    enterSelectionMode(); true
                }
            }

            R.id.action_clear_all -> {
                confirmClearAll(); true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Inline app filter + sort at the top of the list. Replaces the old dialog:
     * the filter is now always visible and one tap away instead of buried in a menu.
     */
    private fun renderFilterChips() {
        val group = findViewById<ChipGroup>(R.id.filterChipGroup) ?: return
        group.removeAllViews()

        val pkgs = allItems.map { it.pkg }.distinct().sortedBy { appLabel(it).lowercase() }

        group.addView(filterChip(getString(R.string.blocked_inbox_filter_all_apps), appFilter == null) {
            setAppFilter(null)
        })
        pkgs.forEach { pkg ->
            group.addView(filterChip(appLabel(pkg), appFilter == pkg) { setAppFilter(pkg) })
        }

        // Sort toggle lives at the end of the same row so both controls are in one place.
        group.addView(
            filterChip(
                getString(
                    if (sortNewestFirst) R.string.blocked_inbox_sort_newest
                    else R.string.blocked_inbox_sort_oldest
                ),
                selected = false,
            ) {
                sortNewestFirst = !sortNewestFirst
                prefs.edit { putBoolean(KEY_SORT_NEWEST, sortNewestFirst) }
                applyFilterSort()
            }
        )
    }

    private fun filterChip(label: String, selected: Boolean, onClick: () -> Unit): Chip {
        val accent = AccentColor.getAccentColorInt(this)
        val onSurfaceVariant = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            Color.GRAY,
        )
        return Chip(this).apply {
            text = label
            isCheckable = true
            isChecked = selected
            isClickable = true
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            minHeight = dp(40)
            chipStrokeWidth = dp(1).toFloat()
            chipStrokeColor = ColorStateList.valueOf(accent)
            chipBackgroundColor = ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(accent, if (selected) 0x24 else 0x00)
            )
            setTextColor(if (selected) accent else onSurfaceVariant)
            checkedIcon = null
            setOnClickListener {
                onClick()
                applyFilterSort()
            }
        }
    }

    private fun setAppFilter(pkg: String?) {
        appFilter = pkg
        prefs.edit {
            if (pkg.isNullOrBlank()) remove(KEY_APP_FILTER) else putString(KEY_APP_FILTER, pkg)
        }
    }

    private fun confirmClearAll() {
        if (isReadOnly() || allItems.isEmpty()) {
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.blocked_inbox_clear_all)
            .setMessage(getString(R.string.blocked_inbox_clear_all_confirm) + "\n\n" + getString(R.string.destructive_cannot_be_undone))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                if (isReadOnly()) {
                    return@setPositiveButton
                }
                BlockedInboxStore.clear(this)
                BlockedNotificationsWidgetProvider.refreshAll(this)
                exitSelectionMode()
                load()
            }
            .showDestructiveAccented()
    }

    private fun confirmDeleteSelected() {
        if (isReadOnly()) {
            exitSelectionMode()
            return
        }
        if (selectedKeys.isEmpty()) {
            exitSelectionMode()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.blocked_inbox_delete_title))
            .setMessage(resources.getQuantityString(R.plurals.blocked_inbox_delete_selected_confirm_fmt, selectedKeys.size, selectedKeys.size) + "\n\n" + getString(R.string.destructive_cannot_be_undone))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                if (isReadOnly()) {
                    exitSelectionMode()
                    return@setPositiveButton
                }
                val toDelete = visibleItems.filter { selectedKeys.contains(eventKey(it)) }
                toDelete.forEach { BlockedInboxStore.remove(this, it) }
                BlockedNotificationsWidgetProvider.refreshAll(this)
                exitSelectionMode()
                load()
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ -> }
            .showDestructiveAccented()
    }

    private fun eventKey(e: BlockedNotificationEvent): String {
        // Matches the fields used by BlockedInboxStore.remove()
        return listOf(e.timeMillis, e.pkg, e.reason, e.title, e.text).joinToString("|")
    }

    private fun appLabel(pkg: String): String {
        return runCatching {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            val label = packageManager.getApplicationLabel(ai)?.toString().orEmpty().trim()
            if (label.isNotBlank()) label else pkg
        }.getOrElse { pkg }
    }

    private fun appIcon(pkg: String): Drawable? {
        return runCatching {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationIcon(ai)
        }.getOrNull()
    }

    private class InboxAdapter(
        private val items: List<BlockedNotificationEvent>,
        private val isSelectionMode: () -> Boolean,
        private val isSelected: (BlockedNotificationEvent) -> Boolean,
        private val onRowClick: (BlockedNotificationEvent) -> Unit,
        private val onRowLongPress: (BlockedNotificationEvent) -> Boolean,
    ) : RecyclerView.Adapter<InboxAdapter.VH>() {

        private val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val v = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_blocked_inbox_event, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val e = items[position]
            holder.title.text = runCatching {
                val ai = holder.itemView.context.packageManager.getApplicationInfo(e.pkg, 0)
                holder.itemView.context.packageManager.getApplicationLabel(ai).toString()
            }.getOrDefault(e.pkg)

            val preview = listOfNotNull(
                e.title.takeIf { it.isNotBlank() },
                e.text.takeIf { it.isNotBlank() }
            ).joinToString(" — ").ifBlank {
                holder.itemView.context.getString(R.string.blocked_inbox_content_unknown)
            }
            holder.content.text = preview

            val profilePart = e.profile.takeIf { it.isNotBlank() }
                ?.let { " • ${holder.itemView.context.getString(R.string.blocked_inbox_field_profile)}: $it" }
                .orEmpty()

            holder.subtitle.text = holder.itemView.context.getString(
                R.string.blocked_inbox_subtitle_compact_fmt,
                fmt.format(Date(e.timeMillis)),
                profilePart
            )

            // Resolve app icon safely (some package names may no longer exist).
            val icon = runCatching {
                holder.itemView.context.packageManager.getApplicationIcon(e.pkg)
            }.getOrNull()
            if (icon != null) {
                holder.icon.setImageDrawable(icon)
            } else {
                holder.icon.setImageResource(R.drawable.notifications_24)
            }

            val selecting = isSelectionMode()
            val selected = selecting && isSelected(e)
            holder.checkbox.visibility = if (selecting) View.VISIBLE else View.GONE
            holder.checkbox.isChecked = selected
            (holder.itemView as? MaterialCardView)?.let { card ->
                val ctx = holder.itemView.context
                val accent = AccentColor.getAccentColorInt(ctx)
                if (selected) {
                    card.strokeWidth = (2 * holder.itemView.resources.displayMetrics.density).toInt().coerceAtLeast(1)
                    card.strokeColor = accent
                    card.setCardBackgroundColor(ColorUtils.setAlphaComponent(accent, 0x14))
                } else {
                    card.strokeWidth = (1 * holder.itemView.resources.displayMetrics.density).toInt().coerceAtLeast(1)
                    card.strokeColor = ContextCompat.getColor(ctx, R.color.foqos_outline_variant)
                    card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.foqos_surface))
                }
            }
            holder.more.visibility = if (selecting) View.GONE else View.VISIBLE

            holder.itemView.setOnClickListener { onRowClick(e) }
            holder.itemView.setOnLongClickListener { onRowLongPress(e) }
        }

        override fun getItemCount(): Int = items.size

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.imgIcon)
            val title: TextView = v.findViewById(R.id.tvTitle)
            val content: TextView = v.findViewById(R.id.tvContent)
            val subtitle: TextView = v.findViewById(R.id.tvSubtitle)
            val checkbox: com.google.android.material.checkbox.MaterialCheckBox = v.findViewById(R.id.cbSelect)
            val more: ImageView = v.findViewById(R.id.imgMore)
        }
    }

    companion object {
        const val EXTRA_APP_FILTER = "extra_app_filter"
        private const val KEY_SORT_NEWEST = "blocked_inbox_sort_newest"
        private const val KEY_APP_FILTER = "blocked_inbox_app_filter"
    }
}
