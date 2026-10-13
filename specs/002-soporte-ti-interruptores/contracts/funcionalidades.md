# Contrato — Interruptores y consola Soporte TI (fase 1)

Lo comparten los agentes A (núcleo), B (IA), C (app) y D (QA). Todo cambio a este archivo se avisa a los cuatro.
Nombres en código: inglés en la API y en los enums de dominio, castellano en la base (como el resto del sistema).

## 1. Piezas en `shared` (las usan todos los módulos)

```java
// shared.web.security
@Target({METHOD, TYPE}) @Retention(RUNTIME)
public @interface Funcionalidad { String value(); }          // "chat.grupos"

@Target({METHOD, TYPE}) @Retention(RUNTIME)
public @interface SinInterruptor { String motivo(); }        // anti-bloqueo: login, sesión, consola, health…

// shared.application
public interface Interruptores {
    /** Estado global (procesos sin actor: tareas programadas, oyentes). */
    boolean prendida(String clave);
    /** Estado para una persona (su rol). */
    boolean prendidaPara(String clave, UserId actor);
    /** Lanza FuncionalidadApagadaException con el aviso si está apagada para el actor. */
    void exigirPrendidaPara(String clave, UserId actor);
}

// shared.domain
public final class FuncionalidadApagadaException extends RuntimeException {
    public FuncionalidadApagadaException(String clave, String aviso) { … }
    public String clave() { … }   // getMessage() = aviso
}
```

Sin `plataforma` en el classpath (pruebas de un módulo suelto) hay una implementación `TodoPrendido` en `shared` con
`@ConditionalOnMissingBean`.

## 2. Error HTTP

`GlobalExceptionHandler` traduce `FuncionalidadApagadaException` a **503** con el formato de siempre más dos campos
aditivos:

```json
{
  "message": "El chat está en mantenimiento. Volvemos a las 18:00.",
  "timestamp": "2026-10-13T22:00:00Z",
  "codigo": "FUNCIONALIDAD_APAGADA",
  "funcionalidad": "chat.grupos"
}
```

- Sin aviso escrito: `message` = «Esta función está en mantenimiento. Vuelve a intentarlo en un rato.»
- Un APK viejo (1.5.0) lo muestra como un error normal: `mensajeDeError` usa `message` en todo lo que no es 401/403.
- La consola bloqueada responde **423** con `codigo: "CONSOLA_BLOQUEADA"`; reautenticación vencida o ausente en un
  cambio crítico: **428** con `codigo: "REAUTENTICACION_REQUERIDA"`.

## 3. Catálogo (código, `plataforma`)

Cada entrada: `clave`, `madre` (o nada), `area`, `nombre`, `descripcion`, `rolesQueLaUsan` (conjunto de `UserRole`),
`critica`, `porDefecto` (`PRENDIDA` | `APAGADA`), `procesos` (`SALTA` | `POSPONE` | `NO_APLICA`). Las claves y su tabla
inicial están en la spec («Catálogo inicial de funcionalidades»). Las críticas: `chat`, `muro`, `ia`, `habitos`,
`registro`, `notificaciones`, `admin`.

## 4. Base de datos (migraciones nuevas, cada una justificada en su cabecera)

```sql
-- V100: valor nuevo del enum de roles, solo (E-187)
ALTER TYPE renaser.rol_usuario ADD VALUE IF NOT EXISTS 'SOPORTE_TI';

-- V101: estado y auditoría
CREATE TABLE renaser.funcionalidades_estado (
    clave            text PRIMARY KEY,                 -- existe en el catálogo (lo verifica la app al arrancar)
    prendida_global  boolean NOT NULL,
    aviso            text NULL CHECK (char_length(aviso) <= 200),
    version          bigint  NOT NULL,
    actualizado_por  uuid    NOT NULL REFERENCES renaser.usuarios(id),
    actualizado_en   timestamptz NOT NULL
);
CREATE TABLE renaser.funcionalidades_estado_rol (
    clave     text NOT NULL REFERENCES renaser.funcionalidades_estado(clave) ON DELETE CASCADE,
    rol       renaser.rol_usuario NOT NULL,
    prendida  boolean NOT NULL,
    PRIMARY KEY (clave, rol)
);
CREATE TABLE renaser.cambios_auditados (
    id           uuid PRIMARY KEY,
    tipo         text NOT NULL CHECK (tipo IN ('MANUAL','AUTOMATICO','DESBLOQUEO','REAUTENTICACION')),
    actor_id     uuid NULL REFERENCES renaser.usuarios(id),   -- NULL solo en AUTOMATICO
    clave        text NULL,
    antes        jsonb NULL,
    despues      jsonb NULL,
    motivo       text NULL,
    dispositivo  text NULL,
    creado_en    timestamptz NOT NULL
);
-- Solo altas: un trigger rechaza UPDATE y DELETE sobre cambios_auditados.
```

La base guarda solo **desvíos**: sin fila, rige `porDefecto` del catálogo.

## 5. Evaluación

```
prendidaPara(clave, actor):
  para cada nivel desde la raíz hasta la clave (chat → chat.grupos):
      si circuitoAbierto(nivel) y no hay apagado manual  → apagada (aviso automático)      [solo claves ia.*]
      si fila(nivel).prendida_global == false            → apagada (aviso de esa fila)
      si filaRol(nivel, rolDe(actor)).prendida == false  → apagada (aviso de esa fila)
  prendida
```

`rolDe(actor)` sale de `users.api.UserSummaryFinder`; una cuenta sin rol conocido se evalúa como `TRAINEE`.

## 6. REST

### Para toda sesión (`@SinInterruptor`, `@RequiresPermission(USE_APP)`)

`GET /api/v1/funcionalidades/mias` → `200` con `ETag: "v<version>"`; `If-None-Match` igual → `304`.

```json
{
  "version": 42,
  "apagadas": [
    { "clave": "chat.audio", "aviso": "Las notas de voz vuelven mañana." },
    { "clave": "ia.voz", "aviso": "SER está en pausa por un problema con el proveedor. Vuelve en unos minutos." }
  ]
}
```

Lo que no figura en `apagadas` está prendido para esa sesión. Las hijas de una madre apagada figuran todas, para que la
app no tenga que conocer el árbol.

### Consola (solo `IT_SUPPORT`; permiso `OPERATE_PLATFORM`, estricto; consola desbloqueada)

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| `POST /api/v1/soporte-ti/desbloqueo` | `{ "contrasena": "…", "para": "CONSOLA" \| "CAMBIO_CRITICO" }` | `200 { "token": "…", "venceEn": "…" }` · `401` contraseña mal · `429` cinco intentos en 15 min |
| `GET /api/v1/soporte-ti/funcionalidades` | — | `200 [ { clave, madre, area, nombre, descripcion, rolesQueLaUsan, critica, porDefecto, prendidaGlobal, porRol: {ROL: bool}, aviso, version, actualizadoPor: {id, nombre}, actualizadoEn, circuito?: {estado, proximaPrueba} } ]` |
| `PUT /api/v1/soporte-ti/funcionalidades/{clave}` | `{ "prendidaGlobal": true, "porRol": {"TRAINEE": false}, "aviso": "…", "motivo": "…", "version": 41 }` | `200` el estado nuevo · `409` versión vieja · `428` crítico sin reautenticación · `400` sin motivo o clave anti-bloqueo |
| `GET /api/v1/soporte-ti/auditoria?clave=&actor=&desde=&hasta=&page=&size=` | — | `200 { contenido: [...], pagina, total }` |
| `GET /api/v1/soporte-ti/ia/circuito` | — | `200 [ { parte: "ia.chat" \| "ia.voz", estado: "CERRADO" \| "ABIERTO" \| "PROBANDO", fallasEnVentana, abiertoDesde?, proximaPrueba? } ]` |

Cabeceras: `X-Consola-Token` (desbloqueo `CONSOLA`) en todas; `X-Reautenticacion` (desbloqueo `CAMBIO_CRITICO`, un solo
uso) en el `PUT` crítico. Ambos tokens viven en Redis con TTL (15 min y 5 min, a confirmar), atados a la sesión.

## 7. Eventos (outbox)

```java
// plataforma.api
record FuncionalidadCambiadaEvent(String clave, boolean critica, boolean apagadaParaTodos, UserId actor,
                                  String motivo, Instant occurredAt) implements DomainEvent {}
// rag.api
record CircuitoDeIaCambiadoEvent(String parte, String estado, String causa, Instant occurredAt) implements DomainEvent {}
```

`notifications` los escucha (idempotente por `occurredAt` + clave): push a `IT_SUPPORT` siempre; a `ADMIN` y
`ALCHEMIST` si `critica` o `apagadaParaTodos`. Tipo de notificación nuevo: `OPERACION_PLATAFORMA`.

## 8. App

```ts
// features/plataforma/api/funcionalidadesApi.ts
const esquema = z.object({
  version: z.number(),
  apagadas: z.array(z.object({ clave: z.string(), aviso: z.string().nullish() }).passthrough()),
}).passthrough();

// features/plataforma/estado/useFuncionalidad.ts
useFuncionalidad('chat.grupos') → { prendida: boolean; aviso: string | null }
```

- Relee al entrar, al volver al frente, cada 60 s y cuando `apiFetch` ve `codigo: "FUNCIONALIDAD_APAGADA"`.
- Sin datos todavía o sin red: usa lo último guardado de esa cuenta; si no hay nada, todo prendido (el servidor igual
  bloquea).
- `AvisoDeMantenimiento({ aviso, alReintentar })`: ícono `wrench` (Lucide, caja de 24), texto de 16, «Reintentar»; con
  lector de pantalla se anuncia el aviso.
- Roles: `IT_SUPPORT` / `SOPORTE_TI` → «Soporte TI» en `etiquetaDeRol`; **no** entra en `ROLES_ASIGNABLES`.

## 9. Pruebas obligatorias (aceptación de la fase 1)

- Arquitectura: todo handler con `@Funcionalidad` (método o clase) o `@SinInterruptor`; toda clave anotada existe.
- Autorización negativa en cada endpoint de la consola: `ADMIN`, `ALCHEMIST`, `MENTOR`, `TRAINEE` y una cuenta
  suspendida → 403; sin desbloqueo → 423; crítico sin reautenticar → 428.
- Ningún endpoint asigna `IT_SUPPORT` (`PATCH /users/{id}/role`, aprobar, invitar, `PUT /admin/staff/{id}`) → 400/403.
- Apagar `chat.grupos` para `TRAINEE`: aprendiz 503 con el aviso, mentor 200; prender: 200 en ≤ 5 s.
- Propagación: dos copias en memoria (dos contextos o dos instancias del servicio) se ponen al día por el canal Redis.
- Auditoría: cada `PUT`, desbloqueo y apertura/cierre del circuito deja su fila; `UPDATE`/`DELETE` fallan.
- IA: con un proveedor de prueba que falla, el circuito abre a las 5 fallas, la app recibe 503 / 1013, vuelve tras la
  prueba buena; un apagado manual no lo cierra el circuito.
- E2E: suite completa de Maestro + mobile-mcp en el emulador, local (regla del dueño).
