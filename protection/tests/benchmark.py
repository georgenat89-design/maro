import json,statistics,sys,time,secrets
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'server'))
import jwt
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from service import PH
def sample(call,count):
    values=[]
    for _ in range(count):
        start=time.perf_counter_ns();call();values.append((time.perf_counter_ns()-start)/1e6)
    return {'median_ms':round(statistics.median(values),4),'p95_ms':round(sorted(values)[int(.95*(len(values)-1))],4),'samples':count}
password=secrets.token_urlsafe(32);hashed=PH.hash(password);key=Ed25519PrivateKey.generate();claims={'iss':'https://benchmark.invalid','aud':'fixture','exp':int(time.time())+300,'permissions':['client:use']};token=jwt.encode(claims,key,algorithm='EdDSA')
print(json.dumps({'argon2id_password_verify':sample(lambda:PH.verify(hashed,password),12),'ed25519_license_sign':sample(lambda:jwt.encode(claims,key,algorithm='EdDSA'),300),'ed25519_license_verify':sample(lambda:jwt.decode(token,key.public_key(),algorithms=['EdDSA'],issuer=claims['iss'],audience=claims['aud']),300)},indent=2))
