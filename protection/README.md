# Separate protected edition

**New here? Open `START-HERE.html` first.** It separates playing with the regular client, testing obfuscation, and setting up the owner service. The protected sign-in screen now walks through sign-in, first activation, and play, with a separate Help page and device-management page.

The ordinary `./gradlew build` produces the existing **authentication-free** client. The opt-in pipeline below produces **Maro-Protected-Test.jar**. It never overwrites the ordinary JAR. Use separate Minecraft profiles: both editions have Fabric ID `maro` and must not be installed together. Protected configuration uses `.minecraft/maro-protected`; ordinary configuration stays in `.minecraft/maro`.

This repository is Java 21, Fabric Loom 1.14.10, Minecraft **1.21.11**, Fabric Loader 0.18.2, with a Gradle build/remap pipeline. The licensing service is a separate Python process on a private host behind HTTPS. No existing hosted authentication service or application updater was present. It does not reuse Minecraft/Microsoft credentials.

## Provision the real owner privately

Supply a server you control, an HTTPS DNS name/TLS termination, Python 3.11+, persistent private storage, backups, and a production release signing key. The service has its own local owner account. `navo` is its display/login name, not a password or an identity bypass. Initial provisioning verifies possession of the new authenticator and binds a newly generated immutable UUID in the server's `owners` table. No public account creation route exists.

From the repository root:

```powershell
python -m venv protection/.venv
protection/.venv/Scripts/python.exe -m pip install -r protection/server/requirements.lock
protection/.venv/Scripts/python.exe protection/server/admin.py provision --state C:/MaroPrivate/license --origin https://YOUR-LICENSE-HOST --output C:/MaroPublic/client-public.json
```

Run provisioning in an unrecorded private terminal. It refuses redirected input/output and a nonempty state directory. Enter a unique 16+ character password, confirm it, enroll the displayed MFA secret in an authenticator, then verify its code. Save the activation key and ten single-use recovery codes in a password manager/offline storage when displayed **once**. Nothing is provisioned merely by copying the supplied test artifact.

Server storage contains Argon2id password hashes (64 MiB, 3 iterations), SHA-256 verifiers of random 384-bit activation/session secrets and random recovery codes, encrypted TOTP enrollment, and private Ed25519 signing material. The encryption key and signing key stay on the server. Protect the complete private directory and its backups with OS access control/encrypted disks; its encryption key must remain outside public artifacts. Windows provisioning restricts the directory ACL to the provisioning account; run the service as that account or explicitly grant only its service identity. Unix mode is 0700/0600. Run under a dedicated service account, not a public web root.

Copy `server/config.example.json` outside the repository, replace placeholders, and set `MARO_LICENSE_CONFIG` to its absolute path. Start `server/run.py`. It binds only `127.0.0.1:8788`. Put Caddy or another correctly configured TLS proxy in front, using `server/Caddyfile.example`. Request bodies are bounded to 16 KiB at the proxy; do not expose the Python listener directly. Use one host with local SQLite, not an NFS database or independent replicas. The service validates Host; configure its exact public origin. Access/body/token logging is disabled. Do not enable proxy request-body logging.

## Build and sign

Generate a **separate** release signing key on the trusted build/signing machine, outside this repository, with a restricted directory. Keep a protected offline backup; a hardware-backed signing workflow can replace the local signer.

```powershell
protection/.venv/Scripts/python.exe protection/release.py keygen --private C:/MaroPrivate/signing/release.pem --public C:/MaroPublic/release-public.pem
```

Add `"release_public_key": "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----\n"` to the exported **public** client configuration. Never add a private key. Keep `offline_max_seconds` equal to the server's `offline_seconds`, normally zero. The public file has `origin`, `audience`, `keys` keyed by license-signing key ID, and `release_public_key`. Public keys and service URLs are intentionally shipped.

```powershell
./gradlew.bat protectedRemapJar -PprotectedEdition=true -PlicensePublicConfig=C:/MaroPublic/client-public.json
protection/.venv/Scripts/python.exe protection/release.py sign --input build/protected/Maro-Protected-Test.jar --output C:/MaroReleases/Maro-Protected-Test.jar --key C:/MaroPrivate/signing/release.pem --sequence 1 --url https://YOUR-DOWNLOAD-HOST/Maro-Protected-Test.jar
```

Ship only the signed output, its `.manifest.json` and `.manifest.sig`, and the public release key through a separately trusted channel. Keep `build/protection/mapping-private.txt`, build intermediates, source/test artifacts and private server state away from the obfuscation tester. The ordinary source distribution remains separate and retains the project's GPL/third-party notices and source obligations.

The signer adds a signed hash inventory covering every JAR entry. Startup verifies it before granting protected functionality. The signed external manifest binds the final JAR's SHA-256, size, audience, expiry and increasing release sequence. **Before installing** an update, use the supplied verifier with the previously trusted release public key and the last installed sequence:

```powershell
protection/.venv/Scripts/python.exe protection/release.py verify-install --jar downloaded.jar --manifest downloaded.jar.manifest.json --signature downloaded.jar.manifest.sig --public C:/TrustedKeys/maro-release.pem --destination C:/MinecraftProtected/mods/Maro-Protected-Test.jar --minimum-sequence 1
```

The verifier rejects unsigned/altered/expired/wrong-audience/rolled-back artifacts and atomically replaces only the separate protected filename after all checks pass. Record the returned sequence in trusted deployment records; don't reset `--minimum-sequence` to bypass rollback checks. There is no unattended updater that skips this verifier. Changing the trust key requires an authenticated out-of-band deployment, not a key delivered by an unverified update.

## Activation, sessions and recovery

Start the protected client on Windows. Its owner screen takes the `navo` password and a fresh authenticator code. Then enter the owner activation key once. MFA codes cannot be replayed. Activation happens only in an authenticated owner session. Each device generates an Ed25519 key and stores it with its rotating refresh credential in **Windows Credential Manager**, never a JSON config file. Unsupported platforms or vault errors fail closed. Password/MFA/activation text is masked, cleared after submission and never persisted. Minecraft's existing account handling is separate.

Every licensing operation verifies the signed token, live owner allowlist, session, license, device, permission and a device-signed request proof. Proofs bind method, path, raw body hash, credential hash, timestamp and unique nonce; SQLite transactions make nonce/OTP consumption and refresh rotation atomic. The client cannot grant itself a role by changing a username/account ID/flag. Issued Ed25519 JWTs have issuer, audience, immutable subject, license/session/device IDs, expiry and explicit permissions. Activation consumes its random key verifier. Refresh credentials rotate; reuse invalidates the affected session family.

Token lifetime is at most **5 minutes**. Client leases renew about every **30 seconds** with a 6-second request timeout. Revocation takes effect immediately on server operations and at the next client lease check. Refresh sessions expire after 7 idle days or 30 absolute days. Device limit defaults to two (`device_limit`, 1–20). IP addresses are only a coarse abuse throttle, never identity; hardware IDs are not collected. Security events contain only event types.

The owner screen's **Revoke other devices** requires the current password and an unused MFA code, and revokes other devices and their sessions. `/v1/devices` lists device/session IDs for authenticated signed management requests; `/v1/devices/reset` also accepts one `device_id` to revoke a selected device. Sign out invalidates the current server session and deletes the local key/credential. If offline, local logout still removes the credential; use another authenticated device or trusted server console to revoke the remote session.

For a lost authenticator, use a private terminal:

```powershell
protection/.venv/Scripts/python.exe protection/server/recover.py --origin https://YOUR-LICENSE-HOST
```

Recovery requires the current password **and** one unused recovery code, verifies a newly enrolled authenticator, changes the password, and revokes all sessions/devices. Then sign out locally to discard its revoked key and sign in using a new MFA code. Codes are consumed individually; ten are issued during provisioning. If both password and recovery material are lost, recovery requires your trusted server administrator and a deliberate new provisioning/migration process—there is no universal remote recovery password.

Trusted-console operations (stop/restart the service around signing rotation):

```powershell
python protection/server/admin.py revoke-all --state C:/MaroPrivate/license --origin https://YOUR-LICENSE-HOST
python protection/server/admin.py disable-license --state C:/MaroPrivate/license --origin https://YOUR-LICENSE-HOST
python protection/server/admin.py rotate-activation --state C:/MaroPrivate/license --origin https://YOUR-LICENSE-HOST
python protection/server/admin.py rotate-signing --state C:/MaroPrivate/license --origin https://YOUR-LICENSE-HOST
python protection/server/admin.py export-public --state C:/MaroPrivate/license --origin https://YOUR-LICENSE-HOST --output C:/MaroPublic/client-public.json
```

Rotation revokes existing sessions. Export the new public configuration, rebuild and sign through the still-trusted release channel. Keep release and license signing keys distinct. Release-key compromise requires distributing a new trust root outside that compromised channel. Activation rotation displays a fresh key once; it never uses `navo` as a secret.

## Offline and denial behavior

Default **offline allowance is zero**: a detected transport failure locks module functionality, while vanilla Minecraft remains running. Recovery of the service allows the saved valid device credential to resume via refresh; it never bypasses authentication. Invalid signatures, claims, tampered credentials, revocation or authorization denial always lock features. An optional 1–120 second offline lease is bounded by the last successful online verification, signed token expiry and monotonic time, and must be explicitly configured on both server/client. Process restarts always require online verification. Clock rollback cannot extend a lease. There is no offline owner provisioning or cached forever-license.

There is no BSOD, Windows crash, process tampering, security-software interference, unrelated-file alteration or retaliation. Checks operate only in the protected edition and its own credential/state/artifact paths.

## Obfuscation and honest limits

ProGuard **7.10.0** performs symbol renaming, while the build strips source paths, line/local-variable metadata and unused packaged build metadata. Keep rules preserve Fabric entry points, mixins, native/JNA layouts, public overrides, record information and Gson field/config compatibility. Selected license-client literals receive AES-GCM string encoding using JDK cryptography; one request-proof method receives branch indirection. Those embedded decoder keys are **not secrets**. No actual activation/private signing secret is embedded. Costly transformations avoid rendering hot paths.

This raises inspection effort; it does not make shipped code impossible to decompile, patch or run under instrumentation. Public API/config/native/mixin names remain visible. Embedded literal keys can be recovered. Client guards and in-JAR trust can be patched; they are supporting signals. Actual owner permissions remain on the service. Existing offline game modules still execute locally and cannot become unpatchable server-side features without moving their work to a service. The separately distributed auth-free edition also exposes equivalent local behavior, by your explicit request.

ProGuard itself does not provide strong commercial control-flow virtualization or string encryption; the small selective ASM pass is inspectable and should not be represented as either. See [ProGuard's documentation](https://www.guardsquare.com/manual/configuration/usage), [version support](https://www.guardsquare.com/manual/releasenotes), [PyJWT](https://pyjwt.readthedocs.io/en/stable/usage.html), [Argon2 parameters](https://argon2-cffi.readthedocs.io/en/stable/parameters.html), and [PyOTP's replay guidance](https://pyauth.github.io/pyotp/). Independent review remains appropriate before public deployment.

## Verify

```powershell
protection/.venv/Scripts/python.exe -m pytest protection/server/test_security.py -q
```

Tests use disposable accounts/keys only and cover valid ownership, removed allowlists, forged fields/claims, invalid/consumed activation keys, inactive permissions, expiry/issuer/audience/subject, license/session/device revocation, signed-request and MFA replay, modified bodies, wrong device keys, refresh rotation/reuse, tampered refresh credentials, logout, device limits/reset, recovery-code consumption, registration/admin denial, rate limiting, origin/offline bounds, and signed release tamper/expiry/rollback checks.

`tests/fixture.py` creates an isolated temporary HTTPS service and CA for native integration checks. Its credentials and signing keys never belong to the real owner and must never be deployed. Native tests and performance results are recorded in `VALIDATION.md` when delivered. Production requires you to complete the private interactive provisioning and supply the actual host, URL, public trust file and signing keys; no real owner password or immutable account ID has been fabricated.
