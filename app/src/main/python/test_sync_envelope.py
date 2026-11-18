"""Host-side tests for the SYNC1 codec mirror (no Android, no DeltaChat).

The fixed vectors below are the canonical wire bytes SyncEnvelope.kt must
produce for the same inputs; the 2026-11-18 live wire test used the same
payloads between the two synthetic chatmail accounts.
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sync_runtime import (MAX_PAYLOAD_BYTES, decode_envelope,  # noqa: E402
                          encode_envelope)

P1 = b"SYNTHETIC-transport-v1-from-a"
P1_SHA = "c8e7370855e210ec70b2bc8b87aebb5f57168d360d2049c0dd0950fed65d1153"
P2 = b"SYNTHETIC-transport-v2-from-b"
P2_SHA = "d9a3205971886b5e0ffd6476532932cfdb433043036c54f1742e739b66c283f4"

EXPECTED_A_TO_B = (
    b"SYNC1\nSYNTHETIC-vault-sync\nSYNTHETIC-device-a\n1\n-\n"
    + P1_SHA.encode()
    + b"\n1000\n\n" + P1
)
EXPECTED_B_TO_A = (
    b"SYNC1\nSYNTHETIC-vault-sync\nSYNTHETIC-device-b\n2\n" + P1_SHA.encode()
    + b"\n" + P2_SHA.encode() + b"\n2000\n\n" + P2
)


class SyncEnvelopeCodecTest(unittest.TestCase):
    def test_fixed_vector_a_to_b_matches_kotlin_format(self):
        wire = encode_envelope("SYNTHETIC-vault-sync", "SYNTHETIC-device-a",
                               1, None, 1000, P1)
        self.assertEqual(EXPECTED_A_TO_B, wire)

    def test_fixed_vector_b_to_a_with_base_chain(self):
        wire = encode_envelope("SYNTHETIC-vault-sync", "SYNTHETIC-device-b",
                               2, P1_SHA, 2000, P2)
        self.assertEqual(EXPECTED_B_TO_A, wire)

    def test_roundtrip(self):
        wire = encode_envelope("SYNTHETIC-vault-sync", "SYNTHETIC-device-a",
                               7, P2_SHA, 3000, P1)
        fields, payload = decode_envelope(wire)
        self.assertEqual("SYNTHETIC-vault-sync", fields["vault_id"])
        self.assertEqual("SYNTHETIC-device-a", fields["device_id"])
        self.assertEqual(7, fields["seq"])
        self.assertEqual(P2_SHA, fields["base_sha256"])
        self.assertEqual(3000, fields["created_at_ms"])
        self.assertEqual(P1, payload)

    def test_decode_of_fixed_vector(self):
        fields, payload = decode_envelope(EXPECTED_B_TO_A)
        self.assertEqual(2, fields["seq"])
        self.assertEqual(P1_SHA, fields["base_sha256"])
        self.assertEqual(P2, payload)

    def test_tampered_payload_refused(self):
        tampered = EXPECTED_A_TO_B[:-1] + b"B"
        with self.assertRaises(ValueError):
            decode_envelope(tampered)

    def test_tampered_payload_hash_field_refused(self):
        tampered = EXPECTED_A_TO_B.replace(P1_SHA[:8].encode(), b"0" * 8, 1)
        with self.assertRaises(ValueError):
            decode_envelope(tampered)

    def test_seq_is_not_hash_covered_by_design(self):
        # The hash covers the payload only; seq is merge metadata. Decode
        # accepts it (same as SyncEnvelope.kt) and SyncMerge decides
        # conflicts. Divergent same-seq payloads are a CONFLICT there.
        tampered = EXPECTED_A_TO_B.replace(b"\n1\n-", b"\n9\n-", 1)
        fields, payload = decode_envelope(tampered)
        self.assertEqual(9, fields["seq"])
        self.assertEqual(P1, payload)

    def test_real_looking_ids_refused(self):
        with self.assertRaises(ValueError):
            encode_envelope("my-real-vault", "SYNTHETIC-device-a", 1, None, 1, P1)
        with self.assertRaises(ValueError):
            encode_envelope("SYNTHETIC-vault", "pixel-7", 1, None, 1, P1)

    def test_oversized_payload_refused(self):
        with self.assertRaises(ValueError):
            encode_envelope("SYNTHETIC-v", "SYNTHETIC-d", 1, None, 1,
                            b"x" * (MAX_PAYLOAD_BYTES + 1))

    def test_garbage_refused(self):
        for blob in (b"", b"SYNC1", b"SYNC1\n\n", b"HELLO\n\nworld",
                     b"SYNC1\na\nb\nc\nd\ne\n\nx"):
            with self.assertRaises(ValueError):
                decode_envelope(blob)


if __name__ == "__main__":
    unittest.main()
