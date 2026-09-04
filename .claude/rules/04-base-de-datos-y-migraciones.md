# Base de datos y migraciones

## La base es propia

Supabase quedó descartado (2026-08-31). El esquema del producto lo define **nuestro Flyway**
(schema `renaser`, ~92 tablas). Hoy corre en Docker local (`pgvector/pgvector:pg16`, puerto **5433**,
`postgres/postgres`, db `renaser`). No hay entorno desplegado.

## Migraciones

- Flyway en `src/main/resources/db/migration/`, numeradas `V<n>__descripcion_en_snake_case.sql`.
- **El baseline (`V1`) está congelado (D-40).** Toda migración posterior **justifica en un
  comentario de cabecera por qué se toca la base**: qué problema resuelve, por qué no se reusa una
  columna existente, y por qué el nombre elegido. Ver `V18` y `V20` como referencia de nivel de
  detalle esperado.
- **Nunca editar una migración ya aplicada.** Se agrega una nueva.
- Los `CHECK` van donde la invariante se puede evaluar con los datos de esa fila. Si la invariante
  depende de otra fecha o de otra tabla, la impone el **dominio**, no la base.

## Cosas de este esquema que hay que saber antes de tocarlo

- `participantes_programa.fecha_graduacion_esperada` **ya no existe** (`V22`). Era
  `GENERATED ALWAYS AS (fecha_inicio + 90) STORED` y desde `V20` mentía, porque ignoraba
  `dias_ajuste_programa`. La fecha real de graduación la da
  `ParticipacionPrograma.fechaGraduacionEsperada()`, y punto. **La lección general:** una columna
  generada que duplica una regla del dominio se desincroniza en cuanto la regla cambia. Si el dato
  es derivable, se deriva en un solo lugar.
- `ajustes_dia_programa` (`V21`) es **append-only**: no hay UPDATE ni DELETE desde el código. Un
  ajuste equivocado se corrige con OTRO ajuste y los dos quedan a la vista, igual que
  `ajustes_puntos` en `points`.
- `registros_habito.dia_programa` es un **snapshot histórico** copiado al generar el track. No se
  recalcula nunca. Cualquier cambio del reloj deja registros viejos con el día anterior — eso es
  deliberado (es historia), pero hay que tenerlo presente al diseñar.
- `participantes_programa.habitos_escalonados_en` existe en la base y **nadie la lee ni la escribe**.
- Ninguna credencial que use la app móvil puede tener `INSERT` sobre `usuarios`: el alta pasa
  obligatoriamente por `ApproveAccountRequestUseCase` o `InviteAndCreateUserUseCase`.

## Persistencia

- Mapper **a mano** entre `*JpaEntity` y dominio cuando hay traducción real (enums, `smallint`↔`int`,
  `ZoneId`↔`text`). MapStruct solo para mapeo plano campo-a-campo, y **solo** hacia la base — nunca
  hacia la respuesta HTTP (un campo nuevo del dominio se filtraría solo al cliente).
- JPA para CRUD de dominio; `JdbcClient` para queries de reporting/ranking sensibles a latencia.
