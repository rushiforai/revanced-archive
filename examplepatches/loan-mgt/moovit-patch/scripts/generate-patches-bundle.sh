#!/bin/bash
set -euo pipefail

VERSION="${1:?version required}"
NOTES_FILE="${2:?release notes file required}"
REPO="${3:?owner/repo required}"

TIMESTAMP="$(date -u +%Y-%m-%dT%H:%M:%S)"
NOTES_JSON=$(jq -Rs '.' < "$NOTES_FILE")

cat > /tmp/patches-bundle.json <<EOF
{
  "version": "$VERSION",
  "description": $NOTES_JSON,
  "created_at": "$TIMESTAMP",
  "download_url": "https://github.com/${REPO}/releases/download/v${VERSION}/patches-${VERSION}.rvp"
}
EOF
