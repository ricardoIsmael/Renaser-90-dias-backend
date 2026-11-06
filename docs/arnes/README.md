# El arnés de Renaser: cómo trabaja un agente en los dos repos

Fecha: 2026-11-06. El arnés es todo lo que rodea al modelo: lo que lee al empezar, cómo navega el código, cómo verifica y
qué lo frena. Este documento junta lo que la comunidad recomienda en 2026, lo que aplicamos y por qué. El mapa corto
para cada sesión sigue siendo `AGENTS.md`; esto es el porqué.

## 1. Lo que recomienda la comunidad en 2026 (y lo que tomamos)

| Práctica | Fuente | Qué hicimos |
|---|---|---|
| `AGENTS.md` como mapa corto, escrito a mano: contexto, comandos, límites; lo largo en documentos enlazados | Osmani, HumanLayer, ignorance.ai | Ya hecho: ≤ 150 líneas (backend) y ≤ 200 (app), con prueba que lo frena |
| Cada error del agente se vuelve una señal duradera: si no sabía algo, va a `AGENTS.md`; si se repite, va a un chequeo o un hook | Osmani, Endor Labs | `docs/arnes/familias-de-errores.md` + `scripts/arnes-familias.test.mjs` (y su par en la app) |
| Restricciones duras además del texto (hooks, pruebas de arquitectura) | Endor Labs, tianpan.co | ArchUnit, chequeos del arnés, `verificar.sh` con la guarda de E-111 |
| Archivo de progreso y artefactos de traspaso entre sesiones; un evaluador separado porque el agente sobrevalora su trabajo | Anthropic (vía InfoQ y resúmenes) | `PROGRESO.md` + hook de inicio; el E2E y la revisión de logs como evaluación externa |
| Un worktree por tarea, creado desde el checkout principal; limpiar al fusionar; vigilar el disco | DEV (dublecc, htekdev), MindStudio, KDnuggets | `scripts/worktree-nuevo.sh`, `worktree-cerrar.sh`, `worktree-viejos.sh` |
| Repartir el trabajo por funcionalidad, no por archivo, para que dos agentes no choquen | MindStudio, DEV | Una rama por funcionalidad (regla 00); chequeo de beans duplicados (E-29) |
| Un grafo de código para no releer archivos; medir el ahorro en el repo propio, no creer la cifra de marketing | AWS Builder Center, graphify.com, MindStudio | Grafo primero en cada worktree; medición abajo (§4) |
| SDD: la spec es la fuente de verdad («spec-anchored»), y la observación del sistema corriendo cierra el ciclo | arXiv 2602.00180, devgenius (ODD) | Seguimos con spec-kit; el E2E + logs es nuestra mitad «observar» (§2) |

Advertencia honesta: casi todas son entradas de blog o de proveedores; las cifras (por ejemplo «70× menos tokens» o
«+4 puntos con AGENTS.md escrito a mano») no tienen un estudio independiente. Por eso medimos lo nuestro.

## 2. Metodología: seguimos con spec-kit, en modo «anclado a la spec»

**Recomendación: spec-kit**, con dos ajustes. Comparado con las alternativas de 2026:

- **Kiro** es un IDE pago de AWS: obliga a cambiar de herramienta y no aporta nada que spec-kit no haga.
- **BMAD** simula un equipo de agentes; es la opción más pesada (un reporte la midió en horas donde OpenSpec tardó minutos).
  Para un equipo chico es ceremonia de más.
- **OpenSpec** es liviano y orientado a cambios; es la alternativa razonable, pero migrar 40+ specs, `trazabilidad.json` y
  el script de trazabilidad no se paga: lo que nos falta de OpenSpec (un camino corto para cambios chicos) se agrega a
  spec-kit sin migrar.
- **ODD (desarrollo guiado por la observación)** existe como práctica en 2026 pero no reemplaza a SDD: es «la otra mitad
  del ciclo». Lo adoptamos como paso, no como metodología: el E2E y la revisión de logs del backend y de logcat.

Los dos ajustes:

1. **Camino corto** para un bug o un cambio chico autorizado: issue → prueba que falla → arreglo → verificar → PR. No
   lleva spec; su rastro es el issue, la bitácora y `docs/decisiones/` de la app si toca un tab.
2. **Observar como paso obligatorio**: un PR no está listo sin el E2E de su spec y los logs del backend y de logcat
   revisados, sin errores nuevos.

### El ciclo completo de una funcionalidad

| Paso | Qué se hace | Skill / comando | Sale |
|---|---|---|---|
| 1. Spec | Qué y por qué, en palabras del dueño; requisitos `FR-NNN` y criterios `SC-NNN` | `speckit-specify` | `specs/NNN-…/spec.md` |
| 2. Preguntas al dueño | Lo ambiguo se pregunta (hasta 5), con opciones y la recomendada primero; **no se inventan reglas de negocio** | `speckit-clarify` | decisiones en la spec y `D-NN` |
| 3. Plan | Diseño técnico, contratos de la API, migraciones, qué tab se toca (y si está autorizado) | `speckit-plan` | `plan.md`, `contracts/` |
| 4. Tareas | Lista ordenada, una funcionalidad por rama | `speckit-tasks`, `speckit-analyze` | `tasks.md` |
| 5. Worktree | Rama y worktree propios, grafo armado | `scripts/worktree-nuevo.sh <rama>` | worktree listo |
| 6. Implementación | Prueba primero; cada `FR`/`SC` con su marca `spec NNN FR-NNN` | `speckit-implement` | código + pruebas |
| 7. Verificar | Todo en verde, sin pasos sin correr | `scripts/verificar.sh`, `npm run verificar` | resumen para el PR |
| 8. E2E y logs | Maestro y mobile-mcp en el emulador contra el backend local; logs del backend y `adb logcat` sin errores nuevos | `npm run verificar -- --e2e` | capturas y logs |
| 9. PR | Resumen de la verificación, el E2E y lo que no se pudo verificar; `Closes #N` | `gh pr create` | PR |
| 10. Cerrar | Al fusionar: borrar worktree, rama y grafo | `scripts/worktree-cerrar.sh <rama>` | disco libre |

## 3. Un arnés por módulos: cargar solo lo que hace falta

Siempre se carga: `AGENTS.md` (mapa) y las reglas generales. El resto se carga **según lo que se toca**:

| Si tocas… | Lee | Pregúntale al grafo |
|---|---|---|
| Una capa del backend (`domain/`, `application/`, `adapter/`) | `.claude/rules/01-arquitectura-hexagonal.md` | `graphify explain "<Clase>"` |
| Fechas, días del programa, schedulers | `.claude/rules/02-tiempo-zonas-y-schedulers.md` | `graphify query "zona de la persona <tema>"` |
| Migraciones (`db/migration/`) | `.claude/rules/04-…` (Claude Code la carga sola al tocar esa carpeta: `paths:`) | — |
| Una spec exigida o sus pruebas | `.claude/rules/06-…` (se carga sola con `specs/` y `src/test/`) | — |
| Un módulo del backend (`habits`, `chat`, `rocks`…) | `docs/MODULO_<NOMBRE>.md` y su spec en `specs/` | `graphify query "<módulo> <tema>"` |
| Un tab de la app | `../Renaser-90-dias-frontend-/docs/decisiones/<tab>.md` | en la app: `graphify query "<Pantalla> <tema>"` |
| Un error conocido | `docs/BITACORA_ERRORES.md` (busca el texto literal) y `docs/arnes/familias-de-errores.md` | — |

## 4. Grafo de código primero (búsqueda con graphify)

El grafo de graphify (un grafo del **código**, no el grafo del flujo de agentes de §5) es la **primera** fuente de navegación: `graphify query`, `path` y `explain` antes que abrir
archivos. Para que eso sea cierto en cualquier carpeta:

- `scripts/worktree-nuevo.sh` arma el grafo **antes que nada** (copia la caché y los nombres de comunidades del checkout
  principal, con reflink en btrfs, y corre `graphify update .`, solo AST, con `pesado.sh`).
- `scripts/grafo-al-dia.sh` lo pone al día cuando el código avanzó (el hook post-commit de graphify **no corre en
  worktrees**); `--estado` solo dice si está atrasado. El hook de inicio de sesión de la app lo informa.
- `scripts/worktree-cerrar.sh` borra el `graphify-out/` del worktree al cerrarlo (el del backend pesa ~0,5 GB).

**Lo medido (2026-11-06, worktrees de prueba creados y cerrados con los scripts):**

| | App | Backend |
|---|---|---|
| Crear worktree + grafo | 2 min 6 s (incluye `npm ci`, porque el lock del checkout principal difería) | 1 min 58 s (grafo: 115 s, 509 MB) |
| `graphify benchmark` (contra leer todo el repo) | 4,8× menos tokens por pregunta | 1,9× |
| Una pregunta concreta («cuenta suspendida interceptor 403») | — | `graphify query`: ~1 600 tokens; los 302 archivos que nombran «suspendid»: ~515 000 tokens |
| Una consulta | ~1 s | ~1 s |
| Cerrar el worktree | libera 2,2 GB | libera 567 MB |

El benchmark de graphify compara contra leer el corpus entero (que nadie hace), así que la cifra útil es la de la
pregunta concreta: el grafo da el mapa en una respuesta, y después se leen 2–3 archivos en vez de buscar a ciegas.

## 5. El grafo del flujo de agentes y sus bucles (graph y loop engineering)

El ciclo de §2 ya no es solo una tabla: es el grafo del **núcleo genérico del arnés** de Ricardo (fuera de estos
repositorios, `~/Documentos/arnes/grafo/grafo.yaml`), con nodos, aristas con condición (`ok`, `falla`, `aprobado`,
`rechazado`, `ayuda`), estado compartido por worktree (`.arnes/`) y cinco bucles con parada medible y presupuesto. Este
repositorio es un **perfil** (`arnes.perfil.yaml`), y `scripts/verificar.sh` lo valida con `arnes validar`. Las 14
fases del curso en los dos repos: `docs/arnes/fases.md`. Fuentes académicas y de Anthropic: `docs/fuentes.md` del núcleo.

## Fuentes (2026)

- Addy Osmani, «Agent Harness Engineering»: https://addyosmani.com/blog/agent-harness-engineering/
- HumanLayer, «Skill Issue: Harness Engineering for Coding Agents»: https://humanlayer.dev/blog/skill-issue-harness-engineering-for-coding-agents
- Endor Labs, «Harness engineering: how to make AI coding agents reliable and secure»: https://www.endorlabs.com/learn/harness-engineering-how-to-make-ai-coding-agents-reliable-and-secure
- «The Emerging Harness Engineering Playbook»: https://www.ignorance.ai/p/the-emerging-harness-engineering
- Tian Pan, «Harness Engineering» (feb. 2026): https://tianpan.co/blog/2026-02-17-harness-engineering-agent-first-software-development
- SE Radio 730, Birgitta Böckeler sobre harness engineering: https://se-radio.net/2026/07/se-radio-730-birgitta-boeckeler-on-harness-engineering-for-ai-agents/
- InfoQ, el arnés de tres agentes de Anthropic (abr. 2026): https://infoq.com/news/2026/04/anthropic-three-agent-harness-ai/
- «Spec-Driven Development» (arXiv 2602.00180, feb. 2026): https://arxiv.org/html/2602.00180v1
- «Spec Kit vs BMAD vs OpenSpec: choosing an SDD framework in 2026»: https://dev.to/willtorber/spec-kit-vs-bmad-vs-openspec-choosing-an-sdd-framework-in-2026-d3j
- «Best Spec-Driven Development Tools (2026)»: https://codemyspec.com/blog/best-spec-driven-development-tools
- «Observability-Driven Development: SDD is only half the loop»: https://blog.devgenius.io/observability-driven-development-spec-driven-development-is-only-half-the-loop-86618a5ba979
- AWS Builder Center, graphify en Kiro: https://builder.aws.com/content/3Bs2aqRyKTc4YuAH0EzViNmKcZs/reduce-ai-coding-agent-token-usage-by-40-70percent-with-graphify-knowledge-graphs-in-kiro
- graphify, «Reduce token usage in AI coding agents» (jul. 2026): https://graphify.com/blog/reduce-token-usage-ai-coding-agents.md
- «Parallel AI agent workflows with git worktrees»: https://dev.to/dublecc/parallel-ai-agent-workflows-with-git-worktrees-the-concrete-pattern-59lp
- MindStudio, «Git worktrees for parallel AI coding agents»: https://www.mindstudio.ai/blog/git-worktrees-parallel-ai-coding-agents
