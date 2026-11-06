@AGENTS.md

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

## graphify — reglas de este proyecto

Complementan la sección `## graphify` de arriba. Sobreviven a `graphify claude uninstall`;
no las borres al reinstalar.

**El grafo NO recoge cambios sin commitear.** El hook `post-commit` se alimenta de
`git diff HEAD~1 HEAD`: lo que está solo en el working tree o en staging es invisible para
el grafo. Si has tocado código en esta sesión y todavía no hay commit, ejecuta
`graphify update .` antes de responder preguntas de arquitectura — si no, el grafo te dará
una foto vieja del repo y la respuesta será incorrecta sin avisar.

**Los docs e imágenes nuevos tampoco entran solos.** El hook solo hace AST. Un `.md` nuevo
o modificado necesita `/graphify --update`, que sí consume tokens de LLM: pídelo de forma
explícita antes de gastarlos, no lo lances por tu cuenta.

**Vigilante en vivo (opcional).** Para cerrar el hueco del working tree sin depender del
commit: `python -m graphify.watch . --debounce 3` en una terminal aparte. Actualiza al
guardar. Solo cubre código; docs e imágenes siguen necesitando el update manual.

**No borres `graphify-out/.graphify_labels.json`.** Guarda los nombres de las comunidades
en castellano y los rebuilds los releen de ahí. Sin ese archivo las comunidades vuelven a
llamarse "Community 0", "Community 1"...

**`graphify-out/` está en .gitignore.** Es artefacto derivado y cada máquina reconstruye el
suyo con el hook. No lo commitees: el `graph.json` del backend pesa ~23 MB.

**Aquí los docs pesan más que en el frontend.** 71 archivos (contratos de `docs/api/`,
auditorías C-3…C-18, la spec de requisitos) aportan buena parte del valor del grafo. Si
documentas un módulo nuevo, el `/graphify --update` semántico deja de ser opcional.
