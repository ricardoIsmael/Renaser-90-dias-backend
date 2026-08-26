# Plan de cierre — qué falta para tener el producto completo

**Fecha:** 2026-08-25
**Consolida:** el módulo `rag` (ver [`MODULO_RAG.md`](MODULO_RAG.md)) + los gaps frontend↔backend (ver [`PLAN_INTEGRACION_FRONTEND.md`](PLAN_INTEGRACION_FRONTEND.md)).

---

## Dónde estamos hoy

| | Estado |
|---|---|
| Módulos construidos | **13 de 14** (`users`, `points`, `phasecontracts`, `support`, `habits`, `rocks`, `notifications`, `academy`, `community`, `calendar`, `chat`, `evidence`, `onboarding`) |
| Tests | **~1026**, verificados endpoint por endpoint contra la app real |
| Tablas de BD en uso | **75 de 90** (83%) — las 15 restantes se explican abajo |
| Módulo faltante | `rag`/`renasia` — **diseñado, no construido** |
| Frontend conectado | **No** — bloqueado por CORS y auth |

**Lo que NO falta:** la arquitectura, la BD, los 13 módulos de dominio, la suite de pruebas. Eso está y está verificado.

---

## Las 4 cosas que faltan, en orden

### 1. Desbloquear el frontend `[lo más urgente]`

Hoy el frontend **no puede hablar con el backend en absoluto**. Dos bloqueos, ninguno grande:

- **CORS** — no está configurado. Un navegador rechaza toda llamada antes de que la ruta importe. Es el cambio más barato del plan entero.
- **Auth** — el backend espera `X-Actor-Id`, el frontend manda `Authorization: Bearer`. **En pausa por decisión tuya** hasta definir el proyecto de Supabase correcto (el que configuramos no tiene tus usuarios ni tu BD).

> ⚠️ **Mientras `X-Actor-Id` siga vigente, este backend no puede exponerse a internet.** Cualquiera que sepa el UUID de otro usuario puede actuar como él. Sirve para desarrollo local, nada más.

### 2. Construir `rag`/`renasia` `[el último módulo]`

Diseño cerrado, con tus 4 respuestas incorporadas y la API verificada contra los JARs reales. Detalle completo en [`MODULO_RAG.md`](MODULO_RAG.md).

Se construye completo **incluso sin credenciales de Gemini** — igual que `evidence` y `onboarding`: persistencia, permisos, búsqueda vectorial, cuota y endpoints funcionan y se prueban; solo el adaptador de IA queda `NoOp` hasta que llegue la API key.

Dependencia previa: agregar `EntradaDiarioFinder` a `habits/api/` (D-50), porque el Espejo Sombra lee entradas de diario y esa tabla es de otro módulo.

### 3. Alinear los contratos con el frontend `[mecánico, ~40 correcciones]`

Casi todo se corrige **del lado del frontend**, porque el backend es el lado con pruebas. Rutas, métodos HTTP y nombres de campo. Ver el detalle en [`PLAN_INTEGRACION_FRONTEND.md`](PLAN_INTEGRACION_FRONTEND.md) §Fase 2.

Excepciones que se corrigen en el backend: `POST /classroom/clase-diaria` (existe solo como GET) y los listados que faltan (`GET /account-requests`, `GET /evidence`).

### 4. Funciones sin backend `[trabajo grande, planificable aparte]`

Estas nunca tuvieron servidor, y la BD lo confirma: **las tablas sin usar se corresponden casi uno a uno con los gaps del frontend.**

| Función | Tablas sin usar que lo evidencian |
|---|---|
| Admin de hábitos, preferencias de horario, día semanal | `categorias_habito`, `iconos_habito`, `dias_semanales_habito`, `historial_cambios_horario` |
| Audioterapias del Santuario | `audioterapias` |
| Revisión semanal sin celular | `revisiones_semanales_sin_celular` |
| Mensaje de bienvenida en chat | `mensajes_bienvenida` |
| Diario / journal, asignación de aprendices a células | (sin tabla dedicada, no construidas) |

Las 6 restantes son de `rag` (se resuelven en el punto 2) y 2 son remanentes de RBAC ya superado por decisión D-21/D-40.

**Conclusión sobre la BD: no está sobredimensionada.** Está dimensionada para el producto completo; el backend cubre el 83%, y el 17% restante es exactamente este punto 4 más el módulo `rag`.

---

## Riesgos abiertos, por si se pierden de vista

| Riesgo | Estado |
|---|---|
| `X-Actor-Id` sin validación real | **Abierto** — bloquea salir a producción |
| Proyecto de Supabase sin definir | **Abierto** — bloquea auth real |
| Credenciales de Gemini | **Abierto** — `evidence`, `onboarding` y `rag` quedan con fallback a revisión manual |
| Credenciales de AWS S3 | Config lista (bucket `s3-renaser90dias`), falta el adaptador real — hoy `NoOpAlmacenamientoAdapter` no sube nada |
| Sin rate limiting en endpoints públicos | **Abierto** — `POST /account-requests` es el más expuesto |

---

## Orden sugerido

```
1. CORS                          → desbloquea todo el frontend web      (chico)
2. Módulo rag                    → cierra los 14 módulos                (grande)
3. Alineación de contratos       → el frontend empieza a funcionar      (mecánico)
4. [decisión Supabase] → Auth    → habilita pensar en producción        (grande)
5. Adaptador real de S3          → cuando estén las credenciales        (mediano)
6. Funciones sin backend         → por prioridad de producto            (grande)
```

Los pasos 1-3 se pueden hacer ya, sin esperar ninguna decisión ni credencial. El 4 espera tu definición del proyecto de Supabase; el 5, las credenciales de AWS.
