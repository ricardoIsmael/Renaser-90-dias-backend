# Feature Specification: Vista del líder, limpia

**Feature Branch**: `018-lider-limpio` (backend y app, una rama en cada repo; se apoya en las specs 005, 015 y 016)
**Created**: 2026-10-29
**Status**: Aprobada por el dueño (vista previa `vista-previa.html`, pestaña «3 · Líder de mentores»)
**Input**: Comentario del equipo que el dueño pegó, textual: «[la líder] tiene mucho para ingresar, debería de verse por
aprender, ingreso y como mentora abre y aprendices, mentores y resultado por grupos» y «cuando le hago clic en persona
ver si programaron, si subieron evidencias». Del dueño: «falta que esté un poco más limpio y no mucho texto».
Vista previa: https://claude.ai/artifact/Lr8upa6x1nswBja6GFxUsc

## Clarifications

### Session 2026-10-29 (decisiones del dueño)

- **Hoy de quien tiene más de un papel**: arriba de las tarjetas, un control con **«Mi programa» · «Mi grupo» · «Mis
  mentores»**. Solo salen los papeles que le tocan; **con un solo papel no hay control**. El aprendiz y el mentor que
  solo es mentor ven Hoy **exactamente como hoy**.
- **«Mi programa»** es lo de siempre del participante. **«Mi grupo»** es la lista de sus aprendices con foto, color y %,
  y tocar a uno abre la ficha que ya existe. **«Mis mentores»** es el resultado por grupo (una fila por grupo con nombre,
  mentor, color y % de los últimos 7 días) y una fila «Consultas de mentores · N sin responder», **sin textos de ayuda**.
- **La ficha de la persona** abre con dos respuestas en dos tarjetas iguales: **«¿Programó?»** (Sí / No) y **«¿Subió
  evidencias?»** («N de M»).
- **¿Qué cuenta como «programó»?** El dueño eligió **«Horas y acciones»**: que cuente las dos cosas, la hora de sus
  hábitos y las acciones agendadas de hoy.
- **Menos texto** en las pantallas del líder: se quitan los párrafos de ayuda que repiten lo que ya se ve, sin quitar
  datos ni funciones.
- **Un comentario que se ignora**: «por el momento no debería haber grupos». El dueño dice que «ahora son pocos alumnos y
  están decidiendo quién será el mentor», así que **no se cambia nada por eso**.

### Lo que hay hoy (verificado el 2026-10-29)

- **Hoy** (`HoyScreen`): las banderas son `esMentor`, `esLider` y `programaPropioActivo`. Las tarjetas van en este
  orden: administración, «Mi grupo» del mentor, tres tarjetas sueltas del líder (**«Mis mentores», «Tickets de
  mentoría» y «Semáforo por grupos»**), el programa propio, el Mapa, hábitos y Radar, semáforo, acciones, la última
  evidencia y el próximo evento. El líder baja por todas para cambiar de papel.
- **La ficha de la persona** (`PersonaDelLiderScreen`, spec 005; la usa también Administración con el mismo endpoint): el
  encabezado, los 7 días (cada uno una cabecera que se abre), el Código Renaser, el tiempo libre y las acciones.
- **No existía ningún dato de «programó»**: `habits.api.HorarioDelDiaFinder` ya trae por hábito `personalizado` (la hora
  la eligió la persona y no vino del catálogo), pero solo lo usaba `rag`; `rocks` guarda `hora_inicio` en cada acción del
  día pero la API pública no la exponía.
- **No existía un conteo de evidencias**: sale de `obligaciones[].requiereEvidencia / entrega / evidencias[]` que la
  ficha ya trae.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Hoy: elegir el papel (Priority: P1)

1. **Given** una líder que además acompaña un grupo y hace su programa, **When** abre Hoy, **Then** ve arriba el control
   con las tres opciones y, debajo, lo del papel elegido. La primera vez abre en «Mi programa».
2. **Given** que elige «Mis mentores», **When** mira la lista, **Then** ve una fila por grupo con nombre, mentor, punto
   de color y porcentaje de los últimos 7 días, y debajo «Consultas de mentores» con «N sin responder» y «Mentores y
   reporte del mes».
3. **Given** una fila de grupo, **When** la toca, **Then** se abre la lista de personas de ese grupo
   (`CumplimientoDelLiderScreen`), en los mismos 7 días de la fila; volver sale a Hoy. Un grupo sin mentor no se abre y su
   fila lo dice en rojo.
4. **Given** «Consultas de mentores», **When** la toca, **Then** abre la bandeja de tickets de siempre.
5. **Given** que elige «Mi grupo», **When** toca a un aprendiz, **Then** abre su ficha de siempre (`AlumnoScreen`); volver
   regresa a Hoy, a «Mi grupo».
6. **Given** que cambia de opción, **When** toca otra, **Then** lo de abajo cambia **en el acto**, sin animación, y la
   próxima vez que abra Hoy se queda en la última opción que eligió (por cuenta).
7. **Given** un aprendiz, un mentor, una guía o cualquiera que no lidera mentores, **When** abre Hoy, **Then** no hay
   control y Hoy es exactamente el de hoy.
8. **Given** una líder sin grupo, **When** abre Hoy, **Then** el control trae «Mi programa» y «Mis mentores», sin «Mi
   grupo».

### User Story 2 - La ficha de la persona: dos respuestas arriba (Priority: P1)

1. **Given** la ficha de una persona, **When** la abro, **Then** arriba, en dos tarjetas iguales, veo «¿Programó?» con
   «Sí» o «No» y «5 de 17 hábitos con su hora · 0 de 2 acciones agendadas», y «¿Subió evidencias?» con «4 de 12» y «Estos 7
   días». El detalle de los días va debajo, igual que antes.
2. **Given** un servidor anterior (sin el campo nuevo), **When** abro la ficha, **Then** no hay tarjeta de «¿Programó?» y
   la ficha es la de antes más la de evidencias.
3. **Given** una persona sin hábitos que le toquen hoy, o que aún no empezó su programa, **When** abro su ficha, **Then**
   no se dibuja «¿Programó?»: nunca un «No» por falta de datos.
4. **Given** que ningún hábito de esos días pedía evidencia, **When** abro la ficha, **Then** no se dibuja «¿Subió
   evidencias?»: nunca un «0 de 0».
5. **Given** Administración o Alquimista, **When** abren la ficha de una persona desde el semáforo, **Then** ven las mismas
   dos tarjetas (es la misma pantalla y el mismo endpoint).

### User Story 3 - Menos texto (Priority: P2)

1. **Given** las pantallas del líder, **When** las recorro, **Then** ya no están los párrafos de ayuda de la tabla de
   abajo; todos los datos y todas las funciones siguen.

#### Textos que se quitan, y por qué

| Dónde | Texto quitado | Por qué |
|---|---|---|
| Hoy, tarjeta «Mis mentores» | «Tu cargo» · «Mis mentores» · «Cómo va cada uno y el reporte del mes.» | La tarjeta ya no existe: es la opción «Mis mentores» y la fila «Mentores y reporte del mes». |
| Hoy, tarjeta de tickets | «Acompañamiento» · «Tickets de mentoría» · «Los bloqueos abiertos en toda la plataforma y qué se respondió. Solo para mirar.» | La tarjeta ya no existe: es la fila «Consultas de mentores · N sin responder». |
| Hoy, tarjeta del semáforo por grupos | «Semáforo» · «Semáforo por grupos» · «Cuántos aprendices de cada grupo van al día, requieren atención o tienen problemas.» | La tarjeta ya no existe: es la lista «Resultado por grupo». |
| `SemaforoGruposScreen` | «Toca un grupo para ver a sus personas.» / «Solo cantidades: a cada aprendiz lo acompaña y lo ve su mentor.» | La tarjeta que se abre lleva su chevron y la que no, no lo lleva: la línea repetía lo que ya se ve. |
| `GrupoDelLiderScreen` | «Primero quienes más lo necesitan. Toca a una persona para ver su semana.» | El orden se ve en los colores y cada fila lleva su chevron. |
| `PersonaDelLiderScreen` | «Le deja a {mentor} una observación sobre {persona} y los días que estás viendo.» | El botón dice «Avisar a su mentor» y la pantalla que abre dice «Avisar a …» y «Sobre …». |
| `PersonaDelLiderScreen` | «{nombre} lleva su propio programa y no se mide en el semáforo: no hay días que mostrar.» | El encabezado ya dice «Mentor · No se mide en el semáforo». |
| `PersonaDelLiderScreen` | «· N con evidencia» al final del resumen de la semana | El dato pasa a la tarjeta «¿Subió evidencias?»; dos cifras de evidencia distintas en la misma pantalla se leían como una contradicción. |
| `ReporteScreen` (parte de `LiderMentoresScreen`) | «De mayor a menor evaluación.» | El orden se ve. |
| `ObservarScreen` | La línea «Lo que hizo bien.» / «Lo que podría cambiar.» / «Lo que va mal y hay que atender.» sobre el campo | Pasa a ser el texto de relleno del campo: la ayuda sigue, en un renglón menos, y se va sola al escribir. |
| `ObservarScreen` | «Le llega como mensaje tuyo, …» (subtítulo de la casilla) | La casilla dice «Enviárselo también por el chat». |

**Lo que NO se quita**: los datos (cortes de fecha, conteos, porcentajes, estados, motivos de un día sin medir), los
mensajes de estado vacío, de carga y de error, ni nada de Administración (`SemaforoAdminScreen` conserva su propia línea de
ayuda: no es una pantalla del líder).

### Edge Cases

- **Líder sin ningún grupo ni mentores**: queda con «Mi programa» solo; sin control.
- **«Mi grupo» recordado y el servidor aún no responde si acompaña uno**: Hoy espera esos instantes en vez de dibujar «Mi
  programa» y saltar al grupo.
- **Lo recordado ya no le corresponde** (dejó de acompañar el grupo): abre en «Mi programa».
- **Interruptor apagado por Soporte TI** (`lider`, `tickets`, `semaforo`, `mentor.mi_grupo`): esa fila o esa opción no se
  ofrece; con las tres de «Mis mentores» apagadas, la opción no aparece.
- **404 o 403 del resumen por grupos**: la lista de grupos desaparece, las filas de consultas y de mentores siguen.
- **Backend anterior**: sin `programacion` no hay tarjeta de «¿Programó?»; todo lo demás funciona.
- **Pantalla angosta**: las dos tarjetas de la ficha pasan a una debajo de la otra antes de apretarse.
- **Cambiar de cuenta en el mismo teléfono**: cada cuenta recuerda su opción; no se ve la de la anterior.
- **Aviso del sábado `/semaforo/grupos`**: sigue abriendo el resumen por grupos de siempre (con sus totales), no un grupo.

## Requirements *(mandatory)*

### Hoy

- **FR-001**: Hoy MUST mostrar un `ControlSegmentado` con «Mi programa» · «Mi grupo» · «Mis mentores» **solo** a quien
  lidera mentores y tiene al menos dos papeles disponibles. Para cualquier otra cuenta Hoy MUST ser el de hoy, sin control.
- **FR-002**: Cada opción MUST aparecer solo si la cuenta la tiene: «Mi programa» si hace su programa; «Mi grupo» si el
  servidor dice que acompaña un grupo (o falló al decirlo, para poder reintentar) y la entrada está prendida; «Mis mentores»
  si alguna de sus tres entradas está prendida. La decisión es una función pura (`papelesDeHoy`).
- **FR-003**: «Mi programa» MUST ser lo de siempre del participante. Con otro papel a la vista, lo del programa propio
  MUST quedar oculto pero montado (volver es instantáneo y no pierde la conversación del orbe).
- **FR-004**: «Mi grupo» MUST listar a los aprendices del grupo con foto, nombre, punto de color y porcentaje del
  semáforo del grupo (`GET /mentor/groups/{g}/semaforo`), en el orden del servidor; tocar a uno MUST abrir `AlumnoScreen`
  y volver MUST regresar a Hoy. Si el semáforo no está, MUST listar los nombres sin color.
- **FR-005**: «Mis mentores» MUST listar los grupos del resumen por grupos (`GET /semaforo/groups`) con nombre, mentor,
  color y % de los últimos 7 días; un grupo con `puedeAbrirse` abre `CumplimientoDelLiderScreen` directo en ese grupo, en
  los mismos 7 días; uno sin mentor no se abre y lo dice. Debajo, «Consultas de mentores» (con «N sin responder» de la
  primera página de la bandeja, o «N+» si hay más páginas) abre la bandeja, y «Mentores y reporte del mes» abre
  `LiderMentoresScreen`.
- **FR-006**: El control MUST cambiar lo de abajo en el acto y sin animación, y MUST recordar la última opción **por
  cuenta** (AsyncStorage). Un almacenamiento roto MUST NOT tumbar Hoy.
- **FR-007**: Las tres tarjetas sueltas del líder (tickets, semáforo por grupos y «Mis mentores») MUST dejar de estar en
  Hoy; sus pantallas se abren desde las filas de «Mis mentores». El aviso `/semaforo/grupos` MUST seguir abriendo el
  resumen por grupos.

### La ficha de la persona

- **FR-008**: `GET /leadership/groups/{g}/people/{p}/week` MUST traer un campo opcional nuevo `programacion`
  `{habitosConHora, habitosTotal, accionesConHora, accionesTotal, programo}` (contrato en
  `contracts/leadership-programacion.md`). Es **aditivo**: un cliente anterior lo ignora.
- **FR-009**: La programación MUST ser la de **hoy** en la zona de la persona (regla 02), no la de la ventana de 7 días. Los
  hábitos son los que le tocan hoy (ni apagados ni en pausa); «con su hora» es la hora que ella eligió
  (`HorarioResuelto.personalizado`), no la del catálogo; las acciones son las de sus objetivos para hoy y «agendada» es
  tener hora de inicio.
- **FR-010**: `programo` MUST decidirlo el servidor, en el dominio (`ProgramacionDelDia`): **al menos la mitad de sus hábitos
  con hora propia y todas las acciones de hoy agendadas**. Sin hábitos que le toquen hoy, o sin programa activado, el campo
  MUST ser `null`.
- **FR-011**: La ficha MUST mostrar arriba dos tarjetas iguales: «¿Programó?» (Sí / No con sus cuentas) y «¿Subió
  evidencias?» («N de M»: de los hábitos de los días cerrados de la ficha que piden evidencia, cuántos tienen entrega,
  archivo o publicación). Cada una MUST ocultarse si no tiene de dónde salir; el «Sí» y el «No» MUST llevar ícono además del
  color.
- **FR-012**: La ficha de Administración MUST mostrar lo mismo (misma pantalla).
- **FR-013**: Cada lectura de la ficha MUST seguir dejando su registro de consulta (FR-016 de la spec 005) y el 403/404 de
  siempre: rol sin permiso, persona de otro grupo y cuenta suspendida MUST NOT disparar ninguna lectura de hábitos.

### Menos texto

- **FR-014**: Las pantallas `SemaforoGruposScreen`, `GrupoDelLiderScreen`, `PersonaDelLiderScreen` y `LiderMentoresScreen`
  (con `ReporteScreen` y `ObservarScreen`) MUST quitar los textos de la tabla de la US3, sin quitar datos ni funciones.

### Transversales

- **FR-015**: Reglas de §2 a §6 de `AGENTS.md`: un solo scroll, áreas de 48, tipo oración, claro y oscuro, retroceso del
  sistema, tuteo de Perú. Cada respuesta lleva su forma (ícono, palabra) además del color.
- **FR-016**: Sin pedidos nuevos que se repitan: «Mi grupo» y «Mis mentores» reusan las lecturas que Hoy ya hacía para el
  mentor y el líder; la bandeja se lee solo con «Mis mentores» a la vista.

## Success Criteria *(mandatory)*

- **SC-001**: En el emulador, las 8 historias de la US1 hacen lo que dice la tabla, a 390 y a 360 dp, en claro y oscuro.
- **SC-002**: Un aprendiz, un mentor y una guía abren Hoy sin un solo cambio visible (sin control, mismas tarjetas).
- **SC-003**: Al tocar a una persona, «¿Programó?» y «¿Subió evidencias?» se ven sin desplazar la pantalla en un teléfono de
  360 × 640.
- **SC-004**: Ningún endpoint nuevo; el endpoint de la ficha sigue respondiendo 403 / 404 como antes y las pruebas de
  autorización negativa pasan.

## Assumptions

- **Umbral de «Sí»** *(propuesto por el equipo; a confirmar con el dueño)*: al menos **la mitad** de los hábitos de hoy con
  hora propia **y** todas las acciones de hoy con hora. Los números viven en un solo lugar
  (`ProgramacionDelDia.programo()`); cambiarlos es editar esa clase y sus pruebas, sin tocar la app.
- **Sin acciones hoy**, la parte de las acciones se cumple sola: la decide lo que sí tiene. No se castiga lo que no se
  planificó; planificar mañana ya es otra pregunta (la ventana de planificación de Plan).
- **La programación es de hoy**, no de los 7 días: «programar» es armar el día; lo de ayer está en los días de abajo.
  A las 6 de la mañana, antes de que la persona arme su día, puede decir «No»: es la foto de ese momento.
- **Quién ve el control**: solo el líder de mentores. Es el único rol con tres papeles; el mentor y la guía ya tienen su
  «Mi grupo» como tarjeta y el pedido no los toca («exactamente como hoy»). Un cargo de Administración o Alquimista tiene
  su propia entrada y tampoco.
- **La primera vez abre en «Mi programa»** (la preferencia queda desde que elige): Hoy sigue siendo su día. La vista previa
  muestra «Mis mentores» porque era lo que había que mostrar.
- **Con otro papel a la vista** se esconde también lo demás del programa propio (frase del día, coherencia, racha, orbe y
  tarjetas), no solo las tarjetas: la vista previa muestra solo la línea de fase y día. La línea de fase, día y puntos queda
  siempre.
- **Una fila más que la vista previa no muestra**: «Mentores y reporte del mes», para que el padrón, la ficha del mentor, las
  observaciones y el reporte del mes (todo `LiderMentoresScreen`) sigan a un toque ahora que la tarjeta «Mis mentores» se
  fue. Sin ella, esas funciones quedaban sin entrada.
- **El resumen con los totales entre grupos** (`SemaforoGruposScreen`) deja de tener tarjeta propia: se abre con el aviso del
  sábado. Las cantidades por color de cada grupo se ven un nivel más adentro (la lista del grupo) y las flechas de semana
  siguen ahí.
- **Las acciones se dicen «N de M»**, no solo «N acciones agendadas» como en la vista previa: el total hace que un «No» se
  explique (1 de 3 sin hora).
- **Las evidencias** cuentan los hábitos que piden evidencia de los días **cerrados** de la ventana (hoy y los días por venir
  no cuentan: lo que aún se puede entregar no es una falta) y se consideran entregadas con `entrega = ENTREGADA`, con algún
  archivo o texto subido, o con la publicación del Muro.
- **El conteo de «sin responder»** sale de la primera página de la bandeja (30 tickets). Si hay más páginas se dice «N+» y,
  si en la primera no hay ninguno, no se afirma que no hay.
- **«Mi grupo» y «Mis mentores» no cambian ningún permiso**: el servidor sigue autorizando cada lectura.
- Se construye sobre las specs 005, 015 y 016. Hace falta publicar el backend antes que el APK solo para ver «¿Programó?»;
  un APK nuevo contra un backend anterior funciona sin esa tarjeta.
- **Cambio de tab**: el pedido del dueño del 2026-10-29 autoriza estos cambios en Hoy y en la ficha del líder; se anota en
  `AGENTS.md` de la app. No hay migración (la base no cambia).
- Todo se prueba en local, con base clonada y build debug; nunca contra producción.
