"""Trusted-console provisioning/rotation only. Run outside CI logging or terminal recording."""
import argparse, getpass, json, os, secrets, sqlite3, subprocess, sys, uuid
from pathlib import Path
import pyotp
from cryptography.fernet import Fernet
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from service import Settings, Store, SCHEMA, PH, password_policy, digest, now

def private_dir(path):
    path.mkdir(parents=True,exist_ok=True)
    if os.name=='nt':
        who=subprocess.check_output(['whoami'],text=True).strip()
        subprocess.run(['icacls',str(path),'/inheritance:r','/grant:r',who+':(OI)(CI)F'],check=True,stdout=subprocess.DEVNULL)
    else:path.chmod(0o700)
def write_private(path,data):
    with path.open('xb') as f:f.write(data)
    if os.name!='nt':path.chmod(0o600)
def provision(directory,origin,password,totp,verified_code):
    """Internal helper also exercised with isolated ephemeral credentials by tests."""
    password_policy(password);s=Settings(directory,origin)
    if len(totp)!=32 or not pyotp.TOTP(totp).verify(verified_code,valid_window=0):raise ValueError('Authenticator verification failed')
    if directory.exists() and any(directory.iterdir()):raise ValueError('Provision into a new empty private directory')
    private_dir(directory)
    key=Ed25519PrivateKey.generate();boxkey=Fernet.generate_key();box=Fernet(boxkey)
    uid=str(uuid.uuid4());lid=str(uuid.uuid4());activation=secrets.token_urlsafe(48);codes=[secrets.token_urlsafe(18) for _ in range(10)]
    write_private(directory/'encryption.key',boxkey)
    write_private(directory/'signing.pem',key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
    db=sqlite3.connect(directory/'license.sqlite3')
    try:
        db.executescript(SCHEMA)
        db.execute('INSERT INTO accounts VALUES(?,?,?,?,?)',(uid,'navo',PH.hash(password),box.encrypt(totp.encode()).decode(),now()//30))
        db.execute('INSERT INTO owners VALUES(?)',(uid,))
        db.executemany('INSERT INTO recovery VALUES(?,?)',[(digest(c),uid) for c in codes])
        db.execute('INSERT INTO license VALUES(?,?,?,0,0)',(lid,uid,digest(activation)));db.commit()
    finally:db.close()
    meta={'kid':str(uuid.uuid4()),'owner_id':uid,'origin':origin,'previous_keys':{}}
    write_private(directory/'provisioned.json',json.dumps(meta,indent=2).encode())
    return activation,codes,uid
def export(s,destination):
    store=Store(s)
    pem=store.public.public_bytes(serialization.Encoding.PEM,serialization.PublicFormat.SubjectPublicKeyInfo).decode()
    Path(destination).parent.mkdir(parents=True,exist_ok=True)
    Path(destination).write_text(json.dumps({'origin':s.origin,'audience':'maro-protected','offline_max_seconds':s.offline_seconds,'keys':{store.meta['kid']:pem}},indent=2))
def main():
    parser=argparse.ArgumentParser();parser.add_argument('command',choices=['provision','export-public','rotate-signing','rotate-activation','revoke-all','disable-license']);parser.add_argument('--state',type=Path,required=True);parser.add_argument('--origin',required=True);parser.add_argument('--output',type=Path)
    args=parser.parse_args();s=Settings(args.state,args.origin)
    if args.command=='provision':
        if not sys.stdin.isatty() or not sys.stdout.isatty():raise SystemExit('Provisioning requires a private interactive terminal; no redirected secrets')
        password=getpass.getpass('New unique navo password (16+ characters): ')
        if password!=getpass.getpass('Confirm password: '):raise SystemExit('Passwords do not match')
        secret=pyotp.random_base32();print('Add this MFA secret to your authenticator (shown once):',secret)
        code=getpass.getpass('Authenticator code to verify enrollment: ')
        activation,codes,uid=provision(args.state,args.origin,password,secret,code)
        print('Verified immutable owner ID:',uid)
        print('Owner activation key (shown once; store in password manager):',activation)
        print('Single-use recovery codes (shown once; store offline):\n'+'\n'.join(codes))
        export(s,args.output or args.state/'client-public.json')
        return
    store=Store(s)
    if args.command=='export-public':
        if not args.output:raise SystemExit('--output required')
        export(s,args.output);return
    if args.command=='rotate-signing':
        key=Ed25519PrivateKey.generate()
        # Emergency fail-closed rotation: all prior sessions are invalidated, no implicit trust in old keys.
        with store.tx() as db:db.execute('UPDATE sessions SET revoked=1')
        (args.state/'signing.pem').write_bytes(key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
        store.meta['kid']=str(uuid.uuid4());store.meta['previous_keys']={}
        (args.state/'provisioned.json').write_text(json.dumps(store.meta,indent=2));print('Rotated. Restart service; rebuild clients with the new public key via the trusted release channel.')
    elif args.command=='rotate-activation':
        if not sys.stdin.isatty() or not sys.stdout.isatty():raise SystemExit('Interactive private terminal required')
        key=secrets.token_urlsafe(48)
        with store.tx() as db:
            db.execute('UPDATE license SET activation=?,active=0,revoked=0',(digest(key),));db.execute('UPDATE sessions SET revoked=1')
        print('New activation key (shown once):',key)
    else:
        with store.tx() as db:
            db.execute('UPDATE sessions SET revoked=1');db.execute('UPDATE devices SET revoked=1')
            if args.command=='disable-license':db.execute('UPDATE license SET revoked=1')
        print('Revoked. Restoring access requires a fresh device key and owner authentication.')
if __name__=='__main__':main()
