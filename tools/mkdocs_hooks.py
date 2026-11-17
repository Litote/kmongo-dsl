"""MkDocs hooks of the documentation site (declared in mkdocs.yml).

Replaces ``{{ kmongo_dsl_version }}`` in the pages by the last released version, read from
.release-please-manifest.json: release-please updates the manifest in the release PR only (the snapshot PR does not
change it), so the site always shows the published version, never a -SNAPSHOT one.
"""

import json
from pathlib import Path

PLACEHOLDER = "{{ kmongo_dsl_version }}"

_MANIFEST = Path(__file__).resolve().parent.parent / ".release-please-manifest.json"
_version: str | None = None


def on_config(config, **kwargs):
    global _version
    version = json.loads(_MANIFEST.read_text(encoding="utf-8")).get(".")
    if not version or "SNAPSHOT" in version:
        raise ValueError(f"invalid released version in {_MANIFEST}: {version!r}")
    _version = version
    return config


def on_page_markdown(markdown, page, config, files, **kwargs):
    return markdown.replace(PLACEHOLDER, _version)
