# Tareas — spec 037 · Los pasos del teléfono llenan los kilómetros diarios

Estado al 2026-11-02. `[x]` = escrito en la rama; la columna «Verificado» dice qué corrió de verdad (ver `plan.md`, «Verificación»).

## Backend (`037-pasos-y-km`)

- [x] T001 Spec, plan, investigación, modelo de datos, contrato y quickstart (`specs/037-pasos-y-km/`).
- [x] T002 V161: `registros_habito` (+5 columnas, CHECK de origen ampliado y de coherencia), `habitos.meta_medicion`.
- [x] T003 Dominio: `OrigenMedicion.HEALTH_CONNECT`, `DispositivoDeMedicion`, `LecturaAutomatica`, `MedicionDiaria(valor, origen, lectura)`.
- [x] T004 `PoliticaKilometros`: meta (5 km por defecto), tope (60 km y 150 000 pasos), `validarMedicionAutomatica`, `conRevision` («por revisar»).
- [x] T005 `RegistrarMedicionAutomaticaUseCase` + `MedicionAutomaticaService` (gesto `PROPIO_DEL_HABITO`, idempotente, lectura de ese día en la zona de la persona).
- [x] T006 `POST /habit-tracks/{id}/auto-measurement` (`MedicionAutomaticaController`, `AutoMeasurementRequest`, `RegistroHabitoResponse.medicion`).
- [x] T007 Persistencia: columnas en `RegistroHabitoJpaEntity` y su mapper; `MetaDeMedicionPort` + adaptador JDBC.
- [x] T008 Meta: `MetaDeMedicionAdminService`, `GET/PUT /admin/habits/{id}/measurement-goal`; la agenda de hoy trae `medicion.meta`, `origenDelDia`, `fuenteDelDia`.
- [x] T009 Ficha del acompañamiento: `ObligacionHabito.medicion` → `ObligacionDelDia.medicion` (mentor y líder/Administración).
- [x] T010 Ranking: `MedicionAcumuladaFinder.conKilometrosAutomaticos` → `kilometrosAutomaticos` en `GET /ranking`.
- [x] T011 Pruebas unitarias: `PoliticaKilometrosAutomaticaTest`, `MedicionDiariaTest`, `MedicionAutomaticaServiceTest` (con el reloj en la madrugada UTC), `MetaDeMedicionAdminServiceTest`, `MedicionesDelDiaConMetaTest`, `SeguimientoServiceTest`, `RankingAgregadoServiceTest`.
- [x] T012 Integración: `MedicionAutomaticaIT` (200, idempotencia, meta, tope «por revisar», 403, 409, 400, agenda, ranking, restricciones de la base, meta de Administración).
- [x] T013 Documentación: D-310, `MODULO_HABITS` §30, `CONTRATO_DIA_A_DIA`.
- [ ] T014 `./mvnw clean verify` en verde — **pendiente de correrse** (ver el informe: la máquina estaba tomada por un E2E).

## App (`037-pasos-y-km`)

- [x] T020 Dependencias revisadas y fijadas: `react-native-health-connect@4.1.3`, `expo-build-properties@~57.0.22`.
- [x] T021 `app.json`: permisos `READ_STEPS` y `READ_DISTANCE`, `minSdkVersion` 26; `plugins/withHealthConnect.js` (actividad de la política de privacidad); `eas.json`: perfil `debug-pasos`.
- [x] T022 `features/pasos/`: tipos, `fuentesDeSalud`, `lecturaDelDia`, `vistaDeKilometros`, textos, `saludConectada` (+ `.web`), conexión por cuenta, API, `usePasosDelDia`.
- [x] T023 UI: `LineaDeKilometros`, `HojaConectarPasos`, `HojaDeKilometros`, contexto `PasosDeLaLista`; `FilaDelDia`, `TrainingPorHora` y `TrainingScreen` cableadas.
- [x] T024 Fichas (`LineaDeLaMedicion`), ranking («· auto») y «Meta diaria de km» en Hábitos del programa.
- [x] T025 Política de privacidad (`public/privacidad/index.html`).
- [x] T026 Jest (13 archivos nuevos o ajustados), Maestro `.maestro/pasos-y-km/` (escritos, sin correr), `AGENTS.md`.
- [ ] T027 `tsc --noEmit` y Jest completo — **pendiente de correrse**.
- [ ] T028 Build nativo con EAS y prueba en un emulador Android 14 con Health Connect Toolbox — **lo arranca la coordinación**.

## Antes de publicar

- [ ] Declarar «Activity and fitness» (Steps y Distance) en Play Console, completar Data safety y revisar que la política enlazada sea la misma.
- [ ] Confirmar con el dueño los supuestos de la spec (meta de 5 km, qué cuenta el ranking, qué hacer con lo «por revisar»).
- [ ] Confirmar en un teléfono real los nombres de paquete de Samsung Health, Fitbit, Garmin Connect y Mi Fitness (`PAQUETES_CONOCIDOS`) y que los relojes lleguen con método de registro automático.
