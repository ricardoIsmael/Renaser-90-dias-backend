# Pruebas de endpoints EN VIVO (curl real, sin mocks)

**Fecha:** 2026-08-31
**Rama:** `feature/migracion-catalogo-y-audioterapia` (HEAD `450484a`)
**Objetivo:** encontrar los E-49 que queden — endpoints que compilan, tienen tests en verde y
revientan igual contra el runtime real. Todo lo de acá salió de un `curl` contra la app corriendo,
no de un test.

---

## 0. Resumen

| | |
|---|---|
| Endpoints en el código | **221** (en 71 controllers) |
| Endpoints invocados al menos una vez | **221 / 221** (86 GET + 117 POST/PUT/PATCH + 18 DELETE) |
| Llamadas `curl` totales | ~700 (incluye el barrido de 86 GET × 5 roles) |
| **Endpoints que devuelven 500** | **1** |
| Defectos de contrato/comportamiento encontrados | **9** |
| Rutas que existen pero **no figuran** en `docs/api/` | **53 de 177** |

**Lo más grave:** la app **no arranca** contra la base `renaser` tal como está hoy (§1.1). Ese es el
hallazgo #1 y es un bloqueante de entorno, no un bug de código. Tuve que trabajar sobre un clon.

**Lo más grave del código:** los `record` de request que usan **primitivos de Java** (`boolean`, `int`)
fallan la deserialización si el cliente omite el campo, y devuelven un 400 genérico que no dice qué
campo falta (§2.2). Son **12 DTOs** en 6 módulos. Es exactamente el patrón E-49: Jackson 3 cambió el
default respecto de Jackson 2, los tests mandan el body completo y nunca lo ven.

---

## 1. Bloqueantes de entorno (no son bugs de código, pero frenan todo)

### 1.1 La app NO arranca contra la base `renaser`: V13 aplicada ≠ V13 del repo

```
Migration checksum mismatch for migration version 13
-> Applied to database : 1113320007
-> Resolved locally    : 240943065
```

La app aborta el arranque (`FlywayValidateException` → `entityManagerFactory` no se crea →
`Application run failed`). Comando exacto: `./mvnw spring-boot:run` sin ninguna variable.

**Causa real, verificada:**

| | En la base `renaser` | En este árbol de trabajo |
|---|---|---|
| V13 descripción | `usuarios avatar ruta` | `usuarios avatar url permanente` |
| Aplicada / commiteada | 2026-08-31 **15:38:34** | commit `450484a`, 2026-08-31 **16:43:15** |
| Columna resultante | `usuarios.avatar_ruta` | `usuarios.avatar_url` |
| CHECK | `usuarios_avatar_ruta_es_ruta` | `usuarios_avatar_url_no_prefirmada` |

El archivo `V13__usuarios_avatar_ruta.sql` **no existe en ningún lado**: ni en el árbol, ni en las 9
worktrees, ni en ninguna de las 12 ramas, ni en todo el historial de git (`git log --all -S avatar_ruta`
solo devuelve `450484a` y `3a3bfa8`). Busqué el archivo en disco: solo aparece
`V13__avatar_url_permanente.sql`.

Es decir: **alguien aplicó a la base compartida una V13 de un intento que después se descartó**, y una
hora más tarde se commiteó otra V13 distinta con el mismo número. La base quedó divergente del código.

`flyway repair` **no alcanza**: repararía el checksum pero la columna seguiría llamándose `avatar_ruta`,
y `UserJpaEntity` mapea `avatarUrl` → `avatar_url`. Toda lectura de usuario daría error.

**Cómo trabajé igual (y por qué es seguro):** cloné la base y revertí ahí la V13 abandonada.
La base real `renaser` **no la toqué nunca** — la app jamás apuntó a ella:

```bash
docker exec renaser-db psql -U postgres -d postgres -c "CREATE DATABASE renaser_pruebas WITH TEMPLATE renaser;"
docker exec renaser-db psql -U postgres -d renaser_pruebas -c "
  ALTER TABLE renaser.usuarios DROP CONSTRAINT usuarios_avatar_ruta_es_ruta;
  ALTER TABLE renaser.usuarios RENAME COLUMN avatar_ruta TO avatar_url;
  DELETE FROM public.flyway_schema_history WHERE version='13';"
DB_URL=jdbc:postgresql://localhost:5433/renaser_pruebas ./mvnw spring-boot:run
```

Con eso la V13 del repo corrió limpia y la app arrancó. **El clon ya está borrado** (`DROP DATABASE`).
Verifiqué al final que `renaser` sigue con 25 usuarios, columna `avatar_ruta` y V13 `usuarios avatar ruta`
— exactamente como la encontré, sin un solo rastro de mis pruebas.

> **Hay que decidir cuál de las dos V13 es la buena** y alinear base y código. Mientras tanto, nadie
> puede levantar la app contra esta base.

### 1.2 No hay ningún usuario `ALQUIMISTA`

El enum `renaser.rol_usuario` tiene 5 valores, pero la base solo tiene actores de 4:
17 `APRENDIZ`, 4 `MENTOR`, 2 `LIDER_MENTORES`, 2 `ADMIN`, **0 `ALQUIMISTA`**.

No inventé un UUID. Todo lo que dice "solo ADMIN/ALCHEMIST" lo probé **solo con ADMIN**; la rama
`ALQUIMISTA` de esos guards queda **sin verificar**.

### 1.3 Redis es compartido

Redis (`renaser-redis`) es el mismo para el clon y para el entorno real: sesiones, cuota de RenasIA,
códigos de verificación y rate-limits conviven. Borré al terminar las claves que creé.

---

## 2. Los rotos — con el `curl` literal

### 2.1 🔴 `PATCH /api/v1/admin/audio-therapies/{week}` → **500 + NullPointerException**

El único endpoint de los 221 que devuelve 500.

```bash
curl -s -X PATCH http://localhost:8080/api/v1/admin/audio-therapies/2 \
  -H "X-Actor-Id: 00000000-0000-0000-0000-000000000001" \
  -H "Content-Type: application/json" -d '{}'
```

**Status:** `500`
**Cuerpo real** (recortado):

```json
{"timestamp":"2026-08-31T22:20:32.800Z","status":500,"error":"Internal Server Error",
 "trace":"java.lang.NullPointerException: Cannot invoke \"java.lang.Integer.intValue()\" because the return value of \"com.renaser.os.habits.infrastructure.adapter.in.rest.audioterapiaadmin.UpdateAudioTherapyDurationRequest.durationDays()\" is null\r\n\tat com.renaser.os.habits.infrastructure.adapter.in.rest.audioterapiaadmin.AudioTherapyAdminController.actualizarDuracion(AudioTherapyAdminController.java:29)..."}
```

**Causa (leída en el código, no supuesta):**

```java
public record UpdateAudioTherapyDurationRequest(@Positive Integer durationDays) {}
```

`@Positive` **acepta `null`** (así lo define Bean Validation: `null` es válido para todas las
restricciones de rango). El campo es `Integer`, el `Command` lo recibe como `int`, y el unboxing de
`null` explota. **Falta `@NotNull`.**

**Dos defectos en uno.** El segundo es el sobre del error:

`GlobalExceptionHandler` tiene 22 `@ExceptionHandler` pero **ninguno para `Exception`**. Cualquier
excepción no contemplada se escapa al `/error` por defecto de Spring, que devuelve el shape
`{timestamp,status,error,trace,message,path}` — **no** el `{"message","timestamp"}` que
`docs/MAPA_ENDPOINTS.md` promete ("Nunca un stacktrace") y que la tabla de errores de
`CONTRATO_DIA_A_DIA.md` documenta. **Ningún 500 del sistema respeta el contrato de error.**

> **Matiz honesto sobre el `trace`:** el stacktrace aparece porque **DevTools está activo** en local
> (`DevToolsPropertyDefaultsPostProcessor` fuerza `server.error.include-stacktrace=always`). En el jar
> de producción devtools no viaja (`<scope>runtime</scope><optional>true</optional>`), así que **el
> `trace` no se filtraría en prod**. Lo que **sí** persiste en prod es el 500 y el sobre equivocado.

### 2.2 🔴 Los `record` con primitivos rompen la deserialización — 12 DTOs, 6 módulos

**Este es el E-49 de esta tanda.** Si el cliente omite un campo declarado como primitivo
(`boolean`/`int`), Jackson 3 falla la deserialización entera. Spring Boot 4 trae Jackson 3, donde
`FAIL_ON_NULL_FOR_PRIMITIVES` viene activo — en Jackson 2 el primitivo se rellenaba con su default y
no pasaba nada. El resultado es un **400 genérico que no nombra el campo**:

```json
{"message":"El cuerpo de la solicitud es invalido o esta mal formado","timestamp":"..."}
```

Verificado en vivo en **tres módulos independientes**:

```bash
# habits — falta reminderEnabled
curl -s -X PATCH http://localhost:8080/api/v1/habit-preferences/61111111-1111-1111-1111-111111111111 \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111" -H "Content-Type: application/json" \
  -d '{"triggerTime":"07:00:00","limitTime":"09:00:00"}'
# -> 400 {"message":"El cuerpo de la solicitud es invalido o esta mal formado", ...}

# notifications — falta enabled
curl -s -X PATCH http://localhost:8080/api/v1/notification-preferences \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111" -H "Content-Type: application/json" \
  -d '{"preferences":[{"type":"RECORDATORIO_HABITO"}]}'
# -> 400 mismo mensaje

# habits/admin — falta isOptional
curl -s -X POST http://localhost:8080/api/v1/admin/habits \
  -H "X-Actor-Id: 00000000-0000-0000-0000-000000000001" -H "Content-Type: application/json" \
  -d '{"title":"X","habitType":"CHECKBOX","category":"BODY","evidenceRequirement":"OPTIONAL"}'
# -> 400 mismo mensaje

# evidence — falta aprobar
curl -s -X POST http://localhost:8080/api/v1/admin/evidence/a0d1e3c1-3b99-4e74-9f24-d71a17a88004/review \
  -H "X-Actor-Id: 00000000-0000-0000-0000-000000000001" -H "Content-Type: application/json" \
  -d '{"notas":"ok"}'
# -> 400 mismo mensaje
```

Con el campo presente, los cuatro devuelven 200/201 normalmente. **El campo es obligatorio de hecho,
pero ningún contrato lo dice y el error no lo nombra.**

**Por qué duele especialmente en `habit-preferences`:** el contrato
(`CONTRATO_DIA_A_DIA.md` §1.7) dice textualmente *"`triggerTime`/`limitTime` obligatorios;
`reminderMinutesBefore` opcional"* — **no menciona `reminderEnabled`**. Un cliente que siga el contrato
al pie de la letra manda exactamente los dos campos documentados como obligatorios y **recibe 400**.
Peor: ese 400 genérico **enmascara los errores reales**. Sin `reminderEnabled`, los tres casos de error
documentados devuelven todos el mismo mensaje inútil:

| Caso | Sin `reminderEnabled` | Con `reminderEnabled` |
|---|---|---|
| `limitTime` < `triggerTime` | 400 `"...mal formado"` | 400 `"horaLimite debe ser posterior a horaDisparo"` ✅ |
| hábito inexistente | 400 `"...mal formado"` | 404 `"Habito no encontrado: ..."` ✅ |
| cuenta suspendida | 400 `"...mal formado"` | 403 `"Cuenta suspendida"` ✅ |

**Los 12 DTOs afectados** (`grep` sobre `src/main/java/**/*Request.java`):

| DTO | Campo primitivo |
|---|---|
| `preferencia/UpdateHabitPreferenceRequest` | `boolean reminderEnabled` |
| `notifications/.../ActualizarPreferenciasRequest.Item` | `boolean enabled` |
| `evidence/.../RevisarManualmenteRequest` | `boolean aprobar` |
| `habitoadmin/CreateHabitRequest` | `boolean isOptional`, `boolean mandatoryOnIntoxication` |
| `habitoadmin/UpdateHabitRequest` | `boolean isOptional`, `boolean mandatoryOnIntoxication` |
| `habitoadmin/ToggleHabitRequest` | `boolean isActive` |
| `horarioadmin/CreateScheduleRequest` | `int startDay` |
| `guiaadmin/UpsertGuideRequest` | `int startDay`, `boolean closePrevious` |
| `guiaadmin/CreateGuideAttachmentRequest` | `int startDay` |
| `guiaadmin/ConfirmarAdjuntoGuiaArchivoRequest` | `int startDay` |
| `grabacionv90/RegistrarGrabacionV90Request` | `short index` |
| `rocasemanal/CerrarSemanaRequest` | `int autoevaluacionFin` |

**Sugerencia (no la apliqué — soy probador):** o se pasan a wrapper + `@NotNull` (el error nombra el
campo y el contrato queda explícito), o se documentan como obligatorios en los 4 contratos.

### 2.3 🟡 Una cuenta SUSPENDIDA sigue leyendo contenido de la comunidad

`CLAUDE.md` §0.3 fija la regla: *"un usuario SUSPENDED recibe 403 aunque su token sea válido"*, con la
excepción deliberada de los tickets de soporte. Barrí los 86 GET con el actor suspendido
`9c675442-6abb-46f3-8971-fe3bf0208127` y **9 devuelven 200**:

```bash
# El muro principal SÍ bloquea:
curl -s http://localhost:8080/api/v1/wall -H "X-Actor-Id: 9c675442-6abb-46f3-8971-fe3bf0208127"
# -> 403 {"message":"La cuenta esta suspendida", ...}

# Pero los comentarios de una publicación NO:
curl -s http://localhost:8080/api/v1/wall/0f7b325a-f464-4939-87d6-3d3f0dcfd1eb/comments \
  -H "X-Actor-Id: 9c675442-6abb-46f3-8971-fe3bf0208127"
# -> 200 {"comments":[{"authorId":"11111111-...","authorName":"Nuevo Nombre Aprendiz",
#         "authorAvatarUrl":"...","text":"Comentario de humo", ...}], "total":2}
```

Los 9 que devuelven 200 a un suspendido:

| Ruta | ¿Se justifica? |
|---|---|
| `/api/v1/support-tickets` | ✅ **Sí** — regla explícita y documentada |
| `/api/v1/habits` | Catálogo, sin datos de nadie. Discutible pero inocuo |
| `/api/v1/cursos/{id}/preview`, `/api/v1/lecciones/{id}/preview` | Solo `{"locked":bool}` |
| `/api/v1/wall/categories`, `/api/v1/wall/mine` | Metadatos y `{"count":0}` |
| `/api/v1/wall/{postId}/comments` | ⚠️ **Nombres, avatares y textos de terceros** |
| `/api/v1/wall/latest-author` | ⚠️ Nombre de otra persona |
| `/api/v1/testimonios` | ⚠️ Nombres, avatares y textos de terceros |

**La escritura sí está bien cerrada** — verificado: publicar → 403, comentar → 403, chat → 403.
Es una asimetría de lectura, no un agujero de escritura.

**Contraste que muestra que no es un descuido general:** `chat` lo hace bien. El suspendido *sí* es
participante del chat global, y aun así:
`GET /api/v1/chat/conversations/global/members` → `403 {"message":"La cuenta esta suspendida"}`.

### 2.4 🟡 `DELETE /api/v1/admin/cohorts/{id}` devuelve 204 para una cohorte que no existe

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE \
  http://localhost:8080/api/v1/admin/cohorts/deadbeef-1234-5678-9abc-def012345678 \
  -H "X-Actor-Id: 00000000-0000-0000-0000-000000000001"
# -> 204   (y otra vez -> 204)
```

Es el **único** de los 18 DELETE que hace esto. Sus vecinos directos son consistentes:

```
DELETE /api/v1/admin/cells/deadbeef-...   -> 404 {"message":"Celula no encontrada: deadbeef-..."}
GET    /api/v1/admin/cohorts/deadbeef-... -> 404 {"message":"Cohorte no encontrada: deadbeef-..."}
```

El panel admin no puede distinguir "la borré" de "nunca existió". El contrato
(`CONTRATO_COMUNIDAD.md` §4.6) documenta el `204` y el `409` si tiene células, pero **no dice nada del
caso inexistente**, así que no lo contradice — está sub-especificado.

---

## 3. Contrato documentado vs. realidad

### 3.1 🔴 El flujo `onboarding_v90` que usa todo el contrato **no existe en la base**

`CONTRATO_IDENTIDAD.md` usa `onboarding_v90` como valor de `flow` en **8 lugares** (líneas 350, 382,
386, 403, 463, 493, 699, 708), incluido el `curl` de ejemplo. La base migrada no lo tiene:

```bash
curl -s "http://localhost:8080/api/v1/onboarding/questionnaire?flow=onboarding_v90" \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111"
# -> 200 {"flow":"onboarding_v90","sections":[]}
```

**200 con `sections` vacío**, no 404. Un cliente que siga el contrato ve una pantalla en blanco y
ningún error que le diga por qué.

Los flujos que **sí** existen (`SELECT flujo, count(*) FROM renaser.secciones_onboarding GROUP BY flujo`)
y que devuelven contenido real:

| `flow` | Secciones | Verificado |
|---|---|---|
| `ficha_inicial` | 6 | 200 con preguntas (`full_name`, `whatsapp`, `email`…) |
| `cuestionario_profundo` | 6 | 200 (`energy_general`, escalas 1-10) |
| `diseno_destino` | 2 | 200 (`rock_body`, `rock_mind`, `rock_business`) |
| `cierre_dia_1` | 2 | — |
| `pacto` | 1 | 200 (`participant_name`, `signature`, `accepted_pacto`) |
| `terminos` | 1 | 200 (`accepted_terms`, `terms_signature`) |

El contrato también nombra la sección `ficha_personal` (línea 350); la real es `identidad_operativa`.

**Además:** cualquier `flow` inventado devuelve 200 vacío
(`?flow=flujo_inexistente` → `{"flow":"flujo_inexistente","sections":[]}`). Un 404 haría el bug obvio
en vez de silencioso.

### 3.2 🔴 53 de 177 rutas no aparecen en `docs/api/`

Comparé las 177 rutas únicas del código contra los 4 contratos. **53 no figuran**, incluido
**todo el módulo de autenticación** y **todo lo de avatar** — justamente lo construido hoy:

- **auth (7):** `/auth/login`, `/auth/logout`, `/auth/me`, `/auth/social`,
  `/auth/password/reset-request`, `/auth/password/reset-confirm`, `/auth/email-verification/send`
- **avatar (2):** `/users/me/avatar/upload-url`, `/users/me/avatar`
- **users (2):** `/users/me/account-deletion`, `/users/me/trainee-profile`
- **admin (21):** todo `/admin/staff/*`, `/admin/habits/*` (guías, horarios, adjuntos, toggle),
  `/admin/cells/{dashboard,mentores,mentores-disponibles,aprendices-disponibles,trainees}`,
  `/admin/audio-therapies/{week}`, `/admin/onboarding/dashboard`, `/admin/trainees/{id}/program-day`
- **pantallas del día (8):** `/home`, `/profile/logros`, `/journal/today`, `/habits`,
  `/habit-unlocks`, `/weekly-habit-days/{habitId}`, `/habits/{habitId}/rename`, `/audio-therapy/status`
- **otros (13):** `/spirit-audio/*`, `/chat/members`, `/chat/conversations/global/*`,
  `/account-requests/{check-email,exists,verify-email,{id}/status}`,
  `/onboarding/master-goal/validation`, `/habit-tracks/phone-free/evidence/upload-url`

**Consecuencia concreta:** el mecanismo de sesión no está documentado en ninguna parte. La sesión
**no viaja en cookie** — viaja en el header **`X-Auth-Token`** (`HeaderHttpSessionIdResolver.xAuthToken()`
en `SecurityConfig`, decisión deliberada para Expo/RN). Yo mismo me equivoqué probando con cookies y
obtuve un 401 engañoso. Un frontend que integre desde el contrato no tiene forma de saberlo.

> Verificado que el mecanismo real **funciona bien**: login → `X-Auth-Token: 990eb900-…` en la respuesta,
> `/auth/me` con ese header → 200, logout → 204, `/auth/me` después → 401. Y CORS lo expone correctamente
> (`Access-Control-Expose-Headers: X-Auth-Token`, con preflight OK para `localhost:8081`).

### 3.3 🟡 `POST /wall/media/upload-url`: el contrato dice que no valida, y sí valida

`CONTRATO_COMUNIDAD.md` §1.11 dice textualmente: *"**sin `@Valid` ni `@NotBlank` en el DTO ni en el
controller**; si mandás `null` o lo omitís, viaja como `null` hasta el firmante de storage
(comportamiento exacto ante `null` no confirmado…)"*. Ya está confirmado, y es lo contrario:

```bash
curl -s -X POST http://localhost:8080/api/v1/wall/media/upload-url \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111" -H "Content-Type: application/json" \
  -d '{"contentType":"image/jpeg"}'
# -> 400 {"message":"SolicitarUrlSubidaMediaCommand.tipoContenido: no debe estar vacío", ...}
```

De paso, **el mensaje de error filtra el nombre de la clase interna del command**
(`SolicitarUrlSubidaMediaCommand.tipoContenido`). Pasa igual en
`/rocks/{id}/evidence/upload-url` (`SolicitarUrlAdjuntoRocaCommand.tipoContenido`) y
`/habit-tracks/phone-free/evidence/upload-url` (`SolicitarUrlAdjuntoRachaCommand.tipoContenido`).
Los DTO validados con `@Valid` en el controller sí devuelven el nombre limpio (`fileName`, `media`, `delta`).

### 3.4 🟡 `POST /users/me` devuelve un campo que el contrato no declara

`CONTRATO_IDENTIDAD.md` §1 documenta 8 campos. La respuesta real trae **9**:

```json
{"id":"...","email":"...","role":"TRAINEE","status":"ACTIVE","fullName":"...","avatarUrl":"...",
 "bio":"...","department":null,
 "traineeProfile":{"personalChallengeName":null,"startDate":"2026-08-25","goalType":null,
                   "isProgramCompleted":false,"programCompletedAt":null,"postProgramDay":0}}
```

`traineeProfile` (objeto anidado, `null` para no-aprendices) no está en el contrato. Es aditivo, así
que no rompe a nadie, pero el frontend no sabe que puede usarlo.

### 3.5 🟡 Los nombres de campo de `upload-url` son inconsistentes (y el contrato lo refleja fielmente)

No es un error del documento — es la API la que es inconsistente, y conviene decirlo:

| Endpoint | Campo | Respuesta |
|---|---|---|
| `/users/me/avatar/upload-url` | `tipoContenido` | `{url, bucket, ruta}` |
| `/wall/media/upload-url` | `tipoContenido` | `{uploadUrl, bucket, ruta}` |
| `/rocks/{id}/evidence/upload-url` | `tipoContenido` | — |
| `/habit-tracks/phone-free/evidence/upload-url` | `tipoContenido` | — |
| `/admin/habits/{habitId}/guide-attachments/upload-url` | `tipoContenido` | — |
| `/onboarding/media/upload-url` | **`contentType`** | `{uploadUrl, bucket, **path**}` |
| `/calendar/events/{id}/portada/upload-url` | **`contentType`** | `{url, bucket, ruta}` |
| `/support-tickets/attachments/upload-url` | `contentType` + **`fileName`** obligatorio | — |

Tres nombres de campo de entrada, tres de URL de salida (`url`/`uploadUrl`) y dos de ruta
(`ruta`/`path`) para el mismo patrón de dos pasos.

### 3.6 🟡 El campo `bucket` de las respuestas de `upload-url` no es el bucket real

Bajo S3 real, la respuesta dice `"bucket":"renaser-files"` pero la URL firmada apunta a
`s3-renaser90dias`:

```json
{"url":"https://s3-renaser90dias.s3.amazonaws.com/avatares/1111...?X-Amz-Algorithm=...",
 "bucket":"renaser-files","ruta":"avatares/11111111-1111-1111-1111-111111111111"}
```

`renaser-files`, `onboarding-media`, `wall` son etiquetas lógicas hardcodeadas
(`AvatarService.BUCKET_AVATARES = "renaser-files"`); el bucket real es siempre
`renaser.storage.s3.bucket`. El contrato le pide al cliente devolver ese `bucket` en el paso 3, lo cual
funciona solo porque el backend lo ignora y recalcula la ruta. Es un campo engañoso.

### 3.7 🟡 Documentación desactualizada sobre S3

`CONTRATO_DIA_A_DIA.md` (línea 15) sigue diciendo: *"**El almacenamiento de archivos (S3) NO está
configurado todavía (D-34).** […] usa `NoOpAlmacenamientoAdapter`, que devuelve literalmente
`about:blank#pendiente-s3/<ruta>`"*. Ya no es cierto: `S3AlmacenamientoAdapter` existe y funciona (§4).

---

## 4. Qué cambió al activar S3

Arranqué una segunda vez con `STORAGE_PROVEEDOR=s3 AWS_PROFILE=renaser AWS_REGION=us-east-1`.
**Todo lo de storage funciona.**

| Endpoint | `noop` (default) | `s3` |
|---|---|---|
| `/users/me/avatar/upload-url` | `about:blank#pendiente-s3/avatares/…` | `https://s3-renaser90dias.s3.amazonaws.com/avatares/…?X-Amz-Signature=…&X-Amz-Expires=600` |
| `/onboarding/media/upload-url` | `about:blank#pendiente-s3/onboarding/…` | firmada, `X-Amz-Expires=900` |
| `/audio-therapy/status` | `about:blank#pendiente-s3/audioterapias/…` | firmada, `X-Amz-Expires=3600` |
| `/cursos` → `portadaFirmada` | `about:blank#…` | firmada |
| `/wall` → `media[].url` | `about:blank#…` | firmada |
| `/phase-contracts` → `signatureUrl` | `about:blank#…` | firmada |
| `/users/me` → `avatarUrl` | `about:blank#…` | `https://s3-renaser90dias.s3.amazonaws.com/avatares/<id>` **sin firma** ✅ |

### 4.1 El avatar abre sin credenciales — verificado de punta a punta

Primero me dio **403**, pero **no era la política del bucket: era que no había ningún objeto**. S3
responde 403 (no 404) a un anónimo sin `s3:ListBucket`. Lo confirmé con el AWS CLI:

```
$ aws s3api head-object --bucket s3-renaser90dias --key avatares/11111111-...
An error occurred (404) when calling the HeadObject operation: Not Found

$ aws s3api get-bucket-policy --bucket s3-renaser90dias
{"Sid":"LecturaPublicaSoloAvatares","Effect":"Allow","Principal":"*",
 "Action":"s3:GetObject","Resource":"arn:aws:s3:::s3-renaser90dias/avatares/*"}

$ aws s3api get-public-access-block --bucket s3-renaser90dias
BlockPublicPolicy: false, RestrictPublicBuckets: false     # correcto para que la policy rija
```

La política está bien. Así que hice el flujo real de 3 pasos con un PNG de 1×1:

```
1. POST /users/me/avatar/upload-url  {"tipoContenido":"image/png"}   -> 200, URL firmada
2. PUT  <esa URL> -H "Content-Type: image/png" --data-binary @zz.png -> 200
3. PATCH /users/me/avatar {"bucket":"renaser-files","ruta":"avatares/<id>"} -> 204

4. curl https://s3-renaser90dias.s3.amazonaws.com/avatares/11111111-1111-1111-1111-111111111111
   -> 200  content-type=image/png  bytes=67     ← SIN credenciales, SIN firma ✅
```

**Confirmado: el requisito de infraestructura de D-55 está bien aplicado.**

Y la lectura firmada también sirve de verdad, contra un archivo real de 40 MB:

```
GET <url firmada de /audio-therapy/status> -> 200 (42.723.369 bytes)
```

**Limpieza:** borré el objeto de prueba **y su versión** (el bucket tiene versionado, así que un
`delete-object` simple solo deja un delete-marker). `avatares/` quedó exactamente como lo encontré:
solo el `_prueba-acceso.txt` previo del dueño.

### 4.2 Detalle menor: las rutas que ya son URL se firman igual, y quedan rotas

Datos de prueba viejos guardaron una URL completa donde iba una ruta. El backend la trata como clave S3
sin chequear:

```
"url":"https://s3-renaser90dias.s3.amazonaws.com/https%3A//example.com/b.jpg?X-Amz-Algorithm=..."
```

Es la "trampa" que el contrato ya advierte (§1.2 de `CONTRATO_COMUNIDAD.md`: mandá `ruta`, no
`uploadUrl`), pero no hay guarda: el backend genera una URL firmada sin sentido en vez de rechazar.
Baja severidad — son datos de prueba —, pero no hay nada que impida que un cliente lo reintroduzca.

---

## 5. Lo que funciona bien (probado, no supuesto)

Para que el informe no quede sesgado: **220 de 221 endpoints no devuelven 500**, y el grueso del
sistema respondió correctamente.

- **Barrido de 86 GET × 5 roles (430 llamadas): cero 500.** Con el mejor actor para cada ruta:
  72 × 200, y los no-2xx son todos correctos (404 por ids que sustituí a propósito, 403 de
  autorización real).
- **Autorización por rol**: `GET /admin/trainees/{id}/habits` con actor APRENDIZ →
  `403 "Solo ADMIN/ALCHEMIST administran el catalogo de habitos"`. Igual en `/wall/hidden`,
  `/account-requests`.
- **Blindaje anti mass-assignment: funciona.** Mandé `"role":"ADMIN"` en el alta pública; el usuario
  quedó `APRENDIZ`:
  ```
  POST /api/v1/account-requests {... "role":"ADMIN"} -> 202
  SELECT rol FROM renaser.usuarios WHERE email='zz.vivo2@renaser.dev'  ->  APRENDIZ  ✅
  ```
- **Flujo de alta completo, de punta a punta**: `email-verification/send` (202) → código real de Redis
  → `confirm` (200 + `verificationToken`) → `account-requests` (202) → `approve` (204) →
  `login` (200 + `X-Auth-Token`) → `/auth/me` (200) → `logout` (204) → `/auth/me` (401). Y
  `login` con clave incorrecta → `401 "Email o contrasena incorrectos"`.
- **`habit-preferences` (construido hoy) cumple el contrato**, con `reminderEnabled` presente:
  `scheduleEdits {used:0→1, remaining:3→2, limit:3, period:"WEEK"}`, 23 hábitos listados,
  `deferred:false`, y los 400/403/404 documentados salen con el mensaje correcto.
- **`GET /admin/trainees/{traineeId}/habits` (construido hoy)** responde con la forma exacta del
  contrato §1.8: `traineeId`, `programDay`, `localDate`, `timeZone`, `scheduleEdits`, y `habits[]` con
  `pendingScheduleChange`/`unlock`/`weeklyDayChoice`.
- **RenasIA SSE funciona** tal como lo describe `CONTRATO_CONTENIDO_IA.md` §5.2: 200,
  `data:Renasia todavia no esta disponible: faltan credenciales de IA por configurar (D-39).`
- **Login social falla con elegancia**: `409 "Login con Google sin configurar: faltan
  GOOGLE_OAUTH_CLIENT_ID/GOOGLE_OAUTH_CLIENT_SECRET"` — no un 500.
- **`GET /api/v1/home` es honesto sobre lo que le falta**: devuelve un array `bloqueos` que nombra el
  gap #21 en vez de inventar datos. Buena decisión de diseño.
- **17 de los 18 DELETE** devuelven 404 con mensaje claro ante un id inexistente.
- **UTF-8 en el body: sin problemas.** Tuve un falso positivo (400 con un guion largo) que resultó ser
  mi shell de Windows mutilando el byte, no el servidor. Con `--data-binary` desde archivo,
  `"ZZ acentos áéí ñ"` se guarda y se devuelve intacto. **No es un bug.**

---

## 6. Qué NO pude probar, y por qué

| Qué | Por qué |
|---|---|
| Rama `ALQUIMISTA` de todos los guards `requireAdmin` | **No existe ningún usuario con ese rol** en la base (§1.2). No inventé UUIDs |
| La app contra la base `renaser` real | No arranca (§1.1). Todo se probó contra un clon idéntico |
| Validación IA de evidencia y V90 (camino feliz) | Sin credenciales Gemini (D-39). El adaptador NoOp responde y no llegué a ver el flujo real |
| RenasIA con respuesta real de modelo | Mismo motivo. Solo verifiqué el transporte SSE |
| WebSocket `/ws` + STOMP `/topic/conversaciones/{id}` | Fuera del alcance de `curl`. Los endpoints REST de `chat` sí quedaron probados |
| `POST /habit-tracks/{id}/complete` en camino feliz | El único track del actor ya estaba `COMPLETADO` → 409. `GET /habit-tracks/today` devuelve `[]` porque el motor diario que genera tracks todavía no existe (backlog conocido) |
| Santuario / racha sin celular en camino feliz | Ningún hábito del actor es de tipo `BLOQUEO` (`400 "Este habito no es de tipo Santuario"`); tampoco había racha en curso |
| Envío real de correo | `EMAIL_PROVEEDOR=noop`. Saqué los códigos de Redis, que es equivalente para probar el flujo |
| Subida real a S3 en los otros 7 endpoints de `upload-url` | Solo hice el PUT real en avatar (§4.1). En el resto verifiqué que la URL sale firmada, no que el objeto suba |

---

## 7. Rastro de lo que creé, y estado final

**Todas las mutaciones se hicieron contra el clon `renaser_pruebas`, que ya está borrado.** La base
`renaser` no fue tocada en ningún momento — la app nunca apuntó a ella. Verificado al terminar:

```
publicaciones ZZ: 0 | usuarios zz.*: 0 | habitos ZZ: 0 | testimonios prueba: 0 | eventos ZZ: 0
total usuarios: 25 (igual que al empezar) | columna: avatar_ruta | flyway V13: "usuarios avatar ruta"
```

| Recurso | Estado |
|---|---|
| BD `renaser_pruebas` (clon) | **Borrada** (`DROP DATABASE`) — con ella se fue todo lo que creé |
| BD `renaser` (real) | **Intacta**, verificado campo por campo |
| Objeto S3 `avatares/11111111-…` | **Borrado**, incluida su versión y el delete-marker |
| Bucket `s3-renaser90dias` | Como lo encontré: solo `avatares/_prueba-acceso.txt` previo |
| Claves de Redis que creé | Borradas (códigos de verificación, token de reset, rate-limits) |
| Puerto 8080 | **Libre** |
| Contenedores `renaser-db` / `renaser-redis` | Arriba y sanos, sin reiniciar |
| Código de producción | **Sin tocar** — `git status` igual que al empezar |

---

## 8. Orden sugerido para arreglar

1. **Decidir cuál V13 vale y alinear base con código** (§1.1) — nadie puede levantar la app hasta eso.
2. **`@NotNull` en `UpdateAudioTherapyDurationRequest.durationDays`** (§2.1) — un 500 en producción.
3. **`@ExceptionHandler(Exception.class)` en `GlobalExceptionHandler`** (§2.1) — para que ningún 500
   se salga del sobre `{"message","timestamp"}`.
4. **Los 12 DTOs con primitivos** (§2.2) — es el que más va a costarle al frontend, porque el error no
   dice qué falta.
5. **`onboarding_v90` en el contrato** (§3.1) — el frontend va a integrar contra una pantalla vacía.
6. **Documentar auth y avatar** (§3.2) — sobre todo que la sesión viaja en `X-Auth-Token`.
7. Suspendido leyendo comunidad (§2.3), `DELETE cohorts` 204 (§2.4), y los detalles de §3.3–§3.7.
