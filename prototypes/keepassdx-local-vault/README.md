# Local KDBX4 vault store prototype (KeePassDX overlay)

Disposable, test-only overlay for the pinned public KeePassDX source
(77ecfa8654a5bd0d79d512f8fa71c0af8a29b554). It prototypes the actual local
vault storage Saku needs, on the upstream database/crypto modules, with
synthetic data only. It is not app code, not a real vault, not a transport,
and not evidence of production safety. No sync, autofill, SAF, real-device or
A24 claims.

## What this adds over the dummy-import prototype

The dummy-import fixture exercised one hardcoded entry through a fixed state
machine. This overlay is a general local vault store:

- `KdbxKdfBudgetGate` - closes the documented open gap in
  `docs/STORAGE-EVAL.md`: at the pin, `Database.loadData` checks the caller's
  `Limits` against the *previously loaded* KDF engine, while a freshly parsed
  header's engine reaches the native transform with only intrinsic range
  checks (memory valid up to 4 GiB). The gate parses only the bounded outer
  KDBX4 header, reuses upstream's own `KdfParameters`/`VariantDictionary`
  codec and `KdfEngine.checkLimits`, and rejects an over-budget or malformed
  header *before* `Database.loadData` runs, so the large allocation never
  happens. KDBX 4.x only; older files fail closed. Header reads are capped at
  1 MiB total / 1 MiB per field.
- `LocalVaultStore` - create/unlock/add-entry/list/lock with:
  - synthetic-only gate (master password and every stored password must carry
    the `SYNTHETIC-` prefix, mirroring the app's `VaultGate`),
  - Argon2id KDF with KeePassDX defaults (16 MiB, 3 iterations) bounded by a
    caller budget (`TEST_LIMITS`: 32000000 bytes, 2 lanes - a test value, not
    an endorsed Samsung A24 budget),
  - atomic save: staging serialize, fsync candidate, reopen-verify candidate
    (budget gate + entry count), atomic rename, directory fsync.

## Coverage map

Host JVM (`test/`, runs in `:database:testDebugUnitTest`): the budget gate
only. KeePassDX's AES/Argon2 transforms are native-only (`UnsatisfiedLinkError`
is not caught by `AESTransformer`'s `catch (Exception)`), so no vault
roundtrip runs on the host.

Android emulator (`androidTest/`, x86_64 API 34, same toolchain as the
seven-storage-tests workflow): real native Argon2id create/unlock/add/reopen,
wrong-key, truncation and payload-tamper rejection, an over-budget patched
header rejected pre-transform, synthetic-gate refusals, and export of a fresh
KeePassDX-written fixture.

Fresh-write interoperability (`tools/check-fresh-kdbx.py`, CI job): the
exported fixture written by KeePassDX code is read by pykeepass 4.2.0 (exact
version pinned), checking the Argon2id KDF, the synthetic entry fields and
wrong-key rejection. This closes the recorded gap that no file written by
KeePassDX's own code had been read by a second implementation.

## Why the gate is load-bearing (upstream context)

The KDF transform runs before the header HMAC check upstream, so without the
gate an over-budget tampered header reaches the native transform before any
integrity rejection. Upstream's header parser also does not bound header field
reads; the gate's 1 MiB caps close that too.

## Known limits (independent review 2026-11-18; none block test-only use)

These are prototype limits, not production-ready guarantees:

- The gate duplicates only the trivial outer header framing (signature,
  version, field id/length). All KDF parameter parsing and validation is
  upstream's. A framing disagreement would fail closed, not open.
- `KdfEngine.checkLimits` treats parallelism over budget as a warning only
  (upstream `Argon2Kdf` logs and continues): a header with huge parallelism
  and in-budget memory passes the gate. Memory allocation stays bounded, so
  "over-budget rejected" applies to memory, not lanes.
- AES-KDF CPU-bound rounds are not budgeted by upstream `Limits`; only Argon2
  memory is. Out of scope here.
- Gate/load file-swap gap: the gate parses the file, then `Database.loadData`
  reopens it; a local swap between check and use is unchecked. Fine in a
  private test directory; must be closed before any production use.
  (`saveAtomic`'s verify-then-rename is safe because the rename preserves the
  verified inode.)
- If the post-rename directory fsync fails, `create()` reports failure with
  state LOCKED but leaves a valid vault file, and a later `create()` then
  refuses ("already exists"). Recoverable via `unlock()`; the error path is
  slightly misleading.
- The verification reopen compares entry count only; field-level corruption
  would pass.
- The synthetic gates use `concatToString()`, creating unwipeable `String`
  copies of passwords; the master password is also held in memory while
  unlocked for the verification reopen. Cleanup is best effort.
- After a failed `addEntry` save, the in-memory database diverges from disk
  until lock/unlock.
- `pykeepass`'s `kdf_algorithm` returns `None` on KDBX 4.1; not triggered
  here, but a future 4.1 write would fail the interop check loudly
  (fail-closed).
- No process-kill, power-loss, SAF provider, multi-process or second-reader
  coverage. Emulator function only; A24 memory and unlock latency unmeasured.
- Files written here are written by KeePassDX 4.5.8 at the pin; writes by
  other versions are not covered.
