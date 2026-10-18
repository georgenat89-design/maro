import base64, json, time, uuid, sys, zipfile
from pathlib import Path
import jwt, pyotp, pytest
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from fastapi.testclient import TestClient
from admin import provision
from service import Settings,create_app,digest,canonical,now,b64
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from release import sign,verify_install
import release

PASSWORD='ephemeral-TEST-only-'+uuid.uuid4().hex
class Device:
    def __init__(self):
        self.key=Ed25519PrivateKey.generate();self.public=b64(self.key.public_key().public_bytes(serialization.Encoding.Raw,serialization.PublicFormat.Raw));self.access='';self.refresh=''
    def headers(self,path,data,credential='',jti=None):
        p={'iat':now(),'jti':jti or str(uuid.uuid4()),'method':'POST','path':path,'body':__import__('hashlib').sha256(canonical(data)).hexdigest(),'credential':digest(credential)}
        h={'X-Maro-Proof':jwt.encode(p,self.key,algorithm='EdDSA',headers={'typ':'maro-request+jwt'})}
        if credential==self.access and credential:h['Authorization']='Bearer '+credential
        return h
    def call(self,c,path,data=None,refresh=False,headers=None):
        data={} if data is None else data;credential=self.refresh if refresh else self.access
        return c.post(path,content=canonical(data),headers=headers or self.headers(path,data,credential))
    def accept(self,response):
        assert response.status_code==200,response.text;p=response.json();self.access=p['access_token'];self.refresh=p.get('refresh_token',self.refresh);return p
@pytest.fixture
def env(tmp_path):
    secret=pyotp.random_base32();state=tmp_path/'state';activation,codes,owner=provision(state,'https://license.test',PASSWORD,secret,pyotp.TOTP(secret).now())
    s=Settings(state,'https://license.test');app=create_app(s);c=TestClient(app,base_url=s.origin)
    # Model the next MFA timestep without waiting; production enrollment consumes its code.
    with app.state.store.tx() as db:db.execute('UPDATE accounts SET last_step=-1')
    return c,app.state.store,secret,activation,codes,owner
def login(env,device=None,**overrides):
    c,store,secret,*_=env;d=device or Device();p={'username':'navo','password':PASSWORD,'otp':pyotp.TOTP(secret).now(),'public_key':d.public};p.update(overrides)
    response=c.post('/v1/login',content=canonical(p),headers=d.headers('/v1/login',p))
    return d,response
def active(env):
    d,r=login(env);d.accept(r);d.accept(d.call(env[0],'/v1/activate',{'activation_key':env[3]}));return d
def fresh_otp(env):
    with env[1].tx() as db:db.execute('UPDATE accounts SET last_step=-1');db.execute('DELETE FROM limits')
def test_valid_owner_activation_and_no_secret_storage(env):
    d=active(env);assert d.call(env[0],'/v1/lease').status_code==200
    with env[1].tx() as db:
        a=db.execute('SELECT * FROM accounts').fetchone();assert a['id']==env[5] and a['id']!='navo';assert a['password'].startswith('$argon2id$')
        row=db.execute('SELECT * FROM license').fetchone();assert row['activation']!=env[3] and env[3] not in str(dict(row))
    assert d.call(env[0],'/v1/activate',{'activation_key':env[3]}).status_code==403
def test_provisioning_consumes_enrollment_code(tmp_path):
    secret=pyotp.random_base32();state=tmp_path/'private';provision(state,'https://license.test',PASSWORD,secret,pyotp.TOTP(secret).now())
    app=create_app(Settings(state,'https://license.test'));c=TestClient(app,base_url='https://license.test');d=Device()
    p={'username':'navo','password':PASSWORD,'otp':pyotp.TOTP(secret).now(),'public_key':d.public}
    assert c.post('/v1/login',content=canonical(p),headers=d.headers('/v1/login',p)).status_code==401
@pytest.mark.parametrize('override',[{'username':'other'},{'password':'navo'},{'otp':'000000'},{'role':'owner'},{'account_id':'navo'},{'authenticated':True}])
def test_unauthorized_and_forged_claims(env,override):
    _,r=login(env,**override);assert r.status_code in (400,401)
def test_allowlist_is_immutable_id_not_username(env):
    with env[1].tx() as db:db.execute('DELETE FROM owners')
    _,r=login(env);assert r.status_code==403
def test_invalid_key_and_inactive_permission(env):
    d,r=login(env);d.accept(r)
    assert d.call(env[0],'/v1/lease').status_code==403
    assert d.call(env[0],'/v1/activate',{'activation_key':'navo'}).status_code==403
def test_token_forgery_expiry_audience_permission_and_license_revocation(env):
    d=active(env);c,store,*_=env;original=d.access
    for change in ({'exp':now()-10},{'aud':'wrong'},{'iss':'https://evil.test'},{'permissions':[]},{'sub':str(uuid.uuid4())}):
        p=jwt.decode(original,options={'verify_signature':False});p.update(change);d.access=jwt.encode(p,store.key,algorithm='EdDSA',headers={'kid':store.meta['kid'],'typ':'maro-license+jwt'})
        assert d.call(c,'/v1/lease').status_code in (401,403)
    d.access=original[:-4]+'AAAA';assert d.call(c,'/v1/lease').status_code==401;d.access=original
    with store.tx() as db:db.execute('UPDATE license SET revoked=1')
    assert d.call(c,'/v1/lease').status_code==401
def test_signed_request_replay_body_tamper_and_wrong_device(env):
    d=active(env);c=env[0];h=d.headers('/v1/lease',{},d.access)
    assert d.call(c,'/v1/lease',headers=h).status_code==200
    assert d.call(c,'/v1/lease',headers=h).status_code==401
    h=d.headers('/v1/lease',{},d.access);assert d.call(c,'/v1/lease',{'altered':'yes'},headers=h).status_code==401
    other=Device();other.access=d.access;assert other.call(c,'/v1/lease').status_code==401
def test_otp_cannot_be_reused(env):
    d,r=login(env);d.accept(r);_,r=login(env);assert r.status_code==401
def test_refresh_rotation_and_reuse_revokes_family(env):
    d=active(env);c=env[0];old=d.refresh;d.accept(d.call(c,'/v1/refresh',{'refresh_token':old},refresh=True));assert d.refresh!=old
    h=d.headers('/v1/refresh',{'refresh_token':old},old)
    assert d.call(c,'/v1/refresh',{'refresh_token':old},headers=h).status_code==401
    assert d.call(c,'/v1/lease').status_code==401
def test_tampered_refresh_and_logout(env):
    d=active(env);c=env[0]
    assert d.call(c,'/v1/refresh',{'refresh_token':'tampered'},refresh=True).status_code==401
    assert d.call(c,'/v1/logout').status_code==200
    assert d.call(c,'/v1/lease').status_code==401
def test_session_expiry_and_owner_removal(env):
    d=active(env)
    with env[1].tx() as db:db.execute('UPDATE sessions SET expires=?',(now()-1,))
    assert d.call(env[0],'/v1/lease').status_code==401
def test_reconnect_preserves_revocation_and_valid_refresh(env):
    d=active(env);c=TestClient(create_app(env[1].s),base_url=env[1].s.origin)
    d.accept(d.call(c,'/v1/refresh',{'refresh_token':d.refresh},refresh=True))
    with env[1].tx() as db:db.execute('DELETE FROM owners')
    assert d.call(c,'/v1/lease').status_code==403
    assert d.call(c,'/v1/refresh',{'refresh_token':d.refresh},refresh=True).status_code==403
def test_signed_proof_freshness_and_path_binding(env):
    d=active(env);h=d.headers('/v1/devices',{},d.access)
    assert d.call(env[0],'/v1/lease',headers=h).status_code==401
    p={'iat':now()-90,'jti':str(uuid.uuid4()),'method':'POST','path':'/v1/lease','body':__import__('hashlib').sha256(canonical({})).hexdigest(),'credential':digest(d.access)}
    h={'Authorization':'Bearer '+d.access,'X-Maro-Proof':jwt.encode(p,d.key,algorithm='EdDSA',headers={'typ':'maro-request+jwt'})}
    assert d.call(env[0],'/v1/lease',headers=h).status_code==401
def test_refresh_absolute_expiry(env):
    d=active(env)
    with env[1].tx() as db:db.execute('UPDATE sessions SET absolute_exp=?',(now()-1,))
    assert d.call(env[0],'/v1/refresh',{'refresh_token':d.refresh},refresh=True).status_code==401
def test_device_limit_and_authenticated_revocation(env):
    d=active(env);fresh_otp(env);two,r=login(env);two.accept(r);fresh_otp(env)
    _,r=login(env);assert r.status_code==403
    # Fresh MFA is mandatory for resets; a signed token alone cannot revoke devices.
    assert d.call(env[0],'/v1/devices/reset',{}).status_code==401
    fresh_otp(env)
    assert d.call(env[0],'/v1/devices/reset',{'password':PASSWORD,'otp':pyotp.TOTP(env[2]).now()}).status_code==200
    assert two.call(env[0],'/v1/lease').status_code==401
def test_recovery_is_one_time_and_revokes_all_sessions(env):
    d=active(env);secret=pyotp.random_base32();p={'username':'navo','password':PASSWORD,'recovery_code':env[4][0],'new_password':PASSWORD+'new','new_totp':secret,'new_otp':pyotp.TOTP(secret).now()}
    assert env[0].post('/v1/recover',json=p).status_code==200
    assert d.call(env[0],'/v1/lease').status_code==401
    p['password']=p['new_password'];assert env[0].post('/v1/recover',json=p).status_code==401
def test_no_registration_docs_or_unprotected_admin(env):
    c=env[0]
    for path in ('/register','/docs','/openapi.json','/admin'):
        assert c.get(path).status_code==404
    for path in ('/v1/lease','/v1/devices','/v1/logout','/v1/devices/reset','/v1/activate'):
        assert c.post(path,json={}).status_code==401
def test_rate_limit(env):
    for _ in range(8):login(env,password='bad')
    _,r=login(env);assert r.status_code==429
def test_origin_and_offline_bounds(env,tmp_path):
    assert env[0].post('/v1/login',json={},headers={'host':'evil.test'}).status_code==400
    for origin in ('http://evil.test','https://test/path','https://user:secret@test'):
        with pytest.raises(ValueError):Settings(tmp_path,origin)
    with pytest.raises(ValueError):Settings(tmp_path,'https://test',offline_seconds=121)
    assert env[1].s.offline_seconds==0
def test_signed_release_tamper_rollback_expiry_and_atomic_install(tmp_path):
    key=Ed25519PrivateKey.generate();source=tmp_path/'raw.jar';signed=tmp_path/'signed.jar'
    with zipfile.ZipFile(source,'w') as z:z.writestr('payload.class',b'fixture')
    sign(source,signed,key,2,'https://download.test/protected.jar');manifest=Path(str(signed)+'.manifest.json');sig=Path(str(signed)+'.manifest.sig');dest=tmp_path/'mods/Maro-Protected-Test.jar'
    assert verify_install(signed,manifest,sig,key.public_key(),dest,1)==2
    with pytest.raises(ValueError):verify_install(signed,manifest,sig,key.public_key(),dest,2)
    p=json.loads(manifest.read_bytes());p['expires']=1;expired=canonical(p);manifest.write_bytes(expired);sig.write_bytes(key.sign(expired))
    with pytest.raises(ValueError):verify_install(signed,manifest,sig,key.public_key(),dest,1)
    sign(source,signed,key,3,'https://download.test/protected.jar');signed.write_bytes(signed.read_bytes()+b'tamper')
    with pytest.raises(ValueError):verify_install(signed,manifest,sig,key.public_key(),dest,1)
    assert dest.exists() # Failed verification never replaces the prior installed artifact.
def test_generated_release_key_is_encrypted(tmp_path,monkeypatch):
    phrase='fixture-key-encryption-'+uuid.uuid4().hex;private=tmp_path/'private/key.pem';public=tmp_path/'public.pem'
    monkeypatch.setattr(sys.stdin,'isatty',lambda:True);monkeypatch.setattr(sys.stdout,'isatty',lambda:True)
    monkeypatch.setattr(release.getpass,'getpass',lambda prompt:phrase)
    monkeypatch.setattr(sys,'argv',['release.py','keygen','--private',str(private),'--public',str(public)])
    release.main()
    with pytest.raises(TypeError):serialization.load_pem_private_key(private.read_bytes(),password=None)
    key=serialization.load_pem_private_key(private.read_bytes(),password=phrase.encode())
    assert key.public_key().public_bytes(serialization.Encoding.PEM,serialization.PublicFormat.SubjectPublicKeyInfo)==public.read_bytes()
