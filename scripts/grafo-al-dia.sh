#!/usr/bin/env bash
# Pone al día el grafo de graphify de ESTE checkout (principal o worktree) si quedó atrás del código (arnés, 2026-11-06).
#
#   scripts/grafo-al-dia.sh            actualiza solo si hace falta (sin grafo, o código más nuevo que el grafo)
#   scripts/grafo-al-dia.sh --forzar   actualiza siempre
#   scripts/grafo-al-dia.sh --estado   solo dice si está al día (sale 0 al día, 3 atrasado, 4 sin grafo); no toca nada
#
# Por qué: el hook post-commit de graphify sale sin hacer nada dentro de un worktree (compara git-dir con git-common-dir),
# y `graphify-out/` no está en git: un worktree nace sin grafo y nunca se actualiza solo. `graphify update` es solo AST
# (sin costo de LLM). Va con pesado.sh: en el backend tarda y usa memoria.
set -uo pipefail
RAIZ=$(git rev-parse --show-toplevel 2>/dev/null) || { echo "No es un repo de git" >&2; exit 2; }
cd "$RAIZ"
SELLO=graphify-out/.commit-del-grafo
MODO=${1:-}
PESADO=${PESADO:-$HOME/.config/renaser-e2e/herramientas/pesado.sh}
[[ -x $PESADO ]] || PESADO=""

estado() {
  [[ -f graphify-out/graph.json ]] || return 4
  [[ -f $SELLO ]] || return 3
  local sello; sello=$(cat "$SELLO")
  git cat-file -e "$sello^{commit}" 2>/dev/null || return 3
  # Atrasado si hay commits nuevos o cambios sin commitear en código desde el sello.
  [[ -z $(git diff --name-only "$sello" -- . ':!graphify-out' ':!*.md' 2>/dev/null) ]] || return 3
  return 0
}

if [[ $MODO == --estado ]]; then
  estado; e=$?
  case $e in 0) echo "Grafo al día (commit $(cat $SELLO | cut -c1-8))";; 3) echo "Grafo ATRASADO: corre scripts/grafo-al-dia.sh";; 4) echo "Sin grafo: corre scripts/grafo-al-dia.sh";; esac
  exit $e
fi
if [[ $MODO != --forzar ]] && estado; then echo "Grafo al día: no hace falta actualizar."; exit 0; fi

if ! command -v graphify >/dev/null; then echo "graphify no está instalado (pip install graphifyy): el agente leerá archivos sueltos." >&2; exit 2; fi
mkdir -p graphify-out
# Los nombres de las comunidades en castellano viven en .graphify_labels.json del checkout principal: sin él las
# comunidades vuelven a llamarse «Community 0» (CLAUDE.md). Y su caché acelera la primera construcción (reflink en btrfs).
PRINCIPAL=$(cd "$(git rev-parse --git-common-dir)/.." && pwd)
if [[ $PRINCIPAL != "$RAIZ" && -d $PRINCIPAL/graphify-out ]]; then
  [[ -f graphify-out/.graphify_labels.json || ! -f $PRINCIPAL/graphify-out/.graphify_labels.json ]] || cp "$PRINCIPAL/graphify-out/.graphify_labels.json" graphify-out/
  [[ -d graphify-out/cache || ! -d $PRINCIPAL/graphify-out/cache ]] || cp -r --reflink=auto "$PRINCIPAL/graphify-out/cache" graphify-out/
fi
echo "▶ graphify update . (solo AST)"
inicio=$(date +%s)
PYTHONHASHSEED=0 ${PESADO:+$PESADO} graphify update . >/tmp/grafo-al-dia.$$.log 2>&1
e=$?
tail -3 /tmp/grafo-al-dia.$$.log; rm -f /tmp/grafo-al-dia.$$.log
if [[ $e -ne 0 || ! -f graphify-out/graph.json ]]; then echo "graphify update falló (salida $e)" >&2; exit 1; fi
git rev-parse HEAD > "$SELLO"
echo "Grafo listo en $(( $(date +%s) - inicio )) s ($(du -sh graphify-out | cut -f1)). Úsalo: graphify query \"…\""
