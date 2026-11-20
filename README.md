<div align="center">

<img src="docs/icon.png" alt="READR icon" width="140"/>

# READR

**A Better Way to Read.**

A beautiful, lightweight, offline-first e-book & document reader for Android.

PDF · EPUB · DOCX · TXT

[![Platform](https://img.shields.io/badge/platform-Android-green)](#)
[![Language](https://img.shields.io/badge/language-Kotlin-orange)](#)
[![UI](https://img.shields.io/badge/ui-Jetpack%20Compose-blue)](#)
[![License](https://img.shields.io/badge/license-Apache%202.0-red)](#)
[![Release](https://img.shields.io/github/v/release/hasib-insights/READR)](https://github.com/hasib-insights/READR/releases/latest)
[![Min SDK](https://img.shields.io/badge/minSdk-24-informational)](#)

</div>

---

## About READR

READR is a personal digital bookshelf. It opens four of the most common
document formats and prints them on a choice of realistic paper surfaces —
starting with **Warm Cream Paper** and a subtle grain texture — so reading on
a phone feels like reading a printed book rather than staring at a screen.

Everything works offline. There is no login, no advertising, no analytics and
no internet permission: your documents and reading data never leave your
device.

## Download READR


### Latest Android version

[Download READR APK](https://github.com/hasib-insights/READR/releases/latest/download/READR.apk)

[View all releases](https://github.com/hasib-insights/READR/releases/latest)

Current version: **1.0.2** (released 2026-11-20) · Requires Android 7.0 (API 24) or newer.

> Releases are signed with the READR release key. Devices still running the
> original **1.0.0** build need a one-time uninstall before installing 1.0.1+;
> 1.0.1 and later update normally over each other.

## Main features

- **My Library** — a personal bookshelf with Continue Reading, Favorites,
  recently imported documents, grid & list views, search, sorting by title /
  date / format / last opened and filtering by PDF, EPUB, DOCX and TXT.
- **Reading Room** — a distraction-free reader: chrome hides while you read,
  the app resumes exactly where you stopped, and page & chapter navigation is
  always one tap away.
- **Paper Studio** — eight reading surfaces (Warm Cream, Classic White,
  Vintage, Recycled, Parchment, Sepia, Soft Gray, Dark Reading Mode) with a
  live preview and three grain intensities.
- **Eye Comfort** — warm reading mode, custom page & text colours, font size
  and family, line spacing, margins and brightness controls.
- **Bookmarks & progress** — bookmark any page or paragraph, track reading
  progress per document, continue any book in one tap.
- **Brightness gesture** — optional vertical swipe along the left edge to
  adjust brightness, with a temporary indicator and sensitivity settings.
- **Document details** — cover, title, author, format, size, length, import
  and last-opened dates, progress and bookmark management.
- **Generated covers** — attractive fallback covers designed on-device for
  documents without embedded artwork.

## Supported document formats

| Format | Engine | Capabilities |
| --- | --- | --- |
| PDF | Android `PdfRenderer` | Smooth streamed page rendering, fit-width / whole-page, pinch zoom in & out (finger-anchored), double-tap & button zoom, pan, page navigation, bookmarks, dark rendering filter |
| EPUB 2/3 | Built-in EPUB engine | Package/spine parsing, chapter navigation, TOC titles, inline images, adjustable typography & paper |
| DOCX | Built-in OOXML reader | Paragraphs & headings via outline levels, bold/italic/underline, list items, simple tables |
| TXT | Built-in chunked loader | Encoding detection (UTF-8/16, GBK, Windows-1252), streamed paragraph loading for large files, full typography controls |

> DOCX is read, not rendered: complex Word layouts (text boxes, headers &
> footers, floating objects) are not reproduced. EPUB CSS styling is not
> applied — READR deliberately uses its own paper & typography for a
> consistent reading experience.

## Paper Studio

The reading screen uses a subtle, realistic paper grain texture by default —
**Warm Cream Paper at Low intensity is selected automatically on first
launch**. The texture is lightweight (one tiled asset, drawn behind the text
so glyphs stay perfectly sharp), and the selected paper applies to EPUB, DOCX
and TXT. PDF keeps its original appearance by default, with an optional dark
rendering filter for night reading.

## Technology stack

- **Kotlin** + **Jetpack Compose** + **Material 3**
- **MVVM** architecture with Kotlin Coroutines & Flow
- **Room** database for library, positions and bookmarks
- **DataStore** preferences for reading settings
- **Android Storage Access Framework** for imports
- Android **PdfRenderer** for PDF (no third-party PDF SDK)
- Hand-built EPUB (zip + XML pull parsing) and DOCX engines — chosen over
  Readium/Apache POI to keep the APK small, fully offline and under READR's
  own styling control
- Coil for cover images, kotlinx.serialization for saved positions

Project layout: `domain/model`, `data/db`, `data/prefs`, `data/repo`,
`engine/pdf`, `engine/epub`, `engine/docx`, `engine/txt`, `importer`,
`ui/*` (library, reader, paper, comfort, settings, details, components,
theme), `di`, `util`.

## Privacy

READR is offline-first by design:

- No mandatory login, no accounts.
- No advertisements, no analytics or tracking SDKs.
- No internet permission — the app physically cannot upload anything.
- Documents are copied into app-private storage; metadata and preferences
  stay local.
- Crash logs are written locally only if a crash occurs; nothing is sent
  automatically.

See [docs/PRIVACY.md](docs/PRIVACY.md) for the full statement.

## Installation

1. Download the latest APK from
   [Releases](https://github.com/hasib-insights/READR/releases/latest)
   (or build it yourself below).
2. Open the APK on your Android 7.0+ device and allow installing from that
   source when asked.
3. Tap **Import** in READR and pick a PDF, EPUB, DOCX or TXT file.

### Building from source

```bash
git clone https://github.com/hasib-insights/READR.git
cd READR
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:assembleRelease        # release APK (debug-signed unless
                                      # release signing properties are set)
```

Requirements: JDK 17 and Android SDK 35 (Android Studio does this
automatically). The output APK is written to
`app/build/outputs/apk/<variant>/`.

### Building from source is fully offline

The project uses only standard Google/Maven Central repositories and needs no
special credentials.

## Source code & contributing

The complete Android Studio project lives in this repository. Contributions
are welcome:

1. Fork the repository and create a branch.
2. Make your change with tests where practical (`app/src/test`).
3. Run `./gradlew :app:testDebugUnitTest` and open a pull request.

Good first issues: EPUB CSS-lite styling, PDF text selection, library
collections, more paper themes.

## Release history

- **1.0.2** (2026-11-20) — Paper Studio paper colour cards now respond to
  taps; the selection outline is drawn on top of the paper preview; choosing
  a paper clears any stale Eye Comfort custom colour in the same save.
  ([notes](docs/RELEASE_NOTES.md))
- **1.0.1** (2026-11-19) — back navigation fixes (gesture overlap, system
  back closes reader panels, guarded exit), custom page colours auto-pick a
  readable text colour (WCAG contrast ≥ 3:1), PDF pinch zoom out.
- **1.0.0** (2026-11-15) — first public release.

## License

Released under the [Apache License 2.0](LICENSE).
