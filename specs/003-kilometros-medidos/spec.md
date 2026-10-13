# Feature Specification: Kilómetros medidos por el teléfono, sin instalar nada

**Feature Branch**: `003-kilometros-medidos` (spec-kit no crea ramas en este repo; la rama se abre al planificar)

**Created**: 2026-10-13

**Status**: Draft — decisiones del dueño cerradas; faltan las verificaciones en teléfonos reales (ver «Riesgos»).

**Input**: Pedido del dueño: «el spec para el hábito de kilómetros diarios: controlar desde la app, no con un reloj; si
es posible la compatibilidad de los dispositivos con el marcapasos (podómetro), y que funcione correctamente». Y, al
proponerle Health Connect: «no quiero que instalen otros aplicativos, busca más información actual».

**Repos**: app (`Renaser-90-dias-frontend-`, la mayor parte: módulo nativo, sincronización, pantallas) y backend
(`Renaser-90-dias-backend`, esta spec: recibir la medición, actualizarla durante el día, cierre al día siguiente).

---

## Contexto

- **Hoy (D-226, V84–V86)**: el hábito «KILÓMETROS DIARIOS» (`clave_sistema = DAILY_KM`, 07:00–23:59, paga completo todo el
  día) se cumple **a mano**: la persona escribe sus km y sube una captura (`registroConFoto`, `PREGUNTA_DE_KILOMETROS`).
  `PoliticaKilometros` acepta 0 < km ≤ 100. El ranking «Kilómetros» suma los km desde el Día 1
  (`KilometrosAcumuladosService`, corte diario 00:05 Lima).
- **Huecos del backend para medir solo** (investigación del 2026-10-13):
  - `RegistrarMedicionDiariaUseCase` existe pero **no tiene endpoint**;
  - `RegistroHabito.completar` lanza si el registro ya está COMPLETADO: hoy no se pueden **actualizar** los km durante el día;
  - D-259 rechaza registrar un día ya cerrado: una sincronización tardía (la de la mañana siguiente) se perdería;
  - `registros_habito.origen_medicion` solo admite `'MANUAL'` (CHECK de V84).
- **Cómo medir sin otra app** (investigación del 2026-10-13, fuentes al final):
  - **Recording API on mobile** (`com.google.android.gms:play-services-fitness:21.3.0`, agosto de 2025): Google Play
    services —que ya viene en el teléfono— graba **pasos y distancia** aunque nuestra app esté cerrada, sin cuenta de Google y
    sin instalar nada; guarda 10 días. Distancia desde Play services 25.05 (`250500000`); solo pasos desde 24.15. Pide el
    permiso «Actividad física» (`ACTIVITY_RECOGNITION`). No hay librería Expo/RN madura: va un **módulo Expo local** como
    `modules/renaser-alarmas`.
  - **Health Connect** sin otra app solo da pasos (no distancia) y solo en Android 14+ con la extensión 20: **fuera**.
  - **Pedometer de `expo-sensors`**: en Android no cuenta en segundo plano ni el día completo: **fuera**.
  - **Sensor propio con servicio permanente**: notificación fija, problemas en Xiaomi, declaración extra en Play: **fuera**
    (solo como idea futura para equipos sin Google).
  - **GPS** con `expo-location` en primer plano iniciado por la persona: no instala nada; pide ubicación «mientras se usa» y
    una declaración con video en Play.
- **Cobertura en Perú** (StatCounter, septiembre de 2026): 85,6 % Android; casi todo con Google; Huawei sin Google ≈ 1 %.
  iOS (14,4 %) queda para después (HealthKit).

## Clarifications

### Session 2026-10-13 (respuestas del dueño)

- Q: ¿De dónde salen los km? → A: **Sin instalar nada**. (Health Connect descartado; la investigación lleva a la Recording
  API de Play services.)
- Q: ¿Qué km cuentan? → A: **Todo lo caminado y corrido del día.** Bicicleta no.
- Q: ¿Medidos y escritos en el mismo ranking? → A: **Mismo ranking, con una marca «medido».** La captura sigue siendo
  obligatoria solo para lo escrito a mano.
- Q: ¿Cómo se actualizan durante el día? → A: **Solos**, sin volver a pagar puntos; lo que llegue tarde se acepta **hasta el
  mediodía del día siguiente**.
- Q: ¿Desde cuántos km cuenta como cumplido? → A: **Cualquier distancia** mayor que cero.
- Q: ¿Sesión con GPS? → A: **Sí, a pantalla completa marcando lo que avanza**, con el criterio de diseño de Emil Kowalski
  (el dueño: «al marcar, que ocupe toda la pantalla y vaya marcando lo que avanza… me gustaría así»).
- Q: ¿Y si el teléfono no puede medir? → A: **Sigue el registro manual con captura**, sin la marca «medido».

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Los km del día se cuentan solos (Priority: P1)

La persona da permiso de «Actividad física» una vez. Desde ahí, sin abrir nada, el teléfono va contando lo que camina y
corre. Cuando abre Hoy o Training ve «4,2 km hoy · medido», y el hábito KILÓMETROS DIARIOS ya figura cumplido.

**Why this priority**: es el pedido: que el hábito se controle desde la app, sin reloj ni otra app.

**Independent Test**: en un teléfono real con Play services al día, caminar 1 km con la app cerrada; al abrirla, el hábito
está cumplido con ≈ 1 km marcado «medido», pagó sus puntos una sola vez y el ranking del día siguiente suma esos km.

**Acceptance Scenarios**:

1. **Given** el permiso concedido y Play services ≥ 25.05, **When** la persona camina con la app cerrada, **Then** al abrir la
   app se leen los pasos y la distancia del día (desde las 00:00 de su zona) y se mandan al servidor.
2. **Given** la primera medición del día mayor que 0, **When** llega al servidor, **Then** el registro de KILÓMETROS DIARIOS se
   completa con esos km, origen «teléfono», y paga sus puntos (10, todo el día).
3. **Given** el hábito ya cumplido, **When** llega una medición mayor, **Then** se actualizan los km **sin volver a pagar
   puntos**; una medición menor que la guardada se ignora (los km del día solo suben).
4. **Given** que la persona no abre la app, **When** pasa el día, **Then** una tarea en segundo plano (`expo-background-task`,
   cada ≥ 15 min cuando el sistema lo permita) sincroniza igual.
5. **Given** la medición de ayer que llega hoy antes de las 12:00 de su zona, **Then** se actualizan los km de ayer (cuentan
   para el ranking); **después de las 12:00** se ignoran.
6. **Given** km medidos, **Then** en Training, Hoy y el ranking se ve la marca «medido».

---

### User Story 2 — Pedir el permiso bien y explicar cuando no se puede medir (Priority: P1)

La primera vez que la persona abre KILÓMETROS DIARIOS, la app le explica en una línea qué se mide y para qué, y pide el
permiso. Si el teléfono no puede medir, se lo dice claro y le deja el registro manual de siempre.

**Why this priority**: sin permiso no hay medición, y un estado mal explicado se vuelve un reclamo al soporte.

**Independent Test**: los seis estados del módulo (`listo`, `solo_pasos`, `sin_permiso`, `sin_sensor`,
`actualizar_play_services`, `sin_play_services`) muestran cada uno su texto y su acción.

**Acceptance Scenarios**:

1. **Given** `sin_permiso`, **When** abre la tarjeta, **Then** ve «Contamos tus pasos y kilómetros con el teléfono, sin
   instalar nada» y «Permitir»; si lo niega, queda el registro manual.
2. **Given** `actualizar_play_services`, **Then** ve «Actualiza Google Play services para medir tus kilómetros» con un botón
   que abre la actualización del sistema (no es otra app).
3. **Given** `solo_pasos` (Play services entre 24.15 y 25.05), **Then** los km salen de pasos × zancada y se marcan
   «estimado».
4. **Given** `sin_sensor` o `sin_play_services` (p. ej. Huawei sin Google), **Then** ve «Tu teléfono no puede contar pasos.
   Registra tus km a mano con una captura» y el flujo manual de hoy.
5. **Given** el permiso revocado más tarde, **When** abre la app, **Then** vuelve al estado `sin_permiso` y se resuscribe al
   concederlo.

---

### User Story 3 — Registrar una caminata o carrera con GPS, a pantalla completa (Priority: P2)

La persona toca «Registrar caminata o carrera», elige cuál, y la app ocupa toda la pantalla mostrando en grande los km que
lleva, el tiempo y el ritmo, mientras camina o corre. Puede pausar; para terminar mantiene presionado. Al final ve un
resumen y esos km se suman a los del día sin contarse dos veces.

**Why this priority**: es más preciso y cubre teléfonos sin sensor de pasos; va después porque lo automático ya resuelve
el hábito.

**Independent Test**: una sesión de 1 km medido en una ruta conocida marca 1,0 ± 0,1 km; con la pantalla apagada sigue
contando (notificación visible); los km del día no se duplican con lo que contó el teléfono en esos minutos.

**Acceptance Scenarios**:

1. **Given** el permiso de ubicación «mientras se usa», **When** toca «Empezar», **Then** cuenta regresiva 3-2-1, la pantalla
   queda encendida y arranca un servicio en primer plano con notificación «Registrando tu caminata · 1,24 km».
2. **Given** la sesión en curso, **Then** la pantalla muestra km (cifra principal), tiempo y ritmo (min/km), y el trazo del
   recorrido como línea sobre fondo liso (sin mapa).
3. **Given** cada kilómetro completo, **Then** una vibración corta y el número del km se marca una vez.
4. **When** toca «Pausar», **Then** se detiene el conteo y se ve «En pausa»; «Seguir» lo reanuda.
5. **When** mantiene presionado «Terminar» ~1 s, **Then** se cierra la sesión y se ve el resumen; soltar antes no termina.
6. **Given** el fin de la sesión, **Then** los km del día = distancia del teléfono − la del teléfono durante la sesión + la del
   GPS.
7. **Given** una ubicación falsa (`mocked`) o saltos imposibles, **Then** esos puntos no suman.
8. **When** el sistema mata la app en plena sesión, **Then** al volver ofrece «Seguir» o «Terminar» con lo ya registrado.

---

### Edge Cases

- **Primer día**: la Recording API solo tiene datos desde que se suscribe; el primer día cuenta desde la hora del permiso y la
  tarjeta lo dice («Contamos desde las 10:42»).
- **Dos cuentas en el mismo teléfono**: la suscripción es del teléfono; la app manda los km solo de la cuenta con sesión y
  desde que esa cuenta inició sesión en el día.
- **Cerrar sesión**: no se desuscribe (desuscribir borra lo grabado).
- **Cambio de zona horaria o medianoche**: el día es el de la zona de la persona (`timezone` de su participación); la app pide
  el total del día en esa zona.
- **11 días sin abrir la app ni sincronizar**: se pierde lo que Play services ya no guarda; sin reclamo posible.
- **Cifras imposibles**: más de 100 km en el día o cadencia sostenida > 230 pasos/min se rechazan o se marcan para revisión
  (agitar el teléfono infla los pasos).
- **Medición más baja que la guardada** (otra fuente, reinicio): se ignora.
- **Registro manual y medido el mismo día**: gana el mayor; si lo manual era mayor, queda manual con su captura.
- **Hábito pausado o no inscrito**: no se crea el registro; la app no muestra km.
- **APK viejo**: sigue con el manual; el backend sigue aceptando la captura.
- **Sin red**: la app guarda la última lectura y la manda al volver la red (dentro del plazo de mediodía).
- **Persona en el día 0 o fuera del programa**: no se sincroniza nada.

---

## Requirements *(mandatory)*

### Functional Requirements

**App: medición**
- **FR-001**: Un módulo Expo local (`modules/renaser-pasos`, Kotlin) MUST exponer `estado()`, `suscribir()` y
  `leerHoy(zona)` sobre `LocalRecordingClient` (`TYPE_STEP_COUNT_DELTA`, `TYPE_DISTANCE_DELTA`), con los seis estados de
  US2. MUST NOT ofrecer desuscribir al cerrar sesión.
- **FR-002**: La app MUST suscribir en cada arranque (no hace nada si ya existe) y leer el total del día al abrir Hoy o
  Training y en segundo plano con `expo-background-task`.
- **FR-003**: Con `solo_pasos`, los km MUST calcularse como pasos × zancada (a confirmar: estatura × 0,414 si hay estatura;
  si no, 0,70 m) y marcarse «estimado».
- **FR-004**: El permiso MUST pedirse con una explicación previa en la tarjeta del hábito, nunca al abrir la app.

**Backend: recibir la medición**
- **FR-005**: Endpoint propio del dueño (`@RequiresPermission(USE_APP)`, dueño del registro, cuenta activa) para mandar la
  medición del día: fecha local, metros, pasos, origen (`TELEFONO`, `TELEFONO_ESTIMADO`, `GPS`), cadencia máxima opcional.
- **FR-006**: La primera medición > 0 del día MUST completar el registro de `DAILY_KM` con los puntos de siempre
  (`ResultadoOtorgamiento`); las siguientes MUST actualizar `valor_medido` solo si es mayor, **sin pagar de nuevo**.
- **FR-007**: Una medición de **ayer** MUST aceptarse hasta las 12:00 de la zona de la persona, solo para actualizar km (no
  completa ni paga puntos si el día ya cerró sin registro: mantiene D-259 para los puntos).
- **FR-008**: `origen_medicion` MUST admitir los nuevos orígenes (migración propia, sin editar V84), y el registro manual
  sigue exigiendo la captura.
- **FR-009**: El ranking «Kilómetros» MUST seguir sumando todos los km y la respuesta MUST decir, por fila, si los km del
  día vienen medidos (campo aditivo).
- **FR-010**: Validaciones del servidor: 0 < km ≤ 100 por día, pasos ≥ 0, cadencia ≤ 230; lo que no pasa se rechaza con
  400 y queda en el log.

**App: sesión con GPS (P2)**
- **FR-011**: Pantalla completa con km, tiempo, ritmo, trazo sin mapa, pausa, terminar manteniendo presionado, resumen, con
  `expo-location` en primer plano (sin ubicación en segundo plano) y la pantalla encendida.
- **FR-012**: Al terminar, los km del GPS MUST reemplazar a los del teléfono en esos minutos (sin doble conteo).
- **FR-013**: Descartar puntos con precisión > 20 m, ubicación falsa y velocidades imposibles (> 25 km/h caminando o
  corriendo).

### Diseño de la sesión con GPS (criterio de Emil Kowalski)

| Elemento | Decisión | Por qué |
|---|---|---|
| Km en vivo | Cifra grande, `tabular-nums`, **sin animación** al cambiar (cambia cada segundo) | lo que se ve decenas de veces por minuto no se anima; las cifras tabulares no bailan |
| Cada km completo | vibración corta + el número del km se marca una vez (opacidad, < 300 ms) | un momento raro sí merece un detalle |
| Empezar | cuenta regresiva 3-2-1, cada número entra con escala 0.96 → 1 y opacidad, ease-out 200 ms | da tiempo a guardar el teléfono; nada aparece de la nada |
| Pausar | toque simple, escala 0.97 al presionar | respuesta inmediata |
| Terminar | **mantener presionado ~1 s** con un relleno que avanza (lineal) y vuelve rápido al soltar (ease-out 200 ms) | evita terminar por error con el teléfono en el bolsillo |
| Trazo | línea dorada sobre fondo liso, se dibuja con lo recorrido; sin mapa de terceros | no requiere clave de mapas ni otra dependencia; foco en el avance |
| Resumen | entra desde abajo como `HojaDesdeAbajo` | coherente con el resto de la app |
| Reducir movimiento | sin escalas ni relleno animado: solo fundidos | accesibilidad |

### Key Entities

- **MedicionDelDia** (en el registro de `DAILY_KM`): fecha local, metros, pasos, origen, cadencia máxima, actualizado en.
- **SesionGps** (app, y resumen en el servidor si se decide en el plan): inicio, fin, pausas, metros, tipo (caminata o
  carrera), puntos descartados.
- **EstadoDelSensor** (app): uno de los seis estados de FR-001.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En un Pixel y en un Xiaomi reales, 1.000 pasos contados a mano difieren ≤ 5 % de lo que lee la app, y 1 km
  de ruta conocida difiere ≤ 10 % en distancia del teléfono y ≤ 5 % con GPS.
- **SC-002**: Con la app cerrada desde recientes y el teléfono bloqueado 4 horas, los pasos de ese tiempo aparecen al
  abrir la app (Pixel, Samsung A, Xiaomi HyperOS, Motorola, Honor).
- **SC-003**: El hábito paga puntos una sola vez por día aunque lleguen 50 mediciones.
- **SC-004**: Ningún teléfono sin Google se queda sin forma de cumplir (manual con captura).
- **SC-005**: La batería no baja más de 2 % en 24 horas por la medición automática.

## Assumptions

- Android primero; iOS (HealthKit) en otra spec.
- Se mantiene `play-services-fitness` 21.3.0 o superior; si Google anuncia el retiro de la Recording API, se evalúa Health
  Connect con sus límites.
- Hace falta un APK nuevo (módulo nativo) y actualizar en Play: declaración de apps de salud («Actividad y fitness»),
  Seguridad de los datos (salud y fitness; ubicación para el GPS) y la Política de privacidad (`/privacidad/`).

## Fuera de alcance

- Bicicleta y otros deportes.
- Health Connect, Samsung Health, Google Fit, relojes.
- Ubicación en segundo plano sin una sesión iniciada por la persona.
- Huawei Health Kit.

## Riesgos

- **Continuidad**: la Recording API vive en el mismo artefacto que la API de Google Fit, que se retira a fines de 2026; no
  hay anuncio de retiro de la Recording API, pero la guía de migración de Fit ya apunta a Health Connect.
- **Exactitud de la distancia**: no está documentado cómo la calcula Play services (probablemente pasos × zancada
  estimada): se mide en las pruebas de SC-001.
- **Play Store**: la sesión GPS necesita declarar un servicio en primer plano de ubicación con video; el uso «ejercicio
  iniciado por la persona» no figura literal en la lista: puede pedir aclaraciones.
- **Cualquier distancia cumple**: con la decisión del dueño, casi toda persona que lleva el teléfono cumple el hábito; la
  diferencia se ve en el ranking.

## Preguntas abiertas (para el plan)

1. Zancada por defecto sin estatura (0,70 m) y si se pide la estatura en el perfil.
2. ¿La sesión GPS se guarda en el servidor (historial de caminatas) o solo suma km?
3. ¿Qué pasa con los km del día cuando el hábito está pausado: se miden igual para el ranking o no?

## Pruebas en dispositivo real (el emulador no tiene sensor de pasos)

1. Pixel con Android 15–16: 1.000 pasos y 1 km de ruta conocida (SC-001).
2. Redmi/Xiaomi HyperOS, Samsung A, Motorola, Honor: app cerrada 2–4 h, reinicio a mitad del día, ahorro de batería,
   «forzar detención».
3. Un equipo sin sensor de pasos y un Android Go: estados `sin_sensor` y `sin_play_services`.
4. Play services viejo: pedido de actualizar.
5. Permiso negado, revocado y quitado por falta de uso.
6. 11 días sin abrir la app.
7. Medianoche, cambio de zona y dos cuentas en el mismo teléfono.
8. Sesión GPS de 30 min con pantalla apagada en Xiaomi.
9. 10 min caminando y 10 trotando.
10. Batería en 24 horas.

## Fases y reparto con agentes

| Fase | Agente | Repo | Entrega |
|---|---|---|---|
| 1 | A — nativo | app | `modules/renaser-pasos`, estados, permiso, lectura del día, tarea en segundo plano |
| 1 | B — backend | backend | endpoint de medición, actualizar sin repagar, ayer hasta mediodía, orígenes, marca en el ranking |
| 1 | C — app | app | tarjeta del hábito con estados y «medido», sincronización, manual de respaldo |
| 1 | D — QA | ambos | pruebas en teléfonos reales de la lista, base clonada, nunca producción |
| 2 | E — GPS | app | pantalla de sesión con el diseño de arriba, servicio en primer plano, sin doble conteo |

## Fuentes

- Recording API on mobile: https://developer.android.com/health-and-fitness/guides/recording-api
- `LocalRecordingClient`: https://developers.google.com/android/reference/com/google/android/gms/fitness/LocalRecordingClient
- Notas de versión de Play services (21.3.0, 2025-08-05): https://developers.google.com/android/guides/releases
- Pasos en Health Connect: https://developer.android.com/health-and-fitness/health-connect/features/steps
- Expo Location SDK 57: https://docs.expo.dev/versions/v57.0.0/sdk/location/
- Declaración de apps de salud en Play: https://support.google.com/googleplay/android-developer/answer/14738291
- Servicios en primer plano en Play: https://support.google.com/googleplay/android-developer/answer/13392821
- StatCounter Perú: https://gs.statcounter.com/android-version-market-share/mobile-tablet/peru
