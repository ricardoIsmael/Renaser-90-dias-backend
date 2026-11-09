# Plan técnico — spec 048, fase 1

## Servidor (este repo)

### `POST /api/v1/habit-tracks/{id}/complete` con lo hecho sin red (FR-008, FR-013–FR-016)

El cuerpo suma tres campos opcionales; sin ellos todo sigue como hoy:

| Campo | Tipo | Qué es |
|---|---|---|
| `hechoEn` | instante ISO-8601 | La hora real en que la persona lo marcó sin red. |
| `idCliente` | UUID | El identificador del envío; un reintento con el mismo vuelve el mismo resultado. |
| `origenDeLaHora` | `RELOJ_DEL_SISTEMA` \| `TELEFONO_CORREGIDO` | Cómo se calculó `hechoEn` (FR-013). |

`RegistroService.completar`, cuando trae `hechoEn`:

1. **Idempotencia** — tabla nueva `envios_sin_red (id_cliente uuid PK, participante_id, registro_id, creado_en)`
   (V188, con `SET search_path TO renaser, public;`). Si el `idCliente` ya está, se devuelve el registro tal como quedó,
   sin tocar nada.
2. **¿Está en regla?** `hechoEn` no está en el futuro (tolerancia de 2 min), cae dentro del día del registro (zona de la
   persona) y la llegada es a lo sumo 48 h después de `hechoEn`.
3. **En regla** — los puntos se calculan con `hechoEn` en vez de `clock.now()` (`ResultadoOtorgamiento.calcular`), y
   `completadoEn = hechoEn`. Se acepta aunque el día ya haya cerrado y el registro figure `EXPIRADO` por el barrido:
   el gesto nuevo `RegistroHabito.completarHechoSinRed` admite `PENDIENTE`, `EN_CURSO` y `EXPIRADO` (la excepción
   a D-259 la justifica `hechoEn`).
4. **No está en regla** — se registra igual con 0 puntos, y `motivoSinPuntos` lo dice
   (`LLEGO_DESPUES_DE_48_H`, `HORA_FUTURA`, `FUERA_DE_SU_DIA`).
5. **Ya estaba hecho** (por otro teléfono o con red) — no rebota: se devuelve el registro con
   `motivoSinPuntos = YA_ESTABA_HECHO`.
6. **No se puede aplicar** (hábito quitado, cuenta suspendida): se anota en el log con `[offline]` para Soporte TI y
   se responde con su código (lo muestra la app; FR-016).

La respuesta suma `motivoSinPuntos` (nulo cuando sumó sus puntos).

La evidencia con foto no cambia de contrato: la app sube la foto (URL firmada → S3 → confirmar) y recién después
completa con `hechoEn`. Confirmar la evidencia de un registro `EXPIRADO` también se acepta cuando el pedido trae
`hechoEn` en regla (mismo criterio).

## App (`Renaser-90-dias-frontend-`, rama `048-modo-offline`)

| Pieza | Dónde | FR |
|---|---|---|
| La sesión no se borra por falta de red; perfil guardado por cuenta | `features/auth/context/AuthContext.tsx`, `features/sinRed/perfilGuardado.ts` | FR-001–FR-003 |
| Estado de la conexión, deducido de las respuestas de `apiFetch` (sin dependencias nativas nuevas) | `features/sinRed/estadoDeConexion.ts` | FR-006 |
| Ícono discreto y globo | `features/sinRed/components/IconoSinConexion.tsx` | FR-006 |
| Lecturas guardadas por cuenta (Hoy, hábitos de hoy, Plan) | `features/sinRed/lecturasGuardadas.ts` + `apiFetch` | FR-004 |
| Una cola por cuenta, generalizando `colaDeEnvio` de la carrera | `features/sinRed/colaSinRed.ts` | FR-007, FR-009 |
| Hora real: ancla del servidor + reloj desde el encendido | `modules/reloj-del-sistema` (módulo nativo chico) + `features/sinRed/horaReal.ts` | FR-013 |
| Marcar hábito sin red: mismas reglas, check al instante, alarmas apagadas | `features/habits/api/evidenciaHabitoApi.ts` y su hook | FR-010–FR-012 |
| «Necesitas conexión para usar esto» | `components/NecesitasConexion.tsx` en SER, chat, Pagos, Eventos, Código Renaser y videos | FR-005 |
| Audios de hoy y mañana | `features/sinRed/audiosDelDia.ts` | FR-017 |
| Aviso al cerrar sesión con pendientes | `AuthContext.logout` | FR-009 |

## Cómo se prueba

- Servidor: pruebas de `RegistroService` (en regla, tarde, futura, fuera de su día, ya hecho, idempotencia) y una IT
  contra Postgres de la tabla nueva.
- App: Jest de la cola, de la hora real, de la sesión sin red y de marcar sin red; E2E en el emulador con el modo avión
  (Maestro + mobile-mcp) y el log del backend.
