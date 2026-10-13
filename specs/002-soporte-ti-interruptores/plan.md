# Plan — Fase 1: interruptores, apagado de la IA y rol Soporte TI

**Spec**: [spec.md](spec.md) · **Contrato**: [contracts/funcionalidades.md](contracts/funcionalidades.md) ·
**Fecha**: 2026-10-13 · **Alcance**: US1, US2 y US3 de la spec (P1). Despliegue progresivo, «ver como rol», roles
personalizados y datos de personas quedan para las fases 2 y 3.

## Decisiones de arquitectura

### D1. Un módulo nuevo, `plataforma`, y cero dependencias hacia él desde los demás

Los interruptores los consultan casi todos los módulos, y `plataforma` necesita leer el rol de la persona
(`users.api`). Si cada módulo importara `plataforma.api`, `users → plataforma → users` sería un ciclo que Modulith
rechaza. Por eso:

- **Lo que usan los demás vive en `shared`** (módulo `OPEN`):
  - la anotación `@Funcionalidad("chat.grupos")` y `@SinInterruptor` (`shared.web.security`), al lado de
    `@RequiresPermission`;
  - el puerto `Interruptores` (`shared.application`) para procesos y WebSockets;
  - la excepción `FuncionalidadApagadaException` (`shared.domain`).
- **`plataforma` implementa** ese puerto (inversión de dependencias, como `MedicionAcumuladaFinder`), registra el
  interceptor web y guarda estado, auditoría y consola. Depende de `shared` y `users.api`.
- `notifications` escucha los eventos de `plataforma.api` y de `rag.api` para los avisos push (ya consume casi todo).

```
shared  ◀── todos (anotación, puerto, excepción)
   ▲
plataforma ──▶ users.api           (rol del actor)
   ▲
notifications ──▶ plataforma.api   (FuncionalidadCambiadaEvent)
rag ──▶ shared.Interruptores       (circuito de la IA)
```

### D2. Dónde se hace cumplir

| Entrada | Cómo |
|---|---|
| REST (~354 endpoints) | `InterruptoresInterceptor` sobre `/api/v1/**`, **después** de `PermissionEnforcementInterceptor`: lee `@Funcionalidad` del método o de la clase y pregunta `prendidaPara(clave, actor)` |
| Tareas programadas y oyentes | `interruptores.prendida(clave)` al empezar; si está apagada, saltan o posponen según el catálogo y lo anotan en el log |
| WebSocket del chat (STOMP) | `SubscripcionAutorizadaInterceptor` rechaza `SEND`/`SUBSCRIBE` de una conversación cuya funcionalidad está apagada para el actor |
| WebSocket de voz en vivo | el handshake cierra con **1013 `no-disponible`** (lo que la app ya entiende) si `ia.voz` está apagada |
| SSE del chat de SER | antes de abrir el stream: 503; con el stream abierto, el evento de error de siempre |

Una **prueba de arquitectura** falla si un handler no declara `@Funcionalidad` (en el método o en su clase) ni
`@SinInterruptor`, y si una clave no existe en el catálogo.

### D3. Estado en Postgres, copia en memoria, aviso por Redis

- Fuente de verdad: tablas `funcionalidades_estado` y `funcionalidades_estado_rol` (ver contrato).
- Cada instancia guarda una **copia en memoria** con su `version`; la consulta es un acceso a un mapa (≤ 2 ms p95,
  SC-007).
- Al cambiar algo se publica en el canal Redis `renaser:funcionalidades` la versión nueva; cada instancia recarga.
  **Respaldo**: recarga cada 30 s aunque no llegue el aviso. Así un cambio rige en ≤ 5 s y, en el peor caso, ≤ 30 s.
- **Si Postgres o Redis fallan**, la instancia sigue con la última copia; si arranca sin poder leer, usa el valor por
  defecto del catálogo (prendida, salvo lo marcado «nace apagada»).

### D4. El catálogo es código

`plataforma.domain.model.catalogo.CatalogoDeFuncionalidades`: una entrada por clave (la tabla de la spec), con madre,
área, nombre visible, roles a los que aplica, `critica`, valor por defecto y política de procesos (`SALTA` /
`POSPONE`). Agregar una funcionalidad es una línea más un `@Funcionalidad`; nunca se crea desde la app. La base solo
guarda **desvíos** del valor por defecto.

**Evaluación** de `prendidaPara(clave, actor)`: se sube por la madre (`chat.grupos` → `chat`); en cada nivel, apagada
para todos gana; si no, manda la excepción del rol del actor; si no hay nada, el valor por defecto. Las claves
anti-bloqueo no existen en el catálogo (`@SinInterruptor`).

### D5. Rol Soporte TI sin abrir la puerta a los demás

- Valor nuevo: API `IT_SUPPORT`, base `SOPORTE_TI` (migración propia, `ADD VALUE IF NOT EXISTS`, sola: E-187).
- **Trampa a evitar:** `UserRole.can()` devuelve `true` para MENTOR, GUÍA, ADMIN y ALQUIMISTA (falla abierta, deuda
  A-1). Un permiso nuevo `OPERATE_PLATFORM` pasaría solo para todos ellos. Por eso `Permission` suma
  `exclusivoDeSoporteTi()` y el interceptor lo verifica **estricto**, antes del paso libre; y cada servicio de la
  consola repite el guard (`requireSoporteTi`), porque también se invoca desde pruebas y oyentes.
- `IT_SUPPORT` no figura en `ROLES_ASIGNABLES`, ni en aprobar, ni en invitar; `UpdateUserRole` rechaza poner o quitar
  ese rol, y Administración no puede suspender ni borrar una cuenta Soporte TI.
- Se asigna con `scripts/soporte-ti/asignar.sql` (psql con `-v correo=…`), que corre el dueño a mano: el correo no
  queda en el repo ni en una migración.
- `can()` le da todo; `actuaComoMentor()` no (no acompaña grupos); no entra a rankings ni padrones salvo que active su
  programa, como el resto del personal.
- Al sumar el valor hay que repasar la lista de D-265: enums espejo de 8 módulos, `switch` por texto (`case "MENTOR"`),
  conjuntos de staff, `RolUsuarioSqlMapper`, etiquetas de la app.

### D6. Sesión corta y contraseña para lo crítico

- `POST /soporte-ti/desbloqueo` con la contraseña abre la consola por **15 min** (a confirmar) y devuelve un token
  propio guardado en Redis con TTL; la consola responde **423 `CONSOLA_BLOQUEADA`** sin él o vencido.
- Un cambio crítico pide además un **token de reautenticación** de 5 min (la misma llamada con
  `para: "CAMBIO_CRITICO"`), de un solo uso.
- Cinco contraseñas mal en 15 min bloquean el desbloqueo 15 min (límite en Redis, como el de reseteo).

### D7. Circuito automático de la IA

- Vive en `rag` (donde están los adaptadores de Gemini). Cuenta en Redis las fallas de proveedor
  (`ProveedorIaNoDisponibleException`: 429, 408, 5xx, timeouts) por parte: `ia.chat` (chat y embeddings) e `ia.voz`
  (TTS y Gemini Live).
- **Números por defecto (a confirmar):** abre con 5 fallas en 2 min; espera 2, después 5, después 15 min; al vencer deja
  pasar un pedido de prueba; si sale bien cierra; push a Soporte TI como máximo 4 por hora.
- El estado automático se guarda aparte del manual; la evaluación combina: **manual apagada gana siempre**; si el
  manual está prendido y el circuito abierto, apagada con el aviso automático («SER está en pausa por un problema con
  el proveedor. Vuelve en unos minutos.»).
- Cada apertura y cierre publica `CircuitoDeIaCambiadoEvent` (`rag.api`) y queda en la auditoría como `AUTOMATICO`.

### D8. App

- `features/plataforma/`: lee `GET /funcionalidades/mias` al entrar, al volver al frente y cada 60 s con `ETag`
  (304 si nada cambió), guarda la última respuesta por cuenta (try/catch) y expone `useFuncionalidad(clave)`.
- `AvisoDeMantenimiento`: componente común (ícono `wrench` de Lucide, el aviso editable, «Reintentar»).
- `apiFetch` reconoce `codigo: "FUNCIONALIDAD_APAGADA"` en un 503 y pide releer el estado.
- **Fase 1 tapa en la interfaz** lo crítico: SER (botón flotante, orbe, panel, chat del curso), chat, muro, Training,
  eventos, cursos y ranking. El resto ya queda protegido por el servidor (503 con el aviso como mensaje) y se tapa en la
  interfaz de a poco.
- `features/soporte-ti/`: consola en Yo, visible solo con `IT_SUPPORT`: desbloqueo, lista por área con buscador, detalle
  con interruptor general y por rol, aviso, motivo, confirmación con contraseña para lo crítico, auditoría y estado del
  circuito de la IA. Es un cambio en el tab `Yo` aprobado por el dueño en esta spec: va su entrada en `AGENTS.md`.

## Reparto con agentes (fase 1)

Cada agente en su worktree y su rama, contra este contrato; un commit por funcionalidad; `clean verify` / `tsc` + `jest`
en verde; nunca contra producción.

| Agente | Rama | Entrega | Depende de |
|---|---|---|---|
| **A — núcleo** (backend) | `claude/plataforma-nucleo` | `shared` (anotación, puerto, excepción, 503), módulo `plataforma` (catálogo, estado, copia en memoria, Redis, interceptor, auditoría, eventos), rol `IT_SUPPORT` y su guard estricto, consola REST, `@Funcionalidad` en los ~120 controladores, prueba de arquitectura, procesos que saltan o posponen | — |
| **B — IA** (backend) | `claude/plataforma-ia` | circuito en `rag`, interruptores `ia.*` en chat, SSE, voz (204/1013) y propuestas, `CircuitoDeIaCambiadoEvent`, push en `notifications` | el puerto de A (se arranca con el contrato y se integra al final) |
| **C — app** | `claude/plataforma-app` | `features/plataforma` (estado, aviso, `apiFetch`), tapar lo crítico, `features/soporte-ti` (consola) | el contrato (con datos de ejemplo hasta que A esté) |
| **D — QA** | — | E2E local en el emulador con la base clonada: apagar/prender por rol, APK viejo (1.5.0), circuito con un proveedor de prueba, permisos negativos (ADMIN y ALQUIMISTA → 403 en la consola), propagación | A, B y C |

Integración: A primero (es el contrato vivo), después B y C sobre A, al final D. El integrador une a master con mensaje
tipo PR y pregunta antes del push.
