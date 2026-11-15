# Publishing READR to GitHub

The repository is published with the official GitHub OAuth Device Flow
(the same flow GitHub's CLI uses). A helper for sandbox environments is
`/home/z/my-project/scripts/github_publish.sh`; for your own machine:

1. Install and authenticate the GitHub CLI once:
   gh auth login
2. bash scripts/publish_github.sh

The script creates the public repository (if missing), pushes main, creates
Release v1.0.0 with READR.apk + SHA-256, verifies the permanent download
link, and activates the README download section.
