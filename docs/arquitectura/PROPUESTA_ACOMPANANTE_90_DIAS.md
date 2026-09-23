# Propuesta: el acompañante de 90 días como planificador

**Estado:** fases 1, 2, 4 y 5 implementadas en el backend (D-152 a D-155); pendientes la 3 (botones, repo de la app) y la 6 (router, necesita medirse con Gemini).
> Decía «propuesta, pendiente de decisiones del dueño. Nada está implementado todavía». Corregido el
> 2026-09-23 al cerrar la fase 2.
**Fecha:** 2026-09-23. **Base:** inventario de todos los endpoints que puede llamar un aprendiz
(`TRAINEE`), hecho módulo por módulo contra el código de la rama `agente`.

## 1. Situación actual

El acompañante (`COMPANION`) tiene **3 herramientas** (`CatalogoHerramientasAgente`):
`consultar_habitos_del_dia`, `consultar_puntos_en_juego` y `marcar_habito_completado`.

El aprendiz puede hacer **unas 90 operaciones** en el backend. La mayor parte de lo que hace falta
para planificar ya existe como caso de uso; lo que falta es exponerlo como herramienta.

## 2. Lo que se pide

Un acompañante que:

1. ayude a **planificar** el día y la semana (hábitos, rocas, eventos);
2. **sepa la hora** y avise: "si no lo haces antes de las 8:30, pierdes X puntos";
3. **ajuste los horarios** de cada persona a su vida real;
4. acompañe, no solo ejecute.

## 3. Qué existe para eso (verificado en el código)

### 3.1 Saber la hora y los puntos

| Dato | De dónde sale | Nota |
|---|---|---|
| Hábitos de hoy: estado, puntos si entrega ahora, puntos máximos, **plazo** | `AgendaDelDiaFinder` (ya lo usa la tool) | La ventana es la hora ancla + 10 min de gracia + extensión, en la zona del participante (`VentanaEntrega`) |
| Día del programa, fase, racha, puntos de liga, próximo evento | `GET /home` (`ConsultarResumenHomeUseCase`) | |
| Rocas de hoy y de mañana | `GET /rocks/today`, `/rocks/tomorrow` | |
| Eventos del calendario | `GET /calendar/events?from&to` | |

**Regla:** "¿llego a tiempo?" y "¿cuánto pierdo?" se **calculan en código** con `plazo`, `puntosEnJuego`
y el reloj (`Clock`, en la zona de la persona). El modelo **no** hace cuentas de horas: es malo con
fechas y números, y la regla de tiempo del repo (`.claude/rules/02`) exige la zona del participante.

### 3.2 Ajustar horarios (todo propio del aprendiz)

| Operación | Endpoint | Restricción ya implementada |
|---|---|---|
| Ver el horario resuelto de un día | `GET /habit-preferences?date=` | precedencia: fecha > día de semana > cambio general > preferencia > catálogo |
| Cambiar la hora de un hábito (general o para una fecha) | `PATCH /habit-preferences/{habitId}` | **consume la cuota semanal de cambios**; puede quedar diferido |
| Horario por día de semana | `PUT /habit-preferences/{habitId}/weekdays/{weekday}` | |
| Apagar o prender un día concreto | `PATCH /habit-preferences/{habitId}/days/{date}` | no acepta días pasados; los obligatorios no se apagan |
| Pausar un hábito del plan | `PATCH /habit-unlocks/{habitId}` | los obligatorios no se pausan |
| Elegir el día de un hábito semanal | `PUT /weekly-habit-days/{habitId}` | solo días de esta semana que no pasaron |

### 3.3 Planificar

| Operación | Endpoint | Restricción ya implementada |
|---|---|---|
| Plan diario de rocas | `POST /rocks/plan` | ventana desde las 18:00 (zona del participante); de 1 a 3 rocas por eje |
| Plan semanal (Domingo Ritual) | `POST /rocks/weekly`, `PATCH /rocks/weekly/{id}` | ventana de planificación semanal |
| Cerrar la semana (revisión) | `PATCH /rocks/weekly/{id}/review` | |
| Objetivo del mes / roca maestra | `GET /rocks/monthly/plan`, `PUT /rocks/master/{eje}` | |
| Confirmar asistencia a un evento | `PUT /calendar/events/{id}/rsvp` | no acepta ocurrencias pasadas |

## 4. Propuesta: niveles de riesgo

| Nivel | Qué hace el acompañante | Herramientas propuestas |
|---|---|---|
| **R0: leer** | directo, sin preguntar | agenda de hoy (ya existe), resumen del día, horario de un día, rocas de hoy/mañana/semana, eventos de la semana, plan de desbloqueo |
| **R1: calcular sin guardar** | directo | "¿llego a tiempo?", "qué me conviene hacer primero", **vista previa** de un cambio de horario o de un plan |
| **R2: escribir lo propio** | **solo con confirmación explícita**, mostrando qué va a cambiar | marcar hábito (ya existe), cambiar horario, apagar un día, pausar hábito, elegir día semanal, crear plan diario o semanal de rocas, RSVP |
| **R3: fuera del acompañante** | nunca, ni con confirmación | ver §4.1 |

El flujo R2 es: el modelo propone → el código valida con las mismas guardas que la app → se le
muestra a la persona → la persona dice "sí" → recién entonces se ejecuta. Es lo mismo que evitó el
incidente D-132 (un "Hola" que marcó un hábito).

### 4.1 Fuera del acompañante (R3), y por qué

| Operación | Motivo |
|---|---|
| Mensajes a otros (chat DM/célula/global, compartir, tickets al mentor o a soporte) | habla en nombre de la persona con terceros |
| Muro: publicar, comentar, reaccionar, editar | contenido público |
| Perfil y avatar | visible para todos |
| Baja de cuenta, vincular cuenta social, contraseña | seguridad y destructivo |
| Activar el programa (fecha de inicio) | irreversible |
| Firmar contratos de fase, aceptar términos o pacto | consentimiento legal de la persona |
| Evidencias con foto, video o audio, V90 | necesitan un archivo que el chat no recibe |
| Descompletar lección | no revierte los puntos (D-146): se podría cobrar dos veces |

## 5.0 Decisiones tomadas (2026-09-23)

| Tema | Decisión del dueño |
|---|---|
| Escrituras R2 de la v1 | horarios (cambiar hora, apagar un día, elegir día semanal), plan de rocas (diario y semanal), pausar hábito. Todas con confirmación y **sin saltarse ninguna regla existente** |
| Cuota de cambios de horario | **Se respeta la regla que ya existe** (`CuotaEdicionHorario`): la primera semana del programa es libre; después, 3 hábitos distintos por semana de programa. No es ilimitada, y el acompañante no tiene cuota propia: usa el mismo caso de uso que la app. Lo que sí puede hacer es avisar cuántos cambios quedan antes de proponer uno |
| Avisos proactivos | **Sí**: el acompañante escribe primero. Diseño en §5.1 |
| Confirmación | **Botones en la app** (corregido el mismo día: primero se había elegido "solo texto"). Verificado: el backend **no** tiene hoy ningún mecanismo de confirmación en el chat. `EventoRenasia` tiene solo `Texto`, `Fuentes`, `Error` y `Fin`, y la "confirmación" de `calendar` es el RSVP de eventos, otra cosa. Hay que construirlo (§5.2) |

### 5.1 Avisos proactivos: cómo, sin costo de IA por aviso

Ya existe el disparador: `DespacharAvisosHabitoUseCase` avisa antes del inicio y antes del
vencimiento de cada hábito, con antelación configurable (`HABITS_AVISO_ANTELACION_*`). Propuesta:
cuando ese aviso sale, dejar **además** un mensaje del acompañante en su conversación, armado con
una **plantilla** y datos reales ("Te quedan 30 min para Meditar: si lo haces ahora sumas 10
puntos"). Sin llamar a la IA: costo cero y sin riesgo de que invente horas o puntos. Si la persona
responde, recién ahí entra el modelo.

Queda **por decidir** (no se inventa): qué avisos pasan al chat (¿todos o solo el de vencimiento?)
y el texto de la plantilla.

### 5.2 Confirmación con botones

El incidente D-132 fue un pedido viejo que se ejecutó con un "Hola". Con botones, **el modelo nunca
ejecuta una escritura**: solo la propone. La ejecuta un endpoint que la persona dispara con un botón.

```text
modelo pide "cambiar_horario(Meditar, 07:00)"
  → el código valida con las guardas reales (cuota, días pasados, obligatorios, ventana)
  → guarda una PROPUESTA pendiente en el servidor: qué cambia, hash de argumentos, vence en minutos
  → SSE: evento nuevo `propuesta` {id, resumen, vence}   ← la app dibuja [Confirmar] [Cancelar]
  → la persona toca Confirmar
  → POST /api/v1/renasia/propuestas/{id}/confirmar
       vuelve a validar: dueño, no vencida, no usada, hash, guardas → ejecuta UNA vez (idempotente)
```

Piezas a construir:

1. **Backend:** tabla de propuestas (migración nueva: PENDIENTE → CONFIRMADA / CANCELADA / FALLIDA.
   > **Corregido al implementar (D-153):** decía también `VENCIDA`; el vencimiento se deriva de
   > `vence_en`, sin estado guardado ni scheduler), variante `EventoRenasia.Propuesta`, endpoints `confirmar` y `cancelar`
   con `@RequiresPermission`, y pruebas negativas (otro usuario → 403, vencida → rechazo, doble
   confirmación → una sola ejecución).
2. **App:** botones para el evento `propuesta`.
3. **Compatibilidad:** la app no se actualiza por aire, y un `tipo` SSE desconocido se ignora en
   silencio (`docs/MODULO_RAG.md`). Por eso cada propuesta manda además un `Texto` con el
   resumen: una app vieja al menos muestra qué se propuso, aunque no pueda confirmarlo.

El texto del modelo **nunca** confirma: "sí" escrito en el chat no ejecuta nada.

## 5. Decisiones del dueño (no las toma el agente)

> **2026-09-23:** las cuatro de abajo quedaron resueltas en §5.0. Se conservan como registro de
> qué se preguntó.

1. **¿Qué escrituras R2 se habilitan en la primera versión?** Recomendación: empezar por las de
   horario (cambiar hora, apagar un día, elegir día semanal) más el plan diario de rocas. Son las
   que se piden.
2. **Cuota semanal de cambios de horario:** si el acompañante cambia un horario, ¿consume la misma
   cuota que la app? Recomendación: sí. Si no, el chat sería una puerta trasera a la regla.
3. **Avisos proactivos:** hoy el acompañante solo responde cuando le escriben. Los avisos push "se
   te vence X" ya existen (`DespacharAvisosHabitoUseCase`, antelación configurable). ¿El
   acompañante también debe **iniciar** conversaciones? Eso es otro alcance (costo de IA por
   usuario y por día).
4. **Confirmación en la app:** R2 necesita que la app muestre "¿Confirmas?" con botones. Hoy el
   contrato SSE no tiene ese evento. Hace falta un tipo nuevo que la app conozca, y la app no se
   actualiza por aire: hay que publicar una versión nueva.

## 5.3 Orden de implementación

Cada fase es un cambio propio, con `clean verify` en verde, y no rompe a la app actual:

| Fase | Qué | Repo |
|---|---|---|
| 1 | Herramientas de **lectura** (R0) y **cálculo de tiempo** en código (R1): "¿llego a tiempo?", resumen del día, horario de un día, cuota restante, rocas, eventos de la semana. **Hecha 2026-09-23 (D-152)**: 3555 pruebas unitarias en verde; la integración (failsafe) quedó sin correr por falta del token de Testcontainers Cloud | backend |
| 2 | **Propuestas con botones**: tabla, evento SSE `propuesta`, endpoints confirmar/cancelar, más `marcar_habito_completado`, que hoy escribe sin confirmar, pasado a propuesta. **Hecha 2026-09-23 (D-153)**, flag apagado; 3624 pruebas unitarias en verde; migración V63 y pruebas de integración sin correr (falta el token de Testcontainers Cloud) | backend |
| 3 | Botones en la app para el evento `propuesta` | app |
| 4 | Herramientas de **escritura** como propuestas: horario, apagar un día, día semanal, pausar, plan de rocas diario y semanal. **Hecha 2026-09-23 (D-154)** | backend |
| 5 | **Avisos proactivos** con plantilla (§5.1). **Hecha 2026-09-23 (D-155)**, flag apagado, textos provisorios | backend |
| 6 | Router rápido (`ClasificarIntencionPort`) medido con el dataset es-PE | backend |

**Riesgo de la fase 2:** pasar `marcar_habito_completado` a propuesta cambia el comportamiento
actual. Mientras la app no tenga los botones (fase 3), la persona no podría confirmar desde el
chat. Por eso la fase 2 se activa detrás de un flag que se prende junto con la versión de la app.

## 6. Hallazgos al margen (se reportan, no se tocan en este cambio)

- `GET /wall/mine` no tiene `@RequiresPermission` y, según el inventario, con el header
  `X-Actor-Id` devuelve el conteo de publicaciones de cualquier usuario. `GET /wall/{postId}/comments`
  tampoco tiene control. **Hay que confirmarlo y corregirlo aparte.**
- `DELETE /lecciones/{id}/complete` no revierte los 10 puntos: completar, desmarcar y completar
  paga de nuevo (ya anotado como D-146).
- `POST /chat/conversations/direct` permite abrir un DM con cualquier usuario activo sin relación
  previa. Puede ser intencional; se deja anotado.
- `GET /tickets/library` hace búsqueda de texto sobre respuestas de mentores; no se verificó que no
  exponga datos de otros aprendices.
