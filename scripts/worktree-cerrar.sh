#!/usr/bin/env bash
# Cierra el worktree de una rama YA FUSIONADA a master: borra el worktree (con su graphify-out/ y su node_modules), la rama
# local y las referencias muertas (arnés, 2026-11-06). Para no gastar disco: el de la laptop ya se llenó una vez.
#
#   scripts/worktree-cerrar.sh <rama>            cierra si es seguro; si no, dice por qué y no toca nada
#   scripts/worktree-cerrar.sh <rama> --probar   solo dice lo que haría
#
# NUNCA borra: un worktree con cambios sin commitear (o archivos nuevos sin agregar), una rama sin fusionar, una rama con
# commits que no están en origin, ni un worktree bloqueado (Claude Code bloquea el de un agente que sigue corriendo).
# «Fusionada» = `gh pr view <rama>` dice MERGED (sirve con squash), o la rama aparece en `git branch --merged origin/master`.
set -uo pipefail
RAMA=${1:-}; [[ -n $RAMA && $RAMA != -* ]] || { echo "Uso: $0 <rama> [--probar]" >&2; exit 2; }
PROBAR=0; [[ ${2:-} == --probar ]] && PROBAR=1
PRINCIPAL=$(cd "$(git rev-parse --git-common-dir)/.." && pwd)
cd "$PRINCIPAL"
no() { echo "NO se cierra «$RAMA»: $1" >&2; exit 1; }

RUTA=$(git worktree list --porcelain | awk -v r="refs/heads/$RAMA" '/^worktree /{w=substr($0,10)} $0=="branch "r{print w}')
[[ $RUTA == "$PRINCIPAL" ]] && no "es el checkout principal"
git fetch -q origin 2>/dev/null || true

fusionada=""
if command -v gh >/dev/null && [[ $(gh pr view "$RAMA" --json state -q .state 2>/dev/null) == MERGED ]]; then fusionada="PR fusionado (gh)"
elif git branch --merged origin/master --format='%(refname:short)' | grep -qxF "$RAMA"; then fusionada="git branch --merged origin/master"; fi
[[ -n $fusionada ]] || no "no está fusionada a master (ni PR MERGED ni git branch --merged). Si se abandonó, bórrala tú a mano."

if [[ -n $RUTA ]]; then
  [[ -d $RUTA ]] || { git worktree prune; RUTA=""; }
fi
if [[ -n $RUTA ]]; then
  git worktree list --porcelain | awk -v w="worktree $RUTA" '$0==w{f=1} f&&/^locked/{print; exit} /^$/{f=0}' | grep -q locked && no "el worktree está bloqueado (un agente lo está usando)"
  sucio=$(git -C "$RUTA" status --porcelain --ignore-submodules | grep -v -E '^\?\? (graphify-out/|node_modules$)' || true)
  [[ -z $sucio ]] || no "tiene cambios sin commitear en $RUTA:"$'\n'"$sucio"
fi
# Commits locales que no llegaron a origin (con squash, la rama de origin puede ya no existir: entonces se compara con el PR).
if git show-ref -q --verify "refs/remotes/origin/$RAMA"; then
  sin_subir=$(git log --oneline "origin/$RAMA..$RAMA" 2>/dev/null)
  [[ -z $sin_subir ]] || no "tiene commits que no están en origin/$RAMA:"$'\n'"$sin_subir"
fi

tam=""; [[ -n $RUTA ]] && tam=$(du -sh "$RUTA" 2>/dev/null | cut -f1)
echo "«$RAMA»: $fusionada. Worktree: ${RUTA:-(ninguno)} ${tam:+($tam)}"
if [[ $PROBAR == 1 ]]; then echo "(--probar) Borraría el worktree, su graphify-out/ y la rama local."; exit 0; fi
if [[ -n $RUTA ]]; then
  rm -rf "$RUTA/graphify-out"; [[ -L $RUTA/node_modules ]] && rm "$RUTA/node_modules"
  git worktree remove "$RUTA" || git worktree remove --force "$RUTA" || no "git worktree remove falló"
fi
git show-ref -q --verify "refs/heads/$RAMA" && git branch -D "$RAMA" >/dev/null && echo "Rama local borrada."
git worktree prune
echo "Cerrado. Liberado: ${tam:-0}."
