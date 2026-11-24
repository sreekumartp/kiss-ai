# The K.I.S.S model for mastering AI

A map of AI and machine learning: 243 topics from arithmetic to the 2024–26 frontier, plus an Applied AI
section. Every topic has a syllabus, learning resources and a **K.I.S.S ladder**:

| Rung | Meaning |
|---|---|
| **I**nformation | You know it exists and what it is for |
| **K**nowledge | You can teach it or hold a technical conversation about it |
| **S**kill | You can build or create something with it |
| **S**uccess | Others recognise, use or build on it (SME status, papers, new techniques, adoption, influence or money) |

It also has phased learning paths for 20 AI roles, a personal funnel, and backup/restore of your progress.

- **Web app:** published to GitHub Pages from `.github/workflows/pages.yml`.
- **Android app:** `android/`. APKs are built by `.github/workflows/android.yml` (see its run artifacts).

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
