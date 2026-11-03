# Research: Código Renaser con la voz

Cada punto: decisión, razón, alternativas descartadas. No quedan `NEEDS CLARIFICATION`.

## R1 — Dictar: reutilizar `useDictado`, con un seguro de sesión

- **Decisión**: el formulario usa `useDictado` (`features/renasia/hooks`), el mismo del chat de SER y del orbe de Hoy, y suma un
  **seguro de sesión propia**: cada instancia solo reacciona a los eventos del reconocedor mientras **ella** abrió el micrófono.
- **Razón**: `expo-speech-recognition` emite sus eventos a **todos** los que escuchan. Con el orbe de Hoy montado, dictar el Código
  Renaser hacía que el orbe se pusiera «escuchando» y que Hoy mostrara «Tú: <lo que dictaste>» debajo del fénix
  (`HoyScreen`, `voz.loQueDijiste`): texto íntimo de «¿qué evito?» a la vista en otra pantalla. Hoy no pasa porque nadie dicta con dos
  consumidores; con este formulario sí. El seguro es aditivo y no cambia el comportamiento de quien tiene una sola instancia.
- **Texto de los errores**: `mensajeDeErrorDeVoz(codigo, destino)` suma el destino («mensaje» por defecto, «respuesta» para el
  Código Renaser) para no decir «escribe tu mensaje» en un formulario.
- **Descartado**: copiar el hook (duplica 150 líneas y su lógica de tramos continuos); un módulo compartido nuevo (mover archivos
  sin necesidad); filtrar en el orbe (arregla un síntoma, no la causa).

## R2 — Un micrófono a la vez, sin perder lo dicho

- **Decisión**: un solo `useDictado` para las cuatro respuestas, con un hook propio (`useDictadoDelRadar`) que sabe cuál es el campo con el
  micrófono. Tocar el micrófono de otra pregunta **cierra el primero, espera a que entregue su texto y recién entonces abre el
  segundo**.
- **Razón**: `stop()` es asíncrono (el texto llega en el evento `end`). Abrir el segundo antes borraría o mandaría al campo equivocado
  lo dicho en el primero. Por construcción hay un solo micrófono (FR-004).
- **Descartado**: una instancia por campo (cuatro suscripciones, y cada una oiría a las otras).

## R3 — Escuchar: cuatro MP3 de Dora, locales

- **Decisión**: la pregunta y su ayuda se leen con la voz Dora (Kokoro-82M, la de las alarmas), generada con `generar-sonidos.sh`
  desde `src/features/radar/vocesDelRadar.json` (lo que dice cada voz; la app y el script leen el mismo archivo, como en las alarmas).
  Archivos `voz_radar_que_<hago|pienso|siento|evito>.mp3`, MP3 mono 44,1 kHz a 64 kb/s como las otras voces. Se reproducen con
  `expo-audio` desde el paquete (`require`), sin red.
- **Razón**: FR-010 pide la voz de la app, sin conexión y no la del teléfono; son cuatro frases fijas.
- **Lo que dice**: el título y la ayuda de cada pregunta («¿Qué siento? La emoción, no la interpretación.»). Una prueba obliga a que
  `vocesDelRadar.json` coincida con `PREGUNTAS_RADAR`: si cambia una pregunta, la prueba avisa que hay que regenerar la voz.
- **También en `app.json`**: el dueño pidió que los MP3 nuevos vayan en `sounds` (la prueba `sonidosElegibles` exige la pareja
  carpeta/`app.json`); cuesta ≈190 KB duplicados dentro del APK. Se acepta.
- **Descartado**: voz del teléfono (`expo-speech`, robótica, FR-010 lo prohíbe); voz del servidor (red y costo por hora).

## R4 — «Dictado»: una columna, un dato por registro

- **Decisión**: `registros_radar.dictado boolean not null default false` (V132). Verdadero si **al menos una** de las cuatro
  respuestas llevó algo dictado y esa parte sigue en el texto registrado. La app lo declara al registrar; los registros anteriores y los de SER
  valen `false`.
- **Razón**: el dueño pidió «una columna o un campo en lo que ya existe, sin tablas nuevas». Un dato por registro responde «¿hubo
  errores de transcripción posibles?», que es para lo que se marca (spec 030, 030-2). Cuatro columnas (una por respuesta) no cambian
  lo que ve quien acompaña.
- **Honestidad del dato**: el servidor no oye audio; confía en lo que declara la app, como confía en `energyLevel`. No se usa para
  nada que pague, puntúe o bloquee, así que no hay incentivo de falsearlo.
- **Descartado**: tabla aparte (el dueño la prohibió); marcar por campo (más datos para el mismo uso); inferir «dictado» en el
  servidor (imposible sin el audio).

## R5 — Volumen en cero (User Story 3, escenario 4)

- **Hallazgo**: ni `expo-audio` ni `expo-speech` ni RN exponen el **volumen del sistema**; hacerlo necesita código nativo nuevo
  (`AudioManager`) que no se puede probar sin el teléfono, y la regla pide no sumar dependencias nativas sin justificarlas.
- **Decisión**: mientras suena una voz se muestra una línea chica «Si no la oyes, sube el volumen de tu teléfono.» y el botón dice
  «Detener». Cubre la causa (volumen) con una línea honesta, sin detectar nada. **Se avisa al dueño** de que no hay detección real y
  de qué haría falta (una lectura de volumen en el módulo nativo `renaser-alarmas`).
- **Descartado**: detectar por heurística (por ejemplo, «sonó y no pasó nada»): daría avisos falsos.

## R6 — Quién ve «dictado» y la spec 034

- **Decisión**: ninguna ruta nueva. `dictado` viaja en los mismos registros que hoy leen la persona
  (`GET /radar/history`), el mentor o guía (`GET /mentor/groups/{g}/learners/{u}/radar`) y el líder, Administración y Alquimista
  (`GET /leadership/groups/{g}/people/{p}/radar`).
- **Razón**: así el interruptor de la spec 034 (que decide si mentor, líder y Administración ven el Código Renaser) oculta también el
  «dictado» sin que esta spec toque ese interruptor, y no se puede ver «dictado» sin ver el registro.

## R7 — Compatibilidad entre versiones

- Backend nuevo + app anterior: la app no manda `dictado` (vale `false`) y lee con esquema `passthrough`: el campo extra no rompe.
- App nueva + backend anterior: manda `dictado: true` solo cuando es verdadero; Spring ignora campos desconocidos (el proyecto no
  activa `FAIL_ON_UNKNOWN_PROPERTIES`; una prueba lo fija) y la ficha no muestra la línea porque el campo no llega.
- Orden de publicación: backend primero (V132), después la app. La app nueva igual necesita un **APK nuevo** (assets y JS no se
  actualizan por aire).

## R8 — Privacidad

- FR-016: la Política de Privacidad (`public/privacidad/index.html`) dice que el Código Renaser también se puede dictar con el
  reconocimiento de voz del teléfono (Google en Android) y que el audio no se guarda. Se agrega a la tabla de terceros y a la lista de
  micrófono; una prueba existente (`paginaPoliticaDePrivacidad`) sigue en verde. Debe publicarse antes de sacar el APK.
