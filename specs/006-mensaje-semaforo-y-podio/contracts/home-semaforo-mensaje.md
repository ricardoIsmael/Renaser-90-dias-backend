# Contrato: `semaforo.mensaje` en `GET /api/v1/home`

Campo **aditivo** dentro del objeto `semaforo` que ya existe. Permiso: el de siempre (`USE_APP`).

```json
"semaforo": {
  "color": "AMARILLO",
  "etiqueta": "Requiere atención",
  "porcentaje": 71.4,
  "diasConDatos": 6,
  "pausado": false,
  "dias": [ ... ],
  "mensaje": {
    "id": "a011",
    "texto": "María, faltan 2 para avanzar de color. Empieza por lo más sencillo y recupera movimiento.",
    "sticker": "azul-reloj"
  }
}
```

## Reglas

- `mensaje` puede ser `null` o no venir: un backend anterior, nadie a quien medir, o ningún candidato. La app entonces
  se ve como hoy.
- `texto`:
  - ya viene redactado, sin llaves y en tuteo;
  - tiene como máximo 300 caracteres;
  - la app no lo transforma.
- `sticker`: uno de los 15 nombres (`dorado-orgulloso`, `dorado-celebrando`, `dorado-guino`, `dorado-sereno`,
  `dorado-trofeo`, `azul-animando`, `azul-pensativo`, `azul-reloj`, `azul-firme`, `azul-decepcionado`,
  `azul-ofrece-ala`, `azul-despertando`, `azul-entrenando`, `azul-vs-macaco`, `azul-sorprendido`) o `null`. Si es un
  nombre desconocido, la app muestra solo el texto.
- `id`: estable durante el día mientras no cambie el grupo; sirve para pruebas y para la clave de animación.
- Solo va en `semaforo` cuando `semaforo` no es `null`; para quien no se mide, `semaforo` sigue en `null`.

## Esquema en la app (zod, tolerante)

`mensaje`: objeto `{ id: string, texto: string (no vacío), sticker: string | null }`, `.nullish()`. Si llega mal
formado, `.catch(null)`: se pierde el mensaje, no la tarjeta.
