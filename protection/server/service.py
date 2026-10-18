"""Single-owner licensing service. No registration, client roles, hardware IDs or bypass keys."""
from __future__ import annotations
import base64, contextlib, hashlib, hmac, json, logging, secrets, sqlite3, time, uuid
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlsplit
import jwt, pyotp
from argon2 import PasswordHasher
from argon2.exceptions import VerificationError
from cryptography.fernet import Fernet
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey
from fastapi import FastAPI, Request, HTTPException
from fastapi.responses import JSONResponse

PH = PasswordHasher(time_cost=3, memory_cost=65536, parallelism=4)
AUD = 'maro-protected'
LOG = logging.getLogger('maro.security')
def now(): return int(time.time())
def digest(value): return hashlib.sha256(value.encode()).hexdigest()
def b64(b): return base64.urlsafe_b64encode(b).rstrip(b'=').decode()
def canonical(value): return json.dumps(value, separators=(',', ':'), sort_keys=True).encode()
def password_policy(value):
    if not 16 <= len(value) <= 256 or len(set(value)) < 8 or value.casefold() in {'navo'*4, 'passwordpassword'}:
        raise ValueError('Use a unique password of 16–256 characters (a password manager is recommended).')

@dataclass
class Settings:
    directory: Path
    origin: str
    device_limit: int = 2
    access_seconds: int = 300
    offline_seconds: int = 0
    refresh_seconds: int = 604800
    absolute_seconds: int = 2592000
    allow_loopback_http: bool = False
    def __post_init__(self):
        self.directory = Path(self.directory)
        u=urlsplit(self.origin)
        if u.scheme != 'https' and not (self.allow_loopback_http and u.scheme=='http' and u.hostname in ('127.0.0.1','localhost')):
            raise ValueError('HTTPS required; explicit loopback HTTP is for isolated tests only')
        if u.path not in ('','/') or u.query or u.fragment or u.username or u.password:
            raise ValueError('Origin must be an HTTPS origin without a path or credentials')
        self.origin=self.origin.rstrip('/')
        if not 1 <= self.device_limit <= 20 or not 30 <= self.access_seconds <= 300 or not 0 <= self.offline_seconds <= 120:
            raise ValueError('Unsafe licensing bounds')

SCHEMA='''
CREATE TABLE IF NOT EXISTS accounts(id TEXT PRIMARY KEY,username TEXT UNIQUE NOT NULL,password TEXT NOT NULL,totp TEXT NOT NULL,last_step INTEGER NOT NULL DEFAULT -1);
CREATE TABLE IF NOT EXISTS owners(account_id TEXT PRIMARY KEY REFERENCES accounts(id));
CREATE TABLE IF NOT EXISTS recovery(verifier TEXT PRIMARY KEY,account_id TEXT NOT NULL REFERENCES accounts(id));
CREATE TABLE IF NOT EXISTS license(id TEXT PRIMARY KEY,account_id TEXT NOT NULL REFERENCES accounts(id),activation TEXT NOT NULL,active INTEGER NOT NULL DEFAULT 0,revoked INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS devices(id TEXT PRIMARY KEY,account_id TEXT NOT NULL REFERENCES accounts(id),public_key TEXT NOT NULL,revoked INTEGER NOT NULL DEFAULT 0,UNIQUE(account_id,public_key));
CREATE TABLE IF NOT EXISTS sessions(id TEXT PRIMARY KEY,account_id TEXT NOT NULL,device_id TEXT,refresh TEXT UNIQUE,created INTEGER NOT NULL,expires INTEGER NOT NULL,absolute_exp INTEGER NOT NULL,revoked INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS spent_refresh(verifier TEXT PRIMARY KEY,session_id TEXT NOT NULL,expires INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS replay(id TEXT PRIMARY KEY,expires INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS limits(bucket TEXT PRIMARY KEY,start INTEGER NOT NULL,count INTEGER NOT NULL);
'''

class Store:
    def __init__(self,s):
        self.s=s
        if not (s.directory/'provisioned.json').exists(): raise RuntimeError('Run the interactive provision command first')
        self.meta=json.loads((s.directory/'provisioned.json').read_text())
        self.box=Fernet((s.directory/'encryption.key').read_bytes())
        self.key=serialization.load_pem_private_key((s.directory/'signing.pem').read_bytes(),password=None)
        if not isinstance(self.key,Ed25519PrivateKey):raise ValueError('Ed25519 signing key required')
        self.public=self.key.public_key()
        # Old verification keys can remain for <= access_seconds during a planned rotation.
        self.keys={self.meta['kid']:self.public}
        for kid,pem in self.meta.get('previous_keys',{}).items():
            self.keys[kid]=serialization.load_pem_public_key(pem.encode())
    @contextlib.contextmanager
    def tx(self):
        db=sqlite3.connect(self.s.directory/'license.sqlite3',timeout=10,isolation_level=None)
        db.row_factory=sqlite3.Row
        db.execute('PRAGMA foreign_keys=ON');db.execute('BEGIN IMMEDIATE')
        try:yield db
        except BaseException: db.rollback();raise
        else:db.commit()
        finally:db.close()
    def event(self,kind): LOG.info('security_event=%s',kind) # deliberately no identifiers or request contents
    def limit(self,bucket,maximum=12,seconds=300):
        with self.tx() as db:
            t=now();db.execute('DELETE FROM limits WHERE start < ?',(t-3600,))
            row=db.execute('SELECT * FROM limits WHERE bucket=?',(bucket,)).fetchone()
            if not row or row['start']+seconds<=t:
                db.execute('INSERT OR REPLACE INTO limits VALUES(?,?,1)',(bucket,t));return
            if row['count']>=maximum:raise HTTPException(429,'Try again later',headers={'Retry-After':str(seconds)})
            db.execute('UPDATE limits SET count=count+1 WHERE bucket=?',(bucket,))
    def owner(self,db,account):
        if not db.execute('SELECT 1 FROM owners WHERE account_id=?',(account,)).fetchone():
            raise HTTPException(403,'Access denied')
    def proof(self,db,request,raw,public,credential):
        try:
            key=Ed25519PublicKey.from_public_bytes(base64.urlsafe_b64decode(public+'==='))
            proof=request.headers.get('X-Maro-Proof','')
            header=jwt.get_unverified_header(proof)
            if header.get('typ')!='maro-request+jwt' or header.get('alg')!='EdDSA':raise ValueError()
            p=jwt.decode(proof,key,algorithms=['EdDSA'],options={'require':['iat','jti','method','path','body','credential'],'verify_aud':False})
            if abs(now()-p['iat'])>30 or not isinstance(p['jti'],str) or not 16<=len(p['jti'])<=128:raise ValueError()
            if p['method']!=request.method or p['path']!=request.url.path or not hmac.compare_digest(p['body'],hashlib.sha256(raw).hexdigest()) or not hmac.compare_digest(p['credential'],digest(credential)):raise ValueError()
            db.execute('DELETE FROM replay WHERE expires<?',(now(),))
            db.execute('INSERT INTO replay VALUES(?,?)',(digest(public+':'+p['jti']),now()+90))
        except (ValueError,TypeError,KeyError,jwt.PyJWTError,sqlite3.IntegrityError):raise HTTPException(401,'Invalid or replayed request')
    def authenticate(self,db,request,raw,permission=None):
        token=request.headers.get('Authorization','').removeprefix('Bearer ')
        try:
            h=jwt.get_unverified_header(token)
            if h.get('typ')!='maro-license+jwt' or h.get('alg')!='EdDSA' or h.get('kid') not in self.keys:raise ValueError()
            p=jwt.decode(token,self.keys[h['kid']],algorithms=['EdDSA'],issuer=self.s.origin,audience=AUD,options={'require':['iss','aud','sub','exp','iat','nbf','jti','sid','did','lid','permissions','cnf']})
            row=db.execute('SELECT * FROM sessions WHERE id=?',(p['sid'],)).fetchone()
            device=db.execute('SELECT * FROM devices WHERE id=?',(p['did'],)).fetchone()
            license=db.execute('SELECT * FROM license WHERE id=?',(p['lid'],)).fetchone()
            if not row or not device or not license or row['revoked'] or device['revoked'] or license['revoked'] or min(row['expires'],row['absolute_exp'])<=now():raise ValueError()
            if p['sub']!=row['account_id'] or p['sub']!=device['account_id'] or p['sub']!=license['account_id'] or row['device_id']!=device['id'] or p['cnf']!={'key_sha256':digest(device['public_key'])}:raise ValueError()
            self.owner(db,row['account_id'])
            self.proof(db,request,raw,device['public_key'],token)
            # Permissions are checked against live state as well as signed claims.
            allowed={'license:activate','devices:manage'} | ({'client:use'} if license['active'] else set())
            if permission and (permission not in p['permissions'] or permission not in allowed):raise HTTPException(403,'Permission denied')
            return row,device,license
        except (ValueError,KeyError,TypeError,jwt.PyJWTError):raise HTTPException(401,'Session expired or invalid')
    def issue(self,db,session,device,license,refresh=False):
        t=now();permissions=['license:activate','devices:manage']
        if license['active'] and not license['revoked']:permissions.append('client:use')
        claims=dict(iss=self.s.origin,aud=AUD,sub=session['account_id'],iat=t,nbf=t,exp=min(t+self.s.access_seconds,session['absolute_exp']),jti=str(uuid.uuid4()),sid=session['id'],did=device['id'],lid=license['id'],permissions=permissions,cnf={'key_sha256':digest(device['public_key'])},offline_seconds=self.s.offline_seconds)
        result={'access_token':jwt.encode(claims,self.key,algorithm='EdDSA',headers={'kid':self.meta['kid'],'typ':'maro-license+jwt'}),'expires_in':claims['exp']-t,'active':bool(license['active'])}
        if refresh:
            secret=secrets.token_urlsafe(48)
            if session['refresh']:db.execute('INSERT OR REPLACE INTO spent_refresh VALUES(?,?,?)',(session['refresh'],session['id'],session['absolute_exp']))
            db.execute('UPDATE sessions SET refresh=?,expires=? WHERE id=?',(digest(secret),min(t+self.s.refresh_seconds,session['absolute_exp']),session['id']))
            result['refresh_token']=secret
        return result

def create_app(settings):
    store=Store(settings);app=FastAPI(docs_url=None,redoc_url=None,openapi_url=None)
    app.state.store=store
    @app.middleware('http')
    async def guard(request,call_next):
        # Never trust X-Forwarded-* for identity or rate-limit keys. Proxy must preserve fixed public origin.
        if request.headers.get('host','')!=urlsplit(settings.origin).netloc:
            return JSONResponse({'detail':'Invalid host'},status_code=400)
        try:length=int(request.headers.get('content-length','0') or 0)
        except ValueError:return JSONResponse({'detail':'Invalid request length'},status_code=400)
        if length<0 or length>16384:return JSONResponse({'detail':'Request too large'},status_code=413)
        raw=await request.body()
        if len(raw)>16384:return JSONResponse({'detail':'Request too large'},status_code=413)
        try:store.limit('ip:'+digest(request.client.host if request.client else 'unknown'),180,60)
        except HTTPException as e:return JSONResponse({'detail':e.detail},e.status_code,headers=e.headers)
        response=await call_next(request)
        response.headers.update({'Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Content-Security-Policy':"default-src 'none'; frame-ancestors 'none'",'Strict-Transport-Security':'max-age=31536000'})
        return response
    async def body(request):
        raw=await request.body()
        try:
            data=json.loads(raw)
            if not isinstance(data,dict) or any(not isinstance(v,str) or len(v)>512 for v in data.values()):raise ValueError()
            return raw,data
        except (ValueError,UnicodeError):raise HTTPException(400,'Invalid request')
    @app.post('/v1/login')
    async def login(request:Request):
        raw,p=await body(request);store.limit('owner-login',8,300)
        # Exact schema rejects forged roles/account IDs rather than ignoring privileged fields.
        if set(p)!={'username','password','otp','public_key'} or any(not isinstance(v,str) or len(v)>256 for v in p.values()):raise HTTPException(400,'Invalid request fields')
        with store.tx() as db:
            account=db.execute('SELECT * FROM accounts WHERE username=?',(p['username'],)).fetchone()
            try:
                if not account:raise ValueError()
                store.owner(db,account['id']);PH.verify(account['password'],p['password'])
                secret=store.box.decrypt(account['totp'].encode()).decode();step=now()//30
                matched=next((n for n in (step,step-1,step+1) if n>account['last_step'] and hmac.compare_digest(pyotp.TOTP(secret).at(n*30),str(p['otp']))),None)
                if matched is None:raise ValueError()
                store.proof(db,request,raw,p['public_key'],'')
            except (ValueError,TypeError,VerificationError):store.event('login_denied');raise HTTPException(401,'Invalid credentials or MFA')
            # All actions in one transaction: OTP and proof can never succeed twice concurrently.
            db.execute('UPDATE accounts SET last_step=? WHERE id=?',(matched,account['id']))
            dev=db.execute('SELECT * FROM devices WHERE account_id=? AND public_key=?',(account['id'],p['public_key'])).fetchone()
            if dev and dev['revoked']:raise HTTPException(403,'Device revoked; use an authenticated device reset')
            if not dev:
                count=db.execute('SELECT count(*) FROM devices WHERE account_id=? AND revoked=0',(account['id'],)).fetchone()[0]
                if count>=settings.device_limit:raise HTTPException(403,'Device limit reached')
                did=str(uuid.uuid4());db.execute('INSERT INTO devices VALUES(?,?,?,0)',(did,account['id'],p['public_key']));dev=db.execute('SELECT * FROM devices WHERE id=?',(did,)).fetchone()
            lid=db.execute('SELECT * FROM license WHERE account_id=?',(account['id'],)).fetchone()
            if not lid or lid['revoked']:raise HTTPException(403,'License revoked')
            sid=str(uuid.uuid4());t=now();db.execute('INSERT INTO sessions VALUES(?,?,?,?,?,?,?,0)',(sid,account['id'],dev['id'],None,t,t+settings.refresh_seconds,t+settings.absolute_seconds))
            row=db.execute('SELECT * FROM sessions WHERE id=?',(sid,)).fetchone();result=store.issue(db,row,dev,lid,True)
        store.event('login_ok');return result
    @app.post('/v1/activate')
    async def activate(request:Request):
        raw,p=await body(request);store.limit('activation',8,300)
        with store.tx() as db:
            row,dev,lic=store.authenticate(db,request,raw,'license:activate')
            if set(p)!={'activation_key'} or lic['active'] or not hmac.compare_digest(lic['activation'],digest(str(p['activation_key']))):raise HTTPException(403,'Invalid or consumed activation key')
            db.execute('UPDATE license SET active=1,activation=? WHERE id=?',(digest(secrets.token_urlsafe(48)),lic['id']))
            result=store.issue(db,row,dev,db.execute('SELECT * FROM license WHERE id=?',(lic['id'],)).fetchone())
        store.event('activated');return result
    @app.post('/v1/lease')
    async def lease(request:Request):
        raw,p=await body(request)
        with store.tx() as db:
            row,dev,lic=store.authenticate(db,request,raw,'client:use');return store.issue(db,row,dev,lic)
    @app.post('/v1/refresh')
    async def refresh(request:Request):
        raw,p=await body(request);store.limit('refresh',90,60)
        secret=str(p.get('refresh_token',''));verifier=digest(secret);reused=False;result=None
        with store.tx() as db:
            db.execute('DELETE FROM spent_refresh WHERE expires<?',(now(),))
            row=db.execute('SELECT * FROM sessions WHERE refresh=?',(verifier,)).fetchone()
            if not row:
                spent=db.execute('SELECT * FROM spent_refresh WHERE verifier=?',(verifier,)).fetchone()
                if spent:
                    row=db.execute('SELECT * FROM sessions WHERE id=?',(spent['session_id'],)).fetchone()
                    dev=db.execute('SELECT * FROM devices WHERE id=?',(row['device_id'],)).fetchone()
                    store.proof(db,request,raw,dev['public_key'],secret)
                    db.execute('UPDATE sessions SET revoked=1 WHERE id=?',(row['id'],));reused=True
                else:raise HTTPException(401,'Invalid session credential')
            else:
                dev=db.execute('SELECT * FROM devices WHERE id=?',(row['device_id'],)).fetchone();lic=db.execute('SELECT * FROM license WHERE account_id=?',(row['account_id'],)).fetchone()
                if row['revoked'] or dev['revoked'] or lic['revoked'] or min(row['expires'],row['absolute_exp'])<=now():raise HTTPException(401,'Session expired or revoked')
                store.owner(db,row['account_id']);store.proof(db,request,raw,dev['public_key'],secret);result=store.issue(db,row,dev,lic,True)
        if reused:store.event('refresh_reuse_revoked');raise HTTPException(401,'Session revoked after credential reuse')
        return result
    @app.post('/v1/logout')
    async def logout(request:Request):
        raw,p=await body(request)
        with store.tx() as db:
            row,_,_=store.authenticate(db,request,raw);db.execute('UPDATE sessions SET revoked=1 WHERE id=?',(row['id'],))
        return {'ok':True}
    @app.post('/v1/devices')
    async def devices(request:Request):
        raw,p=await body(request)
        with store.tx() as db:
            row,_,_=store.authenticate(db,request,raw,'devices:manage')
            return {'devices':[dict(r) for r in db.execute('SELECT id,revoked FROM devices WHERE account_id=?',(row['account_id'],))], 'sessions':[dict(r) for r in db.execute('SELECT id,device_id,created,expires,revoked FROM sessions WHERE account_id=?',(row['account_id'],))]}
    @app.post('/v1/devices/reset')
    async def reset(request:Request):
        raw,p=await body(request)
        with store.tx() as db:
            row,current,_=store.authenticate(db,request,raw,'devices:manage')
            # Reauthenticate with MFA for lost-device revocation; no account ID from client.
            a=db.execute('SELECT * FROM accounts WHERE id=?',(row['account_id'],)).fetchone()
            try:PH.verify(a['password'],str(p.get('password','')))
            except VerificationError:raise HTTPException(401,'Invalid credentials')
            step=now()//30;secret=store.box.decrypt(a['totp'].encode()).decode()
            if step<=a['last_step'] or not pyotp.TOTP(secret).verify(str(p.get('otp','')),valid_window=0):raise HTTPException(401,'Invalid or used MFA')
            db.execute('UPDATE accounts SET last_step=? WHERE id=?',(step,a['id']))
            did=p.get('device_id')
            if did:
                db.execute('UPDATE devices SET revoked=1 WHERE id=? AND account_id=?',(did,a['id']))
                db.execute('UPDATE sessions SET revoked=1 WHERE device_id=? AND account_id=?',(did,a['id']))
            else:
                db.execute('UPDATE devices SET revoked=1 WHERE account_id=? AND id!=?',(a['id'],current['id']))
                db.execute('UPDATE sessions SET revoked=1 WHERE account_id=? AND device_id!=?',(a['id'],current['id']))
        store.event('devices_revoked');return {'ok':True}
    @app.post('/v1/recover')
    async def recover(request:Request):
        _,p=await body(request);store.limit('recovery',5,900)
        try:
            password_policy(p['new_password'])
            if not isinstance(p['new_totp'],str) or len(p['new_totp'])!=32 or len(base64.b32decode(p['new_totp']))!=20:raise ValueError()
            new_totp=pyotp.TOTP(p['new_totp']);valid=new_totp.verify(str(p['new_otp']),valid_window=0)
        except (ValueError,KeyError,TypeError):raise HTTPException(400,'Invalid recovery fields')
        if not valid:raise HTTPException(401,'Verify your new authenticator first')
        with store.tx() as db:
            a=db.execute('SELECT * FROM accounts WHERE username=?',(p.get('username'),)).fetchone()
            try:
                if not a:raise ValueError()
                store.owner(db,a['id']);PH.verify(a['password'],p.get('password',''))
                r=db.execute('SELECT 1 FROM recovery WHERE verifier=? AND account_id=?',(digest(str(p.get('recovery_code',''))),a['id'])).fetchone()
                if not r:raise ValueError()
            except (ValueError,VerificationError):raise HTTPException(401,'Invalid recovery credentials')
            db.execute('DELETE FROM recovery WHERE verifier=?',(digest(p['recovery_code']),))
            db.execute('UPDATE accounts SET password=?,totp=?,last_step=? WHERE id=?',(PH.hash(p['new_password']),store.box.encrypt(p['new_totp'].encode()).decode(),now()//30,a['id']))
            db.execute('UPDATE sessions SET revoked=1 WHERE account_id=?',(a['id'],));db.execute('UPDATE devices SET revoked=1 WHERE account_id=?',(a['id'],))
        store.event('account_recovered');return {'ok':True,'message':'All devices revoked. Create a new device key, then log in with the new authenticator.'}
    return app
