# Cumplimiento de la Especificación de Requisitos (RF-01 … RF-45)

**Fecha:** 2026-08-26
**Insumo:** `docs/spec/Especificacion_Requisitos_Renaser_OS.docx` (v2.0, "Senior Baseline de Producción") — recibido del cliente, describe el sistema **sobre el stack viejo** (Next.js 15 + Prisma 6 + Supabase + Vercel Cron + Gemini 1.5).
**Contrastado contra:** el backend Java real (14 módulos, 1112 tests en verde), verificado leyendo el código, no de memoria.

---

## 0. Cómo hay que leer este documento (regla que gobierna todo lo demás)

La especificación es **ingeniería inversa del backend viejo**. Eso la vuelve dos cosas a la vez, y confundirlas sería el error caro:

- **Fuente legítima de REGLAS DE NEGOCIO.** Fases, umbrales, fórmulas, ciclos, penalizaciones. Esto es producto, no implementación, y vale oro — de hecho **responde varias preguntas que teníamos abiertas** (§2).
- **NO es fuente de arquitectura ni de implementación.** El objetivo declarado del proyecto (`CLAUDE.md`, y textual del dueño: *"no buscamos replicar ese backend mal hecho"*) es mejorar, optimizar y reutilizar bajo SOLID y clean code. Que el documento diga "Vercel Cron", "Prisma", "Supabase Realtime" o "middleware `requireRole`" describe **cómo estaba hecho**, no cómo debe quedar.

Por eso la matriz de §4 usa cuatro veredictos, no tres:

| | Significado |
|---|---|
| ✅ **Cumplido** | La regla de negocio está implementada y probada en el backend Java |
| ⚠️ **Parcial** | Existe una parte real (dominio, endpoint o flujo) pero le falta algo concreto y nombrado |
| ❌ **No construido** | No existe; es trabajo pendiente identificado |
| 🔵 **Divergencia deliberada** | Lo cumplimos, pero con otra tecnología/patrón que el documento — y esa diferencia *es* la migración, no una desviación |

---

## 1. Veredicto ejecutivo

Sobre los 45 requisitos funcionales del documento:

| Estado | Cantidad | % |
|---|---|---|
| ✅ Cumplido | **19** | 42% |
| ⚠️ Parcial | **17** | 38% |
| ❌ No construido | **9** | 20% |

**Lectura de senior, sin maquillaje:** el núcleo transaccional del producto está construido y probado — identidad, hábitos con sus ventanas de entrega, rocas, evidencias, comunidad, chat en tiempo real, calendario, tickets, RAG. Lo que falta se concentra en tres bolsas muy identificables, y ninguna es una sorpresa: **(a)** las funciones de personalización de hábitos que ya sabíamos que nunca tuvieron backend (coinciden 1:1 con las 7 tablas sin uso), **(b)** la capa de *cálculo diario automático* (coherencia → semáforo → Verdugo), que está toda diseñada pero sin quien la dispare, y **(c)** lo que depende de credenciales de Gemini (D-39), que está completo salvo el adaptador.

Las 17 "parciales" son en su mayoría **un paso de distancia**, no medio requisito: un caso de uso que existe pero nadie llama, un endpoint que existe en GET pero no en POST, un flujo entero listo detrás de un adaptador `NoOp`.

---

## 2. Lo más valioso del documento: responde preguntas que teníamos abiertas

Esto es lo que justifica haberlo leído con cuidado. Varias reglas que dejamos explícitamente sin inventar (CLAUDE.md §0.6, registradas como Q-x en los `MODULO_*.md`) **están respondidas acá con número concreto**:

| Pregunta abierta nuestra | Respuesta del documento | Acción |
|---|---|---|
| **Q-2** (`points`): ¿cuál es la fórmula de la Ley VI? | `Coherencia = (Hábitos Obligatorios Completados / Total Obligatorios) × 100`. Los opcionales y los de intoxicación **nunca** reducen el porcentaje ni la racha | Implementable ya. `RegistrarCoherenciaDiariaUseCase` existe y espera exactamente este valor |
| **Semáforo diario** (no lo teníamos ni como pregunta) | Verde ≥80%, Amarillo 60–79%, Rojo <60% | Regla nueva, hoy no existe en el código |
| **Ciclos de intoxicación** — `resolverTipoDia` los deja fuera con un comentario explícito | Días **8-10 (VER)**, **17-19 (CORTAR)**, **26-28 (RENASER)**. Todos los hábitos pasan a opcionales **salvo la publicación diaria en comunidad** | Desbloquea `TipoDia.INTOXICACION` y `Habito.obligatorioEnIntoxicacion`, que ya están modelados pero nunca se activan |
| **Disparo del Verdugo** — teníamos el registro de eventos, no la detección | Semáforo Rojo **o** 2 días seguidos críticos → `EnforcerEvent` ACTIVE | Regla concreta, implementable |
| **Cupo de cambio de horarios** | Máximo **3 por semana de programa**, y aplican desde las **00:00 del día siguiente** (para que nadie manipule el día en curso) | Justifica `historial_cambios_horario` + `cambios_horario_pendientes`, hoy sin uso |
| **Penalizaciones de puntos** | Evidencia rechazada por IA: **−5**. Override manual del admin: **+5**. Semana sin phone-free: **−10**. Roca diaria completada: **+10** | Números duros para `points` |
| **Q-1 / GAP-24** (ranking agregado) | RF-27 exige tabla de posiciones **global y por célula** | Confirma que el agregador de ranking es requisito real, no un capricho del frontend |
| **GAP-30** (reportar alucinación de RenasIA) | RF-40 lo exige como requisito formal, con `overriddenByAdmin` | Confirma que no es "frontend adelantado": falta backend de verdad |

**Conclusión de esta sección:** el documento convierte 8 incógnitas en especificación. Eso vale más que cualquier otra cosa que traiga.

---

## 3. Donde nuestro código es MÁS preciso que el documento (no "corregir" hacia el papel)

Un senior tiene que saber cuándo el documento está peor que el código. Encontré un caso claro y conviene dejarlo escrito para que nadie lo "arregle" al revés:

**Ventana de gracia de los hábitos.** El documento dice: *"hora objetivo con 10 minutos de gracia (puntuación máxima: 10 pts)"* — o sea, plano: dentro de los 10 minutos, 10 puntos.

Nuestro `ResultadoOtorgamiento` implementa una **degradación progresiva**, portada literalmente de `points.ts:100-125` del repo viejo:

```
entrega ≤ hora objetivo          → A_TIEMPO,  10 puntos
≤ +10 min                        → GRACIA,    max(5, 10 − floor(minutos/2))
≤ +10 min + extensión            → EXTENDIDO, 3 puntos fijos
más allá                         → EXPIRADO,  0 puntos
```

Es decir: llegar 6 minutos tarde da 7 puntos, no 10. **El documento simplificó al resumir; nosotros portamos el código real.** Ante la duda gana el código fuente del que se migró, no la prosa de la especificación — pero conviene que el dueño lo confirme, porque si la intención de producto *era* la versión plana, entonces el backend viejo tenía un bug y hay que decidir cuál se queda.

Mismo criterio, más chico: el documento describe la fase II como "Días 8 a 34"; nuestro `FasePrograma` arranca la fase II el día 8 ✅, pero además distingue el **día de desbloqueo de firma** (17) del día de inicio de fase (8) — una precisión que el documento no tiene y que ya está probada end-to-end.

---

## 4. Matriz RF-01 … RF-45

### Onboarding e identidad

| RF | Requisito | Estado | Dónde está / qué falta |
|---|---|---|---|
| RF-01 | Registro de postulación | ✅ | `POST /api/v1/account-requests` |
| RF-02 | Autenticación JWT y RBAC | ⚠️ | RBAC completo (5 roles, guards por relación). **JWT bloqueado por B-2**; hoy `X-Actor-Id`, inseguro y documentado como tal |
| RF-03 | Aprobación y creación de perfil | ⚠️ | El `approve` existe pero **no acepta cohorte/célula/mentor/`startDate`** — hoy no recibe body |
| RF-04 | Cambio de rol auditado | ⚠️ | `PATCH /users/{id}/role` existe; **la auditoría no se escribe** (`auditoria_cambios_rol` sin uso). La Ley del documento exige registro inmutable |
| RF-05 | Diagnóstico inicial | ✅ | Cuestionario + respuestas + hitos |
| RF-06 | Video Variable 90 | ✅ | `v90-recordings` + subida por URL firmada |
| RF-07 | Validación IA de onboarding | ⚠️ | Flujo async 202+polling completo; adaptador `NoOp` por **D-39** (faltan credenciales) |
| RF-08 | Firma de pacto y desbloqueo | ⚠️ | `POST /onboarding/complete` existe pero **sin precondiciones** — es nuestra **Q-O1**, y el documento ahora la responde: bloqueo total hasta cuestionario + V90 + pacto firmado |

### Hábitos

| RF | Requisito | Estado | Dónde está / qué falta |
|---|---|---|---|
| RF-09 | Generación diaria de hábitos (cron) | ⚠️ | Existe el scheduler de expiración; la **generación** nocturna de tracks no está cerrada |
| RF-10 | Desbloqueo escalonado por rampa | ❌ | `desbloqueos_habito` sin uso |
| RF-11 | Personalización de horarios (cupo 3/semana) | ⚠️ | `PreferenciaHorario` existe; **el cupo no** (`historial_cambios_horario` sin uso) |
| RF-12 | Cambio de horario diferido a D+1 | ⚠️ | `CambioHorarioPendiente` modelado; sin caso de uso/endpoint |
| RF-13 | Ventanas de entrega y gracia | ✅ | Más preciso que el documento — ver §3 |
| RF-14 | Phone-Free 24h (cruza medianoche) | ✅ | `RachaSinCelular`, estado que el cron no expira (Ley IV cumplida) |
| RF-15 | Penalización semanal −10 si no se completó | ❌ | `revisiones_semanales_sin_celular` sin uso |
| RF-16 | Reemplazo de bebidas tóxicas | ❌ | `renombres_habito` sin uso |
| RF-17 | Hábitos personales propios | ⚠️ | El dominio los contempla (`Habito.plantillaClave`, ámbito PERSONAL); **falta el caso de uso de creación/programación** y `dias_semanales_habito` está sin uso |

### Evidencias

| RF | Requisito | Estado | Dónde está / qué falta |
|---|---|---|---|
| RF-18 | Envío de evidencias multimedia | ✅ | Con URL firmada en dos pasos |
| RF-19 | Validación con Gemini Vision (−5) | ⚠️ | Máquina de estados + reintentos + caída a revisión manual, todo probado; IA `NoOp` (D-39) |
| RF-20 | Revisión manual y devolución (+5) | ⚠️ | `review`/`void` existen; **falta el listado** de la cola (GAP-20) |

### Rocas y puntaje

| RF | Requisito | Estado | Dónde está / qué falta |
|---|---|---|---|
| RF-21 | Master Rocks | ✅ | |
| RF-22 | Weekly Rocks | ✅ | Con ventana de planificación y cierre semanal |
| RF-23 | Daily Rocks + Pomodoro | ⚠️ | Rocas diarias completas (incluido Pareto); el temporizador es cliente |
| RF-24 | Coherencia diaria (Ley VI) | ⚠️ | `RegistrarCoherenciaDiariaUseCase` existe y **nadie lo llama** — era **Q-2**, ahora resuelta (§2) |
| RF-25 | Semáforo diario (80/60) | ❌ | Los umbrales no existen en el código |
| RF-26 | Puntos inmutables (append-only) | ✅ | Ledger + saldo en la misma transacción (Ley I cumplida) |
| RF-27 | Ranking de liga | ⚠️ | LEAGUE y CELL reales; **GENERAL y COHORT lanzan `UnsupportedOperationException` a propósito** (D-P7: no se inventó la fórmula). El documento la aporta parcialmente |
| RF-28 | Detección y activación del Verdugo | ❌ | Registramos eventos (con el fix de destino ajeno, E-38); **la detección automática no existe** |
| RF-29 | Bloqueo forzado (overlay) | ✅ | Del lado backend: el evento y su resolución. El overlay es cliente |

### Diario, radar, comunidad

| RF | Requisito | Estado | Dónde está / qué falta |
|---|---|---|---|
| RF-30 | Journaling diario | ⚠️ | Dominio + puertos + persistencia listos; **sin caso de uso ni endpoint de escritura** (GAP-31) |
| RF-31 | Espejo de la Sombra (semanal, IA) | ✅ | Construido este ciclo, con scheduler semanal y control de visibilidad D-47 |
| RF-32 | Radar de conciencia | ✅ | `POST/GET /radar`, historial paginado |
| RF-33 | Publicación en muros | ✅ | Con carrusel multimedia |
| RF-34 | Reacciones y comentarios | ✅ | Reacción única excluyente; corregido este ciclo el bug de cuenta suspendida (E-42) |
| RF-35 | Mensajería en tiempo real | 🔵 ✅ | Cumplido **con otra tecnología**: WebSocket/STOMP + Redis Pub/Sub en vez de Supabase Realtime. Con autorización en handshake y en suscripción (E-37) |

### Academia, IA y soporte

| RF | Requisito | Estado | Dónde está / qué falta |
|---|---|---|---|
| RF-36 | Clase diaria | ⚠️ | `GET` sí; **`POST` de completar no existe** (GAP-23) |
| RF-37 | Audioterapia + sincronización Drive | ❌ | `audioterapias` sin uso; el puerto de catálogo de audios existe sin adaptador real |
| RF-38 | Recomendación adaptativa de cursos | ⚠️ | Endpoint real; IA `NoOp` |
| RF-39 | Chatbot RAG RenasIA | ✅ | pgvector 768 dim + citas de lección + cuota diaria (D-48). IA `NoOp` |
| RF-40 | Auditoría de alucinaciones | ❌ | Ni el campo ni la ruta (GAP-30) — el documento lo confirma como requisito |
| RF-41 | Calendario y eventos segmentados | ✅ | Con audiencia por rol/nivel/célula y cola de recordatorios |
| RF-42 | Tickets de bloqueo SMART | ✅ | Con el fix de "mentor asignado ≠ cualquier mentor" (E-38) |
| RF-43 | Biblioteca de sabiduría | ✅ | Búsqueda full-text en español |
| RF-44 | Tickets de soporte con ClientLog | ⚠️ | Tickets sí (incluida la regla deliberada de que un suspendido **sí** puede abrirlos); **el adjunto automático de logs del cliente no está verificado** |
| RF-45 | Tarjetas de bienvenida idempotentes | ❌ | `mensajes_bienvenida` sin uso |

---

## 5. Las Leyes del sistema, una por una

El documento consolida 6 "Leyes Maestras". Las verifiqué contra el código porque violarlas sería un bug de arquitectura, no un gap de alcance:

| Ley | Exigencia | Verificación |
|---|---|---|
| **Ley I** · Inmutabilidad de puntos | `LeaguePointAdjustment` append-only, prohibido UPDATE/DELETE, piso en 0 | ✅ **Se cumple.** `AjustePuntos` es un `record` inmutable (D-P2, "un asiento del ledger es un HECHO"), asiento y saldo en la misma transacción |
| **Ley II** · Identidad por `system_key` | El título es editable; la lógica debe emparejar por la clave inmutable | ✅ **Se cumple.** `Habito.claveSistema` existe y es la identidad de negocio |
| **Ley III** · `programDay` solo avanza por cron | **Nunca** derivarlo de `startDate` vs hoy | ✅ **Se cumple, verificado explícitamente.** `ParticipacionPrograma.diaPrograma` es un contador que solo hace `++` con tope en 90. La única resta de fechas del proyecto está en `SemanaPrograma` (rocks) y calcula *semana de calendario*, que es otra cosa |
| **Ley IV** · Aislamiento Phone-Free | Estado `IN_PROGRESS` excluido de la expiración nocturna | ✅ **Se cumple.** |
| **Ley V/VI** · Semáforo y coherencia | Fórmula y umbrales | ❌ **No implementada** — es RF-24/RF-25. La fórmula recién llega con este documento |

**Cuatro de seis leyes se cumplen y están probadas.** La que falta es justamente la que nadie nos había dado hasta hoy.

---

## 6. Qué cambia en el plan que armamos ayer

El [`PLAN_INTEGRACION_FRONTEND.md`](PLAN_INTEGRACION_FRONTEND.md) listaba 31 GAPs desde la perspectiva "qué le falta al backend para que la app funcione". Este documento agrega una perspectiva distinta y complementaria: **"qué le falta al backend para que el producto esté completo"**. Se solapan bastante, pero no del todo — y lo que aporta de nuevo es prioridad de negocio, que es justo lo que le faltaba a esa lista.

**Reordenamiento que propongo, con criterio de senior:**

**Prioridad 1 — El motor diario (RF-09, 24, 25, 28).** Es una cadena: generar tracks → calcular coherencia → asignar semáforo → disparar Verdugo. Hoy tenemos las cuatro piezas modeladas y **ninguna conectada**, porque nos faltaba la fórmula. Ya la tenemos. Esto es lo que convierte la app de "un registro de hábitos" en "el programa de 90 días" — sin esto, el producto no existe como fue diseñado. Además desbloquea `historial_coherencia` y cierra Q-2.

**Prioridad 2 — Lo barato que cierra requisitos enteros.** `POST /classroom/clase-diaria` (RF-36), listado de evidencias y de la cola de revisión (RF-20), escritura de entrada de diario (RF-30, que además desbloquea el Espejo de la Sombra que ya construimos), auditoría de cambio de rol (RF-04, que es una Ley incumplida). Todos son de días, no de semanas.

**Prioridad 3 — Ciclos de intoxicación (regla de negocio nueva).** `TipoDia.INTOXICACION` y `Habito.obligatorioEnIntoxicacion` ya existen sin usarse; solo falta la función que dice qué días son. Es chico y tiene impacto directo en la coherencia de Prioridad 1.

**Prioridad 4 — Personalización de hábitos (RF-10, 11, 12, 16, 17).** Es la bolsa grande. Son 5 requisitos y 5 de las 7 tablas sin uso. Merece su propio lote.

**Prioridad 5 — Lo que espera credenciales (RF-07, 19, 38, 39).** No es código: es D-39. El fallback a revisión manual ya funciona y está probado, así que **se puede salir a producción sin Gemini** y encenderlo después. Vale decidirlo como negocio, no dejarlo bloqueando.

**No prioridad — lo que el documento pide y nuestra arquitectura ya resolvió mejor.** RF-35 (tiempo real) está cumplido con WebSocket propio en vez de Supabase Realtime; el documento describe Vercel Cron y nosotros usamos `@Scheduled` sobre virtual threads. Eso no se "cumple" más de lo que ya está: es la migración misma.

---

## 7. Riesgos que este documento deja al descubierto

1. **RF-03 y la Ley de auditoría (RF-04) chocan con lo que hoy hace el `approve`.** Aprobar una solicitud debería asignar cohorte, célula, mentor y fecha de inicio; hoy no recibe nada de eso. Y cambiar un rol debería dejar rastro inmutable; hoy no lo deja. Son dos huecos de **gobernanza**, no de funcionalidad — el tipo de cosa que no se nota hasta que hace falta.
2. **El documento asume `programDay` avanzando por cron para *todos* los aprendices activos.** Nuestro `ParticipacionPrograma` tiene el contador y el tope, pero conviene confirmar que existe el scheduler que lo incrementa — si no, el programa nunca avanza solo.
3. **La ventana de gracia (§3) necesita una decisión explícita del dueño.** No es un detalle: cambia cuántos puntos gana un aprendiz que llega 6 minutos tarde, todos los días, en todos los hábitos.
4. **`habitos_personales` no tiene tabla propia en el baseline congelado.** RF-17 se puede cumplir con el `ambito`/`plantilla_clave` de `habitos` (así está modelado), pero conviene confirmarlo antes de construir, porque D-40 prohíbe crear tablas.

---

## 8. Lo que NO hay que hacer con este documento

- **No tratarlo como especificación de arquitectura.** Dice Prisma, Vercel Cron, Supabase Realtime, `requireRole`. Nada de eso aplica: describe el sistema que estamos reemplazando.
- **No "corregir" el código hacia el papel donde el papel simplifica** (§3, ventana de gracia). Primero se pregunta.
- **No crear tablas para cumplir un RF.** Sigue vigente D-40: la BD está congelada en 90 tablas. Si un requisito parece necesitar una tabla nueva, primero hay que verificar si el modelo actual ya lo soporta — en RF-17 resultó que sí.
- **No inventar lo que el documento sigue sin decir:** la fórmula del ranking GENERAL/COHORT, el criterio de "2 días seguidos críticos" (¿críticos = rojo?, ¿o rojo o amarillo?), y qué cuenta exactamente como "hábito obligatorio" en la Ley VI cuando hay hábitos personales de por medio.
