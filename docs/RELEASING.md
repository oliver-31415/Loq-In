# Building and releasing

Loq In ships two kinds of GitHub builds, both produced by GitHub Actions:

| | Nightly | Release |
| --- | --- | --- |
| Workflow | `.github/workflows/nightly.yml` | `.github/workflows/release.yml` |
| Trigger | Every night at 02:00 UTC (skipped when `main` hasn't changed), or **Run workflow** by hand | **Run workflow** by hand only |
| Built from | The default branch | Any branch, tag or commit (default `main`) |
| App id / name | `com.oliver.loqin.nightly`, "Loq In Nightly" (installs next to the stable app) | `com.oliver.loqin`, "Loq In" |
| Version | `<versionName>-nightly.<yyyyMMdd>+<sha>`, versionCode `yyMMddHH` | `versionName` / `versionCode` from `app/build.gradle.kts` |
| Output | One rolling pre-release tagged `nightly` (replaced each night) with the APK and `SHA256SUMS` | Release `v<versionName>` with the APK, the AAB, the gzipped R8 mapping and `SHA256SUMS` |
| Checks | Unit tests | Unit tests and release lint |

Both builds are minified with R8 and signed with the release key. Neither workflow will publish an unsigned build: they fail early if a signing secret is missing.

## One-time setup

### 1. Release keystore

Use your existing upload/release keystore if you have one. Otherwise create one (keep it and its passwords somewhere safe — losing it means users can't update to new builds signed with a different key):

```bash
keytool -genkeypair -v \
  -keystore loqin-release.jks -alias loqin \
  -keyalg RSA -keysize 4096 -validity 10000
```

### 2. Repository secrets

Add these under **Settings → Secrets and variables → Actions** (or with `gh secret set`):

| Secret | Value |
| --- | --- |
| `LOQIN_RELEASE_KEYSTORE_BASE64` | The keystore, base64-encoded: `base64 -w0 loqin-release.jks` |
| `LOQIN_RELEASE_STORE_PASSWORD` | Keystore password |
| `LOQIN_RELEASE_KEY_ALIAS` | Key alias (e.g. `loqin`) |
| `LOQIN_RELEASE_KEY_PASSWORD` | Key password |
| `MAPS_API_KEY` | Optional. Google Maps key for the location-schedule picker; without it the map picker is unavailable |

```bash
base64 -w0 loqin-release.jks | gh secret set LOQIN_RELEASE_KEYSTORE_BASE64
gh secret set LOQIN_RELEASE_STORE_PASSWORD
gh secret set LOQIN_RELEASE_KEY_ALIAS --body loqin
gh secret set LOQIN_RELEASE_KEY_PASSWORD
gh secret set MAPS_API_KEY          # optional
```

`scripts/ci/setup-signing.sh` decodes the keystore into the runner's temp directory and points `LOQIN_RELEASE_STORE_FILE` at it; `app/build.gradle.kts` reads the `LOQIN_RELEASE_*` values from the environment, exactly as it does from `signing.properties` locally.

### 3. Scheduled runs

GitHub only runs scheduled workflows from the default branch, so the nightly starts once `nightly.yml` is on `main`. GitHub also pauses schedules in repositories with no activity for 60 days; re-enable it from the Actions tab if that happens.

## Cutting a release

1. Bump `loqinVersionCode` and `loqinVersionName` in `app/build.gradle.kts`.
2. Add the release to `app/src/main/res/raw/changelog.json` (this is also the in-app **What's new**). The workflow uses that entry as the GitHub release notes; without one it falls back to GitHub's generated notes and warns.
3. Merge to `main`.
4. **Actions → Release → Run workflow**. Inputs:
   - `ref` — what to build (default `main`).
   - `draft` — on by default: the release is created as a draft so you can check the notes and files, then press **Publish**. The `v<versionName>` tag is created when the draft is published.
   - `prerelease` — mark it as a pre-release (e.g. for a beta).
5. The run fails before building if the tag `v<versionName>` already exists, so a version can't be released twice by accident.

Keep the `-mapping.txt.gz` asset: it turns obfuscated crash stack traces from that release back into readable ones (`retrace` from the Android SDK command-line tools).

## Nightly builds

- Download the latest from the **nightly** pre-release on the Releases page. It is never marked "Latest", so the stable release stays the default download.
- Run it by hand with **Actions → Nightly → Run workflow**; tick **force** to rebuild even when nothing changed.
- The rolling `nightly` tag moves to the built commit each time. Each run also keeps the APK and R8 mapping as a workflow artifact for 14 days.

## Building locally

```bash
./gradlew :app:assembleDebug      # debug, app id com.oliver.loqin.loqindev
./gradlew :app:assembleNightly    # nightly flavour of the release build
./gradlew :app:assembleRelease :app:bundleRelease
```

Release and nightly builds are signed when `signing.properties` (see `signing.properties.example`) or the `LOQIN_RELEASE_*` environment variables are present, and unsigned otherwise.

## Play Store note

If Loq In is also published on Google Play with Play App Signing, Play-installed copies are signed with Google's key, not this one. Android won't update a Play install with a GitHub APK (or the other way round) — users have to stay on one channel. Nightlies are unaffected because they use their own app id.
