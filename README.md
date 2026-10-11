# Arlix — Offline Password Vault

[![F-Droid](https://img.shields.io/f-droid/v/com.arlix.shadowvault.svg)](https://f-droid.org/packages/com.arlix.shadowvault)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Min SDK](https://img.shields.io/badge/Android-9%2B-green.svg)]()

A fully **offline**, open-source password vault for Android. Your credentials never leave your device — no cloud, no network, no analytics, ever.

---

## Features

- 🔒 **AES-256 encrypted database** via [SQLCipher](https://www.zetetic.net/sqlcipher/)
- 🔑 **Argon2id key derivation** — memory-hard, resistant to GPU/ASIC brute-force
- 📴 **Zero network permissions** — the app literally cannot make network requests
- 🛡 **FLAG_SECURE** — vault contents hidden from screenshots and recents preview
- ⏰ **Auto-lock** — vault locks on app background and screen-off
- 🚫 **No cloud backup** — all backup excluded at the manifest level
- 📦 **Encrypted export/import** — device-to-device migration without exposing plaintext
- 🧹 **Native memory zeroing** (JNI) — sensitive byte arrays wiped before GC

## Security Model

Arlix uses a **layered defence** approach:

| Layer | Mechanism |
|---|---|
| Database encryption | SQLCipher AES-256-CBC |
| Key derivation | Argon2id (BouncyCastle) |
| Salt | 256-bit cryptographically random (SecureRandom) |
| Memory hygiene | JNI volatile-write zeroing of key material |
| Backup prevention | `allowBackup="false"` + full `data_extraction_rules.xml` exclusions |
| Network isolation | No `INTERNET` permission declared |
| Screen protection | `FLAG_SECURE` on all windows |
| Auto-lock | ProcessLifecycleOwner + `ACTION_SCREEN_OFF` receiver |

## Requirements

- Android 9.0 (API 28) or higher
- No special permissions required

## Building

```bash
git clone https://github.com/AdityaKumbhar07/arlix.git
cd arlix/Arlix
./gradlew :app:assembleRelease
```

The release APK will be at `app/build/outputs/apk/release/app-release.apk`.

> **Note:** F-Droid builds from source and signs with their own key. You do not need a keystore to build.

## Dependencies

All dependencies are open-source and available on Maven Central or Google Maven:

| Library | License | Purpose |
|---|---|---|
| [SQLCipher for Android](https://github.com/sqlcipher/sqlcipher-android) | BSD-style | AES-256 DB encryption |
| [BouncyCastle](https://www.bouncycastle.org/) | MIT | Argon2id KDF |
| [Jetpack Compose](https://developer.android.com/jetpack/compose) | Apache 2.0 | UI framework |
| [Room](https://developer.android.com/training/data-storage/room) | Apache 2.0 | ORM / SQLite abstraction |
| [AndroidX Lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle) | Apache 2.0 | ProcessLifecycleOwner |

## License

Arlix is licensed under the [GNU General Public License v3.0](LICENSE).

