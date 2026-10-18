"""Owner recovery from a trusted terminal. No tokens/secrets are printed or passed as arguments."""
import argparse,getpass,sys
import httpx,pyotp
from service import Settings,password_policy
from pathlib import Path
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--origin',required=True);args=parser.parse_args();Settings(Path('.'),args.origin)
    if not sys.stdin.isatty() or not sys.stdout.isatty():raise SystemExit('Use a private interactive terminal')
    old=getpass.getpass('Current password: ');code=getpass.getpass('Unused recovery code: ')
    new=getpass.getpass('New unique password: ');password_policy(new)
    if new!=getpass.getpass('Confirm new password: '):raise SystemExit('Passwords do not match')
    secret=pyotp.random_base32();print('Enroll this new authenticator secret (shown once):',secret);otp=getpass.getpass('New authenticator code: ')
    if not pyotp.TOTP(secret).verify(otp,valid_window=0):raise SystemExit('New authenticator verification failed')
    result=httpx.post(args.origin.rstrip('/')+'/v1/recover',json={'username':'navo','password':old,'recovery_code':code,'new_password':new,'new_totp':secret,'new_otp':otp},timeout=10,follow_redirects=False)
    if result.status_code!=200:raise SystemExit('Recovery denied or service unavailable; no access granted')
    print('Recovered. All sessions/devices revoked. Sign out in the client to remove its old key, then sign in after the next MFA code.')
if __name__=='__main__':main()
