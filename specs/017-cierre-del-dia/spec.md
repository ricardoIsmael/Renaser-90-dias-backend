# Feature Specification: Cierra tu día

**Feature Branch**: `017-cierre-del-dia` (backend y app; la app en la rama `017-cierre-del-dia` del frontend, sobre `015-huecos-de-horario`)
**Created**: 2026-10-29
**Status**: Aprobada por el dueño (vista previa y decisiones del 2026-10-29); lista para implementar
**Corregida**: 2026-10-30 (pedido del dueño: «Cierra tu día» está siempre y no depende de Dormir; ver «Session 2026-10-30» y las notas `> **Corregido 2026-10-30.**`)
**Vista previa aprobada**: `vista-previa.html` (copia local de https://claude.ai/artifact/Lr8upa6x1nswBja6GFxUsc, pestaña «1 · Cierra tu día»)
**Input**: Encargo de gerencia «Cerrar y evaluar mi día» (https://claude.ai/artifact/VCFE5EYjVa6BCWv3iz7ef7). El dueño pidió que
esté en Training «para los aprendices», es decir, para quien hace su programa de 90 días, también el personal que lo hace.

## Clarifications

### Session 2026-10-29 (decisiones del dueño)

- **Quién lo ve.** La persona **y su acompañamiento**: el mentor o la guía vigente de su grupo, el líder de mentores, Administración
  y Alquimista, en la ficha de la persona (como el Radar y el tiempo libre de la spec 015).
- **Es opcional y no da puntos.** Textual de la vista previa: «Nunca resta ni cuenta en el semáforo. Si no lo cierra, ese día solo
  dice «Sin cerrar».» Cerrar el día **no completa ninguna acción, no paga puntos, no toca la coherencia ni la racha**.
- **No se crea una tabla nueva.** Restricción del dueño: «no crear una nueva tabla sino reutilizar, aplicar SOLID y buenas
  prácticas». Por eso: lo de cada acción va en columnas nuevas de `rocas_diarias` (V121) y la marca de «día cerrado» y la nota del
  día en `entradas_diario`, tipo `EVALUACION_DIA` (existe desde `V1` y nadie lo usaba).
- **Un día anterior no se cierra tarde.** Es la regla D-259 aplicada a este caso: lo del día se registra durante su día. El
  servidor responde 409.

### Decisiones técnicas que tomó el equipo (a la vista; el dueño puede cambiarlas)

- **La hora a la que aparece la fila** (30 minutos antes de Dormir de ese día; a las 21:30 si Dormir no tiene hora) **la calcula la
  app** con lo que Training ya lee de hoy. El servidor no la impone: solo exige que la fecha sea la de hoy en la zona de la persona.
  > **Corregido 2026-10-30.** Ya no hay hora: la fila está siempre y es la última de hoy, sin depender de Despertar ni de Dormir (pedido
  > del dueño, ver «Session 2026-10-30»). Se borró `cierre/utils/horaDelCierre` de la app; el servidor no cambió.
- **La nota es por acción pendiente**, como en la vista previa (la caja de texto vive dentro de la tarjeta de la acción). En un día
  sin acciones pendientes hay **una nota del día**. Las dos tienen el mismo tope (500 caracteres).
- **Guardar vuelve a escribirlo todo.** Reabrir y guardar reemplaza las respuestas del día. Una acción que ya se cumplió deja de tener
  respuesta (ya no hay nada que explicar), y la lectura nunca muestra la respuesta de una acción cumplida.
- **La nota del cierre no entra al Espejo Sombra** de SER: `EntradaDiarioFinder` deja de devolver `EVALUACION_DIA`. Nadie pidió que
  una IA analice lo que alguien le cuenta a su mentor; si el dueño lo quiere, es una línea.
- **Leer el cierre de una persona deja constancia** para el líder, Administración y Alquimista (misma tabla `consultas_de_ficha` que la
  ficha semanal y los huecos). Es lo que decidió el dueño para los huecos (spec 015, FR-027) y esto es más sensible.

### Session 2026-10-30 (corrección del dueño: «Cierra tu día» está siempre y no depende de Dormir)

> **Corregido 2026-10-30.** Pedido textual del dueño: «el tema de Dormir, y que salga ahí "Cerrar tu día", está mal. Motivo: personas
> que trabajan en la noche o de día. No debiste juntar lógica de ese lado… como botón: ingresa "Cerrar el día", y es todos los días, y
> debe guardarse y mostrarse en su ficha». Lo que decían la vista previa aprobada (`vista-previa.html`: «Es la última fila de hoy, media
> hora antes de tu hora de Dormir. Si Dormir no tiene hora, sale a las 21:30») y los puntos de esta spec marcados con esta nota **ya no
> rige**. La regla nueva:
>
> - **«Cierra tu día» está SIEMPRE**: todos los días y a cualquier hora, como **la última fila de la lista de hoy en Training**, después
>   de todo lo del día, incluido Dormir.
> - **No depende** de Despertar, de Dormir ni de ninguna hora, y **no lleva hora** (ni «22:00» ni otra calculada).
> - **Todo lo demás de la 017 sigue igual**: lo cumplido llega marcado y solo se pregunta por lo pendiente y por mañana; no da puntos ni
>   cumple acciones y es opcional; ya cerrado, la fila queda en verde; los días anteriores dicen «Cerrado» o «Sin cerrar» y no se cierran
>   tarde (D-259); el cierre se guarda en el servidor y se ve en las fichas del mentor, el líder y Administración («Cierres del día»).
> - **El día sigue siendo el día calendario de la persona** (su zona horaria). Lo que eso significa para quien trabaja de noche
>   (cerrar después de medianoche, cerrar temprano) se **describe** abajo y se le pregunta al dueño; **no se inventó ninguna regla**.
> - **Servidor**: no cambia. Nunca validó ni sugirió una hora (`GET/PUT /rocks/day-closure` solo exigen que la fecha sea la de hoy en la
>   zona de la persona); la hora vivía solo en la app y se borró de ahí.
>
> **Casos descritos (sin regla nueva), con la regla de «hoy = el día calendario de la persona»:**
> 1. *Turno de noche que cierra a las 23:50*: se guarda en el día de hoy (el calendario de su zona) y la fila queda en verde.
> 2. *La misma persona cierra después de la medianoche* (00:30, 02:00): ve el día **nuevo**; la fila «Cierra tu día» es la del día nuevo
>    y cerrar guarda el día nuevo. El día anterior queda «Sin cerrar» y ya no se puede cerrar (409). Una hoja que estaba abierta al
>    pasar la medianoche se cierra y no envía nada. Las fichas dicen «Sin cerrar» para el día anterior y «Cerrado» para el nuevo.
>    **Decisión del dueño (2026-10-30): se queda como está.** Textual: «déjalo como está; con los hábitos, lo de no cruzar el horario ya
>    se soluciona para esas personas». El día es el del calendario y el anterior queda «Sin cerrar». Sin código nuevo.
> 3. *Cierra temprano* (06:00): se puede; el servidor pide una respuesta por cada acción que **siga pendiente** en ese momento, aunque
>    su hora del día todavía no haya llegado (y la hoja la nombra «Era a las 20:30 · quedó pendiente»). Si después cumple algo, el día
>    **sigue cerrado** (verde), la lectura deja de mostrar lo que dijo de la acción cumplida y se puede volver a abrir y cambiar el
>    mismo día.
>    **Decisión del dueño (2026-10-30), dos partes.** (a) *Cerrar temprano: se pregunta SOLO por las acciones cuya hora ya pasó.* Las
>    de hora futura no se preguntan ni se exigen. **Las acciones sin hora se preguntan como hoy.** Cambia también el servidor: ya no
>    exige una respuesta por cada pendiente, sino por cada pendiente cuya `horaInicio` ya llegó en la zona de la persona (con el
>    `Clock`) o que no tiene hora; las de hora futura se aceptan sin respuesta (y si viene una, se guarda). `GET /rocks/day-closure`
>    dice por cuáles preguntar con `exigeRespuesta` por acción (ver `contracts/api.md`), para que la app y el servidor no discrepen
>    por un reloj distinto. La hoja muestra las de hora futura como una línea quieta, «Es a las 20:30 · todavía no llega», sin
>    preguntas. Si la hora llega con la hoja abierta, el servidor responde 400, la hoja relee el día sin perder lo elegido y avisa.
>    (b) *Cerrar temprano y después cumplir una acción: se queda como está.* El cierre sigue verde (cerrado) y se puede reabrir y
>    cambiar el mismo día; lo que dijo de la acción ya cumplida deja de mostrarse.
> 4. *Día normal* (Despertar 06:00, Dormir 22:30) que cierra a las 21:00: cierra el día de hoy y se ve en la ficha.
>
> Las decisiones del dueño sobre estos casos (2026-10-30) están anotadas arriba, en cada caso, y en D-290 (corrección 2026-10-30).
>
> **FR-002, corregido 2026-10-30.** Cerrar exige, por cada acción del día que siga pendiente **y cuya hora de inicio ya llegó (o que no
> tiene hora)**, un motivo y un plan de mañana. Las pendientes de hora futura no se preguntan ni se exigen.

## User Scenarios & Testing

### User Story 1 — Cierro mi día en dos minutos (Priority: P1)

Al final de la tarde, en Training, aparece una fila «Cierra tu día · 2 minutos» distinta de los hábitos. La toco y se abre una hoja
completa. Lo que ya cumplí llega marcado y no se me pregunta nada. Por cada acción que quedó pendiente respondo, con un toque,
«¿Qué pasó?» y «¿Qué harás mañana?», y si quiero escribo una nota. Veo cuántos hábitos hice hoy y cuántas acciones de la semana
llevo, sin preguntarme nada. «Guardar mi cierre» se apaga hasta que respondí las dos preguntas de cada pendiente y una línea dice qué falta.

> **Corregido 2026-10-30.** La fila no espera a la tarde: está siempre, a cualquier hora, como la última de hoy (ver «Session 2026-10-30»).

**Why this priority**: es la funcionalidad; sin ella no hay nada que ver en las fichas.

**Independent Test**: con un día de tres acciones (una cumplida, dos pendientes), `PUT /api/v1/rocks/day-closure` con las dos
respuestas devuelve `CERRADO`, deja las tres acciones con el mismo `completada` de antes y no cambia ningún punto.

**Acceptance Scenarios**:

1. **Given** un día con acciones pendientes, **When** guardo sin responder una de ellas, **Then** el servidor responde 400 y no se
   guarda nada (ni siquiera la marca).
2. **Given** respondí todas, **When** guardo, **Then** cada pendiente queda con su motivo, su plan de mañana y su nota, el día queda
   `CERRADO` con la hora del primer cierre, y ninguna acción cambia de `completada` ni de `puntos_otorgados`.
3. **Given** un día sin acciones, **When** guardo con o sin nota, **Then** queda `CERRADO` (la marca existe aunque no haya nada
   que explicar).
4. **Given** el cierre de mañana en otra zona o en la madrugada UTC, **When** se calcula «hoy», **Then** es el de **la zona de la
   persona** (Lima: a las 03:00 UTC todavía es el día anterior).

### User Story 2 — Vuelvo a abrirlo y lo cambio mientras el día no termine (Priority: P1)

Ya cerrado, la fila queda en verde con «Día cerrado» y lo que dije para mañana. Puedo volver a abrir la hoja y cambiar mis respuestas
mientras el día no termine. Si entre tanto cumplí una de las acciones pendientes, la hoja la muestra marcada y ya no pregunta por ella.

**Acceptance Scenarios**:

1. **Given** un día cerrado, **When** reabro y guardo con otra respuesta, **Then** la respuesta nueva reemplaza a la anterior y
   `cerradoEn` conserva la hora del primer cierre.
2. **Given** cumplí una pendiente después de cerrar, **When** reabro y guardo, **Then** esa acción ya no lleva respuesta.
3. **Given** la medianoche pasó con la hoja abierta, **When** guardo, **Then** el servidor responde 409 («ese día ya cerró») y la
   app recarga y avisa; no se reasigna el cierre a otro día.

### User Story 3 — Veo qué dije en días anteriores (Priority: P2)

En Training, al tocar un día anterior de la semana, la fila dice «Cerrado» o «Sin cerrar» y es solo de lectura. Un día sin cerrar
**no** se puede cerrar tarde.

**Acceptance Scenarios**:

1. **Given** un día anterior sin cierre, **When** lo consulto, **Then** `estado = SIN_CERRAR` y `puedeCerrar = false`.
2. **Given** un día anterior, **When** intento `PUT` con su fecha, **Then** 409.
3. **Given** una fecha futura, **When** la consulto o la quiero cerrar, **Then** 400.

### User Story 4 — Mi acompañamiento ve mis cierres (Priority: P2)

En la ficha de la persona, una sección «Cierres del día» con lo que dijo cada día de la semana (cerrado / sin cerrar / hoy sin
cerrar todavía), las acciones pendientes con su motivo y su plan de mañana, las notas, y los motivos que más se repiten en la semana.

**Acceptance Scenarios**:

1. **Given** el mentor o la guía **vigente** del grupo, **When** pide `.../cierres?semana=`, **Then** recibe la semana; cualquier otro
   rol, un grupo ajeno o una cuenta suspendida reciben 403.
2. **Given** el líder, Administración o Alquimista, **When** pide la ruta del liderazgo, **Then** recibe lo mismo y queda una fila en
   `consultas_de_ficha`; una lectura que falla o se rechaza no deja nada.
3. **Given** una semana futura, anterior a la primera de la persona, o un día que no es lunes, **Then** 400.
4. **Given** la persona no eligió su Día 1, **Then** la semana viene sin días (no se inventa un programa).

### Edge Cases

- **Dormir sin hora o sin Dormir hoy** (pausado, no le toca): la fila sale a las 21:30. **Dormir a las 00:10** (se acuesta pasada la
  medianoche): 30 minutos antes sería el día anterior, así que la fila se ancla a las 00:00 de ese día (siempre disponible). Lo calcula la app.
  > **Corregido 2026-10-30.** Ya no aplica: no hay hora, así que Dormir (con hora, sin hora, pausado o pasada la medianoche) no cambia
  > nada. La fila está siempre.
- **Una acción cumplida después de cerrar**: se ve cumplida; su respuesta vieja queda en la base pero la lectura no la muestra.
- **Acción sin hora**: la hoja dice solo «Quedó pendiente».
- **Cuenta suspendida o personal sin su programa activado**: 403, como cualquier operación de rocas.
- **Doble envío / reintento por red**: `PUT` idempotente (reemplaza lo mismo por lo mismo).
- **Interruptor `plan` apagado por Soporte TI**: las rutas de la persona responden 503 y la fila no se muestra.

## Requirements

### Functional Requirements

- **FR-001**: Una persona que hace su programa (aprendiz, o personal con su programa activado) puede consultar y cerrar **el día de hoy**
  en su zona. No se puede cerrar otro día (409 pasado, 400 futuro).
- **FR-002**: Cerrar exige, por **cada acción del día que siga pendiente**, un motivo (`SIN_TIEMPO`, `SURGIO_OTRA_COSA`, `SIN_ENERGIA`,
  `PARA_DESPUES`) y un plan de mañana (`OTRA_HORA`, `MAS_CORTA`, `PEDIR_AYUDA`, `DEJARLA`); la nota (≤ 500 caracteres) es opcional.
- **FR-003**: Cerrar **nunca** completa una acción, paga puntos, ajusta puntos, cuenta en la coherencia, el semáforo o la racha, ni
  publica eventos de dominio. Lo garantiza la forma de guardar: solo se escriben las columnas de cierre.
- **FR-004**: La marca «día cerrado» es una fila `EVALUACION_DIA` de `entradas_diario` (única por persona y fecha) con la nota del día;
  su `creado_en` es la hora del primer cierre.
- **FR-005**: Un día sin cerrar se dice `SIN_CERRAR` si ya terminó y `ABIERTO` si es hoy. Nada lo penaliza.
- **FR-006**: El acompañamiento lee los cierres de una **semana** (lunes a domingo, hasta hoy y desde el Día 1 de la persona) con el
  guardián de cada ruta: mentor o guía vigente del grupo (alumno de ese grupo), o `AccesoDeLiderazgo` (grupo regular con mentor,
  persona de ese grupo). Solo lectura. La ruta del liderazgo deja constancia de la consulta (`consultas_de_ficha`).
- **FR-007**: Las rutas respetan los interruptores de Soporte TI (`plan` para la persona, `mentor.mi_grupo` y `lider.cumplimiento`
  para el acompañamiento), declaran `@RequiresPermission`, responden `Cache-Control: no-store` en las lecturas del acompañamiento y no
  loguean el texto de las notas.
- **FR-008**: Ninguna tabla nueva. Una migración (V121) agrega a `rocas_diarias` cuatro columnas nulas con `CHECK` de los valores.
- **FR-009**: El texto que escribe la persona **no entra** al Espejo Sombra (`EntradaDiarioFinder` excluye `EVALUACION_DIA`).
- **FR-010 (app)**: Training muestra la fila «Cierra tu día · 2 minutos» desde 30 minutos antes de Dormir (21:30 sin Dormir), la
  última de hoy, con fondo dorado suave y la luna; no cuenta en «N de M hechos» ni en puntos; no se «atrasa». Cerrada, queda verde con
  «Día cerrado» y lo de mañana.
  > **Corregido 2026-10-30.** Training muestra la fila «Cierra tu día · 2 minutos» **siempre** (todos los días, a cualquier hora), como la
  > **última fila de la lista de hoy**, después de todo lo del día (también de Dormir y de lo hecho), **sin hora** y sin depender de
  > Despertar, de Dormir ni de ninguna hora. El resto (fondo dorado suave, luna, no cuenta, no se atrasa, verde al cerrar) no cambia.
- **FR-011 (app)**: La hoja (completa) muestra lo cumplido marcado, por cada pendiente «¿Qué pasó?» y «¿Qué harás mañana?» con chips y una
  nota, «Tus hábitos: N de M hechos hoy», «Tu semana: N de M acciones cumplidas hasta hoy» (solo lectura), y «Guardar mi cierre», que
  se apaga hasta que cada pendiente tenga sus dos chips y dice qué falta. Al guardar: «Día cerrado», lo de mañana y «Ver mañana en
  Training». Un día sin acciones dice «Hoy no tenías acciones».
- **FR-012 (app)**: Un día anterior es de solo lectura: «Cerrado» o «Sin cerrar». El rechazo 409 recarga y avisa; no reasigna el cierre.
- **FR-013 (app)**: En las fichas del mentor/guía, del líder y de Administración, una sección «Cierres del día» (solo lectura).
- **FR-014**: Textos en español de Perú con tuteo, tipo oración; el movimiento es mínimo (fundido al guardar, `tacto.logro()` una vez,
  «reducir movimiento» respetado).

### Key Entities

- **Respuesta de cierre de una acción** (`rocas_diarias`): `cierre_motivo`, `cierre_manana`, `cierre_nota`, `cierre_en`. Solo existe
  para una acción pendiente; las cuatro vienen juntas o ninguna.
- **Cierre del día** (`entradas_diario`, `EVALUACION_DIA`): la marca de que la persona cerró ese día, con la nota del día.

## Success Criteria

- **SC-001**: Guardar el cierre con respuestas completas deja `completada`, `completada_en` y `puntos_otorgados` de todas las
  acciones **exactamente** como estaban (prueba de dominio y de servicio).
- **SC-002**: Todo comportamiento diario se prueba con el reloj en la madrugada UTC (día local anterior en Lima).
- **SC-003**: Cada ruta nueva pasa `EndpointAuthorizationDeclarationTest`, `FuncionalidadDeclaradaTest`,
  `RutasCubiertasPorElFiltroTest` y `ArchitectureTest`, y tiene su prueba de autorización negativa.
- **SC-004**: La app nueva contra el backend anterior no muestra la fila (404) y no rompe Training; el APK anterior contra el backend
  nuevo no nota nada.

## Assumptions

- La zona de la persona es la de `participantes_programa.timezone` (hoy todas son Lima).
- «Acciones del día» son las `rocas_diarias` de esa fecha (hasta 9: 3 ejes × 3), ordenadas por hora de inicio y posición.
- «Tu semana: N de M» usa la semana de programa de la persona (`SemanaPrograma`, D-203) y cuenta las acciones planificadas de los
  días ya vividos (hoy incluido) y cuántas se cumplieron: la misma cuenta que `progresoSemanalPct` del tablero.
- Alcance fuera de esta spec: avisar al mentor del cierre por el chat, convertir «Hacerla a otra hora» en un cambio de hora
  automático, sumar el cierre al semáforo o a los puntos, y la Política de Privacidad (el texto que la persona escribe en el cierre
  debe sumarse a la página antes de publicar, como pendía con los huecos).
