# Data model: El líder de mentores ve el cumplimiento de cada persona por grupo

## Migración `V109__consultas_del_lider_y_observacion_sobre_persona.sql`

Va después de V98..V108 de la fase 2 de Soporte TI (research R-1). La cabecera explica qué resuelve cada parte.

1. **Tabla nueva `renaser.consultas_de_ficha`** (append-only; FR-016)

   | Columna | Tipo | Nota |
   |---|---|---|
   | `id` | `uuid` PK | |
   | `consultor_id` | `uuid NOT NULL` | Sin FK: el registro sobrevive al borrado de la cuenta |
   | `rol_consultor` | `varchar(32) NOT NULL` | MENTOR_LEAD, ADMIN o ALCHEMIST en el momento de la consulta |
   | `persona_id` | `uuid NOT NULL` | Sin FK, por el mismo motivo |
   | `grupo_id` | `uuid NOT NULL` | |
   | `mentor_id` | `uuid NULL` | Mentor vigente del grupo al consultar (a quién avisar) |
   | `semana_desde` | `date NOT NULL` | Sábado de la semana consultada |
   | `consultada_en` | `timestamptz NOT NULL` | Del `Clock` |

   - Índice `(mentor_id, consultada_en)` para el resumen diario.
   - Trigger `BEFORE UPDATE OR DELETE` que lanza una excepción: no se edita ni se borra (respuesta del dueño:
     «siempre»).

2. **`renaser.observaciones_mentor`** suma `persona_id uuid NULL` y `semana_desde date NULL` (R-7). Las filas de
   antes quedan en `NULL`.

3. **`ALTER TYPE renaser.tipo_notificacion ADD VALUE IF NOT EXISTS 'REVISION_DEL_LIDER'`** (R-8).

## Dominio (sin Spring)

- `leadership/domain/model/consulta/ConsultaDeFicha`: record con las columnas de arriba. Se crea con
  `ConsultaDeFicha.registrar(consultor, rol, persona, grupo, mentor, semana, ahora)`. No tiene métodos de
  modificación.
- `leadership/domain/model/consulta/ReglaDelResumenDeConsultas`:
  - `toca(Instant ahora, ZoneId zonaDelMentor)` es verdadero entre las 20:00 y las 20:59 locales.
  - `diaLocal(ahora, zona)` devuelve el día del mentor.
  - `cuerpo(List<String> nombres)` arma «Hoy revisó a Ana y Luis», o «Ana, Luis, Eva y 2 más» con más de 3.
  - `clave(mentorId, dia)` devuelve `consultas-lider:<mentorId>:<dia>`.
  - Excluye las consultas con `rol_consultor ≠ MENTOR_LEAD` y las de `consultor_id = mentor_id`.
- `leadership/domain/model/cumplimiento/OrdenDeLaListaDelLider`: comparador que ordena por color (ROJO, AMARILLO,
  SIN_DATOS, VERDE, NO_SE_MIDE), después por % ascendente con los nulos al final, después por nombre y por id
  (FR-007).
- `observacion` (existente) suma `Optional<UserId> persona` y `Optional<LocalDate> semana`.

## Puertos y finders

| Puerto | Módulo | Qué hace |
|---|---|---|
| `ConsultarGrupoDelLiderUseCase` | leadership (in) | La tabla del semáforo del grupo, con el orden del líder y los acompañantes con programa propio |
| `ConsultarSemanaDePersonaUseCase` | leadership (in) | La semana sábado–viernes de una persona del grupo, con sus evidencias; registra la consulta |
| `ResumirConsultasDelLiderUseCase` | leadership (in) | Lo llama el scheduler una vez por hora |
| `SaveConsultaDeFichaPort` / `LoadConsultasDeFichaPort` | leadership (out) | JDBC sobre `consultas_de_ficha` |
| `SemanaDeSeguimientoFinder` | mentoring.api (nuevo) | Expone `armarSemana(persona, desde, hasta)` para que leadership no duplique el armado (R-2) |
| `TablaDelSemaforoFinder` | mentoring.api (nuevo) | Expone `armarTabla(grupo, semanaHasta)` (R-3) |
| `PublicacionMuroFinder.primeraDelAutorEnElDia` | community.api | Primera publicación del autor en su día local (R-6) |
| `EvidenciasDeRocasFinder` | rocks.api / evidence.api | Las acciones del día cumplidas con su evidencia, por persona y rango (R-5) |
| `CuentasDePruebaFinder` | users.api | `esDePrueba(id)`. En master siempre da falso; con la fase 2 lee `usuarios.es_cuenta_de_prueba` |

## Respuestas (forma en [contracts/leadership-cumplimiento.md](contracts/leadership-cumplimiento.md))

- `ObligacionDelDia` (existente) suma, todos opcionales: `respuestaTexto`, `evidencias[]` (`id`, `tipo`,
  `subidaEn`), `publicacionId`. Las fichas del mentor y de administración los reciben sin cambiar su forma.
- `DiaDelAlumno` suma `acciones[]` (`id`, `titulo`, `estado`, `evidenciaId`), solo en la respuesta del líder.

## Estados y validaciones

- Persona fuera del grupo hoy → 404 (RL-08). Grupo de recepción o sin mentor → 404.
- `semanaHasta` que no es viernes, o un viernes que todavía no llegó → 400. Una ventana anterior al Día 1 de la persona → la
  ventana vacía, con el motivo `NO_ACTIVADO`.

> **Corregido 2026-10-19.** Decía «`semanaHasta` posterior a la semana en curso → 400». Ahora tampoco se acepta la semana que
> corre (la ventana del resumen termina ayer): ver el contrato, «La ventana: la misma del resumen en las tres pantallas».
- Observación con `personId` de alguien que no está en un grupo de ese mentor → 400.
