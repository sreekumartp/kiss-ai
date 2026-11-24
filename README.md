# The K.I.S.S model for mastering AI

A map of AI and machine learning: 243 topics from arithmetic to the 2024–26 frontier, plus an Applied AI
section. Every topic has a syllabus, learning resources and a **K.I.S.S ladder**:

| Rung | Meaning |
|---|---|
| **I**nformation | You know it exists and what it is for |
| **K**nowledge | You can teach it or hold a technical conversation about it |
| **S**kill | You can build or create something with it |
| **S**uccess | Others recognise, use or build on it (SME status, papers, new techniques, adoption, influence or money) |

It also has phased learning paths for 20 AI roles, a personal funnel, backup/restore, and optional
sign-in that syncs your progress across devices.

- **Web app:** published to GitHub Pages from `.github/workflows/pages.yml`.
- **Android app:** `android/`. APKs and the Play bundle (`.aab`) are built by `.github/workflows/android.yml` (see its run artifacts).
- **Google Play:** everything needed to publish is in [`playstore/`](playstore/README.md): signing setup, listing text, graphics, data-safety answers.
- **Privacy policy:** https://sreekumartp.github.io/kiss-ai/privacy.html

## Layout

```
source/kiss-atlas.html        the atlas (loads d3 and fonts from CDNs)
android/tools/build_app_page.py   turns it into the offline page below
android/app/src/main/assets/  offline page + d3 + fonts: used by the Android app AND the web site
android/                      Android app (Kotlin WebView shell)
```

To change content, edit `source/kiss-atlas.html`, then run:

```sh
python3 android/tools/build_app_page.py source/kiss-atlas.html android/app/src/main/assets/index.html
```

## One-time GitHub Pages setup

Settings → Pages → Build and deployment → Source: **GitHub Actions**. The site is then served at
`https://<owner>.github.io/<repo>/`.

## Turn on sign-in and progress sync (Firebase, free tier)

Without this the app works fully offline and keeps progress on each device. With it, people can
sign in (email/password everywhere, Google on the website) and their progress syncs live across
devices. Each person can only read and write their own data (`firestore.rules`).

1. Go to https://console.firebase.google.com → **Add project** (Google Analytics can be off).
2. **Build → Authentication → Get started.** Enable **Email/Password** and **Google**.
3. **Authentication → Settings → Authorized domains → Add domain:** `sreekumartp.github.io`.
4. **Build → Firestore Database → Create database** (production mode; pick a nearby region).
5. **Firestore → Rules:** paste the contents of `firestore.rules` and **Publish**.
6. **Project settings → General → Your apps → Web (`</>`)**: register an app and copy `firebaseConfig`.
7. Put those values in `android/app/src/main/assets/firebase-config.js` (replace `null`) and push.
   The website and the APK rebuild automatically.

The config values are meant to be public; security comes from the Firestore rules. In the Android
app, Google blocks its sign-in inside app web views, so the app offers email/password.

How sync works: progress is stored as one document per user at `users/{uid}`. On first sign-in on a
device, local progress merges with the account (the higher rung wins, ticked subtopics combine,
existing success definitions are kept). After that every change is saved within a couple of seconds
and other signed-in devices update live. Signing out leaves a copy on the device.

