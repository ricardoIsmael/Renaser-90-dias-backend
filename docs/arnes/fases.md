# Las 14 fases del arnés en los dos repositorios

El arnés de agentes es de Ricardo y tiene un **núcleo genérico** fuera de estos repositorios (`~/Documentos/arnes`): el
grafo del flujo de agentes, los bucles con su presupuesto, el validador, las plantillas de Claude Code, un documento por
cada lección del curso [*learn-harness-engineering*](https://github.com/walkinglabs/learn-harness-engineering) y las
fuentes (`docs/fuentes.md` del núcleo: revistas y congresos revisados, preprints y documentación de Anthropic).

Estos dos repositorios son **perfiles** del núcleo: `arnes.perfil.yaml` en cada uno dice cómo se hace aquí cada nodo
(comandos de verificar y E2E, rutas, reglas, presupuestos). `scripts/verificar.sh` (aquí) y `npm run verificar` (app)
corren `arnes validar --perfil arnes.perfil.yaml`.

| Fase | Lección | Qué hacemos aquí | Archivo o script | Hueco que había y cómo se cerró |
|---|---|---|---|---|
| 01 | Los modelos fuertes no significan ejecución fiable | Cada error se atribuye a una familia (contexto, verificación, estado…) y la familia que se repite se vuelve chequeo | `docs/arnes/familias-de-errores.md`, `docs/BITACORA_ERRORES.md`, `scripts/arnes-familias.test.mjs` | Los errores se anotaban sin agruparse; ahora cada familia tiene su chequeo |
| 02 | Qué es un arnés | Núcleo genérico + un perfil por repositorio | `arnes.perfil.yaml` (los dos repos) | El arnés estaba mezclado con el proyecto; se separó para reutilizarlo |
| 03 | El repositorio como fuente de verdad | Specs, decisiones (D-NN), bitácora (E-NN) y progreso versionados; el estado de una tarea en `.arnes/` del worktree | `specs/`, `docs/`, `PROGRESO.md`, `.gitignore` (`.arnes/`) | El estado de la tarea no tenía lugar; ahora es por worktree y no choca entre ramas |
| 04 | Instrucciones divididas | `AGENTS.md` como mapa corto; reglas por tema en `.claude/rules/` con `paths:` | `AGENTS.md` (≤ 150, `scripts/arnes.test.mjs`), `.claude/rules/` | `CLAUDE.MD` de 1 046 líneas; ahora un mapa con prueba que lo frena |
| 05 | Continuidad entre sesiones | Archivo de progreso y hook de inicio | `PROGRESO.md`, `scripts/inicio-de-sesion.sh` | La sesión nueva no sabía qué quedó a medias |
| 06 | Inicialización como fase | Worktree con el grafo de código armado antes que nada | `scripts/worktree-nuevo.sh` | Cada worktree se preparaba a mano |
| 07 | Límites de la tarea (WIP=1) | Una funcionalidad por rama, worktree y commit; el núcleo no abre dos tareas en una carpeta | regla 00, `arnes iniciar` | Se mezclaban cambios ajenos |
| 08 | Lista de funcionalidades | Los requisitos `FR-NNN` / `SC-NNN` de cada spec exigida con su prueba marcada | `specs/trazabilidad.json`, `scripts/trazabilidad-specs.mjs` | Un requisito sin prueba pasaba inadvertido; ahora la trazabilidad falla |
| 09 | No declarar victoria | `verificar.sh` sale con 2 si algo quedó sin correr; guarda de E-111 (cero pruebas) | `scripts/verificar.sh` | «Verde» con la suite sin correr (E-111) |
| 10 | E2E | El PR exige el E2E de su spec en el emulador contra el backend local | `docs/arnes/README.md` §2, nodo `e2e` del grafo | El E2E era opcional en la práctica |
| 11 | Observabilidad | Logs del backend y de logcat revisados en cada PR; bitácora de cada paso del grafo en `.arnes/bitacora.jsonl` | nodo `logs`, núcleo `bin/arnes.mjs` | No quedaba rastro de las vueltas de una tarea |
| 12 | Estado limpio al cerrar | Cerrar worktree, rama y grafo de código al fusionar | `scripts/worktree-cerrar.sh`, `scripts/worktree-viejos.sh`, `scripts/grafo-al-dia.sh` | Worktrees fusionados se quedaban ocupando disco |
| 13 | Loop engineering | Cada bucle con parada medible, tope de intentos y minutos, y salida a «pedir ayuda»; aquí la verificación tiene 240 min (la suite tarda ~45) | `arnes.perfil.yaml` (`presupuestos`), núcleo `grafo/grafo.yaml` | Los reintentos no tenían tope |
| 14 | Graph engineering | El grafo del flujo (spec → aclarar → plan → tareas → implementar ⇄ verificar → E2E → logs → PR → aprobación del dueño → fusión → cerrar) se valida en cada verificación | núcleo `grafo/grafo.yaml` y `arnes validar` en `scripts/verificar.sh` | El ciclo era una tabla en un documento; ahora es un archivo validado y recorrible |

**graphify** (el grafo de **código**) se conserva como herramienta de búsqueda: `graphify query`, `path` y `explain`
antes de abrir archivos. No es el grafo del flujo de agentes de la lección 14.
