# Loq In architecture notes
Loq In is an Android app for profile-based blocking. The app is intentionally split into feature, runtime, platform receiver, data/prefs, security and UI areas.

## Important packages
- `com.oliver.loqin.blocking` — Accessibility and blocking runtime logic
- `com.oliver.loqin.data.prefs` — local stores and schedule/profile settings (`SharedPreferences` file `loqin_prefs`)
- `com.oliver.loqin.data.sync` — local file backup/restore runtime
- `com.oliver.loqin.data.statistics` — durable Room archive for counters, sessions and Activity History (`loqin_statistics.db`)
- `com.oliver.loqin.feature.*` — screens and feature-specific UI
- `com.oliver.loqin.nfc` — NFC/deep-link command schema and tag entry handling
- `com.oliver.loqin.platform.receiver.*` — Android receivers/services for schedule, Wi-Fi, Bluetooth, location and system events
- `com.oliver.loqin.security` — app lock and related safety helpers

## Build variants
Loq In has a single build with only the standard `debug`/`release` build types. There are no product flavors and no Firebase, cloud-sync, or premium components.

## Statistics persistence
The existing `data.prefs` stores remain the low-latency compatibility cache used by blocking and UI code. `StatsPersistence` mirrors every statistics key into `loqin_statistics.db`, restores missing cache keys on startup, and stores app, website and screen-unlock sessions plus Activity History in structured Room tables.

Android `UsageEvents` is only an import/repair source. Imported app sessions are kept in Room after Android stops exposing the original events. Local JSON backups include a compressed Room snapshot.

A full data reset must delete both preferences and every app database. Statistics-only deletion must remove the relevant preference keys so the registered mirror can remove the matching Room rows.

## Release-sensitive checks
Before release, run lint, test backup/restore, and test exported NFC/QR/barcode action paths with malformed inputs.
