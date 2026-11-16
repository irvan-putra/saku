"""Synthetic durable state model. NOT a transport/owner-auth implementation.
No cryptography, secret storage, networking or production identities.
Callers supply MODELED trusted approval and MODELED authenticated core facts.
The executable tests establish transaction semantics only.
"""
from dataclasses import dataclass
import hashlib
import json
import os
import re
import sqlite3

class Reject(Exception): pass
class RevokeFailed(Exception):
    def __init__(self,message):
        super().__init__(message)
        self.access_may_still_be_active=True


@dataclass(frozen=True)
class ReceiveResult:
    outcome: str
    ack_pending: bool



def fingerprint(value):
    if not isinstance(value, str): raise Reject('fingerprint type')
    value = ''.join(value.split()).upper()
    if re.fullmatch(r'[0-9A-F]{40}', value) is None: raise Reject('fingerprint')
    return value

class State:
    def __init__(self, path, busy_seconds=0.1):
        self.flag_path=str(path)+".revoked"
        self.db = sqlite3.connect(path, isolation_level=None, timeout=busy_seconds)
        mode=self.db.execute('PRAGMA journal_mode=WAL').fetchone()[0]
        if mode.lower()!='wal': raise RuntimeError('WAL unavailable')
        self.db.execute('PRAGMA synchronous=FULL')
        if self.db.execute('PRAGMA synchronous').fetchone()[0]!=2: raise RuntimeError('FULL unavailable')
        self.db.executescript('''
        CREATE TABLE IF NOT EXISTS used_sessions(session TEXT PRIMARY KEY);
        CREATE TABLE IF NOT EXISTS pair(slot INTEGER PRIMARY KEY CHECK(slot=1), fingerprint TEXT NOT NULL, session TEXT NOT NULL, phase TEXT NOT NULL, epoch INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS seen(session TEXT, mid TEXT, envelope TEXT, digest TEXT NOT NULL, PRIMARY KEY(session,mid), UNIQUE(session,envelope));
        CREATE TABLE IF NOT EXISTS entries(id TEXT PRIMARY KEY, version INTEGER NOT NULL, value TEXT NOT NULL, origin TEXT NOT NULL);
        ''')
        self.db.execute('INSERT OR IGNORE INTO used_sessions SELECT session FROM pair')
        # Core-local numeric IDs are deliberately never trusted across startup.
        self.mapping = None

    def close(self): self.db.close()

    def _check_revocation_flag(self):
        try:os.stat(self.flag_path)
        except FileNotFoundError:return
        except OSError:
            self.mapping=None
            raise Reject('revocation flag status unavailable')
        self.mapping=None
        raise Reject('revocation flag set')

    def _write_revocation_flag(self):
        fd=os.open(self.flag_path,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
        try:
            os.write(fd,b'REVOKE REQUESTED\n');os.fsync(fd)
        finally:os.close(fd)
        parent=os.open(os.path.dirname(os.path.abspath(self.flag_path)),os.O_RDONLY)
        try:os.fsync(parent)
        finally:os.close(parent)


    def modeled_trusted_owner_approval(self, fp, session):
        """TEST ADAPTER ONLY. Production owner-channel provenance is absent.
        Calling this method does not authenticate an owner or grant authority.
        A new approval invalidates any current runtime mapping immediately.
        """
        fp = fingerprint(fp)
        if not isinstance(session,str) or not 1 <= len(session) <= 128: raise Reject('session')
        self.mapping = None
        self.db.execute('BEGIN IMMEDIATE')
        try:
            old = self.db.execute('SELECT fingerprint,session,epoch FROM pair WHERE slot=1').fetchone()
            if self.db.execute('SELECT 1 FROM used_sessions WHERE session=?',(session,)).fetchone():
                raise Reject('session was already used')
            self.db.execute('INSERT INTO used_sessions VALUES(?)',(session,))
            self.db.execute('INSERT OR REPLACE INTO pair VALUES(1,?,?,?,?)',(fp,session,'pending',old[2]+1 if old else 1))
            self.db.execute('COMMIT')
        except BaseException:
            self.db.execute('ROLLBACK'); raise
        try:os.unlink(self.flag_path)
        except FileNotFoundError:pass

    def modeled_verified_core_resolution(self, fp, contact_id, chat_id, *, key_available, key_contact, one_to_one, interleave=lambda:None):
        """TEST adapter. Compare epoch again under writer lock. The interleave
        hook models a revocation committed between initial observation and lock.
        No real core facts or owner authorization are established here."""
        self.mapping=None
        self._check_revocation_flag()
        expected=self.db.execute('SELECT fingerprint,session,phase,epoch FROM pair WHERE slot=1').fetchone()
        if not expected or expected[2]=='revoked' or fingerprint(fp)!=expected[0]: raise Reject('unapproved key')
        if not all(x is True for x in (key_available,key_contact,one_to_one)): raise Reject('core unresolved')
        if type(contact_id) is not int or contact_id<=0 or type(chat_id) is not int or chat_id<=0: raise Reject('ids')
        interleave()
        self.db.execute('BEGIN IMMEDIATE')
        try:
            row=self.db.execute('SELECT fingerprint,session,phase,epoch FROM pair WHERE slot=1').fetchone()
            self._check_revocation_flag()
            if row!=expected or row[2]=='revoked': raise Reject('approval changed during resolution')
            updated=self.db.execute("UPDATE pair SET phase='approved' WHERE slot=1 AND epoch=? AND phase!='revoked'",(row[3],))
            if updated.rowcount!=1: raise Reject('resolution CAS failed')
            self.db.execute('COMMIT')
        except BaseException:
            self.db.execute('ROLLBACK');raise
        self.mapping=(row[1],contact_id,chat_id,row[0],row[3])

    def revoke(self):
        self.mapping=None
        try:self._write_revocation_flag()
        except BaseException as exc:
            raise RevokeFailed('sidecar write failed; other processes may still have access') from exc
        try:
            self.db.execute('BEGIN IMMEDIATE')
            try:
                self.db.execute("UPDATE pair SET phase='revoked',epoch=epoch+1 WHERE slot=1")
                self.db.execute('COMMIT')
            except BaseException:
                self.db.execute('ROLLBACK');raise
        except BaseException as exc:
            raise RevokeFailed('DB revoke failed; durable pair may remain approved; sidecar blocks cooperating paths only') from exc

    def receive(self, raw, *, event, mid, contact_id, chat_id, encrypted_signed, key_contact, one_to_one, authenticated_fingerprint, ack, fault=lambda _: None):
        """Atomic apply+dedupe. Authentication booleans are synthetic inputs,
        not evidence of real cryptographic validation. ack called after COMMIT.
        Duplicate authenticated deliveries re-ack after reading durable seen.
        """
        self._check_revocation_flag()
        if self.mapping is None: raise Reject('startup unresolved or not approved')
        session,cid,chid,pinned_fp,epoch=self.mapping
        if fingerprint(authenticated_fingerprint)!=pinned_fp:
            self.mapping=None
            raise Reject('per-message fingerprint changed')
        if event!='IncomingMsg' or (contact_id,chat_id)!=(cid,chid): raise Reject('source')
        if not all(x is True for x in (encrypted_signed,key_contact,one_to_one)): raise Reject('authentication')
        if not isinstance(mid,str) or not 1<=len(mid)<=998 or '\n' in mid or '\r' in mid: raise Reject('mid')
        if type(raw) is not bytes or len(raw)>65536: raise Reject('outer bound')
        try: msg=json.loads(raw)
        except (ValueError,UnicodeError,RecursionError): raise Reject('json')
        if type(msg) is not dict or set(msg)!={'session','envelope','entry','version','value'}: raise Reject('schema')
        for name in ('session','envelope','entry'):
            if not isinstance(msg[name],str) or not 1<=len(msg[name])<=128: raise Reject('field')
        if msg['session']!=session: raise Reject('session')
        if type(msg['version']) is not int or not 1<=msg['version']<=2147483647: raise Reject('version')
        if not isinstance(msg['value'],str) or not msg['value'].startswith('SYNTHETIC-') or len(msg['value'])>4096: raise Reject('synthetic only')
        # Serialize only fixed fields so duplicate comparison is canonical.
        digest=hashlib.sha256(json.dumps(msg,sort_keys=True,separators=(',',':')).encode()).hexdigest()
        self.db.execute('BEGIN IMMEDIATE')
        try:
            row=self.db.execute('SELECT fingerprint,session,phase,epoch FROM pair WHERE slot=1').fetchone()
            self._check_revocation_flag()
            if not row or row[1]!=session or row[2]!='approved' or row[0]!=pinned_fp or row[3]!=epoch: raise Reject('approval changed')
            duplicate=self.db.execute('SELECT envelope,digest FROM seen WHERE session=? AND mid=?',(session,mid)).fetchone()
            if duplicate:
                if duplicate!=(msg['envelope'],digest): raise Reject('mid conflict')
                result='duplicate'
            elif self.db.execute('SELECT 1 FROM seen WHERE session=? AND envelope=?',(session,msg['envelope'])).fetchone():
                raise Reject('envelope replay with different mid')
            else:
                old=self.db.execute('SELECT version,value,origin FROM entries WHERE id=?',(msg['entry'],)).fetchone()
                if old and old[2]!='agent': raise Reject('phone entry protected')
                if old and msg['version']<old[0]: raise Reject('rollback')
                if old and msg['version']==old[0] and msg['value']!=old[1]: raise Reject('version conflict')
                self.db.execute("INSERT OR REPLACE INTO entries VALUES(?,?,?,'agent')",(msg['entry'],msg['version'],msg['value']))
                fault('after_apply')
                self.db.execute('INSERT INTO seen VALUES(?,?,?,?)',(session,mid,msg['envelope'],digest))
                fault('after_seen')
                result='applied'
            fault('before_commit')
            self.db.execute('COMMIT')
        except BaseException:
            self.db.execute('ROLLBACK'); raise
        fault('after_commit')
        try:
            ack(msg['envelope'])
        except BaseException:
            return ReceiveResult(result,True)
        return ReceiveResult(result,False)
