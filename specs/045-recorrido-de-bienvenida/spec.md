# Feature Specification: Recorrido de bienvenida (tutorial hasta el primer post, con confeti)

**Feature Branch**: `045-recorrido-y-046-sobrenombre` (spec; la implementación irá en `045-recorrido-de-bienvenida`, backend y app)
**Created**: 2026-11-06
**Status**: Aprobada por el dueño (2026-11-06), con las decisiones de abajo. En implementación (D-330).
**Input**: Pedido del dueño (2026-11-06): que después de la Ficha Inicial o del diagnóstico la persona llene todo completo; que no sea una pantalla de «publicar» sino **un recorrido tipo tutorial, no tan específico**, que la guíe hasta publicar su **primer post en el Muro**; al publicar, **una animación de felicitación con confeti**; que explique **rápido y en poco** dónde está cada cosa: **Hoy, Plan y Training**; y «el inglés con español se ve feo en una app»: **ningún texto en inglés**.

**Se apoya en**: la guía de arranque actual (`src/features/sparkie/`: `SparkieOverlay`, `useArranqueGuiado`, `data/asistente.ts`; D-95, D-102, D-255 e issue #50 de la app), la Ficha Inicial (`src/features/onboarding/`), el diagnóstico (spec 026), «Tu programa empieza mañana» (spec 036), los términos (specs 027 y 039), el compositor real del Muro (`NuevaPublicacion`) y la celebración de pantalla completa (`fenix/components/AnfitrionDeCelebraciones`, `PantallaDeCelebracion`).

**Vista previa**: `specs/045-recorrido-de-bienvenida/vista-previa/index.html` (una sola para las specs 045 y 046; se abre en el navegador, sin red salvo la fuente).

## Decisiones del dueño (2026-11-06)

| Pregunta | Decisión |
|---|---|
| D1 · La guía de SER actual | **A, integrar**: el recorrido reemplaza el saludo y la guía al Muro; la burbuja de SER queda solo para el Pacto. |
| D2 · A quién le sale | **A, solo cuentas nuevas**: la migración marca como terminadas (`CUENTA_ANTERIOR`) todas las cuentas que ya existen. |
| D3 · Foto en el primer post | **A, sigue obligatoria.** |
| D4 · El personal | **A, lo ve solo si hace su programa propio** (fila de programa activada). |
| D5 · Nombre de la pestaña | **A, «Entrenar»** en la barra («ENTRENAR») y en todos los textos que la nombran. La ruta interna sigue siendo `'Training'`. **El dueño autorizó explícitamente** tocar el nombre en la barra de los cinco tabs. |
| D6 · El saludo | **A, «Te damos la bienvenida»**, sin género. |
| D7 · El confeti | **A, solo en el primer post.** |
| D8 · Los textos en inglés | **A, corregirlos todos en un commit aparte**: Ranking → «Tabla», Stickers → «Figuritas», checklist → «lista», Tickets → «Consultas», «Mi onboarding» → «Mis documentos», «· auto» → «· del teléfono». «chat» se queda. |
| D9 · El primer post (06/11, tras probarlo) | **Obligatorio para una cuenta nueva.** Mientras no publica, el resto de la app queda bloqueado: el paso del Muro tapa las cinco pestañas y las demás secciones, con un candado sobre Hoy, Plan, Entrenar y Yo y la línea «Lo demás se abre cuando publiques.». Puede volver al compositor y nada más. **Se quitan «Ahora no», «Saltar» y toda forma de saltarlo** (el atrás del sistema no lo cierra: en el primer paso y en el del Muro no hace nada). Cerrar la app y abrirla vuelve al paso del Muro, bloqueado. Mientras dura es una **capa obligatoria**: los avisos del teléfono esperan; el Código Renaser y el panel de SER lo pausan y vuelve al cerrarse. Repetido desde Yo → Ajustes sí se puede cerrar. **Cambia FR-004 y FR-005**: saltar el recorrido ya no vale para una cuenta nueva. |
| D10 · Modo oscuro (06/11) | El velo, el foco, las tarjetas de los pasos, los candados y la felicitación con confeti usan los colores del tema (`useTheme`), nunca colores fijos: el velo es el token `velo` (72 % en claro, 78 % en oscuro), la tarjeta `cardBgAlt` y el confeti sale de `coloresDelConfeti(tema)`. Se revisa con capturas en claro y en oscuro. |
| D11 · «Se reinicia cada rato» (06/11) | Causas encontradas: (1) el recorrido se cerraba y volvía al paso 1 cuando la sesión parpadeaba (se relee el ingreso al volver al frente); ahora solo se reinicia al cambiar de **cuenta**; (2) el paso se guardaba en el servidor sin esperar ni reintentar, y si no llegaba (sin red) al reabrir la app volvía a uno anterior; ahora también se guarda en el teléfono por cuenta y se retoma desde el más adelantado (`max(servidor, teléfono)`); (3) en desarrollo, Fast Refresh recargaba la app (no pasa en el APK). |

**Autorización de los cinco tabs**: concedida por el dueño el 06/11 para todo lo listado en la sección «Autorización de los cinco tabs» y para el nombre «Entrenar».

**Cambio de diseño respecto del borrador**: el estado del recorrido no va en `GET /onboarding/state` sino en su propia ruta, `GET/PUT /api/v1/me/recorrido` y `POST /api/v1/me/recorrido/terminar`
(`{ mostrar, paso, terminadoEn, como }`), con su tabla `recorrido_de_bienvenida` (V180). Así no se toca el estado del ingreso, que leen muchas pantallas.

---

## Contexto: lo que hay hoy

1. **Ingreso**: Ficha Inicial (o diagnóstico, spec 026) → Términos con «Mi aceptación» (spec 039) → «Tu programa empieza mañana» (spec 036, se activa solo) → Hoy.
2. **Al llegar a Hoy**, la guía de arranque (`SparkieOverlay`, la burbuja «✳ SER») hace tres cosas, y el estado sale del servidor (`useArranqueGuiado`):
   - saludo de SER (cuatro párrafos largos sobre «la inteligencia de RENASER»);
   - «Tu primera publicación» → «Llévame al Muro» (abre Comunidad con el compositor) o «Ahora no»; mientras no publica, la burbuja dice «Te espero acá»;
   - publicado → «Ya eres parte.» → «Leer y firmar el Pacto».
   La guía termina cuando el Pacto está firmado. Si la persona cierra la app a mitad, vuelve a salir.
3. **Nada explica dónde está cada cosa**: la persona cae en Hoy con cinco pestañas y una burbuja que solo habla del Muro y del Pacto.
4. **No hay celebración del primer post**: la pantalla completa de celebración (`PantallaDeCelebracion`) existe para los hitos del día (día completo, racha de 7 y 30, fase nueva), con el fénix, brasas doradas y `tacto.hito()`, pero **no tiene confeti** y no se dispara al publicar.
5. **El Muro exige al menos una foto** por publicación, en la app (`NuevaPublicacion`) y en el servidor (`Publicacion`: «La publicacion debe llevar al menos una foto o video»).

## Qué cambia (resumen)

Un **recorrido corto, de 5 pasos**, que sale **una sola vez por cuenta**, justo después del ingreso (cuando la persona llega a Hoy por primera vez). Oscurece la pantalla, deja **un foco** sobre la pestaña de la que habla, dice **una frase** y sigue. El último paso lleva al **compositor real del Muro**: la persona escribe, elige su foto y publica de verdad. Al publicar, **confeti dorado** sobre la pantalla de celebración, una vibración, y listo.

---

## Clarifications

### Session 2026-11-06 (pedido del dueño)

- «No tan específico»: el recorrido **no enseña cada botón**; dice para qué sirve cada pestaña en una frase.
- La meta del recorrido es **el primer post**: ahí termina y ahí se celebra.
- Ningún texto en inglés en lo que se ve (ver la lista al final y la historia 4).

### Preguntas abiertas

Ver la sección «[DECISIÓN DEL DUEÑO]» al final. Mientras no estén respondidas, esta spec usa la opción recomendada (la primera de cada pregunta).

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Llego a Hoy por primera vez y la app me muestra dónde está cada cosa (Priority: P1)

Como persona que acaba de terminar su ingreso, al llegar a Hoy veo un recorrido de 5 pasos, una frase por paso, con un foco sobre la pestaña de la que habla, y puedo avanzar o saltarlo.

**Independent Test**: con una cuenta recién activada y sin el recorrido visto, abrir la app muestra el paso 1 sobre Hoy; «Siguiente» recorre Hoy → Plan → Training → Muro; «Saltar» lo cierra y no vuelve a salir.

**Acceptance Scenarios**:

1. **Given** una cuenta que terminó su ingreso y el servidor dice que no vio el recorrido, **When** llega a Hoy, **Then** ve el paso 1 («Bienvenida») con el fénix de SER, un título y una frase, «Empezar» y «Saltar».
2. **Given** el paso 2, 3 o 4, **Then** la pantalla se oscurece salvo un foco circular sobre la pestaña correspondiente de la barra de abajo (Hoy, Plan, Training), y una tarjeta encima del foco dice su nombre y una frase. Botones: «Siguiente» (principal) y «Saltar» (texto). Arriba, la cuenta del paso («2 de 5») en puntitos.
3. **Given** cualquier paso, **When** toca «Saltar», **Then** se pregunta una sola vez «¿Saltar el recorrido? Puedes volver a verlo desde Yo → Ajustes.» con «Saltar» y «Seguir viendo»; al confirmar, el recorrido no vuelve a salir solo.
4. **Given** cualquier paso, **When** usa el gesto atrás del sistema, **Then** vuelve al paso anterior; en el paso 1, equivale a «Saltar» (con la misma pregunta). Nunca cierra la app.
5. **Given** que cierra la app a mitad, **When** vuelve, **Then** el recorrido sigue en el paso donde quedó (lo recuerda el servidor), no desde cero.
6. **Given** que el foco está sobre una pestaña, **When** toca dentro del foco, **Then** avanza igual que «Siguiente» (no cambia de pestaña: el recorrido no navega hasta el paso del Muro). Tocar fuera del foco y de la tarjeta no hace nada.

### User Story 2 - El último paso me lleva a publicar mi primer post de verdad (Priority: P1)

Como persona en el paso 5, toco «Escribir mi publicación», se abre el compositor real del Muro con una sugerencia de qué escribir, elijo mi foto y publico.

**Independent Test**: en el paso 5, «Escribir mi publicación» abre Comunidad → Muro con `NuevaPublicacion` abierto; publicar crea la publicación por `POST /api/v1/wall` como siempre; el recorrido queda «terminado» en el servidor.

**Acceptance Scenarios**:

1. **Given** el paso 5 («Tu primera publicación»), **Then** el foco está sobre la pestaña Comunidad, la tarjeta dice «Preséntate al grupo: quién eres y qué vienes a cambiar. Con una foto.» y el botón «Escribir mi publicación».
2. **When** lo toca, **Then** el velo se va, la app pasa a Comunidad → Muro y abre **el mismo compositor de siempre** (`abrirComposerMuro`), con el texto de ayuda del campo «¿Quién eres y qué vienes a cambiar?». **Nada se publica solo** y el texto no viene escrito.
3. **Given** el compositor abierto desde el recorrido, **When** cierra sin publicar, **Then** queda una pastilla chica fija sobre la barra («Tu primera publicación · Continuar»), que vuelve a abrir el compositor; la pastilla no tapa la barra de escribir ni los botones de SER.
4. **Given** que publica, **When** el servidor confirma (`POST /wall` 201), **Then** sale la celebración con confeti (historia 3) y el recorrido queda terminado.
5. **Given** que la publicación falla (red, foto), **Then** el error es el de siempre del compositor y **no** hay confeti.
6. **Given** que la persona **ya tenía** publicaciones (`GET /wall/mine` > 0, p. ej. cuentas anteriores), **Then** el paso 5 no se muestra: el recorrido termina en el paso 4 con «Listo» y sin confeti.

### User Story 3 - Al publicar, una felicitación con confeti (Priority: P1)

Como persona que acaba de publicar su primer post, veo una felicitación de pantalla completa con confeti dorado, una sola vibración, y vuelvo al Muro con mi publicación a la vista.

**Acceptance Scenarios**:

1. **Given** el primer post confirmado por el servidor, **Then** se monta `PantallaDeCelebracion` con: el fénix, el título «¡Tu primera publicación!», una línea «Ya eres parte del grupo.», y **confeti dorado** que cae una sola vez.
2. **Confeti** (decisiones de movimiento, skills `animate-expo` y `emil-design-eng`):
   - **Corto**: una sola ráfaga de **1,4 s**; nada se repite ni queda en bucle.
   - **Pocas piezas**: 36 rectángulos y círculos chicos (6–10 dp) en **la paleta de la marca** (dorado `#B2924F`, dorado claro `#D9C08A`, crema `#FCFBF9` con borde, y un acento `#1E1B18` al 30 %). Nada de arcoíris.
   - **Hilo de la interfaz**: solo `transform` (translateY, translateX, rotate) y `opacity` con Reanimated; caída con `Easing.out(Easing.quad)` y un vaivén lateral leve; cada pieza se desvanece en los últimos 300 ms. Sin `setState` por cuadro.
   - **No bloquea**: `pointerEvents="none"`; «Seguir» funciona durante el confeti.
   - **Una sola vibración**: `tacto.hito()` al aparecer, una vez (la misma de los hitos). El confeti no vibra por su cuenta.
   - **«Reducir movimiento»**: sin confeti; la pantalla aparece con un fundido y el fénix quieto. Sí vibra una vez.
   - **Lector de pantalla**: se anuncia «¡Tu primera publicación! Ya eres parte del grupo.»; la pantalla no se cierra sola (como hoy).
3. **Se cierra** con «Seguir», tocando, con el atrás del sistema o sola a los 3,8 s (como las otras celebraciones). Al cerrar, queda el Muro con la publicación arriba.
4. **Cuenta como la celebración del día** (una por día por cuenta, `fenix.celebracion.<id>`): si ese mismo día otra celebración ya salió, igual sale esta (es la primera vez y no se repite nunca) — **excepción explícita** a «una por día».
5. El confeti es un componente nuevo (`fenix/components/Confeti`) que `PantallaDeCelebracion` monta solo cuando se le pide (`conConfeti`); las demás celebraciones **no cambian**.

### User Story 4 - Ningún texto en inglés en lo que ve la persona (Priority: P2)

Como persona que usa la app en español, no leo palabras en inglés mezcladas («Training», «Ranking», «Stickers», «Tickets», «onboarding»).

**Acceptance Scenarios**:

1. **Given** el recorrido, **Then** ninguno de sus textos tiene palabras en inglés (los nombres de marca —WhatsApp, Google, Zoom, Meet, Drive— no cuentan).
2. **Given** la lista de textos mezclados de la app (al final de esta spec), **When** el dueño elige cómo llamarlos, **Then** se cambian **en un commit aparte** y una prueba barre `src/` para que no vuelvan (como `ningunNoventaDiasEscrito.test.ts`), con una lista de excepciones de marca.
3. **La pestaña «Training»**: el dueño la nombra así en el pedido; cambiarla toca la barra de pestañas (regla de los cinco tabs). Ver [DECISIÓN DEL DUEÑO] D5.

### User Story 5 - Volver a ver el recorrido (Priority: P3)

Como persona que lo saltó, puedo verlo otra vez desde **Yo → Ajustes → «Ver el recorrido»** (fila con ícono `compass`). Al repetirlo, el paso 5 dice «Ir al Muro» y no celebra si ya publicó.

### Edge Cases

- **Personal (mentor, líder, administración, alquimista)**: el recorrido sale igual a quien hace su programa propio; a quien no lo hace (sin fila de programa), no sale solo (ver D4).
- **Día 0**: el recorrido sale el Día 0 (el día del ingreso), aunque el programa empiece mañana: Hoy, Plan y Training ya se pueden mirar (D-103).
- **Capas obligatorias**: el recorrido **espera** a que se cierren el Código Renaser (días 1–7), los términos actualizados y la elección de formación; nunca se monta encima de otra capa ni del panel de SER (la misma regla de `panelDeSerAbierto`, issue #50).
- **Avisos del teléfono** que abren otra pantalla mientras el recorrido está abierto: el recorrido se pausa (queda en su paso) y sigue al volver a Hoy.
- **Letra grande** (tope 1,2): la tarjeta crece hacia arriba; nunca tapa el foco. Pantalla baja: la tarjeta va arriba del foco si no cabe.
- **Barra de pestañas escondida** (se esconde al desplazar, D-246): al empezar el recorrido la barra se muestra y queda fija hasta que termina.
- **Web**: sale igual (el foco se calcula con la medida real de la pestaña).
- **Sin red al marcar el paso**: el recorrido sigue en el teléfono y reintenta al volver; nunca se traba por eso.
- **Soporte TI** con `muro` o `muro.publicar` apagado o no disponible: el paso 5 no se muestra; el recorrido termina en el 4.
- **«Ver como»** (Soporte TI): el recorrido no sale ni escribe nada.

---

## Los pasos (5)

| # | Foco | Título | Frase | Botones |
|---|------|--------|-------|---------|
| 1 | Ninguno: el fénix de SER al centro | «Te damos la bienvenida, Ana» (con su primer nombre; sin nombre, «Te damos la bienvenida») | «Te muestro en un minuto dónde está cada cosa.» | «Empezar» · «Saltar» |
| 2 | Pestaña **Hoy** | «Hoy» | «Lo de hoy, de un vistazo: tu hábito de esta hora y tu acompañante SER.» | «Siguiente» · «Saltar» |
| 3 | Pestaña **Plan** | «Plan» | «Tus objetivos y la semana: aquí decides hacia dónde vas.» | «Siguiente» · «Saltar» |
| 4 | Pestaña **Entrenar** (botón dorado del centro) | «Entrenar» | «Tus hábitos del día, hora por hora. Aquí registras lo que haces.» | «Siguiente» · «Saltar» |
| 5 | Pestaña **Comunidad** | «Tu primera publicación» | «Preséntate al grupo: quién eres y qué vienes a cambiar. Con una foto.» | «Escribir mi publicación» · «Ahora no» |

- Tipo oración, tuteo de Perú, título a 20 px seminegrita, frase a 16 px, botones de 48 de alto o más.
- Los textos viven en un solo archivo (`recorrido/data/pasosDelRecorrido.ts`) para que el dueño los cambie sin tocar la lógica.

## Cómo se resalta cada pestaña

- **Velo**: una capa a pantalla completa con el texto principal `#1E1B18` al **72 %** de opacidad (en oscuro, negro al 78 %). Dibujada con `react-native-svg` (ya instalado): un rectángulo con un **agujero circular** (máscara) sobre la pestaña. Sin desenfoque (`expo-blur` pediría un APK nativo nuevo).
- **Foco**: círculo de diámetro = ancho del ícono + rótulo + 16 dp de aire (mínimo 64), con un **aro dorado** de 2 dp. La posición sale de **medir** la pestaña (`onLayout` / `measureInWindow` de cada botón de `TabBar`), no de números fijos: así sirve en 320–430 dp, tablets y web.
- **Movimiento**: entre pasos el agujero **se desliza** de una pestaña a la otra (240 ms, `Easing.out(Easing.cubic)`), y la tarjeta hace un fundido con 8 dp de subida (200 ms). El aro **respira** una vez al llegar (escala 1 → 1,06 → 1, 500 ms), no en bucle. Con «reducir movimiento»: el agujero salta al lugar y la tarjeta solo hace fundido.
- **Tarjeta**: fondo crema `#FCFBF9`, radio 20, sombra suave, una flecha chica que apunta al foco. Puntitos del paso («2 de 5»: el actual dorado y ancho).
- **Tacto**: `tacto.seleccion()` al pasar de paso; ninguna otra vibración hasta el confeti.
- **Lector de pantalla**: el velo es un `Modal` accesible; cada paso se anuncia «Paso 2 de 5. Plan. Tus objetivos y la semana…». El foco del lector va a la tarjeta.

## El paso del Muro (detalle)

- Es **el compositor real** (`NuevaPublicacion`), no una copia: mismas reglas (texto y al menos una foto), mismo `POST /api/v1/wall`, mismas alertas. El recorrido solo:
  1. abre Comunidad → Muro con `abrirComposerMuro: true` y una marca `desdeElRecorrido: true`;
  2. cambia el texto de ayuda del campo a «¿Quién eres y qué vienes a cambiar?» (solo esta vez);
  3. escucha `escucharPostPublicado` (ya existe en `sparkie/events/avisoPrimerPost`) para disparar la celebración.
- **Formación**: el post va a la formación de la persona (spec 024). Para Administración/Alquimista con «Todas» elegido, el compositor ya pide elegir una formación.
- **La foto obligatoria**: ver **[DECISIÓN DEL DUEÑO] D3**.

## Cuándo sale (una vez por cuenta)

- **Lo recuerda el servidor**, no el teléfono (como el arranque guiado: cambiar de celular no lo vuelve a mostrar). Tabla `recorrido_de_bienvenida` (V180) y rutas `GET/PUT /api/v1/me/recorrido` y `POST /api/v1/me/recorrido/terminar` (`como`: `PUBLICO`, `LISTO` o `SALTO`). Un APK anterior no las llama.
- **Sale cuando** (todo lo decide el servidor + la app):
  - la cuenta terminó el ingreso (ficha o diagnóstico, términos y activación), y
  - `tourSeenAt` es nulo, y
  - no hay ninguna capa obligatoria a la vista, y
  - la persona está en Hoy.
- **Termina** con: el primer post publicado, «Listo» del paso 4 (si ya había publicado), o «Saltar» confirmado. En los tres casos el servidor guarda `tourSeenAt`.
- **Cuentas que ya existen**: ver **[DECISIÓN DEL DUEÑO] D2**.

## Qué pasa con la guía SER actual

**[DECISIÓN DEL DUEÑO] D1.** La opción recomendada (A) es **integrarla**: el recorrido reemplaza el saludo largo y «Tu primera publicación» de `SparkieOverlay`; la burbuja «✳ SER» queda solo para lo que viene **después** del primer post (la antesala del Pacto, «Ya eres parte. → Leer y firmar el Pacto»), con su texto de siempre. Así no hay dos guías hablando del Muro. Si gana B, ver la pregunta.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Tras el ingreso, la app MUST mostrar un recorrido de 5 pasos (4 si ya publicó o si el Muro no está disponible), una vez por cuenta, recordado en el servidor.
- **FR-002**: Los pasos 2 a 5 MUST oscurecer la pantalla dejando un foco circular sobre la pestaña correspondiente, medido en tiempo de ejecución.
- **FR-003**: Cada paso MUST tener un título y **una** frase de hasta 90 caracteres, en español, tipo oración y tuteo.
- **FR-004**: El recorrido MUST poder repetirse desde Yo → Ajustes, y ahí sí se puede saltar con una confirmación. **Para una cuenta nueva MUST NOT poder saltarse** (D9).
- **FR-005**: El gesto atrás MUST retroceder un paso. En el primero y en el del Muro de una cuenta nueva no hace nada (D9); repetido desde Yo, en el primero equivale a «Saltar».
- **FR-006**: El paso del Muro MUST abrir el compositor real; nada se publica sin que la persona toque «Publicar».
- **FR-007**: Al confirmar el servidor el primer post, la app MUST mostrar la celebración con confeti de 1,4 s, una sola vibración, sin bloquear toques, y sin confeti con «reducir movimiento».
- **FR-008**: El recorrido MUST NOT montarse encima de una capa obligatoria ni del panel de SER.
- **FR-009**: El servidor MUST guardar el paso y cómo terminó el recorrido (`recorrido_de_bienvenida`) y devolverlo en `GET /api/v1/me/recorrido` con `mostrar` decidido por él (D2, D4).
- **FR-010**: Ningún texto del recorrido MUST contener palabras en inglés (salvo nombres de marca).
- **FR-011**: La guía SER actual MUST quedar según D1, sin dos guías sobre el Muro a la vez.
- **FR-012**: La barra de pestañas MUST quedar visible y fija mientras dura el recorrido.
- **FR-013**: El velo, el foco, las tarjetas, los candados y el confeti MUST usar los colores del tema, en claro y en oscuro (D10).
- **FR-014**: Mientras una cuenta nueva no publique su primer post, el resto de la app MUST quedar bloqueado (D9), también al cambiar de pestaña, al volver del compositor sin publicar y al cerrar y reabrir la app; los avisos del teléfono MUST esperar.
- **FR-015**: El recorrido MUST NOT volver a un paso anterior al alcanzado por perder la red al guardar, por cerrar la app ni porque la sesión se relea; dos lecturas que llegan al revés MUST NOT pisar la vigente (D11).

### Success Criteria

- **SC-001**: Una cuenta nueva publica su primer post en la primera sesión en mayor proporción que hoy (medir con `GET /wall/mine` a las 24 h de activar; línea base: las últimas 4 semanas). [sin prueba automática: es una medida de uso en producción]
- **SC-002**: El recorrido completo (sin escribir el post) toma menos de 40 s. [sin prueba automática: se mide en el E2E con una persona]
- **SC-003**: Ninguna cuenta ve el recorrido dos veces sin pedirlo (prueba: dos teléfonos, la misma cuenta).
- **SC-004**: El confeti no baja de 55 cuadros por segundo en un teléfono de gama baja (medido con el monitor de rendimiento de Reanimated en el emulador). [sin prueba automática: se mide en un teléfono]

---

## Autorización de los cinco tabs (pedido explícito)

> **Se pide al dueño autorización explícita** (regla «Integridad del Core» del `AGENTS.md` de la app) para:
> 1. montar un velo temporal **por encima de Hoy, Plan, Training y Comunidad** y de la barra de pestañas mientras dura el recorrido;
> 2. medir los botones de `TabBar` (sin cambiar su forma, íconos ni nombres);
> 3. en Comunidad → Muro, cambiar **solo** el texto de ayuda del compositor cuando se abre desde el recorrido;
> 4. en Yo → Ajustes, sumar la fila «Ver el recorrido»;
> 5. si D1 = A, quitar de la burbuja SER el saludo y la guía al primer post.
> Ninguna pestaña cambia de forma fuera de esto. Cambiar el **nombre** de «Training» es otra autorización (D5).

## Pruebas (cuando se implemente)

- Jest: `pasosDelRecorrido` (textos, sin inglés), `cuandoSaleElRecorrido` (servidor, capas, ya publicó, Muro apagado, «ver como»), `focoDeLaPestana` (medidas a 320/390/430 dp y web), `recorridoDeBienvenida` (avanzar, atrás, saltar con confirmación, retomar en el paso), `primerPostConConfeti` (celebra solo con 201, una vibración, sin confeti con reducir movimiento), `guiaSerTrasElRecorrido` (D1).
- Marcas de trazabilidad: `// spec 045 FR-00N`.
- Maestro: `.maestro/recorrido-de-bienvenida/01-recorrido-completo-y-primer-post.yaml`, `02-saltar.yaml`, `03-retomar-tras-cerrar.yaml`.
- Backend: `EstadoOnboardingServiceTest` (los dos campos), migración aditiva.

## Compatibilidad

- **APK nuevo + backend anterior**: `GET /me/recorrido` responde 404; la app **no lo muestra** (nunca mostrar sobre una duda) y la guía SER queda como hoy.
- **APK anterior + backend nuevo**: ignora los campos; todo como hoy.
- **Hace falta un APK nuevo** (solo JavaScript: el confeti es Reanimated + SVG, ya instalados) y el backend con la migración antes.

---

## [DECISIÓN DEL DUEÑO] (respondidas el 06/11: ver «Decisiones del dueño» arriba)

**D1. ¿Qué pasa con la guía SER de hoy (la burbuja «✳ SER»)?**
- **A (recomendada)**: el recorrido la **integra**. El saludo largo y «Tu primera publicación» se van; la burbuja queda solo para después del primer post: «Ya eres parte → Leer y firmar el Pacto». Una sola guía sobre el Muro.
- B: el recorrido la **reemplaza entera**, también el Pacto; el Pacto se ofrece desde Yo → Mi onboarding (hoy ya está ahí) y una tarjeta en Hoy.
- C: conviven: primero el recorrido, después la burbuja con todo como hoy (repite lo del Muro; no recomendado).

**D2. ¿A quién le sale el recorrido la primera vez?**
- **A (recomendada)**: solo a las cuentas que terminen su ingreso **desde esta versión**. Las de antes pueden verlo desde Yo → Ajustes.
- B: a todas las cuentas una vez, al actualizar la app (incluye a quien ya publicó: verían 4 pasos, sin el del Muro).

**D3. El Muro exige una foto. ¿El primer post también?**
- **A (recomendada)**: sí, igual que siempre; la tarjeta lo dice («Con una foto.»). No cambia el servidor.
- B: el primer post puede ir **solo con texto** (cambio en el servidor solo para la primera publicación de cada cuenta).

**D4. ¿El personal (mentor, líder, administración, alquimista) ve el recorrido?**
- **A (recomendada)**: sí, si hace su programa propio; si no, solo desde Yo → Ajustes.
- B: todos, siempre, una vez.
- C: solo aprendices.

**D5. ¿Cómo se llama la pestaña «Training» (y los textos que dicen «Training → Planificar»)?**
- **A (recomendada)**: **«Entrenar»** (verbo, como «Hoy» y «Plan» son cortos; en la barra «ENTRENAR» cabe en 72 dp a 11 px).
- B: «Hábitos».
- C: «Práctica».
- D: dejar «Training» (es nombre de marca del método). En ese caso el recorrido dice «Training» y se anota como excepción de marca.

**D6. El saludo del paso 1**
- **A (recomendada)**: «Te damos la bienvenida, Ana» (no depende del género).
- B: «Bienvenida, Ana» / «Bienvenido, Juan» según la ficha.

**D7. El confeti**
- **A (recomendada)**: solo en el primer post (es único).
- B: también en las otras celebraciones (día completo, rachas, fase nueva).

**D8. Los demás textos en inglés de la lista (abajo)**
- **A (recomendada)**: cambiarlos todos en un commit aparte de esta spec, con los reemplazos propuestos.
- B: cambiar solo los del recorrido y dejar la lista para después.

---

## Textos en inglés o mezclados encontrados en la app (06/11, `src/` de la app en `master`)

Se barrió `src/` buscando textos que ve la persona (no código, rutas ni comentarios). Los nombres de marca (WhatsApp, Google, Apple, Zoom, Google Meet, Google Drive, Health Connect, Mi Fitness, Strava) quedan fuera.

| Texto visible | Dónde | Reemplazo propuesto |
|---|---|---|
| «TRAINING» (pestaña) y «Training» en textos («Training → Planificar», «Abrir Training», «Las dimensiones que ve esta formación en Training», «aparecen en Training») | `components/TabBar.tsx:29`, `features/huecos/utils/agendaDelDia.ts:226-257`, `screens/HoyScreen.tsx:482`, `features/admin/screens/HabitosProgramaAdminScreen.tsx:127`, `HabitoProgramaFormScreen.tsx:279`, `features/alarmas/components/InterruptoresDeAvisos.tsx:88`, `features/objetivos/components/TarjetasDelPlan.tsx:343` (lector de pantalla) y `AgendarAccionesModal.tsx:283` | Según D5 («Entrenar») |
| «Ranking» (sección de Comunidad), «Ranking general / de coherencia / de kilómetros / de gimnasio» (imagen del podio), «No pudimos cargar el ranking.», «…en el ranking de este mes» | `screens/ComunidadScreen.tsx:552`, `features/ranking/utils/podioParaCompartir.ts:66-69`, `features/ranking/hooks/useRanking.ts:25`, `features/mentor/screens/MiCelulaScreen.tsx:372-373` | «Tabla» / «Tabla general», «Tabla de coherencia»… (o «Posiciones») |
| «Stickers Renaser», «Cerrar stickers», «Enviar sticker: …», «Sticker», «Sticker no disponible», «Sticker Renaser» | `features/chat/components/SelectorDeStickers.tsx:38,55`, `features/chat/api/chatMappers.ts:290,325`, `features/chat/utils/citaDelMensaje.ts:71`, `features/chat/components/BurbujaDeMensaje.tsx:211`, `features/chat/utils/stickersRenaser.ts:5` | «Figuritas Renaser», «Figurita» |
| «No enviaste tu checklist» (nombre de un sticker) | `features/chat/data/stickersRenaser.ts:9` | «No enviaste tu lista» (la imagen dice lo que dice: revisar si el dibujo tiene texto) |
| «Tickets de mentoría», «No hay tickets de mentoría.», «Ver más tickets», «No se pudieron cargar tus tickets.», «…la bandeja de tickets.», «Ver todos los tickets de mentoría es de…» | `features/tickets/screens/BandejaTicketsScreen.tsx:68,94,123,225`, `features/tickets/hooks/useTicketsMentor.ts:20`, `useBandejaDeTickets.ts:56` | «Consultas de mentoría» (ya se llaman «Consultas de mentores» en «Mis mentores») |
| «Mi onboarding» (fila de Yo → Ajustes) y «Vuelve a Mi onboarding…» | `features/yo/utils/navegacionDeYo.ts:56`, `screens/YoScreen.tsx:1188,1515` | «Mi ingreso» está tomado por pagos (spec 032): «Mis documentos» o «Mi Pacto y mis términos» |
| «· auto» (km del ranking leídos por el teléfono) | `features/ranking/utils/tablasDeRanking.ts:48` | «· del teléfono» o «· automático» |
| «chat» / «chats» («Chat de soporte», «Info. del chat», «Volver a la lista de chats»…) | `features/chat/**` | **Se puede dejar**: «chat» está en el diccionario de la RAE. Si se quiere, «conversación». Va como pregunta dentro de D8. |
| Nombre interno «Sparkie» | solo en carpetas y comentarios (`src/features/sparkie/`, `SparkieOverlay`) | **No se ve**: la persona lee «SER». Renombrar la carpeta es opcional (técnico). |

Palabras revisadas que **no** aparecen en lo que se ve: «check», «tracking», «streak», «feed», «feedback», «online», «login», «email», «post» (la app dice «publicación»), «tips», «coach».
