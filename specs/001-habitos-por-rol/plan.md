# Implementation Plan: Hábitos globales por rol

**Branch**: `001-habitos-por-rol` (rama de git a crear solo si se pide commit) | **Date**: 2026-10-12 |
**Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-habitos-por-rol/spec.md`

## Summary

Cada hábito global gana una **audiencia versionada** (conjunto de códigos de rol; vacío = para
todos). Un único caso de uso, `ConsultarCatalogoAplicableUseCase`, decide qué hábitos globales
aplican a una persona en su día local `D`, con la audiencia y el rol vigentes al cierre de `D−1`
(vigencia estricta). Lo usan la generación de registros y las tres vistas que hoy leen el catálogo
directo; todo lo demás (coherencia, semáforo, puntos, agenda, avisos, flujos propios) ya parte de
los registros y hereda el filtro. Habits anota el historial de rol escuchando
`RolDeUsuarioCambiadoEvent`. El CRUD admin existente suma `targetRoles` (opcional, compatible) y un
`GET /roles` con nombres del servidor. La app suma «Hábitos del programa» en Administración → Más
opciones con el CRUD completo.

## Technical Context

**Language/Version**: Java 25 (Temurin, sdkman) / TypeScript estricto (Expo SDK 57, React Native)

**Primary Dependencies**: Spring Boot (según `pom.xml`), Spring Modulith (eventos con outbox),
Spring Data JPA + `JdbcClient`, Flyway; app: zod, componentes propios (sin librerías nuevas)

**Storage**: PostgreSQL schema `renaser`; migración `V96` (dos tablas nuevas, append-only)

**Testing**: JUnit 5 + Mockito (dominio y servicios), Testcontainers (IT con Postgres real),
`@WebMvcTest` (controller), `ArchitectureTest`, `EndpointAuthorizationDeclarationTest`; app: jest +
`tsc --noEmit`

**Target Platform**: backend en EC2 + RDS; app Android (APK) y web (Vercel)

**Project Type**: mobile-app + API (dos repos hermanos)

**Performance Goals**: el filtro no agrega consultas por hábito: audiencias por lote (una consulta
por generación) e historial de rol (una por persona). El barrido de jornadas no debe crecer más de
un 10 % en tiempo con 300 participantes.

**Constraints**: vigencia estricta por zona de la persona; historia append-only; compatibilidad con
APK y clientes admin anteriores (campos opcionales y aditivos)

**Scale/Scope**: ~300 participantes, ~27 hábitos globales, 5 roles; 1 migración, ~15 clases nuevas
en habits, 1 finder en users.api, 1 listener; app: 2 pantallas, 1 api, 1 hook, utils y pruebas

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo se cumple | Estado |
|---|---|---|
| I. Dependencias al dominio | Audiencia, vigencia e historial de rol en `habits/domain` sin Spring; habits consume roles por `users.api.CatalogoDeRolesFinder` y el evento de `users.api`; controller tonto (un caso de uso por endpoint) | ✅ |
| II. No inventar reglas | Todas las reglas salen de la spec (8 respuestas del dueño + 2 de clarify). Decisión técnica nueva: la audiencia inicial rige desde la creación (research R-2); se registra como D-nn y se informa al dueño | ✅ (avisar) |
| III. Pruebas en verde | Unitarias de `Audiencia`, `HistorialDeAudiencia`, `HistorialDeRol` con reloj en madrugada UTC; IT de generación, listener, persistencia y CRUD; 403 de rol y de `SUSPENDED` en endpoints nuevos y extendidos; app con jest y tsc | ✅ |
| IV. Historia | `V96` con cabecera; tablas append-only; registros pasados intactos | ✅ |
| V. Tiempo del participante | `VigenciaPorDia` usa la zona de la persona; sin scheduler nuevo (se deriva al generar) | ✅ |
| VI. Cinco tabs | Ninguna pantalla de los tabs cambia. Training/Plan/Hoy muestran menos hábitos solo porque el servidor los filtra. Administración (estado de Hoy) suma una entrada: se anota en `AGENTS.md` como «Cambio por pedido del dueño — 2026-10-12» | ✅ |
| VII. Documentar | D-nn en `docs/MODULOS_A_AVANZAR.md` §8, `docs/MODULO_HABITS.md`, `AGENTS.md` del frontend, bitácora si aparece un error | ✅ |

**Re-check post-diseño (Fase 1)**: sin violaciones. `ConsultarCatalogoAplicableUseCase` evita
repetir el filtro en 4 lectores; el listener sigue el precedente del semáforo. Sin entradas en
Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/001-habitos-por-rol/
├── spec.md
├── plan.md              # este archivo
├── research.md          # R-1..R-7
├── data-model.md        # V96, dominio, puertos
├── quickstart.md
├── contracts/admin-habits.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code

**Backend** (`Renaser-90-dias-backend/`, `J = src/main/java/com/renaser/os`)

```text
src/main/resources/db/migration/V96__audiencia_de_habitos_por_rol.sql

J/users/api/CatalogoDeRolesFinder.java            # nuevo
J/users/api/RolDelSistema.java                    # nuevo (record codigo, nombre)
J/users/api/UserRole.java                         # + nombreVisible()
J/users/application/services/CatalogoDeRolesService.java   # implementa el finder

J/habits/domain/model/audiencia/
    CodigoDeRol  Audiencia  VersionDeAudiencia  HistorialDeAudiencia
    TramoDeRol  HistorialDeRol  VigenciaPorDia
J/habits/application/ports/in/audiencia/
    ConsultarCatalogoAplicableUseCase  ConsultarRolesElegiblesUseCase
J/habits/application/ports/out/audiencia/
    CargarAudienciasPort  GuardarAudienciaPort  HistorialDeRolPort
    ContarRegistrosDeHabitoPort  CatalogoDeRolesPort
J/habits/application/services/
    CatalogoAplicableService      # nuevo, el filtro único
    HistorialDeRolService         # nuevo, lo llama el listener
    HabitoAdminService            # audiencia en crear/actualizar/listar; eliminar con motivo
    RegistroService               # generarInterno → catálogo aplicable
    MisHabitosService  ConsultaPreferenciasHorarioService   # → catálogo aplicable
J/habits/infrastructure/adapter/in/event/RolDeUsuarioCambiadoHabitsListener.java
J/habits/infrastructure/adapter/in/rest/habitoadmin/
    HabitoAdminController (GET ?role, GET /roles)  CreateHabitRequest  UpdateHabitRequest
    AdminHabitResponse  RolElegibleResponse
J/habits/infrastructure/adapter/out/persistence/audiencia/
    AudienciaHabitoJdbcAdapter  HistorialRolJdbcAdapter  CatalogoDeRolesAdapter
J/habits/infrastructure/adapter/out/persistence/habitosaprendiz/HabitosDeAprendizJdbcAdapter  # filtro

src/test/java/com/renaser/os/habits/…  (unitarias de dominio, servicios, IT, controller)
```

**App** (`Renaser-90-dias-frontend-/`)

```text
src/features/admin/api/habitosProgramaApi.ts        src/features/admin/api/habitosProgramaSchemas.ts
src/features/admin/hooks/useHabitosProgramaAdmin.ts
src/features/admin/utils/habitosPrograma.ts         # etiquetas, filtro, validación, audiencia ↔ texto
src/features/admin/screens/HabitosProgramaAdminScreen.tsx   # lista + filtro por rol
src/features/admin/screens/HabitoProgramaFormScreen.tsx     # crear / editar / roles / activar / eliminar
src/features/admin/screens/AdminScreen.tsx          # vistas 'habitos-programa' y 'habito-programa'
src/features/admin/screens/MasOpcionesScreen.tsx    # entrada nueva; se quita «Catálogo de hábitos» de «en la web»
pruebas en __tests__/                               # api, utils, entrada en Administración, pila
specs/README.md                                     # apunta a la spec del backend
AGENTS.md                                           # «Cambio por pedido del dueño — 2026-10-12»
```

**Structure Decision**: dos repos existentes. En el backend todo vive en el módulo `habits` salvo
el catálogo de roles (`users.api`). En la app, dentro de `features/admin`, con el patrón de
Imágenes de las fases y Bienvenida.

## Diseño de la pantalla (app)

Se diseña e implementa cargando `emil-design-eng` y `animate-expo` (pedido del usuario).

- **Lista** (`HabitosProgramaAdminScreen`): `CabeceraAdmin` «Hábitos del programa» con acción
  «Nuevo». Filtro: «Todos los roles» + un chip por rol del servidor (envuelve en varias líneas, sin
  scroll horizontal). Grupos «Activos» e «Inactivos». Cada fila (`Presionable`, 56 px): ícono de la
  dimensión, título, rótulo de audiencia («Para todos» / «Mentor · Líder de mentores») y, si el
  cambio es de hoy, «Rige desde mañana». Estados: cargando, vacío («Ningún hábito para este rol»),
  error con «Reintentar» y sin permiso.
- **Formulario** (`HabitoProgramaFormScreen`, crear y editar): título (solo al crear), descripción,
  dimensión (Cuerpo/Mente/Emociones/Espíritu), tipo (solo al crear), evidencia
  (Opcional/Obligatoria), «Es opcional», «Obligatorio en intoxicación»; sección **«¿Para quién
  es?»**: `Interruptor` «Para todos los roles» y, apagado, una `Checkbox` por rol (al menos una).
  «Guardar» fijo abajo. En editar: «Desactivar/Activar» y «Eliminar» (`BotonPeligro`, confirmación;
  un 409 abre un diálogo con «Desactivar en su lugar»). Si el hábito es protegido o tiene clave del
  sistema, un aviso: «Este hábito tiene reglas propias en la app; restringirlo deja sin él a los
  demás roles».
- **Movimiento**: solo lo existente (`Presionable` 0.97, entrada de `ConfirmacionEnLinea`,
  `tacto.seleccion()` al marcar un rol, `tacto.logro()` al guardar). Las casillas aparecen con un
  fundido de 180 ms al apagar el interruptor; con «reducir movimiento», al instante.
- `useSystemBackHandler` en las dos vistas (vuelve a la lista / a Más opciones); con cambios sin
  guardar, pregunta antes de salir.

## Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Un lector del catálogo queda sin filtrar | Los 4 lectores pasan por el caso de uso; prueba que falla si `catalogoActivo()` se usa fuera de `CatalogoAplicableService` |
| N+1 al cargar audiencias | Puerto por lote; IT de generación con varios hábitos |
| Restringir Clase diaria / Pastilla rompe su flujo | Pruebas: sin registro, cada flujo responde sin error interno ni puntos |
| Evento reentregado por el outbox | PK `(usuario_id, rige_desde)` + `ON CONFLICT DO NOTHING` |
| Cambio a las 23:30 en Lima (04:30 UTC del día siguiente) | Pruebas de `VigenciaPorDia` con reloj en madrugada UTC |
| App vieja sin `targetRoles` | Campos opcionales; zod `passthrough` |

## Complexity Tracking

Sin violaciones de la constitución.
