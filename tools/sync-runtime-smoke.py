#!/usr/bin/env python3
"""Live synthetic sync smoke: run the repo's SyncRuntime unmodified over
two synthetic chatmail accounts.

Exercises app/src/main/python/sync_runtime.py end to end: configure, start
IO, SecureJoin pair, send a SYNC1 envelope A->B, verify byte-exact decode on
B, then B->A with a base hash. This proves runtime logic on the live relay;
it is NOT Android/background-receive proof (no chaquopy, no WorkManager).

Credentials come from env (GitHub secrets in CI); they are never printed.
Synthetic data only: every id and payload carries the SYNTHETIC- gate.

Exit codes: 0 PASS, 2 missing env, 3 pairing failed, 4 A->B failed,
5 B->A failed.
"""
import hashlib
import os
import queue
import shutil
import sys
import tempfile
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "app", "src", "main", "python"))

import sync_runtime  # the repo module under test


def need(name):
    value = os.environ.get(name, "")
    if not value:
        print("MISSING ENV:", name, flush=True)
        sys.exit(2)
    return value


ADDR_A = need("SAKU_SYNC_ADDR_A")
PW_A = need("SAKU_SYNC_MAIL_PW_A")
ADDR_B = need("SAKU_SYNC_ADDR_B")
PW_B = need("SAKU_SYNC_MAIL_PW_B")

# SyncRuntime expects native_lib_dir/librpcsrv.so; on the host the pip wheel
# ships the same rpc-server binary, so symlink it under that name.
import deltachat_rpc_server
WHEEL_BIN = os.path.join(os.path.dirname(deltachat_rpc_server.__file__),
                         "deltachat-rpc-server")
TMP = tempfile.mkdtemp(prefix="saku-sync-smoke-")
NATIVE = os.path.join(TMP, "native")
os.makedirs(NATIVE)
os.symlink(WHEEL_BIN, os.path.join(NATIVE, "librpcsrv.so"))


def make_runtime(tag, addr, pw):
    rt = sync_runtime.SyncRuntime(os.path.join(TMP, tag), NATIVE)
    rt.start()
    acct = rt.dc.add_account()
    acct.set_config("addr", addr)
    acct.set_config("mail_pw", pw)
    acct.configure()
    if not acct.is_configured():
        raise RuntimeError("account %s did not configure" % tag)
    return rt


def wait_pair(joiner, inviter, timeout_s=75):
    """Wait for SecurejoinJoinerProgress/InviterProgress == 1000."""
    qa = joiner.rpc.get_queue(joiner.account().id)
    qb = inviter.rpc.get_queue(inviter.account().id)
    deadline = time.time() + timeout_s
    join_ok = inv_ok = False
    while time.time() < deadline and not (join_ok and inv_ok):
        for q in (qa, qb):
            try:
                ev = q.get(timeout=0.3)
            except queue.Empty:
                continue
            kind = ev.get("kind", "")
            if kind == "SecurejoinJoinerProgress" and ev.get("progress") == 1000:
                join_ok = True
            if kind == "SecurejoinInviterProgress" and ev.get("progress") == 1000:
                inv_ok = True
    return join_ok and inv_ok


def recv_one(rt, timeout_s=60):
    for fields, payload in rt.poll_envelopes(timeout_s):
        return fields, payload
    return None, None


def main():
    rt_a = make_runtime("a", ADDR_A, PW_A)
    rt_b = make_runtime("b", ADDR_B, PW_B)
    try:
        time.sleep(8)  # let IO settle, per the proven wire test

        qr = rt_b.pair_qr()
        rt_a.accept_qr(qr)
        if not wait_pair(rt_a, rt_b):
            print("RESULT: SecureJoin pairing did not complete", flush=True)
            return 3
        print("paired: SecureJoin 1000/1000 both sides", flush=True)

        addr_a = rt_a.account_addr()
        addr_b = rt_b.account_addr()

        p1 = b"SYNTHETIC-runtime-v1-from-a"
        rt_a.send_envelope(addr_b, "SYNTHETIC-vault-sync", "SYNTHETIC-device-a",
                           1, None, 1000, p1)
        print("A->B sent on verified chat", flush=True)
        fields, payload = recv_one(rt_b, 60)
        if payload != p1 or fields is None or fields["seq"] != 1 \
                or fields["device_id"] != "SYNTHETIC-device-a":
            print("RESULT: A->B not received byte-exact", flush=True)
            return 4
        print("A->B ok seq 1 payload byte-exact", flush=True)

        p2 = b"SYNTHETIC-runtime-v2-from-b"
        rt_b.send_envelope(addr_a, "SYNTHETIC-vault-sync", "SYNTHETIC-device-b",
                           2, hashlib.sha256(p1).hexdigest(), 2000, p2)
        print("B->A sent on verified chat", flush=True)
        fields, payload = recv_one(rt_a, 60)
        if payload != p2 or fields is None or fields["seq"] != 2 \
                or fields["base_sha256"] != hashlib.sha256(p1).hexdigest():
            print("RESULT: B->A not received byte-exact", flush=True)
            return 5
        print("B->A ok seq 2 payload byte-exact, base hash chained", flush=True)

        print("RUNTIME SMOKE PASSED (host-python, live relay, synthetic only)",
              flush=True)
        return 0
    finally:
        rt_a.stop()
        rt_b.stop()
        shutil.rmtree(TMP, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
