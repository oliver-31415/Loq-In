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

package com.oliver.loqin.feature.support

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.AppLogStore
import com.oliver.loqin.theme.AccentColor
import com.oliver.loqin.ui.EdgeToEdgeUtils
import com.oliver.loqin.ui.ThemeUtils
import com.oliver.loqin.ui.showWarnPill
import com.oliver.loqin.ui.dialog.LoqInDialogOption
import com.oliver.loqin.ui.dialog.showAccented
import com.oliver.loqin.ui.dialog.showLoqInOptionDialog
import com.oliver.loqin.util.LocaleHelper
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SupportLogActivity : AppCompatActivity() {

    private lateinit var rvSupportLogs: RecyclerView
    private lateinit var cardEmptyLogs: MaterialCardView
    private lateinit var tvEmptyLogs: TextView
    private lateinit var chipGroup: ChipGroup
    private lateinit var searchBoxLayout: TextInputLayout
    private lateinit var etSearch: TextInputEditText

    private lateinit var adapter: SupportLogAdapter

    private var allEntries: List<SupportLogEntry> = emptyList()
    private var selectedTagFilter: String? = null
    private var searchQuery: String = ""
    private var lastTagSignature: String = ""

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AppLogStore.KEY_LINES || key == null) {
            val atBottom = isScrolledToBottom()
            runOnUiThread {
                loadLogs(forceScrollToEnd = atBottom)
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_support_logs)

        setupToolbar()

        rvSupportLogs = findViewById(R.id.rvSupportLogs)
        cardEmptyLogs = findViewById(R.id.cardEmptyLogs)
        tvEmptyLogs = findViewById(R.id.tvEmptyLogs)
        chipGroup = findViewById(R.id.chipGroupFilters)
        searchBoxLayout = findViewById(R.id.searchBoxLayout)
        etSearch = findViewById(R.id.etSearch)

        applySearchBoxAccent()

        etSearch.doAfterTextChanged { text ->
            searchQuery = text?.toString().orEmpty().trim()
            applyFilter(forceScrollToEnd = false)
        }

        adapter = SupportLogAdapter { entry -> onLogItemClicked(entry) }
        val layoutManager = LinearLayoutManager(this)
        rvSupportLogs.layoutManager = layoutManager
        rvSupportLogs.adapter = adapter

        loadLogs(forceScrollToEnd = true)
    }

    override fun onResume() {
        super.onResume()
        applySearchBoxAccent()
        AppLogStore.registerChangeListener(this, prefsListener)
        loadLogs(forceScrollToEnd = false)
    }

    override fun onPause() {
        AppLogStore.unregisterChangeListener(this, prefsListener)
        super.onPause()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarSupportLogs)
        EdgeToEdgeUtils.setupClassic(activity = this, toolbar = toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setBackgroundColor(AccentColor.getToolbarColor(this))
        val foreground = toolbarForegroundColor()
        toolbar.navigationIcon?.mutate()?.setTint(foreground)
        toolbar.setTitleTextColor(foreground)
    }

    private fun applySearchBoxAccent() {
        val accent = AccentColor.getAccentColorInt(this)
        val outlineVariant = ContextCompat.getColor(this, R.color.foqos_outline_variant)
        val states = arrayOf(
            intArrayOf(android.R.attr.state_focused),
            intArrayOf(-android.R.attr.state_focused)
        )
        val colors = intArrayOf(accent, outlineVariant)
        val strokeColorStateList = ColorStateList(states, colors)
        searchBoxLayout.setBoxStrokeColorStateList(strokeColorStateList)
        searchBoxLayout.boxStrokeColor = accent
        searchBoxLayout.defaultHintTextColor = ColorStateList.valueOf(accent)
        searchBoxLayout.hintTextColor = ColorStateList.valueOf(accent)
        searchBoxLayout.setStartIconTintList(ColorStateList.valueOf(accent))
        searchBoxLayout.setEndIconTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 0xCC)))
        etSearch.highlightColor = ColorUtils.setAlphaComponent(accent, 0x40)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            etSearch.textCursorDrawable?.mutate()?.setTint(accent)
            etSearch.textSelectHandle?.mutate()?.setTint(accent)
            etSearch.textSelectHandleLeft?.mutate()?.setTint(accent)
            etSearch.textSelectHandleRight?.mutate()?.setTint(accent)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_support_logs, menu)
        val foreground = toolbarForegroundColor()
        for (i in 0 until menu.size()) {
            menu.getItem(i).icon?.mutate()?.setTint(foreground)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                toggleSearch()
                true
            }
            R.id.action_share -> {
                promptExport(isShare = true)
                true
            }
            R.id.action_copy_all -> {
                promptExport(isShare = false)
                true
            }
            R.id.action_clear_logs -> {
                confirmClearLogs()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleSearch() {
        if (searchBoxLayout.isVisible) {
            searchBoxLayout.visibility = View.GONE
            etSearch.text?.clear()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(etSearch.windowToken, 0)
        } else {
            searchBoxLayout.visibility = View.VISIBLE
            etSearch.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun loadLogs(forceScrollToEnd: Boolean) {
        val lines = AppLogStore.latestLines(this, MAX_STORED_LINES)
        allEntries = lines.map { parseLogEntry(it) }
        updateChips(allEntries)
        applyFilter(forceScrollToEnd)
    }

    private fun updateChips(entries: List<SupportLogEntry>) {
        val tagsWithCount = mutableMapOf<String, Int>()
        var errorCount = 0
        for (entry in entries) {
            if (entry.isError) errorCount++
            if (entry.tag.isNotBlank() && entry.tag != "LOG") {
                tagsWithCount[entry.tag] = (tagsWithCount[entry.tag] ?: 0) + 1
            }
        }

        val tagSignature = "${tagsWithCount.entries.sortedBy { it.key }.joinToString { "${it.key}:${it.value}" }}|errors:$errorCount|total:${entries.size}"
        if (tagSignature == lastTagSignature && chipGroup.childCount > 0) {
            refreshChipSelections()
            return
        }
        lastTagSignature = tagSignature

        val currentSelection = selectedTagFilter ?: TAG_ALL
        chipGroup.removeAllViews()

        val allChip = createFilterChip(
            label = "${getString(R.string.support_logs_filter_all)} (${entries.size})",
            tagValue = TAG_ALL,
            isSelected = currentSelection == TAG_ALL
        )
        chipGroup.addView(allChip)

        if (errorCount > 0 || currentSelection == TAG_ERRORS) {
            val errorChip = createFilterChip(
                label = "${getString(R.string.support_logs_filter_errors)} ($errorCount)",
                tagValue = TAG_ERRORS,
                isSelected = currentSelection == TAG_ERRORS
            )
            chipGroup.addView(errorChip)
        }

        val topTags = tagsWithCount.entries
            .sortedByDescending { it.value }
            .take(12)
            .map { it.key }

        for (tag in topTags) {
            val tagChip = createFilterChip(
                label = "$tag (${tagsWithCount[tag]})",
                tagValue = tag,
                isSelected = currentSelection.equals(tag, ignoreCase = true)
            )
            chipGroup.addView(tagChip)
        }
    }

    private fun createFilterChip(label: String, tagValue: String, isSelected: Boolean): Chip {
        val chip = Chip(this).apply {
            text = label
            isCheckable = false
            isClickable = true
            tag = tagValue
            setOnClickListener {
                selectedTagFilter = tagValue
                refreshChipSelections()
                applyFilter(forceScrollToEnd = false)
            }
        }
        styleFilterChip(chip, isSelected)
        return chip
    }

    private fun refreshChipSelections() {
        val currentSelection = selectedTagFilter ?: TAG_ALL
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as? Chip ?: continue
            val tagVal = chip.tag as? String ?: continue
            val isSelected = if (currentSelection == TAG_ALL || currentSelection == TAG_ERRORS) {
                tagVal == currentSelection
            } else {
                tagVal.equals(currentSelection, ignoreCase = true)
            }
            styleFilterChip(chip, isSelected)
        }
    }

    private fun styleFilterChip(chip: Chip, isSelected: Boolean) {
        val accent = AccentColor.getAccentColorInt(this)
        val onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)
        val surfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.BLACK)
        val isDark = !MaterialColors.isColorLight(surfaceColor)
        val density = resources.displayMetrics.density

        chip.minHeight = (36 * density).toInt()
        chip.minimumHeight = (36 * density).toInt()
        chip.chipCornerRadius = 18 * density
        chip.textSize = 12.5f
        chip.isCheckedIconVisible = false
        chip.isCheckable = false
        chip.setEnsureMinTouchTargetSize(false)

        if (isSelected) {
            val selectedBg = if (isDark) {
                ColorUtils.compositeColors(ColorUtils.setAlphaComponent(accent, 0x36), 0xFF242628.toInt())
            } else {
                ColorUtils.compositeColors(ColorUtils.setAlphaComponent(accent, 0x22), Color.WHITE)
            }
            chip.chipBackgroundColor = ColorStateList.valueOf(selectedBg)
            chip.chipStrokeColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 0x88))
            chip.chipStrokeWidth = 1 * density
            chip.setTextColor(onSurface)
            chip.setTypeface(chip.typeface, Typeface.BOLD)
        } else {
            val surfaceVariant = ContextCompat.getColor(this, R.color.foqos_surface_variant)
            val outlineVariant = ContextCompat.getColor(this, R.color.foqos_outline_variant)
            chip.chipBackgroundColor = ColorStateList.valueOf(surfaceVariant)
            chip.chipStrokeColor = ColorStateList.valueOf(outlineVariant)
            chip.chipStrokeWidth = 1 * density
            chip.setTextColor(ColorUtils.setAlphaComponent(onSurface, 0x88))
            chip.setTypeface(Typeface.create(chip.typeface, Typeface.NORMAL), Typeface.NORMAL)
        }
    }

    private fun applyFilter(forceScrollToEnd: Boolean) {
        val filtered = allEntries.filter { entry ->
            val matchesTag = when (selectedTagFilter) {
                null, TAG_ALL -> true
                TAG_ERRORS -> entry.isError
                else -> entry.tag.equals(selectedTagFilter, ignoreCase = true)
            }
            val matchesQuery = if (searchQuery.isBlank()) {
                true
            } else {
                entry.message.contains(searchQuery, ignoreCase = true) ||
                    entry.tag.contains(searchQuery, ignoreCase = true) ||
                    entry.timestamp.contains(searchQuery, ignoreCase = true)
            }
            matchesTag && matchesQuery
        }

        adapter.submitList(filtered) {
            if (forceScrollToEnd && filtered.isNotEmpty()) {
                rvSupportLogs.scrollToPosition(filtered.size - 1)
            }
        }

        if (filtered.isEmpty()) {
            cardEmptyLogs.visibility = View.VISIBLE
            if (allEntries.isEmpty()) {
                tvEmptyLogs.text = getString(R.string.support_logs_view_empty)
            } else {
                tvEmptyLogs.text = getString(R.string.support_logs_empty_search)
            }
        } else {
            cardEmptyLogs.visibility = View.GONE
        }
    }

    private fun isScrolledToBottom(): Boolean {
        val lm = rvSupportLogs.layoutManager as? LinearLayoutManager ?: return true
        val lastVisible = lm.findLastVisibleItemPosition()
        val total = adapter.itemCount
        return total == 0 || lastVisible >= total - 3
    }

    private fun promptExport(isShare: Boolean) {
        if (allEntries.isEmpty()) {
            findViewById<View>(android.R.id.content).showWarnPill(getString(R.string.support_logs_view_empty))
            return
        }

        val currentFiltered = adapter.currentList
        val options = mutableListOf<Pair<LoqInDialogOption, () -> String>>()

        // Filtered subset option if active
        if (currentFiltered.size < allEntries.size && currentFiltered.isNotEmpty()) {
            options += LoqInDialogOption(
                title = getString(R.string.support_logs_range_filtered),
                summary = getString(R.string.support_logs_range_filtered_desc, currentFiltered.size),
                iconRes = R.drawable.search_24
            ) to { currentFiltered.joinToString("\n") { it.raw } }
        }

        // Today's logs
        val todayPrefix = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val todayEntries = allEntries.filter { it.timestamp.startsWith(todayPrefix) }
        if (todayEntries.isNotEmpty()) {
            options += LoqInDialogOption(
                title = getString(R.string.support_logs_range_today),
                summary = getString(R.string.support_logs_range_today_desc, todayEntries.size),
                iconRes = R.drawable.calendar_month_24
            ) to { todayEntries.joinToString("\n") { it.raw } }
        }

        // All logs
        options += LoqInDialogOption(
            title = getString(R.string.support_logs_range_all),
            summary = getString(R.string.support_logs_range_all_desc, allEntries.size),
            iconRes = R.drawable.list_alt_24
        ) to { allEntries.joinToString("\n") { it.raw } }

        // Last 50 lines
        if (allEntries.size > 50) {
            options += LoqInDialogOption(
                title = getString(R.string.support_logs_range_last_50),
                summary = getString(R.string.support_logs_range_last_n_desc, 50),
                iconRes = R.drawable.description_24
            ) to { allEntries.takeLast(50).joinToString("\n") { it.raw } }
        }

        // Last 100 lines
        if (allEntries.size > 100) {
            options += LoqInDialogOption(
                title = getString(R.string.support_logs_range_last_100),
                summary = getString(R.string.support_logs_range_last_n_desc, 100),
                iconRes = R.drawable.description_24
            ) to { allEntries.takeLast(100).joinToString("\n") { it.raw } }
        }

        // Last 250 lines
        if (allEntries.size > 250) {
            options += LoqInDialogOption(
                title = getString(R.string.support_logs_range_last_250),
                summary = getString(R.string.support_logs_range_last_n_desc, 250),
                iconRes = R.drawable.description_24
            ) to { allEntries.takeLast(250).joinToString("\n") { it.raw } }
        }

        showLoqInOptionDialog(
            title = if (isShare) getString(R.string.support_logs_share_title) else getString(R.string.support_logs_view_copy),
            subtitle = getString(R.string.support_logs_export_subtitle),
            options = options.map { it.first },
            compact = false
        ) { index ->
            val text = options[index].second()
            if (isShare) {
                executeShare(text)
            } else {
                executeCopy(text)
            }
        }
    }

    private fun executeShare(text: String) {
        if (text.isBlank()) {
            findViewById<View>(android.R.id.content).showWarnPill(getString(R.string.support_logs_view_empty))
            return
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Loq In logs")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(sendIntent, getString(R.string.support_logs_share_title)))
    }

    private fun executeCopy(text: String) {
        if (text.isBlank()) {
            findViewById<View>(android.R.id.content).showWarnPill(getString(R.string.support_logs_view_empty))
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Loq In logs", text))
        findViewById<View>(android.R.id.content).showWarnPill(getString(R.string.support_logs_copied))
    }

    private fun onLogItemClicked(entry: SupportLogEntry) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Loq In log", entry.raw))
        findViewById<View>(android.R.id.content).showWarnPill(getString(R.string.support_logs_copied_single))
    }

    private fun confirmClearLogs() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.support_logs_clear_confirm_title)
            .setMessage(R.string.support_logs_clear_confirm_message)
            .setPositiveButton(R.string.delete) { _, dialog ->
                AppLogStore.clear(this)
                loadLogs(forceScrollToEnd = false)
                // Anchor to the dialog window so the pill is visible above it.
                ((dialog as? androidx.appcompat.app.AlertDialog)?.window?.decorView
                    ?: findViewById<View>(android.R.id.content))
                    .showWarnPill(getString(R.string.support_logs_view_empty))
            }
            .setNegativeButton(R.string.cancel, null)
            .showAccented()
    }

    private fun toolbarForegroundColor(): Int {
        val toolbarColor = AccentColor.getToolbarColor(this)
        return if (ColorUtils.calculateLuminance(toolbarColor) > 0.5) Color.BLACK else Color.WHITE
    }

    companion object {
        private const val MAX_STORED_LINES = 1000
        private const val TAG_ALL = "__ALL__"
        private const val TAG_ERRORS = "__ERRORS__"

        fun parseLogEntry(rawLine: String): SupportLogEntry {
            val clean = rawLine.trim()
            var text = clean
            var repeated = 1

            val repeatRegex = Regex("""\s*\(repeated\s+(\d+)×\)\s*$""")
            val repeatMatch = repeatRegex.find(text)
            if (repeatMatch != null) {
                repeated = repeatMatch.groupValues[1].toIntOrNull() ?: 1
                text = text.substring(0, repeatMatch.range.first).trim()
            }

            var timestamp = ""
            var remainder = text
            if (remainder.length >= 20 &&
                remainder[4] == '-' && remainder[7] == '-' &&
                remainder[10] == ' ' && remainder[19] == ' '
            ) {
                timestamp = remainder.substring(0, 19)
                remainder = remainder.substring(20).trim()
            }

            var tag = "LOG"
            var message = remainder
            if (remainder.startsWith("[")) {
                val closeIdx = remainder.indexOf("]")
                if (closeIdx > 1) {
                    tag = remainder.substring(1, closeIdx).trim().uppercase(Locale.US)
                    message = remainder.substring(closeIdx + 1).trim()
                }
            }

            val isError = tag.contains("ERROR") ||
                tag.contains("FAIL") ||
                message.contains("Exception", ignoreCase = true) ||
                message.contains("Error", ignoreCase = true) ||
                message.contains("fatal", ignoreCase = true) ||
                message.contains(" | ")

            return SupportLogEntry(
                raw = clean,
                timestamp = timestamp,
                tag = tag.ifBlank { "LOG" },
                message = message.ifBlank { clean },
                isError = isError,
                repeatedCount = repeated
            )
        }
    }
}

data class SupportLogEntry(
    val raw: String,
    val timestamp: String,
    val tag: String,
    val message: String,
    val isError: Boolean,
    val repeatedCount: Int = 1
)

class SupportLogAdapter(
    private val onItemClick: (SupportLogEntry) -> Unit
) : ListAdapter<SupportLogEntry, SupportLogAdapter.LogViewHolder>(LogDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.row_support_log_item, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTag: TextView = itemView.findViewById(R.id.tvLogTag)
        private val tvRepeated: TextView = itemView.findViewById(R.id.tvLogRepeated)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvLogTimestamp)
        private val tvMessage: TextView = itemView.findViewById(R.id.tvLogMessage)

        fun bind(entry: SupportLogEntry, onItemClick: (SupportLogEntry) -> Unit) {
            val context = itemView.context
            tvTag.text = entry.tag

            if (entry.isError) {
                tvTag.setBackgroundResource(R.drawable.bg_log_tag_error)
                val errorColor = ContextCompat.getColor(context, R.color.status_error)
                tvTag.setTextColor(errorColor)
            } else {
                tvTag.setBackgroundResource(R.drawable.bg_log_tag_normal)
                val normalColor = MaterialColors.getColor(
                    itemView,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    Color.DKGRAY
                )
                tvTag.setTextColor(normalColor)
            }

            if (entry.repeatedCount > 1) {
                tvRepeated.visibility = View.VISIBLE
                tvRepeated.text = "${entry.repeatedCount}×"
            } else {
                tvRepeated.visibility = View.GONE
            }

            if (entry.timestamp.isNotBlank()) {
                tvTimestamp.visibility = View.VISIBLE
                tvTimestamp.text = entry.timestamp
            } else {
                tvTimestamp.visibility = View.GONE
            }

            tvMessage.text = entry.message

            itemView.setOnClickListener { onItemClick(entry) }
            itemView.setOnLongClickListener {
                onItemClick(entry)
                true
            }
        }
    }

    object LogDiffCallback : DiffUtil.ItemCallback<SupportLogEntry>() {
        override fun areItemsTheSame(oldItem: SupportLogEntry, newItem: SupportLogEntry): Boolean {
            return oldItem.raw == newItem.raw
        }

        override fun areContentsTheSame(oldItem: SupportLogEntry, newItem: SupportLogEntry): Boolean {
            return oldItem == newItem
        }
    }
}
