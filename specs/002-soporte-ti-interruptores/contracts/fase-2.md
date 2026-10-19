# Contrato — Soporte TI fase 2: despliegue progresivo, roles personalizados y ver como rol

Lo comparten los agentes A2 (despliegue), B2 (roles personalizados), B3 (ver como rol), C2 (app) y el QA. Todo cambio a
este archivo se avisa a los demás. Sale de [plan-fase-2.md](../plan-fase-2.md) con las respuestas del dueño del
2026-10-14 (§0 de este contrato). La fase 1 sigue rigiendo: [funcionalidades.md](funcionalidades.md).

Nombres: inglés en la API y en los enums de dominio, castellano en la base, como el resto del sistema.

## 0. Decisiones del dueño (2026-10-14)

| | Decisión |
|---|---|
| P1 | Una funcionalidad en despliegue que a la persona todavía no le toca **se esconde** (no «Muy pronto») |
| P2 | «Ver como rol» con **cuentas de prueba por rol**, en **solo lectura** |
| P3 | La audiencia de un hábito (D-264) se elige **solo entre los seis roles base**: `GET /admin/habits/roles` no cambia |
| P4 | «Ver como» **incluye** los roles personalizados |
| P5 | Métricas del despliegue: **últimas 24 h, por hora** |
| P6 | La auditoría la ve **solo Soporte TI**; los avisos de cambios críticos van **solo por push** (como hoy) |
| P7 | Soporte TI va en una **cuenta aparte** del dueño; no entra a Administración (ya respondida) |

## 1. Base común (ya hecha: commit `92740e4c`, rama `claude/fase2-base`)

Todos los agentes salen de esta rama.

- **`ReglaDeDisponibilidad`** (`plataforma.domain.model.estado`): `Optional<String> noDisponibleEn(List<String> cadena,
  ActorEvaluado actor)`. `cadena` son las claves desde la raíz hasta la pedida. Devuelve la clave del nivel que deja
  fuera a la persona. Cada regla es un bean de Spring en `plataforma` (con `@Order`: **despliegue `@Order(10)`, rol
  personalizado `@Order(20)`**). Responde desde memoria: se evalúa en cada pedido y ~55 veces por `GET /mias`.
- **`ActorEvaluado`**: `id()` y `rol()` (perezoso, una lectura). Las reglas usan `id()` y resuelven lo suyo con su copia.
- **Orden**: apagado manual > circuito de la IA > reglas. Las reglas solo se preguntan con la cadena prendida y con
  actor: un proceso sin actor (`Interruptores.prendida`) no las ve. **Un proceso que actúa sobre una persona y depende
  de una funcionalidad en despliegue pregunta `prendidaPara(clave, persona)`**, no `prendida(clave)`.
- **`ResultadoDeEvaluacion.noDisponible`** y **`FuncionalidadNoDisponibleException`** (extiende
  `FuncionalidadApagadaException`, para que el chat, SER y los hábitos corten igual). HTTP:

  ```json
  404 { "message": "Esta función todavía no está disponible para tu cuenta.", "timestamp": "…",
        "codigo": "FUNCIONALIDAD_NO_DISPONIBLE", "funcionalidad": "eventos" }
  ```
- **`GET /api/v1/funcionalidades/mias`** suma `noDisponibles: ["eventos", "eventos.asistencia", …]` (aditivo; las
  hijas figuran todas; lo que está en `noDisponibles` no está en `apagadas`). El ETag incluye la lista.
- Una regla que lanza una excepción se trata como «disponible» y se anota en WARN (`EstadoVigente`).

## 2. Migraciones (números reservados; cada una justificada en su cabecera)

| Agente | Migraciones | Contenido |
|---|---|---|
| A2 | **V103**, **V104** | despliegue y probadores; métricas por hora |
| B2 | **V105**, **V106** | roles personalizados y `usuarios.rol_personalizado_id`; `ROL_PERSONALIZADO` en el `CHECK` de `cambios_auditados.tipo` |
| B3 | **V107**, **V108** | `usuarios.es_cuenta_de_prueba`; `VER_COMO` en el `CHECK` de `cambios_auditados.tipo` |

`ALTER TYPE … ADD VALUE` va solo en su migración (E-187). Un número que sobre se deja sin usar; no se renumera.

## 3. A2 — despliegue progresivo (US4; FR-018 a FR-021)

### 3.1 Datos (V103, V104)

```sql
CREATE TABLE renaser.funcionalidades_despliegue (
    clave            text PRIMARY KEY,                -- existe en el catálogo (lo verifica la app al arrancar)
    porcentaje       smallint NOT NULL CHECK (porcentaje BETWEEN 0 AND 99),
    version          bigint   NOT NULL,
    iniciado_en      timestamptz NOT NULL,
    actualizado_por  uuid NOT NULL REFERENCES renaser.usuarios(id),
    actualizado_en   timestamptz NOT NULL
);
CREATE TABLE renaser.funcionalidades_probadores (
    clave       text NOT NULL REFERENCES renaser.funcionalidades_despliegue(clave) ON DELETE CASCADE,
    usuario_id  uuid NOT NULL REFERENCES renaser.usuarios(id) ON DELETE CASCADE,
    sumado_por  uuid NOT NULL REFERENCES renaser.usuarios(id),
    sumado_en   timestamptz NOT NULL,
    PRIMARY KEY (clave, usuario_id)
);
CREATE TABLE renaser.funcionalidades_metricas_hora (
    clave    text NOT NULL,
    hora     timestamptz NOT NULL,       -- truncada a la hora, UTC
    dentro   boolean NOT NULL,           -- la persona estaba dentro del despliegue
    pedidos  bigint NOT NULL,
    errores  bigint NOT NULL,            -- respuestas 5xx
    PRIMARY KEY (clave, hora, dentro)
);
```

Sin fila en `funcionalidades_despliegue`, la funcionalidad **no está en despliegue** (llega a todos). Porcentaje 100 =
«Prender para todos»: se borra la fila (y sus probadores por cascada).

### 3.2 Regla (`@Order(10)`)

- `cubeta(clave, usuarioId) = (primeros 4 bytes de SHA-256(clave + ":" + usuarioId), sin signo) mod 100`.
- Dentro si `cubeta < porcentaje` **o** es probadora. Función pura en el dominio; prueba de 1000 personas × 10
  lecturas sin oscilación (SC-004) y de que subir el porcentaje no saca a nadie.
- Sobre la cadena: el **primer nivel en despliegue que deja fuera** a la persona es el que se devuelve (una madre en
  despliegue esconde a sus hijas).
- Copia en memoria con el mismo patrón de la fase 1 (Postgres + aviso por el canal Redis `renaser:funcionalidades` +
  recarga cada 30 s): **no** se consulta la base por pedido.

### 3.3 Métricas

`InterruptoresInterceptor` ya conoce la clave del pedido. En `afterCompletion` se cuenta, por clave con despliegue
activo, `pedidos` y `errores` (status ≥ 500) separando dentro / fuera. Se acumula en memoria y se escribe **en lote cada
minuto** (upsert sumando). Solo para claves en despliegue. Se borra lo de más de 48 h. Sin etiquetas por persona en
Prometheus.

### 3.4 REST (consola: `IT_SUPPORT`, `OPERATE_PLATFORM`, `X-Consola-Token`; crítico → `X-Reautenticacion`)

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| `GET /api/v1/soporte-ti/funcionalidades/{clave}/despliegue` | — | `200 { clave, enDespliegue, porcentaje, version, iniciadoEn, actualizadoPor:{id,nombre}, actualizadoEn, probadores:[{id,nombre,email}], metricas:[{hora, dentro:{pedidos,errores}, fuera:{pedidos,errores}}] }` (`metricas`: 24 h, por hora, las horas sin datos en 0) |
| `PUT /api/v1/soporte-ti/funcionalidades/{clave}/despliegue` | `{ porcentaje: 0..100, motivo, version? }` | `200` el despliegue nuevo · `409` versión vieja · `428` crítico sin reautenticación · `400` sin motivo, clave anti-bloqueo o porcentaje fuera de rango |
| `PUT /api/v1/soporte-ti/funcionalidades/{clave}/despliegue/probadores/{usuarioId}` | `{ motivo? }` | `200` · `404` persona inexistente · `409` la funcionalidad no está en despliegue |
| `DELETE /api/v1/soporte-ti/funcionalidades/{clave}/despliegue/probadores/{usuarioId}` | — | `204` |
| `GET /api/v1/soporte-ti/personas?q=` | — | `200 [ {id, nombre, email, rol} ]` (máx. 20; nombre o correo, sin tildes; activas; sin cuentas de prueba) |

- `porcentaje: 100` termina el despliegue (borra la fila). Empezar un despliegue es poner un porcentaje < 100 en una
  funcionalidad que no lo tenía.
- **Aditivo (A2, 2026-10-14, decisión del coordinador): funcionalidades que se usan sin cuenta.** Las que en el catálogo
  no tienen roles (`rolesQueLaUsan` vacío; hoy solo `registro`, el alta pública) no se despliegan: sin persona no hay
  cubeta y la regla no se aplica. Empezar su despliegue responde `409 { codigo: "DESPLIEGUE_SIN_CUENTA", funcionalidad,
  message: "Esta funcionalidad se usa sin cuenta; un despliegue no tendría efecto." }` (antes de pedir la contraseña), y
  el `GET …/despliegue` suma `desplegable: boolean` (`false` en esas, `true` en el resto) para que la app esconda el
  control. La decisión sale del catálogo, no de un nombre fijo.
- **Crítico** (pide contraseña, como en la fase 1): empezar un despliegue o bajar el porcentaje de una funcionalidad
  marcada crítica, y empezar uno con porcentaje 0 en cualquiera (la esconde para todos salvo probadores).
- `GET /api/v1/soporte-ti/funcionalidades` suma por funcionalidad `despliegue: { porcentaje, probadores } | null`
  (aditivo).
- Auditoría: tipo `MANUAL`, `clave` la funcionalidad, `antes`/`despues` = `{ "despliegue": { "porcentaje": 25,
  "probadores": 3 } }` (`null` si no estaba en despliegue). Sumar o quitar una probadora también deja su fila
  (`{ "despliegue": { "porcentaje": 25, "probadores": 4 } }` y en `motivo` quién).
- Evento `FuncionalidadCambiadaEvent` como en la fase 1 al empezar, cambiar o terminar un despliegue (el push a
  Soporte TI ya existe; a Admin/Alquimista solo si es crítica).

## 4. B2 — roles personalizados (US7; FR-029 a FR-034)

### 4.1 Dónde viven

En el módulo **`users`** (un rol es cosa de `users`; `plataforma` ya depende de `users.api`, y lo contrario sería un
ciclo que Spring Modulith rechaza). `users.api` expone lo que necesita `plataforma`:

```java
// users.api
public interface RolesPersonalizadosFinder {
    Optional<RolPersonalizadoResumen> delUsuario(UserId usuario);          // el asignado, si tiene y está activo
    List<RolPersonalizadoResumen> todos();                                 // activos e inactivos
}
public record RolPersonalizadoResumen(UUID id, String clave, String nombre, String descripcion, UserRole rolBase,
                                      boolean activo, Set<String> funcionalidades, long personas, long version,
                                      UserId actualizadoPor, Instant actualizadoEn) {}
public interface GestionarRolesPersonalizadosUseCase { … crear, editar, desactivar, borrar, reemplazarEnBloque … }
record RolPersonalizadoCambiadoEvent(UUID rolId, Instant occurredAt) implements DomainEvent {}   // y al asignar
```

### 4.2 Datos (V105, V106)

`roles_personalizados (id uuid PK, clave text UNIQUE, nombre text UNIQUE, descripcion, rol_base rol_usuario NOT NULL,
activo boolean, version bigint, creado_por/en, actualizado_por/en)`, `roles_personalizados_funcionalidades (rol_id, clave,
PK (rol_id, clave))` y `usuarios.rol_personalizado_id uuid NULL REFERENCES roles_personalizados(id)`. **`usuarios.rol`
sigue siendo el rol base** (FR-030): guards, enums espejo, rankings, padrones y hábitos por rol no cambian.

### 4.3 Reglas de negocio

- Subconjunto del rol base: solo claves del catálogo que el rol base usa (`rolesQueLaUsan`); **nunca base
  `IT_SUPPORT`**; nombre único y distinto de los seis nombres visibles; hasta **20 activos**.
- Con personas asignadas: no se borra ni cambia de base; se desactiva (las personas quedan con el rol base solo, sin
  restricción) o se **reemplaza en bloque** (a otro rol personalizado con la misma base, o a ninguno), con motivo.
- Una funcionalidad nueva del catálogo nace **sin marcar** en los roles personalizados.

### 4.4 Regla de disponibilidad (`@Order(20)`, en `plataforma`)

Con rol personalizado activo, una funcionalidad le corresponde si su clave **o una madre** está marcada en el rol; si
no, `noDisponible` en la clave pedida. Sin rol personalizado (o inactivo), la regla no hace nada. Copia en memoria
(por persona → rol, por rol → claves), invalidada por `RolPersonalizadoCambiadoEvent` y recargada cada 30 s.

### 4.5 REST

Consola (Soporte TI; mismo guard de la fase 1; los controladores en `plataforma`, que valida las claves contra el
catálogo y llama a `users.api`):

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| `GET /api/v1/soporte-ti/roles` | — | `200 [ { id, clave, nombre, descripcion, rolBase, activo, funcionalidades:[clave], personas, version, actualizadoPor:{id,nombre}, actualizadoEn } ]` |
| `POST /api/v1/soporte-ti/roles` | `{ nombre, descripcion?, rolBase, funcionalidades:[clave], motivo }` | `201` el rol · `400` (base `IT_SUPPORT`, nombre repetido, clave fuera de la base, más de 20 activos) |
| `PUT /api/v1/soporte-ti/roles/{id}` | `{ nombre, descripcion?, funcionalidades, activo, motivo, version }` | `200` · `409` versión vieja · `400` |
| `DELETE /api/v1/soporte-ti/roles/{id}?motivo=` | — | `204` · `409` con personas asignadas |
| `POST /api/v1/soporte-ti/roles/{id}/reemplazar` | `{ rolDestinoId: uuid \| null, motivo }` | `200 { personasMovidas }` |

Asignación (Administración, como hoy: ADMIN y ALQUIMISTA; solo ADMIN asigna algo que hereda de ADMIN o ALQUIMISTA):

- `PATCH /api/v1/users/{id}/role` y aprobar una solicitud aceptan `rolPersonalizadoId` opcional (aditivo). Con él, el
  rol base sale del rol personalizado y se ignora `role`; sin él, se borra el que tuviera.
- `GET /api/v1/admin/roles-asignables` (nuevo) → `[ { clave, nombre, rolBase, rolPersonalizadoId: uuid | null } ]`: los
  seis base (`clave` = el `UserRole`) y los personalizados activos (`clave` = `"rp:" + id`). Lo usan Equipo y roles y
  Solicitudes. `GET /admin/habits/roles` **no** cambia (P3).
- `/auth/me`, `POST /users/me`, las listas de personas de Administración y la info de integrantes del chat suman
  `rolPersonalizado: { id, nombre } | null` (aditivo). `role` sigue siendo el base.
- Auditoría: tipo nuevo `ROL_PERSONALIZADO` (crear, editar, desactivar, borrar, reemplazar), `clave` = `"rp:" + id`,
  `antes`/`despues` con `{ nombre, rolBase, activo, funcionalidades }`.
- **Aditivo (B2, 2026-10-14; D-272, pedido del coordinador): `codigo` en los rechazos de la consola de roles.** Mismo
  formato de error con `codigo` (`ApiErrorConCodigo`, sin `funcionalidad`): `{ "message": "…", "timestamp": "…",
  "codigo": "…" }`. Los estados siguen los de la tabla de arriba:

  | Estado | `codigo` | Cuándo |
  |---|---|---|
  | 409 | `VERSION_CAMBIADA` | `PUT` con una `version` que ya no es la del rol (otra persona lo cambió) |
  | 409 | `ROL_CON_PERSONAS` | `PUT` que cambia el `rolBase` de un rol con personas, o `DELETE` de un rol con personas |
  | 400 | `NOMBRE_REPETIDO` | `POST` o `PUT` con el nombre de otro rol personalizado o de un rol base (sin mayúsculas ni tildes) |
  | 400 | `MAXIMO_DE_ROLES` | `POST` de un rol nuevo, o `PUT` que reactiva uno, con 20 activos |

  El resto de los 400 (sin motivo, base `IT_SUPPORT`, clave fuera de la base) siguen sin `codigo`.
- **Aditivo (B2, 2026-10-14; D-272, respuesta del dueño): push a Administración.** Si Soporte TI edita un rol con
  personas cambiando `funcionalidades`, `activo` o la base, o lo reemplaza en bloque moviendo personas, cada ADMIN y
  ALQUIMISTA activo recibe una notificación `OPERACION_PLATAFORMA` (fila en la bandeja y push), sin `rutaApp`:
  `titulo` «Soporte TI cambió un rol», `cuerpo` «Soporte TI cambió el rol «<nombre>» (<N> persona|personas): <resumen>.
  Motivo: <motivo>». `<resumen>` es una o varias de «lo desactivó: quedan solo con su rol base (<Base>)», «lo volvió a
  activar», «ahora hereda de <Base>», «sumó N funcionalidad(es) y quitó M funcionalidad(es)» (separadas por «; »), o
  en un reemplazo «las pasó a «<destino>»» / «quedaron solo con su rol base (<Base>)». Renombrar, cambiar la
  descripción o editar un rol sin personas no avisa. Las cuentas de prueba no lo reciben.

## 5. B3 — ver como rol (US5), segunda tanda, sobre B2

- **Cuentas de prueba**: `usuarios.es_cuenta_de_prueba boolean NOT NULL DEFAULT false` (V107). Una por rol base y una
  por rol personalizado activo, creadas a pedido la primera vez: correo `ver-como+<rol|rp-id>@renaser.invalid`, nombre
  «Prueba · Mentor», **sin contraseña** (no se puede entrar con ellas por login). El aprendiz de prueba tiene su
  programa activo y está a mitad del programa. El mentor de prueba lidera un «Grupo de prueba (ver como)» con el
  aprendiz y la guía de prueba.
- **Se excluyen** de rankings, padrones, semáforos, listas de Administración, chats de la comunidad y de soporte,
  avisos (push y correo) y métricas. Cada exclusión con su prueba.
- **Sesión de solo lectura**: `POST /api/v1/soporte-ti/ver-como { rol: "MENTOR" } | { rolPersonalizadoId }` → `200 {
  cuenta: {id, nombre, rol, rolPersonalizado}, venceEn }` y la cabecera `X-Auth-Token` de una **sesión nueva** de esa
  cuenta, marcada `soloLectura` y con el id de quien la abrió (30 min). Todo pedido de escritura (POST, PUT, PATCH,
  DELETE y los SEND de WebSocket) de una sesión `soloLectura` → `403 { codigo: "MODO_SOLO_LECTURA", message: "Estás
  viendo la app como otro rol: no se puede cambiar nada." }`, salvo `DELETE /api/v1/soporte-ti/ver-como` (cierra esa
  sesión) y `POST /auth/logout`.
- La sesión `soloLectura` **no abre** la consola Soporte TI (la cuenta de prueba no es `IT_SUPPORT`).
- Auditoría: tipo nuevo `VER_COMO` al entrar y al salir (`despues: { rol | rolPersonalizado }`).
- `users` no puede depender de `plataforma`: si hace falta comprobar el desbloqueo de la consola desde `users`, se hace
  con un puerto en `shared` implementado por `plataforma` (como `Interruptores`).
- **Aditivo (B3, 2026-10-14; D-273).** Lo que el contrato no decía y quedó así (para C2 y el QA):
  - `POST /ver-como` → `200 { cuenta: { id, nombre, rol, rolPersonalizado: { id, nombre } | null }, venceEn }`; `rol` es
    el nombre de la API (`TRAINEE`, `TRAINEE_GUIDE`, `MENTOR`, `MENTOR_LEAD`, `ADMIN`, `ALCHEMIST`); con un rol
    personalizado, `rol` es su base. Errores: 403 (no es Soporte TI activo), 423 (consola bloqueada), 400 (ni `rol` ni
    `rolPersonalizadoId`, los dos, un rol que no existe, `IT_SUPPORT`, o un rol personalizado desactivado), 404 (rol
    personalizado que no existe).
  - `DELETE /ver-como` se llama **con el token de la sesión «ver como»**: 204; con cualquier otra sesión, 404 y no cierra
    nada. La respuesta trae `X-Auth-Token` vacío (la sesión se invalidó), como el logout.
  - Además de las dos salidas, la sesión de solo lectura puede pedir `POST /api/v1/users/me` (es la lectura del perfil
    propio que la app hace al entrar; E-626). Editarlo sigue siendo 403.
  - Abrir un WebSocket que no sea `/ws` (la voz en vivo con SER) → 403. En `/ws` se puede suscribir; un `SEND` recibe un
    frame `ERROR` cuyo `message` empieza con `MODO_SOLO_LECTURA:`.
  - A los 30 minutos (vida absoluta, aunque se siga usando) cualquier pedido con ese token → `401 { codigo:
    "VER_COMO_VENCIDO", message: "La vista «ver como» venció: vuelve a abrirla desde la consola." }` y la sesión se
    cierra. La app debería tratarlo como salir de «ver como» (restaurar la sesión de Soporte TI), no como sesión vencida
    de Soporte TI.
  - La sesión se indexa a nombre de Soporte TI: suspenderla o cambiarle la contraseña también cierra sus «ver como».
  - Auditoría `VER_COMO` sin `clave`, actor = quien la abrió: al entrar `antes: null` y `despues: { rol }` o
    `{ rolPersonalizado: { id, nombre } }`; al salir (`DELETE`), al revés. Salir por `POST /auth/logout` o por
    vencimiento **no** deja fila.
  - Cuentas de prueba con grupo: aprendiz, guía y mentor viven en el «Grupo de prueba (ver como)»; una de un rol
    personalizado entra al mismo grupo como aprendiz (base aprendiz) o como guía (base mentor o guía). Líder, admin y
    alquimista no tienen grupo. El Admin de prueba no ve los chats de los grupos reales (Soporte TI no lee chats).
  - Un `GET` que genera datos (los registros del día, el estado de onboarding) se permite: escribe solo datos de la cuenta
    de prueba, que está excluida de todo lo que ven los demás.

## 6. C2 — app

- **Esconder lo no disponible** (P1): `useFuncionalidad(clave)` suma `disponible: boolean` (falso si la clave está en
  `noDisponibles`). Donde la funcionalidad tiene una entrada (medallón de Comunidad, barra de publicar, botón de SER,
  fila de Yo…), **no se dibuja**. Si igual llega un 404 `FUNCIONALIDAD_NO_DISPONIBLE`, relee `/mias` y vuelve atrás sin
  diálogo. Sin datos, todo disponible (el servidor igual bloquea).
- **Consola → detalle → «Despliegue»**: porcentaje actual, escalones 5 / 25 / 50 / 100 («Prender para todos»), bajar un
  escalón, probadores (buscar con `GET /soporte-ti/personas`, sumar, quitar) y el gráfico de 24 h dentro / fuera (skill
  `dataviz`). Lo crítico pide la contraseña como en la fase 1. La lista muestra «En despliegue · 25 %».
- **Consola → «Roles»**: lista, crear / editar (nombre, descripción, base, árbol de funcionalidades de esa base para
  marcar), desactivar, reemplazar en bloque, borrar (con personas: 409 → ofrece reemplazar).
- **Consola → «Ver como»** (B3): elegir un rol (los seis menos Soporte TI y los personalizados activos), guardar aparte
  la sesión de Soporte TI, entrar con el token nuevo y mostrar arriba una franja fija «Viendo como Mentor · Salir»
  (`DELETE /soporte-ti/ver-como` y restaurar la sesión). Un 403 `MODO_SOLO_LECTURA` se muestra en línea, sin diálogo.
- **Administración**: Equipo y roles y Solicitudes ofrecen los roles de `GET /admin/roles-asignables`; donde se muestra
  el rol de alguien, `rolPersonalizado.nombre` si viene.
- **Auditoría de la consola**: resumen de despliegue («Despliegue 5 % → 25 %», «Probadores 3 → 4»), de roles («Rol
  “Mentor junior” creado») y de ver como («Vio la app como Mentor»).

## 7. Pruebas obligatorias (aceptación de la fase 2)

- Cubeta estable (1000 × 10), subir el porcentaje no saca a nadie, probadora dentro con 0 %, madre en despliegue esconde
  a sus hijas, `noDisponibles` en `/mias` y 404 en la API.
- Métricas: dentro / fuera, errores 5xx, lote por minuto, 24 h.
- Roles: subconjunto de la base, base `IT_SUPPORT` rechazada, nombre repetido, 20 activos, borrar con personas → 409,
  reemplazo en bloque, desactivar devuelve todo lo de la base, una funcionalidad nueva nace sin marcar, asignación con
  `rolPersonalizadoId` (y solo ADMIN para bases ADMIN/ALQUIMISTA), `rolPersonalizado` en `/auth/me`.
- Ver como: cada escritura rechazada con 403 `MODO_SOLO_LECTURA` (REST y WebSocket), salir restaura, la cuenta de
  prueba no aparece en rankings, padrones, semáforos, listas ni avisos.
- Autorización negativa en cada endpoint nuevo de la consola (ADMIN, ALCHEMIST, MENTOR, TRAINEE, suspendida → 403; sin
  desbloqueo → 423; crítico sin reautenticar → 428).
- `ArchitectureTest` (sin ciclos entre módulos) y `FuncionalidadDeclaradaTest` (todo endpoint nuevo con
  `@Funcionalidad` o `@SinInterruptor`).
- E2E local con Maestro + mobile-mcp (regla del dueño), nunca contra producción.
