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

## Pendiente conocido (no bloquea la fase 1)
- La tarjeta «No pudimos comprobar tu programa» de Hoy sin señal: esa lectura no está en la lista de guardadas (no es hueco
  en blanco, ofrece «Reintentar»).
- La Clase diaria y la Pastilla se entregan solo con señal (su propio camino); el audio de mañana normalmente no tiene URL
  hasta desbloquearse, así que en la práctica se baja el de hoy.
- iPhone: sin el módulo del reloj, usa el reloj del teléfono corregido (`TELEFONO_CORREGIDO`).
- Flujo de Maestro de la fase 1: no escrito todavía (el E2E se hizo con mobile-mcp).

