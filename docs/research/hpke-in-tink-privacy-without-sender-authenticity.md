# HPKE in Tink: what the public hybrid API gives you

Status: Tink documentation read 2026-11-16. Source-code and Android-platform claims from earlier work were not re-fetched and are not stated here.

- Tink's hybrid encryption page says the recommended key type is `DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM`, which "implements the Hybrid Public Key Encryption (HPKE) standard as specified in RFC 9180". https://developers.google.com/tink/hybrid
- The same page says hybrid encryption provides privacy but "does not inherently guarantee authenticity, requiring separate sender authentication mechanisms if needed".
- It also describes a `context_info` parameter: public context data bound to the ciphertext so its integrity can be confirmed.

What this means for a design (reasoning, not a source claim): if a message scheme needs to prove who sent a message, the Tink hybrid API alone does not do that. Adding a signature on top is possible, but it is a custom composition and would need review by someone who does this for a living. Whether Tink or Android expose HPKE's authenticated modes was not re-checked.
