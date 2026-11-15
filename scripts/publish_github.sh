#!/usr/bin/env bash
# One-command publishing helper. Run on YOUR machine (or the build sandbox)
# after authenticating with: gh auth login
#
#   1. creates the public repo if missing
#   2. pushes main
#   3. creates GitHub Release v1.0.0 with READR.apk (uses the local APK if present)
#   4. verifies the permanent download link
#   5. activates the README download section
set -euo pipefail

OWNER="hasib-insights"
REPO="READR"
TAG="${1:-v1.0.0}"
SLUG="$OWNER/$REPO"

command -v gh >/dev/null || { echo "Install the GitHub CLI first: https://cli.github.com"; exit 1; }
gh auth status >/dev/null || { echo "Run 'gh auth login' first."; exit 1; }

echo "== 1/5 Repository =="
if gh repo view "$SLUG" >/dev/null 2>&1; then
  echo "Repository $SLUG already exists — keeping its contents."
else
  gh repo create "$REPO" --public \
    --description "READR – A Better Way to Read. Offline-first Android e-book & document reader."
fi

echo "== 2/5 Push source =="
git remote remove origin 2>/dev/null || true
git remote add origin "https://github.com/$SLUG.git"
git push -u origin HEAD:main

echo "== 3/5 Release =="
APK=""
for candidate in READR.apk ../READR.apk ../../download/READR.apk; do
  [ -f "$candidate" ] && APK="$candidate" && break
done
if [ -z "$APK" ]; then
  echo "No READR.apk found locally — pushing tag $TAG so GitHub Actions builds and publishes it."
  git tag "$TAG" 2>/dev/null || true
  git push origin "$TAG"
else
  cp "$APK" ./READR.apk
  sha256sum READR.apk > READR.apk.sha256 || shasum -a 256 READR.apk > READR.apk.sha256
  gh release create "$TAG" READR.apk READR.apk.sha256 \
    --title "READR ${TAG#v}" --notes-file docs/RELEASE_NOTES.md || \
    gh release upload "$TAG" READR.apk READR.apk.sha256 --clobber
fi

echo "== 4/5 Verify download link =="
sleep 5
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -L \
  "https://github.com/$SLUG/releases/latest/download/READR.apk")
if [ "$STATUS" = "200" ]; then
  echo "Direct APK link verified (HTTP 200)."
else
  echo "WARNING: link returned HTTP $STATUS — check https://github.com/$SLUG/releases"
fi

echo "== 5/5 Activate README download section =="
python3 scripts/activate_download_section.py README.md
git add README.md
git commit -m "Publish download links for $TAG" || true
git push

echo "DONE — repository: https://github.com/$SLUG"
