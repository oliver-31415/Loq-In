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
| Screen title (toolbar) | automatic via `LoqIn.TopBar` (22sp medium) |
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
