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
