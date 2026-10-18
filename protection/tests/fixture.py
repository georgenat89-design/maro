"""Isolated TLS integration fixture. Never provisions or modifies a real owner's account."""
import datetime, ipaddress, json, os, secrets, subprocess, sys, tempfile, threading, time, shutil, sqlite3,hashlib
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'server'))
import pyotp,uvicorn
from cryptography import x509
from cryptography.x509.oid import NameOID
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import rsa,ed25519
from fastapi.responses import JSONResponse
from admin import provision
from service import Settings,create_app

def prepare():
    folder=Path(tempfile.mkdtemp(prefix='maro-security-fixture-'))
    key=rsa.generate_private_key(public_exponent=65537,key_size=2048);name=x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'Maro isolated test CA')]);t=datetime.datetime.now(datetime.timezone.utc)
    cert=x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key()).serial_number(x509.random_serial_number()).not_valid_before(t-datetime.timedelta(minutes=1)).not_valid_after(t+datetime.timedelta(days=1)).add_extension(x509.BasicConstraints(ca=True,path_length=None),True).add_extension(x509.SubjectAlternativeName([x509.DNSName('localhost'),x509.IPAddress(ipaddress.ip_address('127.0.0.1'))]),False).sign(key,hashes.SHA256())
    (folder/'tls.pem').write_bytes(cert.public_bytes(serialization.Encoding.PEM));(folder/'tls-key.pem').write_bytes(key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
    secret=pyotp.random_base32();password=secrets.token_urlsafe(32);activation,codes,uid=provision(folder/'state','https://localhost:8799',password,secret,pyotp.TOTP(secret).now())
    release=ed25519.Ed25519PrivateKey.generate();(folder/'release.pem').write_bytes(release.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
    public=serialization.load_pem_private_key((folder/'state/signing.pem').read_bytes(),None).public_key();meta=json.loads((folder/'state/provisioned.json').read_text())
    (folder/'public.json').write_text(json.dumps({'origin':'https://localhost:8799','audience':'maro-protected','test_build':True,'offline_max_seconds':0,'keys':{meta['kid']:public.public_bytes(serialization.Encoding.PEM,serialization.PublicFormat.SubjectPublicKeyInfo).decode()},'release_public_key':release.public_key().public_bytes(serialization.Encoding.PEM,serialization.PublicFormat.SubjectPublicKeyInfo).decode()},indent=2))
    (folder/'fixture-private.json').write_text(json.dumps({'password':password,'totp':secret,'activation':activation}))
    java=Path(os.environ['JAVA_HOME'])/'bin/keytool.exe'
    shutil.copyfile(Path(os.environ['JAVA_HOME'])/'lib/security/cacerts',folder/'truststore.p12')
    subprocess.run([str(java),'-importcert','-noprompt','-alias','fixture','-file',str(folder/'tls.pem'),'-keystore',str(folder/'truststore.p12'),'-storepass','changeit'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    print(folder)
def serve(folder):
    data=json.loads((folder/'fixture-private.json').read_text());app=create_app(Settings(folder/'state','https://localhost:8799'))
    @app.middleware('http')
    async def availability(request,call_next):
        if (folder/'unavailable').exists():return JSONResponse({'detail':'Fixture offline'},status_code=503)
        return await call_next(request)
    def otp_file():
        while True:
            payload={'password':data['password'],'otp':pyotp.TOTP(data['totp']).now(),'activation':data['activation']}
            tmp=folder/'client-credentials.tmp';tmp.write_text(json.dumps(payload));os.replace(tmp,folder/'client-credentials.json');time.sleep(.25)
    threading.Thread(target=otp_file,daemon=True).start()
    uvicorn.run(app,host='127.0.0.1',port=8799,ssl_keyfile=str(folder/'tls-key.pem'),ssl_certfile=str(folder/'tls.pem'),access_log=False,log_level='error')
if __name__=='__main__':
    if len(sys.argv)==1:prepare()
    elif sys.argv[1]=='reset':
        folder=Path(sys.argv[2]);data=json.loads((folder/'fixture-private.json').read_text())
        db=sqlite3.connect(folder/'state/license.sqlite3')
        for table in ('sessions','devices','spent_refresh','replay','limits'):db.execute('DELETE FROM '+table)
        db.execute('UPDATE accounts SET last_step=-1');db.execute('UPDATE license SET active=0,revoked=0,activation=?',(hashlib.sha256(data['activation'].encode()).hexdigest(),));db.commit();db.close()
        (folder/'unavailable').unlink(missing_ok=True)
    else:serve(Path(sys.argv[1]))
