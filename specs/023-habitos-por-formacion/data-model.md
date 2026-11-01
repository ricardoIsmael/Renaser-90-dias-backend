# Data model: Hábitos y categorías por formación (spec 023)

Reutiliza `categorias_habito` y `habitos`. **Una tabla nueva** (`auditoria_catalogo_habitos`, FR-024). Todas las migraciones son aditivas, con cabecera.

## V134 — `categorias_habito` y `habitos`
| Tabla | Cambio |
|---|---|
| `categorias_habito` | `+ formacion text NOT NULL DEFAULT 'ESCUELA' REFERENCES formaciones`, `+ icono text NULL`; `UNIQUE (formacion, clave)`; índice único `(formacion, nombre normalizado)`; índice `(formacion, activa, orden)`. Las 4 de siempre quedan en la Escuela con `activity / brain / heart / sparkles`. |
| `habitos` | `+ formacion text NULL DEFAULT 'ESCUELA' REFERENCES formaciones`; `CHECK (ambito <> 'SISTEMA' OR formacion IS NOT NULL)`; **FK compuesta** `(formacion, categoria_clave) → categorias_habito (formacion, clave)`; el índice único del título pasa a `(formacion, título normalizado)`; `clave_sistema` pasa de única global a **única por formación** (`(formacion, clave_sistema) WHERE clave_sistema IS NOT NULL`); índice `(formacion, activo, orden)` del catálogo. |

El código escribe `formacion = NULL` en un hábito propio (sigue a su dueño); la base no lo exige para no romper a quien inserta a mano.

## V135 — `auditoria_catalogo_habitos` (solo agregar)
`id uuid PK`, `formacion → formaciones`, `entidad ∈ {CATEGORIA, HABITO}`, `clave text`, `accion ∈ {CREADA, RENOMBRADA, ICONO, ORDENADA, ACTIVADA, DESACTIVADA,
ELIMINADA, COPIADO}`, `actor_id uuid`, `detalle jsonb`, `creado_en timestamptz`. Trigger que rechaza UPDATE y DELETE.

## V136 — topes de día
`horarios_habito.dia_inicio` y `desbloqueos_habito.dia_desbloqueo`: `BETWEEN 1 AND 365`. `guias_habito.dia_inicio` sigue en 90 (contenido en escala base).

## Dominio (sin Spring)
- `CategoriaHabito` (clave, formación, nombre 2–40, orden, icono, activa) · `IconoDeCategoria` (16 íconos que la app ya dibuja) · `CodigoDeCategoria`
  (`CUERPO ↔ BODY`, … ; el resto, la clave).
- `Habito`: `+ formacion` (nula en un propio), `crearDeSistema(…, formacion, …)`, `copiarA(idNuevo, destino, categoriaDestino, ahora)`,
  `esDeFormacion`, `exigirQueSeaVisibleEn(formacionDeLaPersona)` (404 si es del catálogo de otra).
- `HorarioHabito.copiarPara`, `GuiaHabito.copiarPara`. `ProgresoParticipanteHabits` suma `formacion`, `duracionDias` y `diaEnEscalaBase()`.

## Reglas que cruzan filas (en el servicio, no en el agregado)
- El nombre de la categoría es único dentro de la formación (sin tildes ni mayúsculas).
- Una categoría con hábitos **activos** no se desactiva ni se elimina; con hábitos de cualquier estado no se elimina.
- Un hábito se crea y se mueve solo a una categoría **activa de su misma formación**.
- Copiar: el destino no puede ser la misma formación, no puede tener ya el título ni la `clave_sistema`.
