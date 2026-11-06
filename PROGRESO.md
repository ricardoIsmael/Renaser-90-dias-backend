# Progreso — Renaser OS (backend)

Se lee **al empezar** y se actualiza **al cerrar** cada sesión (`AGENTS.md`). Todo lo que se escribe aquí se puede
comprobar; lo que no comprobaste dice «sin confirmar». Es corto a propósito (≤ 150 líneas, `scripts/arnes.test.mjs`):
lo viejo se borra, porque ya queda en git. El detalle vive en cada spec (`specs/`), en la bitácora y en el registro de
decisiones.

## Estado verificado — 2026-11-06

- **`master`**: `3a9f0b8` (#90, spec 042: el mapa en la cuenta, cámara en diagonal y 2D/3D).
- **Producción**: el CD despliega cada fusión a `master` después del CI. El último despliegue en verde es `e74f566`
  (2026-11-05, spec 042 y la grabación automática del Meet). El de `3a9f0b8` estaba corriendo al escribir esto: **sin
  confirmar**.
- **Trazabilidad de specs**: 8 specs exigidas, 224 requisitos, 217 cubiertos, 7 exentos, 0 sin prueba (contra el
  `master` de la app, 2026-11-06).
- **Últimos números usados**: error `E-671`, decisión `D-324` (míralos otra vez antes de usar uno: otras sesiones
  escriben en paralelo).
- **Bugs abiertos**: #60 (SER no propone planes desde el diagnóstico hasta que la persona abre Plan), #61 (V160 de la
  spec 038 sin `SET search_path TO renaser`; ahora lo frena el arnés) y #95 (189 mensajes de error sin tildes, deuda
  congelada por el arnés).
- **Arnés**: 11 chequeos de familias de errores (`scripts/arnes-familias.test.mjs`), scripts de worktree con el grafo
  primero (`scripts/worktree-*.sh`, `grafo-al-dia.sh`), hook de inicio de sesión y `docs/arnes/`. Probado el 2026-11-06:
  un worktree creado (grafo en 115 s) y cerrado (567 MB liberados); `worktree-cerrar.sh` se negó con un archivo sin
  commitear y con un commit sin fusionar.

## Siguiente paso

1. Que el dueño revise y fusione el PR del arnés (`arnes-propio`, en los dos repos; en la app reemplaza al PR #48).
2. Ver si el despliegue de `3a9f0b8` terminó en verde (`gh run list --workflow cd.yml --limit 1`).

## A medias o pendiente

- **Worktrees que se pueden cerrar**: `scripts/worktree-viejos.sh` lista los fusionados y limpios (el 2026-11-06:
  `tanda-1-7-1`, `whatsapp-en-admin-trainees`). No se cerraron: son de otras sesiones.
- **Lo que el arnés todavía no automatiza**: concurrencia, 4xx genérico, avisos y lecturas viejas (reglas en `AGENTS.md`);
  el E2E corre a mano en el emulador, no en el CI.

- **Seguridad Java (CVE-2026-91403)**: el 27/10 se confirmó que nos afecta; el detalle quedó en una rama local del
  checkout de la app (`pendiente/cve-java-backend`), sin commitear en ningún repo. Producción **no está verificada ni
  parcheada**: hay que traer esa nota aquí y resolverlo.
- **4 PR de Dependabot abiertos** (#59, #66, #67, #68).
- **`docs/` tiene muchos documentos fechados** mezclados con los vigentes. `docs/README.md` los clasifica; no se movieron
  porque el código los cita por su ruta.

## Sesiones (las cinco últimas)

- **2026-11-06 — arnés: familias de errores, worktrees y grafo.** 604 entradas de la bitácora y 83 issues `bug`
  clasificados en 16 familias (`docs/arnes/familias-de-errores.md`); las 10 más frecuentes o caras tienen chequeo
  (11 aquí, 8 en la app) o regla corta en `AGENTS.md`. Investigación de 2026, metodología (spec-kit con camino corto y
  E2E + logs obligatorios) y módulos en `docs/arnes/README.md`; reglas 04 y 06 se cargan solas por ruta (`paths:`).

- **2026-11-06 — arnés propio.** `CLAUDE.MD` (diseño de agosto, 1046 líneas que cada sesión cargaba) pasó tal cual a
  `docs/ARQUITECTURA.md`; el mapa es `AGENTS.md` (corto) y `CLAUDE.md` lo importa. Lo vigente de su §0 que no estaba en
  `.claude/rules/` se trajo a `03-pruebas.md`. Un solo comando (`scripts/verificar.sh`) que también usa el CI, este
  `PROGRESO.md`, `docs/README.md` y reglas del arnés (`scripts/arnes.test.mjs`). Verificación: ver el PR.
