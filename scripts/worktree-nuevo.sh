#!/usr/bin/env bash
# Crea un worktree para una rama y lo deja listo para un agente: el GRAFO DE GRAPHIFY PRIMERO, después lo mínimo para
# trabajar (arnés, 2026-11-06).
#
#   scripts/worktree-nuevo.sh <rama> [--desde <base>]   (base por defecto: origin/master)
#
# Dónde queda: app → <checkout principal>/.claude/worktrees/<rama>; backend → <carpeta padre>/wt-<rama>
# (las mismas convenciones que ya usábamos). Si la rama existe (local o en origin) se usa; si no, se crea desde la base.
#
# Lo que hace, en orden:
#   1. Comprueba que queden ≥ 5 GB libres (el disco ya se llenó una vez, 02/11).
#   2. git worktree add (siempre desde el checkout principal, nunca desde otro worktree).
#   3. El grafo: scripts/grafo-al-dia.sh (graphify update, solo AST), para que el agente use `graphify query` en vez de
#      leer archivos sueltos. El hook de graphify no corre en worktrees: sin esto no habría grafo.
#   4. App: enlaza node_modules del checkout principal SOLO si su package-lock.json es idéntico (mismas versiones, y los
#      parches de postinstall ya aplicados). Si difiere, no enlaza: corre `npm ci` con pesado.sh (o dice cómo).
#      Backend: nada que instalar (Maven usa ~/.m2 compartido).
set -uo pipefail
# Los scripts hermanos se toman de donde está ESTE script: la base del worktree puede no tenerlos todavía.
AQUI=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
RAMA=${1:-}; [[ -n $RAMA && $RAMA != -* ]] || { echo "Uso: $0 <rama> [--desde <base>]" >&2; exit 2; }
BASE=origin/master
[[ ${2:-} == --desde && -n ${3:-} ]] && BASE=$3
PRINCIPAL=$(cd "$(git rev-parse --git-common-dir)/.." && pwd)
cd "$PRINCIPAL"
ES_APP=0; [[ -f package.json && -f app.json ]] && ES_APP=1
NOMBRE=${RAMA//\//-}
if [[ $ES_APP == 1 ]]; then DESTINO="$PRINCIPAL/.claude/worktrees/$NOMBRE"; else DESTINO="$(dirname "$PRINCIPAL")/wt-$NOMBRE"; fi
DESTINO=${WT_DESTINO:-$DESTINO}

libres=$(df -BG --output=avail "$(dirname "$DESTINO")" 2>/dev/null | tail -1 | tr -dc 0-9)
if [[ -n $libres && $libres -lt 5 ]]; then echo "Quedan ${libres} GB libres (< 5): no creo el worktree. Limpia con scripts/worktree-viejos.sh" >&2; exit 1; fi
[[ -e $DESTINO ]] && { echo "Ya existe $DESTINO: úsalo (o ciérralo con scripts/worktree-cerrar.sh $RAMA)" >&2; exit 1; }

git fetch -q origin 2>/dev/null || echo "(sin red: uso lo que hay localmente)"
if git show-ref -q --verify "refs/heads/$RAMA"; then git worktree add "$DESTINO" "$RAMA"
elif git show-ref -q --verify "refs/remotes/origin/$RAMA"; then git worktree add --track -b "$RAMA" "$DESTINO" "origin/$RAMA"
else git worktree add --no-track -b "$RAMA" "$DESTINO" "$BASE"; fi || exit 1

cd "$DESTINO"
echo "▶ Grafo de graphify (antes que nada)"
bash "$AQUI/grafo-al-dia.sh" --forzar || echo "AVISO: el grafo no quedó armado; el agente tendrá que leer archivos."

if [[ $ES_APP == 1 ]]; then
  if [[ -d $PRINCIPAL/node_modules ]] && cmp -s package-lock.json "$PRINCIPAL/package-lock.json"; then
    ln -s "$PRINCIPAL/node_modules" node_modules && echo "node_modules: enlazado al del checkout principal (mismo package-lock.json)"
  elif command -v npm >/dev/null; then
    echo "▶ package-lock.json distinto al del checkout principal: npm ci (con pesado.sh)"
    "$HOME/.config/renaser-e2e/herramientas/pesado.sh" npm ci || echo "AVISO: npm ci falló"
  else
    echo "AVISO: package-lock.json distinto y sin npm en el PATH: corre npm ci a mano (PATH=~/.local/hostnode-shim:\$PATH)."
  fi
fi
echo
echo "Listo: $DESTINO (rama $RAMA). Empieza con: graphify query \"<tu pregunta>\". Al fusionar: scripts/worktree-cerrar.sh $RAMA"
