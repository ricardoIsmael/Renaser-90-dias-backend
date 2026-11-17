# Modelo de datos — Spec 055 fase 1

## Nota privada del mentor (nueva)

Tabla `renaser.notas_del_mentor`

| Campo | Tipo | Reglas |
|---|---|---|
| id | uuid | PK |
| aprendiz_id | uuid | NOT NULL, FK `participantes_programa(usuario_id)`; la nota sigue al aprendiz, no al grupo |
| autor_id | uuid | NOT NULL, FK `usuarios(id)` |
| texto | text | NOT NULL, 1–2000 caracteres sin contar espacios de los extremos (CHECK) |
| dia_del_autor | date | NOT NULL: el día local del autor cuando la escribió (decide si se puede corregir) |
| creado_en | timestamptz | NOT NULL |
| corregido_en | timestamptz | NULL hasta la primera corrección |

Índice `(aprendiz_id, creado_en DESC)`. Sin `ON DELETE CASCADE` desde el autor: si el autor deja de existir la nota se conserva (la FK es `RESTRICT`; los usuarios no se borran físicamente).

Tabla `renaser.notas_del_mentor_versiones` (historia de correcciones, principio IV)

| Campo | Tipo | Reglas |
|---|---|---|
| nota_id | uuid | FK `notas_del_mentor(id)` |
| texto_anterior | text | NOT NULL |
| reemplazado_en | timestamptz | NOT NULL |

PK `(nota_id, reemplazado_en)`.

### Reglas del dominio

- Escribe solo quien acompaña hoy al aprendiz (mentor o guía con asignación vigente de su grupo).
- Corrige solo el autor y solo si `hoy en su zona == dia_del_autor`; cada corrección guarda el texto anterior en versiones.
- No hay borrado.
- Leen: el acompañante vigente (todas las notas del aprendiz), el liderazgo de su formación y Administración.

## Lo que se agrega a lo existente (sin migración)

- `ObligacionDelDia.pilar` (`String`, nullable): `categoria_clave` del hábito.
- Respuesta `DiasDelAprendiz`: `List<{ fecha, color, porcentaje, estado }>` leída de `semaforo_dias` con la regla de `ColorSemaforo`.
- `SemanaDelAprendizParaElMentor`: la `SemanaDeLaPersona` del líder servida al mentor, más el `diaUno` (fecha) para limitar el mes.
