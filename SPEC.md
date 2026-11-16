# Saku - design checkpoint 1

Status: early design draft. Documentation only. No Android app or protocol implementation is published in this checkpoint.

## Known broken prototype: do not use

An earlier synthetic protocol prototype was found to be unsafe. The handshake-derived root value is visible in the public transcript, allowing an observer to derive message keys and decrypt captured traffic. The prototype also lacks safe handling for state loss/desynchronization, authenticated peer-pin persistence, and rollback-safe snapshot recovery. These are critical design failures, not minor implementation gaps.

**Do not use the earlier prototype, this draft, or any related design with real passwords, accounts, keys, or live networks. No security property is established.** The prototype's code and tests are intentionally excluded from this public checkpoint. A passing synthetic test suite would not prove these properties safe.

## Goal

Explore a local-first Android password manager with a usable on-device vault and optional sync with the owner's assistant. The phone should also support manually added records. Existing-password import/migration is not yet scoped.

This is a project goal, not a claim that the app, vault, pairing, sync, recovery, or Autofill works.

## Current state

- No Android application has been built.
- No network identity, real account, key, password, or private payload has been created for this project.
- No crypto design is approved for implementation. The previously proposed Noise XK plus custom hash-ratchet design is withdrawn pending independent security review and replacement or repair.
- The assistant's credential vault is write-only; existing saved passwords cannot be extracted for sync. Any future sync path must be designed around that limit.

## Open design questions

- Compare mature, interoperable password-vault formats and Android-compatible storage before choosing a format or writing custom crypto.
- Define a reviewed pairing and peer-authentication design, including durable peer pinning, replay/rollback handling, state loss, snapshot recovery and revocation.
- Confirm whether the assistant should sync only logins it creates or support a broader workflow; this is not decided.
- Decide how manual phone entries are stored and whether they ever sync back.
- Verify DeltaChat's Android transport packaging and operation on a real device before making any sync claim. Previous component work does not prove Saku runs on Android.
- Select a repository license before calling this project open source.

## Next checkpoint

Compare existing vault formats and reviewed protocol/storage options. Keep any implementation synthetic-only until an independent security review clears a design. This checkpoint deliberately contains no executable protocol code.
