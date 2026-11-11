# Loq In — Material 3 Expressive design spec

Loq In keeps its own identity — warm paper neutrals (`foqos_*`), a user-chosen accent, big rounded
shapes, pill buttons, the LOQ IN wordmark and the Home focus heatmap/profile hero — and aligns the
rest with current Android (Android 16 Settings, Pixel apps): tonal surfaces instead of outlines,
grouped lists instead of dividers, emphasized type, shape-morphing pill buttons and Expressive
components. Read `docs/UI_CONVENTIONS.md` first; this spec only adds the visual layer.

Everything below already exists as a shared style or widget. Use it; don't recreate it per screen.

## Surfaces
| Role | Use |
| --- | --- |
| Page background | window / `@color/foqos_bg` (set by the theme — don't set it per screen) |
| Content container (card, list group) | `@color/foqos_surface`, **no stroke, no elevation** |
| Nested / secondary container inside a card | `?attr/colorSurfaceContainer` (`@color/foqos_surface_container`) |
| Emphasis container (selected, hero chip) | accent container (`AccentColor.getAccentContainerColorInt()` in code, `?attr/colorPrimaryContainer` in XML) |

- `@style/LoqIn.Card` and `@style/LoqIn.TileCard` are flat (no stroke, no shadow). Do not add
  `app:strokeWidth`, `app:strokeColor`, `app:cardElevation` back onto cards. Remove existing
  per-card `app:strokeWidth="1dp"` / `strokeColor` / `cardElevation` overrides unless the stroke
  carries meaning (a selected state, an error).
- Unstyled `MaterialCardView`s default to the outlined style: give them `style="@style/LoqIn.Card"`
  (or set `app:strokeWidth="0dp"`) unless the outline is meaningful.
- Toolbars are transparent; the theme's `LoqIn.AppBar` shows the page colour and lifts to a tonal
  colour on scroll. Don't give an `AppBarLayout`, `MaterialToolbar` or its parent a
  `colorSurface`/white background. Keep `style="@style/LoqIn.TopBar"` on toolbars.

## Grouped lists (the main change)
Any card that stacks rows separated by dividers becomes a **grouped list**: each row is its own
rounded segment (24dp outer corners, 6dp inner corners, 2dp gap), as in Android 16 Settings.

```xml
<TextView style="@style/LoqIn.SectionHeader" ... android:text="@string/..." />

<com.oliver.loqin.ui.widgets.ListGroupLayout style="@style/LoqIn.ListGroup">
    <LinearLayout android:id="@+id/rowA" style="@style/LoqIn.ListRow"
        android:clickable="true" android:focusable="true"> ... </LinearLayout>
    <LinearLayout android:id="@+id/rowB" style="@style/LoqIn.ListRow" ...> ... </LinearLayout>
</com.oliver.loqin.ui.widgets.ListGroupLayout>
```

- No `MaterialDivider` between rows, no `?attr/selectableItemBackground` on rows — the group draws
  the segment and a clipped ripple for clickable rows. Rows can be shown/hidden at runtime; the
  group re-shapes itself. If code changes `isClickable` later, call `refreshSegments()`.
- `ListGroupLayout` is a vertical `LinearLayout`: Kotlin that treats the old inner LinearLayout as
  `LinearLayout`/`ViewGroup` keeps working. Kotlin that casts the old *card* to `MaterialCardView`
  (e.g. `setCardBackgroundColor`, `strokeColor`) does **not** — check every id you move.
- Row anatomy: leading 24dp icon tinted `?attr/colorPrimary` (16dp gap to text), title
  `@style/TextAppearance.LoqIn.RowTitle`, summary `@style/TextAppearance.LoqIn.RowSummary`, trailing
  chevron 24dp tinted `?attr/colorOnSurfaceVariant` (only for in-app navigation, per UI_CONVENTIONS),
  or a switch / value text. Row min height 72dp for two-line rows, 56dp for one-line rows.
- Scripts (run from the repo root; read their output — they skip what they can't convert safely):
  - `python3 -I scripts/design/to_list_group.py path/to/layout.xml` converts `LoqIn.Card` blocks
    of rows+dividers into a `ListGroupLayout` (skips cards that have an `android:id`; check those
    by hand: grep the id in Kotlin, then convert with `--allow-card-id` if no code needs a card).
  - `python3 -I scripts/design/restyle_rows.py path/to/layout.xml` applies the row anatomy above.
  - Always review the diff afterwards (`git diff`), and fix anything odd by hand.
- On a sheet or dialog (white surface) use `style="@style/LoqIn.ListGroup.OnSurface"` so the segments
  are tonal and stay visible.
- `python3 -I scripts/design/check_list_groups.py app/src/main/res/layout/*.xml` must print nothing: a
  TextView/Button/View directly inside a group is a mis-converted content card or a leftover divider.
- A card that is *not* a list of rows (a chart, a hero, a form, a single block of text) stays a
  `LoqIn.Card`.

## Type
| Use | Style |
| --- | --- |
| Screen title (toolbar) | automatic via `LoqIn.TopBar` (20sp medium) |
| Section header above a group | `@style/LoqIn.SectionHeader` (accent, 14sp medium, 16dp inset) |
| Row title / summary | `TextAppearance.LoqIn.RowTitle` / `TextAppearance.LoqIn.RowSummary` |
| Big numbers (stats, timers, counts) | `TextAppearance.LoqIn.HeroValue` (36sp bold) or keep larger existing sizes |
| Body text | default `TextView` (theme default is now primary text colour) |

- Replace ad-hoc `textSize="15sp" textStyle="bold"` titles and `textColor="?android:attr/textColorSecondary" textSize="13sp"`
  summaries with the appearances above. Don't hard-code `#RRGGBB` text colours; use
  `?attr/colorOnSurface` / `?attr/colorOnSurfaceVariant` / `?attr/colorPrimary`.
- Never set `android:lineHeight` (API-34 framework bug, see `values/layout.xml`).

## Buttons and controls
| Role | Style |
| --- | --- |
| One primary action | `@style/LoqIn.Button` (filled accent pill, morphs on press) |
| Secondary action | `@style/LoqIn.TonalButton` (replaces `Widget.Material3.Button.TonalButton` / `UnelevatedButton`) |
| Low-emphasis / alternative | `@style/LoqIn.OutlinedButton` |
| Inline / dialog action | `@style/LoqIn.TextButton` |
| Two-or-three way mode switch | `MaterialButtonToggleGroup` + `@style/LoqIn.Segment` buttons, styled by `SegmentedToggleUi.apply()` |
| On/off setting | `MaterialSwitch` (theme adds the check thumb) |

- Don't set `app:cornerRadius` on buttons — it overrides the shape morph. Remove existing ones on
  `LoqIn.*` buttons (the segmented control is the exception; `SegmentedToggleUi` owns it).
- Replace `Widget.MaterialComponents.*` styles with the Material3/LoqIn equivalents
  (`Widget.MaterialComponents.TextInputLayout.OutlinedBox` → `@style/LoqIn.TextInputLayout`,
  dropdowns → `@style/LoqIn.DropdownLayout`).
- Indeterminate spinners (`ProgressBar` / `CircularProgressIndicator` with `indeterminate`) become
  `com.google.android.material.loadingindicator.LoadingIndicator` (Expressive shape-morph loader).
  Determinate progress stays a progress indicator.

## Spacing
- Screen content: 16dp horizontal padding. Between a section header and its group: 8dp. Between
  groups: 24dp (header `layout_marginTop="24dp"`, except the first).
- Touch targets ≥ 48dp. Keep layouts working with large fonts and German text (no fixed heights on
  text containers, no `singleLine` on titles that may wrap).

## Don'ts
- Don't change behaviour, ids, strings, or navigation. This is a visual pass only.
- Don't touch home-screen widgets (`widget_*.xml`, RemoteViews can't use these widgets).
- Don't introduce new colours; new strings go through the Gemini translation rule.
- Don't restyle charts/heatmaps' internals — only their containers.

## Cohesion rules
Every page is built from the pieces below and nothing else. Sizes live in `values/dimens_page.xml`,
styles in `values/styles_page.xml` (+ `values/layout.xml`), code twins in `ui/PageUi.kt`. If a
layout or Kotlin file sets a size, colour or typeface that one of these already owns, delete the
local value. Proof screens that follow every rule: `activity_settings.xml`, `activity_manage_keys.xml`,
`activity_protection_changes.xml`, `LoqInOverviewActivity.kt` + `grid_stat_tile.xml`.
`activity_ignored_usage_apps.xml` shows the filter chips, segments and sticky bottom action.

### Page container
The scrolling container gets `LoqIn.PageContent` (16dp sides `@dimen/page_margin`, 8dp top,
32dp bottom, `clipToPadding=false`). Remove its own `padding*`/`clipToPadding`; children add no
horizontal margins, so headers, groups, cards and tiles all share one gutter.
```xml
<androidx.core.widget.NestedScrollView
    android:id="@+id/..."
    style="@style/LoqIn.PageContent"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:layout_behavior="@string/appbar_scrolling_view_behavior">
```
Code-built screens: `PageUi.applyPageContentPadding(contentView)`. Vertical rhythm: 24dp before a
section (`@dimen/section_gap`), 8dp header-to-group (`@dimen/section_header_gap`), 12dp between two
cards/groups in one section (`@dimen/block_gap`). A RecyclerView page uses `LoqIn.PageContent` too.

### Section header
`LoqIn.SectionHeader` carries its own margins (24dp top, 8dp bottom) and colour. The first header on a
page uses `LoqIn.SectionHeader.First` (8dp top). A header text inside a row that also holds an action
uses `LoqIn.SectionHeader.Inline` (no margins/padding; the row owns them, see
`item_website_section_header.xml`). Never set `layout_margin*`, `textColor`, `textSize` on a header,
and remove `layout_marginTop` from the group/card right after it.
```xml
<TextView style="@style/LoqIn.SectionHeader.First" android:layout_width="match_parent"
    android:layout_height="wrap_content" android:text="@string/..." />
<com.oliver.loqin.ui.widgets.ListGroupLayout style="@style/LoqIn.ListGroup"
    android:layout_width="match_parent" android:layout_height="wrap_content"> ... </...>
<TextView style="@style/LoqIn.SectionHeader" android:layout_width="match_parent"
    android:layout_height="wrap_content" android:text="@string/..." />
```
The colour is the *readable* accent (`PageUi.readableAccent`, darker on light / lighter on dark until
it reads), applied by the theme's `viewInflaterClass` (`ui/LoqInViewInflater.kt`) to any view whose
`style=` is `LoqIn.SectionHeader*`, `LoqIn.StatValue`, `LoqIn.RowIcon`, `LoqIn.StatIcon` or
`LoqIn.EmptyStateIcon`, for every preset and custom accent. No Activity code: don't call
`AccentContrast.strengthen` or tint headers in Kotlin. Code-built headers:
`PageUi.addSectionHeader(parent, R.string.x, first = false)` (inflates `page_section_header.xml`).

### Top bar
`style="@style/LoqIn.TopBar"` on every `MaterialToolbar` (it is also the theme's `toolbarStyle`, so
toolbars built in code get it). Title 20sp medium (`TextAppearance.LoqIn.TopBarTitle`), subtitle 14sp
on-surface-variant (`TextAppearance.LoqIn.TopBarSubtitle`). Back is always the chevron
`@drawable/keyboard_arrow_left_24`: the theme's `homeAsUpIndicator` is the chevron, so
`setDisplayHomeAsUpEnabled(true)` shows it too. Don't set title/subtitle sizes, colours or text
appearances on toolbars, and don't use `abc_ic_ab_back_material` / arrow icons.

### List row + status line
Rows sit in `ListGroupLayout` (`LoqIn.ListGroup`, or `LoqIn.ListGroup.OnSurface` on sheets).
```xml
<LinearLayout android:id="@+id/rowX" style="@style/LoqIn.ListRow"
    android:layout_width="match_parent" android:layout_height="wrap_content"
    android:clickable="true" android:focusable="true">
    <ImageView style="@style/LoqIn.RowIcon" android:layout_width="24dp" android:layout_height="24dp"
        android:contentDescription="@null" android:src="@drawable/..." />
    <LinearLayout style="@style/LoqIn.RowText" android:layout_width="0dp"
        android:layout_height="wrap_content">
        <TextView style="@style/LoqIn.RowTitle" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:text="@string/..." />
        <TextView style="@style/LoqIn.RowSummary" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:text="@string/..." />
        <TextView android:id="@+id/tvStatus" style="@style/LoqIn.RowStatus"
            android:layout_width="match_parent" android:layout_height="wrap_content" />
    </LinearLayout>
    <ImageView style="@style/LoqIn.RowChevron" android:layout_width="24dp"
        android:layout_height="24dp" android:contentDescription="@null" />
</LinearLayout>
```
- Row: `LoqIn.ListRow` (72dp min, 20/16dp sides, 14dp vertical), `LoqIn.ListRow.OneLine` (56dp).
- Text column: `LoqIn.RowText` (16dp after the icon); `LoqIn.RowText.NoIcon` when there is no icon.
- Status line (optional third line): `LoqIn.RowStatus` (neutral), `.Positive` (`status_ok`),
  `.Warning` (`status_warning`), `.Error` (`?attr/colorError`). The text must say the state in words;
  colour only reinforces it. Switch tone from code with `PageUi.setRowStatus(tv, PageUi.Status.X)`.
- Trailing: `LoqIn.RowChevron` (in-app), `LoqIn.RowExternal` (`open_in_new`, leaves the app),
  `LoqIn.RowValue` (value text such as "Off"; neutral, not accent), `LoqIn.RowSwitch` on a switch.
- Locked row that still explains itself on tap (protection on, feature off):
  `PageUi.setRowLocked(row, locked, stateDescription)` — full contrast, lock icon instead of chevron.
  Really disabled row: `PageUi.setRowDimmed(row, true)` — content at 38%, segment stays solid. Never
  set `row.alpha` (it fades the white segment too); `UiConsistency` dims disabled group rows this way.
- `python3 -I scripts/design/adopt_row_styles.py layout.xml` converts hand-written rows inside groups to
  these styles (rows with unusual paddings are skipped; finish by hand and review `git diff`).

### Cards
A content card on a page (chart, form, text block) is `LoqIn.PageCard` (flat, 24dp corners like a
group, **no built-in inset**), `LoqIn.PageCard.OnSurface` on sheets. Put its content in
`android:padding="@dimen/page_card_padding"` (20dp). `LoqIn.Card` adds an invisible 8dp inset on each
side (`cardUseCompatPadding`), which made cards sit 8dp further in than headers and groups; replace it
with `LoqIn.PageCard` and give stacked cards `layout_marginTop="@dimen/block_gap"` instead. Grid tiles
that rely on the inset for their gaps (`view_home_quick_actions.xml`, `grid_*_tile.xml`) can keep it.

### Empty state
One look: `com.oliver.loqin.ui.widgets.EmptyStateView` (flat page card, full content width, centred
24dp accent icon, title in RowTitle, optional body in RowSummary, optional tonal action). Put it in
place of the list it replaces (not inside a card or group) and toggle `visibility` as before.
```xml
<com.oliver.loqin.ui.widgets.EmptyStateView
    android:id="@+id/emptyX"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:emptyIcon="@drawable/schedule_24"
    app:emptyTitle="@string/..."
    app:emptyBody="@string/..."
    app:emptyActionText="@string/..." />
```
On a sheet add `style="@style/LoqIn.EmptyState.OnSurface"`. Code: `setTitle`, `setBody`, `setIcon`,
`setAction(text) { ... }` or `setOnActionClickListener { ... }`. If Kotlin typed the old view as
`TextView`, retype the field as `View`/`EmptyStateView` (keep the id). Hide the card that held the list
while the empty state shows (see `ProtectionChangesActivity.refreshUi`).

### Stat tile
`grid_stat_tile.xml` is the one stat tile: `SquareCardView` in `LoqIn.StatTile` (flat, no stroke, no
shadow), icon `LoqIn.StatIcon`, number `LoqIn.StatValue` (20sp bold, readable accent), label
`LoqIn.StatLabel` (12sp on-surface-variant, 2 lines). Tiles are 3 per row with `@dimen/tile_gap`
between them and the outer tiles on the page gutter (see `LoqInOverviewActivity.fillStatGrid`: half-gap
margins on tiles, minus half-gap on the row). Don't use `LoqIn.StatChip` or elevated cards for stats.

### Segmented control
```xml
<com.google.android.material.button.MaterialButtonToggleGroup android:id="@+id/toggleX"
    android:layout_width="match_parent" android:layout_height="wrap_content"
    app:singleSelection="true" app:selectionRequired="true">
    <com.google.android.material.button.MaterialButton android:id="@+id/btnA"
        style="@style/LoqIn.Segment" android:layout_width="0dp"
        android:layout_height="wrap_content" android:layout_weight="1" android:text="@string/..." />
    ...
</com.google.android.material.button.MaterialButtonToggleGroup>
```
Then `SegmentedToggleUi.apply(context, buttons, selectedId)` on every selection change. It owns the
size (48dp min touch height inside a 4dp pill track), label (`TextAppearance.LoqIn.Segment`, 14sp,
medium weight when selected, wraps to 2 lines), colours and corners. Never set `textSize`, `minHeight`,
`layout_height` in dp, `cornerRadius`, `singleLine` or tints on segments (it also clears fixed heights
set from code). Code-built segments: `MaterialButton(ctx, null, materialButtonOutlinedStyle)` with
`LayoutParams(0, WRAP_CONTENT, 1f)` and nothing else.

### Filter chip
All / Social / Video / Suggested / Hidden, log filters: `FilterChipUi.create(context, label)` (style
`LoqIn.FilterChip` via the theme's `loqinFilterChipStyle`: 36dp pill, 48dp touch target, 14sp) and
`FilterChipUi.setSelected(chip, selected)` for every chip after each tap (selected = accent-container
fill and on-surface label, like a selected segment; unselected = outlined). In XML:
`<com.google.android.material.chip.Chip style="@style/LoqIn.FilterChip" .../>`. Remove per-screen
`refreshChecks` colour code and `Widget_Material3_Chip_Suggestion`.

### Buttons and dialog actions
Every button label is `TextAppearance.LoqIn.Button` (14sp medium, **no letter-spacing**, no caps):
`LoqIn.Button`, `LoqIn.TonalButton`, `LoqIn.OutlinedButton`, `LoqIn.TextButton` (48dp touch) all use
it. Alert dialogs get `LoqIn.DialogButton` through the dialog theme overlays, and
`styleLoqInDialogButtons()` applies the same appearance. Never use
`TextAppearance.MaterialComponents.Button` (0.09em tracking) or `Widget.Material3.Button.*` directly;
use the LoqIn styles and drop local `textSize`/`minHeight` on them.

### Sticky bottom action
One primary action pinned under the scrolling content (Save, Done): a `LoqIn.BottomBar` container
(page background, 16dp sides, 12dp top/bottom) holding one full-width `LoqIn.Button`. It is the last
child of the vertical root, outside the scrolling view; `EdgeToEdgeUtils.setupClassic` already pads the
root above the navigation bar, so add no inset code or margins.
```xml
<LinearLayout style="@style/LoqIn.BottomBar"
    android:layout_width="match_parent" android:layout_height="wrap_content">
    <com.google.android.material.button.MaterialButton android:id="@+id/btnSave"
        style="@style/LoqIn.Button" android:layout_width="match_parent"
        android:layout_height="wrap_content" android:text="@string/save" />
</LinearLayout>
```
