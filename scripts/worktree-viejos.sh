#!/usr/bin/env bash
# Lista los worktrees de este repo y dice cuáles se pueden cerrar (arnés, 2026-11-06). Solo lee: no borra nada.
#
#   scripts/worktree-viejos.sh          tabla: rama, ¿fusionada?, ¿cambios?, tamaño, último commit
#   scripts/worktree-viejos.sh --solo-cerrables   solo las ramas que scripts/worktree-cerrar.sh cerraría
#
# Para cerrar: scripts/worktree-cerrar.sh <rama> (una por una; revisa antes lo que no esté fusionado).
set -uo pipefail
PRINCIPAL=$(cd "$(git rev-parse --git-common-dir)/.." && pwd)
cd "$PRINCIPAL"
SOLO=0; [[ ${1:-} == --solo-cerrables ]] && SOLO=1
git fetch -q origin 2>/dev/null || true
FUSIONADAS=$(git branch --merged origin/master --format='%(refname:short)')
PRS=""; command -v gh >/dev/null && PRS=$(gh pr list --state merged --limit 300 --json headRefName -q '.[].headRefName' 2>/dev/null)
[[ $SOLO == 0 ]] && printf '%-42s %-10s %-9s %7s  %s\n' RAMA FUSIONADA CAMBIOS TAMAÑO "ÚLTIMO COMMIT"
total=0
git worktree list --porcelain | awk '/^worktree /{w=substr($0,10)} /^branch /{print w"\t"substr($0,19)} /^detached/{print w"\t(detached)"}' |
while IFS=$'\t' read -r ruta rama; do
  [[ $ruta == "$PRINCIPAL" ]] && continue
  if [[ ! -d $ruta ]]; then [[ $SOLO == 0 ]] && printf '%-42s %s\n' "$rama" "carpeta borrada: git worktree prune"; continue; fi
  fus=no; { grep -qxF "$rama" <<<"$FUSIONADAS" || grep -qxF "$rama" <<<"$PRS"; } && fus=sí
  cam=$(git -C "$ruta" status --porcelain | grep -v -E '^\?\? (graphify-out/|node_modules$)' | wc -l)
  tam=$(du -sh "$ruta" 2>/dev/null | cut -f1)
  fecha=$(git -C "$ruta" log -1 --format=%cs 2>/dev/null)
  if [[ $SOLO == 1 ]]; then [[ $fus == sí && $cam == 0 ]] && echo "$rama"; continue; fi
  printf '%-42s %-10s %-9s %7s  %s%s\n' "${rama:0:42}" "$fus" "$cam" "$tam" "$fecha" "$([[ $fus == sí && $cam == 0 ]] && echo '  ← se puede cerrar')"
done
