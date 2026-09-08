# Reparto de trabajo entre agentes — 2026-09-08

Lista para repartir en paralelo. Cada bloque es **independiente de los demás** (tocan archivos
distintos) salvo donde se dice lo contrario, así que dos agentes no se pisan.

**Cómo se armó:** leyendo el código y los documentos del repo, no de memoria. Cada punto lleva de
dónde salió. Lo que **no** está verificado se dice en la propia línea.

**Estado de las ramas al escribir esto:**

| Repo | Rama | HEAD | ¿Falta subir? |
|---|---|---|---|
| backend | `codex/horarios-habitos-por-fecha` | `098af437` (E-160) | No — igual a `origin` |
| frontend | `planificar-por-bloque-en-training` | `32454377` | No — igual a `origin` |

Ninguna de las dos está fusionada a `master` (`3cdd228f` y `2df3e499`).

---

## Bloque A — El reloj del programa (día 0). ~~**Empezar por acá**~~ **HECHO 2026-09-08**

> **Cerrado el 2026-09-08**, bitácora **E-161**. A1, A2 y A3 aplicados y verificados
> (`clean verify`: 2576 unitarias + 25 de integración, 0 fallos). El detalle de abajo se deja como
> estaba para que se vea qué se arregló. **Sin commitear todavía.**

Ver el análisis en el hilo del 2026-09-08 y `BITACORA_ERRORES.md` E-91 / E-137.

**A1. El día se materializa, no se deriva al leer.**
`ConsultarResumenParticipacionPersistenceAdapter` lee `COALESCE(pp.dia_programa, 0)`, y esa columna
la escribe **solo** `AvanzarDiaProgramaScheduler`. Si el backend no estuvo arriba en el minuto :05
de la hora que cruza la medianoche local, la pantalla muestra 0 aunque
`ParticipacionPrograma.diaProgramaDerivado` sepa la respuesta correcta.
La query ya trae `fecha_inicio` y `timezone`: derivar ahí mismo cierra el hueco sin tocar los
7 módulos consumidores.

**A2. El barrido no tiene `try/catch` por participante.**
`RelojProgramaService.avanzarParticipantesActivos` recorre el padrón sin proteger cada iteración.
Una sola fila que explote (zona inválida, dato raro) aborta el barrido entero **para todos**, cada
hora. Lo exige `.claude/rules/02-tiempo-zonas-y-schedulers.md` §4 y hoy no está.

**A3. Test de regresión** que falle contra el código viejo: participante con `fecha_inicio = ayer`
y `dia_programa = 0` en la base → el endpoint tiene que devolver 1, sin correr el scheduler.
Reloj en una hora UTC que en Lima sea el día anterior (regla 03).

> A1 y A2 son el mismo archivo/zona: **un solo agente**.

---

## Bloque B — Producción, que hoy se cae sola

**B1. E-160 — la JVM puede pedir más memoria de la que hay.** **ABIERTO, sin corregir.**
La corrección ya está escrita en la bitácora: bajar `MaxRAMPercentage` a ~40, `--memory=1300m` al
contenedor y 2 GB de swap. Requiere ventana de reinicio (~45 s).

**B2. E-139 — el workflow de CD termina en verde en 15 s sin desplegar.** Diagnosticado; falta un
paso manual del dueño.

**B3. E-150 — `unTokenVencidoYaNoSePuedeConsumir` falla solo dentro de la suite completa.**
Diagnosticado, sin corregir, **reapareció el 2026-09-07**.

---

## Bloque C — El hueco más grande del producto

**C1. La coherencia y la racha no se calculan (D-83).**
`RegistrarCoherenciaDiariaUseCase` no tiene un solo llamador; `puntajes_participante.coherencia`
queda en 100 para siempre e `historial_coherencia` está vacía. Un aprendiz que no abre la app
termina con la misma coherencia que uno perfecto.
**Bloqueado por una decisión de producto:** ¿coherencia = % de hábitos completados sobre los
esperados del día, acumulada o ventana móvil? ¿Expirar penaliza puntos o solo coherencia?
**No empezar sin esa respuesta.**

**C2. La graduación nunca se marca.** `programa_completado`, `dia_post_programa` y
`programa_completado_en` no tienen setter ni caso de uso.

---

## Bloque D — Deudas puntuales de backend (independientes entre sí)

- **D1.** Un hábito PERSONAL no puede aplicar solo algunos días: `MisHabitosService.crear` fija
  `TipoDia.TODOS` y el DTO no lleva días. Pide decisión de producto sobre qué combinaciones valen.
- **D2.** El comentario de `MisHabitosController.listar` dice que no ejecuta ningún guard y **es
  falso** (`consultar` llama a `requireProgreso`). Justifica la ausencia de `@RequiresPermission`
  con un hecho que no ocurre.
- **D3.** El interruptor ACTIVO/PAUSADO se ve por día y se guarda por **rango de fechas**
  (`pausado_en`/`pausado_hasta`). Pausar "solo el martes" pausa hasta esa fecha. Cerrarlo exige
  decidir cuál de las dos lecturas manda.
- **D4.** `participantes_programa.habitos_escalonados_en` sigue sin lector ni escritor.
- **D5.** Fase 4 de seguridad: faltan `anyRequest().authenticated()` y migrar los **162 usos de
  `X-Actor-Id` en 54 controllers**. Ya está hecho para `/home`, `/users/me/**`, hábitos, evidencia,
  academia, célula, tickets, ranking y renasia — verificado hoy: producción devuelve **403** en
  `/api/v1/home` sin sesión.

---

## Bloque E — Frontend

- **E1.** **El Mapa de Renacimiento no se guarda en el servidor.** Vive entero en `AsyncStorage`
  (`renaser.mapa-renacimiento.v1.<usuario>`). Si el aprendiz borra la app o cambia de teléfono
  **pierde el mapa completo**; solo sobreviven los hábitos creados. Necesita entidades y endpoints
  nuevos en el backend → es el trabajo más grande de esta lista.
- **E2.** El Pacto **nunca se monta**: `OnboardingFlow` tiene `ficha → terminos → activar-programa`.
  `PactoScreen`, `BienvenidaScreen` y `GongVictoriaScreen` son código muerto y `pactSignedAt` queda
  `null` para todos. **Pregunta abierta:** ¿el Pacto se firma en el alta o después desde YO?
- **E3.** `YoScreen` dice "FIRMA DIGITAL REGISTRADA & SELLADA" sobre el **nombre en cursiva**, no
  sobre la firma trazada. Se ve igual para alguien que nunca firmó.
- **E4.** `HAY_RECORDATORIOS` apaga los recordatorios en Expo Go con un argumento que aplica a
  **push**, no a notificaciones locales (`scheduleNotificationAsync`, que sí funcionan). Es una
  línea, pero es decisión de producto.
- **E5.** `PlanScreen` conserva su copia de `ULTIMO_INDICE_NO_PLANIFICABLE`, duplicada de
  `esPlanificable` en `semanaDelPlan.ts`. Unificar cuando termine el remodelado de esa pantalla.
- **E6.** **Sin una sola prueba.** No hay script `test` ni un `.test.tsx`. Todo se verifica a ojo.
- **E7.** Sin verificar en dispositivo (los tres son de dedo): editor de hora en hábitos
  obligatorios, candado de días no planificables, recorrido libre del Mapa.
- **E8.** Datos falsos todavía en pantalla: Comunidad (`12 conversaciones · 3 eventos ·
  2 mentorías`), tarjeta "Consistencia de la Dimensión" en Training, vista OBJETIVOS en Plan, y las
  fases `1-30 / 31-60 / 61-90` cuando el backend tiene **cuatro**: 1-7 / 8-34 / 35-64 / 65-90.
  (Coherencia y racha se dejan como están por decisión del dueño.)
- **E9.** `.env` con IP de LAN (`http://192.168.18.46:8080`). Se rompe en cada cambio de red.

---

## Bloque F — Fuera de código (solo el dueño)

- **F1.** Ataque de cadena de suministro del 2026-09-05 en `RENASER-LAB`: revocar el acceso de la
  cuenta del force push, activar el ruleset que bloquea force push y borrado de ramas, pedir a
  GitHub que purgue el objeto `11b7fd4` (accesible por SHA directo) y rotar credenciales.
- **F2.** Rotar los 5 secretos de `.run/RenaserOsApplication.run.xml` (carpeta sincronizada con
  OneDrive).
- **F3.** Bucket policy de lectura pública sobre `avatares/*` (D-55): hoy la URL es correcta y
  responde 403.
- **F4.** Push web **resuelto en este cambio**: `WebPushAdapter` entrega los dos avisos de hábitos
  con VAPID y conserva el aislamiento por usuario. Falta únicamente cargar las variables VAPID en
  el entorno de producción.

---

## Orden sugerido si hay pocos agentes

```
1. A (reloj)  +  B1 (memoria)     → en paralelo, son lo que se ve roto hoy
2. C1                              → después de que el dueño defina coherencia
3. E1 / E2                         → los dos huecos de producto del frontend
4. D, E5-E9, F                     → sueltos, cualquiera, en cualquier orden
```
