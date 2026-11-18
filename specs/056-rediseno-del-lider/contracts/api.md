# Contratos — Spec 056

JSON; fechas ISO (`LocalDate` sin zona, `Instant` UTC); errores `{ codigo, message }`. Todo lo del líder: `AccesoDeLiderazgo` (MENTOR_LEAD/ADMIN/ALCHEMIST activo) + la persona dentro del alcance de sus formaciones; fuera → 403 `FUERA_DE_SU_ALCANCE`. Cambios a respuestas existentes: solo campos nuevos.

> **Ajustado 2026-11-18 (carril BA, lecturas).** Las lecturas por persona (`/semana`, `/dias`, `/objetivos`) responden **403** a quien no es del liderazgo (mentor, aprendiz, cuenta suspendida) y **404 sin código** a una persona de otra formación o que no existe: es la regla de la spec 022 (FR-020, «de otra formación responde como si no existiera, sin revelar nada») que ya cumplen `/week`, `/radar`, `/huecos` y `/cierres`. El `403 FUERA_DE_SU_ALCANCE` queda para las acciones sobre grupos (carril BB). Un mentor o una guía que no sigue su programa es **404 `SIN_PROGRAMA`** en las tres.

## Persona (prefijo `/api/v1/leadership/people/{personId}`, `VIEW_MENTOR_CORPS`)

### `GET /semana?semanaHasta=AAAA-MM-DD`
Misma forma que `GET /mentor/groups/{g}/learners/{u}/semana` (055): `{ aprendizId, nombre, avatarUrl, zona, desde, hasta, diaUno, primeraSemana, semanaEnCurso, cerrada, color, porcentaje, dias: [ { fecha, diaDePrograma, motivo, obligaciones: [… + pilar] } ] }`. Persona sin programa → 404 `SIN_PROGRAMA`. Errores de fecha: los de la 055 (400 `SEMANA_INVALIDA` / `FUERA_DEL_PROGRAMA`). Para un mentor con su programa, `color` es `NO_SE_MIDE`. **Deja la constancia de consulta** del líder (spec 005 FR-016), como `/week`: con el grupo y el mentor de la persona, o en NULL si no tiene grupo o es un mentor.

### `GET /dias?desde&hasta`
Misma forma que el `/dias` del mentor (055). Máx. 62 días; recorta a `[diaUno, hoy]`.

### `GET /objetivos`
200, solo lectura (no hay POST ni PUT). Es lo que da `RocasDelAprendizFinder`; el contrato original suponía un solo objetivo por mes y por semana, y el finder da uno **por eje**:

```
{ noventaDias: [ { id, eje, titulo, avance, meta, valor, unidad, unidadAdelante, lineaBase, porConfirmar } ],
  mes: { titulo: "Mes 2", numeroMes, diaDeCierre, objetivos: [ { eje, titulo, cifra, unidad, unidadAdelante, metaAlcanzada, motivoSinCifra } ] } | null,
  semana: { titulo: "Semana 7", numeroSemana, inicio, fin, objetivos: [ { eje, titulo } ], planificadas, cumplidas } | null }
```

- `noventaDias`: uno por eje (CUERPO, TRABAJO, RELACIONES), ordenado; `id` es el eje (el finder no da otro identificador); `avance` es el **porcentaje** 0–100 hacia la meta y es `null` si el objetivo es solo cualitativo (no se inventa un cero); `valor` es lo que lleva hoy; `meta`, `valor`, `unidad` y `lineaBase` son `null` sin meta medible.
- `mes`: `null` si no hay objetivos del mes. **No trae `avance`**: el finder no calcula el avance del mes; `titulo` de cada objetivo es lo que la persona escribió (`null` si solo tiene la cifra calculada).
- `semana`: `null` si no tiene objetivos semanales; `planificadas` y `cumplidas` suman las acciones diarias de los días de la semana que ya llegaron (hoy incluido), lo mismo que alimenta el avance del Plan.
- Sin objetivos, o sin Día 1 elegido: `noventaDias: []`, `mes: null`, `semana: null` (200, no 404).

### `POST /avisar-al-mentor`  body `{ texto, operationKey }`
204. El mentor vigente recibe el mensaje en su chat con el actor (con el enlace a la ficha) y un aviso. Sin mentor → 409 `SIN_MENTOR`. Mismo `operationKey` → 204 sin duplicar.

### `POST /documento`  body `{ tipo: APRENDIZ|MENTOR, semanaHasta?: AAAA-MM-DD }`
200: `{ url, venceEn, reutilizado, semana: { desde, hasta } }`. Permite también al MENTOR que acompaña hoy a la persona (tipo `APRENDIZ`) — 403 `NO_ACOMPANA` si no. Semana sin datos → el documento lo dice (FR-038), no es error.

## Mentores (`/api/v1/leadership/mentors…`)

- `GET /` (padrón) y `GET /{id}` (ficha): + `cupo: { acompanados, maximo }` y + `porColor: { rojo, ambar, verde, sinDatos }`, **dentro del objeto de cada mentor** (`mentors[i]` en el padrón; `mentor` en la ficha), junto a `traineeCount` y `semaforo`. `acompanados` son los aprendices vigentes de sus grupos sin repetir; `maximo`, la suma de los cupos de esos grupos (el propio de cada grupo si lo tiene y, si no, el de la política de su cohorte). Un mentor sin grupo: `0/0` y ceros. `ambar` es el amarillo del semáforo. Si la fuente cae, `cupo` y/o `porColor` van en `null`, nunca en cero. También salen en `GET /leadership/report` (mismo objeto de mentor).
- `POST /{id}/observations`: igual; además el servidor manda el mensaje y el aviso (ignora `sentByChat` de una app vieja si ya lo mandó la app: `messageId` presente → no reenvía).
- `POST /{id}/documento`  body `{ semanaHasta? }` → como el de persona, tipo `MENTOR`.
- `GET /por-resolver` → `{ gruposSinMentor, consultas: { cantidad, diasDeLaMasVieja }, peorMentor: { id, nombre, evaluacion } | null }`. `gruposSinMentor`: grupos regulares **en curso** de las formaciones del líder sin mentor vigente. `consultas`: consultas abiertas de los aprendices de los grupos con mentor (una consulta no se cuenta dos veces); `diasDeLaMasVieja` es `null` sin consultas. `peorMentor.evaluacion` es el porcentaje de su evaluación del mes y solo aparece si es el más bajo y está **por debajo de 60**. Una parte cuya fuente cae va en `null` (no en 0).

## Grupos (`/api/v1/admin/cells…`, `MANAGE_CELLS`)

Sin rutas nuevas. El líder (con `MANAGE_CELLS`) opera solo sobre grupos y personas de sus formaciones; fuera → 403 `FUERA_DE_SU_ALCANCE`. `GET /dashboard`: + `capacity` por grupo.

## Consultas

- `POST /api/v1/tickets/{id}/answer`: también el líder de la formación del aprendiz.
- `TicketMentorResponse`: + `respondidoPor: { id, nombre } | null`.
- `POST /api/v1/leadership/tickets/{id}/recordar`  body `{ operationKey }` → 204; mensaje directo al mentor asignado con la consulta.

## Reporte del mes

- `POST /api/v1/leadership/report/documento`  body `{ month: AAAA-MM }` → como `/documento`, tipo `MES`.

## Sin cambios

`POST /api/v1/calendar/events` (sesión personalizada con PERSONA/GRUPO/MENTORES y `crearEnlaceMeet`); lecturas del líder de radar, cierres, huecos, diagnóstico, 80/20 y notas.
