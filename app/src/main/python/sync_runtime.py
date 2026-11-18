"""Saku DeltaChat sync runtime (prototype, synthetic-only).

Mirror of app/src/main/java/com/vanipy/saku/sync/SyncEnvelope.kt: the SAME
canonical SYNC1 wire format, the same SYNTHETIC- prefix gate, the same
bounds. Keep both sides byte-compatible; changes land in both or neither.

The runtime owns the device side of the proven transport flow (see
docs/deltachat-sync-groundwork.md and the 2026-11-18 wire test):
  - pair devices with SecureJoin QR;
  - send ONLY on the verified 1:1 chat that SecureJoin created (chatmail
    refuses unverified chats with 'e2e encryption unavailable');
  - start_io() after every Rpc start: no mail flows without it.

deltachat_rpc_client is imported lazily inside start() so host-side unit
tests can exercise the codec without the chaquopy-bundled runtime.
"""
import hashlib
import re
import time

VERSION = "SYNC1"
MAX_HEADER_BYTES = 4096
MAX_PAYLOAD_BYTES = 4 * 1024 * 1024
HEX64 = re.compile(r"[0-9a-f]{64}")
ID = re.compile(r"SYNTHETIC-[A-Za-z0-9._-]{1,64}")


def _require_id(value, what):
    if not ID.match(value or ""):
        raise ValueError("%s must be a synthetic id (SYNTHETIC-...)" % what)


def encode_envelope(vault_id, device_id, seq, base_sha256, created_at_ms, payload):
    """Encode an envelope exactly like SyncEnvelope.encode (Kotlin)."""
    _require_id(vault_id, "vaultId")
    _require_id(device_id, "deviceId")
    if seq < 0:
        raise ValueError("seq must be >= 0")
    if created_at_ms < 0:
        raise ValueError("createdAtEpochMs must be >= 0")
    if base_sha256 is not None and not HEX64.match(base_sha256):
        raise ValueError("bad base hash")
    if len(payload) > MAX_PAYLOAD_BYTES:
        raise ValueError("payload too large")
    digest = hashlib.sha256(payload).hexdigest()
    header = "\n".join([
        VERSION, vault_id, device_id, str(seq),
        base_sha256 or "-", digest, str(created_at_ms),
    ]) + "\n\n"
    head = header.encode("utf-8")
    if len(head) > MAX_HEADER_BYTES:
        raise ValueError("header too large")
    return head + payload


def decode_envelope(data):
    """Fail-closed parse mirroring SyncEnvelope.decode. Returns
    (fields dict, payload bytes). Raises ValueError on any problem."""
    if len(data) > MAX_HEADER_BYTES + MAX_PAYLOAD_BYTES:
        raise ValueError("too large")
    sep = data.find(b"\n\n")
    if sep < 0 or sep > MAX_HEADER_BYTES:
        raise ValueError("bad header")
    lines = data[:sep].decode("utf-8").split("\n")
    if len(lines) != 7 or lines[0] != VERSION:
        raise ValueError("bad header")
    _, vault_id, device_id, seq_s, base_s, digest_s, created_s = lines
    _require_id(vault_id, "vaultId")
    _require_id(device_id, "deviceId")
    if not seq_s.isdigit() or not created_s.isdigit():
        raise ValueError("bad numbers")
    if base_s != "-" and not HEX64.match(base_s):
        raise ValueError("bad base hash")
    if not HEX64.match(digest_s):
        raise ValueError("bad payload hash")
    payload = data[sep + 2:]
    if len(payload) > MAX_PAYLOAD_BYTES:
        raise ValueError("payload too large")
    if hashlib.sha256(payload).hexdigest() != digest_s:
        raise ValueError("payload hash mismatch")
    return ({"vault_id": vault_id, "device_id": device_id, "seq": int(seq_s),
             "base_sha256": None if base_s == "-" else base_s,
             "payload_sha256": digest_s, "created_at_ms": int(created_s)}, payload)


class SyncRuntime:
    """DeltaChat rpc-server lifecycle + SYNC1 send/receive for Saku."""

    def __init__(self, files_dir, native_lib_dir):
        self.files_dir = files_dir
        self.native_lib_dir = native_lib_dir
        self.rpc = None
        self.dc = None

    def start(self):
        import os
        from deltachat_rpc_client import DeltaChat, Rpc
        self.rpc = Rpc(
            accounts_dir=os.path.join(self.files_dir, "accounts"),
            rpc_server_path=os.path.join(self.native_lib_dir, "librpcsrv.so"))
        self.rpc.start()
        self.dc = DeltaChat(self.rpc)
        self.dc.start_io()
        return self

    def stop(self):
        if self.rpc is not None:
            self.rpc.close()
            self.rpc = None
            self.dc = None

    def account(self):
        accts = [x for x in self.dc.get_all_accounts() if x.is_configured()]
        if not accts:
            raise RuntimeError("no configured sync account")
        return accts[0]

    def pair_qr(self):
        """This device invites: return the SecureJoin QR text to show."""
        return self.account().get_qr_code()

    def accept_qr(self, qr_text):
        """This device joins a peer's invite QR."""
        return self.account().secure_join(qr_text)

    def verified_chat(self, peer_addr):
        """The SecureJoin-verified 1:1 chat to peer_addr; never a fresh
        create_contact chat (chatmail refuses those)."""
        for ch in self.account().get_chatlist():
            s = ch.get_basic_snapshot()
            if s.chat_type == "Single" and s.name == peer_addr:
                return ch
        raise RuntimeError("no verified chat for peer")

    def send_envelope(self, peer_addr, vault_id, device_id, seq, base_sha256,
                      created_at_ms, payload):
        wire = encode_envelope(vault_id, device_id, seq, base_sha256,
                               created_at_ms, payload)
        self.verified_chat(peer_addr).send_text(wire.decode("utf-8"))

    def poll_envelopes(self, timeout_s=30):
        """Yield (fields, payload) for incoming SYNC1 messages until the
        deadline. Unknown/foreign messages are marked noticed and skipped."""
        deadline = time.time() + timeout_s
        acct = self.account()
        while time.time() < deadline:
            for m in acct.get_fresh_messages():
                text = m.get_snapshot().text or ""
                if text.startswith(VERSION + "\n"):
                    yield decode_envelope(text.encode("utf-8"))
            time.sleep(2)
