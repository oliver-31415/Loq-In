# Contributing to Switchly
Thanks for your interest in contributing to Switchly.

This repository contains the public release code of Switchly. Development may sometimes continue in a private test repository before release-ready changes are synced here.

Before starting larger changes, please contact the maintainer so work can be coordinated and release/policy-sensitive areas stay aligned.

## Repository scope
Switchly has a single build with only the standard `debug`/`release` build types.

## Getting started
1. Install the latest stable version of Android Studio
2. Use JDK 17
3. Clone the repository
4. Build:
```bash
./gradlew :app:assembleDebug
```

For release validation:
```bash
./gradlew :app:assembleRelease
```

A release build without a signing key in `signing.properties` stays unsigned. `signing.properties`, keystores, secrets, tokens, and generated build outputs must not be committed.

## Translation rules
All user-facing strings must live in Android resources.

Keep English and German resource keys in sync when possible.

## Policy-sensitive areas
Be extra careful with:
- Accessibility disclosure and service behavior
- location, Wi-Fi, Bluetooth and exact-alarm scheduling
- exported NFC/QR/barcode/deep-link entry points
- backup and restore data handling
- support/debug report contents

For changes touching Accessibility, schedules, backup/restore, NFC/QR/barcode actions, blocking logic, or background services, test the affected area on a real device when possible.

Before changing navigation, icons, dialogs, selection controls, or security-sensitive management screens, read [`UI_CONVENTIONS.md`](./UI_CONVENTIONS.md).
For vector assets and icon imports, also read [`DRAWABLE_CONVENTIONS.md`](./DRAWABLE_CONVENTIONS.md).

## Guidelines
- Keep changes focused and easy to review
- Prefer small merge requests over large rewrites
- Avoid unrelated cleanup in the same merge request
- Do not commit generated files such as `build/` outputs
- Do not commit APK/AAB files unless explicitly requested for a release workflow
- Keep user-facing strings in resources
- Keep English and German translations aligned when possible
- Keep file structure and naming consistent with the existing project

## Naming
- `*Activity`, `*Fragment`: screen entry points
- `*Adapter`: list or RecyclerView adapters
- `*Store`: key-value persistence only
- `*Repository`: grouped feature data access
- `*Runtime`: runtime or system state handling
- `*Mapper`: mapping between models or UI data
- `*Formatter`: display formatting
- `*Validator`: validation and rule checks

## Merge requests
Please include:
- what you changed
- why you changed it
- screenshots or screen recordings for UI changes, if relevant
- notes about behavior changes, especially around blocking, schedules, permissions, NFC, QR, barcode, or profiles
- what you tested, including device/flavor where relevant
