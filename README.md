# Loq In

**Loq In** is an Android app for **profile-based app blocking**. Profiles define which
apps, websites, and in-app activities are blocked, with schedules, NFC/QR/barcode unlock
channels, usage statistics, and local file backup/restore — all offline, with no accounts.

Loq In is a fork that continues [Switchly](https://gitlab.com/Saltyy/switchly-public) by
Saltyy. It is free software licensed under the **GNU General Public License v3.0**.

- Repository: <https://github.com/oliver-31415/loqin>

---

## Project Structure

Source code root:

```text
app/src/main/java/com/oliver/loqin
```

- `blocking` — accessibility and blocking runtime logic
- `data/prefs` — key-value stores and schedule/profile settings
- `data/sync` — local file backup/restore runtime
- `data/statistics` — Room archive for counters, sessions, and Activity History
- `feature/` — screens and feature-specific UI
- `nfc` — NFC/deep-link command schema and tag entry handling
- `platform/receiver` — receivers/services for schedule, Wi-Fi, Bluetooth, location, and system events
- `security` — app lock and related safety helpers
- `ui`, `widget`, `util`, `theme` — shared UI, home-screen widgets, helpers

Icons are based on **Material Symbols**:
[https://fonts.google.com/icons](https://fonts.google.com/icons)

---

## Build

Requirements: Android Studio (or the Android SDK), **JDK 17**, Android SDK 36.

Loq In builds a single app with the standard `debug` and `release` build types — no
product flavors.

Debug build:

```bash
./gradlew :app:assembleDebug
```

Release build (unsigned unless `signing.properties` provides the release keystore):

```bash
./gradlew :app:assembleRelease
```

Release signing is configured by copying `signing.properties.example` to
`signing.properties` and filling in:

```properties
LOQIN_RELEASE_STORE_FILE=/path/to/loqin-release.jks
LOQIN_RELEASE_STORE_PASSWORD=...
LOQIN_RELEASE_KEY_ALIAS=...
LOQIN_RELEASE_KEY_PASSWORD=...
```

The same keys can be provided via `~/.gradle/gradle.properties`, `-P...`, or environment
variables. Release builds enable R8 minification and resource shrinking. Never commit
`signing.properties`, keystores, or passwords.

---

## Localization

All user-facing text lives in resources, not hard-coded in Kotlin/XML:

- Default (English): `app/src/main/res/values/strings.xml`
- German: `app/src/main/res/values-de/strings.xml`

Guidelines:

- Use `getString(R.string.some_key)` / `@string/some_key`
- Prefer formatted strings (`*_fmt`) over string concatenation
- Keep EN + DE keys in sync with the same key set

This includes Toasts, dialogs, notifications, and inline UI labels.

---

## Contributing

Please read:

- [`docs/CONTRIBUTING.md`](./docs/CONTRIBUTING.md)
- [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md)
- [`docs/UI_CONVENTIONS.md`](./docs/UI_CONVENTIONS.md)
- [`docs/DRAWABLE_CONVENTIONS.md`](./docs/DRAWABLE_CONVENTIONS.md)

---

## Attribution

- **Switchly** by [Saltyy](https://gitlab.com/Saltyy/switchly-public) (GPLv3) — the base
  Loq In continues to build on
- **[Foqos](https://github.com/awaseem/foqos)** — design inspiration
- **Scrollless** — parts of the blocking logic are derived from it

---

## License

Loq In is licensed under the **GNU General Public License v3.0**.

You are free to use, modify, and distribute this software, but any distributed
modifications must also be licensed under **GPLv3**.

See:

- [`docs/LICENSE`](./docs/LICENSE)
- [`docs/NOTICE`](./docs/NOTICE)
