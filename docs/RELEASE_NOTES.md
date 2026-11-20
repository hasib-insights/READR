# READR 1.0.2 — bug-fix release

One fix, no new features and no design changes.

## Paper selection fixed (Paper Studio & Reading Room)
- The paper colour cards in **Paper Studio** — and in the reader's Paper
  Studio sheet — did not respond to taps at all: the tap handler was never
  connected to the paper previews, so every card looked like a button but
  behaved as a static picture. Tapping a paper now applies it immediately:
  the live preview recolours, the selection outline moves, and the choice is
  saved across pages and sessions.
- Selecting a paper also clears any custom page colour picked earlier in Eye
  Comfort (in the same atomic save), so a stale custom colour can no longer
  silently override the paper you just tapped.
- The selection outline is now drawn **on top of** the paper preview instead
  of underneath it, so the selected paper is always clearly highlighted.

The texture-intensity chips, font chips and all other Paper Studio controls
were already working and are unchanged.

## Files

- `READR.apk` — signed release APK (Android 7.0+). Signed with the same
  release key as 1.0.1, so it installs as a normal update (only 1.0.0 needs
  a one-time uninstall first).
- `READR.apk.sha256` — checksum to verify your download

---

# READR 1.0.1 — bug-fix release

Three fixes, no new features and no design changes:

## Back button fixed
- The left-edge brightness gesture no longer sits on top of the reader's back
  button, so the in-app back arrow responds reliably again.
- System back now closes the reader's settings/chapter panels first, then
  returns to the previous screen as before.
- On the home screen, back closes the search panel if it is open; otherwise a
  second press within two seconds exits — a stray press never drops you out
  of the app unexpectedly.

## Page colour selection fixed
- Previously, a custom page colour chosen in Eye Comfort silently overrode
  every later paper selection, so tapping papers in the Reading Room or Paper
  Studio appeared to do nothing. Selecting a paper now always applies that
  colour immediately (and clears the older custom colour), across pages and
  sessions.
- When you pick a custom page colour whose paper is too dark or too light for
  the current text, READR automatically picks the readable text colour from
  the palette (WCAG contrast ≥ 3:1), so the page stays legible.

## PDF zoom-out fixed
- PDFs now support real pinch-to-zoom in AND out (before, only double-tap and
  the toolbar buttons worked).
- Zooming fully out snaps the page back to fit-width and smoothly re-enables
  page scrolling — previously the page could stay stuck in a shifted position.
- Pinch zoom is anchored between your fingers, the page can never be dragged
  out of the viewport while zoomed, and page navigation via swipe or the page
  slider keeps working.

## Files

- `READR.apk` — signed release APK (Android 7.0+). **Note:** this build is
  signed with a new release key (the original keystore was lost in a build
  machine reset), so updating from 1.0.0 requires uninstalling 1.0.0 first.
- `READR.apk.sha256` — checksum to verify your download

---

# READR 1.0.0 — first public release

READR is a beautiful, lightweight, offline-first e-book and document reader
for Android. **A Better Way to Read.**

## Highlights

- Personal digital bookshelf: Continue Reading, Favorites, recents, search,
  sorting and format filtering, grid & list views.
- Reading Room: distraction-free reader with resume-from-last-position,
  bookmarks, progress and adjustable typography.
- Paper Studio: Warm Cream, Classic White, Vintage, Recycled, Parchment,
  Sepia, Soft Gray and Dark Reading Mode — with a subtle paper grain texture
  behind the text (Low / Medium / High intensity). Warm Cream at Low grain is
  the default from the very first launch.
- Eye Comfort: warm reading mode, custom page and text colours, brightness
  controls and an optional left-edge brightness gesture.
- Four formats, real engines: PDF (streamed PdfRenderer), EPUB 2/3, DOCX and
  TXT with encoding detection.
- Fully offline: no login, no ads, no analytics, no internet permission.

## Files

- `READR.apk` — signed release APK (Android 7.0+)
- `READR.apk.sha256` — checksum to verify your download
