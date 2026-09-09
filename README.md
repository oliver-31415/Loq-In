# Switchly
[**Switchly**](https://switchly.saltyy.at) is an Android application for **profile-based app blocking**.
A lightweight background service monitors the currently foreground app and shows a blocking overlay whenever a restricted app is opened.

Designed for focus, control, and flexibility — without unnecessary complexity.

---

## Project Structure
Source code root:
```text
app/src/main/java/at/saltyy/switchly
```

Icons are based on **Material Symbols**:
[https://fonts.google.com/icons](https://fonts.google.com/icons)

---

## Localization/i18n
All user-facing text lives in **translations**, not hard-coded in Kotlin/XML:
* Default: `app/src/main/res/values/strings.xml`
* German: `app/src/main/res/values-de/strings.xml`

Guidelines:
* Use `getString(R.string.some_key)`/`@string/some_key`
* Prefer formatted strings (`*_fmt`) over string concatenation
* Keep EN + DE keys in sync with the same key set

This includes **Toasts, dialogs, notifications, and inline UI labels**.

---

## APK Build Options
Switchly builds a single app with the standard `debug` and `release` build types.

Build:
```bash
./gradlew :app:assembleDebug
```

Release build (unsigned unless `signing.properties` provides the release keystore):
```bash
./gradlew :app:assembleRelease
```

---

## Maps and Signing Configuration
Google Maps and release signing are configured through `signing.properties`:
```properties
MAPS_API_KEY=your-maps-api-key

SWITCHLY_RELEASE_STORE_FILE=/path/to/switchly-release.jks
SWITCHLY_RELEASE_STORE_PASSWORD=...
SWITCHLY_RELEASE_KEY_ALIAS=...
SWITCHLY_RELEASE_KEY_PASSWORD=...
```

You can still override any of those via `-P...` or environment variables.

---

## Public Links and Contact Configuration
Official builds can compile public website/contact links through `signing.properties`. Forks can leave these blank or replace them with their own URLs.
```properties
SWITCHLY_WEBSITE_URL=https://your-domain.example
SWITCHLY_DOWNLOADS_URL=https://your-domain.example/pages/download
SWITCHLY_DEV_EMAIL=support@example.com
```

These values are public and safe to compile into the APK.

---

## Shrinking: Unused Code and Resources
Release builds enable:
* **R8/minification** (`minifyEnabled true`)
* **Resource shrinking** (`shrinkResources true`)

This means most unused code/resources are removed automatically at build time.

To verify locally:
```bash
./gradlew :app:assembleRelease
```

---

## Supported Android Versions
| Requirement | Value                    |
| ----------- | ------------------------ |
| **Min SDK** | **Android 8.1 (API 27)** |
| Target SDK  | 36                       |
| JDK         | 17                       |
| Kotlin      | 2.2+                     |
| AGP         | 8.9+                     |

---

## Versioning
Switchly follows **MAJOR.MINOR.PATCH**.

| Type  | Example | Description                       |
| ----- | ------- | --------------------------------- |
| Patch | `1.0.1` | Bug fixes                         |
| Minor | `1.1.0` | New features, backward-compatible |
| Major | `2.0.0` | Breaking changes                  |

---

## Contributing
Before starting a contribution, please contact me first:
**[andi@saltyy.at](mailto:andi@saltyy.at)**

Please also read:
* [`docs/CONTRIBUTING.md`](./docs/CONTRIBUTING.md)
* [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md)
* [`docs/DRAWABLE_CONVENTIONS.md`](./docs/DRAWABLE_CONVENTIONS.md)

---

## Useful Commands
### Run lint
```bash
./gradlew clean lint
```

### Get info about all dependencies
```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
```

---

## Contributor
**Andi S.**
[https://saltyy.at](https://saltyy.at)

---

## License
Switchly is licensed under the **GNU General Public License v3.0**.

You are free to use, modify, and distribute this software, but any distributed modifications must also be licensed under **GPLv3**.

See:
* [`docs/LICENSE`](./docs/LICENSE)
* [`docs/NOTICE`](./docs/NOTICE)

---

**Made with ♥️ and 🍪 by saltyy**
