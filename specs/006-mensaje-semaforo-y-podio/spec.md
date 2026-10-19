# Feature Specification: Mensaje del semáforo con sticker y descarga del podio

**Feature Branch**: `006-mensaje-semaforo-y-podio` (sin rama todavía: spec-kit no crea ramas en este repo)

**Created**: 2026-10-19

**Status**: Draft

**Input**: Retroalimentación del dueño del producto: «1. El mensaje que acompaña los semáforos tiene que ser mucho más
personalizado, no solo que muestre el porcentaje avanzado en el día. 2. Que haya opción para descargar en los podios.»

**Repos**: backend (`Renaser-90-dias-backend`, esta spec) y app (`Renaser-90-dias-frontend-`). La spec vive solo acá;
el frontend la referencia desde `specs/README.md` sin copiarla.

## Contexto

- **Semáforo (tarjeta de Hoy)**: hoy muestra la palabra del color («Al día», «Requiere atención», «Con problemas»), el
  porcentaje de los últimos 7 días y «N de 7 días con datos». No le habla a la persona.
- **Tarjeta diaria del semáforo en el chat de soporte (D-223)**: a las 23:50 de cada aprendiz el programa le manda en su
  chat de soporte la imagen del color de su día y la línea «Hoy llevas N % de tus hábitos.». Tampoco le habla a la
  persona: es solo el porcentaje.
- **Podio del Ranking (Comunidad)**: tres tablas (General, Coherencia, Kilómetros) con un podio de los tres primeros y la
  lista debajo. No hay forma de sacarlo de la app.

## Clarifications

### Session 2026-10-19

Respuestas del dueño en la conversación de diseño:

- Q: ¿Dónde aparece el mensaje? → A: En la tarjeta del semáforo de Hoy, acompañado de un sticker del búho para que se
  sienta humano.
- Q: ¿Quién lo redacta? → A: Reglas en el servidor (sin IA), sobre un catálogo de mensajes escritos de antemano que se
  eligen al azar.
- Q: ¿Con qué datos se personaliza? → A: Nombre, día y fase; el hábito que más le cuesta y el que mejor le va; cuánto
  le falta para el siguiente color.
- Q: ¿Cada cuánto cambia? → A: Una vez al día.
- Q: ¿Tono en rojo? → A: Firme con cariño: exigente, sin humillar.
- Q: ¿Los stickers nuevos llevan texto? → A: No, solo el búho; el mensaje va escrito al lado.
- Q: ¿Los días sin color llevan mensaje? → A: Sí, el mensaje sale todos los días; los días sin color (inicio, sin
  programa, pausa, cuenta en pausa, graduado) tienen mensajes propios.
- Q: ¿Qué se descarga del podio? → A: Una imagen para compartir.
- Q: ¿Quién puede descargarla? → A: Solo Líder de mentores, Alquimista, Administrador y Soporte TI.
- Q: ¿Formato y nombres? → A: Vertical 9:16, nombre completo.
- Q: ¿Cómo sale del teléfono? → A: Con la hoja de compartir del sistema (WhatsApp, Instagram, «Guardar imagen»).
- Q: «Te faltan N», ¿qué cuenta? → A: Lo mismo que el semáforo: hábitos + objetivos (regla D-168, vigente desde el
  2026-09-25; la coherencia es otra cifra, solo de objetivos, D-128). El semáforo no cambia.
- Q: ¿Esperamos la fase 2 de Soporte TI? → A: No: la fase 2 quedó en pausa; se empieza ya. El rol `IT_SUPPORT` (fase 1)
  ya está en `origin/master` y recibe el permiso desde el inicio.
- Q: ¿Se confirman la ventana de 14 días, el mensaje nuevo si cambia el color, los datos actualizados en cada lectura y
  la descarga como archivo en la web? → A: Sí, las cuatro.
- No cambian: el aviso del sábado del semáforo y lo que ve el mentor de cada aprendiz (fuera de alcance).

### Session 2026-10-19 (tarde): el chat diario — «Porcentaje + mensaje» (D-281)

El dueño se quejó de que el mensaje que acompaña al semáforo «solo dice el porcentaje» y, para la tarjeta diaria del chat
de soporte, eligió entre las opciones que se le presentaron:

- Q: ¿Qué lleva el texto de la tarjeta diaria del chat? → A: **«Porcentaje + mensaje»**: «Hoy llevas N % de tus
  hábitos.», una línea en blanco y un mensaje personalizado del MISMO catálogo que el de Hoy. La imagen del color
  queda exactamente como hoy.
- Q: ¿Se manda sticker al chat? → A: No. Solo texto.

Reglas de diseño que fijó la sesión que coordina el trabajo para hacerlo posible sin contradecir lo anterior (se
documentan para que nadie las «arregle» de vuelta; el dueño puede cambiarlas):

- El mensaje es del grupo del **color del día de la tarjeta** (VERDE, AMARILLO o ROJO), no del de la ventana de 7 días
  que muestra Hoy: el chat habla del día que acaba de terminar.
- Variables permitidas: `{nombre}`, `{dia}`, `{fase}`, `{habitoFlojo}` y `{habitoFuerte}`. **`{faltan}` no**: a las
  23:50 el día ya casi cerró y «te faltan 2 para subir» sería falso o inútil. Los mensajes que lo piden no son
  candidatos en el chat. Los hábitos flojo y fuerte siguen la misma regla que en Hoy (ventana vigente).
- El mensaje del chat es **distinto del que la persona vio en Hoy ese día**, y la regla de no repetir en 14 días
  considera **los dos canales** (lo visto en Hoy cuenta para el chat y al revés).
- Si no hay mensaje (catálogo sin candidatos, error al elegir), sale el texto de siempre: **el chat nunca falla por el
  mensaje**.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Un mensaje del día que le habla a la persona (Priority: P1)

Una aprendiz abre Hoy y, junto a su semáforo, ve un sticker del búho y una frase que la nombra y le dice algo concreto
sobre su semana: si va bien, la reta a sostenerlo; si va regular, le marca qué hacer hoy («Te faltan 2 para subir al
siguiente color…»); si va mal, se lo dice con firmeza y le da una salida.

**Why this priority**: Es el pedido principal del dueño: que el semáforo motive y no sea solo un número.

**Independent Test**: Con una cuenta de prueba en cada color, abrir Hoy y comprobar que aparece un sticker y un mensaje
del color correcto, con sus datos reemplazados, y que al volver a abrir Hoy el mismo día el mensaje es el mismo.

**Acceptance Scenarios**:

1. **Given** una aprendiz en VERDE, **When** abre Hoy, **Then** ve un sticker y un mensaje del grupo VERDE con sus
   datos (por ejemplo su nombre) ya escritos, sin llaves ni huecos.
2. **Given** una aprendiz en AMARILLO a la que le faltan 2 hábitos hoy para subir de color, **When** abre Hoy,
   **Then** el mensaje es del grupo AMARILLO y, si menciona cuántos faltan, dice 2.
3. **Given** que la aprendiz ya vio su mensaje hoy, **When** vuelve a abrir Hoy más tarde el mismo día (sin cambiar de
   color), **Then** ve el mismo mensaje y el mismo sticker.
4. **Given** que la aprendiz vio un mensaje ayer, **When** abre Hoy al día siguiente, **Then** ve otro mensaje, que no
   se repite con los de sus días recientes.
5. **Given** una persona que no tiene datos de su hábito más flojo, **When** se elige su mensaje, **Then** nunca recibe
   un mensaje que necesite ese dato.

---

### User Story 2 - Mensaje también en los días sin color (Priority: P2)

Quien recién empieza, quien no programó nada esa semana, quien está en pausa, quien vuelve después de tener la cuenta en
pausa y quien ya terminó los 90 días también ve un sticker y un mensaje adecuado a su situación, sin reproches.

**Why this priority**: El dueño quiere un mensaje todos los días; sin esto, esos días la tarjeta quedaría muda.

**Independent Test**: Con cuentas en cada uno de los cinco casos, abrir Hoy y comprobar que el mensaje corresponde al
caso.

**Acceptance Scenarios**:

1. **Given** una persona en pausa, **When** abre Hoy, **Then** ve un mensaje del caso PAUSA, comprensivo y sin presión.
2. **Given** una persona después del día 90, **When** abre Hoy, **Then** ve un mensaje del caso GRADUADO con un sticker
   dorado.
3. **Given** una persona sin nada programado esa semana, **When** abre Hoy, **Then** ve un mensaje del caso
   SIN_PROGRAMA que la invita a planificar.

---

### User Story 3 - Descargar la imagen del podio para compartir (Priority: P2)

Una administradora entra a Comunidad → Ranking, elige la tabla (General, Coherencia o Kilómetros) y toca «Descargar».
Se arma una imagen vertical con el logo, la fecha, el nombre de la tabla, el podio de los tres primeros y del 4.º al
10.º, con nombre completo y puntaje. Se abre la hoja de compartir del teléfono para mandarla por WhatsApp, subirla a
historias o guardarla.

**Why this priority**: Segundo pedido del dueño; sirve para reconocer públicamente a los primeros y motivar a la
comunidad.

**Independent Test**: Con una cuenta Administrador, generar la imagen de cada tabla y compartirla; con una cuenta
Aprendiz o Mentor, comprobar que el botón no aparece.

**Acceptance Scenarios**:

1. **Given** una cuenta Administrador, Alquimista, Líder de mentores o Soporte TI en Ranking, **When** toca
   «Descargar», **Then** se genera la imagen de la tabla que está mirando y se abre la hoja de compartir del teléfono.
2. **Given** una cuenta Aprendiz, Guía de aprendiz o Mentor, **When** entra a Ranking, **Then** no ve la opción de
   descargar.
3. **Given** una tabla con menos de 10 personas con puntaje, **When** se genera la imagen, **Then** salen solo las que
   hay; los lugares del podio sin persona se ven vacíos, como en la app.
4. **Given** que la persona cancela la hoja de compartir, **When** vuelve a la app, **Then** sigue en Ranking sin
   errores.

---

### User Story 4 - El chat de las 23:50 también le habla (Priority: P2)

A las 23:50 la aprendiz recibe en su chat de soporte la imagen del color de su día y, debajo del porcentaje, un mensaje
distinto del que vio en Hoy esa mañana: la nombra, le habla de su día y, si corresponde, de su hábito más flojo o más
fuerte.

**Why this priority**: El dueño quiere que el mensaje no sea «solo el porcentaje» también en el chat diario (D-281).

**Independent Test**: Con una cuenta en cada color, dejar correr la tarjeta de las 23:50 (reloj de prueba en la
madrugada UTC de Lima) y leer el texto del chat de soporte.

**Acceptance Scenarios**:

1. **Given** una aprendiz con 88 % hoy, **When** sale su tarjeta de las 23:50, **Then** el texto es «Hoy llevas 88 % de
   tus hábitos.», una línea en blanco y un mensaje VERDE con sus datos escritos, sin llaves; la imagen es la de siempre
   y no hay sticker.
2. **Given** que la aprendiz vio en Hoy el mensaje `v001`, **When** sale su tarjeta del mismo día en el mismo color,
   **Then** el mensaje del chat no es `v001`.
3. **Given** que la tarjeta ya salió a las 23:50, **When** corre el reintento de las 23:55, **Then** no se manda nada
   más y no se elige otro mensaje.
4. **Given** que no hay ningún mensaje posible para esa persona, **When** sale la tarjeta, **Then** el texto es el de
   siempre.
5. **Given** un mensaje del catálogo que pide `{faltan}`, **When** se elige el del chat, **Then** ese mensaje no es
   candidato.

### Edge Cases

- **Variables sin dato**: se descartan los mensajes que piden un dato que la persona no tiene (sin hábito flojo, sin
  fase, «faltan» en 0). Si en su grupo no queda ninguno, se usa un mensaje del mismo color sin variables.
- **Cambio de color durante el día**: si el color cambia, se elige un mensaje del color nuevo para que el mensaje nunca
  contradiga al semáforo; si no cambia, se mantiene el del día.
- **Los datos cambian durante el día** (cumple un hábito y «faltan» baja): el mensaje elegido se mantiene y sus datos se
  vuelven a escribir con los valores actuales en cada lectura.
- **Catálogo agotado**: si la persona ya vio todos los mensajes de su grupo dentro de la ventana de no repetición, se
  vuelve a elegir entre todos, menos el de ayer.
- **App antigua**: un APK que no conoce el mensaje sigue mostrando la tarjeta como hoy.
- **Servidor antiguo**: la app nueva contra un backend sin el mensaje muestra la tarjeta como hoy, sin hueco.
- **Sticker que no carga**: se muestra solo el mensaje.
- **Nombres largos en la imagen del podio**: se cortan con «…» sin salirse de su lugar.
- **Reducir movimiento**: el sticker y el mensaje aparecen sin animación.
- **Web**: la descarga en la web baja el archivo de la imagen en lugar de abrir la hoja de compartir.
- **Chat — nada que mostrar o falla** (D-281): sin candidatos, sin participación o con un error al elegir, el chat manda
  «Hoy llevas N % de tus hábitos.» como siempre y se registra un `warn`; nunca se retrasa ni se pierde la tarjeta.
- **Chat — lo único posible ya salió en Hoy** (D-281): si el único mensaje que cumple las reglas es el que la persona vio
  en Hoy ese día, el chat no lo repite: sale sin mensaje.
- **Chat — reintento** (D-281): el texto conserva su id (aprendiz + fecha), así que el reintento de las 23:55 no duplica;
  y la elección del día queda guardada, así que si se repite el cálculo da el mismo mensaje.

## Requirements *(mandatory)*

### Functional Requirements

**Mensaje del semáforo**

- **FR-001**: El sistema MUST entregar, junto al semáforo de la persona, un mensaje del día con su sticker; MUST hacerlo
  para todo aprendiz que hoy ve la tarjeta del semáforo.
- **FR-002**: El sistema MUST elegir el mensaje de un catálogo de 115 mensajes agrupados por color (35 VERDE, 35 AMARILLO,
  30 ROJO) y por caso sin color (INICIO, SIN_PROGRAMA, PAUSA, CUENTA_PAUSADA, GRADUADO, 3 cada uno). El catálogo inicial
  es `data/mensajes-semaforo.json`.
- **FR-003**: El sistema MUST elegir al azar un mensaje por persona y por día (en la zona horaria de la persona), y
  mantenerlo durante el día salvo que cambie el color o el caso.
- **FR-004**: El sistema MUST NOT repetir un mensaje que la persona vio en los últimos 14 días, mientras queden otros
  disponibles en su grupo.
- **FR-005**: El sistema MUST reemplazar las variables `{nombre}`, `{dia}`, `{fase}`, `{habitoFlojo}`, `{habitoFuerte}` y
  `{faltan}` con los datos de la persona en el momento de mostrarlo, y MUST NOT elegir un mensaje que pida un dato que
  la persona no tiene.
- **FR-006**: `{habitoFlojo}` MUST ser el hábito con menor cumplimiento de la persona en la ventana del semáforo, y
  `{habitoFuerte}` el de mayor cumplimiento (los dos con al menos 2 días programados; el fuerte con 50 % o más; si
  empatan o son el mismo, no se usa el dato); `{faltan}` MUST ser cuántos pendientes de hoy (hábitos + objetivos, la
  misma cuenta del semáforo, D-168) le faltan para subir al color siguiente con los umbrales vigentes, y solo se usa
  en AMARILLO y ROJO.
- **FR-007**: La app MUST mostrar el sticker y el mensaje en la tarjeta del semáforo de Hoy, debajo de la palabra del
  color, sin cambiar el resto de la tarjeta. El lector de pantalla MUST leer el mensaje.
- **FR-008**: La app MUST incluir los 15 stickers nuevos (5 dorados para logros y 10 azules) sin texto en la imagen, y
  MUST tolerar un sticker desconocido o que no cargue mostrando solo el mensaje.
- **FR-009**: El aviso del sábado, el detalle del semáforo y las vistas del mentor, el líder y administración MUST NOT
  cambiar.

**Mensaje en el chat diario (D-281)**

- **FR-014**: La tarjeta diaria del semáforo en el chat de soporte MUST llevar el texto «Hoy llevas N % de tus hábitos.»,
  una línea en blanco y un mensaje personalizado del mismo catálogo que el de Hoy. La imagen del color MUST NOT cambiar
  y el chat MUST NOT recibir sticker.
- **FR-015**: El mensaje del chat MUST ser del grupo del color del día de la tarjeta (VERDE, AMARILLO o ROJO), MUST usar
  solo las variables `{nombre}`, `{dia}`, `{fase}`, `{habitoFlojo}` y `{habitoFuerte}` (un mensaje con `{faltan}` MUST NOT
  ser candidato en el chat) y MUST escribirse con la fecha local de la persona.
- **FR-016**: El mensaje del chat MUST ser distinto del que la persona vio en Hoy ese día, y la regla de no repetir en 14
  días (FR-004) MUST considerar los mensajes de los dos canales.
- **FR-017**: Cada elección MUST guardarse con su canal (`HOY` o `CHAT`) además de persona, fecha local y grupo, de forma
  idempotente: el reintento de la tarjeta MUST reutilizar el mismo mensaje.
- **FR-018**: Si no hay mensaje o elegirlo falla, el chat MUST mandar el texto de siempre; el mensaje MUST NOT impedir
  ni retrasar la tarjeta. El id del texto MUST seguir siendo el calculado de aprendiz + fecha.

**Descarga del podio**

- **FR-010**: La app MUST ofrecer «Descargar» en Comunidad → Ranking solo a Líder de mentores, Alquimista, Administrador y
  Soporte TI; el permiso lo decide el servidor.
- **FR-011**: La app MUST generar una imagen vertical 9:16 de la tabla que se está mirando, con el logo de Renaser, la
  fecha, el nombre de la tabla, el podio de los tres primeros y del 4.º al 10.º, con nombre completo y puntaje, en los
  colores de la marca.
- **FR-012**: La app MUST abrir la hoja de compartir del teléfono con esa imagen; en la web MUST bajar el archivo.
- **FR-013**: La imagen MUST mostrar los mismos puestos y puntajes que la tabla en pantalla en el momento de generarla.

### Key Entities

- **Mensaje del semáforo**: texto con variables, color o caso al que pertenece, sticker que lo acompaña, si está activo.
- **Mensaje visto**: qué mensaje se le mostró a qué persona y qué día, con el color de ese momento y **el canal** (`HOY`
  o `CHAT`, D-281); se guarda para no repetir (en los dos canales), mantener el del día y hacer que el del chat sea
  distinto del de Hoy.
- **Sticker del semáforo**: imagen del búho identificada por un nombre (`dorado-orgulloso`, `azul-firme`…).
- **Imagen del podio**: tabla, fecha y los diez primeros puestos con nombre y puntaje; se arma en el teléfono y no se
  guarda en el servidor.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de los aprendices que ven la tarjeta del semáforo ven un mensaje con sticker todos los días,
  también los días sin color.
- **SC-002**: Ningún mensaje mostrado tiene variables sin reemplazar, huecos ni voseo (verificado sobre todo el catálogo
  y en cada caso de prueba).
- **SC-003**: Una persona no ve el mismo mensaje dos veces en 14 días mientras queden otros de su grupo.
- **SC-004**: El mensaje aparece al mismo tiempo que la tarjeta del semáforo, sin espera adicional perceptible.
- **SC-005**: Generar y compartir la imagen del podio toma menos de 3 segundos desde que se toca «Descargar».
- **SC-006**: Ninguna cuenta fuera de los cuatro roles autorizados puede obtener la imagen del podio.
- **SC-007**: Ninguna tarjeta del chat lleva el mismo mensaje que la persona vio en Hoy ese día, y ninguna lleva
  variables sin reemplazar ni `{faltan}`.

## Assumptions

- La ventana de no repetición es de 14 días (confirmado).
- «Al azar» es aleatorio uniforme entre los mensajes disponibles de su grupo.
- El catálogo se carga en la migración V110 (reservada) y se puede corregir después sin cambiar la app; la edición del
  catálogo desde Administración queda fuera de alcance.
- La tabla del podio es la vigente que muestra la app (mismo corte y alcance que hoy).
- La imagen del podio se arma en el teléfono a partir de los datos que la app ya tiene; no hace falta un endpoint nuevo
  salvo el permiso.
- Soporte TI es el rol `IT_SUPPORT` de la spec 002 (fase 1, en producción).
- Hace falta un APK nuevo para los stickers, la tarjeta y la descarga; la app nueva debe tolerar el backend anterior.
- El chat (D-281) no necesita cambios en la app ni un APK nuevo: el texto del programa ya se muestra tal cual. Sí necesita
  el backend con V110 (la columna `canal` va dentro de V110 mientras no esté integrada en master).
- Hace falta autorización anotada en `AGENTS.md` para tocar Hoy (tarjeta del semáforo) y Comunidad (Ranking); el dueño
  ya la dio en esta conversación.
