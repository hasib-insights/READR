# Privacy

READR is built offline-first. This page explains exactly what the app does and
does not do with your data.

## What READR does NOT do

- **No internet access.** The app does not request the `INTERNET` permission.
  It is technically impossible for READR to upload anything or talk to any
  server.
- **No accounts or login.**
- **No advertising, no analytics, no tracking SDKs.**
- **No automatic cloud backups of your documents.** Standard Android device
  backups may include app-private files; you can disable that in your device
  settings.

## What READR stores on your device

- **Imported copies** of the documents you add, inside the app's private
  storage (`files/documents`). The original files you pick are never modified.
- **A local Room database** with library metadata: title, author, format,
  file size, import date, last opened date, reading progress and saved
  positions.
- **Generated cover images** inside `files/covers`.
- **Preferences** (paper theme, typography, gestures, library settings) in a
  local DataStore file.
- **Local crash logs** (only if a crash happens) inside `files/logs`. These
  never leave the device unless you choose to share them yourself.

## Removing your data

Removing a document from the library deletes its imported copy, its cover and
its bookmarks. Uninstalling the app removes everything READR has stored.
