# Saku

Local-first Android password-manager project, with a proposed path to deliver newly created logins over Delta Chat. License selection is still pending; do not treat this project as an open-source licensed release.

**Development checkpoint only. Do not use for real passwords, accounts, keys, or live sync.**

The earlier protocol prototype was critically broken and is not included here. Its transcript-derived value let an observer derive message keys and decrypt captured traffic; it also lacked safe state-loss, peer-pinning, and rollback handling. See `SPEC.md` and `docs/SECURITY.md`. No security properties are established by this checkpoint.

## What is included

- A synthetic-only Android UI scaffold with fixed demo records. It has no real vault storage, network permission, account, pairing, or Autofill implementation. `VaultGate`'s synthetic-prefix check is a test convention, not a security boundary.
- Host-only storage experiments and small, synthetic interoperability fixtures. The KeePassJava2 2.2.6 reverse-interoperability case fails because of missing `IconID`; that known failure remains visible. A separate, narrow 3.1.0 host spike passes its current tests, but this does not select or endorse a storage implementation.
- A separate compile/DEX/package probe for the 3.1.0 API in the disposable namespace `com.vanipy.saku.storageprobe`. It is not production code and was not run on Android.
- Current requirements and security/storage notes. The previous broken-prototype warning and documentation-only checkpoint history are preserved.

## Test and build status

The latest checkpoint reports 10 passing tests and one known failing 2.2.6 reverse-interop test (missing `IconID`). The full test suite is **not green**. The host `assembleDebug` build passed with Gradle limited to one worker and a 512 MiB JVM cap. A separate three-test 3.1.0 host spike passed. The storage probe compiled, DEXed, and assembled; it was not executed on Android. A Robolectric native-graphics render passed. Its screenshot is at `evidence/main-activity-robolectric.png`; it is a mock, host-rendered screen, not a device screenshot.

No APK is included or released. No real passwords, keys, accounts, or network identities were used. Passing host tests or compilation does not establish Android runtime support, safe password storage, cryptographic security, or readiness for use.

## Design notes

- The current v1 direction is Delta Chat's native end-to-end encryption, with **no forward-secrecy claim** and pinned keys behind a reviewed owner-approval gate. No gate is implemented, and no real sync is enabled.
- Local storage is not selected. Host interoperability samples do not establish Android support, safety, performance, or audit status.
- The full test/build limitations and open work are in `SPEC.md`, `docs/PROTOCOL-EVAL.md`, `docs/SECURITY.md`, and `docs/STORAGE-EVAL.md`.

## Build

Use JDK 17, Gradle 8.2.1, Android SDK 34, and build-tools 33.0.1. Configure an untracked `local.properties`; run `gradle assembleDebug testDebugUnitTest`. The full test suite retains the known 2.2.6 failure described above. The standalone `spikes/storage3` and `spikes/storage3-checkpoint6/host` projects have separate host test tasks. The Android packaging probe lives under `spikes/storage3-checkpoint6/android`.


## Separate receive-state research model

`investigations/durable-checkpoint9/` contains an isolated synthetic SQLite receive-state prototype and its tests/boundaries. It is not part of the Android app or a production transport, authentication, storage, or revocation gate. Its 21 host tests are research evidence only; the directory documents the known races and unimplemented production controls. It does not change the earlier prototype warning or the existing storage-test failures above.

## Research checkpoints

- [Bitwarden password-creation CLI route limits](docs/research/bitwarden-password-cli-route-limits.md) - bounded reading of official docs and terms; no account or live vault tested, and other routes remain unevaluated.
- [HPKE in Tink: privacy without sender authenticity](docs/research/hpke-in-tink-privacy-without-sender-authenticity.md) - official API docs; no custom cryptography implementation or security claim.
- [KeePassDX reuse: baseline qualification and integration boundaries](docs/research/keepassdx-baseline-qualification.md) - pinned source review and negative-test targets; no fork, build, or real vault tested.
- [Cryptography research checkpoint (2026-11-16)](investigations/crypto-research-20261116/RESEARCH.md) - source and library research only; no implementation choice, audit, license change, or usable password vault.
- [Whole Android vault reuse versus KDBX library](investigations/vault-reuse-20261116/RESEARCH.md) - bounded source comparison; not an audit, implementation choice, or usable vault.
