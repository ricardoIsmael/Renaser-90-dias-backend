# Tasks: Mensaje del semáforo con sticker y descarga del podio

**Input**: `specs/006-mensaje-semaforo-y-podio/` (spec, plan, research, data-model, contracts, quickstart)

**Prerequisites**: rama `006-mensaje-semaforo-y-podio` en los dos repos, creada desde `origin/master` (ya trae
`IT_SUPPORT`, fase 1). Migración **V110** reservada; antes de integrar, confirmar que siga siendo mayor que la más alta
de `origin/master` (la fase 2 está en pausa con V113–V118; la spec 005 tiene V109).

**Tests**: obligatorios por la constitución (III): TDD, unitarias sin Spring, IT con Testcontainers, 403 negativos y
reloj en la madrugada UTC.

**Rutas**: `backend:` = `Renaser-90-dias-backend/` (código en `src/main/java/com/renaser/os/`, que abajo se escribe
`…/`); `app:` = `Renaser-90-dias-frontend-/`.

**Coordinación**: antes de cualquier `clean verify`, Testcontainers, jest completo, backend o emulador, avisar a las
otras sesiones y esperar a que la máquina esté libre.

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup

- [x] T001 Hacer `git fetch`, crear la rama `006-mensaje-semaforo-y-podio` desde `origin/master` en backend y app (en un
  worktree propio, sin tocar los trabajos en curso de otras sesiones), y verificar que `IT_SUPPORT` esté en
  `backend:…/users/api/UserRole.java`.
- [x] T002 [P] Copiar los 15 stickers de `backend:specs/006-mensaje-semaforo-y-podio/data/stickers/*.webp` a
  `app:assets/stickers/semaforo/` (512×512, fondo transparente, ≤ 90 KB cada uno).
- [x] T003 [P] Reservar en `backend:docs/MODULOS_A_AVANZAR.md` §8 el siguiente D-nn libre (verificar el último usado)
  para «mensaje del semáforo» y otro para «capacidad de exportar el ranking».

---

## Phase 2: Foundational (bloquea US1 y US2)

- [x] T004 Escribir `backend:src/main/resources/db/migration/V110__mensajes_del_semaforo.sql`, con la cabecera
  «-- V110 — Mensajes del semáforo (D-nn)» y las secciones «Por qué se toca la base:» y «Por qué así:». Crea dos tablas:
  - Tabla `renaser.mensajes_semaforo`:
    - `id varchar(8)` PK;
    - `grupo varchar(20) not null` con CHECK en (VERDE, AMARILLO, ROJO, INICIO, SIN_PROGRAMA, PAUSA, CUENTA_PAUSADA,
      GRADUADO);
    - `texto varchar(300) not null`;
    - `sticker varchar(40) not null`;
    - `activo boolean not null default true`.
  - Tabla `renaser.mensajes_semaforo_elegidos`:
    - `participante_id uuid not null`;
    - `fecha date not null`;
    - `grupo varchar(20) not null`;
    - `mensaje_id varchar(8) not null` con FK a `mensajes_semaforo(id)`;
    - `elegido_en timestamptz not null`;
    - PK `(participante_id, fecha, grupo)` e índice `(participante_id, fecha desc)`. *(Corregido 2026-10-19, D-281: la
      PK es `(participante_id, fecha, canal, grupo)`; ver T048.)*
  - Siembra los 115 mensajes desde `data/mensajes-semaforo.json`: el grupo es el `color`, o el `caso` cuando el color es
    SIN_COLOR. Las variables no se guardan:
    se derivan del texto en el dominio.
  > **Hecho 2026-10-19 (D-277).** Sin el índice `(participante_id, fecha desc)`: la PK ya lo sirve. Sin FK al participante distinta de la de las tablas del semáforo (`participantes_programa`, `ON DELETE CASCADE`).
- [x] T005 [P] Prueba `backend:src/test/java/com/renaser/os/points/domain/model/semaforo/mensaje/CatalogoDelJsonTest.java`
  sobre el JSON:
  - Hay 115 mensajes: 35/35/30 por color y 3 por caso.
  - Ningún texto pasa de 140 caracteres contando 10 por variable.
  - No hay voseo (`vos|podés|hacé|tenés|usted`).
  - Las variables están dentro del conjunto permitido y coinciden con el texto.
  - Los stickers están entre los 15.
  - Los dorados solo van en VERDE y GRADUADO.
  - `{faltan}` y `{habitoFlojo}` no aparecen en SIN_COLOR.
- [x] T006 [P] Crear los enums `GrupoDelMensaje` y `Variable`, y los records `MensajeDelSemaforo` y `DatosDelMensaje`,
  en `backend:…/points/domain/model/semaforo/mensaje/`, sin Spring ni JPA.

**Checkpoint**: migración y modelo listos.

---

## Phase 3: User Story 1 — Mensaje del día que le habla a la persona (P1) 🎯 MVP

**Goal**: `/home` trae `semaforo.mensaje` del color vigente, redactado, estable en el día y sin repetir en 14 días; la
tarjeta de Hoy lo muestra con su sticker.

**Independent Test**: una cuenta por color: el mensaje es del color, sin llaves, el mismo `id` en el día y otro
distinto al día siguiente (quickstart, Backend 2–3 y App 2–3).

### Tests (primero, en rojo)

- [x] T007 [P] [US1] `EleccionDelMensajeTest` en `backend:src/test/java/…/points/domain/model/semaforo/mensaje/`:
  - Filtra los mensajes por las variables disponibles.
  - Quita los vistos en 14 días; si se agotan, quita solo el de ayer; si sigue vacío, usa todos.
  - Elige uno con un azar fijo.
  - Si ninguno tiene datos, cae a uno del grupo sin variables; si tampoco hay, devuelve vacío.
- [x] T008 [P] [US1] `RedaccionDelMensajeTest`: reemplaza las 6 variables; el resultado nunca contiene `{` ni `}`;
  usa el primer nombre.
- [x] T009 [P] [US1] `HabitosQueFaltanTest`:
  - Con 6 días cerrados más hoy, T = 60 o 80, y redondeo HALF_UP a entero por día y a 1 decimal en el promedio (como
    `ReglaDelSemaforo`), da el `k` mínimo.
  - Devuelve vacío si `k ≤ 0`, si `k > P − C` o si el color es VERDE.
  - Un caso borde con 79,9.
- [x] T010 [P] [US1] `CumplimientoPorHabitoFinderIT` en `backend:src/test/java/…/habits/`, con Testcontainers:
  - Por hábito devuelve programados y cumplidos en un rango.
  - Los opcionales no cumplidos no cuentan.
  - El nombre visible respeta el renombre.

### Backend

- [x] T011 [P] [US1] `EleccionDelMensaje` y `RedaccionDelMensaje` en
  `backend:…/points/domain/model/semaforo/mensaje/` (método ≤ 20 líneas, clase ≤ 150).
- [x] T012 [P] [US1] `HabitosQueFaltan` en el mismo paquete, reusando `ReglaDelSemaforo` (sin duplicar umbrales).
- [x] T013 [US1] `CumplimientoPorHabitoFinder` en `backend:…/habits/api/`, con su implementación en
  `habits/application` y un puerto o adaptador que consulte `registros_habito` por rango; usa
  `ConteoDiarioHabitos.calificables()` para la regla de los opcionales.
  > **Hecho 2026-10-19 (E-630).** El finder se declara en `points.api` (no en `habits/api`: ciclo) y lo implementa `habits.application.services.CumplimientoPorHabitoFinderService`, con `ContarCumplimientoPorHabitoPort` + `CumplimientoPorHabitoJdbcAdapter`; la regla de «contaba» es `ConteoDiarioHabitos.calificables(total, opcionalesNoCompletados)`, ahora estática.
- [x] T014 [US1] Elegir flojo y fuerte en `backend:…/points/domain/model/semaforo/mensaje/HabitosDestacados.java`, con
  su prueba `HabitosDestacadosTest`:
  - Solo cuentan los hábitos con ≥ 2 días programados.
  - El fuerte necesita una tasa ≥ 50 %.
  - Un empate o un mismo hábito como flojo y fuerte deja el dato vacío.
- [x] T015 [US1] Puertos `CatalogoDeMensajesPort`, `MensajesElegidosPort` y `AzarPort` en
  `backend:…/points/application/ports/out/semaforo/mensaje/`.
- [x] T016 [US1] Adaptadores JPA en `backend:…/points/infrastructure/adapter/out/persistence/semaforo/mensaje/`:
  - Entidades `*JpaEntity` separadas del dominio, con mapper a mano.
  - `registrar` con `INSERT … ON CONFLICT DO NOTHING`, que después relee.
  - `AzarPort` con `SecureRandom`.
  > **Hecho 2026-10-19.** `JdbcClient` con mapeo a mano (como los tres adaptadores del semáforo), sin `*JpaEntity`. `AzarPort` con `SecureRandom` en `adapter/out/azar/`.
- [x] T017 [US1] `MensajesElegidosAdapterIT`, con Testcontainers:
  - Dos `registrar` concurrentes dejan una sola fila.
  - `desde(fecha)` devuelve los ids de los 14 días.
- [x] T018 [US1] `MensajeDelSemaforoService` en `backend:…/points/application/services/`:
  - Calcula la fecha con `clock.now()` en `ParticipacionPrograma.zona()`.
  - Toma el grupo del color vigente de `resumenParaHoy` (para US1, solo VERDE, AMARILLO y ROJO).
  - Reúne los datos: primer nombre por el finder de `users`, día, `FasePrograma.nombreVisible()`, destacados y `faltan`
    con `SemaforoDelDiaFinder`. *(Corregido 2026-10-19, SC-004: `faltan` con lo de hoy que `/home` ya leyó; nombre y
    destacados en memoria por día local; D-277.)*
  - Si ya hay elección del día y grupo, la reusa; si no, elige y registra.
  - Siempre redacta con los datos actuales.
  - Sin `@Transactional` alrededor de nada externo.
  > **Hecho 2026-10-19.** El grupo sale de `GrupoDelDia` desde el principio (T018 + T030 juntas); el servicio recibe lo que `/home` ya leyó (participación y resumen del semáforo) en vez de releerlo. Retoques de redacción documentados en D-277.
- [x] T019 [US1] Agregar `nombreVisible()` a `backend:…/users/api/FasePrograma.java`, con los nombres que muestra la
  app, y su prueba.
- [x] T020 [US1] Integrar en `HomeAgregadoService`: `semaforo.mensaje` solo cuando `semaforo` no es null. Un fallo al
  elegir se registra en el log y deja `mensaje` en null, sin romper `/home`.
- [x] T021 [US1] Agregar `MensajeResponse { id, texto, sticker }` a `SemaforoResponse` en
  `backend:…/points/infrastructure/adapter/in/rest/home/ResumenHomeResponse.java`, según
  `contracts/home-semaforo-mensaje.md`.
- [x] T022 [US1] `HomeMensajeDelSemaforoIT`:
  - El reloj en la madrugada UTC da el día local anterior en Lima.
  - El mismo `id` se repite en dos lecturas del día.
  - Otro día da otro `id`, fuera de los 14 anteriores.
  - Si el color cambia, entra una fila nueva y el mensaje es del color nuevo.
  - El texto no lleva llaves.
  - `{faltan}` baja al cumplir un hábito.
  - Costo (SC-004): `/home` con mensaje hace como mucho 3 consultas más y 1 escritura por día y persona.

### App

- [x] T023 [P] [US1] Prueba `app:src/features/semaforo/api/__tests__/mensajeDelSemaforo.test.ts`:
  - El esquema acepta un `mensaje` válido.
  - Con `null`, ausente o mal formado da `mensaje: null` sin perder la tarjeta.
  - Un `sticker` desconocido queda como texto solo.
- [x] T024 [P] [US1] Agregar el tipo `MensajeDelSemaforo` y `mensaje: MensajeDelSemaforo | null` en `SemaforoDeHoy`
  (`app:src/features/semaforo/types/semaforo.types.ts`), y `.nullish().catch(null)` en `semaforoDeHoySchema`
  (`app:src/features/semaforo/api/semaforoSchemas.ts`). Agregar solo el campo, sin reordenar nada (la spec 005 suma
  `puedeAbrirse` en grupos).
- [x] T025 [P] [US1] Crear el mapa `nombre → require('…webp')` de los 15 en
  `app:src/features/semaforo/data/stickersDelSemaforo.ts`, con la prueba `stickersDelSemaforo.test.ts`: cada sticker
  del JSON del catálogo existe en el mapa y en `assets/stickers/semaforo/`.
- [x] T026 [US1] Crear el componente `app:src/features/semaforo/components/MensajeDelSemaforo.tsx`:
  - Sticker de 56 px con `expo-image` y texto Jost 14–15 px.
  - `flexShrink: 1`, contraste del tema en claro y oscuro.
  - Fundido de 180 ms; con «reducir movimiento», sin animación (cargar las skills `emil-design-eng` y `animate-expo`).
  - Si la imagen falla, queda solo el texto.
- [x] T027 [US1] En `app:src/features/semaforo/components/TarjetaSemaforoHoy.tsx`:
  - Montar `MensajeDelSemaforo` debajo de `EtiquetaSemaforo`, sin tocar lo demás de la tarjeta (respetar el `if` de
    disponibilidad de la fase 2).
  - Sumar el texto al `accessibilityLabel`.
  - Con `mensaje: null`, la tarjeta queda idéntica a hoy (`semaforoPlegadoYTarjeta.test.ts`).

**Checkpoint**: el MVP se puede probar solo.

---

## Phase 4: User Story 2 — Mensaje en los días sin color (P2)

**Goal**: INICIO, SIN_PROGRAMA, PAUSA, CUENTA_PAUSADA y GRADUADO reciben un mensaje de su caso.

**Independent Test**: una cuenta por caso; el `id` empieza con `s` y es del caso correcto (quickstart, Backend 4).

- [x] T028 [P] [US2] `GrupoDelDiaTest`: el orden de research R3 (suspendida, graduado, pausa, inicio, sin programa) y
  los colores en su grupo.
- [x] T029 [US2] `GrupoDelDia` en `backend:…/points/domain/model/semaforo/mensaje/`; exponer `programaCompletado` y
  `suspendido` por el finder de `users/api/` si no están (sin leer tablas ajenas).
  > **Hecho 2026-10-19 (E-631).** No hizo falta exponer `programaCompletado` ni `suspendido` por `users/api`: «terminó el programa» sale del calendario del semáforo (`ResumenParaHoy.programaTerminado`) y una cuenta suspendida recibe 403 antes de llegar. Hallazgo sobre `/home` después del día 90: sigue mandando `semaforo` (no es null), ver research R3.
- [x] T030 [US2] Usar `GrupoDelDia` en `MensajeDelSemaforoService` en lugar del color directo; los casos sin color no
  usan `{faltan}` ni `{habitoFlojo}`.
- [x] T031 [US2] Ampliar `HomeMensajeDelSemaforoIT` con un caso por grupo sin color, más «antes del Día 1», «día 91
  (graduado: llega `semaforo` con el mensaje GRADUADO aunque no haya medición)» y «reactivada con días
  CUENTA_SUSPENDIDA en la ventana y ninguno medido».

**Checkpoint**: todos los días hay mensaje.

---

## Phase 5: User Story 3 — Descargar la imagen del podio (P2)

**Goal**: Líder de mentores, Alquimista, Administrador y Soporte TI descargan o comparten una imagen 9:16 del podio.

**Independent Test**: Admin genera y comparte; Mentor y Aprendiz no ven el botón (quickstart, Backend 5 y App 4–6).

### Backend

- [x] T032 [P] [US3] Prueba por rol en `backend:src/test/java/…/mentor/`, `CapacidadExportarRankingIT`:
  - `canExportRanking` es `true` para MENTOR_LEAD, ADMIN, ALCHEMIST e IT_SUPPORT.
  - Es `false` para MENTOR, TRAINEE_GUIDE y TRAINEE.
  - Una cuenta `SUSPENDED` recibe 403 en `/mentor/context`.
- [x] T033 [US3] Agregar `EXPORT_RANKING` a `backend:…/shared/domain/Permission.java` (javadoc «Descargar la imagen del
  podio del ranking para compartirla») y concederlo en `backend:…/users/api/UserRole.java` a MENTOR_LEAD, ADMIN,
  ALCHEMIST e IT_SUPPORT. Ojo con el *fail-open* de `can()` (A-1): para MENTOR y TRAINEE_GUIDE el valor debe salir
  `false`, así que la capacidad se calcula con la matriz explícita, no con `can()`.
  > **Hecho 2026-10-19 (D-278).** Se concede en la matriz de MENTOR_LEAD y, para quitárselo a MENTOR y TRAINEE_GUIDE (falla-abierto), `UserRole.can` lo niega explícito. ADMIN, ALCHEMIST e IT_SUPPORT lo tienen por su matriz.
- [x] T034 [US3] Publicar `capabilities.canExportRanking` en la respuesta de `GET /api/v1/mentor/context` (DTO y caso de
  uso), según `contracts/capacidad-exportar-ranking.md`.

### App

- [x] T035 [P] [US3] Agregar `canExportRanking: z.boolean().nullish()` en
  `app:src/features/mentor/api/mentorSchemas.ts` y `exportarRanking` en
  `app:src/features/admin/hooks/useCapacidades.ts` (con `false` por defecto).
- [x] T036 [P] [US3] Prueba `app:src/features/ranking/utils/__tests__/podioParaCompartir.test.ts`:
  - Toma los 10 primeros de la tabla vigente.
  - Usa `textoDelPuntaje` por tabla.
  - Corta los nombres largos con «…».
  - Con menos de 10, solo los que hay; los lugares del podio sin persona quedan vacíos.
  - Fecha en español de Perú.
- [x] T037 [US3] Crear `app:src/features/ranking/utils/podioParaCompartir.ts` (puro).
- [x] T038 [US3] Crear `app:src/features/ranking/components/TarjetaDelPodioParaCompartir.tsx`:
  - 1080×1920, fuera de pantalla (`collapsable={false}`).
  - Fénix de `assets/imagenes/fenix-renaser.png`, título de la tabla, fecha, podio plano (oro, plata, bronce) y del 4.º
    al 10.º.
  - Colores de la marca; Fraunces solo en el título, Jost en el resto.
- [x] T039 [US3] Crear `app:src/features/ranking/hooks/useCompartirPodio.ts`:
  - `captureRef` (`react-native-view-shot`, PNG) y `Sharing.shareAsync` (expo-sharing).
  - En la web, un data URI y un `<a download="podio-<tabla>-<fecha>.png">`.
  - Cancelar no es error; un fallo muestra un `Alert` de `components/Alerta`.
  - Vibración `tacto.logro()` al terminar.
- [x] T040 [US3] En `app:src/screens/ComunidadScreen.tsx`, sección Ranking, solo esto:
  - Botón «Descargar» (`Presionable`, 48 px, ícono `share`) junto al `ControlSegmentado`, visible solo con
    `capacidades.exportarRanking`.
  - Monta `TarjetaDelPodioParaCompartir` oculta mientras se comparte.
  - Prueba en `app:src/screens/__tests__/rankingDescargar.test.ts`.

**Checkpoint**: las tres historias funcionan.

---

## Phase 6: Polish & documentación

- [x] T041 Anotar en `app:AGENTS.md` dos entradas «Cambio por pedido del dueño — 2026-10-19», con «lo que cambia y nada
  más»: una para el tab `Hoy` (tarjeta del semáforo) y otra para el tab `Comunidad` (Ranking → Descargar). Las dos
  dicen «Hace falta un APK nuevo y el backend con V110».
- [x] T042 [P] Escribir en `backend:docs/MODULOS_A_AVANZAR.md` §8 los D-nn reservados en T003, y anotar los E-nn de los
  errores encontrados en `backend:docs/BITACORA_ERRORES.md`.
- [x] T043 [P] Agregar el contrato aditivo de `semaforo.mensaje` a `backend:docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md`
  (§4.2), con una nota de que el aviso del sábado no cambia.
- [x] T044 Correr `node node_modules/typescript/bin/tsc --noEmit` y jest del área tocada; jest completo solo con la
  máquina libre.
- [x] T045 Correr `./mvnw clean verify` con la máquina libre y leer las líneas `Tests run:` de surefire y failsafe.
- [x] T046 Correr `graphify update .` en los dos repos.
- [x] T046b Regresión (FR-009): correr sin cambios las pruebas de `RedaccionDelSemaforo`, del detalle del semáforo
  (`SemaforoScreen`) y de las vistas del mentor y el líder; ninguno de esos archivos se modifica.
- [x] T047 Hacer el E2E del quickstart completo en el emulador (Maestro + mobile-mcp) contra el backend local y la base
  clonada, coordinado con las otras sesiones. Medir el tiempo de «Descargar» hasta la hoja
  de compartir (SC-005, < 3 s). Guardar las capturas en `specs/006-mensaje-semaforo-y-podio/e2e.md`.

---

## Phase 7: User Story 4 — El chat de las 23:50 también le habla (P2, D-281)

**Goal**: la tarjeta diaria del semáforo en el chat de soporte (D-223) lleva «Hoy llevas N % de tus hábitos.», una línea
en blanco y un mensaje personalizado del mismo catálogo (canal `CHAT`), distinto del de Hoy ese día. Sin sticker; la
imagen del color no cambia.

**Independent Test**: `TarjetaDelSemaforoIT` (texto compuesto, fila de canal `CHAT` con la fecha de Lima, distinto del de
Hoy) y `MensajeDelChatServiceTest`.

- [x] T048 [US4] Agregar la columna `canal` (`HOY` | `CHAT`, CHECK, default `HOY`) a `mensajes_semaforo_elegidos` dentro de
  `backend:src/main/resources/db/migration/V110__mensajes_del_semaforo.sql`, con la PK `(participante_id, fecha, canal,
  grupo)`, actualizando la cabecera. V110 no está integrada: se edita, no se crea V111.
- [x] T049 [P] [US4] Pruebas de dominio (primero): `CanalDelMensajeTest` (CHAT no admite `{faltan}`), `EleccionDelMensajeTest`
  (sin `{faltan}` en el chat, distinto del de Hoy, no repetir entre canales), `ModeloDelMensajeTest` (`MensajesVistos.de` con
  canal) y `GrupoDelDiaTest` (`delColor`).
- [x] T050 [US4] Dominio en `backend:…/points/domain/model/semaforo/mensaje/`: `CanalDelMensaje`, `ClaveDeLaEleccion`,
  `MensajeVisto` y `MensajesVistos` con el canal y `hoyEnElOtroCanal`, `EleccionDelMensaje` con el canal y la exclusión de
  lo que el otro canal mostró hoy, `GrupoDelDia.delColor`.
- [x] T051 [US4] `MensajesElegidosPort`/`MensajesElegidosJdbcAdapter` con `ClaveDeLaEleccion` (canal en la lectura, en el
  `INSERT … ON CONFLICT` y en la relectura); la lectura de 14 días suma los dos canales y trae lo del otro canal de hoy.
  `MensajesElegidosAdapterIT` con los casos del canal.
- [x] T052 [US4] `ElectorDelMensajeDelDia` (elegir + registrar, compartido), `MensajeDelSemaforoService` sobre él con el
  canal `HOY`, y `ConsultaDelMensaje.deHoy` como `Optional`.
- [x] T053 [US4] `points.api.MensajeDelChatFinder` y `MensajeDelChatService` (grupo del color del día, fecha de la tarjeta,
  sin `{faltan}`, idempotente), con `MensajeDelChatServiceTest` (incluye «distinto de Hoy» y 14 días entre canales) y los
  dobles compartidos `DoblesDelMensajeDelSemaforo`.
- [x] T054 [US4] `chat`: `TarjetaDelSemaforo.textoCon(mensaje)` (id del texto sin cambios) y `TarjetaEnSoporte` que pide el
  mensaje solo cuando el texto va a salir y cae al texto de siempre si falla o no hay. `TarjetaDelSemaforoTest` y
  `TarjetasDelSemaforoServiceTest` (con mensaje, sin mensaje, falla, ya enviado).
- [x] T055 [US4] `TarjetaDelSemaforoIT`: el texto compuesto con el catálogo real de V110, la fila de canal `CHAT` con la
  fecha de Lima, el mensaje distinto del de Hoy y el de ayer.
- [x] T056 [P] [US4] Documentación: D-281 en `backend:docs/MODULOS_A_AVANZAR.md` §8, la tarjeta diaria en
  `backend:docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md` §1.3, spec (FR-014 a FR-018, User Story 4, clarificación),
  data-model, research R11 y `contracts/chat-tarjeta-diaria-mensaje.md`.
- [x] T057 [US4] E2E en el emulador/base clonada: dejar correr la tarjeta de las 23:50 de una cuenta con soporte y leer el
  texto en el chat (la app no cambia). Se hace con la máquina libre, junto con T047.

---

## Dependencies & Execution Order

- **Setup (T001–T003)**, después **Foundational (T004–T006)**, después las historias.
- **US1 (T007–T027)** es el MVP. **US2 (T028–T031)** depende del servicio de US1 (T018). **US3 (T032–T040)** es
  independiente de US1 y US2 y puede ir en paralelo desde el Checkpoint 2.
- Dentro de cada historia: tests → dominio → puertos y adaptadores → servicio → REST → app.
- **Polish (T041–T047)** al final; T045 y T047 esperan a la máquina libre.
- **US4 (T048–T057, D-281)** depende de V110 y del servicio de US1; no toca la app.

## Parallel Opportunities

- T002, T003, T005 y T006 en paralelo.
- US1: T007–T010 juntos; T011 y T012 en paralelo; la app (T023–T025) en paralelo con el backend desde T021, porque el
  contrato está fijo.
- US3 completo en paralelo con US1, porque toca otros archivos (agente aparte).

## Implementation Strategy

1. **MVP**: Setup, Foundational y US1. Demo con cuentas de cada color.
2. Sumar US2: el mensaje sale todos los días.
3. Sumar US3: la descarga del podio.
4. Polish, verify, E2E, APK nuevo y despliegue (primero el backend: la app nueva tolera el anterior).

Implementa: agentes Sonnet 5.5 en esfuerzo xhigh (uno para el backend de US1 y US2, otro para US3, otro para la app); la
sesión general supervisa y evita choques.

> **Cierre del 2026-10-19.** T045: `./mvnw clean verify` en verde, BUILD SUCCESS (surefire 6612, failsafe 497, sin
> fallos). T057: en la base clonada `renaser_semaforo`, la cuenta VERDE se pasó a UTC+2 y a las 16:50 de Lima (sus
> 23:50) el barrido mandó «Hoy llevas 67 % de tus hábitos.» con un mensaje AMARILLO del catálogo (fila `CHAT`).
> Maestro `33-chat-tarjeta-diaria.yaml` lo vio en el chat de soporte. Sin almacenamiento real (`noop`) no hay imagen
> del color, como ya pasaba (G-5). Maestro de la 006: 15 de 15 flujos más el 33. La app también suma el arreglo del
> Código Renaser (rama aparte `codigo-renaser-sin-parpadeo`, `.maestro/codigo-renaser/40-hora-ya-registrada.yaml`).
