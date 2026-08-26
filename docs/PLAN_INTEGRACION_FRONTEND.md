# Plan de integración frontend ↔ backend Java

**Fecha:** 2026-08-25
**Insumo:** auditoría de gaps frontend/backend (`RenaserPlayStoreCopy` ↔ `com.renaser.os`).
**Estado de esta verificación:** el documento de gaps fue **verificado contra el código real**, no aceptado a ciegas. Todo lo que se comprobó coincide. Además aparecieron **dos hallazgos nuevos** que ese documento no cubría (§0.5 y §0.6 acá).

> **Regla que gobierna todo este plan:** el backend tiene **1014 tests en verde** y 13 módulos verificados endpoint por endpoint contra la app real. El frontend es el lado **no verificado**. Ante cada desalineación, la pregunta no es "¿quién tiene razón?" sino **"¿cuál de los dos lados tiene una prueba que lo respalde?"** — y hoy, casi siempre, es el backend.

---

## 0. Verificación del documento de gaps

| Afirmación auditada | Resultado |
|---|---|
| §0.1 — No hay CORS configurado | ✅ **Confirmado.** Cero ocurrencias de `cors`/`Cors`/`@CrossOrigin` en todo `src/main/java` |
| §0.2 — El backend espera `X-Actor-Id` | ✅ **Confirmado.** 49 archivos lo usan, sobre 45 `@RestController` |
| §3.5 — Chat: falta el segmento `/chat/` | ✅ **Confirmado.** Real: `/api/v1/chat/conversations` y `/api/v1/chat/conversations/{id}/messages` |
| §3.1 — Ranking exige `{tipo}` en el path | ✅ **Confirmado.** Real: `@RequestMapping("/api/v1/ranking")` + `@GetMapping("/{tipo}")` |
| §2 — `clase-diaria` no acepta POST | ✅ **Confirmado.** Solo `@GetMapping` |

**Conclusión: el documento es fiable.** Se puede planificar sobre él sin re-auditar cada línea.

### 0.5 HALLAZGO NUEVO (bloqueante duro) — frontend y backend apuntan a proyectos de Supabase DISTINTOS

- Frontend (`.env`): `EXPO_PUBLIC_SUPABASE_URL=https://qchpxyaiipghayyfmthg.supabase.co`
- Backend (`application.yaml`, configurado hoy): JWKS de `https://apvnaigldsjqeloiolcu.supabase.co`

**Ambos proyectos existen y responden** (verificado: los dos endpoints JWKS devuelven 200). Pero son bases de usuarios distintas: un JWT emitido por `qchpxyaiipghayyfmthg` **jamás** va a validar contra las claves de `apvnaigldsjqeloiolcu`.

Esto invalida el trabajo de auth apenas se active la validación real de JWT, y además explica por qué el puente temporal de `X-Actor-Id` "funciona": manda un UUID sin verificar nada, así que la discrepancia de proyecto no se nota todavía.

**Requiere una decisión del dueño del proyecto antes de tocar auth:** ¿cuál de los dos proyectos es el bueno? El que tiene los usuarios reales manda; el otro se descarta y se corrige la config del lado que corresponda.

### 0.6 HALLAZGO NUEVO — el `X-Actor-Id` actual es un agujero de seguridad, no solo un placeholder

El documento lo describe como "mecanismo temporal". Es más grave que eso, y conviene decirlo con todas las letras: hoy **cualquiera que sepa el UUID de otro usuario puede actuar como ese usuario** — basta mandar su id en el header. No hay firma, no hay verificación, no hay nada. `SecurityConfig` hace `anyRequest().permitAll()`.

Mientras esto sea así, **el backend no puede exponerse a internet bajo ninguna circunstancia.** Sirve para desarrollo local contra datos de prueba, nada más. Esto no es un gap de integración: es la razón por la que §1 del plan es lo primero.

---

## 1. La secuencia — por qué este orden y no otro

El error caro sería empezar por lo visible (arreglar rutas del frontend, que son ~40 correcciones mecánicas) antes que por lo transversal. Si se hace en ese orden, se corrigen 40 rutas que igual no funcionan (por CORS), y cuando después se active el JWT real hay que volver a tocar **todos** los servicios del frontend otra vez para sacar el `X-Actor-Id`. Dos pasadas donde alcanza una.

```
FASE 1  Desbloqueo transversal   →  sin esto, nada del resto se puede probar de verdad
FASE 2  Alineación de contratos  →  rutas/métodos/campos, mecánico y de bajo riesgo
FASE 3  Huecos reales de backend →  código nuevo, con las reglas de siempre
FASE 4  Migración de bypasses    →  lo que hoy va directo a Supabase pasa por la API
```

---

## FASE 1 — Desbloqueo transversal (nada funciona sin esto)

### 1.1 CORS `[backend, chico, sin riesgo]`

Un `CorsConfigurationSource` en `SecurityConfig`, con orígenes por configuración (nunca `*` junto con credenciales). Es el cambio más barato del plan y desbloquea el 100% del target web.

### 1.2 Decidir el proyecto de Supabase `[decisión del dueño, bloqueante]`

Ver §0.5. **Nadie puede avanzar con auth real hasta que esto se responda.** No es una decisión técnica: es saber dónde viven los usuarios de verdad.

### 1.3 Auth real: JWT de Supabase reemplaza a `X-Actor-Id` `[backend, grande, riesgo alto]`

**El cambio más delicado de todo el plan** — toca los 45 controllers. La estrategia para no romper nada:

1. Activar el filtro de Resource Server (la config del JWKS ya está puesta y verificada contra el proyecto real).
2. **No borrar `X-Actor-Id` de una.** Convivencia temporal: si hay JWT válido, el `sub` del token manda y el header se ignora; si no hay JWT, se acepta el header **solo bajo un perfil de desarrollo** (`@Profile("local")` o un flag de config), nunca en producción.
3. Migrar el frontend a mandar solo `Authorization`.
4. Recién ahí, retirar el header y el flag.

Los pasos 2 y 4 son lo que evita el big-bang. El paso 4 no se hace hasta que el 3 esté verificado.

**Regla dura:** cada controller migrado necesita su test de que un token ausente/inválido da 401 y que el `sub` del token es efectivamente el actor usado — no alcanza con que compile.

---

## FASE 2 — Alineación de contratos (mecánico, bajo riesgo)

Todo esto se corrige **del lado del frontend**, porque el backend es el lado con pruebas. Salvo las dos excepciones anotadas.

| Área | Corrección | Lado |
|---|---|---|
| Chat (§3.5) | Agregar `/chat/` al prefijo; `{userId}`→`{otherUserId}`; `PUT`→`POST` en marcar-leído | frontend |
| Ranking (§3.1) | Usar `/ranking/{tipo}`; adaptar a la respuesta de lista plana | frontend |
| Cohortes/células (§3.3) | `POST`→`PATCH` (update, status, cells update); `POST`→`PUT` (mentor); usar `/admin/cells?cohortId=` | frontend |
| Calendar portada (§3.4) | Migrar al flujo de dos pasos `upload-url` → `confirm` | frontend |
| Habits phone-free (§4) | Quitar el `{trackId}` del path — el backend resuelve la racha activa | frontend |
| Evidence admin (§6) | `/admin/evidence-review/{id}/override` → `/admin/evidence/{id}/review` o `/void` | frontend |
| Onboarding V90 (§7) | Id en el path, segmento `v90-recordings` | frontend |
| **Notifications `type` (§11)** | **Backend expone el enum en español; el frontend espera inglés.** Traducir en el frontend, NO renombrar el enum del backend (la BD tiene los valores en español y la BD está congelada) | frontend |
| **Phase contracts (§9)** | **El backend calcula la ruta de la firma; el frontend sube a otra ruta.** El backend tiene razón (es determinístico y ya probado): el frontend debe usar `upload-url`→subir→`POST /` | frontend |

**Excepción — esto se corrige en el backend:** `POST /api/v1/classroom/clase-diaria` (§2). El frontend necesita marcar la clase como completada y el backend solo tiene `GET`. Acá el hueco es real del lado del servidor, no un error de ruta del cliente.

---

## FASE 3 — Huecos reales del backend (código nuevo)

Ordenados por costo/beneficio. Cada uno sigue las reglas de siempre: comando self-validating, controller tonto, verificación de actor activo, test de 403, y `./mvnw clean test` en verde.

### 3.1 Barato y desbloquea pantallas existentes
- `GET /api/v1/account-requests` (listado admin) — hoy la pantalla admin de solicitudes **no tiene de dónde leer**.
- `GET /api/v1/evidence` (listado) — hoy el frontend cae a listar desde Storage por esto.
- `POST /api/v1/classroom/clase-diaria` (completar).
- `traineeProfile` dentro de `UserResponse` + su `PATCH` — sin esto no hay perfil de aprendiz editable.

### 3.2 Decisión de negocio pendiente (no codificar a ciegas)
- **Asignar rol al aprobar una solicitud** (§1.2): la pantalla admin deja elegir rol, el backend lo ignora. Hay que decidir si el rol se elige al aprobar o si toda alta pública es siempre `APRENDIZ`. **No inventar la regla** (CLAUDE.MD §0.6).
- **`phone` en el dominio `User`** (§1.2): el frontend lo edita, el dominio no lo tiene. Decidir si se suma al dominio o se deja fuera.

### 3.3 Módulos/funciones no construidas (trabajo grande, planificable aparte)
- Admin de hábitos completo (`habitsAdmin.ts` — **ningún** controller existe).
- Preferencias de horario, día semanal, renombrado, desbloqueos de hábitos.
- Diario / journal (`GET`/`PUT /api/v1/journal/today`) — no existe.
- Shadow Mirror (§7) — no existe; es parte del módulo `rag` (Ola 5, no construido).
- Asignación de aprendices a células (§3.3) — no existe.
- Conversación global / miembros de chat (§3.5).

### 3.4 Bloqueado por credenciales, no por código
Las dos validaciones por IA (§0.4) están completas salvo el adaptador real de Gemini. El camino de fallback a revisión manual **ya funciona y está probado**. Es una decisión de negocio si se lanza con revisión manual mientras tanto.

---

## FASE 4 — Retirar los bypasses a Supabase

Cada bypass (§0.3) es una pantalla que hoy escribe directo a la BD, salteándose las reglas de negocio del backend: validaciones, eventos de dominio, verificación de actor. Migrarlos **uno por uno, verificando después de cada uno**, nunca en bloque.

Prioridad por riesgo (qué tan grave es que se salte las reglas):

1. **`onboarding`** — el más crítico: se saltea toda la máquina de estados y los gates de progreso.
2. **`cursos`** (progreso de lección) — se saltea los gates de día/rol que el backend calcula.
3. **`rocks`/`evidencias`** (evidencia a Storage) — se saltea el registro en la tabla `evidencias` y su cola de validación.
4. **`radar`, `users`, `testimonios`** — menor riesgo, son escrituras más planas.
5. **Chat Realtime → WebSocket/STOMP** — el backend ya tiene la infraestructura lista (§3.5); es cambio de cliente, no de servidor.

---

## 2. Estado de la base de datos: ¿usamos las 90 tablas?

**No, y está bien: 75 de 90 en uso (83%).** Las 15 restantes no son un olvido — se agrupan en tres causas claras, todas ya decididas o documentadas:

### RAG / Renasia — módulo no construido (Ola 5, pospuesto a pedido del dueño) — 6 tablas
`base_conocimiento`, `conversaciones_renasia`, `mensajes_renasia`, `fuentes_mensaje_renasia`, `informes_espejo_sombra`, `preguntas_confrontacion`

Coincide exactamente con el gap §7 del frontend (Shadow Mirror sin backend). **No es deuda: es alcance no empezado.**

### RBAC superado por decisión D-21/D-40 — 2 tablas
`rol_permiso`, `auditoria_cambios_rol`

La decisión ya está tomada: los permisos se resuelven con el enum `UserRole` en código, no con junction tables. Estas quedan como remanente del diseño anterior.

### Funciones de `habits` no construidas — 7 tablas
`categorias_habito`, `iconos_habito`, `audioterapias`, `dias_semanales_habito`, `historial_cambios_horario`, `revisiones_semanales_sin_celular`, `mensajes_bienvenida`

**Este es el dato más valioso del análisis:** estas tablas se corresponden **casi uno a uno** con los gaps de §4 del documento de frontend (`weekly-habit-days`, `habit-preferences`, admin de hábitos). No son tablas huérfanas — son **la evidencia de que esas funciones del frontend nunca tuvieron backend**. Las dos auditorías, hechas por caminos independientes, llegaron a la misma conclusión.

**Implicación práctica:** la BD de 90 tablas no está sobredimensionada. Está dimensionada para el producto completo; el backend cubre hoy el 83%, y el 17% restante es exactamente el trabajo de la Fase 3.3 más el módulo `rag`.

---

## 3. Mejoras que conviene hacer aprovechando el momento

Ninguna es urgente, pero todas son más baratas ahora que después:

1. **Contrato de API versionado y verificable.** Hoy la única forma de saber si frontend y backend coinciden fue esta auditoría manual. Con `springdoc-openapi` (ya previsto en la arquitectura) se puede generar el spec y que el frontend derive tipos de ahí — el desalineamiento se vuelve un error de compilación en vez de un 404 en runtime.
2. **Elegir un idioma por frontera y no mezclarlo.** Hoy conviven DTOs en inglés (`chat`: `otherUserId`) y en español (`evidence`: `tipo`, `contenidoTexto`). Ambas decisiones son defendibles; la mezcla no. Esto ya causó fricción real durante las pruebas.
3. **Tests de contrato en CI.** Un snapshot del OpenAPI que falle el build cuando una ruta cambia sin querer. Es la red que hubiera evitado el 80% de los gaps de la Fase 2.
4. **Retirar el fallback a Supabase de `tickets.ts`** (§10): hoy, si el REST devuelve vacío, lee la tabla directo — puede mostrar datos que el backend no ve. Es un fallback silencioso, el peor tipo.
5. **Rate limiting real** antes de exponer a internet — hoy no hay ninguno en los endpoints públicos (`POST /account-requests` es el más obvio).

---

## 4. Lo que NO hay que hacer

- **No renombrar el enum `TipoNotificacion` al inglés.** La BD está congelada y tiene esos valores en español. Se traduce en el cliente.
- **No cambiar la ruta de firma de `phase-contracts` en el backend.** Es determinística y está probada; el frontend se adapta.
- **No borrar `X-Actor-Id` antes de que el frontend mande JWT.** Rompería el desarrollo local de todos.
- **No tocar la BD.** Sigue vigente D-40: ni tablas nuevas, ni ALTER, ni seeds desde código.
- **No migrar los bypasses en bloque.** Uno por uno, verificando cada uno.
