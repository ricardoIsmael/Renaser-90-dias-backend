# Verificación de Training contra el servidor real — 2026-09-07

Prueba de punta a punta contra el backend corriendo (`192.168.18.46:8080`) y la base de dev, con la
cuenta del dueño. No es la suite automática: es el sistema real respondiendo a peticiones reales.

Todo lo que sigue **se ejecutó**; ningún resultado está estimado.

## Resumen

| | |
|---|---|
| Casos ejecutados | 22 |
| Pasaron | 21 |
| Encontraron un defecto | 1 (corregido, ver §4) |
| Suite automática después del arreglo | 56 pruebas, 0 fallos |

**Recomendación: se puede subir**, con las tres salvedades de §6.

---

## 1. Sesión y estado de la cuenta

| Caso | Esperado | Obtenido |
|---|---|---|
| `POST /auth/login` | 200 + token en cabecera `X-Auth-Token` | ✅ |
| `GET /home` | día de programa real | ✅ `diaPrograma = 0`, `fase = PHASE_1_REBIRTH`, `inscrito = true` |

`diaPrograma = 0` confirma que el **"DÍA 1 DE 90"** que mostraba la app era un valor inventado por el
cliente cuando la carga fallaba (`?? 1`), no un dato del servidor. Ya corregido: sin dato muestra
`DÍA — DE 90`.

## 2. Hora por día de la semana (V39) — el pedido central

Configuración en la base para `DESPERTAR`: jueves 06:00, viernes 07:00, sábado 06:00, domingo 06:00.
Más un cambio general a 05:00 con fecha efectiva 2026-09-08.

Se pidió `GET /habit-preferences?date=` para ocho fechas consecutivas:

| Fecha | Resolvió | Por qué |
|---|---|---|
| lun 2026-09-07 (hoy) | sin hora | el cambio a 05:00 rige **desde mañana** — D-91, el día en curso no se reacomoda |
| mar 2026-09-08 | 05:00 | ya rige el cambio general |
| mié 2026-09-09 | 05:00 | ídem |
| **jue 2026-09-10** | **06:00** | hora propia del jueves |
| **vie 2026-09-11** | **07:00** | hora propia del viernes |
| **sáb 2026-09-12** | **06:00** | hora propia |
| **dom 2026-09-13** | **06:00** | hora propia |
| lun 2026-09-14 | 05:00 | vuelve al general |

La cadena completa resolviendo bien: **fecha exacta > día de semana > cambio pendiente vigente >
preferencia general > catálogo**. Y el día en curso respetando D-91 sin que nadie lo pidiera acá.

## 3. Apagar y volver a encender un día (V40)

| Paso | Resultado |
|---|---|
| `DELETE /weekdays/WEDNESDAY/active` | 204 |
| `GET /weekdays` después | `activo=false`, `propio=true` |
| Fila en la base | `activo=false`, `hora_disparo=NULL` |
| `DELETE /weekdays/WEDNESDAY` (volver a encender) | 204 |
| `GET /weekdays` después | `activo=true`, `propio=false` |

Apagar **no borra la hora** y volver a encender deja el día como estaba. Idempotente en las dos
direcciones.

## 4. El defecto que apareció, y su corrección

**Síntoma.** Para el mismo miércoles, dos endpoints daban respuestas distintas:

```
GET /habit-preferences/{id}/weekdays   ->  MIÉ: sin hora
GET /habit-preferences?date=2026-09-09 ->  MIÉ: 05:00
```

**Causa.** La vista semanal componía el respaldo con la preferencia general y el catálogo, y **se
salteaba el cambio general pendiente ya vigente**, que la resolución por fecha sí considera. Dos
implementaciones de la misma precedencia, separadas.

**Consecuencia real.** La app habría pintado el miércoles en blanco (`·`) para un hábito que ese día
sí corre a las 05:00. La clase exacta de mentira silenciosa que este trabajo vino a eliminar.

**Corrección.** `consultar` ahora carga el cambio pendiente y lo aplica cuando su fecha efectiva ya
pasó, con el mismo criterio que el adaptador. Verificado: 56 pruebas en verde después del cambio.

## 5. Casos adversos

Todos devolvieron lo esperado:

| Caso | Esperado | Obtenido |
|---|---|---|
| Apagar un hábito **obligatorio** del programa | 409 | ✅ 409 |
| Día de la semana inventado (`LUNES` en vez de `MONDAY`) | 400 | ✅ 400 |
| Hora límite anterior a la de disparo | 400 | ✅ 400 |
| `triggerTime` ausente | 400 | ✅ 400 |
| Sin token de sesión | 401/403 | ✅ 403 |
| Hábito inexistente | 404 | ✅ 404 |
| Borrar un día que no tiene hora propia | 204 (idempotente) | ✅ 204 |

El 409 del obligatorio confirma el candado que acota la objeción de V31: un hábito del programa se
puede mover de hora, **no sacar**.

## 6. Salvedades antes de producción

**1. Orden de despliegue: backend primero.** La app publicada no conoce los días apagados; un hábito
sin generar le va a parecer que falta. Con el backend adelante, la app vieja sigue funcionando
(todos los campos nuevos son aditivos y los esquemas zod usan `.passthrough()`), pero al revés no.

**2. ~~La cuota no cubre esta vía.~~ CERRADO el mismo día.** El argumento con el que se había
dejado afuera —"un patrón semanal no tiene fecha efectiva"— era falso: la tiene, y es la próxima vez
que caiga ese día. Fijar la hora de un día ahora consume el cupo semanal y queda registrado en
`historial_cambios_horario`, así que cuenta para los siguientes. Apagar un día sigue siendo gratis,
y eso sí es deliberado: es hermano de la pausa, que nunca cobró cupo.

**3. Sin probar en producción real:** el barrido nocturno con estos datos. Está cubierto por pruebas
—incluida una que corre contra Postgres real por el mismo método que usa el cron— pero nunca corrió
un 05:02 UTC con filas de `horario_semanal_habito` en la base. Es el riesgo #1 del plan y la primera
noche conviene mirar el log del scheduler.

## 7. Cómo repetir esto

```bash
./scripts/verificar-training.sh            # foto de la base
./scripts/verificar-training.sh DESPERTAR  # filtrado por hábito
```

Muestra el horario general, el de cada día de la semana, las excepciones por fecha, las pausas, los
registros de hoy y los hábitos propios. **No afirma que nada esté bien**: muestra lo que hay, para
compararlo con lo que la pantalla dijo que hizo.
