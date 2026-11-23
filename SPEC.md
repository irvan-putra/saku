# Saku - development checkpoint 5

Status: synthetic UI scaffold and research/testing notes only. Not a usable password manager and not security-audited. Licensed under the Apache License 2.0 (see `LICENSE`). The earlier documentation-only checkpoint remains in git history.

## Known broken protocol prototype: do not use

An earlier synthetic protocol prototype was critically unsafe. Its handshake-derived root value was visible in the public transcript, allowing an observer to derive message keys and decrypt captured traffic. It also lacked safe state-loss/desynchronization handling, authenticated peer-pin persistence, and rollback-safe snapshot recovery. These are critical design failures, not minor implementation gaps. The prototype and its gate sketches are withdrawn and excluded from this checkpoint.

**Do not use the earlier prototype, this draft, or any related design with real passwords, accounts, keys, or live networks. No security property is established.** Passing synthetic tests does not prove these properties safe.

## Goal

Explore a local-first Android password manager with manual local records, Android Autofill, and delivery of newly created logins over Delta Chat. Sync-back is a later scope. Existing-vault migration is not scoped here. This is a goal, not a claim that the app, vault, pairing, sync, recovery, or Autofill works.

## Current state

- The included Android UI is a synthetic mock. There is no real vault storage, password input, pairing, network permission, sync, or Autofill implementation.
- The v1 transport direction chosen for investigation is Delta Chat's native OpenPGP end-to-end encryption, with no forward-secrecy claim and pinned keys behind an owner-approval gate. The gate is not implemented; no real traffic is enabled.
- Local storage is not selected. KeePassJava2 2.2.6 has a retained reverse-interoperability failure; the separate 3.1.0 host spike is narrow and not an adoption, audit, or Android-runtime claim.
- No real account, identity, key, password, or private payload was created or tested.
- The assistant's credential vault is write-only; existing saved passwords cannot be extracted for sync.

## Open work

- Review and implement durable owner approval and peer pinning before any real traffic. SecureJoin, QR pairing, or a message is not owner approval.
- Complete atomic receive, replay/deduplication, rollback and conflict handling; do not treat the previous broken prototype as a starting implementation.
- Continue format/interoperability, malformed-file, KDF bounds, memory, crash-safety, and real-device testing before choosing storage.
- Test Android execution, Autofill, unlock, backup/restore, transport packaging, and device performance before making any such claims.
