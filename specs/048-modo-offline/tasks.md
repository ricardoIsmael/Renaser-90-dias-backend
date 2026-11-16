# Tareas — spec 048, fase 1 (una en curso a la vez)

## Servidor
- [x] T01 V188 `envios_sin_red` + IT de la migración.
- [x] T02 `CompletarRegistroCommand`/request con `hechoEn`, `idCliente`, `origenDeLaHora`; respuesta con `motivoSinPuntos`.
- [x] T03 `RegistroHabito.completarHechoSinRed` (admite `EXPIRADO`) y `RegistroService.completar` con hora real, reglas
      de D14 e idempotencia. Pruebas por cada caso del plan.
- [x] T04 Confirmar evidencia con `hechoEn` en regla sobre un registro `EXPIRADO`.

## App
- [x] T05 La sesión no se borra por falta de red; perfil guardado por cuenta (FR-001–FR-003).
- [x] T06 Estado de conexión + ícono y globo (FR-006).
- [x] T07 Módulo nativo del reloj desde el encendido + hora real (FR-013).
- [x] T08 Cola por cuenta (FR-007, FR-009).
- [x] T09 Marcar hábito sin red, con texto o foto (FR-010–FR-012).
- [x] T10 Lecturas guardadas de Hoy, hábitos de hoy y Plan (FR-004).
- [x] T11 «Necesitas conexión» (FR-005).
- [x] T12 Audios de hoy y mañana (FR-017).
- [x] T13 Aviso al cerrar sesión con pendientes (FR-009).
- [x] T14 E2E con modo avión, logs, APK.

## E2E de la fase 1 (2026-11-09, emulador Pixel_6, APK debug con `renaser-reloj`, backend de la rama sobre `renaser_e2e_048`)

Sin señal = túnel del backend cortado (en modo avión el build debug no alcanza a Metro; el APK de producción trae el JS).

- Abrir la app sin señal entra a Hoy sin login; ícono con «Sin conexión desde las 14:01» (FR-001, FR-006).
- Entrenar se ve con lo guardado; Despertar marcado sin señal queda hecho («3 de 17»), sin puntos hasta enviarse (FR-004, FR-011).
- SER: «Necesitas conexión para usar esto» (FR-005).
- Al volver la señal, a los 15 s: Despertar COMPLETADO con 10 puntos, `completado_en` = la hora real (19:03:13Z) aunque
  llegó 19:08, `[offline] … RELOJ_DEL_SISTEMA … en regla` en el log (FR-013, FR-014, FR-008).
- Foto sin señal (Agua tibia con limón): la foto sube a S3 y DESPUÉS se cierra, con la hora real; 0 puntos porque era de las
  06:00 (misma regla que con red) (FR-012).
- Hallazgos corregidos en el mismo E2E: el check se destildaba con la relectura; Entrenar no se releía al confirmarse; el
  ícono tapaba el botón de planificar; SER no recargaba solo al volver la señal.

## E2E completo de la fase 1 (2026-11-09, tarde)

Sin señal = túnel cortado **y la app reiniciada**: si solo se quita el túnel, las conexiones ya abiertas siguen vivas y la app
cree que hay señal (artefacto de la prueba, no de la app).

- Sin señal: Hoy, Plan, Yo y Ajustes completos; Escritura libre, Ritual noche y Ritual mañana con texto, Gimnasio con foto y
  Última comida con check quedan hechos al instante; la Pastilla abre su hoja; Muro, composer, Eventos, SER, Planificar y la
  Clase dicen «Necesitas conexión».
- Cerrar sesión con 2 pendientes: «Tienes cosas sin enviar… Quedarme / Salir igual» (FR-009).
- Cerrar la app con la cola llena y abrirla con señal: llega en 10 s, una sola vez (`envios_sin_red`), con la hora real.
- Al volver la señal: los puntos llegan con la hora real (Última comida 10 pts a las 16:14:03); SER, el Muro y Entrenar se
  recargan solos. Logs: backend sin errores; la app solo con avisos de lecturas que fallan sin señal.
- Hallazgos corregidos: la sesión se borraba al abrir sin red; las lecturas se guardaban por huella del token (ahora por
  cuenta); el texto de un ritual llegaba dos veces (carrera de la cola); Planificar, la Clase y el Muro mostraban el error
  crudo; SER, el Muro, Planificar y la Clase no se recargaban al volver la señal (prueba `recargaAlVolverLaSenal`); Entrenar
  sin señal mostraba pendiente lo ya confirmado (la lectura guardada se corrige al confirmar).

## Pendiente conocido (no bloquea la fase 1)
- La Clase diaria (video) y la Pastilla se entregan solo con señal (su propio camino); el audio de mañana normalmente no tiene URL
  hasta desbloquearse, así que en la práctica se baja el de hoy.
- iPhone: sin el módulo del reloj, usa el reloj del teléfono corregido (`TELEFONO_CORREGIDO`).
- Flujo de Maestro de la fase 1: no escrito todavía (el E2E se hizo con mobile-mcp).


# Tareas — fase 2 (el Muro sin red)

## Servidor
- [ ] T15 V189 `publicaciones_sin_red` + IT de la migración.
- [ ] T16 `POST /wall` con `hechoEn`/`idCliente`/`origenDeLaHora`, idempotente; `PublicacionCreadaEvent.hechoSinRedEn`.
- [ ] T17 `PostDiarioComunidadHabitoService` cierra por la hora real; `existeDeAutorEntre` cuenta la hora real.
- [ ] T18 `PublicarEnElMuroSinRedIT`.

## App
- [ ] T19 Envío `publicarMuro` en la cola (fotos copiadas, subida y publicación con la hora real).
- [ ] T20 La publicación en espera en el Muro con «Borrar»; el compositor abre sin red (categorías guardadas).
- [ ] T21 El «Post diario» de Entrenar hecho al instante.
- [ ] T22 E2E en el emulador, logs y APK.


# Tareas — fase 3 (Plan, Cierra tu día, Código Renaser y planificar hábitos y alarmas, FR-019)

Contrato en `plan.md` («Plan técnico — fase 3»). Servidor y app se pueden hacer en paralelo: la app contra el contrato, el servidor contra sus
pruebas. Una tarea en curso a la vez por lado. Los tramos (A núcleo + Cierra tu día + Código Renaser, B Plan, C planificar hábitos) se pueden
fusionar por separado si el dueño lo pide; el APK sale con el último. Decisiones P1–P9 del plan: se anotan en `spec.md` como D20+ cuando el dueño
responda, antes de cerrar la fase.

## Servidor (`Renaser-90-dias-backend`, rama `048-fase-3`)

### Tramo A — núcleo, Cierra tu día, Código Renaser y Mi Radar
- [x] T23 `V190__envios_sin_red_de_todo_lo_hecho_sin_red.sql` y `V191__mi_radar_actualizado_en.sql` (columnas, CHECK de `resultado`/`codigo`, índice
      parcial de no aplicados, cabecera con el porqué) + `EnviosSinRedGeneralIT` (migraciones y dos pedidos concurrentes con el mismo `X-Id-Cliente`).
- [x] T24 Núcleo común: `HechoSinRed`/`OrigenDeLaHora` a `shared.domain`; `HandlerMethodArgumentResolver` de las tres cabeceras (`shared/web`);
      `RevisionDeLaHora` (futuro 2 min, 48 h, `referencia`, día del programa a la hora real); `habits.api.EnviosSinRedPort` generalizado con
      `EnviosSinRedJdbcAdapter` (`tipo`, `recurso`, `resultado`, `codigo`, `mensaje`, candado `pg_advisory_xact_lock`); `NoAplicado` → 409
      `ApiErrorConCodigo` con los cinco `codigo`; línea `[offline]` sin textos. Pruebas unitarias y `ArchitectureTest`; la fase 1 sigue verde.
- [x] T25 Cierra tu día (`PUT /rocks/day-closure`): `CierreDelDiaService.cerrar` con `referencia` (`CierreDelDiaController`,
      `CerrarElDiaUseCase`, `CierreDelDia`, `EvaluacionDelDiaService.dejarCerrado`); acciones creadas después de `hechoEn` no se exigen; confirmar que
      `guardarSoloElCierre` mueva `actualizado_en`. `CierreDelDiaSinRedServiceTest`, `CierreDelDiaControllerTest` (cabeceras, 409, 403) y `CierreDelDiaSinRedIT`.
- [x] T26 Código Renaser (`POST /radar`): `RadarService.registrar` con `referencia`; `AgendaDelRadar.delDia(…, fecha)`;
      `LoadRegistroRadarPort.deParticipanteEnLaHora`; `creado_en`/`horaPrevista` de la hora real; `MI_RADAR_NO_APLICA`, `YA_ESTABA_HECHO`.
      `CodigoRenaserSinRedServiceTest`, controller y `CodigoRenaserSinRedIT`.
- [x] T27 Mi Radar (`PUT /radar/mi-radar`): `MiRadarService.configurar` con `referencia` y `mi_radar_actualizado_en`; choque por la hora lógica.

### Tramo B — Plan
- [x] T28 Agendar acciones (`POST /rocks/plan`): `RocaDiariaService.crear` con `referencia` (ventana, fechas, semanas), reemplazo de un día futuro con choque,
      `FUERA_DE_SU_DIA` y `YA_ESTABA_HECHO`. Prueba de servicio y de controller.
- [x] T29 Marcar una acción (`POST /rocks/{id}/evidence`): `RocaDiariaService.completar` con `referencia` (EXIF, `EscalaPuntosRoca`, evento), 48 h / hora futura /
      fuera de su día sin puntos, `RECURSO_YA_NO_EXISTE`, ya hecha → 200 `YA_ESTABA_HECHO`; `RocaDiariaResponse.motivoSinPuntos` (aditivo) y su lectura.
- [x] T30 Plan semanal (`POST /rocks/weekly`, `PATCH /rocks/weekly/{id}`, `PATCH …/review`): `RocaSemanalService` con `referencia` y choque por `actualizado_en`.
- [x] T31 Objetivo de 90 días y del mes (`PUT /rocks/master/{eje}`, `PUT /rocks/monthly/{eje}/{mes}`): `RocaMaestraService.definir` y
      `RocaMensualService.definir` con `referencia` y choque. `PlanDeRocasSinRedIT` (agendar, marcar, semanal, maestra y mensual, con el reloj en una hora UTC del día local anterior).

### Tramo C — planificar hábitos y alarmas
- [x] T32 Preferencias de horario y alarmas (`PATCH /habit-preferences/{id}`, `…/days/{date}`, `…/weekdays/{d}`, `…/reminder`, `…/alarm`, `DELETE …/active`):
      `PreferenciaHorarioService` con `referencia` (ventana «ya arrancó», cuota, semana libre); `PUT`/`DELETE` idempotentes; choque por `actualizado_en`.
- [x] T33 Plan de la semana, repetir semana, desbloqueos y hábito personal (`PUT/DELETE …/week`, `POST …/week-repeat`, `DELETE …/weeks/{w}`,
      `PUT/PATCH/DELETE /habit-unlocks/{id}`, `POST /habits`): `PlanDeLaSemanaService`, `RepeticionDeSemanaService`, `DesbloqueoHabitoService`,
      `HabitosPersonalesService`. `PlanificarHabitosSinRedIT`.

### Cierre del servidor
- [x] T34 Docs en el mismo cambio: contrato en `docs/api/` y `docs/MAPA_ENDPOINTS.md` (cabeceras y los cinco `codigo`), decisión D-NN en `docs/MODULOS_A_AVANZAR.md` §8,
      `.arnes/PROGRESO.md`, marcas `// spec 048 FR-019` en las pruebas; `scripts/verificar.sh` en 0; `graphify update .`.

## App (`Renaser-90-dias-frontend-`, rama `048-fase-3`)

- [ ] T35 Cola: `cabecerasSinRed.ts`, envío genérico `escritura` y `completarAccion`, `escribirOEncolar` con respuesta optimista y orden por `hechoEn`;
      `desenlaceDe` trata un 409 con `codigo` como final (sale de la cola y anota el aviso) y un 4xx de otro tipo también termina en aviso
      (`colaSinRed.ts`, `escrituraSinRed.ts`). Jest: `colaSinRed.test.ts`, `cabecerasSinRed.test.ts`. **Abrir antes el issue de bug de R6.**
- [ ] T36 Avisos de lo que no se aplicó: `avisosDeLoNoAplicado.ts`, `components/AvisoDeLoNoAplicado.tsx`, globo en `AnfitrionSinRed.tsx` y `textoDelGlobo.ts`; se borran al
      cerrar sesión; también muestran lo bloqueado y el `motivoSinPuntos` de las fases 1 y 2. Jest `avisosDeLoNoAplicado.test.ts`.
- [ ] T37 Lecturas guardadas y ajustes de lectura nuevos: rutas de Radar y de preferencias/desbloqueos de hábitos (`lecturasGuardadas.ts`); ajuste por área sobre lo
      que espera en la cola. Jest `lecturasSinRed.test.ts`.
- [ ] T38 Cierra tu día sin red (`cierreApi.ts`, `cierreSinRed.ts`, `useHojaDelCierre.ts`): encola con el día de la hora real, conserva el borrador hasta que sale,
      queda «cerrado» al instante. Jest `cierreSinRed.test.ts`.
- [ ] T39 Código Renaser y Mi Radar sin red (`radarApi.ts`, `radarSinRed.ts`, `useRadarDelDia.ts`, `useConfigDeMiRadar.ts`, `CodigoRenaserModal.tsx`, `RadarContext.tsx`): sin
      `SoloConConexion`, el innegociable vuelve a abrirse solo (P8), la hora queda respondida al instante. Jest `radarSinRed.test.ts` y ajuste de `radarSinSenal.test.ts`.
- [ ] T40 Plan, acciones sin red (`objetivosApi.ts`, `planSinRed.ts`, `sellarRocaDiaria.ts`, `useRocasDiarias.ts`): agendar encola («Se agendará al volver la señal»);
      marcar con texto o foto sale hecha al instante, apaga sus alarmas y llega con sus puntos. Jest `planSinRed.test.ts`.
- [ ] T41 Plan, objetivos sin red (`objetivosApi.ts`, `planMensualApi.ts`, `useRocasSemanales.ts`, `useRocasMaestras.ts`, `usePlanMensual.ts`): semana, mes y 90 días con
      respuesta optimista. Jest en `planSinRed.test.ts`.
- [ ] T42 Planificar hábitos y alarmas sin red (`habitsApi.ts`, `planificarSinRed.ts`, `PlanificarDimensionModal.tsx`, `SeccionAlarmas.tsx`, `cambioDeHora.ts`): sin `SoloConConexion`,
      las escrituras pasan por `escribirOEncolar`, las alarmas locales se reprograman al instante. Jest `planificarSinRed.test.ts`.
- [ ] T43 `docs/decisiones/transversal.md` (entrada de la fase 3, con P1–P9 resueltas), `.arnes/PROGRESO.md`, marcas `// spec 048 FR-019`; `npm run verificar` en 0; `.arnes/scripts/grafo-al-dia.sh`.

## E2E de la fase 3
- [ ] T44 E2E en el emulador con el túnel cortado y la app reiniciada (casos 1 a 7 del plan), con el backend de la rama sobre `renaser_e2e_048`, log del backend y
      `adb logcat` revisados; flujo de Maestro de lo que se pueda (`# spec 048 FR-019`); APK del día instalado. Todo hallazgo se corrige con su issue.

## Addendum #186 — la Pastilla Renacer sin señal con su hora real
- [x] T45 Servidor: `POST /spirit-audio/submit` acepta las tres cabeceras (`PastillaSinRedService`, `RegistroEspiritu.entregarSinRed`, `CompletarPastillaRenacerUseCase.completarSinRed`); sin ellas, igual que antes.
      `PastillaSinRedServiceTest`, `EspirituControllerSinRedTest`, `RegistroEspirituTest`, `PastillaRenacerHabitoServiceTest`, `EntregarPastillaSinRedIT`. D29, D-341, E-677.
- [ ] T46 App: `pastillaSinRed.ts` manda las cabeceras y avisa lo no aplicado y lo sin puntos (`pastillaSinRed.test.ts`); `docs/decisiones/transversal.md`.
