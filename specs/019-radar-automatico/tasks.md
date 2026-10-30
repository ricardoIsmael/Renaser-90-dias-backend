# Tasks: Mi Radar automático y personalizado

Estado al 2026-10-30: **[x]** hecho y probado · **[ ]** pendiente. Alcance: solo Mi Radar desde el día 8 y lo justo en Hoy y las
fichas. No se toca Training, Plan, los días 1 a 7, los hábitos ni otras pantallas.

## Backend (`019-radar-automatico`)

- [x] T001 V122: `mi_radar_encendido`, `dias_mi_radar` con `{dia, modo, veces, sinRadar}` y fuera `horas_mi_radar` (la V119 no se toca)
- [x] T002 `DiaDeMiRadar` y `MiRadar(encendido, dias)`: día 1–7 sin repetir, modo, veces de 1 a 21, solo lo que no es de fábrica
- [x] T003 `HorarioDelRadar` puro: `horasQueCaben`, `repartir`, `planDelDia`, `esEditable`, `lunesDeLaSemanaMostrada`,
      `proximaFecha`, `puedeRegistrar`, `horaPrevista`; `DiaDelRadar` y `PlanDeMiRadar`
- [x] T004 `HorarioDelRadarTest` y `MiRadarTest`: 05:30–22:30 da 17; 06:00–22:00 da 06–21; 02:00–23:30 da 03–23; sin Despertar o
      Dormir o Dormir ≤ Despertar, ninguna; 6 de 17 da 06, 09, 12, 16, 19, 22; extremos del reparto; hoy y lo pasado (viernes, lunes,
      domingo)
- [x] T005 `users.api.MiRadarGuardadoPort` + `MiRadarGuardadoJdbcAdapter` (renombra el de las horas) y `MiRadarPersistenceAdapter`
- [x] T006 `AgendaDelRadar` (lee `HorarioDelDiaFinder` de la próxima fecha de cada día) y `MiRadarService` (consultar y configurar:
      solo lo tocado, 409 si cambia hoy o un día pasado, 400 si no cumple o no caben)
- [x] T007 `RadarService.registrar` con las horas de hoy (409 «apagado», «Hoy no tienes Mi Radar», «03:00 a 23:59»); los días 0 a 7
      no leen el horario
- [x] T008 `LecturaDelRadar` y `CodigoRenaserDePersonaService`: `miRadar` con el día de hoy para el historial y las tres fichas
- [x] T009 `GET/PUT /radar/mi-radar` (`MiRadarResponse`, `GuardarMiRadarRequest`) y la respuesta de liderazgo
- [x] T010 Pruebas: `MiRadarServiceTest`, `RadarServiceTest`, `RadarControllerMiRadarTest` (`@WebMvcTest`), `RadarMiRadarIT`,
      `MiRadarGuardadoJdbcAdapterTest`, `MiRadarPersistenceAdapterTest`, `CumplimientoDelLiderIT`, fichas del mentor y del líder
- [x] T011 `ArchitectureTest`, `EndpointAuthorizationDeclarationTest`, `FuncionalidadDeclaradaTest`, `RutasCubiertasPorElFiltroTest`
- [x] T012 Documentar: D-292 (`docs/MODULOS_A_AVANZAR.md`), `docs/MODULO_HABITS.md` §8.4, `docs/api/CONTRATO_DIA_A_DIA.md`, y
      «Corregido 2026-10-30» en las specs 012 y 016
- [x] T013 `./mvnw clean verify` completo

## App (`019-radar-automatico`)

- [x] T020 Contrato: tipos, `miRadarSchema` tolerante (un backend anterior es un error, no un apagado) y `guardarMiRadar` con solo lo tocado
- [x] T021 `utils/vecesDeMiRadar.ts` (`horasQueCaben`, `repartirHoras`, borrador, `cambiosParaGuardar`, «Hora») y `utils/miRadar.ts`
      (`horasDelDia` sobre los días resueltos, `horasDeAviso` semanales, `textoDeMiRadar`)
- [x] T022 `MiRadarScreen` nueva (modo, «Hora», «Veces al día», fila de días con las veces, «Sin Radar estos días», «Guardar · …»,
      «Programar mi Despertar») y se borran los componentes de horas a mano
- [x] T023 Alarmas: una semanal por hora y día (`renaser.radar.slot.d{1..7}.HH`); se relee al abrir la app y al volver a primer plano
- [x] T024 Hoy (mitad del Radar y hoja «Tu Radar de hoy») y las fichas (`CodigoRenaserDelAlumno`) con el horario y las veces de hoy
- [x] T025 `AGENTS.md`: la entrada del 2026-10-30 después de la 018 y «Corregido 2026-10-30» en las de la 012 y la 016
- [x] T026 `tsc --noEmit` y Jest
- [x] T027 Maestro: `.maestro/mi-radar/` ajustado a los dos modos y corrido en el emulador, con capturas (ver el informe)

## Anotado, no hecho (fuera de alcance)

- **«Programar mi Despertar» lleva a la pestaña Training, no al hábito dentro de Planificar:** no existe un parámetro para abrir
  Planificar en un hábito y Training no se toca en esta spec.
- **Rehacer las alarmas al instante al cambiar el Despertar o el Dormir en Planificar** exigiría tocar Planificar: se rehacen la
  próxima vez que se abre la app o vuelve a primer plano.
