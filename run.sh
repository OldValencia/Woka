#!/usr/bin/env bash
# Launch Woka without the IDE: ./run.sh
#
# Runs the app straight from source through the Gradle wrapper, so nothing is
# installed and nothing is packaged — the first start compiles what changed, the
# next ones reuse the Gradle daemon and are quick. Close the window to stop it.
#
# Anything passed here goes on to Gradle, so `./run.sh --info` or
# `./run.sh -Dsomething=1` work. A file to open goes through the app's own CLI
# argument instead: ./run.sh --args="D:/Movies/film.mkv"
set -euo pipefail

# Run from the project root whatever directory the script was called from.
cd "$(dirname "$0")"

exec ./gradlew.bat :desktopApp:run "$@"
