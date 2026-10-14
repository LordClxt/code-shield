#!/usr/bin/env bash
# Récupère les installeurs construits par la CI (workflow « Desktop installers »)
# dans frontend/downloads/, servi par serve.ts sur /downloads/<nom>.
# Usage : ./scripts/fetch-installers.sh [id-du-run]   (défaut : dernier run réussi sur main)
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
out="$here/downloads"
run="${1:-$(gh run list --workflow desktop-installers.yml --branch main --status success --limit 1 --json databaseId -q '.[0].databaseId')}"
if [ -z "$run" ]; then
  echo "Aucun run réussi du workflow Desktop installers." >&2
  exit 1
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
gh run download "$run" --dir "$tmp"

mkdir -p "$out"
for name in CodeShield-macos.dmg CodeShield-windows.msi CodeShield-linux.deb; do
  f="$(find "$tmp" -name "$name" -type f | head -n 1)"
  if [ -n "$f" ]; then
    mv "$f" "$out/$name"
    echo "✓ $name"
  else
    echo "✗ $name absent du run $run" >&2
  fi
done
echo "Installeurs du run $run dans $out"
