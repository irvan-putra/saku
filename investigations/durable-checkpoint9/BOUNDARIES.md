# Production boundaries - required, NOT implemented by this model

No real-secret integration until the following strategy is independently
reviewed and implemented. Numbers below are proposed conservative model limits,
not owner policy or measured phone capacity. Reaching a limit pauses intake,
reports status and preserves data; it never silently drops or ages out entries.

## Approval and rollback anchor
A separate owner-only, no-backup, fsynced approval-epoch anchor must advance
BEFORE raising the database epoch/approval. Persist its parent directory on
first creation/replacement as supported; verify actual platform durability.
Then transactionally record the identical epoch and fresh session in the DB.
On startup and before a sensitive state transition, DB behind anchor, missing
anchor, invalid anchor or any mismatch fails closed with manual owner
reapproval/recovery. A crash after anchor raise but before DB commit therefore
locks rather than restoring old approval. Do not lower the anchor to match DB.

The anchor must not be in the same restore unit as the DB. This is not a
Keystore monotonic-counter claim. Device-root/full-image rollback, compromise
of both stores and unproven storage-fsync guarantees remain outside this
mechanism's protection. Android no-backup behavior, permissions and crash
ordering must be exercised, not assumed. Host SQLite epochs in state.py do
NOT implement this anchor or backup rollback resistance.

Every reapproval allocates a cryptographically fresh session identifier from
a reviewed platform RNG, never a caller-selected reused session. Historical
used sessions must remain rejectable until retirement protected by the same
rollback anchor. The model only enforces a historical used-session table; it
neither generates entropy nor protects that history against DB rollback.

## Core identity changes and per-message evidence
Any core account/database-generation change invalidates mapping immediately.
Serialize resolution and receive against restore/account replacement. Verify
the authenticated fingerprint from the actual message/core evidence on EVERY
message, not only numeric sender ID. Check it against durable approved pin and
session epoch in the transaction. The model's per-message fingerprint remains
a supplied test fact, not an available/authenticated real-core API claim.
If core cannot provide equivalent evidence, stop integration and review an
alternative, never trust a payload's claimed fingerprint.

## Permanent rejection and bounded queue
Classify malformed/schema/session/source/replay failures as permanent for that
immutable input. Persist a bounded redacted quarantine record (reason, source
identifier, byte count, no password content), stop automatic retry for that
item and emit one owner-visible status per reason/session with repeat counts.
A transient attachment download may retry up to 3 times with bounded backoff;
ack failure is ack_pending, never a pre-commit rejection or instruction to
reapply. No automatic retry loop on a poison item.
Proposed ceilings: 128 quarantine summaries, 4 MiB total quarantine metadata,
64 pending downloads and 4 MiB total pending bytes, 64 KiB per envelope.
If full, pause source intake with a visible capacity condition. No silent
overwrite or deletion. The actual transport must support pausing/backpressure.
Queue/notification policies and persistence are not coded in this model.

## State retention and pruning
Proposed session cap 16 historical sessions, 10,000 entries, 100,000 seen IDs
and 64 MiB total modeled application data. Measure before selecting phone
limits. Before exceeding them, pause and require reviewed compaction/recovery.
Keep historical sessions, dedupe IDs and tombstones unless an authenticated,
durable monotonically advancing session watermark makes old updates
unacceptable on both sides and the rollback anchor protects that watermark.
Never TTL-prune dedupe or tombstones. No watermark protocol or pruning exists
here. The prototype lacks resource caps, so it must not run as a live inbox.

## Real-secret storage and equality checking
The unkeyed SHA-256 message digest in this synthetic model can enable guessing
of low-entropy secret fields. It MUST NOT be copied to real-password storage.
A reviewed DB-local HMAC key inside the selected encrypted store is required
for equality/replay binding; key generation, custody and rotation need review.
This is an integration requirement, not permission to add custom cryptography.
Encrypt the DB and all WAL/SHM/journal/temp artifacts using the reviewed store
and verify actual behavior. Apply least-access file/directory permissions and
verify PRAGMA journal_mode/synchronous returned state. The model now checks
WAL and FULL results, but is plaintext and does not enforce private permissions.
No secret storage or Android behavior is established by that check.

## Archives, conflicts and deletes
Delete/archive features remain blocked until versioned tombstones and snapshot
merge preservation are implemented and reviewed. A manual edit currently
protects the phone-origin row conservatively by rejecting agent writes. This
is NOT a settled owner UX choice. Integration must surface a conflict without
silently overwriting either version; ownership/merge behavior is a later
working-product decision. No user policy is inferred from this prototype.

## Checkpoint9 bounded failure model, not a complete revocation protocol
The model writes/fsyncs a separate revocation-request sidecar before attempting
SQLite revoke. All modeled resolution/receive paths check it; it survives a
restart. SQLite lock waiting is bounded by a caller-set timeout, default 0.1s;
there is no unbounded retry loop. A DB lock/write failure raises RevokeFailed
with access_may_still_be_active=True, never a success status. It reports that
the durable DB pair may still be approved even when cooperating paths stop.
If sidecar creation/fsync fails, another process CAN STILL RECEIVE. A test
explicitly demonstrates this and verifies the failure is surfaced.

This sidecar is NOT the production approval epoch anchor, a guaranteed disk
write, a global cancellation primitive or rollback protection. It does not
stop an already in-flight transaction past its last check, noncooperating
processes, malicious deletion, full-image restore or another actor clearing
it. Modeled explicit new-session reapproval removes it; concurrent reapproval
vs revocation requires a reviewed cross-process ordering/owner-action protocol
before production. No guarantee that every concurrent revoke wins is made.
Failures must show an unmistakable future UI state: "Revocation failed; access
may still be active" and offer bounded retry/recovery, not say "revoked".
Fingerprint/mapping invalidation must likewise be visible as blocked/unpaired,
not a silent disconnected state. These UI paths are not built here.

Old checkpoint7 databases seed their current session into used_sessions on
startup, preventing immediate reuse. Sessions predating the retained current
pair cannot be recovered from a schema that never recorded them; such legacy
state requires manual fresh pairing/history reset policy, not a claim of full
historical reuse detection. Migration has no real secret data in this project.

## Final independent-review notes (checkpoint9 code frozen)
- The sidecar is a cooperative flag alongside the SAME database path/restore
  unit, NOT an independent rollback anchor. Restoring DB plus sidecar together
  can restore old approval. The production external anchor remains unbuilt.
- The 0.1-second SQLite busy timeout applies to ALL model connections by
  default. Operations other than revoke may still raise raw OperationalError
  before commit, rather than a typed retryable result. Production retry/status
  handling is not implemented. Do not infer all errors are safely classified.
- Concurrent approval can COMMIT and then unlink the sidecar after another
  process has written a revoke flag whose DB revoke failed. That ordering can
  erase the flag. The actual owner flow must serialize approval/revocation
  across processes and define recovery ordering before integration. This
  known race is documented, not fixed or claimed safe in checkpoint9.
- Catching BaseException from ack consumes one interrupt once the DB outcome
  is known and returns ack_pending. This is a conscious model tradeoff, not
  a general cancellation strategy. Process termination cannot be converted
  into a return value; durable duplicate retry remains necessary.
- Tombstones, independent rollback anchor, quotas/pruning, HMAC equality key,
  full encrypted-store/WAL/SHM protection and actual trusted authentication
  remain unimplemented. Model code is frozen for research inspection; known
  flaws do not become an operational authorization/revocation gate.
