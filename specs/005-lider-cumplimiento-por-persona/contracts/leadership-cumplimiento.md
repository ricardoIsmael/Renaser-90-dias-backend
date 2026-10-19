# Contrato: cumplimiento por persona para el liderazgo

Todas las rutas van bajo `/api/v1/leadership/`, que `SecurityConfig` ya cubre. Las usan MENTOR_LEAD, ADMIN y
ALCHEMIST con la cuenta ACTIVE (guard `requireLiderazgoActivo`). El permiso es `@RequiresPermission(VIEW_MENTOR_CORPS)`
y, con la fase 2 integrada, `@Funcionalidad("lider.cumplimiento")`. Todas las respuestas van con
`Cache-Control: no-store`.

Errores, con la convención de siempre:

| Caso | Respuesta |
|---|---|
| Rol sin permiso o cuenta suspendida | 403 |
| Grupo o persona fuera de alcance | 404 |
| Funcionalidad apagada | 404 `FUNCIONALIDAD_NO_DISPONIBLE` (fase 2) |
| Semana futura | 400 |

## 1. `GET /leadership/groups/{groupId}/semaforo?semanaHasta=YYYY-MM-DD`

Es la misma forma que `TablaDelSemaforoResponse` (contrato del semáforo §4.3), con dos diferencias:

- `aprendices[]` viene con el orden del líder (FR-007) e incluye a los acompañantes con programa propio, con
  `color: "NO_SE_MIDE"`, `etiqueta: "No se mide en el semáforo"` y su `porcentaje`.
- Cada fila suma `rol` (`TRAINEE` | `MENTOR` | `TRAINEE_GUIDE` | …), opcional.

Sin `semanaHasta`, devuelve la semana vigente. Nunca incluye cuentas de prueba ni cuentas cerradas.

## 2. `GET /leadership/groups/{groupId}/people/{personId}/week?semanaHasta=YYYY-MM-DD`

```json
{
  "grupoId": "…", "personaId": "…", "nombre": "Ana Pérez", "avatarUrl": null,
  "desde": "2026-10-17", "hasta": "2026-10-23", "zona": "America/Lima",
  "semanaEnCurso": true, "primeraSemana": "2026-08-29",
  "mentor": { "id": "…", "nombre": "Luisa" },
  "resumen": { "obligaciones": 35, "cumplidas": 30, "conEntrega": 12, "pendientes": 3, "sinCumplir": 2 },
  "color": "AMARILLO", "porcentaje": 74.5,
  "dias": [
    {
      "fecha": "2026-10-17", "diaDePrograma": 49, "motivo": null,
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

- `motivo` del día, cuando no hay nada que medir: `NO_ACTIVADO`, `PAUSADO`, `CUENTA_SUSPENDIDA`,
  `SIN_NADA_PLANIFICADO`. **Nunca se lo cuenta como incumplido.**
- **Efecto**: registra una `ConsultaDeFicha` por llamada. El cambio de semana es otra llamada y otro registro.
- `mentor` es `null` en un grupo sin mentor vigente: ahí la app esconde «Avisar a su mentor».

## 3. Evidencias (existentes, regla ampliada)

- `GET /evidence/{id}` y `GET /evidence/{id}/url` pasan a aceptar a MENTOR_LEAD activo como **lector**. El URL
  vence a los 10 minutos.
- `PATCH /evidence/{id}/review`, o la ruta de revisión que exista, sigue respondiendo 403 al líder.

## 4. `GET /leadership/publications/{publicationId}`

La vista de una publicación del Muro (`PublicacionParaCompartir`: texto, fotos con URL firmado, autor y fecha), para
abrir el post diario. 404 si no existe o fue borrada.

## 5. Observación sobre una persona (ruta existente, campos nuevos)

`POST /leadership/mentors/{mentorId}/observations` suma dos campos opcionales:

```json
{ "type": "ALERTA", "text": "Ana lleva 3 días sin el ritual", "sentByChat": true, "messageId": "…",
  "operationKey": "…", "personId": "…", "weekStart": "2026-10-17" }
```

400 si `personId` no está hoy en un grupo de ese mentor. `GET …/observations` devuelve `personId`, `personName` y
`weekStart`, opcionales.

## 6. Resumen de grupos (ruta existente, campo nuevo)

`GET /semaforo/groups`: cada grupo suma `puedeAbrirse: boolean`, opcional. Vale `true` si tiene mentor y la
funcionalidad está prendida. Si el campo falta, la app no abre la tarjeta (FR-021).

## 7. Aviso al mentor

- Tipo `REVISION_DEL_LIDER`.
- Título «Tu líder revisó a tu grupo»; cuerpo «Hoy revisó a Ana y Luis».
- `rutaApp: /mentor/groups/{g}/semaforo`.
- Deduplicación `consultas-lider:<mentorId>:<fecha>`.
- Preferencia en `GET/PATCH /notification-preferences`, con el tema `REVISION_DEL_LIDER`.

## 8. Fichas del mentor y de administración (campos opcionales nuevos)

`…/progress` y `…/weekly-progress` suman `respuestaTexto`, `evidencias[]` y `publicacionId` en cada obligación. Son
aditivos: un APK anterior los ignora.
