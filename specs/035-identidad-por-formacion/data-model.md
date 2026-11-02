# Modelo de datos — Identidad por formación (V159)

**Sin tabla nueva.** Se reutiliza `cambios_bienvenida` (V73, ampliada en V82): ya es la bitácora append-only de «lo que Administración pone encima del original», con una
fila por cambio y lo vigente en la última. La formación es una **columna** de esa fila.

| Cambio | Detalle |
|---|---|
| `cambios_bienvenida.formacion text NOT NULL DEFAULT 'ESCUELA' REFERENCES formaciones(codigo)` | Todo lo que ya existe es de la Escuela (mismo criterio que V125). El `DEFAULT` se conserva a propósito: la app anterior y los caminos que no mandan formación siguen escribiendo en la Escuela. |
| `pieza` + `'IMAGEN_DE_LA_FORMACION'` | Una pieza más de imagen, como `CARTA_CAJA` en V82 (sumar una pieza es cambiar el CHECK; por eso V73 no usó un ENUM). Su ruta vive bajo `formaciones/imagenes/`. |
| `CHECK` de rutas | Cada pieza de imagen solo acepta su propio prefijo (`bienvenida/portadas/`, `caja/cartas/`, `formaciones/imagenes/`). |
| `CHECK (pieza <> 'CARTA_CAJA' OR formacion = 'ESCUELA')` | La carta de la caja es única. |
| Índice `(formacion, pieza, id DESC)` | Reemplaza a `(pieza, id DESC)`: «el último cambio de una pieza en una formación» es la única lectura. |

## Por qué no una tabla nueva ni columnas en `formaciones`

- Una tabla `identidad_de_formacion` con una columna por pieza perdería la **historia** (quién y cuándo, «volver al original») y obligaría a migrar los 5 cambios que
  ya existen; la bitácora ya lo resuelve y es lo que Administración ya entiende («último cambio»).
- Columnas de imagen en `formaciones` (módulo `users`) harían que `users` guardara rutas de imágenes del chat; `chat` es quien las abre, las revisa y las sirve.
- Los **originales** de los textos no se copian a la base (la lección de V73/V22: se desincronizarían con el repo). Viven en `bienvenida/mensajes.yaml`, ahora
  con una sección por formación.

## Cómo se resuelve lo vigente (una sola regla, en `EstadoDePieza`)

| Pieza | Vigente en la formación F |
|---|---|
| Texto | último cambio de F; si no hay o volvió al original → original de F (YAML de F, o el de la Escuela si F no lo trae). **No hereda ediciones de otra formación.** |
| `PORTADA` | último cambio de F; si no hay o volvió → la **vigente de la Escuela** (su último cambio, o `original`). |
| `IMAGEN_DE_LA_FORMACION` | último cambio de F; si no → la vigente de la Escuela; si tampoco → ninguna (los chats usan lo de la app). |

## Idempotencia y datos existentes

`ADD COLUMN IF NOT EXISTS`, `DROP CONSTRAINT IF EXISTS` antes de cada `ADD CONSTRAINT`, `CREATE INDEX IF NOT EXISTS`. Las filas existentes quedan con `formacion = 'ESCUELA'`:
los textos y la portada que la Escuela tenga hoy siguen siendo los suyos, byte a byte.
