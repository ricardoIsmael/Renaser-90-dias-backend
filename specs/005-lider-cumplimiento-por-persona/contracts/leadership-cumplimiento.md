# Contrato: cumplimiento por persona para el liderazgo

> **Corregido 2026-10-19.** Este contrato se reescribió al implementar el backend. Cambios respecto de la versión del
> plan: la semana por defecto de la lista y de la ficha (§1 y §2), los días futuros (§2), el endpoint de la publicación,
> que pasó a `GET /api/v1/wall/{id}` y se abre a mentor y administración (§4), los valores de `motivo` y de
> `acciones[].estado` (§2) y los campos nuevos `rol`, `cerrada` y `semanaEnCurso`. Cada cambio lleva su nota.

Las rutas de §1 y §2 van bajo `/api/v1/leadership/`, que `SecurityConfig` ya cubre. Las usan MENTOR_LEAD, ADMIN y
ALCHEMIST con la cuenta ACTIVE (guard `AccesoDeLiderazgo`). El permiso es `@RequiresPermission(VIEW_MENTOR_CORPS)` y
`@Funcionalidad("lider.cumplimiento")`. Las respuestas de §1 y §2 van con `Cache-Control: no-store`.

Errores, con la convención de siempre:

| Caso | Respuesta |
|---|---|
| Rol sin permiso o cuenta suspendida | 403 |
| Grupo sin mentor, recepción, o persona que hoy no está en ese grupo | 404 |
| Cuenta de prueba, suspendida o cerrada | 404 (en la lista, no aparece) |
| Funcionalidad `lider.cumplimiento` apagada por Soporte TI | 503 `{ "codigo": "FUNCIONALIDAD_APAGADA", "funcionalidad": "lider.cumplimiento", "message": <aviso editable> }`, antes de entrar al caso de uso |
| `semanaHasta` que no es viernes, o un viernes que todavía no llegó (la semana que corre y las futuras) | 400 |

## La ventana: la misma del resumen en las tres pantallas

> **Corregido 2026-10-19 (decisión del dueño tras el E2E: «la ventana del resumen manda en todo»; E-629 d).** Esta sección
> decía que la lista abría en la última semana cerrada y la ficha en la semana en curso, las dos de sábado a viernes. Eso
> hacía que el resumen («Últimos 7 días · del lunes 12 al domingo 18») y la lista del grupo («Semana cerrada · del sábado 10
> al viernes 16») hablaran de días distintos y dieran porcentajes distintos para la misma persona (Rocío: 50 % y 47,6 %). Ahora
> las tres leen exactamente la misma ventana que `GET /semaforo/groups`:

| `semanaHasta` | Ventana (`desde = hasta − 6`) | Se lee con |
|---|---|---|
| ausente | la **vigente**: los 7 días que terminan ayer en la zona de quien se mira (`hasta = hoy − 1`), aunque ayer no sea viernes | `SemaforoFinder.vigenteDe` |
| un viernes que ya llegó | la semana de **sábado a viernes** que termina ese día | `SemaforoFinder.semanaDe` |

- Lista (§1), ficha (§2) y resumen (§6) comparten esa ventana: con la misma fecha dan el mismo `desde`/`hasta`, el mismo `color` y
  el mismo `porcentaje` para la misma persona, porque la regla del semáforo se lee del mismo lugar sobre los mismos 7 días
  (`MismaVentanaEnLasTresPantallasTest`).
- «Hoy» es el de la persona (`participantes_programa.timezone`) en la ficha y el de la zona del grupo en la lista, nunca el del
  servidor.
- `cerrada` es `true` solo si el último día cae en una semana ya cerrada **según el calendario** (el viernes ya pasó); `semanaEnCurso`
  es `!cerrada`. La vigente de un lunes (12 al 18) no está cerrada; la de un sábado (17 al viernes 23) sí. No depende de que cada
  persona tenga su foto del cierre (E-629). El resumen: su `periodo.cerrada` sigue diciendo `false` para la ventana
  vigente, también un sábado; la app solo rotula con `cerrada` una semana elegida con ‹ ›, nunca la vigente («Últimos 7 días»).
  > **Corregido 2026-10-19 (E-629 e).** Esta frase seguía con «El resumen no cambia» y, con un viernes pedido, el resumen decía `cerrada: false`
  > para una semana ya pasada si una sola persona no tenía su foto del cierre (lista y ficha decían `true`). Ahora, con `semanaHasta`, el
  > `periodo.cerrada` del resumen también sale del calendario: las tres pantallas rotulan igual la misma semana.
- **Ninguna ventana llega al futuro.** Un viernes posterior a hoy (la semana que corre incluida) es 400, igual que una fecha
  que no es viernes. El viernes de hoy sí se acepta: la ventana llega hasta hoy y hoy dice `EN_CURSO`.
- La app abre la lista y después la ficha en la misma ventana que se miraba en el resumen, y las flechas ‹ › mueven la ventana
  de la misma manera en las tres: desde la vigente a la última semana cerrada y de ahí de 7 en 7 días hacia atrás; ninguna llega
  al futuro.

## 1. `GET /leadership/groups/{groupId}/semaforo?semanaHasta=YYYY-MM-DD`

Es la misma forma que `TablaDelSemaforoResponse` (contrato del semáforo §4.3), en la ventana de arriba, con estas diferencias:

- `aprendices[]` viene con el orden del líder (FR-007): `ROJO`, `AMARILLO`, `SIN_DATOS`, `VERDE`, `NO_SE_MIDE`; dentro de
  cada color, el menor `porcentaje` primero (los nulos, al final), después el nombre (castellano) y el id.
  > **Nota 2026-10-19.** FR-007 dice «de rojo a verde, después sin datos y no se mide». Se tomó el orden de la tabla del
  > mentor, que deja «sin datos» entre amarillo y verde (data-model y T010 lo fijan así), y solo «no se mide» al final.
- Incluye a quien lleva su propio programa en el grupo (su mentor o su guía de aprendiz) si lo tiene activado: sale con
  `color: "NO_SE_MIDE"`, `etiqueta: "No se mide en el semáforo"`, su `porcentaje` (null si el semáforo no lo mide), su
  `diasConDatos` y **`dias: []`**: sin barras de colores que contradigan el «no se mide».
- Cada fila suma `rol` (`TRAINEE` | `MENTOR` | `TRAINEE_GUIDE` | …); puede ser null.
- `resumen` cuenta solo a quien se mide: `verde + amarillo + rojo + sinDatos = total`, sin los `NO_SE_MIDE`.
- `motivo`: por qué está «Sin datos» (`SIN_NADA_PLANIFICADO`, `NO_ACTIVADO`, `PENDIENTE_DE_CALCULO`,
  `CUENTA_SUSPENDIDA`, `PAUSADO`, `FUERA_DEL_PROGRAMA`); null si tiene color. En una fila `NO_SE_MIDE` es null.
- Nunca incluye cuentas de prueba, suspendidas ni cerradas.

```json
{
  "grupoId": "…", "grupoNombre": "Grupo Fénix", "desde": "2026-10-12", "hasta": "2026-10-18", "cerrada": false,
  "resumen": { "verde": 1, "amarillo": 0, "rojo": 1, "sinDatos": 0, "total": 2 },
  "aprendices": [
    { "aprendizId": "…", "nombre": "Beto Paz", "avatarUrl": null, "rol": "TRAINEE", "porcentaje": 40.0,
      "color": "ROJO", "etiqueta": "Con problemas", "diasConDatos": 7, "motivo": null,
      "dias": [{ "fecha": "2026-10-12", "estado": "MEDIDO", "porcentaje": 40, "color": "ROJO", "etiqueta": "Con problemas" }] },
    { "aprendizId": "…", "nombre": "Luisa Rojas", "avatarUrl": null, "rol": "MENTOR", "porcentaje": 75.0,
      "color": "NO_SE_MIDE", "etiqueta": "No se mide en el semáforo", "diasConDatos": 7, "motivo": null, "dias": [] }
  ]
}
```

`puedeAbrirse` (`GET /semaforo/groups`, ver §6) dice qué grupos se pueden abrir desde la tarjeta.

## 2. `GET /leadership/groups/{groupId}/people/{personId}/week?semanaHasta=YYYY-MM-DD`

```json
{
  "grupoId": "…", "personaId": "…", "nombre": "Ana Pérez", "avatarUrl": null, "rol": "TRAINEE",
  "desde": "2026-10-12", "hasta": "2026-10-18", "zona": "America/Lima",
  "semanaEnCurso": true, "cerrada": false, "primeraSemana": "2026-08-15",
  "mentor": { "id": "…", "nombre": "Luisa" },
  "resumen": { "obligaciones": 35, "cumplidas": 30, "conEntrega": 12, "pendientes": 3, "sinCumplir": 2 },
  "color": "AMARILLO", "etiqueta": "Requiere atención", "porcentaje": 74.5,
  "dias": [
    {
      "fecha": "2026-10-12", "diaDePrograma": 44, "motivo": null,
      "obligaciones": [
        {
          "registroId": "…", "titulo": "Ritual de la mañana", "estadoHabito": "COMPLETADO",
          "requiereEvidencia": true, "entrega": "ENTREGADA", "entregadaEn": "…", "revision": null,
          "evidenciaId": "…", "respuestaTexto": "Me sentí con energía",
          "evidencias": [{ "id": "…", "tipo": "FOTO", "subidaEn": "…" }],
          "publicacionId": null
        }
      ],
      "acciones": [{ "id": "…", "titulo": "Llamar a 3 clientes", "estado": "COMPLETADA", "evidenciaId": "…" }]
    }
  ]
}
```

- **`dias[]` siempre trae los siete días** de la ventana (`desde` a `hasta`), aunque vengan vacíos. Con la ventana vigente
  son los siete días que terminan ayer; con un viernes, de sábado a viernes.
- **No hay días futuros** (decisión del dueño, 2026-10-19): la ventana termina a lo sumo hoy. **Hoy** solo aparece al pedir el
  viernes de hoy y trae sus obligaciones y acciones tal cual, con `motivo: "EN_CURSO"` si el semáforo no lo midió (el semáforo
  solo mide los días cerrados).
- `motivo` del día, cuando no hay nada que medir. **Nunca se lo cuenta como incumplido.** Valores posibles:
  `SIN_NADA_PLANIFICADO`, `NO_ACTIVADO` (el semáforo no mide a la persona: todavía no activó su programa),
  `PENDIENTE_DE_CALCULO`, `CUENTA_SUSPENDIDA`, `PAUSADO`, `FUERA_DEL_PROGRAMA` (antes del Día 1 o después del 90) y
  `EN_CURSO`. Es `null` si el día se midió.
- `acciones[].estado`: **solo `COMPLETADA` o `PENDIENTE`**. Una acción se completa únicamente entregando evidencia, así
  que `COMPLETADA` trae `evidenciaId`; `PENDIENTE`, `evidenciaId: null`.
- `primeraSemana`: **el sábado que abre la semana del Día 1 de la persona** (la semana en la que empezó su programa); es el
  tope hacia atrás: ninguna ventana que termine antes del viernes de esa semana (`primeraSemana + 6`) tiene nada que mostrar,
  así que la app no ofrece ir más atrás. `null` mientras no eligió su Día 1.
- `semanaEnCurso` es `!cerrada`: `true` mientras la semana en la que cae el último día de la ventana no haya cerrado.

> **Corregido 2026-10-19.** Este contrato decía que la semana en curso traía los días que faltaban con `motivo: "FUTURO"`, vacíos
> y sin las acciones que la persona ya hubiera planificado, y que `GET …/week` sin `semanaHasta` devolvía esa semana en curso.
> Con la ventana única del resumen ya no hay días futuros: el motivo `FUTURO` se retiró del servidor. La app todavía lo tolera
> (lo lee como un motivo más), pero ningún servidor lo manda.
- `color` es el de la lista (`ROJO`…, `SIN_DATOS` si el semáforo no mide a la persona) o `NO_SE_MIDE` para quien lleva su
  propio programa en el grupo; va con su `etiqueta`. `porcentaje` es de la semana, 1 decimal, null si ningún día se midió.
- `mentor` es `null` en un grupo sin mentor vigente y cuando la persona **es** el mentor del grupo: ahí la app esconde
  «Avisar a su mentor».
- `obligaciones[]` tiene la forma de `…/weekly-progress` (§8) más `respuestaTexto`, `evidencias[]` y `publicacionId`.
  `evidencias[].tipo`: `FOTO`, `VIDEO`, `AUDIO`, `TEXTO`, `CAPTURA`. Cada una se abre con §3.
- **Efecto**: registra una `ConsultaDeFicha` por llamada, con el rol de quien consulta. El cambio de semana es otra
  llamada y otro registro.

## 3. Evidencias (existentes, regla ampliada)

- `GET /evidence/{id}` y `GET /evidence/{id}/url` aceptan a MENTOR_LEAD activo como **lector**, además del dueño, el
  mentor asignado y ADMIN/ALCHEMIST. El URL vence a los 10 minutos. Un texto (la Audioterapia, «¿Qué sentiste?» largo)
  viene en `contenidoTexto` del detalle y `…/url` responde 204.
- La revisión y la anulación (`PATCH /evidence/{id}/review`, anular veredicto) siguen respondiendo 403 al líder.

## 4. La publicación del Muro: `GET /api/v1/wall/{id}`

> **Corregido 2026-10-19 (decisión del coordinador).** El plan decía `GET /leadership/publications/{id}`, solo para el
> liderazgo. La ficha del mentor y la de administración también tienen «Ver publicación», así que el endpoint vive en el
> Muro, que es de donde es el recurso, y acepta a quien corresponde:

Quién puede leerla (cuenta ACTIVE; permiso declarado `USE_APP`, la autorización es del servicio):

- su autor;
- MENTOR_LEAD, ADMIN y ALCHEMIST;
- un mentor o guía de aprendiz que **acompaña hoy al grupo** del autor (el mismo criterio de `…/progress`).

Cualquier otro rol (aprendiz de otro grupo, mentor que no lo acompaña) recibe 403; una cuenta suspendida, 403. Una
publicación que no existe, está oculta o borrada, o es de una cuenta cerrada, es 404 (la app dice «Ya no está
disponible»).

```json
{
  "id": "…", "authorId": "…", "authorName": "Ana Pérez", "authorAvatarUrl": null,
  "text": "Hoy completé mi ritual…", "createdAt": "2026-10-14T16:00:00Z",
  "media": [{ "url": "https://…firmada…", "mimeType": "image/jpeg" }]
}
```

`media` son los mismos archivos que muestra el Muro, con la URL firmada que vence (15 minutos); `[]` si la publicación
no tiene imagen. Los nombres son los de `GET /wall`; los campos de conteo del feed (reacciones, comentarios) no viajan.
Esta ruta lleva `@Funcionalidad("muro")`, no la de la ficha del líder: es un recurso del Muro.

## 5. Observación sobre una persona (ruta existente, campos nuevos)

`POST /leadership/mentors/{mentorId}/observations` suma dos campos opcionales:

```json
{ "type": "ALERTA", "text": "Ana lleva 3 días sin el ritual", "sentByChat": true, "messageId": "…",
  "operationKey": "…", "personId": "…", "weekStart": "2026-10-17" }
```

400 si `personId` no está hoy en un grupo que ese mentor acompaña, o si viene `weekStart` sin `personId`. `weekStart` es
el `desde` de la semana que el líder miraba. La respuesta del `POST` y cada elemento de `GET …/observations` devuelven
`personId` y `weekStart`; solo el `GET` trae además `personName` (la cuenta se lee una vez por página; `null` si ya no existe).
Los tres son `null` en las observaciones que no tratan de una persona. Reintentar con la misma `operationKey` devuelve lo ya
creado; usarla con otra `personId` es 400.

## 6. Resumen de grupos (ruta existente, campo nuevo)

`GET /semaforo/groups`: cada grupo suma `puedeAbrirse: boolean`, opcional. Vale `true` si tiene mentor y la
funcionalidad `lider.cumplimiento` está prendida para quien pregunta (apagada, todos van en `false`). Si el campo falta (backend anterior), la app no abre la tarjeta
(FR-021).

## 7. Aviso al mentor

- Tipo `REVISION_DEL_LIDER`.
- Título «Tu líder revisó a tu grupo»; cuerpo «Hoy revisó a Ana y Luis» (primeros nombres; hasta 3 y «y N más»).
- `rutaApp: /mentor/groups/{g}/semaforo`, con el grupo en el que más personas se consultaron.
- Deduplicación `consultas-lider:<mentorId>:<fecha>`.
- Preferencia en `GET/PATCH /notification-preferences`, con el tema `REVISION_DEL_LIDER`.
- Sale a las 20:00 del día, en la zona del mentor (`participantes_programa.timezone`; si no tiene programa, la de su primer
  grupo; por defecto America/Lima). El barrido corre en los minutos 10 y 40 de cada hora UTC (dos intentos por ventana local) y publica solo a quien
  ahora está entre las 20:00 y las 20:59 de su zona; la segunda corrida no repite el aviso (misma clave) y recupera el fallo de
  la primera. Solo cuenta lo que abrió el **líder de mentores** (no Administración ni Alquimista),
  sin repetir personas, y nunca a quien se consultó a sí mismo. Las cuentas que ya no existen no se nombran; sin nadie que
  nombrar, no hay aviso. Apagar `lider.cumplimiento` en Soporte TI detiene el barrido.

## 8. Fichas del mentor y de administración (campos opcionales nuevos)

`…/progress` y `…/weekly-progress` suman `respuestaTexto`, `evidencias[]` y `publicacionId` en cada obligación. Son
aditivos: un APK anterior los ignora. Siguen de lunes a domingo.
