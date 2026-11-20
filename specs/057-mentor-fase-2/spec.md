# Spec 057 — Rol de mentor, fase 2

**Rama**: `057-mentor-fase-2` (backend y app)

**Creada**: 2026-11-20

**Estado**: En curso

**Input**: la fase 2 que la spec 055 dejó escrita en «Lo que no cubre» (`specs/055-rediseno-del-mentor/spec.md`, línea 197) y que el dueño ya había decidido el 17–18/11 con los bocetos B (Objetivos), 0 (Mis grupos con anteriores), F (Mi evaluación v2) y D (aviso de rojo) y las decisiones 2 y 8 de https://claude.ai/artifact/1NGKi8rKZxxm6BmRmU3GRz. El 20/11 el dueño pidió terminarla antes de la próxima versión, **reutilizando lo que ya existe, sin duplicar código y sin tablas nuevas**.

## Por qué

La fase 1 (055) ordenó el recorrido del mentor, pero quedaron cuatro huecos que el dueño ya había aprobado: el mentor no ve los objetivos del aprendiz, «Mi evaluación» muestra un porcentaje sin decir qué hacer, al rotar pierde todo rastro de sus grupos anteriores, y se entera de que alguien cayó a rojo recién con el resumen del sábado.

## Lo que se reutiliza (regla de esta spec)

| Pieza | Ya existe y se reutiliza | Lo único nuevo |
|---|---|---|
| Objetivos | `RocasDelAprendizFinder` (rocks), `ObjetivosDeLaPersonaService` y su respuesta (líder, spec 056 FR-027), `CortesDeFase`; en la app `ObjetivosDeLaFicha` + `useObjetivosDeLaFicha` (ya montados en `FichaDePersona`) y `leerObjetivosDeLaPersona` | Ruta del mentor con el guard de acompañamiento vigente; `duracionDias` y `fase` en la respuesta; `cargarObjetivos` en la rama del mentor de `lecturasDeLaFicha.ts` |
| Mi evaluación | `GET /api/v1/mentor/me/evaluation` (`SeguimientoService.evaluacionDe`), `EvaluacionCumplimiento.detallePorAprendiz` que hoy se descarta, `RankingDeGruposController`; en la app `HojaDeMiEvaluacion` y `useRankingDeGrupos` | Tres campos aditivos en la misma respuesta: `porcentajeMesAnterior`, `quienBaja[]`, `acciones[]` («Qué sigue») |
| Grupos anteriores | Tabla `asignaciones_celula` (inicio/fin), `AcompanamientoFinder.tramosDeMentor`/`tramosDeAprendices`, `SemaforoFinder.semanaDe`, ranking por mes; en la app `MisGruposScreen` | Un endpoint de solo lectura y la sección «Anteriores» |
| Aviso de rojo | `NotificacionService.emitir` (preferencias, bandeja, push, deduplicación por `origen_evento_id`), `SemaforoDelDiaFinder.delDia`, la regla de zona de `ResumenDeConsultasService`, el patrón de scheduler por zona con ShedLock e interruptor; en la app `TEMAS` de `preferenciasDeNotificacion.ts`, `rutaDeAviso.ts` y `destinoDeRuta` | Un valor nuevo del enum `tipo_notificacion` (V200, sin tabla), un scheduler, un evento y su listener, y un interruptor en `TEMAS` |

**Ninguna tabla nueva.** La única migración es `V200`, que agrega un valor al enum `renaser.tipo_notificacion`.

## Escenarios de aceptación

### Historia 1 — Ver los objetivos del aprendiz (P1)

1. **Dado** un aprendiz con programa activo que el mentor acompaña hoy, **cuando** el mentor abre su ficha, **entonces** ve la sección de objetivos que ya usa el líder: el objetivo del programa por eje con su avance («Objetivo de N días», con N el plazo de **su formación**, nunca «90» escrito a mano), el objetivo del mes y el de la semana, y arriba la fase en la que va («Fase 2 · días 8 a 34»).
2. **Dado** un aprendiz sin programa activo, en Día 0 o suspendido, **entonces** la sección dice en una línea que todavía no tiene objetivos; no queda un hueco en blanco.
3. **Dado** un aprendiz que el mentor ya no acompaña, **entonces** el servidor responde 403 y la app vuelve como en la fase 1.

### Historia 2 — «Mi evaluación» con «Qué sigue» (P1)

1. **Dado** un mes con evaluación calculada, **cuando** el mentor abre «Mi evaluación», **entonces** ve su porcentaje como hoy y, debajo, la diferencia con el mes anterior («3 puntos más que en octubre»; sin mes anterior calculado, no se muestra).
2. **Entonces** ve «Qué sigue»: hasta 3 acciones con su motivo en gris, calculadas por el servidor con las reglas fijas de FR-011.
3. **Entonces** ve «Quién baja el promedio»: hasta 5 aprendices **que acompaña hoy** con menos de 80 % en el mes, del menor al mayor, con el borde de su color; tocar uno abre su ficha.
4. **Dado** un mes sin muestra o sin historial, **entonces** no hay acciones ni lista y se ve el texto de estado de hoy.

### Historia 3 — Mis grupos anteriores (P2)

1. **Dado** un mentor que rotó, **cuando** abre «Mis grupos», **entonces** debajo de los activos ve «Anteriores» con cada grupo que acompañó: nombre, período, cantidad de aprendices, cómo cerró (puesto del último mes del tramo y color final) y la lista de nombres con su color final.
2. **Entonces** todo es de solo lectura: tocar un grupo anterior no abre fichas ni datos de hoy de esas personas.
3. **Dado** un mentor sin grupos anteriores, **entonces** no aparece la sección (sin título vacío).
4. **Dado** un mentor con un solo grupo activo **y** grupos anteriores, **entonces** su entrada ya no salta directo al grupo: abre «Mis grupos» para que vea los anteriores.

### Historia 4 — Aviso de rojo del mismo día (P1)

1. **Dado** un aprendiz que hoy (su día local) está en rojo según el semáforo del día, **cuando** en la zona del mentor son las 19:00, **entonces** el mentor recibe una notificación «<Nombre> está en rojo hoy» con «Lleva N % de sus hábitos. Toca para ver su ficha y escribirle.» y los botones «Escribirle» (abre el chat 1 a 1) y «Ver ficha»; tocar el cuerpo abre la ficha.
2. **Dado** que ya se le avisó por ese aprendiz esta semana, **entonces** no se repite hasta la semana siguiente.
3. **Dado** que el mentor apagó «Aprendices en rojo» en Yo → Notificaciones, **entonces** no recibe ni el push ni la entrada de la bandeja; los demás avisos siguen igual.
4. **Dado** un aprendiz sin datos, pausado, suspendido o fuera del programa, **entonces** no hay aviso.

### Casos límite

- La zona del mentor no está en `usuarios`: se toma como en `ResumenDeConsultasService` (su programa, si participa; si no, la cohorte de su primer grupo; Lima por defecto).
- Las 19:00 del mentor caen en otro día del aprendiz: se usa el día local **del aprendiz**.
- Mentor que acompaña varios grupos: el aviso y «Quién baja» cubren todos sus grupos vigentes; «Anteriores» lista cada tramo por separado.
- Grupos de antes del historial (V45): no aparecen en «Anteriores».
- Toques repetidos: un toque, un pedido.

## Requisitos funcionales

**Objetivos**

- **FR-001**: `GET /api/v1/mentor/groups/{groupId}/learners/{userId}/objetivos` devuelve los objetivos del aprendiz con la misma forma que `GET /api/v1/leadership/people/{personId}/objetivos` (se reutiliza la respuesta), más `duracionDias` y `fase {numero, primerDia, ultimoDia}` calculada con `CortesDeFase`.
- **FR-002**: La autoriza el servidor como el resto de rutas `/mentor/groups/{g}/learners/{u}` de la fase 1: el actor acompaña hoy el grupo y el aprendiz es aprendiz vigente de ese grupo; si no, 403.
- **FR-003**: Aprendiz sin programa activo o suspendido: 404 con `codigo: "SIN_PROGRAMA_ACTIVO"` (no 500 ni 403).
- **FR-004**: La ficha del mentor monta la sección de objetivos existente (`ObjetivosDeLaFicha`), con el rótulo «Objetivo de N días» (N = `duracionDias`) y la fase arriba; con `SIN_PROGRAMA_ACTIVO` muestra una línea («Todavía no tiene objetivos»).

**Mi evaluación**

- **FR-010**: La respuesta de `GET /api/v1/mentor/me/evaluation` suma, sin quitar nada: `porcentajeMesAnterior` (número o null si el mes anterior no está CALCULADA), `quienBaja[]` y `acciones[]`. Las apps viejas los ignoran.
- **FR-011**: `acciones[]` («Qué sigue») se calcula en el servidor con reglas fijas, en este orden y como mucho 3:
  1. Por cada aprendiz de `quienBaja` con menos de 60 % (rojo), hasta 2: tipo `ESCRIBIR`, «Escríbele a <nombre>», motivo «Entregó E de S este mes».
  2. Si `porcentajeMesAnterior` existe y el porcentaje bajó 5 puntos o más: tipo `REVISAR_GRUPO`, «Conversa con tu grupo sobre qué cambió», motivo «Bajaste N puntos desde <mes anterior>».
  3. Si `tardiasFueraDeVentana` > 0: tipo `VENTANAS`, «Recuérdales las horas de entrega», motivo «N evidencias llegaron fuera de hora».
  4. Si no salió ninguna y el porcentaje es 80 o más: tipo `RECONOCER`, «Reconoce el avance de tu grupo», motivo «Van en verde este mes».
  Con estado SIN_MUESTRA o SIN_HISTORIAL, `acciones` y `quienBaja` van vacíos.
- **FR-012**: `quienBaja[]` sale de `EvaluacionCumplimiento.detallePorAprendiz`, filtrado a los aprendices **vigentes hoy** en los grupos que el mentor acompaña hoy (el mentor saliente no ve a quien ya no acompaña), con menos de 80 %, del menor al mayor y como mucho 5: `{aprendizId, grupoId, nombre, porcentaje, entregadas, esperadas}`.
- **FR-013**: `HojaDeMiEvaluacion` muestra la diferencia con el mes anterior, «Qué sigue» (ícono, acción y motivo; tocar `ESCRIBIR` abre la conversación 1 a 1 con `useEscribirleAlAprendiz`) y «Quién baja el promedio» (tocar abre la ficha). Reutiliza el ranking y las pastillas «Este mes / Mes anterior» que ya tiene.

**Grupos anteriores**

- **FR-020**: `GET /api/v1/mentor/me/groups/history` (autoconsulta) devuelve los tramos MENTOR/GUIA del actor ya cerrados o de grupos que ya no están en curso, del más reciente al más viejo: `{grupoId, nombre, desde, hasta, aprendices, puestoFinal {posicion, total} | null, colorFinal, porcentajeFinal, personas [{nombre, colorFinal}]}`.
- **FR-021**: El color y porcentaje finales son los de la última semana cerrada **dentro del tramo** (`SemaforoFinder.semanaDe`), nunca los de hoy; `personas` no lleva ids ni ningún dato de hoy.
- **FR-022**: `MisGruposScreen` muestra «Anteriores» debajo de los activos, de solo lectura; sin anteriores no hay sección. Con un solo grupo activo y anteriores, la entrada abre «Mis grupos» en vez de saltar al grupo.

**Aviso de rojo**

- **FR-030**: Nuevo valor `ROJO_DEL_DIA` de `TipoNotificacion` (V200: `ALTER TYPE renaser.tipo_notificacion ADD VALUE IF NOT EXISTS 'ROJO_DEL_DIA'`, solo en su archivo), con su espejo JPA; aparece en `GET /notification-preferences` y se apaga con `PATCH` como los demás.
- **FR-031**: Un scheduler en UTC con ShedLock e interruptor (`mentoring.aviso-de-rojo`, prendido por defecto) corre cada 30 minutos; para cada mentor con grupo vigente en cuya zona sean entre las 19:00 y las 19:29, consulta `SemaforoDelDiaFinder.delDia` de sus aprendices vigentes en el día local de cada aprendiz y emite un evento por cada aprendiz en ROJO (estado MEDIDO).
- **FR-032**: Como mucho un aviso por mentor, aprendiz y semana: `origenEventoId = UUID.nameUUIDFromBytes(mentor|aprendiz|ROJO_DEL_DIA|lunes de la semana del aprendiz)`.
- **FR-033**: El push dice «<Nombre> está en rojo hoy» / «Lleva N % de sus hábitos. Toca para ver su ficha y escribirle.» con `route` `/mentor/groups/{g}/learners/{u}` (la ruta que la app ya sabe abrir); queda en la bandeja del mentor.
- **FR-035**: La notificación trae dos botones, «Escribirle» y «Ver ficha» (boceto D). El servidor manda `categoryId: "rojo-del-dia"` y en `data` el `grupoId` y el `aprendizId` además de `route` (`MensajePush`/`ExpoPushTransporte` aceptan una categoría opcional; los demás pushes no cambian). La app registra esa categoría al arrancar con `setNotificationCategoryAsync` (como ya hace la carrera) y, al tocar, `rutaDeAviso.ts` lee `actionIdentifier`: «Escribirle» abre la conversación 1 a 1 (`abrirConversacionDirecta`), «Ver ficha» o tocar el cuerpo abren la ficha. Una app sin la categoría registrada ignora los botones y abre la ficha por `route`.
- **FR-034**: En Yo → Notificaciones aparece «Aprendices en rojo» («Te avisamos a las 19:00 si alguien de tu grupo cae a rojo ese día»), solo para quien acompaña un grupo; la bandeja de avisos del mentor incluye `ROJO_DEL_DIA` y abrir la ficha lo marca como leído, como `ACOMPANAMIENTO_ALUMNO`.

**Transversal**

- **FR-040**: Textos en español de Perú, con tuteo y tipo oración; toques de 48 de alto; el plazo siempre de la formación de cada persona.
- **FR-041**: Cada lectura nueva respeta el patrón de la fase 1: un toque, un pedido; una lectura vieja no pisa a la nueva; el motivo del servidor se muestra.

## Criterios de éxito

- **SC-001**: Ninguna tabla nueva; una sola migración (V200, un valor de enum).
- **SC-002**: Ningún componente, hook o servicio duplicado: lo listado en «Lo que se reutiliza» se usa tal cual o se amplía. [sin prueba automática: es una regla de revisión del código; se comprobó pieza por pieza en la revisión del 20/11 (la zona del acompañante quedó en un solo finder, los objetivos en un servicio compartido con el líder)]
- **SC-003**: Las apps 1.8.0 (135) siguen funcionando contra el backend nuevo (campos aditivos, rutas que ya conocen).
- **SC-004**: El mentor ve objetivos, «Qué sigue», anteriores y recibe el aviso en el E2E del emulador.

## Lo que no cubre

Nada de lo decidido para el rol de mentor queda afuera (regla del arnés del 20/11: lo que el dueño decidió sale completo).
