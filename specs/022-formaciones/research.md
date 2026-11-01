# Research: Formaciones (spec 022)

Todas las dudas del spec las respondió el dueño el 2026-10-31 (ver «Decisiones del dueño (31/10)» en `spec.md`). Aquí queda **cómo** se resuelve cada punto técnico.

## 1. ¿Dónde vive la formación de una persona?

- **Decisión**: aprendiz → `participantes_programa.formacion` (+ `duracion_dias`); personal → `formaciones_personal` (N:M, una fila con `activa = true`); Administración, Alquimista y
  Soporte TI → sin fila (alcance «todas»).
- **Por qué**: `participantes_programa` ya es «el programa de la persona» y 1:1 con ella; agregarle dos columnas no crea tablas. El personal necesita varias, y eso solo se
  guarda como relación.
- **Descartado**: columna `usuarios.formacion` (el personal tiene varias y Administración ninguna); `text[]` en `usuarios` (sin FK, sin quién asignó).

## 2. ¿Quién resuelve «qué formaciones ve quien actúa»?

- **Decisión**: `users.api.AlcanceDeFormacionFinder.de(actor)` devuelve un `shared.domain.AlcanceDeFormacion` (conjunto de `Formacion`). Reglas:
  `ADMIN` / `ALCHEMIST` / `IT_SUPPORT` → todas; `TRAINEE` → la de su participación (Escuela si no tuviera fila: defensa para el APK anterior); `MENTOR` / `TRAINEE_GUIDE` /
  `MENTOR_LEAD` → **solo la activa** (si la activa ya no está asignada, cae a la primera asignada; sin asignaciones, alcance **vacío** → no ve nada).
- **Por qué**: «nunca ve nada mezclado» (Historia 6) se logra devolviendo UNA formación a quien tiene varias; Administración usa el parámetro `formacion` de cada lista para
  acotar la vista mixta, y el alcance «todas» solo se le concede a ella.
- **Persistencia de la activa**: en el servidor, no en un header. La app no puede «decir» qué formación ve (es una preferencia de uso, no un permiso).

## 3. ¿Cómo se filtra en SQL?

- **Decisión**: `FiltroDeFormacionSql` (en `shared.infrastructure.persistence`) arma el predicado `col = ANY(:formaciones)` para `JdbcClient`/`EntityManager`. Cada consulta indica
  la columna (`pp.formacion` o `c.formacion`) y recibe el alcance; con alcance vacío el predicado es `FALSE`.
- **Pedir algo de otra formación**: `ObjetoDeOtraFormacionException` extiende `NoSuchElementException` → el `GlobalExceptionHandler` ya responde 404 con el mismo mensaje que
  «no existe». No se revela que existe en otra formación.

## 4. Duración y fases

- **Decisión**: `DuracionDelPrograma` (dominio puro) fija los topes: `MAXIMA = 365`; el tope del día de una persona es **su** `duracionDias`. `FasePrograma.paraDiaPrograma(dia, duracion)`
  estira los cortes originales (1, 8, 35, 65 sobre 90) con `inicio(k) = (corte(k) − 1) · duracion / 90 + 1` (división entera). Con `duracion = 90` da exactamente los cortes de hoy
  (verificado en prueba). `paraDiaPrograma(dia)` sin duración queda como estaba (90).
- **Graduación**: «al llegar al último día de la duración propia» (era el 90).
- **Por qué proporcional entero**: cumple Q2-A («mismas 4 fases estiradas en proporción») sin tabla de cortes ni inventar contenido; es determinista y se puede repetir en la app.
- **Calendario**: 1 año = 365 días, 3 meses = 90 (Q4-A). La etiqueta («3 meses», «1 año», «90 días») se arma en el servidor.
- **Topes de 90 en la base**: las columnas que guardan el día de la persona (`participantes_programa.dia_programa`, `ajustes_dia_programa`, `registros_habito.dia_programa`,
  `solicitudes_emergencia.dia_al_pedir`, `publicaciones_muro.dia_programa`) pasan a `0..365` y `participantes_programa` suma `dia_programa <= duracion_dias`. Las columnas de **contenido
  por día** (`audioterapias`, `guias_habito.dia_inicio`, `horarios_habito.dia_inicio`, `desbloqueos_habito.dia_desbloqueo`) no se tocan: son del catálogo de la Escuela y las
  specs 023 y 024 deciden cómo se reparten.

## 5. Traslado entre formaciones

- **Decisión**: un solo caso de uso en `users` (`TrasladarAprendizDeFormacionService`) que, en una transacción: valida (Q1-A: terminó, o `darPorTerminada` con motivo escrito), guarda
  `participaciones_anteriores`, reinicia la fila de `participantes_programa` (formación y duración nuevas, día 0, sin Día 1 elegido, sin grupo ni mentor, `programa_completado = false`,
  `dias_ajuste_programa = 0`) y publica `AprendizTrasladadoDeFormacionEvent`. `community` quita del grupo (sus 3 escrituras + chat) y entra a la recepción de la nueva formación;
  `points` reinicia puntos (ajuste del ledger) y la racha. Los registros de hábito, evidencias y publicaciones **no se tocan**.
- **Por qué eventos**: `users` no importa a nadie; `community` y `points` ya importan `users.api`. Cualquier otro módulo (024) escucha el mismo evento.
- **Al instante**: la fila se reinicia en la transacción; el alcance de la persona cambia en su siguiente pedido (Q11-A). La app lo detecta al volver al primer plano y en `GET /me/formacion`.

## 6. Grupos (FR-010)

- **Decisión**: `celulas.formacion` (NOT NULL, FK, default `ESCUELA` para que un APK anterior que crea grupos siga funcionando). Crear/editar un grupo valida que mentor e integrantes sean de
  esa formación (mensaje claro que nombra las dos). La recepción vigente se busca **por formación** (cada formación nueva tiene su grupo de bienvenida, que Administración crea).
- **Mentor con varias formaciones**: su grupo es de una; al cambiar su formación activa «Mi grupo» muestra el de esa formación (y vacío si allí no lidera ninguno).

## 7. Centro «Formaciones» de Administración

- **Decisión**: endpoints en `leadership` (que ya depende de `users`, `community`, `points`, `habits`): lista de 3 tarjetas con cifras y un detalle por formación con Resumen, Personas,
  Grupos y Por atender. La duración y el nombre/descripción se editan con `PUT /admin/formaciones/{codigo}` (control de versión optimista). Cada cifra lleva el filtro que la respalda
  (`formacion=`) en las listas existentes.
- **Ficha y términos / Hábitos y contenido**: dependen de las specs 026, 027, 023; el detalle deja el lugar y por ahora enlaza a «Hábitos del programa» (global hoy).

## 8. Compatibilidad con el APK 1.6.0 (SC-007)

- Todos los campos nuevos son aditivos; las columnas nuevas tienen DEFAULT `ESCUELA` / `90`; el APK anterior no manda `formacion` al crear grupos ni al aceptar y todo cae en la Escuela.
- La app nueva trata la ausencia de `formacion` en `/auth/me` como Escuela.

## 9. Personal que además hace su programa (Q9-A)

- **Decisión**: al elegir su Día 1, la participación toma la formación **activa** del personal en ese momento (o la única que tenga). Administración/Alquimista eligen en el selector de
  «Elegir mi Día 1» (parámetro opcional `formacion`, por defecto Escuela).
