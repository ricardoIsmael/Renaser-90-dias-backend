# Feature Specification: Términos y condiciones editables desde la app

**Feature Branch**: `027-terminos-editables`

**Created**: 2026-10-31

**Status**: Implementada (2026-10-31) con las decisiones del dueño; ver «Decisiones del dueño del 31/10» y `plan.md`

**Input**: Pedido del dueño del 2026-10-31, textual: «El sistema debe poder gestionar sus propios términos y condiciones, para no estar borrando ni editando código.»

**Repos**: backend (`Renaser-90-dias-backend`, donde vive esta spec) y app (`Renaser-90-dias-frontend-`: onboarding y Administración).

**Se apoya en**: `022-formaciones` (el texto puede ser propio de cada formación) y `025-solicitud-con-formacion` (cuándo se aceptan los términos: en el primer ingreso, después de la ficha de `026-ficha-con-diagnostico`).

## Contexto

Hoy, la primera vez que entra, toda persona lee los **Términos y condiciones** (23 cláusulas numeradas), marca «He leído y acepto» y **firma con el dedo**; el servidor guarda que aceptó (y cuándo) y guarda su firma. Después elige su Día 1.

El **texto de las 23 cláusulas está escrito dentro de la app**: cambiar una palabra exige tocar código, publicar una app nueva y esperar a que la gente la instale, porque la app no se actualiza por aire. El servidor tampoco sabe **qué texto** aceptó cada persona: guarda «aceptó», no «aceptó esta versión».

El dueño quiere cambiar los términos él mismo, desde la app, cuando quiera, sin tocar código; y poder decir con certeza **qué versión aceptó cada persona**.

## Clarifications

### Session 2026-10-31 (decisiones del dueño, ya tomadas)

- Q: ¿Quién gestiona los términos? → A: **El propio sistema, desde la app, para no editar ni borrar código.**
- Q: ¿Qué se conserva de lo que existe? → A: Lo de hoy sigue valiendo: las 23 cláusulas actuales son el punto de partida y quienes ya aceptaron siguen aceptados.

### Decisiones del dueño del 31/10/2026 (mandan sobre lo que dice el resto de este documento)

Fuente: `~/.config/renaser-e2e/formaciones/decisiones-del-dueno-2026-10-31.md`. **Todas las dudas, la opción A (la recomendada).**

| # | Decisión | Qué se implementó |
|---|---|---|
| Q1 A | Cada formación con los suyos; nacen como copia de los de la Escuela, en borrador «Por revisar». | `terminos_versiones` por formación; V149 deja la versión 1 de la Escuela y un borrador en las otras dos. |
| Q2 A | Administración decide en cada publicación si quienes ya aceptaron deben aceptar de nuevo; por defecto no. | Interruptor «Pedir que acepten de nuevo» (apagado) al publicar; si sí, la app bloquea al abrir hasta aceptar. |
| Q3 A | En una nueva aceptación, casilla y firma. | La misma pantalla de siempre con otro titular. |
| Q4 A | El personal firma los de cada formación en la que trabaja, la primera vez que entra a ella; Administración y Alquimista, los de la Escuela. | `QuePideLaAceptacion.PRIMERA_EN_LA_FORMACION`; la formación de los términos de una persona es `AlcanceDeFormacionFinder.formacionDe`, o la Escuela. |
| Q5 A | El texto actual nunca cambió. | Las aceptaciones de antes se atribuyen a la versión 1 (V150). |
| Q6 A | La persona puede releer lo que aceptó. | `GET /onboarding/terms/accepted` (sin pantalla nueva en Yo: ver el reporte). |
| Q7 A | Texto plano. | Sin formato. |
| Q8 A | Editan Administrador y Alquimista. | `GuardiaDeTerminos`. |
| Q9 A | Sin push: se ve al abrir la app. | — |
| Q10 A | Bloqueo inmediato hasta aceptar. | Sin plazo de gracia. |
| Q11 A | Al pasar de formación se aceptan los de la nueva. | Ídem Q4. |
| Q12 A | Las constancias siguen la política de borrado de cuentas vigente. | `aceptaciones_de_terminos.usuario_id ON DELETE CASCADE`. |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Administración edita y publica los términos sin tocar código (Priority: P1)

Administrador y Alquimista abren los términos, escriben o cambian cláusulas, ven cómo los verá la persona y los publican. Lo que ven las personas nuevas cambia en el acto, sin publicar una app nueva.

**Why this priority**: Es todo el pedido del dueño. Sin esto, cada cambio legal es un desarrollo.

**Independent Test**: Cambiar el texto de una cláusula, publicar y verificar que una persona nueva la lee con el texto nuevo, sin haber cambiado ni instalado la app.

**Acceptance Scenarios**:

1. **Given** Administración, **When** abre **Más opciones → «Términos y condiciones»** (o la formación → «Ficha y términos»), **Then** ve los términos vigentes con su versión, quién y cuándo los publicó, y cuántas personas los aceptaron.
2. **Given** los términos, **When** toca **Editar**, **Then** trabaja sobre un **borrador** (copia de lo vigente) y puede cambiar el título, la introducción, cada cláusula, el texto de la casilla de aceptación y el orden; todo se ve y se guarda sin afectar a nadie.
3. **Given** el borrador, **When** agrega una cláusula, **Then** se numera sola; si la mueve, borra o inserta otra, **las cláusulas se renumeran solas**.
4. **Given** el borrador, **When** toca **Vista previa**, **Then** ve la pantalla de términos tal como la verá la persona (cláusulas, casilla y lienzo de firma), sin guardar nada.
5. **Given** el borrador, **When** toca **Publicar**, **Then** confirma («Lo verán las personas que entren desde ahora») y puede escribir una **nota del cambio** (opcional); queda una **versión nueva** con número, fecha, autor y nota.
6. **Given** un borrador sin publicar, **When** otra persona entra a aceptar los términos, **Then** ve la versión **vigente**, nunca el borrador.
7. **Given** un mentor, un líder o un aprendiz, **When** busca «Términos y condiciones» en Administración, **Then** no existe para ellos.
8. **Given** el borrador con una cláusula vacía o sin ninguna cláusula, **When** intenta publicar, **Then** no puede y se le dice qué falta.

---

### User Story 2 - La persona acepta los términos vigentes de su formación y queda constancia de la versión (Priority: P1)

En su primer ingreso, la persona lee los términos vigentes de **su** formación, marca la casilla y firma, igual que hoy. El sistema guarda **qué versión** aceptó, cuándo y su firma.

**Why this priority**: Sin la constancia de la versión no hay valor legal al cambiar los términos.

**Independent Test**: Publicar la versión 2, hacer que una persona nueva acepte y verificar que su constancia dice «versión 2» con fecha y firma.

**Acceptance Scenarios**:

1. **Given** una persona nueva de la Escuela, **When** llega a los términos (después de su ficha), **Then** ve el texto vigente de la Escuela con exactamente la misma pantalla de hoy: cláusulas numeradas, casilla y firma con el dedo.
2. **Given** que acepta, **When** guarda, **Then** el sistema registra la formación, la **versión** aceptada, la fecha y hora y su firma, y la persona sigue a «Elegir tu Día 1».
3. **Given** la casilla sin marcar o la firma sin hacer, **When** toca **Continuar**, **Then** ve cuál falta (como hoy) y no avanza.
4. **Given** una falla de red al guardar, **When** ocurre, **Then** la persona se entera y no avanza creyendo que quedó firmado (como hoy); nada queda a medias.
5. **Given** que Administración publicó una versión nueva **mientras** la persona leía, **When** envía su aceptación, **Then** el sistema no la registra contra la versión equivocada: la app recarga el texto nuevo y avisa «Los términos cambiaron mientras leías: léelos de nuevo».
6. **Given** una persona del Diplomado, **When** llega a los términos, **Then** ve los de su formación y no los de otra (aislamiento de la spec 022).

---

### User Story 3 - Cuando los términos cambian, Administración decide si hay que aceptarlos de nuevo (Priority: P2)

Al publicar una versión nueva, Administración elige si las personas que ya aceptaron deben aceptar de nuevo. Si lo pide, a esas personas se les muestra, la próxima vez que abran la app, lo que cambió y deben aceptar para seguir.

**Why this priority**: Es lo que hace útil la edición: un cambio legal importante exige nueva aceptación; uno de redacción, no.

**Independent Test**: Publicar una versión pidiendo nueva aceptación; abrir la app con una persona que ya había aceptado y comprobar que la ve y que no puede seguir sin aceptarla; publicar otra sin pedirla y comprobar que a nadie se le interrumpe.

**Acceptance Scenarios**:

1. **Given** una versión nueva publicada **sin** pedir nueva aceptación, **When** las personas que ya aceptaron abren la app, **Then** no ven ningún aviso; siguen figurando con la versión que aceptaron.
2. **Given** una versión nueva publicada **pidiendo** nueva aceptación, **When** una persona que ya aceptó abre la app, **Then** ve una pantalla «Actualizamos nuestros términos» con la nota del cambio, el texto completo y la casilla y la firma; no puede usar el resto de la app hasta aceptar (puede cerrar sesión).
3. **Given** esa pantalla, **When** acepta, **Then** se registra la nueva versión y la persona sigue donde estaba.
4. **Given** una persona que todavía no aceptó ninguna versión, **When** se publica una nueva, **Then** simplemente verá la nueva cuando llegue a los términos.
5. **Given** Administración, **When** mira una versión que pidió nueva aceptación, **Then** ve cuántas personas la aceptaron y cuántas faltan, y quiénes faltan, filtrables por formación. *(Regla exacta: pregunta Q2.)*

---

### User Story 4 - Constancia: quién aceptó qué y cuándo (Priority: P2)

En la ficha de cada persona, Administración ve qué versión de los términos aceptó, cuándo y con qué firma; y para cada versión, cuántas personas la aceptaron.

**Why this priority**: Es la razón legal de versionar; sin consulta no sirve.

**Independent Test**: Aceptar con dos personas versiones distintas y comprobar que la ficha de cada una dice la suya.

**Acceptance Scenarios**:

1. **Given** una persona que aceptó, **When** Administración abre su ficha, **Then** ve «Aceptó los términos de la Escuela, versión 2, el 31 oct 2026, 19:42» y puede ver su firma.
2. **Given** una persona que todavía no aceptó, **When** abre su ficha, **Then** dice «Todavía no aceptó los términos».
3. **Given** el historial de versiones de unos términos, **When** Administración lo abre, **Then** ve cada versión con número, fecha, autor, nota y cuántas personas la aceptaron, y puede **ver el texto completo** de cualquiera.
4. **Given** una versión anterior, **When** Administración toca **Restaurar como borrador**, **Then** se crea un borrador con ese texto (no se publica solo).

---

### User Story 5 - Cada formación puede tener sus propios términos (Priority: P2)

Cada formación (Escuela, Diplomado, Certificación) tiene su propio texto, su propio historial y su propia constancia, y se editan por separado. *(Alcance exacto: pregunta Q1.)*

**Why this priority**: El Diplomado y la Certificación pueden tener condiciones distintas; mezclar los textos sería un riesgo legal.

**Independent Test**: Cambiar los términos del Diplomado y comprobar que los de la Escuela no se mueven y que una persona de la Escuela sigue viendo los suyos.

**Acceptance Scenarios**:

1. **Given** las tres formaciones, **When** Administración abre los términos, **Then** elige primero la formación y trabaja sobre la suya.
2. **Given** que cambia y publica los del Diplomado, **When** una persona de la Escuela abre sus términos, **Then** ve los de la Escuela sin ningún cambio.
3. **Given** el Diplomado y la Certificación recién creados, **When** Administración los abre, **Then** cada una trae una **copia de los términos de la Escuela en borrador**, marcada «Por revisar», que hay que revisar y publicar antes de aceptar a alguien en esa formación.
4. **Given** una formación sin términos publicados, **When** Administración intenta aceptar a alguien en ella (spec 025), **Then** el sistema lo impide y dice «Esta formación todavía no tiene términos publicados».

---

### User Story 6 - La persona puede releer los términos que aceptó (Priority: P3)

Desde Yo, la persona puede abrir los términos que aceptó, con su versión y la fecha, en solo lectura.

**Why this priority**: Transparencia; no fue pedido textual por el dueño (ver Q6).

**Independent Test**: Aceptar y abrir Yo → Mi onboarding → Términos: se ve el texto de la versión aceptada.

**Acceptance Scenarios**:

1. **Given** una persona que aceptó la versión 2, **When** abre sus términos desde Yo, **Then** ve el texto **de la versión 2** aunque exista una versión 3, con «Aceptados el 31 oct 2026».
2. **Given** que hay una versión más nueva, **When** los mira, **Then** el texto aceptado no cambia.

---

### Edge Cases

- **Las 23 cláusulas de hoy**: pasan a ser la **versión 1** de los términos de la Escuela, **publicada**, con el texto que la app trae hoy. **Todas las personas que ya aceptaron figuran como aceptantes de la versión 1**, con su fecha y su firma de siempre. Nadie vuelve a firmar por esto.
- **Persona que aceptó un texto anterior a la versión 1**: el texto se cambió antes de existir las versiones y no queda copia; figura en la versión 1 y se anota «aceptó antes del control de versiones». *(Duda Q5.)*
- **Publicar sin cambios**: el sistema avisa «No hay cambios respecto de la versión vigente» y no crea una versión vacía.
- **Cláusulas que se citan entre sí** («según la cláusula 5»): al renumerar, el sistema no puede corregir el texto de otra cláusula; avisa al guardar si encuentra referencias a números en el texto.
- **Texto muy largo o con saltos de línea**: el límite por cláusula lo dice la pantalla con un contador; los saltos de línea se respetan.
- **Dos administradores editando a la vez**: al publicar, el segundo ve «Otra persona publicó una versión mientras editabas» y los cambios de ambos, antes de decidir; nunca se pisa en silencio.
- **Borrador abandonado**: sigue ahí hasta que se publique o se descarte; nunca se publica solo ni lo ve nadie más que Administración.
- **Persona sin conexión en la pantalla de términos**: no puede aceptar (como hoy); nada queda a medias.
- **Persona que rechaza o no acepta la nueva versión obligatoria**: queda en la pantalla de aceptación; puede cerrar sesión; Administración la ve como «Pendiente de aceptar» en su formación. No se borra su cuenta ni su progreso.
- **Persona que pasa de formación** (spec 022): debe aceptar los términos de su nueva formación; los de la anterior quedan en su historial.
- **Personal y Administración**: hoy también pasan por los términos; qué texto firman cuando hay términos por formación: pregunta Q4.
- **Cuenta eliminada**: las constancias de aceptación siguen la política de borrado de cuentas vigente; esta spec no la cambia.
- **Cambio de nombre de una formación**: el texto de los términos no se reescribe solo; si usa el nombre de la formación mediante un marcador, se actualiza.
- **Acentos, comillas y símbolos legales** (§, «», —): se guardan y se muestran sin deformarse en las dos plataformas.
- **App publicada antes de esta función**: sigue mostrando las 23 cláusulas que lleva dentro y registrando la aceptación como hoy; el servidor la registra contra la versión vigente de la Escuela. Si el texto vigente ya cambió, la constancia dice la versión **vigente** aunque la persona haya leído el texto antiguo de su app (límite aceptado, ver Assumptions).

## Requirements *(mandatory)*

### Functional Requirements

**Gestionar el texto**

- **FR-001**: Solo Administrador y Alquimista MUST poder ver, editar y publicar los términos.
- **FR-002**: Los términos MUST tener título, introducción opcional, cláusulas numeradas y el texto de la casilla de aceptación, todos editables.
- **FR-003**: Administración MUST poder crear, editar, reordenar y quitar cláusulas; la numeración MUST ser automática y consecutiva.
- **FR-004**: Los cambios MUST quedar en un **borrador** que nadie más ve; solo **Publicar** los pone a disposición de las personas.
- **FR-005**: El sistema MUST ofrecer una **vista previa** idéntica a la pantalla que verá la persona, sin guardar nada.
- **FR-006**: Cada publicación MUST crear una **versión** con número, fecha, autor y nota opcional; las versiones MUST conservarse y poder consultarse y restaurarse como borrador.
- **FR-007**: El sistema MUST NOT permitir publicar con cláusulas vacías, sin cláusulas o sin cambios.
- **FR-008**: Dos administradores editando a la vez MUST ser detectados al publicar, sin pisar en silencio lo publicado por el otro.
- **FR-009**: Un texto MUST poder nombrar la formación y su duración mediante marcadores que se muestran con su valor para cada persona.

**Aceptar**

- **FR-010**: La persona MUST aceptar los términos **vigentes de su formación** con casilla y firma como hoy, sin cambios visibles en la pantalla.
- **FR-011**: El sistema MUST registrar, por cada aceptación, la persona, la formación, la **versión**, la fecha y hora y la firma.
- **FR-012**: Una aceptación enviada contra una versión que dejó de ser la vigente MUST NOT registrarse contra la equivocada: la app MUST recargar el texto vigente y avisar.
- **FR-013**: Una falla al guardar la aceptación o la firma MUST impedir avanzar (como hoy).
- **FR-014**: Todo lo existente MUST migrarse sin que nadie vuelva a firmar: las 23 cláusulas de hoy son la versión 1 de la Escuela, y quien ya aceptó figura en ella.

**Cambios y nueva aceptación**

- **FR-015**: Al publicar, Administración MUST poder elegir entre pedir una nueva aceptación a quienes ya aceptaron o no pedirla. *(Regla por defecto y alcance: pregunta Q2.)*
- **FR-016**: Si se pide, la persona MUST ver al abrir la app una pantalla con la nota del cambio, el texto y la aceptación, y MUST NOT poder usar el resto de la app hasta aceptar, pudiendo cerrar sesión.
- **FR-017**: Si no se pide, el sistema MUST NOT interrumpir a nadie.
- **FR-018**: Administración MUST ver, por versión, cuántas personas la aceptaron y cuántas faltan, y quiénes faltan.

**Constancia y formaciones**

- **FR-019**: La ficha de cada persona en Administración MUST mostrar la versión que aceptó, cuándo y su firma.
- **FR-020**: El sistema MUST poder tener términos propios por formación, con historial y constancia separados. *(Alcance: pregunta Q1.)*
- **FR-021**: Una formación MUST NOT aceptar personas hasta tener términos publicados; las formaciones nuevas MUST nacer con una copia de los de la Escuela en borrador, marcada «Por revisar».
- **FR-022**: Las personas MUST ver solo los términos de su formación (aislamiento de la spec 022).
- **FR-023** *(P3)*: La persona MUST poder releer, en solo lectura, el texto de la versión que aceptó.

**Compatibilidad y experiencia**

- **FR-024**: Una app publicada antes de esta función MUST seguir mostrando y registrando la aceptación sin errores; la aceptación se registra contra la versión vigente de la Escuela.
- **FR-025**: Una app nueva contra un servidor anterior MUST seguir mostrando el texto que lleva dentro, como hoy.
- **FR-026**: La edición MUST ser una pantalla completa de Administración con un solo scroll, áreas táctiles de 48 px o más, tipo oración, tuteo de Perú, confirmación en línea al publicar y gesto atrás que sube un nivel.

### Key Entities *(include if feature involves data)*

- **Términos de una formación**: el texto vigente y sus versiones; tiene título, introducción, cláusulas y texto de la casilla.
- **Cláusula**: un párrafo numerado automáticamente; se puede mover, editar y quitar.
- **Versión publicada**: foto del texto en el momento de publicarlo (número, fecha, autor, nota, si pidió nueva aceptación).
- **Borrador**: copia de trabajo, visible solo para Administración.
- **Aceptación**: persona, formación, versión, fecha y hora, firma. Reutiliza el registro de aceptación y de firma que ya existe.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Administración cambia una cláusula y la publica en **menos de 2 minutos**, **sin ningún cambio de código ni versión nueva de la app**.
- **SC-002**: El **100 %** de las aceptaciones nuevas queda con formación, versión, fecha, hora y firma.
- **SC-003**: **0** aceptaciones anteriores se pierden: el **100 %** de quienes ya aceptaron figura como aceptante de la versión 1 de la Escuela, con su fecha y su firma de siempre.
- **SC-004**: Un borrador lo ve **0** personas; un texto publicado lo ve el **100 %** de quienes llegan a los términos después.
- **SC-005**: Cuando Administración pide nueva aceptación, el **100 %** de las personas alcanzadas la ve al abrir la app y **0** usan el resto de la app sin aceptar; cuando no la pide, **0** personas son interrumpidas.
- **SC-006**: Para las personas de la Escuela, la pantalla de términos tiene **0 cambios visibles** respecto de hoy mientras Administración no edite el texto.
- **SC-007**: Una persona de una formación ve **0** términos de otra (aislamiento de la spec 022, por E2E).
- **SC-008**: Una aceptación enviada contra una versión que cambió mientras leía se registra contra la versión equivocada en **0** casos.
- **SC-009**: El historial muestra el **100 %** de las publicaciones con autor y fecha, y **restaurar** una versión funciona en el **100 %** de los casos.

## Fuera de alcance

- **La Política de privacidad y la página de eliminar cuenta** (páginas públicas aparte): no cambian.
- **Contratos de fase** (Fase 2 a 4) y el **Pacto**: no cambian.
- **Texto con formato** (negritas, listas, imágenes, PDF): solo texto con saltos de línea.
- **Traducciones**: los términos son en español.
- **Firma electrónica con valor legal por un tercero** (certificadora): no se pide; se mantiene la firma dibujada con el dedo de hoy.
- **Qué dicen los términos**: lo escribe el dueño; esta spec solo da la herramienta.

## Restricciones del dueño

Reglas del dueño del 2026-10-31 para todas las specs de esta tanda:

1. **Reutilizar lo que ya existe**: la pantalla de términos, el registro de aceptación y de firma de hoy, las pantallas de Administración, la hoja desde abajo, el aviso en línea y los componentes de formulario de la app. Nada de tablas nuevas si una existente sirve con una columna o una relación;
   donde haga falta guardar versiones, se reutiliza lo que ya guarda el catálogo de la ficha o se justifica por qué no alcanza. Datos normalizados y, si algo se desnormaliza, justificado.
2. **No romper la lógica actual**: quienes ya aceptaron siguen aceptados (SC-003); la pantalla de hoy no cambia (SC-006); la app publicada sigue funcionando (FR-024).
3. **Respetar la constitución del repo** (hexagonal, Spring Modulith, SOLID; el módulo de onboarding expone en su `api/` lo que otros necesiten) **y el `AGENTS.md` del frontend** (estructura por feature, esquemas tolerantes, 48 px, tipo oración, tuteo, gesto atrás).
   Tocar Administración y la pantalla de términos exige anotar la autorización en `AGENTS.md` con «lo que cambia y nada más».
4. **Aislamiento total entre formaciones**: cada persona ve solo los términos de su formación y Administración edita una formación sin alterar otra (SC-007). Criterio E2E: «una persona del Diplomado no ve ni acepta los términos de la Escuela».
5. **El dueño hace él mismo todas las preguntas antes de implementar**: la sección «Dudas para el dueño» las lista todas, aunque tengan supuesto.

## Assumptions

- **El texto es plano** (párrafos con saltos de línea), sin formato.
- **Límites razonables**: hasta 2.000 caracteres por cláusula y hasta 100 cláusulas; el plan los puede ajustar.
- **Las 23 cláusulas actuales**, tal como las lleva la app hoy, son la versión 1 de la Escuela.
- **Todas las aceptaciones existentes se atribuyen a la versión 1**; si el texto cambió alguna vez antes, no hay forma de reconstruirlo (Q5).
- **La firma** sigue siendo el dibujo con el dedo de hoy; en una nueva aceptación se vuelve a pedir casilla y firma (Q3).
- **Una nueva aceptación obligatoria bloquea el uso de la app** hasta aceptar; no hay plazo de gracia (Q10).
- **El personal y Administración** firman, mientras no se decida otra cosa, los términos de la Escuela (o, si solo tienen otras formaciones, los de la primera que se les asigna) (Q4).
- **Quién edita**: Administrador y Alquimista, igual que el resto de la configuración (Q8).
- **No hay aviso push** por un cambio de términos: la persona lo ve al abrir la app (Q9).
- **Pasar de formación** exige aceptar los términos de la nueva.
- **Un servidor anterior** no entrega términos: la app nueva usa el texto que lleva dentro; la aceptación se registra como hoy.
- **Dependencias**: spec 022 (formaciones) para el texto por formación; spec 025 para impedir aceptar en una formación sin términos; spec 026 porque los términos siguen a la ficha.

## Dudas para el dueño

Todas las dudas reales, aunque ya tengan supuesto. Las marcadas **Sí** cambian el alcance.

| # | Duda | Opciones (la recomendada primero) | ¿Cambia el alcance? |
|---|---|---|---|
| Q1 | [NEEDS CLARIFICATION: ¿los términos son los mismos para las tres formaciones o cada formación tiene los suyos?] | A) Cada formación con los suyos (nacen como copia de los de la Escuela, en borrador «Por revisar»); recomendada porque el Diplomado y la Certificación pueden tener condiciones distintas. B) Un solo texto común, con el nombre y la duración de cada formación como marcadores. | **Sí** |
| Q2 | [NEEDS CLARIFICATION: al publicar una versión nueva, ¿quienes ya aceptaron deben aceptar de nuevo?] | A) Lo decide Administración en cada publicación («Pedir nueva aceptación» sí/no; por defecto no) (recomendada). B) Siempre se pide. C) Nunca se pide: el texto nuevo solo rige para quien llegue después. | **Sí** |
| Q3 | En una nueva aceptación, ¿se vuelve a pedir la firma o basta la casilla? | A) Casilla y firma, igual que la primera vez (recomendada: mismo valor). B) Solo la casilla. | No |
| Q4 | Con términos por formación, ¿qué firman el personal y Administración/Alquimista (que no pertenecen a una sola formación)? | A) El personal firma los de cada formación en la que trabaja, la primera vez que entra a ella; Administración y Alquimista, los de la Escuela. B) Un texto general aparte para personal y Administración. C) Los de la Escuela para todos. | Sí, si A o B |
| Q5 | ¿El texto actual de las 23 cláusulas cambió alguna vez desde que hay gente aceptándolo? Lo digo porque hoy no se sabe qué texto aceptó cada quien. | A) No, siempre fue el mismo. B) Sí, cambió (indicar cuándo) y se anota «aceptó antes del control de versiones». | No (afecta la constancia legal) |
| Q6 | ¿La persona puede releer desde Yo los términos que aceptó? | A) Sí, en solo lectura (P3). B) No, solo se ven al aceptar. | Sí, si A (suma una pantalla) |
| Q7 | ¿Texto plano o con formato (negritas, listas)? | A) Plano (recomendada: se ve igual en todos los teléfonos y simple de editar). B) Con formato básico. | Sí, si B |
| Q8 | ¿Quién edita los términos? | A) Administrador y Alquimista (recomendada). B) Solo Administrador. C) También Soporte TI. | No |
| Q9 | Cuando se pide nueva aceptación, ¿se avisa con una notificación o solo se ve al abrir la app? | A) Solo al abrir (recomendada). B) También un aviso al teléfono. | No (B suma un aviso) |
| Q10 | Si se pide nueva aceptación, ¿se bloquea la app de inmediato o hay plazo (por ejemplo 7 días)? | A) Bloqueo inmediato hasta aceptar (recomendada: es lo más claro legalmente). B) Plazo en el que se puede seguir usando la app con un recordatorio. | Sí, si B |
| Q11 | Al pasar a una persona de formación, ¿debe aceptar de nuevo los términos de la nueva? | A) Sí (recomendada). B) No, valen los que ya aceptó. | No |
| Q12 | Las constancias de aceptación de una cuenta eliminada, ¿se conservan por razones legales? | A) Se rige por la política de borrado de cuentas vigente (no cambia). B) Se conservan sin datos personales. | No (legal) |
