# Data model: Formaciones (spec 022)

Reutiliza `participantes_programa`, `celulas`, `usuarios`. Agrega **un catálogo** y **dos tablas** (justificadas en `plan.md`). Todas las migraciones son aditivas y llevan
cabecera con el porqué (regla 04).

## Tablas

### `formaciones` (V125) — catálogo fijo de 3 filas
| Columna | Tipo | Notas |
|---|---|---|
| `codigo` | `text` PK | `ESCUELA`, `DIPLOMADO`, `CERTIFICACION` (mayúsculas, `CHECK codigo = upper(codigo)`) |
| `nombre` | `text` NOT NULL | «Escuela Renaser», «Diplomado Renaser», «Certificación Renaser» (editable) |
| `descripcion` | `text` NOT NULL | descripción corta/larga (editable) |
| `activa` | `boolean` NOT NULL | si se ofrece a personas nuevas |
| `orden` | `smallint` NOT NULL | para listar |
| `duraciones_dias` | `smallint[]` NOT NULL | opciones de duración; `CHECK` 1 a 4 valores entre 1 y 365; Escuela `{90}`, Diplomado `{90,365}`, Certificación `{90}` |
| `version` | `integer` NOT NULL | control de versión optimista (dos administradores no se pisan) |
| `actualizado_en`, `actualizado_por` | `timestamptz`, `uuid` | quién y cuándo |

### Columnas nuevas en tablas existentes (V125)
- `participantes_programa.formacion text NOT NULL DEFAULT 'ESCUELA' REFERENCES formaciones(codigo)`
- `participantes_programa.duracion_dias smallint NOT NULL DEFAULT 90 CHECK (duracion_dias BETWEEN 1 AND 365)`
- `participantes_programa`: el `CHECK dia_programa BETWEEN 0 AND 90` pasa a `dia_programa BETWEEN 0 AND duracion_dias`.
- `celulas.formacion text NOT NULL DEFAULT 'ESCUELA' REFERENCES formaciones(codigo)` + índice por formación.
- **Migración**: todo lo existente queda en `ESCUELA`/`90` por el DEFAULT (SC-002: ni el día, ni los puntos, ni el grupo cambian).

### `formaciones_personal` (V126) — N:M del personal con sus formaciones
| Columna | Tipo | Notas |
|---|---|---|
| `usuario_id` | `uuid` FK `usuarios` ON DELETE CASCADE | PK compuesta con `formacion` |
| `formacion` | `text` FK `formaciones` | |
| `activa` | `boolean` NOT NULL DEFAULT false | índice único parcial `(usuario_id) WHERE activa` → como mucho una activa |
| `asignada_en`, `asignada_por` | `timestamptz`, `uuid` FK `usuarios` ON DELETE SET NULL | quién la asignó |

Solo para `MENTOR`, `TRAINEE_GUIDE`, `MENTOR_LEAD`. **Migración**: el personal existente recibe `ESCUELA` activa.

### `participaciones_anteriores` (V127) — historial append-only
`id uuid PK`, `usuario_id` FK, `formacion`, `duracion_dias`, `fecha_inicio date`, `fecha_fin date` (día local del traslado), `dia_final smallint`,
`resultado` (`TERMINADA` | `DADA_POR_TERMINADA`), `motivo text` (obligatorio, 3–500, si `DADA_POR_TERMINADA`), `puntos_liga`, `coherencia`, `racha_maxima`,
`trasladado_a` FK `formaciones`, `trasladado_por` FK `usuarios`, `creado_en`. Un trigger impide UPDATE/DELETE (como `consultas_de_ficha`).

### Topes de día (V128)
`ajustes_dia_programa.dia_anterior/dia_nuevo`, `registros_habito.dia_programa`, `solicitudes_emergencia.dia_al_pedir`, `publicaciones_muro.dia_programa` pasan de `0..90` a `0..365`.

## Dominio (sin Spring)
- `Formacion` (enum en `shared.domain`): `ESCUELA`, `DIPLOMADO`, `CERTIFICACION`; `codigo()`, `desde(String)`.
- `AlcanceDeFormacion` (valor): `todas()`, `de(Formacion...)`, `ninguna()`, `incluye(f)`, `formaciones()`, `esVacio()`, `unica()`.
- `DuracionDelPrograma` (valor, `users.domain.model.formacion`): `dias` entre 1 y 365; `ultimoDia()`, `estirar(corte)`.
- `FasesDelPrograma`: `inicioDeFase(numero, duracion)`, `faseDelDia(dia, duracion)`; `duracion = 90` reproduce los cortes de siempre.
- `ParticipacionPrograma` suma `formacion` y `duracionDias`; `diaProgramaDerivado` acota a la duración propia; graduar es llegar al último día; `trasladarA(formacion, duracion)`
  reinicia (lo valida `ReglasDeTraslado`).
- `ReglasDeTraslado`: un aprendiz solo se traslada si `programaCompletado`, o con `darPorTerminada` + motivo escrito; destino ≠ origen; la formación destino debe estar activa y
  ofrecer esa duración. Sin Día 1 elegido (nunca empezó): se corrige sin historial (FR-030).

## Transiciones del traslado
```
(aprendiz en Escuela, programa completado | dado por terminado con motivo)
   └─ trasladar(Diplomado, 365)
        ├─ INSERT participaciones_anteriores (foto de lo que fue)
        ├─ UPDATE participantes_programa: formacion, duracion_dias, dia 0, fecha_inicio provisional, programa_activado_en NULL,
        │          celula_id NULL, mentor_id NULL, programa_completado false, dias_ajuste 0, fase inicial
        ├─ evento AprendizTrasladadoDeFormacionEvent
        │     ├─ community: sale del grupo (asignaciones + puntero + chat) y entra a la recepción de la formación nueva
        │     └─ points: ajuste del ledger a 100 puntos, coherencia 100, racha 0
        └─ la persona elige su nuevo Día 1 (flujo de siempre)
```
