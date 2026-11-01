# Tareas: términos y condiciones editables (spec 027)

## Backend
- [x] T001 V148 `terminos_versiones` y `aceptaciones_de_terminos` (+ trigger de inmutabilidad)
- [x] T002 V149 versión 1 de la Escuela (las 23 cláusulas) y borradores en las otras dos · V150 lo ya aceptado pasa a la versión 1
- [x] T003 Dominio: `ContenidoDeTerminos`, `VersionDeTerminos`, `QuePideLaAceptacion`, `ReglasDeTerminos` + pruebas
- [x] T004 Puertos y adaptadores JDBC (`TerminosPort`, `AceptacionesDeTerminosPort`, `LiteralDeArreglo`)
- [x] T005 `ObtenerTerminosUseCase` (los de mi formación, marcadores, releer) y `GET /onboarding/terms[/accepted]`
- [x] T005b `GET /onboarding/terms/accepted` trae también la firma de la persona (`signatureUrl`, enlace de 15 min; `FirmaDeLaAceptacion`, compartida con la constancia de Administración)
- [x] T006 La constancia en el hito de Términos (`termsVersionId`, 409 si cambió)
- [x] T007 `GestionarTerminosUseCase` / `ConsultarTerminosUseCase` y `/admin/terms/**`
- [x] T008 `users.api.TerminosPublicados` + `IntegrantesDeFormacionFinder`; la 025 rechaza una formación sin términos
- [x] T009 `TerminosEditablesIT` (Postgres) y ajuste de `AceptarConFormacionIT`
- [x] T010 D-304

## App
- [x] T020 API y esquemas tolerantes; el hito con la versión; `useSellarTerminos`
- [x] T021 `TerminosScreen` con el texto del servidor (y el de la app como respaldo); `ContenidoDeLosTerminos`
- [x] T022 `TerminosActualizadosScreen` + `RootNavigator` (la pantalla que bloquea)
- [x] T023 Administración: lista, detalle con historial, editor, vista previa, publicar, quiénes aceptaron
- [x] T024 Constancia en la ficha de la persona; formación sin términos apagada en la hoja «Aceptar»
- [x] T025 Jest, `AGENTS.md` y flujos de Maestro
