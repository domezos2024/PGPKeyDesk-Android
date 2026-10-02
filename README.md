# PGPKeyDesk for Android

Android port of the Windows app [PGPKeyDesk](https://github.com/domezos2024/OpenGPG): manage **OpenPGP key profiles** and **encrypt, decrypt, sign and verify** text and files. Kotlin, Jetpack Compose, [BouncyCastle](https://www.bouncycastle.org/) (no GnuPG needed). English and German UI.

## Features (parity with the Windows app)

- Profiles: generate RSA 2048/4096 key pairs (optional expiry), import/export `.asc`, validate keys on import
- Decrypt PGP messages (paste, file, share sheet) with optional signature check against a sender key
- Encrypt text; with a profile selected the message is additionally signed
- Recipient keyring with fingerprints; expired/revoked keys are blocked, keys expiring within 30 days are flagged, startup expiry warning
- File encrypt/decrypt/sign/verify (up to 200 MB, optional ASCII armor); results are written to a temp file first and saved only on success
- Clipboard auto-clear after 60 s for decrypted text

## Security

- Profiles are stored AES-256-GCM encrypted with a non-exportable Android Keystore key (counterpart of Windows DPAPI)
- Passphrases are never stored; no INTERNET permission, no telemetry, backups disabled
- Release builds set `FLAG_SECURE` (no screenshots / recents preview)
- Not an audited product; use established tools for highly critical cases

## Build

Requirements: JDK 17+, Android SDK 35.

```
./gradlew testDebugUnitTest assembleDebug
./gradlew assembleRelease   # unsigned unless PGPKEYDESK_KEYSTORE* env vars are set
```

Signing env vars: `PGPKEYDESK_KEYSTORE`, `PGPKEYDESK_KEYSTORE_PASSWORD`, `PGPKEYDESK_KEY_ALIAS`, `PGPKEYDESK_KEY_PASSWORD`.

## Layout

| Path | Contents |
|------|----------|
| `core/` | `PgpService` (BouncyCastle operations), `KeyValidator`, `KeyExpiry`; UI-independent, unit-tested |
| `data/` | encrypted profile store, keyring, Keystore protector, clipboard, file helpers |
| `ui/` | Compose screens: Decrypt, Encrypt, Files, Keyring, Profiles |

License: MIT
