#!/usr/bin/env bash
#
# Regenerates gradle/verification-metadata.xml after a dependency change, with the tasks run by the CI.
#
# The script starts Gradle itself (a Gradle task could not start when a plugin of the build is not verified yet),
# with an empty Gradle user home: the parent POMs and BOMs found in a cache are not recorded, and the CI, which
# downloads them, would reject them.
#
# Usage: tools/update-verification-metadata.sh

set -euo pipefail

cd "$(dirname "$0")/.."

export GRADLE_USER_HOME="$PWD/.gradle/verification-metadata-home"
rm -rf "$GRADLE_USER_HOME"
trap 'rm -rf "$GRADLE_USER_HOME"' EXIT

./gradlew --no-daemon --write-verification-metadata sha256 --no-configuration-cache \
  build dokkaGenerate jacocoAggregatedReport publishToMavenLocal
