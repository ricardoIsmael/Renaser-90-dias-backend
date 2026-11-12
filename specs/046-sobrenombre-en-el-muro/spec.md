# Feature Specification: Sobrenombre en el Muro

**Feature Branch**: `045-recorrido-y-046-sobrenombre` (solo la spec; la implementación irá en `046-sobrenombre-en-el-muro`, backend y app)
**Created**: 2026-11-06
**Status**: Aprobada por el dueño (2026-11-06) con las decisiones de abajo. En implementación (D-331).
**Input**: Pedido del dueño (2026-11-06): el **día 7** la persona elige un **sobrenombre o apodo** («no es nombre») que **reemplaza su nombre en el Muro**; Administración puede **configurarlo o editarlo**.

**Vista previa**: la misma de la spec 045 (`specs/045-recorrido-de-bienvenida/vista-previa/index.html`), pantallas «Elegir sobrenombre», «El Muro con sobrenombre» y «Administración».


## Decisiones del dueño (2026-11-06)

| Pregunta | Decisión |
|---|---|
| D1 · Dónde se elige | **A**: una fila en Hoy desde el día 7 y en Yo → Ajustes → Perfil, sin obligar. |
| D2 · «Ahora no» | **B: la fila no vuelve**; después se elige solo desde Yo. |
| D3 · Dónde reemplaza | **A**: en todo el Muro (publicaciones, comentarios y «quién reaccionó»). Ranking, chats y Tribu siguen con el nombre real. |
| D4 · La foto | **A**: se ve su foto. |
| D5 · Cambiarlo | **A**: una vez cada 30 días (Administración, cuando quiera). |
| D6 · Único | **A**: dentro de la formación. |
| D7 · Aviso | **A**: si Administración lo cambia, se le avisa a la persona, sin decir el motivo. |
| D8 · Obligatorio | No. |
| D9 · El personal | **B: el personal también puede tener sobrenombre.** |

**Autorización de los cinco tabs**: concedida el 06/11 para lo listado en «Autorización de los cinco tabs».

**Ajustes de la implementación** (sin cambiar lo decidido):
- La fila de Hoy sale **desde** el día 7 (no solo ese día) hasta que elija uno o diga «Ahora no»: así nadie se la pierde por no abrir la app ese día.
- El nombre real lo ve **todo el equipo** (mentor, guía, líder, Administración, Alquimista y Soporte TI), no solo el mentor de su grupo:
  antes del sobrenombre ya veían el nombre de todos en el Muro.
- Rutas: `GET/PUT/DELETE /api/v1/me/sobrenombre`, `POST /api/v1/me/sobrenombre/descartar`; Administración (`MODERATE_WALL`):
  `GET /api/v1/admin/sobrenombres?q=`, `GET/PUT/DELETE /api/v1/admin/personas/{id}/sobrenombre` (con motivo) y
  `GET/POST/DELETE /api/v1/admin/sobrenombres/palabras-prohibidas`. Errores con código `SOBRENOMBRE_LARGO`, `_CARACTERES`, `_PROHIBIDO` (400),
  `_REPETIDO` y `_ESPERA` (409). El Muro manda `authorRealName` (publicaciones y comentarios) y `realName` (reacciones), solo al equipo.
- Tablas `sobrenombres`, `sobrenombres_cambios` y `palabras_prohibidas_sobrenombre` (V181).

---

## Contexto: qué pasa hoy el día 7

- El día 7 es el **último día de la Fase 1** (días 1–7). El día 8 empieza la Fase 2 (celebración «¡Entraste en la Fase 2!») y se abre **Mi Radar** (spec 012/019).
- Los días 1 a 7 el **Código Renaser** es obligatorio cada hora (08:00–19:00): el día 7 la persona ya lo tiene muy visto.
- El **Mapa de Renacimiento**, que antes era el momento del día 7, **se retiró** (spec 026): hoy el día 7 no tiene nada propio. El sobrenombre ocupa ese lugar.
- En el Muro el nombre del autor lo manda **el servidor** (`authorName` en `GET /api/v1/wall` y en cada comentario); la app solo lo dibuja (`wallMappers`: sin nombre, «Miembro Renaser»). **No existe** ningún campo de sobrenombre en `usuarios`.

## Qué cambia (resumen)

El día 7, en Hoy, una tarjeta invita a elegir **cómo te verán en el Muro**. La persona escribe un sobrenombre, ve cómo queda en una publicación de ejemplo y lo guarda. Desde entonces, **en el Muro** sus publicaciones y comentarios dicen su sobrenombre. Su nombre real lo siguen viendo su mentor, su líder y Administración. Administración puede verlo, cambiarlo o quitarlo desde la ficha de la persona.

**Lo decide el servidor**: el `authorName` del Muro pasa a ser el sobrenombre cuando existe, así que **un APK anterior ya lo muestra** sin cambios.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - El día 7 elijo mi sobrenombre (Priority: P1)

**Acceptance Scenarios**:

1. **Given** una persona en su día 7 sin sobrenombre, **When** abre Hoy, **Then** ve una fila compacta bajo la frase del día: «Elige cómo te verán en el Muro · Tu sobrenombre ›» (la misma `FilaCompacta` de «Haz tu programa…»). No es obligatoria ni tapa nada.
2. **When** la toca, **Then** se abre una hoja desde abajo «Tu sobrenombre en el Muro» con: un campo («Ej.: Fénix de Arequipa»), el contador «12 / 20», una **vista previa** de una publicación del Muro con ese sobrenombre, la línea «Tu mentor y el equipo siguen viendo tu nombre.» y «Guardar».
3. **Given** un sobrenombre válido, **When** toca «Guardar», **Then** el servidor lo guarda, la hoja dice «Listo: en el Muro te verán como Fénix de Arequipa.» con `tacto.logro()` y la fila desaparece de Hoy.
4. **Given** uno inválido, **Then** el campo se marca y dice por qué, en tuteo («Usa de 3 a 20 letras.», «Ese sobrenombre ya lo tiene otra persona.», «Elige otro: ese no se puede usar.»), sin diálogos.
5. **Given** «Ahora no» (la ✕ de la fila), **Then** la fila no vuelve ese día; ver D2 para los días siguientes.
6. **Después del día 7** sin sobrenombre: se puede elegir siempre desde Yo → Ajustes → Perfil → «Sobrenombre en el Muro».

### User Story 2 - En el Muro me ven con mi sobrenombre (Priority: P1)

1. **Given** una persona con sobrenombre, **Then** en el Muro sus publicaciones, sus comentarios y la lista «quién reaccionó» dicen el sobrenombre (alcance según D3).
2. **Given** su foto de perfil, **Then** se sigue viendo (ver D4).
3. **Given** que comparte una publicación a un chat, **Then** la tarjeta compartida dice «De Fénix de Arequipa» (es contenido del Muro).
4. **Los chats, el ranking, las fichas, Tribu y los eventos** siguen con el nombre real (salvo lo que diga D3).

### User Story 3 - Quien acompaña sigue viendo el nombre real (Priority: P1)

1. **Given** el mentor o la guía de su grupo, el líder de mentores, Administración, Alquimista o Soporte TI, **When** miran el Muro, **Then** ven el sobrenombre y, debajo y en gris, el nombre real («Fénix de Arequipa · Ana Pérez»). El resto de las personas solo el sobrenombre.
2. **Given** la moderación del Muro (ocultar / eliminar, solo ADMIN y ALCHEMIST), **Then** la lista de «Publicaciones ocultas» muestra los dos.
3. Las fichas (mentor, líder, Administración) muestran «Sobrenombre en el Muro: Fénix de Arequipa».

### User Story 4 - Cambiarlo después (Priority: P2)

1. **Given** una persona con sobrenombre, **When** va a Yo → Ajustes → Perfil → «Sobrenombre en el Muro», **Then** puede cambiarlo o quitarlo (vuelve su nombre), con el límite de D5.
2. El cambio rige al instante y **en todas sus publicaciones, también las viejas** (el servidor lo resuelve al leer; no se guarda copiado en cada publicación).

### User Story 5 - Administración lo configura o edita (Priority: P2)

1. **Ficha de la persona** (Administración → Personas → la persona): sección «Sobrenombre en el Muro» con el actual (o «Sin sobrenombre»), «Cambiar» y «Quitar». Cambiarlo pide un motivo corto (3–200) y deja constancia (quién, cuándo, antes → después). Si se le avisa a la persona lo decide D7.
2. **Administración → Más opciones → «Sobrenombres»**: lista de quienes tienen sobrenombre (sobrenombre, nombre real, formación, desde cuándo), buscador por cualquiera de los dos, y **«Palabras que no se pueden usar»** (agregar y quitar; la lista de base viene con el sistema).
3. Solo ADMIN y ALCHEMIST (el servidor lo vuelve a autorizar).

### Edge Cases

- **Sobrenombre igual a un nombre real** de otra persona del Muro: se rechaza como repetido (evita suplantar).
- **Nombre real dentro del sobrenombre** («Ana Fénix»): se permite.
- **Cuenta eliminada** (D-243): el sobrenombre se borra con la cuenta y queda libre.
- **Cambio de formación** (spec 022): se conserva; la unicidad se vuelve a comprobar en la nueva (D6).
- **Emojis**: no se permiten (cambian de forma entre teléfonos y rompen la búsqueda).
- **«Ver como»** (Soporte TI): no se puede elegir ni cambiar.
- **Backend anterior**: la fila de Hoy y las secciones no aparecen (404); todo como hoy.

---

## Validaciones (propuestas)

| Regla | Valor propuesto |
|---|---|
| Largo | de 3 a 20 caracteres, sin espacios al principio ni al final y sin dos espacios seguidos |
| Caracteres | letras (con tildes y ñ), números, espacio, punto y guion; sin emojis ni otros símbolos |
| Único | sí, sin distinguir mayúsculas ni tildes («Fenix» = «Fénix»), en el alcance de D6; tampoco igual al nombre real de otra persona |
| Palabras prohibidas | lista de base (groserías, insultos, «admin», «renaser», «mentor», «soporte», «SER», nombres de roles) + las que agregue Administración; se compara sin tildes ni mayúsculas y también dentro de la palabra |
| Quién valida | el servidor (la app repite las reglas de largo y caracteres para avisar antes) |

## Requirements *(mandatory)*

- **FR-001**: La persona MUST poder elegir un sobrenombre desde su día 7, y en cualquier momento después desde Yo → Ajustes → Perfil.
- **FR-002**: El servidor MUST devolver el sobrenombre en lugar del nombre en `authorName` del Muro (publicaciones y comentarios) y en la lista de reacciones, según D3.
- **FR-003**: El mentor/guía del grupo, el líder, ADMIN, ALCHEMIST y Soporte TI MUST ver también el nombre real (campo nuevo `authorRealName`, solo para ellos).
- **FR-004**: Las validaciones de la tabla MUST cumplirse en el servidor.
- **FR-005**: ADMIN y ALCHEMIST MUST poder ver, cambiar y quitar el sobrenombre de cualquier persona, con motivo y constancia. Ningún otro rol MUST poder: el servicio responde 403 (D-349).
- **FR-006**: ADMIN y ALCHEMIST MUST poder editar la lista de palabras prohibidas. Ningún otro rol MUST poder: el servicio responde 403 (D-349).
- **FR-007**: Ningún texto de esta función MUST estar en inglés.

## Datos (borrador, sin plan detallado)

- `usuarios.sobrenombre` (texto, nulo) y `usuarios.sobrenombre_normalizado` (único según D6), `sobrenombre_desde`.
- `sobrenombres_cambios` (constancia: quién, cuándo, antes, después, motivo).
- `palabras_prohibidas_sobrenombre`.
- Rutas: `GET/PUT/DELETE /api/v1/me/sobrenombre`, `POST /api/v1/me/sobrenombre/validar`, `GET/PUT/DELETE /api/v1/admin/personas/{id}/sobrenombre`, `GET /api/v1/admin/sobrenombres`, `GET/POST/DELETE /api/v1/admin/sobrenombres/palabras-prohibidas`.

## Autorización de los cinco tabs

> Se pide al dueño autorización explícita para: (1) en **Hoy**, la fila compacta del día 7; (2) en **Comunidad → Muro**, dibujar el nombre real en gris solo para quien acompaña; (3) en **Yo → Ajustes → Perfil**, la fila «Sobrenombre en el Muro». Ninguna pestaña cambia de forma fuera de esto.

---

## [DECISIÓN DEL DUEÑO] (respondidas el 06/11: ver «Decisiones del dueño» arriba)

**D1. ¿Dónde se elige el día 7?**
- **A (recomendada)**: una fila compacta en Hoy ese día (no obligatoria) + siempre disponible en Yo → Perfil.
- B: una pantalla obligatoria el día 7, como el Código Renaser (no se usa la app hasta elegir).
- C: solo en Yo → Perfil, sin aviso.

**D2. Si dice «Ahora no» el día 7…**
- **A (recomendada)**: la fila vuelve a salir en Hoy los 3 días siguientes y después solo queda en Yo.
- B: no vuelve; solo Yo.

**D3. ¿Dónde reemplaza el sobrenombre al nombre?**
- **A (recomendada)**: en todo el **Muro**: publicaciones, comentarios y «quién reaccionó». Ranking, chats y Tribu con el nombre real.
- B: Muro **y Ranking**.
- C: solo en las publicaciones (los comentarios con nombre real).

**D4. ¿Y la foto de perfil en el Muro?**
- **A (recomendada)**: se sigue viendo la foto.
- B: con sobrenombre, en el Muro van las iniciales del sobrenombre (más anónimo).

**D5. ¿Cuántas veces se puede cambiar?**
- **A (recomendada)**: una vez cada 30 días (Administración puede cambiarlo cuando quiera).
- B: libre, sin límite.
- C: una sola vez; después, solo Administración.

**D6. ¿Único en qué alcance?**
- **A (recomendada)**: único **dentro de la formación** (el Muro de cada formación está aislado, spec 024).
- B: único en toda la app.
- C: no tiene que ser único.

**D7. Si Administración lo cambia, ¿se le avisa a la persona?**
- **A (recomendada)**: sí, con un aviso corto en la app («Administración cambió tu sobrenombre en el Muro.»), sin el motivo.
- B: no se avisa.

**D8. ¿Es obligatorio tener sobrenombre?**
- **A (recomendada)**: no; sin sobrenombre, el Muro sigue con su nombre como hoy.
- B: sí, desde el día 7 todo el Muro con sobrenombre.

**D9. ¿El personal (mentor, líder, admin) también puede tener sobrenombre?**
- **A (recomendada)**: no; en el Muro el equipo se ve con su nombre real (da confianza).
- B: sí, igual que los aprendices.
