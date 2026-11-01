# Tasks: Código Renaser con la voz

**Input**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/api.md`.
**Pruebas**: obligatorias (constitución III). Formato: `[ID] [P?] [US] Descripción — ruta`. `[P]` = se puede hacer en paralelo (otro archivo, sin dependencia).
Historias: US1 dictar · US2 si la voz falla, escribir · US3 escuchar la pregunta · US4 igual en las cuatro entradas · US5 «dictado» en el registro y en las fichas (decisión 030-2).

## Fase 1 — Backend: el registro dice «dictado» (US5) · repo backend, rama `030-codigo-renaser-con-audio`

- [x] T001 Migración `V132__codigo_renaser_dictado.sql`: `ALTER TABLE renaser.registros_radar ADD COLUMN dictado boolean NOT NULL DEFAULT false`, con cabecera que justifique (qué resuelve, por qué no alcanza el modelo, por qué ese nombre).
- [x] T002 Dominio `RegistroRadar`: campo `dictado`, `comoDictado()`, `rehydrate(... , dictado)`. Prueba unitaria en `RegistroRadarTest` (nace en `false`; `comoDictado` conserva todo lo demás; `conHoraPrevista` conserva `dictado`).
- [x] T003 Comando `RegistrarCheckInRadarCommand` + `boolean dictado`; `RadarService.registrar` marca el registro; `DiarioYRadarService` (SER) registra con `false`. Pruebas en `RadarServiceTest` (se guarda `dictado`; el segundo intento de la hora devuelve el primero con su valor) y `DiarioYRadarServiceTest`.
- [x] T004 Persistencia: `RegistroRadarJpaEntity.dictado`, `RegistroRadarPersistenceMapper` en los dos sentidos. Prueba en `RegistroRadarPersistenceAdapterTest` (Testcontainers): ida y vuelta con `true` y con `false` y registros previos con `false`.
- [x] T005 REST: `RegistrarCheckInRadarRequest.dictado` (opcional, `Boolean`), `RadarController` lo pasa, `RegistroRadarResponse.dictado`. Prueba en `RadarControllerMiRadarTest`: con `dictado:true` el comando lo lleva y la respuesta lo dice; sin el campo, `false`; la respuesta de `history` lo trae.
- [x] T006 Módulo `leadership` y mentor: `CodigoRenaserDePersonaFinder.RegistroDelRadar` + `dictado`, `CodigoRenaserDePersonaService`, `RadarDeLaPersonaResponse.RegistroResponse`. Pruebas en `RadarDePersonaServiceTest`, `CumplimientoDelLiderControllerTest` y `AcompanamientoDeAlumnoServiceTest` (el mentor lo lee por `RegistroRadarResponse`).
- [x] T007 Corrida focalizada con `-Xmx1500m` (dominio, servicio, controladores, persistencia, `ArchitectureTest`) y lectura de las líneas `Tests run:`.
- [x] T008 Documentar: D-297 en `docs/MODULOS_A_AVANZAR.md` §8, nota en `docs/MODULO_HABITS.md` (sección del radar) y en `docs/MAPA_ENDPOINTS.md` si lista los campos.

## Fase 2 — App: las voces y su generación (US3) · repo de la app, rama `030-codigo-renaser-con-voz`

- [x] T010 [P] [US3] `src/features/radar/vocesDelRadar.json` (campo, archivo, lo que dice) + prueba de que coincide con `PREGUNTAS_RADAR`.
- [x] T011 [US3] `scripts/sonidos/tabla_de_voces.py` y `generar_voces.py` leen también `vocesDelRadar.json`; generar los cuatro MP3 con `generar-sonidos.sh voces …` y verificar con Whisper.
- [x] T012 [US3] Agregar los cuatro MP3 a `app.json` (`sounds`) y a la lista que `sonidosElegibles.test.ts` espera; el tope de 2 MiB sigue en verde.

## Fase 3 — App: el dictado compartido (US1, US2)

- [x] T020 [US1/US2] `useDictado`: seguro de sesión propia (solo reacciona si él abrió el micrófono; un `empezar` con sesión abierta no abre otra; el cierre al desmontar solo aborta lo propio) y `destino` para el texto de los errores; `mensajeDeErrorDeVoz(codigo, destino)`. Pruebas con un doble del módulo de voz (nueva `useDictado.test.ts`): una instancia ajena no ve los eventos; los errores dicen «respuesta» o «mensaje»; sin módulo, `disponible` es `false`.
- [x] T021 [P] [US1] `utils/dictadoDelRadar.ts` (puro): sumar lo dictado sin pasar de 2 000, marcar/desmarcar respuestas dictadas, `hayDictado`. Prueba unitaria.
- [x] T022 [US1/US2] `hooks/useDictadoDelRadar.ts`: un micrófono a la vez; cambiar de pregunta cierra el primero, espera su texto y abre el segundo; el texto va al campo que tenía el micrófono; error en una línea. Pruebas con el doble de `useDictado`.

## Fase 4 — App: escuchar la pregunta (US3)

- [x] T030 [US3] `config/vozDelRadar.ts` (campo → `require` del MP3) y `hooks/useEscucharPregunta.ts` (una voz a la vez; el reproductor y el módulo `expo-audio` se crean/cargan al primer toque; se detiene al terminar, al tocar otra vez, al abrir el micrófono y al cerrar el formulario). Pruebas con un doble de `expo-audio`.

## Fase 5 — App: el formulario (US1–US4)

- [x] T040 [US4] `components/BotonDeVoz.tsx` y `components/VozDeLaPregunta.tsx`: «Escuchar» y «Dictar» de 48, ícono + palabra, estado anunciado (Detener / Parar), línea de volumen y línea de error; sin animaciones propias (`Presionable` y `tacto.seleccion()`).
- [x] T041 [US1–US4] `CodigoRenaserModal`: integra las dos cosas, el campo muestra lo que se va diciendo, queda editable, `dictado` viaja en `onEnviar` solo si es `true`; cierra voz y micrófono al ocultarse; el borrador guarda/recupera las respuestas dictadas (`storage/borradorRadar.ts`). Pruebas en `codigoRenaserModal.test.ts`: dictar suma sin borrar; no registra solo; cambiar de pregunta; el fallo del micrófono no pierde lo escrito; sin reconocimiento no hay micrófono pero sí «Escuchar»; `dictado` solo con dictado.
- [x] T042 [US4] Las cuatro entradas usan el mismo `CodigoRenaserModal` (obligatorio, Hoy, Mi Radar, aviso): prueba que los tres consumidores lo montan sin props de voz (misma forma en todos). `RadarContext.enviar` pasa `dictado` sin cambios.
- [x] T043 [US5] `radarSchemas`/`radar.types`: `dictado` tolerante. `CodigoRenaserDelAlumno`: «Dictado por voz. Puede haber errores de transcripción.» en cada registro dictado; prueba en `codigoRenaserDelAlumno.test.ts`.

## Fase 6 — Cierre

- [x] T050 FR-016: `public/privacidad/index.html` dice que el Código Renaser también se puede dictar con el reconocimiento de voz del teléfono y que el audio no se guarda; `paginaPoliticaDePrivacidad.test.ts` en verde.
- [x] T051 FR-018: entrada fechada 2026-10-31 en `AGENTS.md` del frontend (solo agregar).
- [x] T052 Maestro: `.maestro/codigo-renaser/41-dictar-y-escuchar.yaml` (abrir el Código Renaser, tocar «Dictar» y «Escuchar», ver los estados) + línea en el README. **Escrito, NO corrido** (sin emulador en este trabajo, por indicación del dueño).
- [x] T053 `tsc --noEmit` y Jest de todo lo tocado; `graphify update .` en cada repo; sin push.

## Dependencias

T001 → T004 · T002 → T003 → T005/T006 → T007 → T008 · T010 → T011 → T012 · T020 → T022 → T041 · T021 → T022 · T030 → T041 · T040 → T041 → T042 · T043 independiente tras T006 (contrato) · Fase 6 al final.
