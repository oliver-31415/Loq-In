# Switchly Firestore production rules
The repository root contains the production client rules in `firestore.rules`.

Switchly stores user data below:
```text
switchly_users/{uid}
└── backups/{backupId}
    └── stats_chunks/{chunkId}
```

The rules enforce the following model:
- unauthenticated clients cannot access Switchly user data
- an authenticated user can access only `switchly_users/{their uid}` and its backup data
- `hasPremiumExternal` is server-managed and cannot be created or changed by the mobile/web client
- `hasPremiumClientMirror` is diagnostic only and never grants Premium
- the legacy `hasPremium` field is ignored for external entitlement restore
- all other paths are denied by default

## Deploy
In Firebase Console open **Firestore Database -> Rules**, replace the existing rules with the contents of the repository-root `firestore.rules`, then publish them. Publishing rules changes access control only; it does not delete existing Firestore documents.

Before publishing, test at least these cases in the Rules Playground or Emulator:
1. signed-out read of `switchly_users/<uid>` -> denied
2. user A read/write of `switchly_users/A` -> allowed except changes to `hasPremiumExternal`
3. user A read/write of `switchly_users/B` -> denied
4. user A backup and `stats_chunks` access below `switchly_users/A` -> allowed
5. client create/update containing a changed `hasPremiumExternal` -> denied

Backend/Admin SDK writes are not authorized by these client rules; the payment backend must remain responsible for setting `hasPremiumExternal` only after verified entitlement state.
