# Feature Specification: Tipos de alarma por día al planificar un hábito

**Feature Branch**: `009-tipos-de-alarma` (backend y app). Se apoya en la 008.
**Created**: 2026-10-20
**Status**: Draft
**Input**: Pedido del dueño (2026-10-20), sobre el editor de Training → Planificar: «abajo saldrá los diferentes tipos de alarmas que pondremos… debajo de los recordatorios». Dio como referencia un interruptor grande (encendido/apagado) y pidió: «para que sea más agradable visual para los usuarios y recuerda que es por día totalmente personalizable… es para personas de 40 años para arriba y que sea cómodo». Además: «recuerda el UX/IA».

## Clarifications

### Session 2026-10-20 (corrección del dueño tras verlo en el emulador)

> **Corregido 2026-10-20.** El dueño vio la primera versión en el emulador: cuatro interruptores debajo del recordatorio. Respondió: «esa configuración no iba el interruptor, era para escoger el sonido que quieren o la alarma que quería… y esa configuración creo que en Ajustes… para mejor UX/IA ocupa toda la pantalla». Quedó así:
> - **Planificar un hábito pasa a pantalla completa** («‹ GIMNASIO» arriba, Guardar fijo abajo, el atrás vuelve a la lista).
> - **Debajo del Recordatorio va «Sonido de la alarma»**: se elige el tono (los mismos de Yo → Alarmas: el del teléfono, campana, voz que dice el hábito, solo vibrar, para alertar, para relajar), con ▶ para escuchar. Va **por día**, igual que la hora y el aviso. Sin elección, hereda el tono de Yo → Alarmas.
> - **Los interruptores grandes y dorados van a Yo → Ajustes → Alarmas**, como configuración general de todas las alarmas de hábitos: **Vibración** y **Repetir si no la atiendo**. La voz ya es un tono. «Sin sonido» es el tono «Solo vibrar».
> - **Vibrar por defecto** (respuesta en consola): hoy las alarmas de hábitos no vibran, porque su canal se creó sin vibración (E-641). Desde ahora vibran salvo que la persona apague la vibración.
> - Lo de abajo, sobre los interruptores por día y la combinación {sonido, vibración, voz, repetir}, queda reemplazado por esto.

### Session 2026-10-20 (primera versión, reemplazada)

- Q: ¿Qué opciones van con interruptor? → A: **Sonido**, **Vibración**, **Voz que dice el hábito** y **Repetir si no la atiendo**. Se descartó «subir el volumen poco a poco».
- Q: ¿Color del interruptor encendido? → A: **dorado de Renaser**, grande, con sombra suave como la referencia (alto ~40 px, ancho ~72 px), y el rótulo a 17 px.
- Q: ¿Cómo se aplica por día? → A: **igual que la hora y el aviso (008)**. Sin días marcados se cambia lo general; con días marcados, solo esos días. Cada día puede tener su propia combinación.
- **UX/IA (pedido del dueño)**: la pantalla es para personas de 40 años en adelante. Pocas opciones a la vista, rótulos que dicen qué pasa, nada escondido detrás de gestos y un orden que se lee de arriba abajo: hora → días → recordatorio → cómo suena.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Elegir cómo suena la alarma con interruptores grandes (Priority: P1)

Como participante, quiero ver debajo del recordatorio unos interruptores grandes y claros (Sonido, Vibración, Voz, Repetir) para elegir cómo me avisa cada hábito, sin tener que entender menús.

**Why this priority**: es lo que el dueño mostró con la referencia. Hace la alarma más clara y cómoda para el público objetivo.

**Independent Test**: en el editor de Gimnasio, apagar «Sonido» y dejar «Vibración». A la hora, el teléfono vibra sin sonar.

**Acceptance Scenarios**:

1. **Given** el editor de un hábito con hora, **When** bajo a «Cómo suena», **Then** veo cuatro filas, cada una con su nombre, una línea corta que explica qué hace y un interruptor dorado grande.
2. **Given** «Sonido» apagado y «Vibración» encendida, **When** suena la alarma, **Then** vibra y no suena.
3. **Given** «Sonido» y «Vibración» apagados, **When** intento guardar, **Then** la app no lo permite y explica en una línea que al menos una de las dos tiene que quedar encendida. Si la persona quiere silencio, para eso está «Sin aviso».
4. **Given** «Voz» encendida en un hábito del catálogo, **When** suena, **Then** la voz dice el nombre del hábito. Si el hábito no tiene voz propia, dice la frase general.
5. **Given** «Voz» encendida, **When** apago «Sonido», **Then** «Voz» se apaga y queda deshabilitado con la línea «La voz necesita el sonido», porque la voz es un sonido.
6. **Given** «Repetir si no la atiendo» encendido, **When** la alarma suena y no la toco, **Then** vuelve a sonar a los 5 minutos y otra vez a los 10, y no más.
7. **Given** que toco la alarma o completo el hábito, **When** iban a sonar las repeticiones de ese día, **Then** no suenan.

---

### User Story 2 - Una combinación distinta para cada día (Priority: P1)

Como participante, quiero que, por ejemplo, de lunes a viernes suene con voz y repita, y el sábado solo vibre.

**Why this priority**: el dueño pidió que sea «por día, totalmente personalizable».

**Independent Test**: marcar S, apagar Sonido y Repetir, y guardar. El sábado solo vibra; los demás días siguen como estaban.

**Acceptance Scenarios**:

1. **Given** días marcados, **When** cambio un interruptor y guardo, **Then** el cambio aplica solo a esos días. El botón dice «Guardar alarma · S» si solo cambié «Cómo suena».
2. **Given** varios días marcados con combinaciones distintas, **When** miro «Cómo suena», **Then** cada interruptor que difiere entre ellos se ve en un estado intermedio claro: gris con un guion y la palabra «Varía». Al tocarlo pasa a encendido para todos.
3. **Given** un día con combinación propia, **When** miro su chip, **Then** se distingue que tiene alarma propia sin texto largo, coherente con la campana de la 008.
4. **Given** un día con combinación propia, **When** lo devuelvo a «como los demás días», **Then** vuelve a la combinación general, junto con la hora y el aviso (008).

---

### User Story 3 - Lo mismo en todos los teléfonos de la persona (Priority: P2)

Como participante con dos teléfonos, quiero que la combinación que elegí valga en ambos.

**Independent Test**: elegir la combinación en un teléfono, entrar con la misma cuenta en otro y abrir el editor: los interruptores coinciden y la alarma suena igual.

**Acceptance Scenarios**:

1. **Given** una combinación guardada, **When** abro la app en otro teléfono, **Then** el editor la muestra y las alarmas se programan con ella.

---

### Edge Cases

- **Hábito sin aviso ese día** («Sin aviso», 008): la sección «Cómo suena» se ve atenuada, con la línea «Este día no tiene aviso».
- **Antelaciones múltiples** (30 y 10 min antes): la combinación vale para todas las alarmas de ese día. «Repetir» repite solo la alarma de la hora del hábito, no las de antes.
- **Teléfono en silencio o «No molestar»**: manda la configuración del teléfono. La app no la salta.
- **Permiso de alarmas exactas** (007): sin él, las repeticiones pueden llegar tarde, como cualquier alarma. Se mantiene el aviso de la 007.
- **Web**: no hay alarmas locales. La sección no se muestra en web y el push del servidor sigue como hoy.
- **APK anterior con backend nuevo**: ignora la combinación y suena como hoy, con el sonido general de Yo → Alarmas.
- **APK nuevo con backend anterior**: no muestra «Cómo suena».
- **El sonido general de Yo → Alarmas** (campana, voz, sonidos para alertar o relajar) sigue siendo el tono que se usa cuando «Sonido» está encendido y «Voz» apagada.
- **Teléfono que borró las alarmas** (007): al rearmar se respeta la combinación de cada día, repeticiones incluidas.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Debajo del Recordatorio, el editor MUST mostrar la sección «Cómo suena» con cuatro filas: Sonido, Vibración, Voz que dice el hábito, Repetir si no la atiendo. Cada fila lleva rótulo, una línea explicativa y un interruptor.
- **FR-002**: El interruptor MUST ser grande y cómodo: área de toque de al menos 48 px de alto en toda la fila, que se puede tocar entera; dorado de Renaser encendido y gris claro apagado; pulgar blanco con sombra suave. Se respeta «reducir movimiento». Por accesibilidad lleva rol de interruptor y nombre.
- **FR-003**: Los textos MUST ser legibles para mayores de 40: rótulo de 17 px, explicación de 15 px o más, alto contraste en claro y oscuro, tuteo y tipo oración.
- **FR-004**: Sonido y Vibración MUST NOT quedar los dos apagados. La app lo impide con un mensaje en línea.
- **FR-005**: Voz MUST depender de Sonido: sin sonido se apaga y se deshabilita con su explicación.
- **FR-006**: Repetir MUST volver a sonar a los 5 y a los 10 minutos de la hora del hábito si la persona no tocó la alarma ni completó el hábito ese día, y MUST NOT sonar si lo hizo.
- **FR-007**: La combinación MUST elegirse por día, igual que la hora y el aviso: general sin días marcados, propia con días marcados, y estado «Varía» cuando los marcados difieren.
- **FR-008**: La combinación MUST guardarse en el servidor para que valga en todos los teléfonos de la persona.
- **FR-009**: La alarma del teléfono MUST sonar, vibrar, usar la voz y repetir según la combinación de ese día, y conservar lo de la 007 y la 008: tipo despertador, rearmado, aviso de detención y etiqueta del día.
- **FR-010**: Volver un día a «como los demás días» MUST quitarle también su combinación propia.
- **FR-011**: Un APK o un backend anterior MUST seguir funcionando como hoy, sin errores.
- **FR-012**: La arquitectura de la pantalla MUST leerse de arriba abajo en este orden: hora → días → recordatorio → cómo suena → guardar. Cada bloque lleva su rótulo de sección, sin opciones escondidas detrás de gestos.

### Key Entities

- **Combinación de alarma**: sonido (sí/no), vibración (sí/no), voz (sí/no) y repetir (sí/no). Hay una general por hábito y, opcional, una propia por día de la semana, que se resuelve por capas como la hora y el aviso (008).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una persona puede cambiar cómo suena un día en menos de 20 segundos y con un solo guardado.
- **SC-002**: En el emulador, el 100 % de las alarmas suenan, vibran, usan voz y repiten según la combinación de su día (verificado por día).
- **SC-003**: Con «Repetir», hay 0 repeticiones después de tocar la alarma o completar el hábito.
- **SC-004**: Todas las filas de «Cómo suena» tienen un área táctil de 48 px o más y textos de 15 px o más (revisado en la captura y en el árbol de accesibilidad).
- **SC-005**: El APK publicado y el backend anterior siguen funcionando sin errores.

## Assumptions

- La 008 (aviso por día) está integrada antes. La 009 usa sus mismas capas y su regla de esquema por día.
- Sonido y vibración se logran con canales de Android por combinación, que la app crea sin código nativo, igual que los sonidos de hoy. Se confirma en el plan.
- Repetir usa alarmas extra a +5 y +10 min, que se cancelan al tocar la alarma o completar el hábito. El detalle va en el plan.
- «Subir el volumen poco a poco» quedó fuera por decisión del dueño.
- El tono que suena con «Sonido» es el elegido en Yo → Alarmas. Esta spec no agrega tonos nuevos.
- Todo se prueba en local, con base clonada y build debug; sin APK si no cambia código nativo. Nunca contra producción.
