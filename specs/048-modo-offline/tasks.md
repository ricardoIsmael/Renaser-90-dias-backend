# Tareas — spec 048, fase 1 (una en curso a la vez)

## Servidor
- [x] T01 V188 `envios_sin_red` + IT de la migración.
- [x] T02 `CompletarRegistroCommand`/request con `hechoEn`, `idCliente`, `origenDeLaHora`; respuesta con `motivoSinPuntos`.
- [x] T03 `RegistroHabito.completarHechoSinRed` (admite `EXPIRADO`) y `RegistroService.completar` con hora real, reglas
      de D14 e idempotencia. Pruebas por cada caso del plan.
- [ ] T04 Confirmar evidencia con `hechoEn` en regla sobre un registro `EXPIRADO`.

## App
- [x] T05 La sesión no se borra por falta de red; perfil guardado por cuenta (FR-001–FR-003).
- [ ] T06 Estado de conexión + ícono y globo (FR-006).
- [ ] T07 Módulo nativo del reloj desde el encendido + hora real (FR-013).
- [ ] T08 Cola por cuenta (FR-007, FR-009).
- [ ] T09 Marcar hábito sin red, con foto (FR-010–FR-012).
- [ ] T10 Lecturas guardadas de Hoy, hábitos de hoy y Plan (FR-004).
- [ ] T11 «Necesitas conexión» (FR-005).
- [ ] T12 Audios de hoy y mañana (FR-017).
- [ ] T13 Aviso al cerrar sesión con pendientes (FR-009).
- [ ] T14 E2E con modo avión, logs, APK.
