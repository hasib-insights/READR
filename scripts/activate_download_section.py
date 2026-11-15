#!/usr/bin/env python3
"""Activates the commented download section in README.md after the first
release has been published. Usage: python3 activate_download_section.py [README.md]"""
import sys
import pathlib

def main() -> int:
    path = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "README.md")
    text = path.read_text(encoding="utf-8")
    if "<!-- DOWNLOAD_SECTION_BEGIN" not in text:
        print("Download section is already active (or README has no marker). Nothing to do.")
        return 0
    text = text.replace("<!-- DOWNLOAD_SECTION_BEGIN", "", 1)
    text = text.replace("DOWNLOAD_SECTION_END -->", "", 1)
    path.write_text(text, encoding="utf-8")
    print(f"Download section activated in {path}. Commit and push to publish it.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
