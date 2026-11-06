# AGENTS.md — Renaser OS (backend)

Backend del programa Renaser: Java 25, Spring Boot 4.1, Spring AI 2.0, monolito modular hexagonal con Spring Modulith,
PostgreSQL propio (Flyway) y Redis. Lo consume la app de React Native de `../Renaser-90-dias-frontend-/`. **Las specs
de los dos repos viven aquí** (`specs/`). Este archivo es el **mapa**: por dónde empezar, cómo verificar y qué reglas
valen siempre. Mantenlo corto (≤ 150 líneas, `scripts/arnes.test.mjs`): un diseño, un plan o un informe no van acá.

## Al empezar

1. `pwd`, `git status --short`, `git log --oneline -5`. **Maven nunca corre en el checkout que usa el IDE**: trabaja en
   un worktree propio (E-313, E-104), creado con `scripts/worktree-nuevo.sh <rama>` (arma el grafo antes que nada).
2. Lee `PROGRESO.md` (qué está verificado, qué sigue, qué quedó a medias) y, si trabajas una spec, su carpeta en
   `specs/NNN-…/` (spec, plan, tareas, contratos y decisiones del dueño).
3. Antes de elegir un número nuevo de error (`E-NN`) o de decisión (`D-NN`), mira el último usado.
4. **Grafo primero**: `graphify query "…"` (o `path` / `explain`) antes de abrir archivos; después lee solo los 2–3 que
   nombra. En un worktree el hook de graphify no corre: `scripts/grafo-al-dia.sh` lo pone al día (`--estado` solo mira).
   Qué cargar según lo que tocas (capa, módulo, tab): `docs/arnes/README.md` §3.
5. Si la verificación ya está en rojo antes de empezar, arréglala primero o dilo: no apiles trabajo encima.

## Dónde está cada cosa

| Qué | Dónde |
|---|---|
| Estado verificado, siguiente paso y lo que quedó a medias | `PROGRESO.md` |
| Reglas detalladas (Claude Code las carga solas; otros agentes, léelas) | `.claude/rules/00` a `06` (abajo, una línea de cada una) |
| Constitución de spec-kit (la misma en los dos repos) | `.specify/memory/constitution.md` |
| Specs de los dos repos, y cuáles exigen trazabilidad | `specs/`, `specs/trazabilidad.json` |
| Bitácora de errores: síntoma literal, causa real, cómo evitarlo | `docs/BITACORA_ERRORES.md` (`E-NN`) |
| Registro de decisiones del dueño y del equipo | `docs/MODULOS_A_AVANZAR.md` §8 (`D-NN`) |
| Diseño de la arquitectura («`CLAUDE.MD` §N» en comentarios del código = este archivo) | `docs/ARQUITECTURA.md` |
| Despliegue (CD a EC2 + RDS), CI y pruebas en la nube | `docs/DESPLIEGUE_Y_CI.md`, `docs/PRUEBAS_EN_CLOUD.md` |
| Contratos de la API y mapa de endpoints | `docs/api/`, `docs/MAPA_ENDPOINTS.md` |
| Mapa de los demás documentos (vigentes e históricos) | `docs/README.md` |
| Lo que el dueño autorizó en los tabs de la app | `../Renaser-90-dias-frontend-/docs/decisiones/` |
| El arnés: metodología (spec-kit), ciclo completo, módulos, grafo y fuentes | `docs/arnes/README.md` |
| Familias de errores que se repiten y el chequeo que frena cada una | `docs/arnes/familias-de-errores.md` |

## Reglas que no se negocian

Cada una tiene su archivo con el detalle y el porqué:

1. **Commits y alcance** (`00`): castellano, imperativo, qué cambia y por qué; sin `Co-Authored-By` ni atribución a IA;
   una funcionalidad o un bug por rama y por commit; nunca `--no-verify`; no ampliar el alcance por cuenta propia; **no
   inventar reglas de negocio** (si no está confirmado, se pregunta).
2. **Arquitectura** (`01`): las dependencias apuntan al dominio; un módulo solo importa `<otro>/api/`; controller tonto;
   nada de IA dentro de `@Transactional`. `ArchitectureTest` rompe el build.
3. **Tiempo** (`02`): la medianoche es la de la zona de cada persona; lo que depende del calendario se deriva de fechas;
   el reloj entra por `Clock`.
4. **Pruebas** (`03`): nada está terminado sin `scripts/verificar.sh` en verde (adentro, `./mvnw clean verify`); el test
   de un bug falla contra el código viejo; todo endpoint nuevo prueba el 403 de un rol sin permiso y de una cuenta
   suspendida, y declara `@RequiresPermission`/`@PublicEndpoint` y `@Funcionalidad`/`@SinInterruptor`.
5. **Base de datos** (`04`): nunca se edita una migración aplicada; se agrega otra.
6. **Documentar en el mismo cambio** (`05`): el error en la bitácora, la decisión en el registro, y la sección vieja que
   quedó mal se corrige diciendo qué decía antes.
7. **Trazabilidad** (`06`): cada `FR-NNN` y `SC-NNN` de una spec exigida tiene una prueba con la marca `spec NNN FR-NNN`
   (en el backend o en la app). Un requisito sin prueba es rojo.
8. **Nunca se prueba contra producción**: base local clonada (`pgvector/pgvector:pg16`, puerto 5433) y backend local.
9. **La máquina se comparte entre sesiones**: Maven y Jest van con `~/.config/renaser-e2e/herramientas/pesado.sh` (uno a
   la vez, con tope de memoria). Antes de levantar un backend o el emulador, pregunta quién los tiene.
10. **Un bug, primero un issue** (`gh issue create --label bug`); el PR lo cierra con `Closes #N`.

## Errores que se repiten y no tienen chequeo automático

Los que sí lo tienen fallan solos (`scripts/arnes-familias.test.mjs`, ArchUnit). Estos dependen de ti:

- **Dos pedidos a la vez** (doble toque, reintento, dos réplicas): toda creación «si no existe» y todo cierre es
  idempotente o tiene su índice único, y se prueba con dos llamadas concurrentes (E-70, E-76, E-596, E-655, E-668).
- **El motivo viaja**: un rechazo es un 4xx con `codigo` y un mensaje en tuteo y con tildes, nunca un 500 ni un genérico;
  un 404 «no hay» no se confunde con «no existe la ruta» (E-45, E-84, E-373, E-473).
- **Lo que se escribe se lee**: si guardas un dato nuevo, una prueba comprueba que la lectura lo devuelve (E-53, E-145).
- **Avisos**: un push lleva caducidad y prioridad, no sale dos veces y se apaga cuando la persona ya cumplió (E-302, E-645).



Un solo camino, el mismo que corre el CI:

| Comando | Qué corre |
|---|---|
| `scripts/verificar.sh` | las reglas del arnés, la trazabilidad de specs contra la app (si la encuentra) y `./mvnw -B -ntp clean verify`, con la guarda de E-111: cuenta las líneas `Tests run:` y falla si no corrió nada o si hay menos de `MINIMO_PRUEBAS` (2000) |
| `scripts/verificar.sh --rapido` | arnés, trazabilidad y `./mvnw -B -ntp -q compile`: sirve mientras trabajas, **no cierra nada** (sale con 2) |
| `scripts/verificar.sh --nube` | igual que el primero, con los contenedores en Testcontainers Cloud (`scripts/test-cloud.sh`) |

Sale con **0** si todo corrió y pasó, **1** si algo falló y **2** si nada falló pero algo quedó sin correr (eso no es
verde: dilo en el PR). La app se busca en `../Renaser-90-dias-frontend-` o en `RENASER_APP=<ruta>`.

## Cuándo algo está terminado

1. `scripts/verificar.sh` en 0, con su resumen en el PR (cuántas pruebas corrieron, 0 fallos), y el CI del PR en verde.
2. **Un PR no está listo sin el E2E de su spec** en el emulador contra este backend local (ver el `AGENTS.md` de la app)
   **y los logs revisados**: el del backend y `adb logcat` de la app, sin errores nuevos (pégalo en el PR). Lo que no se
   pudo verificar se dice con todas sus letras.
3. Bitácora, registro de decisiones, spec y `PROGRESO.md` al día; `graphify update .`.

## Al cerrar la sesión

1. `PROGRESO.md`: estado verificado, siguiente paso, lo que quedó a medias y tu sesión (solo las cinco últimas).
2. El trabajo seguro, commiteado en su rama; nada a medio hacer sin anotar; sin archivos temporales en el repo.
3. `scripts/grafo-al-dia.sh` (el hook no corre en los worktrees). Si la rama ya se fusionó: `scripts/worktree-cerrar.sh
   <rama>` (nunca borra cambios sin commitear ni ramas sin fusionar); `scripts/worktree-viejos.sh` lista lo que se puede
   limpiar.
