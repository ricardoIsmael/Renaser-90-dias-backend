#!/usr/bin/env bash
# Hook SessionStart de Claude Code (.claude/settings.json): le dice al agente dónde está parado y que use el grafo antes
# que leer archivos (arnés, 2026-11-06). Solo LEE; nunca falla la sesión. A mano: bash scripts/inicio-de-sesion.sh
cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/..}" 2>/dev/null || exit 0
rama=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo '?')
sucios=$(git status --porcelain 2>/dev/null | grep -vc '^?? graphify-out/')
wt=""; git rev-parse --git-dir 2>/dev/null | grep -q /worktrees/ && wt=" (worktree)"
grafo=$(bash scripts/grafo-al-dia.sh --estado 2>/dev/null || true)
siguiente=$(awk '/^## Siguiente paso/{f=1;next} /^## /{f=0} f' PROGRESO.md 2>/dev/null | tr '\n' ' ' | sed 's/  */ /g' | cut -c1-400)
cat <<TXT
Estado al abrir la sesión (scripts/inicio-de-sesion.sh; detalle en PROGRESO.md):
- Rama: ${rama}${wt} · ${sucios} archivos con cambios sin commitear (no los mezcles con tu trabajo)
- Grafo: ${grafo:-graphify no disponible}. Navega con graphify query/path/explain ANTES de abrir archivos.
- Siguiente paso: ${siguiente:-(PROGRESO.md no lo dice)}
- Un PR no está listo sin scripts/verificar.sh en 0, el E2E de su spec y los logs del backend y logcat sin errores nuevos.
TXT
exit 0
