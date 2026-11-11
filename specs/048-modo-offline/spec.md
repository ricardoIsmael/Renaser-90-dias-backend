# Feature Specification: La app funciona sin internet y envía sola lo que hiciste cuando vuelve la señal

**Feature Branch**: `048-modo-offline`
**Created**: 2026-11-09
**Status**: Aprobada por el dueño (2026-11-09) con las decisiones de abajo, tomadas en una sesión de preguntas por rondas. Fase 1 en curso.
**Input**: Pedido del dueño (2026-11-09), textual: «vamos con el modo offline […] me imagino que el login debe de durar siempre o la primera vez sí debe de estar conectado con conexión». Y sobre el alcance: «que podamos realizar que se guarde y como si fuera normal, pero lo del muro, la publicación diaria, entraría en cola, que apenas esté un poco de conexión cargue directamente […] el tema de audio y audioterapias, y SER quedaría deshabilitado». Sobre los rechazos: «esto no debe de pasar». Sobre las reglas: «en modo offline sí es posible que se respeten las reglas […] el registro de la hora que se hizo y luego en base a eso el servidor descarte lo que sí y lo que no».

## Decisiones del dueño (2026-11-09)

| # | Pregunta | Decisión |
|---|---|---|
| D1 | Login | Solo el primer ingreso pide internet. La falta de señal nunca cierra la sesión: se cierra al salir a mano o cuando el servidor la da por vencida (hoy, 30 días sin uso). |
| D2 | Alcance | Hacer «como si hubiera señal»: hábitos (con texto o foto), publicación del Muro, Plan (acciones y objetivos), Cierra tu día / bitácora, check-in del Radar y planificar hábitos y alarmas. Todo entra en una cola y se envía solo. |
| D3 | Lo que necesita internet | SER, chat, Pagos, Eventos y Meet, Código Renaser y los videos: mensaje claro «Necesitas conexión para usar esto», nunca un hueco en blanco, y vuelven solas con la señal. |
| D4 | Aviso | Un ícono discreto; al tocarlo, un globo: «Sin conexión desde las 10:42 · 3 cosas por enviar». Con señal y la cola vacía no aparece. |
| D5 | Puntos | Cuenta la hora en que lo hiciste, no la hora en que llega. |
| D6 | Audios | Con red, la app baja sola el audio de Espíritu y la audioterapia de hoy y mañana; se borran a los 7 días. |
| D7 | Muro | La publicación queda en tu Muro marcada «Se publicará al volver la señal»; se puede borrar antes; los videos esperan wifi o buena señal. |
| D8 | Cola | Una sola cola por cuenta, en el orden en que hiciste las cosas; ningún envío se duplica aunque se reintente. |
| D9 | Plataformas | Solo la app instalada (Android y iPhone). La web sigue pidiendo internet. |
| D10 | Roles | Ver sin red: todos los roles. Escribir sin red: solo lo propio. Aprobar, responder o moderar a otros sigue pidiendo internet. |
| D11 | Datos en el teléfono | Separados por cuenta y borrados al cerrar sesión; lo pendiente nunca sale con otra cuenta; si al salir queda algo sin enviar, se avisa antes. |
| D12 | Al marcar sin red | El check sale al instante y las alarmas de ese día se apagan; los puntos y la celebración salen cuando el servidor confirma. |
| D13 | Choques | Si en el servidor cambió después de lo que hiciste, gana lo del servidor y se avisa qué no se aplicó. Si no, se aplica lo tuyo. |
| D14 | Nada rebota | En el teléfono rigen las mismas reglas que con red. Se guarda la hora real en que lo hiciste. El servidor revisa con esa hora: lo que estaba en regla y llega dentro de 48 h suma sus puntos; lo demás queda registrado sin puntos y la app lo muestra así. Nada se pierde en silencio. |
| D15 | Sesión vencida con pendientes | La app pide volver a entrar; si entra la misma cuenta, la cola se envía; con otra cuenta, nunca. |
| D16 | Entrega | Tres fases, cada una con su PR, su E2E y su APK. Fase 1: sesión, ver sin red, hábitos, puntos por hora real, audios y el ícono. Fase 2: Muro. Fase 3: Plan, Cierra tu día, Radar y planificar hábitos. |
| D17 | Post diario sin red (fase 2, 2026-11-09) | El hábito «Post diario en comunidad» hecho sin señal cuenta el día en que se escribió, por la hora real (mismas reglas que D14: 48 h, sin hora futura). |
| D18 | Lugar en el Muro (fase 2) | Al llegar, la publicación aparece arriba, como nueva, para que la tribu la vea; solo el hábito usa la hora real. |
| D19 | Mientras espera (fase 2) | Solo se puede borrar. Editar, ya publicada, como cualquier otra. |
| D20 | «Bitácora» (fase 3, 2026-11-10) | Es Cierra tu día: la nota del día se guarda ahí y no existe otra bitácora. Una sola cosa. |
| D21 | Agendar un día que ya pasó al llegar (fase 3) | No se aplica y se avisa (`FUERA_DE_SU_DIA`): crear acciones en un día vivido distorsiona el avance de la semana. |
| D22 | Tope de 48 h (fase 3) | Las 48 h solo quitan puntos y solo al marcar una acción. Lo que no da puntos (Cierra tu día, Código Renaser, planificar) no tiene tope de llegada: lo decide el día y el choque (D13). |
| D23 | Código Renaser de una hora que el servidor ya tiene (fase 3) | Se queda el del servidor y se avisa (`YA_ESTABA_HECHO`). Nunca dos por hora. |
| D24 | Código Renaser que ya no corresponde al llegar (fase 3) | No se aplica y se avisa (`MI_RADAR_NO_APLICA`); el texto que la persona escribió va en el aviso para que lo copie. |
| D25 | Acciones agendadas sin señal (fase 3) | No se pueden marcar hasta que lleguen al servidor («Se agendará al volver la señal»): el `id` lo pone el servidor. |
| D26 | Cierra tu día con una acción que ya no existe (fase 3) | Todo o nada: no se aplica y se avisa (`RECURSO_YA_NO_EXISTE`); el aviso guarda lo escrito. |
| D27 | Código Renaser innegociable (días 1 a 7) sin señal (fase 3) | Vuelve a abrirse solo sin señal y se responde y se encola (revierte el límite de la fase 1). |
| D28 | Cómo se avisa lo que no se aplicó (fase 3) | Una hoja corta la próxima vez que se abre Hoy; el globo del ícono dice «1 cosa no se aplicó» hasta que se ve. No hay bandeja histórica. |
| D29 | La Pastilla Renacer sin señal (#186, 2026-11-11) | Se registra con la hora real en que se entregó (mismas reglas de D14, FR-010 y FR-015), no con la de llegada: la entregada antes del mediodía cuenta **a tiempo** aunque llegue pasada esa hora o al día siguiente, y una sola vez (idempotente por `X-Id-Cliente`). Si la hora real cae en un día que ya cerró para ese audio, **no se aplica y se avisa** (`FUERA_DE_SU_DIA`, igual que D21): el audio siguiente es otro y nunca recibe el resumen de ayer; no hay un «PERDIDO» que rebote. Si ese día ya estaba entregado, `YA_ESTABA_HECHO` (o `CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR` si se entregó desde otro lado después de lo que hiciste). El hábito «Pastilla Renacer» se cierra con esa hora; pasadas 48 h o con hora futura queda sin puntos y la respuesta lo dice (`motivoSinPuntos`). *Decisión técnica derivada de D14, D21 y FR-010: el dueño no la ha confirmado, se confirma al revisar el PR.* |

## Lo que hay hoy (leído el 2026-11-09)

- **La sesión se pierde al abrir sin red.** `AuthContext` (app) rehidrata la sesión pidiendo `/me`; cualquier fallo, también «sin red», cae en el mismo `catch` que «token vencido» y borra el token.
- **La sesión del servidor** dura `spring.session.timeout: 30d` sin uso y cada pedido la renueva.
- **Ya hay una cola que funciona sin red**: la de la carrera con GPS (spec 042, `colaDeCarreras.ts` + `colaDeEnvio.ts`): por cuenta, idempotente por `carreraId`, se procesa al terminar, al volver al frente y con un reloj.
- **Los puntos dependen de la hora del servidor**: `RegistroService.completar` usa `clock.now()` contra la ventana del registro, y `ExpiracionDeRegistrosService` pasa a `EXPIRADO` lo vencido.
- **La evidencia con foto** pide una URL firmada (`/evidence/upload-url`), sube a S3 y confirma (`/evidence`): los tres pasos necesitan red.
- **La app ya mide el desfase con el reloj del servidor** (`relojServidor.ts`, con el header `Date` de cada respuesta), pero no tiene un reloj que no cambie cuando la persona cambia la hora del teléfono.

## User Scenarios & Testing

### Historia 1 — Abrir la app sin señal y seguir adentro (P1, fase 1)

Ana entró ayer con internet. Hoy abre la app en el metro, sin señal: entra directo a Hoy con lo último que cargó, y ve el ícono de sin conexión.

**Acceptance**:
1. **Dado** una sesión guardada y vigente, **cuando** la app se abre sin red, **entonces** entra a la pantalla de inicio con el último perfil y los últimos datos cargados, sin pasar por el login.
2. **Dado** que el servidor responde que la sesión venció (401 con sesión mandada), **cuando** la app lo recibe, **entonces** lleva al login como hoy.
3. **Dado** una cuenta suspendida mientras la app estuvo sin red, **cuando** vuelve la señal, **entonces** la app se entera con la primera respuesta del servidor y aplica lo de hoy (sin dejarla usar la app).

### Historia 2 — Marcar un hábito sin señal (P1, fase 1)

Luis hace su Escritura libre a las 21:10 en una casa de campo sin señal. La marca: sale el check y se callan sus alarmas de esa noche. Al día siguiente, en la ciudad, la app la envía sola y recién ahí ve sus puntos y la celebración.

**Acceptance**:
1. **Dado** un hábito dentro de su ventana, **cuando** se marca sin red, **entonces** queda hecho en la pantalla al instante, se apagan sus alarmas de ese día y se guarda en la cola con la hora real.
2. **Dado** un hábito fuera de su ventana o que pide una evidencia que falta, **cuando** se intenta marcar sin red, **entonces** la app aplica la misma regla que con red y lo dice igual.
3. **Dado** un envío en la cola, **cuando** vuelve la señal, **entonces** se envía solo y, con la respuesta, salen los puntos y la celebración.
4. **Dado** que lo marcado se hizo a tiempo, **cuando** llega después de que el día cerró, **entonces** el servidor lo acepta con los puntos que tocaban a esa hora.
5. **Dado** que llega más de 48 h después, o la hora no está en regla, **cuando** el servidor lo revisa, **entonces** queda registrado sin puntos y la app lo muestra así: nunca rebota ni se pierde.
6. **Dado** un reintento del mismo envío (se cortó la señal a mitad), **cuando** llega dos veces, **entonces** se registra una sola vez.

### Historia 3 — Hábito con foto sin señal (P1, fase 1)

**Acceptance**:
1. **Dado** un hábito que pide foto, **cuando** se toma sin red, **entonces** la foto se guarda en el teléfono y el hábito queda hecho en la pantalla.
2. **Cuando** vuelve la señal, **entonces** la foto se sube y el hábito se cierra con la hora en que se tomó.

### Historia 4 — Escuchar el audio de hoy sin señal (P1, fase 1)

**Acceptance**:
1. **Dado** que hubo red en algún momento del día anterior o de hoy, **cuando** se abre el audio de Espíritu o la audioterapia de hoy sin red, **entonces** se escucha desde el teléfono.
2. Los audios bajados se borran a los 7 días.

### Historia 5 — Lo que necesita internet (P1, fase 1)

**Acceptance**:
1. **Dado** que no hay red, **cuando** se abre SER, el chat, Pagos, Eventos, el Código Renaser o un video, **entonces** se ve «Necesitas conexión para usar esto», sin hueco en blanco.
2. **Cuando** vuelve la señal, **entonces** esa pantalla carga sola.

### Historia 6 — Publicar en el Muro sin señal (P2, fase 2)

La publicación aparece en el Muro marcada «Se publicará al volver la señal», se puede borrar antes y sube sola. El Muro no
tiene videos (solo fotos), así que la regla de los videos de D7 no aplica.

**Acceptance Scenarios**:
1. **Given** sin señal, **When** escribo una publicación con foto y toco Publicar, **Then** queda arriba en mi Muro marcada
   «Se publicará al volver la señal» y el «Post diario» de Entrenar sale hecho al instante.
2. **Given** esa publicación esperando, **When** la borro, **Then** desaparece y no sale nunca.
3. **Given** la escribí a las 21:00 y la señal vuelve a la mañana siguiente, **When** llega, **Then** la tribu la ve arriba
   como nueva y el «Post diario» de ayer queda cumplido con sus puntos (D17, D18).
4. **Given** el envío se repite (corte a mitad), **Then** la publicación existe una sola vez.

### Historia 7 — Plan, Cierra tu día, Radar y planificar sin señal (P2, fase 3)

Las mismas reglas que con red, en la misma cola, con la regla de choques D13.

## Requirements

### Sesión

- **FR-001** La app MUST NOT borrar la sesión guardada por un fallo de red (sin respuesta del servidor). Solo la borra al salir a mano o con una respuesta del servidor que diga que la sesión venció o que la cuenta no puede entrar.
- **FR-002** Al abrir sin red con una sesión guardada, la app MUST entrar con el último perfil guardado de esa cuenta y revalidarlo con el servidor apenas haya respuesta.
- **FR-003** Si la sesión vence con envíos pendientes, la cola MUST conservarse y enviarse solo cuando vuelva a entrar la misma cuenta (D15).

### Ver sin red

- **FR-004** Las lecturas de Hoy, de los hábitos de hoy (Entrenar) y del Plan MUST mostrarse sin red con lo último cargado por esa cuenta.
- **FR-005** SER, chat, Pagos, Eventos, Código Renaser y videos MUST mostrar sin red «Necesitas conexión para usar esto» (sin hueco en blanco) y cargar solos al volver la señal.
- **FR-006** La app MUST mostrar un ícono discreto mientras no haya señal o quede algo por enviar; al tocarlo, un globo con desde cuándo no hay conexión y cuántas cosas faltan enviar. Con señal y la cola vacía, no aparece.

### La cola

- **FR-007** Toda escritura hecha sin red MUST guardarse en una sola cola por cuenta, en orden, con un identificador propio (`idCliente`) y la hora real en que se hizo; MUST enviarse sola al volver la señal, al volver la app al frente y con un reloj mientras está abierta.
- **FR-008** El servidor MUST registrar una sola vez un envío repetido con el mismo `idCliente` y devolver el mismo resultado.
- **FR-009** La cola y lo guardado para ver sin red MUST quedar separados por cuenta y borrarse al cerrar sesión; si al salir queda algo sin enviar, la app MUST avisar antes cuántas cosas se perderían.

### Hábitos sin red (fase 1)

- **FR-010** Marcar un hábito sin red MUST aplicar las mismas reglas que con red (ventana, evidencia que pide, cierre del día), calculadas con la hora real.
- **FR-011** Al marcarlo sin red, el hábito MUST verse hecho al instante y sus alarmas de ese día MUST apagarse; los puntos y la celebración MUST salir con la respuesta del servidor.
- **FR-012** La foto de un hábito MUST guardarse en el teléfono y subirse al volver la señal, antes de cerrar el hábito.

### La hora real y los puntos

- **FR-013** La hora real de lo hecho sin red MUST calcularse como la última hora conocida del servidor más el tiempo transcurrido desde entonces según el reloj del sistema desde el encendido, que no cambia cuando la persona cambia la hora del teléfono. Si el teléfono se reinició sin señal, MUST usarse su reloj corregido con el último desfase conocido, y el envío MUST decirlo (`origenDeLaHora`).
- **FR-014** `POST /habit-tracks/{id}/complete` MUST aceptar `hechoEn` (instante) e `idCliente`. Con `hechoEn`, el servidor MUST calcular los puntos con esa hora en vez de la de llegada, aunque el registro ya figure vencido por el cierre del día.
- **FR-015** El servidor MUST registrar sin puntos (nunca rechazar) lo que llegue más de 48 h después de `hechoEn`, lo que tenga un `hechoEn` futuro o anterior a la ventana, y lo marcado con la hora corregida del teléfono que no cumpla esas mismas condiciones. La respuesta MUST decir por qué quedó sin puntos, y la app MUST mostrarlo.
- **FR-016** Lo que no se puede aplicar (hábito quitado, cuenta suspendida) MUST quedar anotado en el servidor para Soporte TI y la app MUST mostrar que no se aplicó; MUST NOT perderse en silencio.

### Audios (fase 1)

- **FR-017** Con red, la app MUST bajar sola el audio de Espíritu y la audioterapia de hoy y de mañana, y reproducirlos desde el teléfono sin red; MUST borrar los bajados de más de 7 días.

### Fases 2 y 3

- **FR-018** (fase 2) La publicación del Muro hecha sin red MUST entrar a la cola, verse en el Muro marcada «Se publicará al volver la señal», poder borrarse antes de salir (D19) y subir sola con sus fotos.
- **FR-020** (fase 2) Al llegar, la publicación MUST guardarse con la hora de llegada (aparece arriba, D18) y el hábito «Post diario en comunidad» MUST cerrarse por la hora real con las reglas de D14 (D17); un envío repetido MUST quedar una sola vez.
- **FR-021** (fase 2) Sin red, el compositor del Muro MUST abrirse (con las categorías guardadas) y el Muro MUST mostrar las publicaciones propias en espera; el resto del Muro dice que se verá al volver la señal.
- **FR-019** (fase 3) Plan (acciones y objetivos), Cierra tu día / bitácora, check-in del Radar y planificar hábitos y alarmas MUST funcionar sin red con la misma cola; en un choque MUST ganar lo más nuevo y avisarse qué no se aplicó (D13).

### Fuera de alcance

- La versión web (D9). Escribir sin red sobre datos de otros (D10). Bajar todos los audios del programa (D6).

## Success Criteria

- **SC-001** Abrir la app sin red con una sesión vigente entra a la pantalla de inicio en el 100 % de los casos.
- **SC-002** Un hábito marcado sin red dentro de su ventana suma los mismos puntos que con red si llega dentro de 48 h.
- **SC-003** Un mismo envío repetido queda registrado una sola vez.
- **SC-004** Ninguna pantalla de la fase 1 muestra un hueco en blanco sin red.
- **SC-005** (fase 2) Una publicación hecha sin red llega una sola vez y su «Post diario» suma los mismos puntos que con red si llega dentro de 48 h.

## Supuestos

- La duración de la sesión del servidor (30 días sin uso) no cambia.
- El reloj del sistema desde el encendido sale de un módulo nativo chico (Android `SystemClock.elapsedRealtime`, iOS `ProcessInfo.systemUptime`); la fase 1 lleva APK nuevo.
