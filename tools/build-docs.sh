#!/usr/bin/env bash
#
# Builds the documentation site (build/site): the MkDocs pages of docs/ + the Dokka API documentation (api/).
#
# Usage: tools/build-docs.sh [--skip-dokka]
#   requires MkDocs Material: pip install -r requirements-docs.txt
#   preview: mkdocs serve (after a first build, for the API documentation)

set -euo pipefail

cd "$(dirname "$0")/.."

if [ "${1:-}" != "--skip-dokka" ]; then
  ./gradlew :dokkaGenerate
fi
# the API documentation is copied in the docs (gitignored): MkDocs checks the links to the API pages
rm -rf docs/api
cp -r build/dokka/html docs/api

mkdocs build --strict
echo "Documentation site: build/site/index.html"
