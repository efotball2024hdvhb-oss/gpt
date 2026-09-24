#!/usr/bin/env sh
set -eu
VERSION=8.9
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
CACHE="$ROOT/.gradle-dist"
ZIP="$CACHE/gradle-$VERSION-bin.zip"
DIR="$CACHE/gradle-$VERSION"
if [ ! -x "$DIR/bin/gradle" ]; then
  mkdir -p "$CACHE"
  echo "Downloading Gradle $VERSION..."
  curl -L --fail --retry 3 -o "$ZIP" "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
  unzip -q -o "$ZIP" -d "$CACHE"
fi
exec "$DIR/bin/gradle" "$@"
