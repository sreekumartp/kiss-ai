# Publishing K.I.S.S AI to Google Play

Everything the app needs for Play is in this repository. These are the steps only the account owner
can do. Allow about an hour for the forms, plus Google's review and testing time.

## 0. One-time: Google Play developer account
1. Sign up at https://play.google.com/console/signup (one-time US$25 fee, identity verification).
2. **New personal accounts must run a closed test before going public:** at least **12 testers**
   opted in for **14 days in a row**. Organisation accounts skip this. Plan for it.

## 1. Create your upload key (once, keep it forever)
Run this on your own computer (needs Java's `keytool`, included with Android Studio or any JDK):

```sh
keytool -genkeypair -v -keystore kiss-ai-upload.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

Choose a strong password. **Back up `kiss-ai-upload.jks` and the password somewhere safe.**
Google holds the real app-signing key (Play App Signing); this upload key only proves updates come
from you, and Google can reset it if lost, but that takes time.

## 2. Give the key to GitHub (encrypted secrets)
In https://github.com/sreekumartp/kiss-ai/settings/secrets/actions add four **repository secrets**:

| Secret | Value |
|---|---|
| `UPLOAD_KEYSTORE_BASE64` | output of `base64 -w0 kiss-ai-upload.jks` (macOS: `base64 -i kiss-ai-upload.jks`) |
| `UPLOAD_KEYSTORE_PASSWORD` | the keystore password |
| `UPLOAD_KEY_ALIAS` | `upload` |
| `UPLOAD_KEY_PASSWORD` | the key password (same as keystore password if you pressed Enter) |

Then run **Actions → Android app → Run workflow**. Download the **kiss-ai-play-bundle** artifact:
it contains `app-release.aab`, signed with your upload key.

## 3. Create the app in Play Console
**Create app** → name `K.I.S.S AI: Master AI & ML`, App, Free, accept the declarations.

## 4. Store listing
Copy the texts and upload the graphics from [`listing.md`](listing.md) and `graphics/`.

## 5. App content (Policy → App content)
| Section | Answer |
|---|---|
| Privacy policy | https://sreekumartp.github.io/kiss-ai/privacy.html |
| Ads | No ads |
| App access | All functionality is available without an account (sign-in is optional) |
| Content rating | Questionnaire → category **Reference, news or educational**; answer No to all content questions |
| Target audience | **13 and over** (or 18+); not designed for children |
| Data safety | See below |
| Account deletion | Yes, users can create accounts. Deletion URL: https://sreekumartp.github.io/kiss-ai/privacy.html#delete — in-app: Settings → Account and sync → Delete account |
| Government / financial / health features | None |

### Data safety answers
- **Does the app collect or share user data?** Yes, collects (only when the user signs in). Not shared.
- **Encrypted in transit?** Yes. **Can users request deletion?** Yes.
- Data types collected:
  - **Personal info → Email address:** Collected, not shared. Required for the account. Purpose: *Account management*. Not processed ephemerally.
  - **Personal info → User IDs:** Collected (Firebase user ID). Purpose: *Account management*, *App functionality*.
  - **App activity → Other user-generated content** (study progress and the user's own success notes): Collected. Purpose: *App functionality*.
- Everything else (location, contacts, photos, device IDs, analytics, crash logs): **not collected**.

## 6. Release
1. **Testing → Closed testing** → create a track, add your 12+ testers (email list or Google Group),
   upload `app-release.aab`, write release notes, roll out. Share the opt-in link with testers.
2. After 14 days with 12+ opted-in testers, **apply for production access** from the dashboard.
3. **Production → Create release** → upload the same or a newer `.aab` → roll out.

For each update later: bump `versionCode` and `versionName` in `android/app/build.gradle.kts`, push,
download the new `.aab` from Actions, upload it to a new release.

## Notes
- Package name: `io.github.sreekumartp.aimlatlas`. It can never change once published.
- Target API 36 (Android 16), minimum Android 8.0.
- Sign-in on Android is email/password (Google blocks its sign-in inside app web views).
