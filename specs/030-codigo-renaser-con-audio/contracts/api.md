# Contrato: el campo `dictado`

Cambio **aditivo**: un campo opcional en un pedido y en cuatro respuestas. Ninguna ruta nueva, ningún permiso nuevo, ninguna
funcionalidad nueva (`@Funcionalidad` sigue siendo `radar`, `mentor.mi_grupo` y las de `leadership`: lo que apague la spec 034 apaga
también este dato).

## Pedido

`POST /api/v1/radar`

```json
{ "whatAmIDoing": "…", "whatAmIThinking": "…", "whatAmIFeeling": "…", "energyLevel": 6, "whatAmIAvoiding": "…",
  "dictado": true }
```

- `dictado` es opcional; ausente o `null` = `false`. La app solo lo manda cuando es `true`.
- Las validaciones de siempre no cambian (cuatro textos de 1 a 2 000, energía de 1 a 10, el 409 por Mi Radar apagado, etc.).
- Si ya hay un registro en la hora (índice único), se devuelve ese y **su** `dictado`: el del segundo intento no lo pisa.

## Respuestas

| Ruta | Quién | Campo |
|---|---|---|
| `POST /api/v1/radar` | la persona | `dictado: boolean` en `RegistroRadarResponse` |
| `GET /api/v1/radar/history` | la persona | `entries[].dictado` |
| `GET /api/v1/mentor/groups/{g}/learners/{u}/radar` | mentor o guía vigente | `entries[].dictado` |
| `GET /api/v1/leadership/groups/{g}/people/{p}/radar` | líder, Administración, Alquimista | `entries[].dictado` |

`dictado` es siempre un booleano en las respuestas (nunca `null`); los registros anteriores a V132 dicen `false`. Un cliente que no lo
conoce lo ignora (la app publicada usa esquemas `passthrough`).

## Seguridad

Ningún endpoint nuevo: la autorización negativa (rol sin permiso → 403, cuenta suspendida → 403, otro grupo → 404/403) es la de los
endpoints existentes y sus pruebas siguen en verde. El dato lo escribe solo la propia persona en su propio registro.
