# Saku DeltaChat sync - groundwork (local, synthetic only)

Goal of the full feature (user, 2026-11-16): logins Instinct creates land in
the phone vault without manual copy links, synced over DeltaChat.

This groundwork covers only the local, transport-free pieces: the sync
envelope format and the merge decision. There are NO accounts, no network,
no real secrets here, and the vault layer is unchanged.

## Envelope (SYNC1)

Canonical byte format (see SyncEnvelope.kt): seven UTF-8 header lines
(version, vaultId, deviceId, seq, basePayloadSha256, payloadSha256,
createdAtEpochMs), one empty line, then the raw payload bytes (the sync
payload, e.g. an encrypted KDBX, opaque to this layer).

Properties:
- Deterministic encoding (same fields + payload -> identical bytes).
- Bounded: header <= 4 KiB, payload <= 4 MiB, decode fails closed on any
  malformed field, bad hash format, or payload/hash mismatch.
- Synthetic-only gate: vaultId and deviceId must match
  `SYNTHETIC-[A-Za-z0-9._-]{1,64}`. A real vault id cannot be encoded or
  accepted while Saku is a prototype. Remove the gate only with the
  real-secret readiness review.

## Merge (SyncMerge)

One vault identified by vaultId; each writer bumps a monotonic seq and
records the payload hash it based its revision on (basePayloadSha256).

- remote.seq higher -> APPLY_REMOTE
- remote.seq lower -> KEEP_LOCAL
- equal seq, equal payload hash -> SAME (converged)
- equal seq, different payload, or different vaultId -> CONFLICT

CONFLICT is deliberately conservative: the groundwork never resolves
divergence by discarding data. Conflict resolution UI/flow is future work.

## Not covered yet (explicit non-claims)

- DeltaChat transport (accounts, attachments, send/receive) - needs its own
  scoped authority before any external test account is touched.
- Applying a payload to the vault (decrypt/merge entries) - vault-layer
  work, emulator-qualified native path, independent review for real secrets.
- Seq/base persistence in the app, device identity, conflict UX.
- A24 device measurements.

Tests: SyncGroundworkTest (12 host tests) covers roundtrip, determinism,
version/truncation/hash/size/synthetic refusals and the full merge matrix.
