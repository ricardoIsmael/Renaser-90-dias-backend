# Research: Hábitos globales por rol

Fase 0 del plan. Cada decisión cita el código que la sostiene (rutas relativas a
`src/main/java/com/renaser/os` = `J/`, migraciones = `M/`, app = `F/`).

## R-1. Dónde se guarda la audiencia y cómo escala a roles nuevos

**Decisión**: tabla nueva `audiencias_habito`, una fila por **versión** de la audiencia de un hábito
global: `roles text[]` con los códigos de rol de la API (`TRAINEE`, `MENTOR`, `MENTOR_LEAD`,
`ADMIN`, `ALCHEMIST`), vacío = para todos, más `rige_desde timestamptz`, `cambiado_por` y
`cambiado_en`. En el dominio de habits el rol es un valor opaco (`CodigoDeRol`), no un enum.

**Por qué**:
- La vigencia estricta (clarify 2026-10-12) exige conservar versiones: un array por fila hace que
  una versión sea **una** fila, sin junction por versión.
- Con el rol opaco, un rol nuevo **no** obliga a tocar habits (FR-004, SC-006). La lista de roles
  válidos y su nombre visible la da `users.api` (R-5); habits valida contra esa lista.
- Los códigos son los de la API (`UserRole.name()`), los mismos que ya viajan en eventos
  (`RolDeUsuarioCambiadoEvent`) y que el frontend usa (`RolAsignable`, `F/src/features/admin/api/
  adminApi.ts:449`). Evita traducir el enum PG en español (`rol_usuario`) dentro de habits.

**Alternativas descartadas**:
- *Junction con FK a `roles(id)`* como cursos y eventos (`M/V1:969`, `M/V1:1213`): la tabla
  `roles` está marcada SUPERADA (D-21) y obligaría a una caché más (`RolesCatalogoCache`). Además
  no soporta versiones sin otra tabla.
- *Columna `rol_usuario[]`* (enum PG): no hay precedente fuera de `usuarios`; obliga a castear
  (`ConsultarResumenParticipacionPersistenceAdapter.java:230`: "operator does not exist:
  renaser.rol_usuario = character varying") y ata habits a los nombres en español.
- *Columna `roles` en `habitos`*: pierde la historia que exige la vigencia estricta.

## R-2. Vigencia estricta ("desde mañana")

**Decisión**: para el día local `D` de una persona (zona `participantes_programa.timezone`):
- **Audiencia vigente** = la versión con mayor `rige_desde` cuya fecha local (en la zona de la
  persona) sea `< D`. Si ninguna cumple pero el hábito tiene versiones, rige la **primera**
  (la audiencia con la que se creó rige desde su creación). Si no tiene versiones, es para todos
  (los 18 hábitos de hoy, sin migrar datos: SC-003).
- **Rol vigente** = misma regla sobre el historial de rol de la persona (R-3); sin historial, su
  rol actual.

Función pura en el dominio (`VigenciaPorDia`), probada con el reloj en la madrugada UTC
(constitución V). No hace falta scheduler: se deriva al generar (regla 2 de `.claude/rules/02`).

**Por qué la primera versión rige desde la creación**: hoy un hábito recién creado se genera ese
mismo día para quien aún no generó su jornada; si la audiencia inicial esperara a mañana, ese día
el hábito sería "para todos" por no tener versión vigente. Solo los **cambios** se difieren.

**Alternativa descartada**: respetar solo lo ya generado (más simple); el dueño eligió estricto.

> **Corregido 2026-10-12 (implementación).** Con solo "la primera versión rige desde la creación", restringir uno de
> los 18 hábitos que existían sin versiones lo aplicaba HOY (su primera versión era el cambio). Ahora el primer cambio
> de un hábito sin versiones anota antes una versión base «para todos» con `rige_desde = epoch`
> (`AudienciasDelCatalogo.guardarSiCambio`); la regla de la primera versión queda solo para la audiencia con la que
> nace un hábito (`guardarAlCrear`). Probado en `HistorialDeAudienciaTest` y `HabitosPorRolIT`.

## R-3. Historial del rol de la persona

**Decisión**: habits escucha `users.api.RolDeUsuarioCambiadoEvent`
(`J/users/api/RolDeUsuarioCambiadoEvent.java:21`: `usuarioId, rolAnterior, rolNuevo, occurredAt`)
con `@ApplicationModuleListener`, y anota en `historial_rol_participante`
(`usuario_id, rol text, rige_desde timestamptz`). Si es el primer cambio de esa persona, inserta
antes una fila con `rolAnterior` y `rige_desde = epoch` (1970-01-01; corregido 2026-10-12: '-infinity' no se lee limpio por JDBC), para que los días anteriores al
cambio sigan con el rol viejo.

**Por qué**: el rol que trae `ProgresoParticipanteHabits.rol` es el actual
(`ConsultarProgresoParticipanteHabitsPort.java:43`), y `users` no guarda historia de rol
(`.claude/rules/04`: no hay historial de cambios de cuenta; el outbox borra los eventos). El
precedente es el semáforo (`EstadoDeCuentaSemaforoListener` → `semaforo_pausas`, V72).
El evento se publica en un solo lugar (`UserAccountService.java:282`) y solo si el rol cambió.

**Límite aceptado**: un cambio de rol anterior al despliegue no tiene historia; rige el rol actual
para todos los días (spec, Edge Cases).

## R-4. Dónde se aplica el filtro (un solo lugar)

**Decisión**: puerto de entrada nuevo `ConsultarCatalogoAplicableUseCase.para(participante, fecha)`
que devuelve los hábitos globales activos que aplican a esa persona ese día local. Lo usan:

| Lector | Hoy | Cambio |
|---|---|---|
| `RegistroService.generarInterno` (`:206`) | `catalogoActivo()` | `catalogoAplicable(progreso, fecha)` |
| `MisHabitosService.vistas` (`:106`) | `catalogoActivo()` | aplicable para su día de hoy |
| `ConsultaPreferenciasHorarioService` (`:100`) | `catalogoActivo()` | aplicable para su día de hoy |
| `HabitosDeAprendizJdbcAdapter` (`:63,90`, vista de staff) | SQL directo al catálogo | filtrar con el mismo caso de uso |

**Por qué basta con esto**: el único `RegistroHabito.generar` está en `RegistroService.java:277`;
ningún flujo inserta registros fuera de `generarInterno` (Clase diaria, Pastilla, Audioterapia,
post diario y Despertar/Dormir buscan el registro de hoy y, si falta, responden 404/vacío o
generan **pasando** por `generarInterno`: `PostDiarioComunidadHabitoService.java:159`,
`TracksDelDiaProyeccionService.java:140`). Coherencia, semáforo, puntos, agenda, avisos y Home
leen `registros_habito` (`ConteoDiarioHabitosFinderService`, `AvisoHabitoService`,
`AgendaDelDiaFinderService`…): si el registro no se genera, no cuenta (FR-014). FR-015 queda
cubierto: sin registro, cada flujo ya responde "no hay registro de hoy" sin puntos.

**Riesgo detectado**: el mensaje de Clase diaria ("No hay Clase Diaria generada para hoy") y de
Audioterapia ("Ese registro no es la Audioterapia de hoy") son aceptables como "no aplica"; no se
cambian (sin ampliar alcance). Se agregan pruebas que lo fijan.

## R-5. Catálogo de roles con nombre visible

**Decisión**: `users.api.CatalogoDeRolesFinder` con `List<RolDelSistema(codigo, nombre)>` derivado
de `UserRole.values()`; el nombre visible vive junto al enum (`UserRole.nombreVisible()`):
Aprendiz, Mentor, Líder de mentores, Administrador, Alquimista. Endpoint
`GET /api/v1/admin/habits/roles` (permiso `MANAGE_HABIT_CATALOG`).

**Por qué**: hoy no hay catálogo expuesto (`EventoResponse`, `CursoResponse` devuelven
`Enum::name`), y el frontend tiene la tabla duplicada en 5 lugares. Con el nombre en el servidor,
una app publicada muestra un rol nuevo sin APK (US5).

> **Corregido 2026-10-12 (implementación).** `UserRole` ya vive en `users.api`, así que no hizo falta
> `CatalogoDeRolesFinder` ni `RolDelSistema`: basta `UserRole.nombreVisible()` y habits lo lee directo
> (`AudienciasDelCatalogo.rolesElegibles`). **Límite:** el rol de la persona llega a habits por el espejo
> `RolParticipante`; un rol nuevo pide sumarle la constante (el `switch` del adaptador no compila sin ella). El filtro,
> la audiencia, las tablas y la app no cambian.

## R-6. CRUD admin: compatibilidad y eliminar

- Los DTOs suman `targetRoles` **opcional**: `null`/ausente = en crear, para todos; en editar, no se
  toca la audiencia (mismo patrón que `mandatoryOnIntoxication`, E-263). Un cliente viejo
  (`habitsAdmin.ts` citado en el controller) sigue funcionando igual.
- `AdminHabitResponse` suma `targetRoles` (última versión guardada), `targetRolesEffectiveFrom`,
  `audienceChangedAt`, `audienceChangedBy`, `systemKey` y `isDeactivatable` (solo lectura).
- `GET /api/v1/admin/habits?role=MENTOR` filtra por la **última** audiencia guardada.
- Eliminar con historia: hoy cae en `DataIntegrityViolationException` → 409 genérico
  (`GlobalExceptionHandler.java:294`). Se valida antes en el servicio con un puerto que cuenta
  registros y se lanza `IllegalStateException` con mensaje claro (→ 409 con motivo; el frontend ya
  muestra el motivo de un 409, `F/src/features/admin/utils/mensajes.ts:41`).
- Rol desconocido → `IllegalArgumentException` → 400 (FR-021).

## R-7. App

- Patrón de Imágenes de las fases / Bienvenida: `admin/api` + `schemas` (zod `passthrough`,
  `z.string()` para enums), hook `{datos, cargando, fallo, recargar}`, utils puros probados,
  pantallas en la pila `useState<Vista[]>` de `AdminScreen.tsx:36-54,89`.
- Entrada nueva en `enLaApp` de `MasOpcionesScreen.tsx:47-82` y se quita la fila «Catálogo de
  hábitos» de `enLaWeb` (`:93-97`).
- Roles desde `GET /admin/habits/roles` (no se agrega otra tabla de nombres en la app).
- Componentes: `CabeceraAdmin`, `Interruptor`, `Checkbox`, `ControlSegmentado` (tipo, evidencia),
  `HojaDeOpciones` (filtro por rol y categoría), `FormField`, `BotonPrincipal/Secundario/Peligro`,
  `ConfirmacionEnLinea`, `Presionable`, `confirmar/avisar` de `admin/utils/dialogo`.
- Diseño con las skills de Emil (`emil-design-eng`, `animate-expo`), pedido del usuario 2026-10-12.
- Sin caché que invalidar: `tracksDeHoyEnVuelo` y `catalogoYaPedido` son promesas por carga. Las
  alarmas locales de un hábito que deja de aplicar se cancelan en la siguiente puesta al día
  (`ponerAlDiaLasAlarmas.ts`), como hoy con un hábito desactivado (spec, Edge Cases).
