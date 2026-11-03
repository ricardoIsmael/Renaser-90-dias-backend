# Tasks: Acuerdo de transformación (spec 039)

> **Corregido 2026-11-02 (D-315):** T006/T007/T101/T102 se hicieron con el cronograma en la firma; el dueño lo sacó de los términos (los pagos son asíncronos). Se quitaron `CronogramaParaFirmarUseCase`/`Service`, `scheduleFingerprint`, `MiCronogramaDePagos` y la huella de la aceptación; ver `spec.md`.


Un commit por pieza. `[x]` = escrita; la verificación completa (`./mvnw verify`, Jest, `tsc`) está encolada y no corrió con el emulador prendido.

## Backend
- [x] T001 Spec, plan, tareas y contrato (`specs/039-acuerdo-de-transformacion/`).
- [x] T002 `ProgramaDelAcuerdo` (dominio) y su prueba.
- [x] T003 `ParticipacionPrograma.inicioParaMostrar/finParaMostrar`, `users.api.FechasDelProgramaFinder` y su servicio, con pruebas (90 días, 3 meses, 1 año).
- [x] T004 V162: columnas de la constancia, tres versiones del texto nuevo, descarte de borradores copia; idempotente.
- [x] T005 `TerminosDePrueba` (base V149) y ajustes de las pruebas de la spec 027.
- [x] T006 `CronogramaParaFirmarUseCase` y `ArmadorDelAcuerdo`; `agreement` en `GET /onboarding/terms`.
- [x] T007 La aceptación guarda lo mostrado, rechaza un inicio vencido y confirma el cronograma (misma huella que «Mi ingreso»); `agreement` en «releer» y en la constancia de Administración.
- [x] T008 `AcuerdoDeTransformacionIT`.
- [x] T009 D-312 en `docs/`.

## App
- [x] T101 Esquemas, API y pedido (`agreementStartDate`, `scheduleFingerprint`); el texto de respaldo pasa al nuevo.
- [x] T102 `MiAceptacion` y `MiCronogramaDePagos` (componentes y utils puros).
- [x] T103 `TerminosScreen` y `TerminosActualizadosScreen`.
- [x] T104 `TerminosAceptadosVista` y la constancia de Administración.
- [x] T105 Jest: sin cronograma, con cronograma y volver a aceptar.
- [x] T106 Maestro `acuerdo-de-transformacion/` y ajuste de los flujos que buscan textos viejos.
- [x] T107 `AGENTS.md`.

## Cierre
- [ ] T201 `./mvnw -B clean verify`, `tsc --noEmit` y Jest (encolados; ver el informe).
- [ ] T202 E2E en el emulador (Maestro `acuerdo-de-transformacion/`).
