# Feature Specification: Control de la plataforma — interruptores, despliegue progresivo y rol Soporte TI

**Feature Branch**: `002-soporte-ti-interruptores` (spec-kit no crea ramas en este repo; la rama de trabajo se abre al
planificar)

**Created**: 2026-10-13

**Status**: Draft — decisiones del dueño cerradas (ver «Clarifications»); faltan los números finos marcados como
«a confirmar».

**Input**: Pedido del dueño (textual, ordenado): «implementación de interruptores para el agente de IA, si llega a
fallar o cuando tengamos una actualización; un despliegue por cantidad de usuarios: una nueva funcionalidad que esté
disponible para cierta cantidad de usuarios y, si funciona, que se active progresivamente para todos, para no afectar
producción; tener control total de la app en funciones y también de la información de los usuarios; un nuevo rol que
contenga todo esto, para no recurrir a código si algo falla, hacer la revisión y poder habilitar con interruptores. Este
rol será para mí como programador y nada más: Soporte TI, donde estén todas las funcionalidades de todos los roles, una
por una, para activar o desactivar; tener el control total, hasta del chat o del muro si fallan».

**Repos**: backend (`Renaser-90-dias-backend`, esta spec) y app (`Renaser-90-dias-frontend-`). La spec vive acá.

---

## Contexto: lo que hay hoy (inventario del 2026-10-13)

- **No existe ningún interruptor en caliente.** Todo lo que hoy prende o apaga algo se lee **una sola vez al
  arrancar**: propiedades de `application.yaml` que en producción vienen de Parameter Store (`/renaser/prod/X`). Cambiar
  uno exige `put-parameter` y `docker restart backend` (`docs/DESPLIEGUE_Y_CI.md` §6.2). Ejemplos: `IA_PROVEEDOR`,
  `IA_VOZ_EN_VIVO`, `IA_ACOMPANANTE_CONFIRMACION_CON_BOTONES`, `IA_ACOMPANANTE_MEMORIA`, `BIENVENIDA_ACTIVA`,
  `RANKING_SEMANAL_ACTIVO`, `SEMAFORO_TARJETA_DIARIA_ACTIVA`, `MENTOR_LEAD_ENFORCEMENT`. Varios usan
  `@ConditionalOnProperty`: apagados, el bean ni existe. Hay crons fijos en el código que no se pueden apagar sin
  desplegar (recordatorios de eventos, cola de validación, avisos de hábito, corte del ranking).
- **Los únicos «interruptores» que cambian sin reiniciar son datos**: activar un hábito, su audiencia por rol (D-264),
  categorías del muro, suspender una cuenta, la bienvenida (V73).
- **La app no lee configuración del servidor.** No hay endpoint de configuración, banderas ni versión mínima; no hay
  `expo-updates`; sus banderas (`EXPO_PUBLIC_*`) se fijan al compilar. Lo más parecido es `capabilities` de
  `GET /mentor/context` (`canAdminister`, `canStartProgram`, `canAccompany`, `programRequired`).
- **IA**: solo Google Gemini (chat `gemini-3.5-flash-lite`, embeddings, TTS, Gemini Live). Ante una caída hay reintentos
  cortos, 503 con `Retry-After` fuera del stream y un evento de error dentro del SSE; la voz del orbe cae a la voz del
  teléfono (204) y la voz en vivo cierra con 1013. **No hay apagado automático** ni alternativa: el único «fallback» es
  poner `IA_PROVEEDOR=noop` y reiniciar.
- **Autorización**: sesión (Spring Session + Redis, `X-Auth-Token`) → `@RequiresPermission` + interceptor (suspensión y
  matriz; MENTOR/GUÍA/ADMIN/ALQUIMISTA pasan por la deuda A-1) → guards en servicios y dominio. Seis roles; ninguno
  equivalente a Soporte TI.
- **Tamaño**: ~120 controladores y ~354 endpoints, 2 WebSocket (chat STOMP y voz en vivo), 17 módulos de Modulith.

El inventario completo por área está abajo, en «Catálogo inicial de funcionalidades».

## Clarifications

### Session 2026-10-13 (respuestas del dueño)

**Rol Soporte TI**
- Q: ¿Quién puede tener el rol? → A: **Solo la cuenta del dueño-programador, asignada desde la base** (migración o
  script). Nadie lo da desde la app, ni Admin ni Alquimista.
- Q: ¿Dónde vive la consola? → A: **En la app**, sección «Soporte TI», visible solo con ese rol.
- Q: ¿Qué hace con los datos de las personas? → A: **Ve todo y hace arreglos de soporte acotados**, auditados. **No lee
  chats privados.**
- Q: ¿Seguridad extra? → A: **Las cuatro**: auditoría de cada cambio con motivo; confirmar con contraseña lo crítico;
  avisar al Alquimista y al Admin cuando se apaga algo para todos; sesión corta en la consola. Y control de **todas**
  las funcionalidades.
- Q: ¿Ver la app como cualquier rol? → A: **Sí, «ver como rol» en solo lectura.**

**Interruptores**
- Q: ¿Qué tan fino? → A: **Por funcionalidad y, dentro, por rol** (p. ej. «Chat → voz» apagado solo para aprendices).
- Q: ¿Qué ve la persona cuando algo está apagado? → A: **Un aviso «En mantenimiento» editable**: Soporte TI puede
  escribir un texto propio por interruptor.
- Q: ¿En cuánto tiempo se aplica? → A: **Menos de un minuto, sin reiniciar nada** (ni backend ni APK).
- Q: ¿APK viejo? → A: **El servidor también bloquea**: una app que no conoce los interruptores recibe un error amable.

**IA (SER)**
- Q: ¿Cómo se apaga si el proveedor falla? → A: **Manual y automático**: se apaga sola tras fallas seguidas, vuelve a
  probar sola y avisa por push.
- Q: ¿Qué partes se apagan por separado? → A: **Chat de SER (y del curso), voz (orbe y voz en vivo), propuestas y
  acciones.** Memoria y mensajes automáticos quedan bajo el interruptor general de IA.

**Despliegue progresivo**
- Q: ¿A quién le llega primero? → A: **Lista de probadores y luego porcentaje estable** (5 → 25 → 50 → 100 %): cada
  persona cae siempre en el mismo grupo.
- Q: ¿Quién sube de escalón? → A: **Las dos formas**: «prender para todos» de golpe, o **por partes a mano** con
  métricas a la vista. (El escalón automático no se pidió; queda fuera.)

**Roles personalizados** (pedido del dueño el mismo día: «con esto ya podríamos hacer un CRUD de roles y asignar al rol
los permisos de las funcionalidades»)
- Q: ¿CRUD total de roles o roles que heredan? → A: **Rol nuevo que hereda de uno base**: Soporte TI crea, renombra,
  desactiva o borra un rol, elige de qué rol existente hereda el comportamiento y marca sus funcionalidades del catálogo.
  El CRUD total (roles solo como datos) se descartó: exige reescribir la autorización del backend.
- Q: ¿Quién asigna los roles creados? → A: **Admin y Alquimista, como hoy** (Equipo y roles, y al aprobar una solicitud).

**Orden**
- Q: ¿En qué orden se construye? → A: **1) interruptores + Soporte TI (con la IA), 2) progresivo, ver como rol y roles
  personalizados, 3) herramientas sobre datos de personas.**

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Apagar cualquier funcionalidad, para un rol o para todos, en menos de un minuto (Priority: P1)

Algo falla en producción (el chat se cae, una pantalla rompe datos, el muro recibe spam). Soporte TI abre la consola,
busca la funcionalidad, la apaga para todos o solo para un rol, escribe el aviso que verá la gente y un motivo. En menos
de un minuto nadie más puede usarla, ni desde la app nueva ni desde un APK viejo, y cuando lo arregla la vuelve a
prender igual de rápido.

**Why this priority**: es lo que protege producción. Hoy la única salida es desplegar código o reiniciar con un
parámetro, y varias cosas (crons fijos, endpoints) ni siquiera tienen esa salida.

**Independent Test**: con el chat de grupo prendido, Soporte TI lo apaga para APRENDIZ con el aviso «Volvemos a las
18:00»: un aprendiz ve el aviso en lugar del chat (app nueva) o recibe el error amable (APK viejo) en ≤ 60 s; un mentor
lo sigue usando; prenderlo lo devuelve igual.

**Acceptance Scenarios**:

1. **Given** «Chat → grupos» prendido, **When** Soporte TI lo apaga para todos con aviso y motivo, **Then** en ≤ 5 s el
   servidor rechaza los pedidos de esa funcionalidad con el aviso, y en ≤ 60 s la app (en primer plano o al volver a
   ella) muestra el aviso en el lugar de la funcionalidad.
2. **Given** una funcionalidad apagada solo para APRENDIZ, **When** un MENTOR la usa, **Then** funciona; **When** un
   APRENDIZ la usa, **Then** ve el aviso.
3. **Given** un APK 1.5.0 (no conoce los interruptores), **When** usa una funcionalidad apagada, **Then** el servidor
   responde un error con el aviso como mensaje, que esa app ya muestra como error normal, y no se escribe nada.
4. **Given** una funcionalidad apagada, **When** corre un proceso automático de esa funcionalidad (cron, oyente de
   evento), **Then** no actúa (o pospone, según el catálogo) y lo anota en el log.
5. **Given** cualquier cambio, **When** se guarda, **Then** queda en la auditoría: quién, cuándo, qué, de qué valor a cuál,
   para qué roles, motivo y aviso.
6. **Given** un cambio crítico (apagar para todos, o algo marcado crítico: IA, chat, muro, login de terceros…),
   **When** Soporte TI lo confirma, **Then** la app le pide su contraseña de nuevo y avisa al Alquimista y al Admin.

---

### User Story 2 — Apagar la IA a mano o sola cuando el proveedor falla (Priority: P1)

Gemini se satura o cae, o hay que actualizar el modelo. SER se puede apagar entera o por partes (chat, voz,
propuestas). Si el proveedor falla varias veces seguidas, SER se apaga sola por unos minutos, prueba de nuevo sola, y
Soporte TI recibe un push en cada cambio.

**Why this priority**: la IA es la dependencia externa más frágil y hoy no tiene ni apagado en caliente ni automático.

**Independent Test**: con un proveedor de prueba que falla, tras N errores en una ventana SER pasa a «apagada
automáticamente», la app muestra el aviso de SER, llega un push a Soporte TI; al vencer la espera, un pedido de prueba
sale bien y SER vuelve sola, con otro push.

**Acceptance Scenarios**:

1. **Given** SER prendida, **When** Soporte TI apaga «IA → voz», **Then** el orbe usa la voz del teléfono / no abre voz
   en vivo y el chat de texto sigue.
2. **Given** «IA» (general) apagada, **When** alguien abre el panel de SER o el chat del curso, **Then** ve el aviso;
   tampoco se envían mensajes automáticos de SER ni se compacta memoria.
3. **Given** el proveedor responde error N veces en la ventana configurada, **When** llega el pedido N+1, **Then** no se
   llama al proveedor, la persona ve el aviso y queda registrado el apagado automático con su causa.
4. **Given** el circuito abierto, **When** vence la espera, **Then** se permite un pedido de prueba; si sale bien se
   cierra y se avisa; si falla, se reabre con espera mayor.
5. **Given** un apagado manual, **When** el circuito automático intentaría cerrarse, **Then** sigue apagado: **lo manual
   manda** sobre lo automático.

---

### User Story 3 — El rol Soporte TI y su consola (Priority: P1)

El dueño-programador entra con su cuenta Soporte TI y ve una sección propia en la app con: todas las funcionalidades de
todos los roles, una por una, con su estado; la auditoría; las alertas. Ese rol no se puede dar desde la app ni pedir.

**Why this priority**: sin el rol no hay quién opere los interruptores sin código, y sin sus resguardos el rol más
poderoso sería el más fácil de abusar.

**Independent Test**: una cuenta con el rol (asignado por script) ve «Soporte TI»; ADMIN y ALQUIMISTA reciben 403 en
todos los endpoints de la consola y no la ven; ningún endpoint permite asignar el rol; tras el tiempo de inactividad la
consola pide la contraseña.

**Acceptance Scenarios**:

1. **Given** una cuenta Soporte TI, **When** abre la app, **Then** ve la sección Soporte TI además de lo que le
   corresponde (Administración incluida).
2. **Given** cualquier otro rol (incluidos ADMIN y ALQUIMISTA), **When** llama a un endpoint de la consola, **Then** 403;
   **When** intenta asignar el rol por `PATCH /users/{id}/role`, aprobar con ese rol o invitar con él, **Then** 400/403.
3. **Given** la consola abierta, **When** pasan X minutos sin uso (a confirmar: 15), **Then** se bloquea y pide la
   contraseña.
4. **Given** el catálogo, **When** Soporte TI lo recorre, **Then** ve cada funcionalidad con: estado por rol, aviso,
   quién la cambió por última vez y cuándo, si está en despliegue progresivo y si es crítica.
5. **Given** que Soporte TI intenta apagar la propia consola, el login o la lectura de interruptores, **Then** no puede:
   esas funcionalidades no tienen interruptor (anti-bloqueo).

---

### User Story 4 — Desplegar una funcionalidad nueva de a poco (Priority: P2)

Sale una funcionalidad nueva. Soporte TI la prende primero para su lista de probadores, después para el 5 %, 25 %, 50 %
y 100 % de las personas, mirando errores y uso en cada escalón; o la prende para todos de golpe. Si algo sale mal, la
baja con un toque.

**Why this priority**: evita que una funcionalidad nueva afecte a todos a la vez. Va después de los interruptores
porque se apoya en ellos.

**Independent Test**: con una funcionalidad marcada «en despliegue» al 25 %, exactamente las personas de ese 25 %
(estable por persona) la ven, los probadores también, y subir a 50 % mantiene dentro a las del 25 %.

**Acceptance Scenarios**:

1. **Given** una funcionalidad al 25 %, **When** una persona entra varias veces o cambia de teléfono, **Then** siempre ve
   lo mismo (asignación estable por persona y funcionalidad).
2. **Given** 25 % → 50 %, **When** se sube, **Then** nadie que la tenía la pierde (los grupos crecen, no se rearman).
3. **Given** la lista de probadores, **When** la funcionalidad está al 0 %, **Then** solo ellos la ven.
4. **Given** «prender para todos», **When** se confirma, **Then** pasa a 100 % y deja de estar «en despliegue».
5. **Given** un escalón, **When** Soporte TI mira la funcionalidad, **Then** ve, para quienes la tienen, cuántos la usan y
   cuántos errores del servidor tuvo en las últimas horas, comparado con el escalón anterior.
6. **Given** un interruptor apagado (US1) sobre una funcionalidad en despliegue, **Then** el apagado manda: nadie la ve.

---

### User Story 5 — Ver la app como otro rol, en solo lectura (Priority: P2)

Soporte TI elige «ver como Aprendiz» (o Guía, Mentor, Líder, Admin) y recorre la app tal como la vería ese rol, con
datos de prueba propios, sin poder escribir nada.

**Why this priority**: es la herramienta para revisar un reporte («a los mentores no les carga X») sin tocar cuentas
reales.

**Independent Test**: en «ver como Mentor» la app muestra lo que ve un mentor; cualquier acción que escribiría responde
«modo solo lectura»; salir vuelve a Soporte TI.

**Acceptance Scenarios**:

1. **Given** «ver como Aprendiz», **When** navega, **Then** ve las pestañas, tarjetas y menús de un aprendiz.
2. **Given** ese modo, **When** intenta completar un hábito, publicar o cambiar algo, **Then** el servidor lo rechaza con
   «Modo solo lectura» y no escribe nada.
3. **Given** ese modo, **Then** la auditoría registra cuándo entró, a qué rol y cuándo salió.

---

### User Story 6 — Datos de las personas y arreglos de soporte (Priority: P3)

Soporte TI busca a una persona y ve todo lo suyo (ficha, rol, estado, programa, día, registros, puntos, evidencias,
sesiones activas, avisos fallidos, errores recientes) salvo el contenido de chats privados, y aplica arreglos de una
lista cerrada, cada uno auditado y con motivo.

**Why this priority**: va al final por orden del dueño; antes hay que proteger producción.

**Independent Test**: la ficha 360 de una persona muestra esos bloques; leer un chat privado no es posible; cada arreglo
de la lista queda auditado y uno fuera de la lista no existe.

**Acceptance Scenarios**:

1. **Given** una persona, **When** Soporte TI abre su ficha, **Then** ve los bloques listados y **no** el contenido de sus
   chats privados (sí puede ver metadatos: en qué conversaciones está, último mensaje como fecha, sin texto).
2. **Given** la lista cerrada de arreglos (a confirmar: cerrar sesiones, reintentar publicaciones de eventos
   incompletas, regenerar el día de hábitos, corregir un registro con motivo, reenviar un aviso, recalcular un ranking),
   **When** aplica uno, **Then** se pide motivo, se ejecuta por el mismo caso de uso que la app y queda auditado.

---

### User Story 7 — Crear roles que heredan de uno base (Priority: P2)

Soporte TI crea «Coordinador de eventos»: hereda de MENTOR y tiene solo eventos, chat y muro. Admin o Alquimista se lo
asignan a una persona desde «Equipo y roles» o al aprobar su solicitud. Esa persona se comporta como mentor en todo lo
que el código decide por rol, pero solo ve y usa las funcionalidades marcadas. Si mañana sobra, Soporte TI lo desactiva
o, si nadie lo tiene, lo borra. Es «Guía de aprendiz» (D-265) hecho desde la consola y sin desplegar.

**Why this priority**: da al dueño roles nuevos sin código, apoyado en el catálogo de la fase 1. Va después porque
depende de que el catálogo y la evaluación por rol ya existan.

**Independent Test**: crear un rol que hereda de MENTOR con «eventos» y sin «muro», asignarlo a una cuenta de prueba: la
cuenta usa eventos, ve el aviso en el muro, pasa los chequeos de mentor del backend (por ejemplo, su grupo) y figura con
el nombre nuevo en el chat y en Equipo y roles.

**Acceptance Scenarios**:

1. **Given** el catálogo, **When** Soporte TI crea un rol con base MENTOR, **Then** solo puede marcar funcionalidades que
   MENTOR tiene: un rol hijo nunca tiene más que su base.
2. **Given** un rol personalizado activo, **When** Admin o Alquimista abren Equipo y roles o aprueban una solicitud,
   **Then** el rol aparece en la lista con su nombre y el texto «Se comporta como Mentor».
3. **Given** una persona con ese rol, **When** el backend decide algo por rol (guards, rankings, padrones, hábitos por
   rol), **Then** usa el rol base; **When** decide si una funcionalidad está prendida, **Then** usa el rol personalizado.
4. **Given** un rol con personas asignadas, **When** Soporte TI intenta borrarlo, **Then** no puede: lo desactiva (nadie
   nuevo lo recibe; quienes lo tienen lo conservan) o lo reemplaza en bloque por otro rol, con motivo.
5. **Given** un rol con personas asignadas, **When** Soporte TI intenta cambiar su rol base, **Then** no puede (cambiaría
   permisos sin aviso); se crea otro rol y se reasigna.
6. **Given** cualquier alta, cambio, desactivación, borrado o reemplazo en bloque, **Then** queda auditado y, si afecta a
   personas, avisa al Alquimista y al Admin.

---

### Edge Cases

- **Anti-bloqueo**: login, sesión, `GET` de interruptores, la consola Soporte TI y el health del servidor **no tienen
  interruptor**. Ningún cambio puede dejar a Soporte TI sin acceso.
- **El servicio de interruptores falla** (Redis o base caídos): cada instancia sigue con el último estado conocido; si
  nunca lo leyó, usa el valor por defecto del catálogo (prendido, salvo lo marcado «nace apagado»). La app sin red usa
  el último estado guardado.
- **Varias instancias del backend**: un cambio llega a todas en ≤ 5 s.
- **Rol y global en conflicto**: apagado para todos gana sobre prendido para un rol.
- **Pedido en vuelo** cuando se apaga: termina; el siguiente ya ve el apagado.
- **Funcionalidad apagada con datos pendientes** (p. ej. podio semanal en su ventana, recordatorios por enviar): el
  catálogo dice si el proceso automático **salta** o **pospone** (p. ej. el podio sigue intentando la hora siguiente
  cuando se prenda).
- **La persona cambia de rol** con una funcionalidad apagada para su rol viejo: rige su rol actual en el próximo pedido.
- **Circuito de IA que oscila**: espera creciente entre pruebas y un máximo de avisos push por hora.
- **Despliegue**: bajar de 50 % a 25 % saca a quienes estaban entre 25 y 50; los probadores siempre la tienen; una
  persona nueva cae en un grupo estable desde su primer pedido.
- **Cuenta Soporte TI comprometida**: contraseña para lo crítico, sesión corta, aviso al Alquimista y Admin, y la
  auditoría no se puede borrar ni editar desde la app.
- **Aviso editable vacío**: se muestra el genérico «En mantenimiento, vuelve pronto».
- **Funcionalidad desconocida para la app** (una app vieja recibe una clave nueva): la ignora; el servidor sigue
  bloqueando.
- **Ver como rol** mientras la funcionalidad está apagada para ese rol: se ve apagada (es lo que vería ese rol).
- **Rol personalizado y funcionalidad nueva**: una funcionalidad que se suma al catálogo después de crear el rol nace
  **apagada** para los roles personalizados (hay que marcarla a propósito) y con su valor normal para los seis base.
- **Interruptor por rol base vs. personalizado**: apagar «chat» para MENTOR también lo apaga para los roles que heredan de
  MENTOR (el hijo nunca tiene más que su base).
- **APK viejo con una persona de rol personalizado**: la app vieja ve el rol base en `/auth/me` (`role`) y el nombre nuevo
  solo en un campo aditivo; el servidor sigue bloqueando lo no marcado.
- **Nombre repetido o igual a un rol base** («Mentor»): se rechaza.

---

## Requirements *(mandatory)*

### Functional Requirements

**A. Catálogo de funcionalidades**
- **FR-001**: El sistema MUST tener un **catálogo de funcionalidades declarado en el código** (no creado desde la app):
  clave estable, nombre visible, área, descripción, roles a los que aplica, si es **crítica**, valor por defecto,
  qué hacen sus procesos automáticos al apagarse (saltar/posponer) y si admite despliegue progresivo.
- **FR-002**: Una funcionalidad MAY tener **hijas** (p. ej. `chat` → `chat.grupos`, `chat.voz`); apagar la madre apaga
  las hijas.
- **FR-003**: El catálogo inicial MUST cubrir **todas** las áreas listadas en «Catálogo inicial de funcionalidades».
- **FR-004**: Las funcionalidades anti-bloqueo (login, sesión, lectura de interruptores, consola Soporte TI, health)
  MUST NOT tener interruptor.

**B. Estado y cambios**
- **FR-005**: El estado de cada funcionalidad MUST poder fijarse **para todos** y **por rol** (los seis roles más
  Soporte TI), con un **aviso editable** por funcionalidad (máx. 200 caracteres) y un motivo obligatorio.
- **FR-006**: Los cambios MUST aplicarse en el servidor en **≤ 5 s** en todas las instancias, **sin reiniciar**.
- **FR-007**: Cada cambio MUST registrarse en una **auditoría append-only**: actor, fecha, funcionalidad, valor anterior y
  nuevo, roles, aviso, motivo, dispositivo. No editable ni borrable desde ninguna API.
- **FR-008**: Los cambios **críticos** (apagar para todos, o cualquier cambio en una funcionalidad marcada crítica) MUST
  pedir **re-autenticación con contraseña** y MUST **avisar por push al Alquimista y al Admin** (y a Soporte TI).

**C. Aplicación en el servidor**
- **FR-009**: Todo endpoint, oyente de evento y tarea programada de una funcionalidad MUST consultar su estado antes de
  actuar; los endpoints MUST declarar a qué funcionalidad pertenecen (como hoy declaran `@RequiresPermission`) y una
  prueba de arquitectura MUST fallar si un endpoint no la declara (o se marca explícitamente como anti-bloqueo).
- **FR-010**: Una funcionalidad apagada MUST responder un **error identificable** (código propio, p. ej.
  `FUNCIONALIDAD_APAGADA`) cuyo mensaje sea el aviso editable, para que el APK viejo lo muestre como error normal.
- **FR-011**: La consulta del estado MUST costar ≤ 2 ms p95 por pedido (caché en memoria invalidada por el cambio).

**D. Aplicación en la app**
- **FR-012**: La app MUST leer el estado de las funcionalidades **para su sesión** al entrar, al volver al primer plano
  y cada ≤ 60 s mientras está abierta, y guardarlo para usarlo sin red.
- **FR-013**: Una funcionalidad apagada MUST mostrarse con el aviso editable en su lugar (no desaparecer), con un
  componente común reutilizable.
- **FR-014**: La app MUST tratar el error `FUNCIONALIDAD_APAGADA` del servidor igual que el estado leído (muestra el
  aviso y relee el estado).

**E. IA**
- **FR-015**: Funcionalidades de IA: `ia` (general; incluye memoria y mensajes automáticos de SER), `ia.chat` (panel y
  chat del curso), `ia.voz` (orbe y voz en vivo), `ia.propuestas` (acciones y botones de SER).
- **FR-016**: Un **circuito automático** MUST abrir `ia` (o la parte afectada) tras N fallas del proveedor en una
  ventana (a confirmar: 5 fallas en 2 minutos), esperar un tiempo creciente (a confirmar: 2, 5, 15 min), probar con un
  pedido y cerrarse si sale bien; cada apertura y cierre MUST avisar por push a Soporte TI (máx. N por hora) y quedar
  auditado como «automático».
- **FR-017**: Un apagado manual MUST prevalecer sobre el circuito automático.

**F. Despliegue progresivo**
- **FR-018**: Una funcionalidad MAY estar «en despliegue» con: lista de probadores, porcentaje (0–100) y escalones; la
  asignación MUST ser **estable** por persona y funcionalidad, y crecer sin sacar a nadie al subir el porcentaje.
- **FR-019**: La consola MUST ofrecer **«prender para todos»** y **subir/bajar escalón a mano**; cada paso es un cambio
  auditado.
- **FR-020**: La consola MUST mostrar, por funcionalidad en despliegue, uso y errores del servidor de quienes la tienen
  en las últimas horas.
- **FR-021**: El apagado (FR-005) MUST prevalecer sobre el despliegue.

**G. Rol Soporte TI**
- **FR-022**: Nuevo rol `SOPORTE_TI` (nombre técnico a confirmar en el plan). MUST asignarse **solo** por migración o
  script de base; ningún endpoint MUST permitir asignarlo, quitarlo, invitarlo o aprobar con él.
- **FR-023**: Soporte TI MUST tener todos los permisos de todos los roles (incluida Administración) más la consola; MUST
  NOT figurar en rankings, padrones de aprendices ni como candidato a mentor salvo que active su programa.
- **FR-024**: La consola MUST bloquearse tras X minutos de inactividad (a confirmar: 15) y pedir contraseña.
- **FR-025**: Todo endpoint de la consola MUST rechazar con 403 a cualquier otro rol (incluidos ADMIN y ALQUIMISTA) y a
  una cuenta suspendida, con pruebas de autorización negativa.

**H. Ver como rol**
- **FR-026**: Soporte TI MUST poder entrar en «ver como {rol}» en solo lectura; todo pedido que escriba MUST rechazarse con
  «Modo solo lectura»; entrar y salir queda auditado.

**I. Datos de las personas**
- **FR-027**: Ficha 360 de una persona (ficha, rol, estado, programa, día, registros, puntos, evidencias, sesiones,
  avisos fallidos, errores recientes) **sin el contenido de chats privados**.
- **FR-028**: Arreglos de soporte de una **lista cerrada** (a confirmar), cada uno con motivo, ejecutado por el mismo caso
  de uso que la app y auditado.

**J. Roles personalizados**
- **FR-029**: Soporte TI MUST poder crear, renombrar, describir, desactivar, reactivar y borrar roles personalizados,
  cada uno con un **rol base** entre los seis (nunca SOPORTE_TI) y un conjunto de funcionalidades del catálogo que
  MUST ser un subconjunto de las del rol base.
- **FR-030**: La cuenta MUST seguir guardando el **rol base** en `usuarios.rol` (así todos los guards, rankings,
  padrones, enums espejo y hábitos por rol siguen igual) más una referencia opcional al rol personalizado. La evaluación de
  interruptores MUST usar el rol personalizado cuando exista.
- **FR-031**: ADMIN y ALQUIMISTA MUST poder asignar y quitar los roles personalizados activos desde los mismos lugares que
  hoy (Equipo y roles, aprobar solicitud), con las mismas reglas que el rol base (p. ej. solo ADMIN asigna un rol que
  hereda de ADMIN).
- **FR-032**: La app MUST mostrar el nombre del rol personalizado donde hoy muestra el rol (perfil, chat, muro, Equipo y
  roles), vía un campo aditivo de `/auth/me` y de las listas; `role` sigue siendo el base.
- **FR-033**: Un rol con personas asignadas MUST NOT borrarse ni cambiar de base; MUST poder reemplazarse en bloque por
  otro rol con motivo.
- **FR-034**: Máximo de roles personalizados activos (a confirmar: 20) para que la consola siga siendo manejable.

### Key Entities

- **Funcionalidad** (catálogo, en código): clave, área, madre, nombre, descripción, roles aplicables, crítica, defecto,
  política de procesos automáticos, admite despliegue.
- **EstadoDeFuncionalidad** (base): clave, prendida para todos (sí/no), excepciones por rol, aviso, actualizado por/en,
  versión.
- **DespliegueDeFuncionalidad** (base): clave, porcentaje, probadores, historial de escalones.
- **CambioAuditado** (base, append-only): actor, fecha, tipo (manual, automático, ver como, arreglo), clave, antes,
  después, roles, aviso, motivo, dispositivo.
- **CircuitoDeIa** (memoria compartida): parte, estado (cerrado/abierto/probando), fallas en ventana, próxima prueba.
- **SesionDeConsola**: actor, desbloqueada hasta, re-autenticaciones.
- **RolPersonalizado** (base): id, clave, nombre visible, descripción, rol base, activo, funcionalidades marcadas, creado
  y actualizado por/en. La cuenta lo referencia de forma opcional junto a su rol base.

---

## Catálogo inicial de funcionalidades

Claves propuestas (el nombre técnico definitivo sale del plan). **Roles**: T aprendiz, G guía, M mentor, L líder, A
admin, X alquimista. «Proceso» = qué hacen sus crons/oyentes al apagarse.

| Clave | Qué apaga | Roles | App (principal) | Servidor (principal) | Proceso |
|---|---|---|---|---|---|
| `registro` | Solicitar acceso (alta pública) | anónimo | LoginScreen «Solicitar acceso» | `/account-requests` públicos | — |
| `onboarding` | Ficha, términos, pacto, cuestionario | T (staff opcional) | OnboardingFlow | `/onboarding/*` | — |
| `dia_uno` | Elegir Día 1 / activar programa | T, staff | ActivarProgramaScreen | `/onboarding/activate-program`, `/mentor/activate-tracking` | — |
| `radar` | Código Renaser diario | T (staff opc.) | CodigoRenaserOverlay | `/radar` | — |
| `mapa` | Mapa de Renacimiento | con programa | MapaRenacimientoFlow | `/mapa-renacimiento` | — |
| `plan` | Rocas y objetivos (maestra, mes, semana, día) | T, staff+prog | PlanScreen | `/rocks/**` | Verdugo salta |
| `habitos` | Hábitos de hoy y completar | T, staff+prog | TrainingScreen | `/habit-tracks/**`, `/habits` | generación de tracks y avisos saltan |
| `habitos.foto` | Evidencia con foto | idem | RegistroConFotoModal | `/habit-tracks/{id}/evidence*` | — |
| `habitos.km` | Kilómetros diarios | idem | registro de km | complete con `valorMedido` | ranking km salta |
| `habitos.gimnasio` | Gimnasio | idem | tarjeta GIMNASIO | complete del hábito GYM | ranking gimnasio salta |
| `habitos.espiritu` | Pastilla y audioterapia | idem | PastillaRenacerModal | `/spirit-audio`, `/audio-therapy` | — |
| `habitos.ajustes` | Horarios, pausa, renombre | idem | Planificar, renombrar | `/habit-preferences`, `/habit-unlocks`, `/habits/{id}/rename` | — |
| `clase_diaria` | Clase diaria | idem | ClaseDiariaModal | `/classroom/clase-diaria` | — |
| `muro` | Muro entero | todos | sección Muro | `/wall/**` | post diario salta |
| `muro.publicar` | Publicar | todos | NuevaPublicacion | `POST /wall`, media | — |
| `muro.comentarios` | Comentar | todos | comentarios | `/wall/{id}/comments` | — |
| `muro.reacciones` | Reaccionar | todos | reacciones | `/wall/{id}/react` | — |
| `chat` | Chat entero | todos | sección Tribu, sala | `/chat/**`, WS `/ws` | bienvenidas, tarjetas, podio posponen |
| `chat.grupos` | Chat de grupo | participantes | sala | mensajes de conversaciones CELULA | — |
| `chat.global` | Formación Renaser Global | todos | sala global | idem GLOBAL | podio pospone |
| `chat.soporte` | Chat de soporte | T, A, X | sala soporte | idem SOPORTE | tarjeta diaria salta |
| `chat.directos` | 1 a 1 | todos | directos | `/chat/conversations/direct` | — |
| `chat.audio` | Notas de voz | todos | grabar | MIME audio en mensajes | — |
| `chat.fotos` | Fotos y stickers | todos | galería, cámara, stickers | media upload-url | — |
| `eventos` | Ver y confirmar | audiencia | SeccionEventos | `/calendar/events` GET, rsvp | recordatorios saltan |
| `eventos.gestion` | Crear/editar | A, X | FormularioDelEvento | `/calendar/events` POST/PUT/DELETE | — |
| `eventos.asistencia` | Quién respondió, pasar lista | creador, A, X, L | Asistencia | `/calendar/events/{id}/attendance*` | — |
| `cursos` | Classroom | todos | sección Cursos | `/cursos`, `/lecciones` | — |
| `ranking` | Ranking | todos | sección Ranking | `/ranking` | corte diario sigue (dato) |
| `ranking.podio_semanal` | Podio de los lunes | todos (lo reciben) | chat global | PodioDeLaSemanaScheduler | pospone |
| `semaforo` | Semáforo (propio, mentor, grupos, admin) | T, M/G, L, A, X | SemaforoScreen y afines | `/me/semaforo`, `/semaforo/**` | cierre semanal pospone |
| `ia` | SER entera (memoria, mensajes automáticos) | todos | RenasiaLauncher, panel | `/renasia/**` | mensajes de SER saltan |
| `ia.chat` | Chat de SER y del curso | todos | RenasiaPanel, ChatDelCurso | `POST /renasia/mensajes` | — |
| `ia.voz` | Voz del orbe y voz en vivo | todos | OrbeAcompanante | `/renasia/voz`, WS voz en vivo | — |
| `ia.propuestas` | Propuestas y acciones | todos | TarjetaPropuesta | `/renasia/propuestas/**` | — |
| `evidencias` | Mis evidencias | todos | Yo → evidencias | `/evidence` | cola de validación pospone |
| `caja` | Caja Renaser | T, A | MiCajaScreen, CajaLista | `/me/caja`, `/admin/caja/**` | barrido salta |
| `emergencia` | Botón de emergencia | T, A, X | EmergenciaScreen | `/me/emergency-request`, admin | — |
| `cuenta.eliminar` | Eliminar mi cuenta | todos | EliminarMiCuentaScreen | `/users/me/account-deletion` | purga sigue (legal) |
| `notificaciones` | Push y bandeja | todos | Yo → notificaciones | `/notifications`, `/push-tokens` | despacho pospone |
| `mentor.mi_grupo` | Mi grupo y fichas | M, G | MiCelulaScreen, AlumnoScreen | `/mentor/**` | avisos de acompañamiento saltan |
| `lider` | Mis mentores | L (A, X) | LiderMentoresScreen | `/leadership/**` | — |
| `tickets` | Bandeja de tickets | L, A, X | BandejaTicketsScreen | `/admin/tickets` | — |
| `admin` | Administración entera | A, X | AdminScreen | `/admin/**` | — |
| `admin.solicitudes` | Solicitudes | A, X | SolicitudesAdminScreen | `/account-requests` admin | — |
| `admin.equipo` | Equipo y roles | A, X | StaffRolesScreen | `/admin/staff`, `/users/{id}/role` | — |
| `admin.grupos` | Grupos y cohortes | A, X | GruposAdminScreen | `/admin/cells/**`, `/admin/cohorts/**` | armado y rotación saltan |
| `admin.personas` | Personas y fichas | A, X | PersonasAdminScreen | `/admin/trainees/**` | — |
| `admin.habitos` | Hábitos del programa | A, X | HabitosProgramaAdminScreen | `/admin/habits/**` | — |
| `admin.bienvenida` | Bienvenida | A, X | BienvenidaAdminScreen | `/admin/bienvenida/**` | bienvenidas saltan |
| `admin.fases` | Imágenes de fases | A, X | AnimalesDeFaseAdminScreen | `/phase-animals/**` escritura | — |
| `perfil` | Editar perfil y foto | todos | Yo → ajustes | `/users/me`, avatar | — |

Funcionalidades **críticas** (FR-008), propuesta: `chat`, `muro`, `ia`, `habitos`, `registro`, `notificaciones`, `admin` y
cualquier apagado «para todos».

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Apagar o prender una funcionalidad tiene efecto en el servidor en ≤ 5 s y en la app en ≤ 60 s, sin
  reiniciar el backend ni publicar un APK (medido en E2E).
- **SC-002**: El 100 % de los endpoints declara su funcionalidad o se marca anti-bloqueo (prueba de arquitectura).
- **SC-003**: Con el proveedor de IA fallando, SER se apaga sola en ≤ 2 min y Soporte TI recibe el push; vuelve sola
  cuando el proveedor responde.
- **SC-004**: En despliegue, ninguna persona alterna entre ver y no ver una funcionalidad sin un cambio de escalón (0
  oscilaciones en una prueba de 1000 personas × 10 lecturas).
- **SC-005**: El 100 % de los cambios (manuales, automáticos, ver como, arreglos) queda auditado.
- **SC-006**: Ningún rol distinto de Soporte TI accede a la consola (403 en todas las pruebas negativas) y ningún
  endpoint asigna el rol.
- **SC-007**: La consulta de estado agrega ≤ 2 ms p95 por pedido.

---

## Assumptions

- Habrá una sola cuenta Soporte TI al principio; el diseño admite más sin cambios.
- Las métricas de uso y error salen de lo que el servidor ya mide (logs y Alloy/Grafana del despliegue) más contadores
  propios por funcionalidad; no se agrega un proveedor externo de feature flags (LaunchDarkly, Unleash): se decide en el
  plan con la comparación.
- La app sigue sin actualización por aire: los avisos y estados llegan por API, los componentes nuevos con un APK.
- Los textos visibles van en español de Perú, tuteo.

## Fuera de alcance

- Escalón automático del despliegue (no lo pidió el dueño).
- Leer el contenido de chats privados.
- Suplantar («entrar como») una cuenta real; «ver como rol» usa datos de prueba propios.
- Un proveedor de IA alternativo (fallback a otro modelo): se evalúa aparte.
- Un CRUD total de roles (roles solo como datos, sin rol base en el código): exigiría reescribir los ~60 guards, los enums
  espejo de 8 módulos y el enum de Postgres. Los roles personalizados heredan de uno base (US7).

## Preguntas abiertas (para el plan)

1. Números del circuito de IA: fallas por ventana, esperas, máximo de push por hora.
2. Minutos de inactividad de la consola.
3. Lista cerrada de arreglos de soporte (US6).
4. ¿El Alquimista y el Admin pueden ver la auditoría en solo lectura?
5. Canal de los avisos a Alquimista/Admin: ¿push solamente, o también chat de soporte?
6. ¿Qué datos de prueba usa «ver como rol»: cuentas sintéticas fijas por rol o la cuenta Soporte TI con el rol simulado?
7. Hábitos por rol (D-264): ¿la audiencia de un hábito puede elegir un rol personalizado, o solo los seis base?
8. ¿«Ver como rol» incluye los roles personalizados?

---

## Fases y reparto con agentes

**Fase 1 (P1): interruptores + IA + Soporte TI.** Se reparte en paralelo, cada agente en su worktree y su rama, contra un
**contrato primero** (`contracts/funcionalidades.md`: forma del catálogo, `GET` del estado por sesión, error
`FUNCIONALIDAD_APAGADA`, endpoints de la consola):

| Agente | Repo | Entrega |
|---|---|---|
| A — núcleo | backend | catálogo, estado, caché e invalidación entre instancias, filtro por endpoint, prueba de arquitectura, auditoría, rol `SOPORTE_TI` y endpoints de la consola |
| B — IA | backend | interruptores `ia.*` en SER (chat, voz, propuestas), circuito automático y avisos |
| C — app | frontend | lectura del estado, componente de aviso, manejo del error, consola Soporte TI (lista por área, cambio con motivo y contraseña, auditoría) |
| D — QA | ambos | E2E en el emulador con la base clonada: apagar/prender por rol, APK viejo, circuito de IA, permisos negativos |

**Fase 2 (P2): despliegue progresivo + ver como rol + roles personalizados.** Un agente backend (asignación estable,
escalones, métricas), uno backend para roles personalizados (tabla, referencia en la cuenta, evaluación de interruptores,
asignación por Admin y Alquimista) y uno de app (controles de escalón, «ver como», CRUD de roles en la consola y el rol
nuevo en Equipo y roles y Solicitudes).

**Fase 3 (P3): datos de personas.** Un agente por lado, con la lista cerrada de arreglos ya confirmada.

Reglas para todos: un commit y una rama por funcionalidad; `clean verify` / `tsc` + `jest` en verde; E2E local antes de
subir; nunca contra producción.
