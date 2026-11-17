# Contratos — Spec 055 fase 1

Todo en JSON; fechas ISO (`LocalDate` sin zona, `Instant` en UTC); errores con `{ codigo, mensaje }` como el resto de la API.

## Mentor (prefijo `/api/v1/mentor/groups/{groupId}/learners/{userId}`, `USE_APP`, `@Funcionalidad("mentor.mi_grupo")`)

Guard común: acompañante vigente del grupo (`acompanaVigente`) + cuenta activa + el aprendiz es del grupo hoy. Si no → 403 `NO_ACOMPANA`.

### `GET /semana?semanaHasta=AAAA-MM-DD`
- `semanaHasta` opcional (viernes; sin él, la semana en curso en la zona del aprendiz). Otro día → 400 `SEMANA_INVALIDA`. Antes de la semana del Día 1 o futura → 400 `FUERA_DEL_PROGRAMA`.
- 200: `{ aprendizId, nombre, avatarUrl, zona, desde, hasta, diaUno, primeraSemana, semanaEnCurso, cerrada, color, porcentaje, dias: [ { fecha, diaDePrograma, motivo, obligaciones: [ObligacionDelDia + pilar] } ] }`

### `GET /dias?desde=AAAA-MM-DD&hasta=AAAA-MM-DD`
- Máx. 62 días; recorta a `[diaUno, hoy]`. Rango inválido → 400 `RANGO_INVALIDO`.
- 200: `{ zona, diaUno, dias: [ { fecha, estado: MEDIDO|SIN_DATOS|PAUSADO|CUENTA_SUSPENDIDA|PENDIENTE, color: VERDE|AMARILLO|ROJO|SIN_DATOS, porcentaje } ] }`

### `GET /notas`
- 200: `{ notas: [ { id, texto, autor: { id, nombre }, creadoEn, corregidoEn, puedeCorregir } ] }` (de la más nueva a la más vieja).

### `POST /notas`  body `{ texto }`
- 201: la nota creada. Vacía o > 2000 → 400 `NOTA_INVALIDA`.

### `PATCH /notas/{notaId}`  body `{ texto }`
- 200: la nota corregida. No es el autor → 403 `NO_ES_EL_AUTOR`; otro día → 409 `YA_NO_SE_PUEDE_CORREGIR`; no es de este aprendiz → 404.

## Líder (`VIEW_MENTOR_CORPS`)

### `GET /api/v1/leadership/people/{personId}/notas`
- Guard: `AccesoDeLiderazgo.requireLiderazgoActivo` + la persona está en el alcance de su formación. 200: misma forma, `puedeCorregir` siempre `false`.

## Administración (`MANAGE_TRAINEES`)

### `GET /api/v1/admin/trainees/{traineeId}/notas`
- ADMIN y ALCHEMIST activos. 200: misma forma, `puedeCorregir` siempre `false`.

## Cambio aditivo en respuestas existentes

- `ObligacionDelDia` suma `pilar` (`CUERPO|MENTE|ESPIRITU|EMOCIONES|null`) en todas las respuestas que la usan (mentor, líder, Administración). Las apps instaladas lo ignoran.

## Lo que se reutiliza sin cambios

`GET /mentor/groups/{g}/semaforo` (Mi grupo), `GET /evidence/{id}/url`, `POST /chat/conversations/direct`, `GET /notifications` y `PUT /notifications/{id}/read`.
