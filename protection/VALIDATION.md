# Validation and deployment status

Validated on 17 October 2026, Windows 11, AMD Ryzen 7 7700X / Radeon RX 7600, Java 21, Minecraft 1.21.11. These are local measurements, not a performance guarantee for other machines.

## Friendly-menu update

The regular client now uses a neutral background, smaller branding, plain-language world/server shortcuts, and a compact honest loading bar. Native title controls, embedded libraries, resource callbacks, cancel/error paths, world fade and GUI scales 2/3/4 were verified. The protected edition adds sign-in/activation/play steps, busy states, field validation, a separate help/device page, reconnect guidance and the bundled START-HERE.html guide. Authentication and server permissions are unchanged. The new UI was exercised through native mouse clicks, including small-scale layouts.

## Completed checks

- **26 server/release tests passed** using disposable accounts, MFA seeds and cryptographic keys. Coverage includes ownership and allowlist removal, forged roles/account IDs/authentication flags, password/MFA failure, consumed enrollment/activation codes, JWT signature/issuer/audience/subject/permission/expiry checks, license/session/device revocation, replayed and altered signed requests, request age/path binding, device key binding, refresh rotation/reuse/expiry, logout, device limits and MFA reset, recovery-code consumption and revocation, reconnect persistence, registration/admin denial, rate limiting, strict origins/offline bounds, encrypted release-key generation, and signed update tamper/expiry/rollback rejection.
- **Real Windows Credential Manager check passed**: credential write/read, saved asymmetric key reload, malformed private-key denial and deletion, with no plaintext fallback. It uses the isolated test origin's credential namespace.
- **Native Minecraft checks passed** with the signed, obfuscated production artifact, both alone and alongside Meteor 1.21.11-85. Checks exercise initial denial, password + MFA, activation, guarded module enabling, native GUI rendering, HTTPS outage denial and module cleanup, service recovery, in-world rendering and logout. Screenshots inspected. Test mod, fixture credentials, test CA private material and private mapping are excluded from the tester artifact.
- **Normal build passed**, and the refreshed authentication-free JAR is SHA-256 `76983400f599dea187c798213c1685eb346a80bee4e4b0897a71ee46d6a7e031`. Unrelated classes and assets are checked against the preceding delivery during packaging. Default builds do not include `dev/maro/protection` classes or licensing trust resources.
- Prior native section tests passed for Panel/Dropdown grouping, fold controls, section search, automatic expansion of matching results, saved fold state, scrolling, scale/fullscreen and live visual previews.

The Python test harness emits one upstream deprecation warning about its `httpx` adapter. All assertions pass; the service's production HTTP listener does not use that adapter. Existing Minecraft test-profile warnings about dummy Mojang credentials and optional local media are unrelated to licensing.

## Measured cost

| Check | Measured result |
|---|---:|
| Full signed JAR integrity verification at startup, off render thread | about 185–207 ms across native runs |
| Licensed hot access guard, 2,000,000 calls in native client | about 23–24 ns/call |
| Argon2id password verification, 12 samples | median 31.43 ms, p95 33.25 ms |
| Ed25519 server license signing, 300 samples | median 0.0307 ms, p95 0.0401 ms |
| Ed25519 server license verification, 300 samples | median 0.0848 ms, p95 0.0893 ms |
| Ordinary authentication-free JAR | 29,051,761 bytes |
| Obfuscated artifact before final signed inventory | about 28.39 MB |
| ProGuard class renaming | 501 of 1,190 program classes in the measured build |
| Selective transformations | 80 encrypted literal sites; 1 branch-indirection site; 3 core gate instrumentation sites |

Expensive integrity/network/MFA work runs outside the render thread. Leases renew about every 30 seconds. There is no extra AES/string decoding on the hot access-guard path. No formal gameplay FPS benchmark or exhaustive combination test of every third-party mod was performed; native startup, GUI and in-world compatibility checks passed. Fabric mixins intentionally retain constructor line metadata, which is required for correct injected field initialization.

## External work still required

The delivered protected test JAR uses an **isolated localhost test service and disposable public trust keys**. Its temporary service is stopped and its private test keys are discarded after packaging. It is useful for obfuscation inspection; it is not provisioned production owner access. No real owner password, MFA enrollment, immutable account ID, DNS/TLS service or production signing identity has been fabricated or deployed.

For live use, supply a private HTTPS server/domain, persistent protected storage and backups, perform the interactive `navo` provisioning command, generate separate production release keys, export public trust, and rebuild/sign the protected artifact. Keep clocks synchronized. See `README.md` for exact commands, recovery/revocation/rotation and offline policy. The test artifact and the ordinary client share Fabric ID `maro`; install them in separate profiles.

Only the **tester bundle/JAR** should be given to the obfuscation tester. The owner kit/full source and `mapping-private.txt` disclose implementation and mappings and should remain separate from that exercise. Applicable GPL/third-party notices and source obligations still apply.

Server authorization is the security boundary. Client code, checks and embedded string-decoding keys remain inspectable and modifiable. Signed releases establish authenticity when verified against a previously trusted public key; replacing that trust root or compromising its private signer defeats that trust. Optional nonzero offline leases trade immediate revocation for a strictly bounded grace period; the shipped/tested policy is zero.
