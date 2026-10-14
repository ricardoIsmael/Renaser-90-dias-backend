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

## 10. Cómo quedó en el backend (agente A, 2026-10-13; D-267)

Lo implementado sigue este contrato con estos desvíos mínimos y agregados, todos aditivos para la app:

- **ETag** de `GET /funcionalidades/mias`: `"v<version>-<huella>"` (no `"v<version>"` a secas). La respuesta depende del
  rol de quien pide y del circuito de la IA, y ninguno de los dos cambia la versión manual: con la versión sola, un cambio
  de rol o un circuito que abre devolverían 304 con un estado viejo. Para la app es opaco.
- **`version`** de `/mias` = versión manual (secuencia global de `funcionalidades_estado`) + `version()` del estado
  automático (`EstadoAutomaticoDeFuncionalidades`, que implementa el agente B): cambia también cuando el circuito abre o
  cierra. Las claves `ia.*` apagadas por el circuito figuran en `apagadas` con el aviso automático.
- **Errores**: además de 503/423/428, el desbloqueo con la contraseña mal responde **401** con
  `codigo: "CONTRASENA_INCORRECTA"` (la app distingue así una sesión vencida).
- **§4 sin FK** a `usuarios` en `funcionalidades_estado.actualizado_por` ni en `cambios_auditados.actor_id`: la auditoría
  no se puede borrar y una FK impediría para siempre la purga de la cuenta (D-243). Se agregó la secuencia
  `funcionalidades_version_seq` y un CHECK de que solo AUTOMATICO va sin actor; el trigger rechaza también TRUNCATE.
- **`porRol`** en el `PUT`: se aceptan los siete roles con su nombre de la API, en `true` o `false`; solo los `false` se
  guardan (la base guarda desvíos). Un rol que no existe es 400.
- **Auditoría**: `antes`/`despues` de un cambio manual traen `prendidaGlobal`, `porRol` (los siete roles), `apagadaPara` y
  `aviso`; los desbloqueos traen `despues: { resultado: "OK" | "CONTRASENA_INCORRECTA" }`.
- **Crítico** (428 sin `X-Reautenticacion`): toda funcionalidad marcada crítica, o cualquier `PUT` que la deje apagada para
  todos. El token `CAMBIO_CRITICO` se pide con `X-Consola-Token` puesto.
- **§7** `FuncionalidadCambiadaEvent` suma al final `nombre` y `resumen` («Apagada para Aprendiz»). El push de
  `OPERACION_PLATAFORMA` sale aunque `notificaciones` esté apagada.
- **Para el agente B** (`shared.application`): `EstadoAutomaticoDeFuncionalidades` (`apagadaAutomaticamente(clave)`,
  `version()`, `circuitos()`) y `AuditoriaDePlataforma.registrarCambioAutomatico(...)` para la fila AUTOMATICO.
  `CircuitoDeIaCambiadoEvent` y su push siguen siendo de B.

### 10.1 Lo del agente B (IA de SER, 2026-10-13; D-267)

Rama `claude/plataforma-ia`, sobre la de A. Sigue el contrato con estos agregados y desvíos, todos aditivos para la app:

- **Circuito** (§5, plan.md D7): por parte, `ia.chat` (chat de SER y del curso, embeddings) e `ia.voz` (texto a voz y
  Gemini Live). Abre con 5 fallas del proveedor en 2 min; espera 2, 5 y 15 min en las reaperturas seguidas (la última se
  repite); vencida la espera pasa un solo pedido de prueba. Configurable: `renaser.ia.circuito.fallas-para-abrir`,
  `ventana`, `esperas`, `plazo-de-la-prueba` (2 min: lo que se reserva a la prueba si se corta sin avisar) y
  `recarga-cada`. Estado en Redis (`renaser:ia:circuito:<parte>` y `…:fallas`), aviso entre instancias por el canal
  `renaser:ia:circuito`. Sin Redis el circuito deja pasar.
- **Qué cuenta:** falla = `ProveedorIaNoDisponibleException` (429, 408, 5xx, timeouts) del chat, de los embeddings y del
  texto a voz, y en la voz en vivo toda `ConversacionEnVivoNoDisponibleException` al abrir (desvío: el adaptador de Gemini
  Live no distingue el código). Éxito = el stream del chat completo, el audio completo, la sesión en vivo abierta. Los
  embeddings solo suman fallas: la prueba la resuelve el modelo de esa parte. Un error nuestro (un 400) no cuenta.
- **`EstadoAutomaticoDeFuncionalidades`** lo implementa `rag.application.services.circuito.EstadoAutomaticoDeLaIa`:
  `apagadaAutomaticamente("ia.chat" | "ia.voz")` con el aviso «SER está en pausa por un problema con el proveedor. Vuelve
  en unos minutos.» mientras la espera corre o hay una prueba en curso (`ia` e `ia.propuestas` no tienen circuito);
  `version()` = suma de las versiones del circuito, que suben con cada apertura, cierre y toma de la prueba (no retrocede
  si Redis se vacía); `circuitos()` con `estado` `CERRADO` / `ABIERTO` / `PROBANDO` (abierto con la espera vencida se
  muestra `PROBANDO`), `fallasEnVentana`, `abiertoDesde` y `proximaPrueba` (los dos `null` cerrado).
- **§7** `CircuitoDeIaCambiadoEvent(parte, estado, causa, occurredAt)` tal cual, solo para `ABIERTO` y `CERRADO` (tomar la
  prueba no se avisa ni se audita). La fila `AUTOMATICO` lleva `antes`/`despues` con `estado` (y, abierto,
  `abiertoDesde`, `proximaPrueba`, `aperturasSeguidas`) y la causa como `motivo`.
- **Push:** a IT_SUPPORT activa, `OPERACION_PLATAFORMA`, como máximo 4 por hora y por parte
  (`renaser.notifications.circuito-ia.maximo-por-hora`); los omitidos no ocupan lugar y quedan en el log.
- **Respuestas** (§2 y plan.md D2): chat 503 antes del stream (un turno en vuelo termina con el `error` de siempre: el
  interceptor ya no evalúa el despacho `ASYNC`, E-612); voz del orbe **204** con `ia.voz` apagada, a mano o por el
  circuito (agregado a §1: `@Funcionalidad(value = "ia.voz", laResuelveElCasoDeUso = true)`, el interceptor no corta y
  responde el caso de uso); voz en vivo: se abre el socket y se cierra con **1013 `no-disponible`**; `ia.propuestas`: el
  modelo no recibe las herramientas `proponer_*` (ni `marcar_habito_completado` cuando propone) y confirmar/cancelar dan
  el 503 del interceptor; `ia` general: no se compacta memoria y los mensajes automáticos de SER saltan.
- **Desvío en un puerto:** `SintetizarVozPort` decía «nunca lanza»; ahora puede lanzar `ProveedorIaNoDisponibleException`
  (el único que llama la cuenta y la trata como sin voz).
