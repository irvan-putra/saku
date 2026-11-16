# Durable receive checkpoint 9 - synthetic architecture prototype

License pending. Research/model code, not an operational password manager.
No crypto, network, actual owner authentication, real identity or vault.
Python standard library only. Run `python3 -m unittest -v` here.

## What is actually implemented and tested
SQLite transaction semantics with WAL and synchronous=FULL:
- Durable modeled pair fingerprint/session/phase/approval epoch.
- No trusted numeric contact/chat mapping survives a process startup.
- Modeled core resolution must match durable fingerprint and one-to-one
  key-contact/encryption-availability facts before that process enables receive.
- Revocation is durable; stale processes fail the epoch check even if another
  process revokes then reapproves the same peer/session.
- Fixed-schema, bounded synthetic entry updates only. Version rollback and
  equal-version conflicts rejected; phone-origin records protected.
- Seen RFC Message-ID, envelope ID and canonical body digest are committed in
  the same transaction as entry changes. Replays with altered data rejected.
- Ack invoked only after commit; failed ack does not undo persisted state.
  An authenticated duplicate delivery can re-ack without applying again.

21 tests pass. A subprocess exits with os._exit at four actual boundaries:
after entry write, after dedupe write, before commit and after commit. Reopen
checks SQLite integrity, both-table all-or-neither state, no pre-commit ack,
startup identity gating and safe retry. Another SQLite connection confirms
committed seen state exists when ack executes. Additional tests cover source,
schema, rollback, identity, restart, stale epoch and revocation behavior.

## Deliberately NOT implemented
`modeled_trusted_owner_approval` is a TEST ADAPTER. It does not authenticate
an owner, inspect trusted-channel permission or prove a UI action is genuine.
`modeled_verified_core_resolution` and message authentication flags are TEST
ADAPTERS, not actual core/QR/SecureJoin integration or cryptographic evidence.
They must not be wired to untrusted payload-supplied booleans in production.
No direct callable method here creates authority for a real task.

This model is not a complete transport gate. No actual signature validation,
QR parsing, contact re-resolution API, encrypted local SQLite, Android runtime,
concurrent core/account restore lock, file attachment pipeline, snapshot merge,
archive tombstones, event watermark, acknowledgement authentication or lost-ack
scheduler exists. Database backup rollback resistance is NOT solved by a local
epoch: restoring all durable data can roll that epoch back. See BOUNDARIES.md for the required production design and concrete proposed
capacity/rejection policies, none implemented as live integration.
No power-loss or
hardware/fsync guarantee is established by process-death tests.
The prefix filter is synthetic test scoping, not secret detection.

Before real integration: independent review of this transaction model; trusted
approval provenance and durable app UI state; actual core facts/identity adapter;
reviewed encrypted-storage transaction strategy; restore/account-generation
binding; safe snapshot/archive semantics; real Android execution and transport
tests; explicit authority for identities and any live data. No storage pick
or custom crypto is made by this prototype.


## Review fix in checkpoint 9
Checkpoint7 resolution had a real race: SELECT then an unconditional UPDATE
could resurrect a concurrent revoked approval. Resolution now uses BEGIN
IMMEDIATE, re-reads and compares the entire expected row, checks a conditional
non-revoked epoch UPDATE affected exactly one row, then commits. An interleave
test commits revocation between observation and lock; resolution rejects and
revocation survives restart. This is an actual model bug fix, not an audit.

Historical session reuse now rejects; modeled authenticated fingerprint is
checked per message and mismatch invalidates runtime mapping. ACK exceptions
return ReceiveResult(applied|duplicate, ack_pending=True) after commit, distinct
from pre-commit Reject. WAL/FULL returned state is verified. New tests exercise
these properties. Production anchor/HMAC/encryption/queue/retention/tombstones
are deliberately not claimed implemented. See BOUNDARIES.md.


## Checkpoint9 review delta
A busy SQLite writer previously left revoke() as an OperationalError while the
durable pair stayed approved. It now reports RevokeFailed/access-may-still-be-
active and writes a fail-closed model sidecar before attempting DB revoke.
Resolution/receive check the sidecar. Actual two-connection lock failure and
sidecar-write-failure tests exercise both outcomes, including demonstrating
that access CAN REMAIN ACTIVE when sidecar writing fails. This is not a full
revocation protocol or production epoch anchor; see BOUNDARIES.md for races,
in-flight operations and failure limitations. Old current sessions are seeded
at migration. All BaseException from ack is represented as ack_pending after
commit, including a tested KeyboardInterrupt. No raw ack exception is confused
with a precommit rejection; process death still relies on duplicate retry.
