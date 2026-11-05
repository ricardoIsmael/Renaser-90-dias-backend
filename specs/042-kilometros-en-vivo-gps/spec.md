# Feature Specification: Kilómetros en vivo con GPS

**Feature Branch**: `042-kilometros-en-vivo-gps` (backend y app)
**Created**: 2026-11-05
**Status**: Backend implementado (2026-11-05, D-323, migración V170). El dueño respondió los supuestos el 2026-11-05: ver «Decisiones del dueño». La app queda por implementar.
**Input**: Decisiones del dueño del 2026-11-05 sobre el lienzo de diseño (https://claude.ai/artifact/ReXHkKhQPazr1ZvdDwKFps, fila
«Elegida»): estilo adidas Running con los botones de Renaser (redondeados, tipo oración, dorado `#B2924F`, fondo crema). Pedido explícito
del dueño de explicar **la idempotencia y que no se llame al servidor a cada rato**. El mismo día sumó: **«el avatar debe reflejar el
movimiento real al instante, incluso 4 pasos»**.
**Se apoya en**: spec 037 (`POST /api/v1/habit-tracks/{id}/auto-measurement`, `PoliticaKilometros`, `registros_habito.valor_medido` /
`origen_medicion`) y D-226 (el hábito KILÓMETROS DIARIOS, `clave_sistema = DAILY_KM`). No cambia la 037 salvo lo que dice la regla de km
del día (FR-022).

## Clarifications

### Session 2026-11-05 (decisiones del dueño)

- **Flujo elegido.** Tocar «Kilómetros» en Training → cuenta regresiva 3, 2, 1 (pitido, voz y vibración) → **Corriendo** (tiempo grande,
  distancia, ritmo min/km, calorías; pestañas Mapa / Estadísticas / Tramos / Voz; mapa 3D inclinado con edificios, **sin botón 2D/3D**;
  barra «Desliza para pausar») → **Pausa** («Tu carrera queda guardada, aunque cierres la app»; tramos, estadísticas, voz y sonidos,
  bloquear pantalla; Seguir / Terminar / Descartar) → **Terminaste** (mapa, km, duración, ritmo, calorías, tramos, «Registrar X km en mi
  hábito», «Compartir en el Muro»).
- **Estadísticas:** distancia, ritmo medio, ritmo actual, velocidad, calorías aprox., pasos, pasos por minuto, subida acumulada.
- **Voz:** avisos cada km / cada 5 min / nunca; qué dice (distancia, tiempo, ritmo, ritmo del último km); pitidos; pausa automática;
  pantalla siempre encendida.
- **Fuera de la app y con la pantalla bloqueada:** aviso fijo con km, tiempo, ritmo, «Pausar» y «Abrir»; el GPS y la voz siguen.
- **Sin meta:** la carrera solo captura lo que la persona corre o camina. No se elige distancia ni tiempo objetivo.
- **Mapa:** Google Maps SDK for Android (`react-native-maps`, proveedor Google), cámara inclinada 3D con edificios. Requiere una clave
  de Maps SDK restringida a la app y un APK nuevo.
- **Avatar en el mapa** como en Rappi o un juego de caminar: un personaje propio de Renaser que camina (no copiado de terceros), hombre o
  mujer según `sexo` de la ficha inicial; sin dato (o «Otro»), uno neutral. Animado solo mientras se mueve; quieto en pausa; respeta
  «reducir movimiento».
- **El avatar refleja el movimiento real al instante, incluso 4 pasos** (segunda decisión del mismo día): fusión de GPS y podómetro
  (FR-030 a FR-036).
- **Android primero.** Corre en segundo plano con un servicio en primer plano y una notificación fija.

## Reglas generales del dueño para este lote

Respetar las reglas y no romper la lógica, reutilizar, no crear tablas innecesarias, normalizar, SOLID. Aquí se traduce en: **una sola
tabla nueva** (`carreras`, porque una persona puede tener varias carreras en un mismo día y cada una tiene su clave de idempotencia y su
ruta: no caben en la fila única del registro del día), el cierre del hábito sigue pasando por `RegistroService.completar` (puntos,
ventana, evento y racha en un solo lugar) y la validación vive en `PoliticaKilometros`, como la de la 037.

## Cómo se garantiza que no se llame al servidor a cada rato, y que nada se duplique

1. **Durante la carrera no hay ningún pedido al servidor.** Los puntos GPS, los pasos, los tramos y el estado (corriendo, en pausa,
   tiempo activo) se escriben solo en el teléfono, en un almacenamiento local persistente que sobrevive a que Android mate la app. Al
   reabrirla, la carrera se retoma donde quedó.
2. **Una sola escritura, al terminar.** Al empezar, el teléfono genera una `carreraId` (UUID v4). Al tocar «Terminar», la app manda **un
   único** pedido (`POST /api/v1/carreras`) con esa `carreraId` como clave de idempotencia.
3. **El servidor guarda la carrera con una restricción única `(participante, carrera_id)`.** Repetir el mismo pedido —un reintento, un
   doble toque, o «sin red» y después «con red»— devuelve **el mismo resultado** sin crear otra carrera, sin sumar km otra vez y sin pagar
   puntos dos veces. Dos pedidos iguales al mismo tiempo los resuelve la base (el segundo choca con el índice único y lee lo guardado).
4. **Los km del hábito se registran en esa misma transacción** con la regla de la 037 (cerrar por `RegistroService.completar`), así que no
   hace falta un segundo pedido para «registrar en el hábito».
5. **Sin red al terminar:** la carrera queda en una cola local y se reintenta con espera creciente. La pantalla dice «Guardada en tu
   teléfono, se enviará al tener señal».
6. **Lecturas:** la app no consulta el servidor durante la carrera; solo al abrir Training (la agenda de hoy, que ya se lee) y la respuesta
   del pedido al terminar.

## User Scenarios & Testing

### Historia 1 — Salir a correr desde Training (Prioridad: P1)

La persona toca «Kilómetros» en Training. Si es la primera vez, la app pide el permiso de ubicación (y el de actividad física para los
pasos, que es opcional). Ve la cuenta regresiva 3, 2, 1 con pitido, voz y vibración, y empieza a correr: el tiempo grande, la distancia,
el ritmo y las calorías se actualizan; el mapa inclinado la sigue con su personaje caminando.

**Escenarios**
1. **Dado** el permiso de ubicación concedido, **cuando** toca «Kilómetros», **entonces** ve 3, 2, 1 y empieza la carrera, con una
   `carreraId` nueva guardada en el teléfono.
2. **Dado** que rechaza el permiso de ubicación, **entonces** no empieza la carrera, se le dice por qué y le queda «Subir captura» (la
   de siempre) y, si tiene Health Connect, lo de la 037.
3. **Dado** que rechaza el permiso de actividad física, **entonces** la carrera funciona igual, sin pasos ni pasos por minuto, y el avatar
   se mueve solo con el GPS (FR-036).
4. **Dado** que da 4 pasos parado frente al mapa, **entonces** el avatar camina esos 4 pasos al instante (FR-031) aunque el GPS todavía
   no haya cambiado.

### Historia 2 — Pausar, cerrar la app y volver (Prioridad: P1)

**Escenarios**
1. **Dado** que desliza «Desliza para pausar», **entonces** el tiempo se detiene, el avatar queda quieto y ve «Tu carrera queda guardada,
   aunque cierres la app».
2. **Dado** que bloquea la pantalla o cambia de app, **entonces** el aviso fijo muestra km, tiempo y ritmo con «Pausar» y «Abrir», y el GPS
   y la voz siguen.
3. **Dado** que Android mata la app a mitad de carrera, **cuando** la abre otra vez, **entonces** ve la carrera con lo registrado hasta ese
   momento y puede seguir o terminar; el tiempo en que el proceso estuvo muerto sin puntos se trata como pausa.
4. **Dado** «Descartar», **entonces** se confirma, se borra todo del teléfono y **no se manda nada** al servidor.

### Historia 3 — Terminar y registrar sin duplicar (Prioridad: P1)

**Escenarios**
1. **Dado** que toca «Terminar» con red, **entonces** sale un solo pedido; la pantalla «Terminaste» muestra mapa, km, duración, ritmo,
   calorías y tramos, y la línea del hábito pasa de «Registrando 3,2 km en tu hábito…» a «Registramos 3,2 km en tu hábito».
2. **Dado** que la respuesta se pierde y la app reintenta, **entonces** el servidor responde lo mismo (`repetida: true`), sin carrera ni
   puntos de más.
3. **Dado** que termina sin señal, **entonces** ve «Guardada en tu teléfono, se enviará al tener señal»; al volver la señal se envía sola
   y la línea cambia sin que la persona haga nada.
4. **Dado** que hoy ya cumplió el hábito con la captura, **entonces** la carrera se guarda igual y la pantalla dice «Tu carrera quedó
   guardada. Hoy ya registraste este hábito con tu captura.».
5. **Dado** que la carrera llega cuando su día ya cerró (estuvo sin señal hasta mañana), **entonces** la carrera se guarda en su día pero
   **no** se registra en el hábito de ningún día (regla del 17-oct: no se reasigna un día cerrado).

### Historia 4 — Varias fuentes el mismo día (Prioridad: P1)

**Escenarios**
1. **Dado** dos carreras de 3 y 4 km el mismo día, **entonces** el hábito queda con 7 km y se pagan los puntos una sola vez.
2. **Dado** 7 km de Health Connect registrados (037) y una carrera de 3 km después, **entonces** el hábito queda con 7 km (el mayor, no
   10): los pasos de Health Connect ya incluyen los de la carrera.
3. **Dado** una carrera de 6 km que cerró el hábito y después Health Connect lee 9 km, **entonces** el hábito pasa a 9 km, sin puntos de más.

### Historia 5 — Quién más lo ve (Prioridad: P2)

El mentor o la guía, el líder, Administración y Alquimista ven en la ficha «6,2 km · Carrera con GPS», la duración, el ritmo y «Por
revisar» si fue dudosa; con la ruta guardada, pueden abrir el mapa. Cada lectura de una ruta ajena deja constancia.

### Historia 6 — Compartir en el Muro (Prioridad: P3)

«Compartir en el Muro» abre la publicación de siempre con una imagen del mapa y los datos (km, duración, ritmo). La imagen no muestra los
primeros ni los últimos metros de la ruta (zona de privacidad, FR-043). Es un pedido aparte, que solo hace la persona.

### Casos de borde

- **Carrera que cruza la medianoche:** cuenta para el día en que empezó (FR-021).
- **Carrera muy corta** (menos de 100 m o menos de 1 minuto activo): no se envía; la pantalla dice «Muy corta para guardarla» y ofrece
  descartar.
- **Velocidad imposible o saltos de GPS:** ver FR-024 y FR-025.
- **Mismo `carreraId` con otros datos** (app manipulada o error): 409, no se pisa la carrera guardada.
- **Cambio de cuenta con una carrera en la cola:** la cola es por cuenta; la de la otra cuenta no se envía con la sesión nueva.
- **Cerrar sesión con una carrera en la cola:** se avisa que hay una carrera sin enviar antes de cerrar; si igual cierra, se borra.
- **Formación sin el hábito de km** (spec 023): la carrera se guarda; la pantalla no promete «registrar en tu hábito».
- **Interruptor `habitos.km.carreras` apagado por Soporte TI:** la entrada no se dibuja o muestra el aviso de mantenimiento; una carrera ya
  en la cola sigue esperando y se envía cuando se prenda.
- **Batería baja o ahorro de energía extremo:** si el sistema deja de dar puntos, el hueco se trata como en FR-035.
- **GPS que «tiembla» con la persona parada:** no suma metros (FR-033).

## Requirements

### Carrera en el teléfono (app)

- **FR-001** La carrera se inicia solo desde la fila «Kilómetros diarios» de Training, de hoy, y solo en Android. En la web (iPhone) no
  se ofrece; queda la captura.
- **FR-002** Al empezar, el teléfono genera una `carreraId` (UUID v4) y la guarda con el inicio y la cuenta, antes de la cuenta regresiva.
- **FR-003** Cuenta regresiva de 3 segundos con pitido, voz y vibración en cada número; con «reducir movimiento», sin animación de escala.
- **FR-004** Durante la carrera la app **no hace ningún pedido al servidor** (ni lecturas ni escrituras) relacionado con la carrera.
- **FR-005** Los puntos GPS aceptados, los pasos, los tramos, el estado y el tiempo activo se escriben en un almacenamiento local persistente
  a medida que llegan (como máximo cada 5 s o cada 10 puntos), de modo que si Android mata el proceso se pierden como mucho esos segundos.
- **FR-006** Al reabrir la app con una carrera sin terminar, se ofrece retomarla («Seguir» / «Terminar» / «Descartar»); el tiempo sin
  puntos mientras el proceso estuvo muerto no cuenta como tiempo activo.
- **FR-007** Pantalla Corriendo: tiempo grande, distancia, ritmo (min/km) y calorías; pestañas Mapa / Estadísticas / Tramos / Voz; barra
  «Desliza para pausar». Sin botón 2D/3D.
- **FR-008** Estadísticas: distancia, ritmo medio, ritmo actual (últimos 30 s), velocidad, calorías aprox., pasos, pasos por minuto y subida
  acumulada (altitud suavizada; solo subidas mayores a 2 m entre lecturas suavizadas).
- **FR-009** Tramos: un tramo por kilómetro completo con su tiempo y ritmo; el último, parcial.
- **FR-010** Voz: avisos cada km, cada 5 min o nunca (por defecto cada km); qué dice (distancia, tiempo, ritmo, ritmo del último km, todos
  encendidos por defecto); pitidos sí/no; pausa automática (sí por defecto: sin pasos y con velocidad menor a 1 km/h durante 10 s pausa;
  al moverse, sigue); pantalla siempre encendida (no por defecto). Las preferencias se guardan por cuenta en el teléfono.
- **FR-011** Fuera de la app o con pantalla bloqueada, un servicio en primer plano mantiene GPS, pasos y voz, con una notificación fija que
  muestra km, tiempo y ritmo (actualizada como mucho cada 5 s) y las acciones «Pausar» y «Abrir».
- **FR-012** Pausa: el tiempo activo se detiene, no se suman metros, el avatar queda quieto; muestra «Tu carrera queda guardada, aunque cierres
  la app» y las opciones tramos, estadísticas, voz y sonidos y bloquear pantalla, con «Seguir», «Terminar» y «Descartar».
- **FR-013** «Descartar» pide confirmación, borra la carrera del teléfono y no envía nada.
- **FR-014** Pantalla Terminaste: mapa con la ruta, km, duración, ritmo, calorías y tramos; la línea del hábito con su estado (FR-018) y
  «Compartir en el Muro».
- **FR-015** Calorías aproximadas = MET según la velocidad × peso (kg) × horas activas. El peso sale de Plan, sin campo nuevo (decisión 5
  del dueño): el objetivo de 90 días del eje `CUERPO` de `GET /api/v1/rocks/master`, si su `unidad` es de peso en kilos («kg», «kilo»,
  «kilos», «kilogramo(s)», sin importar mayúsculas ni tildes), usa su `avance` y, si no hay, su `lineaBase`; un valor fuera de 30–300 kg se
  ignora. Sin nada de eso, 70 kg. Lo calcula la app; se muestran como «aprox.». El servidor las guarda tal como llegan, topadas (FR-026).

### Envío único e idempotencia

- **FR-016** Al tocar «Terminar» la app arma **un único** pedido `POST /api/v1/carreras` con la `carreraId`, el resumen, los tramos y, si
  corresponde, la ruta (FR-040), y lo envía. No hay otro pedido para «registrar en el hábito».
- **FR-017** Si el envío falla por red o por 5xx, la carrera queda en una cola local **por cuenta** y se reintenta con espera creciente
  (5 s, 15 s, 1 min, 5 min, 15 min y luego cada 30 min) y en cada vuelta a primer plano y cada vez que el teléfono recupera la red. Un 4xx
  distinto de 409 no se reintenta solo: queda en la cola marcado con el motivo y se le dice a la persona.
- **FR-018** La pantalla dice el estado del envío: «Registrando X km en tu hábito…», «Registramos X km en tu hábito», «Guardada en tu
  teléfono, se enviará al tener señal», o por qué la carrera se guardó pero no entró al hábito (captura, día cerrado, sin hábito de km,
  no cuenta por velocidad).
- **FR-019** El servidor guarda la carrera con restricción única `(participante_id, carrera_id)`. Un pedido con una `carreraId` ya guardada
  y **la misma huella** (hash del cuerpo canónico) responde 200 con el resultado guardado y `repetida: true`, sin tocar el registro del
  hábito ni los puntos. Con **otra huella**, 409 `CARRERA_YA_REGISTRADA` sin cambiar nada.
- **FR-020** Guardar la carrera y aplicar los km al hábito ocurren en **una sola transacción**; si falla la parte del hábito por un error
  inesperado, no queda la carrera a medias.

### Día, hábito y regla de km del día

- **FR-021** El día de la carrera es la fecha de `iniciadaEn` en la zona del participante (aunque termine pasada la medianoche).
- **FR-022** **Regla de km del día:** el registro del hábito queda con `max(Σ km de las carreras válidas del día, km de Health Connect
  registrados ese día)`. Nunca se suman carreras con Health Connect (los pasos del teléfono ya incluyen los de la carrera). El valor solo
  sube, nunca baja; el origen guardado es el de la fuente mayor (`GPS` o `HEALTH_CONNECT`).
- **FR-023** Aplicar al hábito: si el registro del día está PENDIENTE, una carrera válida con km > 0 lo cierra por
  `RegistroService.completar` con origen `GPS` (gesto propio del hábito, como la 037) y paga los puntos de siempre **una vez**. Si ya está
  COMPLETADO con origen `GPS` o `HEALTH_CONNECT`, se aplica FR-022 sin pagar de nuevo. Si está COMPLETADO con la captura (`MANUAL`), no se
  toca. Si el día del registro ya cerró (D-259) o no existe el hábito de km, no se aplica. En todos los casos la carrera se guarda y la
  respuesta dice qué pasó con el hábito. Nunca se aplica al registro de otro día (regla del 17-oct).
- **FR-024** **Límites que impiden que cuente** (la carrera se guarda con `cuentaParaElHabito = false`, resultado `NO_CUENTA` y su motivo):
  velocidad media (distancia / duración activa) mayor a 25 km/h, más de 60 km (el tope automático de la 037), o `terminadaEn` más de
  5 minutos en el futuro según el reloj del servidor. No son un 400: la carrera existe y se ve en la ficha.
- **FR-025** **Lo dudoso se acepta marcado «por revisar»** (como la 037): algún tramo de 1 km a más de 22 km/h, o una ruta enviada cuya
  longitud difiere más de 15 % de la distancia declarada, o una zancada (distancia / pasos) fuera de 0,3–2,2 m cuando hay pasos.
- **FR-026** Topes de entrada: duración total y activa ≤ 12 h, duración activa ≥ 60 s y no mayor que la total, distancia de 100 m a
  100 km, pasos ≤ 150 000, calorías ≤ 6 000, subida ≤ 5 000 m, metros sin GPS entre 0 y la distancia, tramos ≤ 61 con `km` consecutivos
  desde 1 y segundos > 0, ruta ≤ 3 000 puntos y ≤ 100 KB codificada, versión de la app ≤ 20 caracteres. Fuera de tope: 400 con un texto que
  la persona lee. (Entre 60 y 100 km la carrera se acepta y no cuenta, FR-024.)
- **FR-027** Si el hábito se cumple por una carrera, se emite el mismo evento de hábito cumplido de siempre (alarmas, celebraciones,
  semáforo); al subir el valor de un registro ya cumplido, no se emite de nuevo.
- **FR-028** El ranking de Kilómetros no cambia de reglas: suma `valor_medido` (que ya respeta FR-022). Quien tiene algún día con origen
  `GPS` se marca como los automáticos de la 037.
- **FR-029** La agenda de hoy (`GET /habit-tracks/today`, y la de otro día) suma en la `medicion` del hábito de km: `origenDelDia` puede ser
  `GPS`, y `carrerasDelDia` (cuántas carreras tiene ese día y la suma de los km de las que cuentan para el hábito), para que Training muestre
  lo ya registrado sin otra lectura. `carrerasDelDia` va **siempre** en el hábito de km (con `cantidad: 0` si no hay carreras): que falte es
  la señal de un backend anterior, y entonces la app no ofrece la carrera.

### El avatar refleja el movimiento real al instante (fusión de sensores)

- **FR-030** **GPS para la ruta y los km oficiales:** `expo-location` en el servicio en primer plano, precisión de navegación, actualización
  cada ~1 s o 5 m. Se descarta todo punto con precisión peor que 20 m y todo salto que implique más de 25 km/h desde el último punto aceptado;
  la posición se suaviza (promedio ponderado por precisión de los últimos puntos, o filtro de Kalman simple). Distancia = suma haversine entre
  puntos aceptados.
- **FR-031** **Podómetro para la respuesta inmediata:** cada paso detectado (sensor de pasos del teléfono: `TYPE_STEP_DETECTOR` o
  `expo-sensors` Pedometer) anima al avatar un paso y lo adelanta una zancada estimada en la dirección del rumbo (brújula / heading). El avatar
  se mueve **antes de 500 ms** desde el primer paso detectado, también con 4 pasos y sin que el GPS haya cambiado.
- **FR-032** **Reconciliación:** cuando llega el siguiente punto GPS bueno, el avatar se lleva suavemente (≤ 600 ms, sin saltos) a la posición
  real; si la diferencia es mayor a 30 m, se coloca en la real sin animar el recorrido.
- **FR-033** **Sin pasos, quieto:** sin pasos detectados y con velocidad GPS menor a 1 km/h, el avatar no se mueve y no se suman metros aunque
  el GPS «tiemble». Parado 60 s con el teléfono en la mano, la distancia no sube más de 5 m.
- **FR-034** **Los km oficiales son los del GPS filtrado** (FR-030). La zancada del podómetro solo mueve al avatar; no se suma a la distancia
  salvo FR-035.
- **FR-035** **Huecos cortos sin señal:** si el GPS no da puntos buenos por menos de 60 s y hay pasos, se rellena la distancia con pasos ×
  zancada estimada (calibrada con los últimos km de GPS de esa carrera; sin calibración, 0,75 m caminando y 1,0 m corriendo). Más de 60 s sin
  señal: el tramo se marca sin GPS y no suma (supuesto del equipo, ver «Decisiones del dueño»).
- **FR-036** Sin permiso de actividad física o sin sensor de pasos, el avatar se mueve solo con el GPS (con la misma reconciliación) y las
  estadísticas de pasos no se muestran.
- **FR-037** **El mapa no calcula nada:** dibuja la ruta aceptada como una línea que crece (dorado `#B2924F`) y el avatar propio animado entre
  posiciones; la cámara inclinada (≈ 60°) con edificios sigue el rumbo de la persona.
- **FR-038** El avatar es hombre o mujer según `sexo` de la ficha inicial (`Masculino` / `Femenino`); sin dato u «Otro», el neutral. Camina
  solo mientras hay pasos o velocidad; en pausa queda quieto; con «reducir movimiento» se dibuja quieto (sin ciclo de caminata) y solo
  cambia de posición.
- **FR-039** La app pide solo ubicación precisa (y, si el estudio técnico lo exige, en segundo plano; ver plan) y, opcional, actividad
  física. No lee contactos, ni Health Connect para esto, ni nada más.

### Ruta y privacidad

- **FR-040** La ruta viaja **una sola vez**, dentro del pedido final, como polyline codificada (algoritmo de Google) y simplificada
  (Douglas-Peucker, tolerancia 5 m). Es opcional: la persona puede apagar «Guardar mi ruta» (encendido por defecto, decisión 4 del dueño); apagada,
  solo viaja el resumen.
- **FR-041** Ven la ruta: la persona, su mentor o guía de ese momento, el líder de mentores, Administración y Alquimista, cada uno por la
  puerta por la que ya ve su ficha (la persona, la suya; el mentor o la guía, la de su grupo; el líder, Administración y Alquimista, las de
  liderazgo, con y sin grupo). Cada lectura de una ruta ajena deja un registro de consulta (`consultas_de_ficha`, tipo `RUTA_DE_CARRERA`,
  como el diagnóstico de la spec 026). Nadie más (ni el Muro, salvo la imagen que la persona comparte).
- **FR-042** La ruta se guarda 180 días desde la carrera; después se borra y queda solo el resumen (decisión 4 del dueño).
- **FR-043** Zona de privacidad: los primeros y últimos 200 m de la ruta no se guardan en el servidor ni se muestran en la imagen del Muro
  (la distancia sí cuenta).

### Lectura del acompañamiento y Administración

- **FR-044** En las fichas (`ObligacionDelDia.medicion`) el día con carreras trae `origen = GPS` y la lista resumida de carreras (km,
  duración, ritmo, por revisar, si tiene ruta). La ruta se pide aparte, solo al abrir el mapa (FR-041).
- **FR-045** El interruptor `habitos.km.carreras` (hijo de `habitos.km`) apaga o esconde la función; los endpoints declaran esa funcionalidad.
- **FR-046** Toda pantalla nueva cumple lo de siempre: tipo oración, tuteo, áreas táctiles de 48 o más y fuente por los roles de
  `theme/fuentes.ts`; botones redondeados con el dorado del tema sobre el fondo crema.

## Success Criteria

- **SC-001** Una carrera de 30 minutos genera **cero** pedidos al servidor mientras dura y **uno** al terminar (medido con el registro de
  red en una prueba de la app).
- **SC-002** Repetir el mismo pedido 5 veces (también a la vez) deja **una** carrera, el mismo valor en el hábito y los puntos pagados una
  sola vez.
- **SC-003** Matar la app a mitad de carrera y reabrirla conserva la distancia con una pérdida máxima de 5 s de puntos.
- **SC-004** Una carrera terminada sin señal se registra sola dentro del minuto siguiente a recuperar la red con la app en primer plano.
- **SC-005** El avatar se mueve en menos de 500 ms desde el primer paso detectado (medido sobre la lógica de fusión con pasos simulados).
- **SC-006** Parado 60 s con puntos GPS que oscilan ±10 m y sin pasos, la distancia sumada es ≤ 5 m.
- **SC-007** En un recorrido de prueba de 1 km medido con una ruta conocida, la distancia de la app difiere menos de 5 %.
  [sin prueba automática: necesita un recorrido real al aire libre]
- **SC-008** Un día con Health Connect (7 km) y una carrera (3 km) queda en 7 km, no en 10.
- **SC-009** Una carrera que llega cuando su día ya cerró se guarda y no cambia ningún registro de hábito.
- **SC-010** Un rol sin permiso que pide la ruta de otra persona recibe 403; una cuenta suspendida, 403.
- **SC-011** Con la pantalla bloqueada 10 minutos, la notificación sigue actualizándose y la carrera suma la distancia recorrida.
  [sin prueba automática: depende del teléfono real y su ahorro de batería]

## Decisiones del dueño (2026-11-05, respuestas a los supuestos)

Reemplazan a los «Supuestos para el dueño» que tenía este borrador. Cada una ya está aplicada en los requisitos de arriba.

1. **Registrar en el hábito es automático al terminar**, sin botón aparte. El botón «Registrar X km en mi hábito» del diseño pasa a ser la
   línea que muestra el estado de ese mismo envío (FR-018): una sola escritura (FR-016).
2. **Cualquier distancia mayor que cero cumple el hábito**, sin meta (la meta de 5 km de la 037 sigue siendo solo de Health Connect). Como
   la carrera más corta que se envía es de 100 m (decisión 9), en la práctica toda carrera que cuenta cumple (FR-023).
3. **Límites anti-trampa:** no cuenta para el hábito una carrera con velocidad media mayor a 25 km/h ni de más de 60 km (se guarda con
   `cuentaParaElHabito = false`, FR-024); un km a más de 22 km/h la deja «por revisar» (FR-025).
4. **Ruta:** «Guardar mi ruta» viene encendido; la ven la persona, su mentor o guía, el líder de mentores, Administración y Alquimista, y cada
   lectura ajena queda registrada (FR-041); se guarda 180 días (FR-042); se recortan los primeros y los últimos 200 m (FR-043).
5. **Calorías sin un campo de peso nuevo** (FR-015): si la persona tiene en Plan su objetivo de 90 días del eje Cuerpo con una unidad de
   peso en kilos y un número de avance o de línea base, se usa ese peso (el más reciente: el avance actual; si no hay, la línea base); si
   no, 70 kg. **Lo calcula la app**, con la lectura de `GET /api/v1/rocks/master` que ya hace en Plan (eje `CUERPO`, campos `unidad`,
   `avance` y `lineaBase`): el servidor no lee Plan para esto, no expone un peso y no cambia nada de Plan. El servidor guarda las calorías
   tal como llegan, topadas (FR-026). Se eligió así por ser lo más simple y coherente: las calorías son aproximadas, nadie decide nada con
   ellas, y el dato ya está en el teléfono.
6. **Voz:** el texto a voz del teléfono (los números cambian en cada aviso; no hay audios grabados).
7. **Carreras de menos de 100 m o de menos de 1 minuto activo no se envían** (la pantalla ofrece descartarlas). El servidor responde 400 a
   una que llegue igual (FR-026).
8. **Health Connect el mismo día: el mayor, nunca la suma**, y los puntos una sola vez (FR-022, FR-023).

### Supuestos del equipo que siguen en pie (no los contestó el dueño; se pueden cambiar sin tocar el servidor)

- Los pasos rellenan huecos sin señal de hasta 60 s; más largos no suman (FR-035). Es lógica de la app.
- Solo el hábito KILÓMETROS DIARIOS: una carrera no cumple GIMNASIO ni otro hábito.

## Qué necesita

- **APK nuevo** (código nativo: `react-native-maps`, servicio en primer plano, sensores). No llega por aire.
- **Clave de Maps SDK for Android** restringida al paquete de la app y a las huellas SHA-1 de la llave de subida y la de firma de Play.
- **Migración** (V170): tabla `carreras` con índice único `(participante_id, carrera_id)` y el CHECK de
  `registros_habito.origen_medicion` ampliado a `GPS` (ver `plan.md`).
- **Play Console:** declaración de servicio en primer plano de tipo ubicación (con video) y, si se pide `ACCESS_BACKGROUND_LOCATION`, la
  declaración de ubicación en segundo plano; actualizar Data safety (ubicación precisa, actividad física).
- **Política de privacidad** (`/privacidad/` de la app): ubicación durante la carrera, la ruta, quién la ve y cuánto se guarda.
- **Backend antes que la app.** La app nueva contra un backend anterior no ofrece la carrera (no hay `carrerasDelDia` en la agenda).

## Fuera de alcance (para después)

iPhone (la app web no tiene GPS en segundo plano), metas o planes de entrenamiento, historial «Mis carreras», competir con otros en vivo,
frecuencia cardiaca, exportar a Strava o GPX, escribir la carrera en Health Connect.
