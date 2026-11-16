# Security status

Not audited. Not production-ready. No real-password use.

The app is a synthetic UI only: no real storage, network, pairing, Autofill, or password-entry form. The host storage spikes are experiments, not a vault. No production keys or network identities were used.

The v1 protocol direction is Delta Chat native OpenPGP with pinned keys behind an explicit owner-approval gate and **no forward-secrecy claim**. Archived ciphertext plus later key theft may expose past messages. The gate is incomplete and absent from the app. No security property is established by passing synthetic tests alone.

A first private cryptographic draft was critically broken: public transcript material used as a secret, permanent desync on loss/garbage, and missing binding/atomicity controls. It was withdrawn before any code publication. The draft and anonymous-sealed-box proposal are not implementation paths. No bespoke crypto fallback is authorized to bypass the review gate.

Application state must authenticate, validate bounds, deduplicate, and apply in one durable transaction. An acknowledgement follows durable commit, never before. Per-entry versions, archive tombstones, replay/rollback resistance, safe snapshot merging, and phone-origin record preservation remain requirements.

Storage implementation is not selected. See `STORAGE-EVAL.md` for the retained failing interop case and later narrow host/package probes. Device memory/KDF performance, malformed-file limits, Android runtime support, and backup safety remain unverified.
