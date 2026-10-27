# Investment Tracker

Personal Android app to view mutual fund and stock holdings from Paytm Money and Groww,
with insights. All data stays on the phone in an encrypted database; there is no backend.

## Modules

| Module | What it holds |
| --- | --- |
| `app` | Application, activity, theme, manual dependency wiring |
| `core:model` | Pure Kotlin domain models and calculations (holdings resolution; later XIRR, tax, SWP) |
| `core:parsing` | Pure Kotlin parsers: CAS text (CAMS / KFintech, NSDL, CDSL), AMFI NAV file, NSE / BSE bhavcopy |
| `core:data` | Room database encrypted with SQLCipher, Keystore-wrapped key, repositories |
| `core:sync` | PDF unlock + text extraction (PdfBox-Android), statement import, Gmail fetch, price refresh, background worker |
| `feature:portfolio` | Portfolio screen (Compose) |
| `feature:import` | Import screen: PAN / passwords, file picker, Gmail connect, price refresh, import history |

Planned additions: `feature:insights`, `feature:swp`.

## Importing holdings

Holdings come from the Consolidated Account Statement (CAS), which covers both Groww and
Paytm Money because it is issued per PAN:

- **NSDL / CDSL CAS** is emailed every month, locked with your PAN. It lists shares and ETFs in
  every demat account and every mutual fund folio, with units and current value. The demat
  account's broker (Groww / Paytm Money) is detected from the DP name.
- **CAMS / KFintech detailed CAS** (request it at camsonline.com) adds full mutual fund
  transaction history and cost. It is locked with the password you choose when requesting it.

Ways to import, all parsed on the phone:

1. *Import → Choose CAS PDF*, or share / "open with" a CAS PDF from Gmail or Files.
2. *Import → Connect Gmail*: the app searches your mailbox (read-only) for CAS emails and
   imports new ones twice a day in the background.

The newest statement is authoritative for what it covers, so a fund or share that disappears
from a newer statement is treated as sold. Purchase cost of shares isn't in the depository CAS,
so gains for shares show only once a cost source is added (a later step).

Prices: mutual fund NAVs come from AMFI's daily NAV file and share / ETF closing prices from the
NSE bhavcopy (BSE as fallback), refreshed twice a day and on demand.

## Gmail setup (once)

Gmail access uses Google Play services on the phone, so Google needs to know the app:

1. In [Google Cloud Console](https://console.cloud.google.com/) create a project, then enable the
   **Gmail API** (*APIs & Services → Library*).
2. *Google Auth Platform → Branding / Audience*: user type **External**, app name
   "Investment Tracker", your email as support and developer contact. Add your Gmail address as a
   **test user**. Under *Data access* add the scope `.../auth/gmail.readonly`.
3. *Clients → Create client → Android*: package name `com.johnyrahul.investmenttracker` and the
   SHA-1 shown at the bottom of the app's Import screen (also printed in each CI run's summary).
4. In the app: *Import → Connect Gmail*, pick your account and allow read-only access.

While the consent screen is in "Testing", Google expires the grant after 7 days and the app will
ask you to connect again. To avoid that, click **Publish app** on the Audience page; for an app
only you use, verification isn't needed. Google will show an "unverified app" warning on
connect (*Advanced → Go to Investment Tracker*).

## Privacy

- Database is encrypted with SQLCipher; the random passphrase is wrapped by an Android Keystore key.
- Backups and device-to-device transfer are disabled.
- Network is used only to download public NAV / price files (AMFI, NSE, BSE) and to read CAS
  emails from Gmail with a read-only token. No portfolio data is uploaded anywhere; there is no backend.
- PAN and statement passwords are stored in the encrypted database.
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
