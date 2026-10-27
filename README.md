# Investment Tracker

Personal Android app to view mutual fund and stock holdings from Paytm Money and Groww,
with insights. All data stays on the phone in an encrypted database; there is no backend.

## Modules

| Module | What it holds |
| --- | --- |
| `app` | Application, activity, theme, manual dependency wiring |
| `core:model` | Pure Kotlin domain models and calculations (holdings; later XIRR, tax, SWP) |
| `core:data` | Room database encrypted with SQLCipher, Keystore-wrapped key, repository |
| `feature:portfolio` | Portfolio screen (Compose) |

Planned additions: `feature:import` (CAS PDF parsing, Gmail fetch), `core:prices`
(daily NAV / stock price refresh), `feature:insights`, `feature:swp`.

## Privacy

- Database is encrypted with SQLCipher; the random passphrase is wrapped by an Android Keystore key.
- Backups and device-to-device transfer are disabled.
- No `INTERNET` permission yet.
- Screens are marked `FLAG_SECURE` (no screenshots / recents preview).

## Building

CI (`.github/workflows/android.yml`) runs unit tests and builds an APK on every push.
Download it from the run's **Artifacts**; pushes to `main` also create a GitHub Release.

### Signing secrets

Add these under *Settings → Secrets and variables → Actions*:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | key alias, e.g. `tracker` |
| `ANDROID_KEY_PASSWORD` | key password |

Create the keystore once and keep it safe (losing it means reinstalling the app and losing data):

```sh
keytool -genkeypair -v -keystore release.jks -alias tracker \
  -keyalg RSA -keysize 4096 -validity 10000
```

Without these secrets CI still builds a debug APK.
