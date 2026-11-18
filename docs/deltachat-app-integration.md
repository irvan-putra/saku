# DeltaChat app integration groundwork (sync-integration branch)

Status: groundwork, synthetic-only. NOT in any release (0.1.3 was cut from
the pre-sync line; main carries only the envelope groundwork + emulator
payload tests). Not ready for real vault data; the SYNTHETIC- id gate stays
until the real-secret readiness review.

## What exists here

- `app/src/main/python/sync_runtime.py`: device-side runtime. Mirror of
  `SyncEnvelope.kt` (same canonical SYNC1 bytes, same gates) plus the
  DeltaChat rpc-server lifecycle for Android (chaquopy):
  `Rpc(accounts_dir=<files>/accounts, rpc_server_path=<nativeLibDir>/librpcsrv.so)`,
  then `start_io()` - required on every start, nothing flows without it.
- `app/src/main/python/test_sync_envelope.py`: 10 host codec tests with
  fixed wire vectors (the exact bytes the Kotlin side produces; payloads
  from the 2026-11-18 live wire test). CI: `.github/workflows/sync-codec.yml`
  (dispatch-only, exact-count gate).
- Gradle: chaquopy 17.2.0 plugin, pip pins
  (`deltachat-rpc-client` from encore fork `c89d6294`, `cryptography`).
  The arm64-v8a abiFilters is CHAQUOPY-SCOPED so the app's KeePassDX x86_64
  emulator crypto keeps working; `librpcsrv.so` exists for arm64 only, so
  the sync runtime itself is arm64-only (emulator sync tests are out until
  an x86_64 rpc server exists - same constraint as intodo).

## Transport lessons baked in (from the 2026-11-18 live test)

1. `start_io()` after every `rpc.start()`; a fresh rpc server does not run
   IMAP/SMTP until told. Symptom otherwise: SecureJoin stuck, sends pending.
2. Send ONLY on the SecureJoin-verified 1:1 chat (the chat named by the
   peer address after pairing). `create_contact(...).create_chat()` makes a
   SECOND, unverified chat that chatmail refuses with
   'Failed to create send jobs: e2e encryption unavailable'.
3. Pairing: one side `get_qr_code()`, the other `secure_join(qr)`; wait for
   SecurejoinJoinerProgress/InviterProgress = 1000 on BOTH sides.

## Not here yet (next steps)

- Kotlin bridge (Python.getInstance / chaquopy start) + a sync service.
- Pairing UX (show/scan QR) and background receive scheduling.
- Emulator or on-device smoke test of the runtime inside the app.
- Decision (needs the user when close): phone-side sync identity - one of
  the two existing synthetic accounts vs a third synthetic account.
- Automatic vault save on envelope receipt (original requirement); any
  unlock/preview/approve step is a proposal only.
