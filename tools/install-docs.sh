#!/usr/bin/env bash
#
# Installs MkDocs Material for tools/build-docs.sh, with the versions and hashes of the CI: the requirements.txt of the
# Litote/ci setup-mkdocs action, at the SHA pinned in the docs workflow (updated by Dependabot).
#
# Usage: tools/install-docs.sh (preferably in a Python virtual environment)

set -euo pipefail

cd "$(dirname "$0")/.."

sha=$(sed -n 's#.*Litote/ci/\.github/actions/setup-mkdocs@\([0-9a-f]\{40\}\).*#\1#p' .github/workflows/docs.yml | head -n 1)
if [ -z "$sha" ]; then
  echo "No Litote/ci/.github/actions/setup-mkdocs@<sha> in .github/workflows/docs.yml" >&2
  exit 1
fi

python3 -m pip install --disable-pip-version-check --only-binary ':all:' --require-hashes \
  -r "https://raw.githubusercontent.com/Litote/ci/${sha}/.github/actions/setup-mkdocs/requirements.txt"
