# Validación — spec 040, «Meet automático en eventos» (backend)

Fecha: 2026-11-04. Rama `040-meet-automatico-impl` sobre `origin/master`. Nada de esto toca producción ni usa credenciales reales: Google está
detrás del puerto `SalaDeReunionPort` (adaptador real `GoogleCalendarMeetAdapter`, y en las pruebas el doble `GoogleSimulado` o un servidor HTTP de
mentira, `MockRestServiceServer`).

## Qué se corrió

| Qué | Cómo | Resultado |
|---|---|---|
| Todo `calendar`, `notifications`, `shared` y `plataforma` (unitarias, WebMvc y las `@SpringBootTest` contra Postgres de Testcontainers, V169 incluida) | `pesado.sh ./mvnw -o test -Dtest='com.renaser.os.calendar.**.*Test,…'` | 1 216 pruebas, 0 fallas, 0 errores |
| `ArchitectureTest`, `EndpointAuthorizationDeclarationTest`, `FuncionalidadDeclaradaTest` | ídem | verdes |
| Trazabilidad | `node scripts/trazabilidad-specs.mjs <app> --spec 040 --backend .` | ver el PR: todos los FR y SC con prueba; SC-001 exento |

No se corrió `clean verify` entero (solo los módulos afectados) ni nada contra el servidor local del 8080.

## SC-001 — la puerta del dueño (NO está pasada)

SC-001 es una prueba manual con la cuenta real `renasercorporation@gmail.com`, y el dueño decidió que incluye **una reunión de más de 60 minutos con 3 o más
personas** (FR-021). Este trabajo no la hizo ni puede hacerla (no hay credenciales ni cuenta). Por eso:

- `eventos.meet` nace **APAGADA** en el catálogo de Soporte TI (se esconde con 404) y los dos procesos de fondo saltan su turno.
- **No se prende en producción hasta anotar aquí el resultado de SC-001**: la hora del corte, o «no cortó» con la duración medida. Si Google la corta,
  se para y se avisa al dueño (P-PRUEBA60); la app no muestra ningún aviso de los 60 minutos (FR-021).
- Si el permiso reducido (`calendar.app.created`) no alcanza para alguna parte de la prueba, el cambio es la variable `EVENTOS_GOOGLE_PERMISOS`
  (`calendar.events`), sin tocar código.

Resultado de SC-001: _pendiente (lo anota el dueño o quien haga la prueba)_.

## Cobertura por requisito

| Requisito | Dónde se prueba |
|---|---|
| FR-001, FR-004, FR-020, FR-023 | `EnlaceDeMeetAlGuardarUnEventoTest`, `EventoConMeetControllerTest`, `EventoConEnlaceDeMeetAutomaticoTest`, `EnlaceDeMeetAutomaticoPersistenceTest` |
| FR-002, FR-003, FR-006, FR-007 | `GoogleCalendarMeetAdapterTest` (insert, `pending`, 409), `IdDeEventoDeGoogleTest`, `SalaMeetTest`, `SalasYCuentaDeGooglePersistenceTest` |
| FR-005, FR-017, FR-018, SC-012 | `SalasDeMeetDeUnaSerieTest`, `PlanDeSalasTest`, `OcurrenciasDeseadasTest` |
| FR-008, FR-013 | `AccesoYReintentoDeLasSalasTest`, `PoliticaDeReintentosTest`, `LimitadorDePedidosAGoogleTest` |
| FR-009, SC-005, SC-010 | `RevocacionYAutorizacionDeLasSalasTest`, `TokenDeAccesoDeGoogleTest`, `CuentaDeGoogleRevocadaNotificationListenerTest` |
| FR-010, FR-011, FR-034 | `ConectarLaCuentaDeGoogleTest`, `GoogleOAuthAdapterTest`, `AesGcmCifradoTest`, `SalasYCuentaDeGooglePersistenceTest` |
| FR-012, FR-016 | `SalasYCuentaDeGooglePersistenceTest` (V169 contra Postgres) |
| FR-014, SC-009 | `CatalogoDeFuncionalidadesTest`, `SalasDeMeetSchedulerTest`, `EnlaceDeMeetAlGuardarUnEventoTest` |
| FR-015, SC-008 | `ConectarLaCuentaDeGoogleTest`, `RevocacionYAutorizacionDeLasSalasTest` |
| FR-022 | `GoogleCalendarMeetAdapterTest`, `EnlaceDeMeetAlGuardarUnEventoTest` |
| SC-006 | `NingunSecretoEnLosRegistrosTest`, `AesGcmCifradoTest`, `CuentaDeGoogleControllerTest` |
| FR-035, FR-036, FR-037, SC-014, SC-015 (decisión 6, 2026-11-05) | `GrabacionYAnfitrionDeLaSalaTest` (con el Google simulado), `GoogleCalendarMeetAdapterTest` (attendees, `artifactConfig`, rechazo y pedido de a uno), `SalasYCuentaDeGooglePersistenceTest` (V171); en la app, `meetApi`, `enlaceDeMeetAutomatico` y `enlaceDeMeetEnElFormulario` (el aviso del plan) |
| FR-019, FR-021 | en la app (`Renaser-90-dias-frontend-`: `src/features/eventos` y `src/features/integraciones-google`; ver `enlaceDeMeetAutomatico`, `enlaceDeMeetEnElFormulario` y `paginaPoliticaDePrivacidad`) |
