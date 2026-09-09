# Switchly architecture notes
Switchly is an Android app for profile-based blocking. The app is intentionally split into feature, runtime, platform receiver, data/prefs, security and UI areas.

## Important packages
- `at.saltyy.switchly.blocking` — Accessibility and blocking runtime logic
- `at.saltyy.switchly.data.prefs` — local stores and schedule/profile settings
- `at.saltyy.switchly.data.sync` — local file backup/restore runtime
- `at.saltyy.switchly.data.statistics` — durable Room archive for counters, sessions and Activity History
- `at.saltyy.switchly.feature.*` — screens and feature-specific UI
- `at.saltyy.switchly.nfc` — NFC/deep-link command schema and tag entry handling
- `at.saltyy.switchly.platform.receiver.*` — Android receivers/services for schedule, Wi-Fi, Bluetooth, location and system events
- `at.saltyy.switchly.security` — app lock and related safety helpers

## Statistics persistence
The existing `data.prefs` stores remain the low-latency compatibility cache used by blocking and UI code. `StatsPersistence` mirrors every statistics key into `switchly_statistics.db`, restores missing cache keys on startup, and stores app, website and screen-unlock sessions plus Activity History in structured Room tables.

Android `UsageEvents` is only an import/repair source. Imported app sessions are kept in Room after Android stops exposing the original events. Local JSON backups include a compressed Room snapshot.

A full data reset must delete both preferences and every app database. Statistics-only deletion must remove the relevant preference keys so the registered mirror can remove the matching Room rows.

## Release-sensitive checks
Before release, run lint, test backup/restore, and test exported NFC/QR/barcode action paths with malformed inputs.
