# Tasks: El programa empieza al día siguiente, sin elegir el Día 1

**Input**: `spec.md`, `plan.md`, `contracts/activate-program.md`. Estado: **[x]** hecho y probado · **[ ]** pendiente.
Reglas: sin tablas ni migración (V160 no se usa); D-309; commits por pieza, en castellano y sin coautoría; lo pesado solo por `pesado.sh`.

## Phase 1: Setup

- [x] T001 Spec, plan, tareas y contrato en `specs/036-empieza-al-dia-siguiente/`
- [ ] T002 `docs/MODULOS_A_AVANZAR.md`: D-309 (tras D-307) y «Corregido» en D-66 / D-261; `docs/MODULO_ONBOARDING.md` y `docs/MODULO_USERS.md`

## Phase 2: Servidor

- [ ] T003 `ParticipacionPrograma`: `inicioAlActivar(clock)`, `activarPrograma(fechaPedida?, clock)`, `opcionesDeActivacion` de una fecha; borrar `MAX_DIAS_ESPERA_ACTIVACION`
- [ ] T004 `ActivateProgramCommand`, `ActivarProgramaRequest` y `ParticipacionProgramaController`: `startDate` y cuerpo opcionales; javadocs
- [ ] T005 Pruebas de dominio (madrugada UTC, viernes, fin de mes y de año, zona distinta a Lima) y de servicio
- [ ] T006 `ParticipacionProgramaControllerTest` y las IT existentes ajustadas; `EmpiezaAlDiaSiguienteIT` nueva
- [ ] T007 `./mvnw -B clean verify` con `TZ=UTC` (por `pesado.sh`)

## Phase 3: App (repo del frontend)

- [ ] T008 Pantalla corta («Tu programa empieza mañana»), textos de todas las entradas, Mi Radar, avisos
- [ ] T009 Jest, `e2e/dia-uno/`, `.maestro/empieza-manana/` y los flujos que citaban el selector
- [ ] T010 `AGENTS.md`: entrada del 2026-11-02 y «> Corregido» en las que describen el selector
- [ ] T011 `tsc --noEmit` y Jest completo (por `pesado.sh`)

## Phase 4: Cierre

- [ ] T012 `graphify update .` en los dos worktrees
