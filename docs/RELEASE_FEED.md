# Switchly release feed integration
Switchly can enrich the Google Play update prompt with public metadata from:
`https://release.saltyy.at/data/releases.json`

Google Play remains the source of truth for whether an update is available. The release feed is optional; if it cannot be reached, Switchly falls back to the normal generic update prompt. The last successful JSON response is cached locally for later update checks.

## Existing release timeline fields
The parser already understands the current release timeline shape:
```json
{
  "date": "2026-08-17",
  "title": "Switchly",
  "subtitle": "Android App",
  "version": "2.2.5-beta",
  "desc": "Release description...",
  "type": "app"
}
```

Only entries where `title` is `Switchly`, `type` is `app`, and the subtitle is an Android app entry are considered.

## Optional richer update fields
Future Switchly entries may include these fields without breaking the release website:
```json
{
  "version": "3.0.0",
  "versionCode": 300,
  "updateType": "major",
  "breaking": true,
  "highlights": [
    "New protection engine",
    "Updated setup flow",
    "Review compatibility changes before updating"
  ]
}
```

- `versionCode`: lets the app match Play's exact target version without inferring it from SemVer.
- `updateType`: `maintenance`/`patch`/`hotfix`, `feature`/`minor`, or `major`.
- `breaking`: only set this when the release really contains compatibility or behavior changes users should review. A major version is not automatically treated as breaking.
- `highlights`: concise bullets shown directly in the update dialog. Without this array, Switchly derives a compact preview from `desc`.

## Update classification fallback
If `updateType` is absent, Switchly compares semantic versions:
- `2.2.4 → 2.2.5`: Maintenance update
- `2.1.6 → 2.2.5`: Feature update
- `2.x.x → 3.0.0`: Major update

`breaking` is never inferred from the major version alone.
