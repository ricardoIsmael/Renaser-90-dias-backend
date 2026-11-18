# Modelo de datos — Spec 056

## Nuevo (V199)

### `documentos_semanales`

| Columna | Tipo | Nota |
|---|---|---|
| id | uuid PK | |
| persona_id | uuid NOT NULL | aprendiz o mentor |
| tipo | varchar(16) NOT NULL | `APRENDIZ`, `MENTOR`, `MES` |
| periodo_inicio | date NOT NULL | sábado de la semana (o día 1 del mes) en la zona de la persona |
| periodo_fin | date NOT NULL | viernes (o último día del mes) |
| huella | char(64) NOT NULL | SHA-256 de los datos con que se generó |
| ruta | varchar(300) NOT NULL | `reportes/{persona}/{inicio}-{tipo}-{huella8}.html` |
| generado_en | timestamptz NOT NULL | |
| generado_por | uuid NOT NULL | |

Único: `(persona_id, tipo, periodo_inicio, huella)`. No se borra (para siempre).

### `descargas_de_documentos`

| Columna | Tipo | Nota |
|---|---|---|
| id | uuid PK | |
| documento_id | uuid FK → documentos_semanales | |
| actor_id | uuid NOT NULL | quién lo pidió |
| reutilizado | boolean NOT NULL | true si no se generó de nuevo |
| pedido_en | timestamptz NOT NULL | |

## Derivado (sin tabla)

- **Cupo del mentor**: `acompanados = Σ aprendices vigentes de sus grupos`, `maximo = Σ Celula.cupo(política)`.
- **Por resolver**: grupos sin mentor (de sus formaciones), consultas sin respuesta con la fecha de la más vieja, mentor con la evaluación del mes más baja < 60 %.

## Existente que cambia

- `TicketMentor.respondidoPor` (V88): se expone con nombre; puede ser el líder.
- `ObservacionDeMentor`: al guardarse publica `ObservacionDelLiderEvent` (no cambia la tabla).
