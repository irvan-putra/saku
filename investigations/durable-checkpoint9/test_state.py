import json
import os
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from state import State,Reject,ReceiveResult,RevokeFailed
FP='AB'*20
OTHER='CD'*20

def resolve(s,cid=7):
    s.modeled_verified_core_resolution(FP,cid,11,key_available=True,key_contact=True,one_to_one=True)

def armed(p):
    s=State(p);s.modeled_trusted_owner_approval(FP,'s1');resolve(s);return s

def message(**kw):
    m=dict(session='s1',envelope='env1',entry='e1',version=1,value='SYNTHETIC-one');m.update(kw)
    return json.dumps(m).encode()

def recv(s,raw=None,**kw):
    args=dict(event='IncomingMsg',mid='<one@test.invalid>',contact_id=7,chat_id=11,encrypted_signed=True,key_contact=True,one_to_one=True,authenticated_fingerprint=FP,ack=lambda _:None)
    args.update(kw)
    result=s.receive(message() if raw is None else raw,**args)
    return result.outcome if not result.ack_pending else result

class Tests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.p=self.tmp.name+'/state.db';self.s=armed(self.p)
    def tearDown(self): self.s.close();self.tmp.cleanup()
    def counts(self):
        return tuple(self.s.db.execute('SELECT count(*) FROM '+t).fetchone()[0] for t in ('entries','seen'))
    def test_prepin_and_restart_fail_closed(self):
        self.s.close();self.s=State(self.p)
        with self.assertRaises(Reject):recv(self.s)
        resolve(self.s,42)
        with self.assertRaises(Reject):recv(self.s)
        self.assertEqual(recv(self.s,contact_id=42),'applied')
    def test_wrong_fingerprint_clears_mapping(self):
        with self.assertRaises(Reject):self.s.modeled_verified_core_resolution(OTHER,7,11,key_available=True,key_contact=True,one_to_one=True)
        with self.assertRaises(Reject):recv(self.s)
    def test_pending_key_and_non_key_or_group_fail(self):
        for field in ('key_available','key_contact','one_to_one'):
            args=dict(key_available=True,key_contact=True,one_to_one=True);args[field]=False
            with self.assertRaises(Reject): self.s.modeled_verified_core_resolution(FP,7,11,**args)
            with self.assertRaises(Reject): recv(self.s)
    def test_source_and_auth_rejections_no_mutation(self):
        for args in ({'event':'MsgsChanged'},{'event':'MsgEdited'},{'event':'MsgDeleted'},{'contact_id':8},{'chat_id':12},{'encrypted_signed':False},{'key_contact':False},{'one_to_one':False},{'mid':''}):
            with self.assertRaises(Reject):recv(self.s,**args)
            self.assertEqual(self.counts(),(0,0))
    def test_schema_failure_does_not_consume_mid(self):
        for raw in (b'{}',b'not-json',b'x'*65537,message(version=True),message(value='not-fixture')):
            with self.assertRaises(Reject):recv(self.s,raw)
            self.assertEqual(self.counts(),(0,0))
        self.assertEqual(recv(self.s),'applied')
    def test_ack_observes_committed_state_from_second_connection(self):
        seen=[]
        def ack(_):
            with sqlite3.connect(self.p) as other:
                seen.append(other.execute('SELECT count(*) FROM seen').fetchone()[0])
        recv(self.s,ack=ack);self.assertEqual(seen,[1])
    def test_duplicate_reack_no_second_apply(self):
        a=[];self.assertEqual(recv(self.s,ack=a.append),'applied')
        self.assertEqual(recv(self.s,ack=a.append),'duplicate');self.assertEqual(a,['env1','env1']);self.assertEqual(self.counts(),(1,1))
        with self.assertRaises(Reject):recv(self.s,message(value='SYNTHETIC-changed'))
        with self.assertRaises(Reject):recv(self.s,mid='<different@test.invalid>')
    def test_ack_failure_does_not_rollback_commit(self):
        def fail(_):raise OSError('modeled send failure')
        self.assertEqual(recv(self.s,ack=fail),ReceiveResult('applied',True))
        self.assertEqual(self.counts(),(1,1));self.assertEqual(recv(self.s),'duplicate')
    def test_rollback_conflict_phone_entries_preserved(self):
        recv(self.s,message(version=3))
        for version,value in ((2,'SYNTHETIC-old'),(3,'SYNTHETIC-conflict')):
            with self.assertRaises(Reject):recv(self.s,message(envelope='other',version=version,value=value),mid='<other@test.invalid>')
        self.s.db.execute("INSERT INTO entries VALUES('phone',1,'SYNTHETIC-local','phone')")
        with self.assertRaises(Reject):recv(self.s,message(entry='phone',envelope='phone'),mid='<phone@test.invalid>')
        self.assertEqual(self.counts(),(2,1))
    def test_revocation_persists_and_blocks_restart_resolution(self):
        self.s.revoke();self.s.close();self.s=State(self.p)
        with self.assertRaises(Reject):resolve(self.s)
        with self.assertRaises(Reject):recv(self.s)
    def test_owner_reapproval_requires_new_runtime_resolution(self):
        self.s.modeled_trusted_owner_approval(FP,'s2')
        with self.assertRaises(Reject):recv(self.s)
        resolve(self.s)
        with self.assertRaises(Reject):recv(self.s)
    def test_stale_process_cannot_resume_after_revoke_and_reapprove(self):
        other=State(self.p)
        try:
            other.revoke();other.modeled_trusted_owner_approval(FP,'s2');resolve(other)
            with self.assertRaises(Reject):recv(self.s)
            self.assertEqual(self.counts(),(0,0))
            resolve(self.s);self.assertEqual(recv(self.s,message(session='s2')),'applied')
        finally:other.close()
    def test_resolution_cannot_overwrite_interleaved_revocation(self):
        other=State(self.p)
        try:
            with self.assertRaises(Reject):
                self.s.modeled_verified_core_resolution(FP,7,11,key_available=True,key_contact=True,one_to_one=True,interleave=other.revoke)
            self.assertEqual(other.db.execute('SELECT phase FROM pair').fetchone()[0],'revoked')
            self.s.close();self.s=State(self.p)
            with self.assertRaises(Reject):resolve(self.s)
        finally:other.close()
    def test_session_cannot_be_reused_after_reapproval(self):
        self.s.revoke()
        with self.assertRaises(Reject):self.s.modeled_trusted_owner_approval(FP,'s1')
        self.s.modeled_trusted_owner_approval(FP,'s2')
        with self.assertRaises(Reject):self.s.modeled_trusted_owner_approval(FP,'s1')
    def test_same_numeric_id_wrong_permessage_key_invalidates_mapping(self):
        with self.assertRaises(Reject):recv(self.s,authenticated_fingerprint=OTHER)
        with self.assertRaises(Reject):recv(self.s)
    def test_duplicate_ack_failure_is_distinct(self):
        recv(self.s)
        def fail(_):raise OSError('ack fail')
        self.assertEqual(recv(self.s,ack=fail),ReceiveResult('duplicate',True))
    def test_busy_revoke_failure_and_sidecar(self):
        other=State(self.p,busy_seconds=0.01);resolve(other)
        self.s.db.execute('BEGIN IMMEDIATE')
        try:
            with self.assertRaises(RevokeFailed) as caught:other.revoke()
            self.assertTrue(caught.exception.access_may_still_be_active)
            self.assertEqual(self.s.db.execute('SELECT phase FROM pair').fetchone()[0],'approved')
            with self.assertRaises(Reject):recv(self.s)
        finally:self.s.db.execute('ROLLBACK');other.close()
        self.s.close();self.s=State(self.p)
        with self.assertRaises(Reject):resolve(self.s)
        self.s.modeled_trusted_owner_approval(FP,'s2');resolve(self.s)
        self.assertEqual(recv(self.s,message(session='s2')),'applied')
    def test_sidecar_write_failure_access_can_remain_active(self):
        from unittest.mock import patch
        other=State(self.p);resolve(other)
        try:
            with patch.object(self.s,'_write_revocation_flag',side_effect=OSError('disk full')):
                with self.assertRaises(RevokeFailed) as caught:self.s.revoke()
            self.assertTrue(caught.exception.access_may_still_be_active)
            self.assertEqual(recv(other),'applied')
        finally:other.close()
    def test_old7_migration_seeds_current_session(self):
        self.s.db.execute('DELETE FROM used_sessions');self.s.close();self.s=State(self.p)
        with self.assertRaises(Reject):self.s.modeled_trusted_owner_approval(FP,'s1')
    def test_baseexception_ack_returns_pending(self):
        def fail(_):raise KeyboardInterrupt('modeled interrupt')
        self.assertEqual(recv(self.s,ack=fail),ReceiveResult('applied',True))
        self.assertEqual(self.counts(),(1,1))
    def test_real_process_death_boundaries(self):
        for point in ('after_apply','after_seen','before_commit','after_commit'):
            p=self.tmp.name+'/'+point+'.db';initial=armed(p);initial.close()
            code='''import os,sys
from test_state import *
s=State(sys.argv[1]);resolve(s)
def die(point):
 if point==sys.argv[2]:os._exit(73)
recv(s,fault=die,ack=lambda _:open(sys.argv[3],'w').write('ack'))
'''
            ackfile=p+'.ack'
            r=subprocess.run([sys.executable,'-c',code,p,point,ackfile],cwd=os.path.dirname(__file__),timeout=10)
            self.assertEqual(r.returncode,73);self.assertFalse(os.path.exists(ackfile))
            recovered=State(p)
            try:
                self.assertEqual(recovered.db.execute('PRAGMA integrity_check').fetchone()[0],'ok')
                counts=tuple(recovered.db.execute('SELECT count(*) FROM '+t).fetchone()[0] for t in ('entries','seen'))
                self.assertEqual(counts,(1,1) if point=='after_commit' else (0,0))
                with self.assertRaises(Reject):recv(recovered)
                resolve(recovered)
                self.assertEqual(recv(recovered),'duplicate' if point=='after_commit' else 'applied')
            finally:recovered.close()

if __name__=='__main__':unittest.main()
