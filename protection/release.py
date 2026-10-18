"""Ed25519 signed artifact/manifest/in-jar integrity. Keys are supplied outside the repository."""
import argparse, base64, hashlib, json, os, tempfile, time, zipfile,subprocess,getpass,sys
from pathlib import Path
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
def canonical(v):return json.dumps(v,sort_keys=True,separators=(',',':')).encode()
def sha(b):return hashlib.sha256(b).hexdigest()
def signed_jar(source,target,key,sequence):
    with zipfile.ZipFile(source) as z:
        entries={n:z.read(n) for n in z.namelist() if not n.startswith('META-INF/maro-integrity') and not n.endswith('/')}
    # Signature covers every entry, including public trust and Fabric metadata. No private material is added.
    manifest=canonical({'format':1,'sequence':sequence,'entries':{n:sha(b) for n,b in sorted(entries.items())}})
    with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for n,b in entries.items():z.writestr(n,b)
        z.writestr('META-INF/maro-integrity.json',manifest)
        z.writestr('META-INF/maro-integrity.sig',key.sign(manifest))
    return sha(Path(target).read_bytes())
def sign(source,target,key,sequence,url):
    digest=signed_jar(source,target,key,sequence)
    payload=canonical({'format':1,'sequence':sequence,'sha256':digest,'size':Path(target).stat().st_size,'audience':'maro-protected','url':url,'expires':int(time.time())+604800})
    Path(str(target)+'.manifest.json').write_bytes(payload)
    Path(str(target)+'.manifest.sig').write_bytes(key.sign(payload))
    return digest
def verify_install(jar,manifest,signature,public,destination,min_sequence):
    payload=Path(manifest).read_bytes()
    if len(payload)>16384:raise ValueError('Manifest too large')
    public.verify(Path(signature).read_bytes(),payload);p=json.loads(payload)
    if not isinstance(p['size'],int) or not 1<=p['size']<=134217728 or Path(jar).stat().st_size!=p['size']:raise ValueError('Invalid artifact size')
    data=Path(jar).read_bytes()
    if p['audience']!='maro-protected' or p['format']!=1 or p['expires']<=time.time() or p['sequence']<=min_sequence or p['sha256']!=sha(data) or p['size']!=len(data):raise ValueError('Invalid, expired, rolled-back or tampered release')
    if not p['url'].startswith('https://'):raise ValueError('HTTPS release URL required')
    with zipfile.ZipFile(jar) as z:
        integrity=z.read('META-INF/maro-integrity.json');public.verify(z.read('META-INF/maro-integrity.sig'),integrity);entries=json.loads(integrity)['entries']
        names=[n for n in z.namelist() if n not in ('META-INF/maro-integrity.json','META-INF/maro-integrity.sig') and not n.endswith('/')]
        if len(names)!=len(set(names)) or set(names)!=set(entries) or any(sha(z.read(n))!=h for n,h in entries.items()):raise ValueError('Invalid jar integrity')
    destination=Path(destination)
    if destination.name!='Maro-Protected-Test.jar':raise ValueError('Install only to the separate protected filename')
    destination.parent.mkdir(parents=True,exist_ok=True)
    fd,tmp=tempfile.mkstemp(dir=destination.parent,prefix='.maro-verified-')
    try:
        with os.fdopen(fd,'wb') as f:f.write(data);f.flush();os.fsync(f.fileno())
        os.replace(tmp,destination)
    finally:
        if os.path.exists(tmp):os.unlink(tmp)
    return p['sequence']
def main():
    p=argparse.ArgumentParser();sub=p.add_subparsers(dest='command',required=True)
    k=sub.add_parser('keygen');k.add_argument('--private',type=Path,required=True);k.add_argument('--public',type=Path,required=True)
    s=sub.add_parser('sign');s.add_argument('--input',type=Path,required=True);s.add_argument('--output',type=Path,required=True);s.add_argument('--key',type=Path,required=True);s.add_argument('--sequence',type=int,required=True);s.add_argument('--url',required=True)
    v=sub.add_parser('verify-install');v.add_argument('--jar',required=True);v.add_argument('--manifest',required=True);v.add_argument('--signature',required=True);v.add_argument('--public',required=True);v.add_argument('--destination',required=True);v.add_argument('--minimum-sequence',type=int,required=True)
    a=p.parse_args()
    if a.command=='keygen':
        if not sys.stdin.isatty() or not sys.stdout.isatty():raise SystemExit('Use a private interactive terminal for release key generation')
        phrase=getpass.getpass('New release-key passphrase (16+ characters): ')
        if len(phrase)<16 or phrase!=getpass.getpass('Confirm release-key passphrase: '):raise SystemExit('Passphrases do not match or are too short')
        key=Ed25519PrivateKey.generate()
        a.private.parent.mkdir(parents=True,exist_ok=True);a.public.parent.mkdir(parents=True,exist_ok=True)
        with a.private.open('xb') as f:f.write(key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.BestAvailableEncryption(phrase.encode())))
        if os.name!='nt':a.private.chmod(0o600)
        else:
            who=subprocess.check_output(['whoami'],text=True).strip()
            subprocess.run(['icacls',str(a.private),'/inheritance:r','/grant:r',who+':F'],check=True,stdout=subprocess.DEVNULL)
        a.public.write_bytes(key.public_key().public_bytes(serialization.Encoding.PEM,serialization.PublicFormat.SubjectPublicKeyInfo));return
    if a.command=='sign':
        material=a.key.read_bytes()
        try:key=serialization.load_pem_private_key(material,password=None)
        except TypeError:
            if not sys.stdin.isatty():raise SystemExit('Private interactive terminal required to unlock release key')
            key=serialization.load_pem_private_key(material,password=getpass.getpass('Release-key passphrase: ').encode())
        a.output.parent.mkdir(parents=True,exist_ok=True)
        print('Signed SHA256:',sign(a.input,a.output,key,a.sequence,a.url));return
    print('Verified and installed sequence:',verify_install(a.jar,a.manifest,a.signature,serialization.load_pem_public_key(Path(a.public).read_bytes()),a.destination,a.minimum_sequence))
if __name__=='__main__':main()
