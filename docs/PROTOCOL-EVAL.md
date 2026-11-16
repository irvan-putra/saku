# Protocol direction - selected for investigation, not implemented

The v1 direction is Delta Chat's native end-to-end encryption, with no forward-secrecy claim and pinned keys behind a reviewed owner-approval gate. The app gate is not implemented; no real traffic or keys are enabled. This is not an endorsement of the former prototype.

Archived ciphertext plus later key theft may expose past messages. No claim is made that core 2.69.0 ships forward secrecy. Avoid adding bespoke cryptography.

Source inspection and offline observations against core 2.69.0 support a possible app-side gate, not a finished implementation:
- `show_padlock` means a message was correctly encrypted and signed.
- Key-contacts are identified by key; different fingerprints at the same email address are distinct contacts. Addresses are not identity pins.
- Pin from the same-machine `check_qr` result: `askVerifyContact`, fingerprint, contact ID, invitation/auth fields. Normalize whitespace/case and require exactly 40 hex characters for the observed v4 fingerprint format.
- Never parse `get_contact_encryption_info`: it is localized and includes both keys and display names. Do not infer identity from a display name or address.
- A QR contact may have `e2eeAvail=false` until its actual key arrives. Keep it pending rather than trusting it or rejecting the legitimate pending step.
- Numeric contact IDs can change after database restore; durable fingerprint pinning and verified startup re-resolution are required.
- Require owner authorization through the trusted owner channel. A QR scan, successful SecureJoin, or Delta Chat message is not that authorization.

Remaining gates: durable approval state; matching key available and validated; one-to-one chat and pinned key-contact; correctly signed/encrypted per message; first `IncomingMsg` event only; RFC Message-ID dedupe committed atomically with schema validation/state apply; no edits/deletes as commands. No payload processing before an authorized live pin. Both directions need real two-account tests and device execution before any sync or security claim. The synthetic gate sketches are incomplete and excluded from this checkpoint.

## Other candidates, not recommendations

vodozemac has an Apache-2.0 Rust implementation and an independent audit. Android/agent bindings and lifecycle integration remain unverified here. Signal libsignal is AGPL-3.0; licensing, third-party support, bindings and integration need investigation. A license does not make integration secure. Anonymous sealed boxes do not authenticate senders; `crypto_box_seal` does not provide the proposed external AAD API. The earlier drop-in proposal is withdrawn. Noise plus a custom skipped-key scheme is excluded after the failed draft. No extra encryption layer will be improvised to bypass the review gate.

Sources:
https://raw.githubusercontent.com/chatmail/core/v2.69.0/deltachat-jsonrpc/src/api/types/message.rs
https://raw.githubusercontent.com/chatmail/core/v2.69.0/deltachat-jsonrpc/src/api/types/contact.rs
https://github.com/chatmail/core/blob/v2.69.0/deltachat-jsonrpc/src/api.rs
https://delta.chat/en/2025-08-04-encryption-v2
https://delta.chat/en/privacy
https://github.com/matrix-org/vodozemac
https://github.com/signalapp/libsignal
