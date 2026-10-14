#!/usr/bin/env bash
# Construit l'installeur de l'app desktop pour l'OS courant (.dmg, .msi ou .deb)
# et le dépose sous un nom stable dans frontend/downloads/, servi par frontend/serve.ts
# sur /downloads/<nom>. Compose ne sait empaqueter que pour l'OS sur lequel il tourne :
# lancez ce script sur chaque OS (ou dans une CI matricielle).
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
out="${1:-$here/../frontend/downloads}"

case "$(uname -s)" in
  Darwin) task=packageDmg; ext=dmg; name=CodeShield-macos.dmg ;;
  Linux) task=packageDeb; ext=deb; name=CodeShield-linux.deb ;;
  MINGW*|MSYS*|CYGWIN*) task=packageMsi; ext=msi; name=CodeShield-windows.msi ;;
  *) echo "OS non pris en charge : $(uname -s)" >&2; exit 1 ;;
esac

# APP_VERSION=1.2.3 fixe la version de l'installeur (sinon celle de build.gradle.kts).
(cd "$here" && ./gradlew ":desktopApp:$task" ${APP_VERSION:+"-PappVersion=$APP_VERSION"})

built="$(find "$here/desktopApp/build/compose/binaries/main/$ext" -name "*.$ext" -type f | head -n 1)"
if [ -z "$built" ]; then
  echo "Aucun .$ext produit par :desktopApp:$task" >&2
  exit 1
fi

mkdir -p "$out"
cp "$built" "$out/$name"
echo "Installeur prêt : $out/$name"
