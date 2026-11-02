# Feature Specification: Identidad por formación (bienvenida e imagen de cada formación)

**Feature Branch**: `035-identidad-por-formacion` (backend y app)

**Created**: 2026-11-02

**Status**: Implementada (2026-11-02; sin preguntas abiertas: lo no dicho por el dueño está en *Supuestos*, para que lo revise)

**Input**: Pedido del dueño (2026-11-02), textual: «procede con la spec; revisa siempre eso en todas las partes, no quiero errores en producción».
Parte de dos huecos confirmados en el código, después de que la 024 dejara listos los **nombres** de los chats por formación
(«Diplomado Renaser Global», «Pedro – Diplomado Renaser»; la Escuela conserva «Formación Renaser Global»).

Se apoya en la **022** (formaciones fijas y `AlcanceDeFormacionFinder`) y en la **024** (cada conversación tiene su formación). No repite lo que ellas definen.

## Contexto: los dos huecos

1. **La bienvenida es la misma para las tres.** `BienvenidaParaAdministrar` y `BienvenidaEnGrupoService` no distinguen la formación: la portada de la
   tarjeta y los tres mensajes (el que acompaña la tarjeta, el formal y el del grupo) son los de la Escuela para todos. Una persona del Diplomado recibe
   «Hoy empiezas tu Formación Renaser».
2. **La imagen de los chats es la misma para las tres.** El fénix de la comunidad y la tarjeta de los grupos (`tarjeta-renaser.jpg`) son assets de la app,
   iguales en las tres; la tarjeta con nombre del soporte se dibuja siempre sobre la portada de la Escuela.

## User Scenarios & Testing

### US1 — Cada formación tiene su bienvenida (P1)

Una persona nueva del **Diplomado** recibe, al nacer su soporte, la tarjeta con su nombre sobre **la portada del Diplomado** y los dos mensajes del
**Diplomado**; al entrar a su grupo, el mensaje del grupo **del Diplomado**. Lo mismo la Certificación. La Escuela recibe exactamente lo de hoy.

**Aceptación**:
1. Una persona del Diplomado recibe el texto del Diplomado y **nunca** el de la Escuela, aunque Administración haya editado el de la Escuela.
2. Lo que Administración cambia en la bienvenida del Diplomado no altera la de la Escuela ni la de la Certificación.
3. Si a una formación le falta un texto, usa el de la Escuela; nunca sale una bienvenida vacía (salvo que el texto esté vacío a propósito en el recurso, D-190).
4. Sin cambios, la Escuela es idéntica a hoy (mismos textos, misma portada, mismas rutas, mismos bytes).

### US2 — Cada formación tiene su imagen en el chat (P1)

La comunidad general y los grupos **sin foto propia** de una formación muestran **su imagen**; la tarjeta con nombre del soporte usa **la portada de su formación**.
Sin imagen propia, la Escuela queda como hoy (el fénix y la tarjeta de siempre, que son de la app) y el Diplomado y la Certificación usan lo de la Escuela.

**Aceptación**:
1. Una persona del Diplomado ve, en la lista, la cabecera y la info de su comunidad general, la imagen del Diplomado; nunca la de otra formación.
2. Un grupo con foto propia (D-212) conserva la suya: manda sobre la de la formación.
3. Sin ninguna imagen subida, el servidor no manda `photoPath` en la comunidad ni en los grupos y la app dibuja lo de siempre.
4. La foto de un integrante (soporte y grupos del Diplomado) es su tarjeta con nombre sobre la portada del Diplomado.

### US3 — Administración lo cambia por formación (P1)

- **Administración → Más opciones → Bienvenida** suma un selector de formación (Escuela · Diplomado · Certificación). Todo lo de siempre (vista previa,
  editor, «Volver al original», portada) vale para la formación elegida.
- **Administración → Formaciones → cada formación** suma «Imagen de la formación»: elegir, ver cómo queda en el chat, guardar y volver a la de siempre.
- Solo ADMIN y ALQUIMISTA con la cuenta activa, como hoy. Todo queda en la bitácora (quién y cuándo).

**Aceptación**: un MENTOR, un aprendiz o una cuenta suspendida reciben 403 en cada ruta nueva; un código de formación inexistente es 400/404; la app anterior
(sin `formation`) edita la Escuela sin notar nada.

### Casos límite

- Formación inactiva en el catálogo: su identidad se puede editar igual (la bienvenida no se manda si nadie entra).
- Una portada o imagen guardada que ya no abre en el almacenamiento: se dibuja sobre la de la Escuela o sobre el original, con un `WARN` (como hoy).
- El personal con varias formaciones ve la bienvenida y la imagen de **la formación de la conversación**, no la de la que tenga activa.
- El almacenamiento de marcador (local): los textos se cambian, la imagen no (409, como la portada de hoy).
- Cuenta de prueba de «ver como»: ve la identidad de la formación de su rol.

## Requirements

### Functional Requirements

- **FR-001**: La bitácora de la bienvenida (`cambios_bienvenida`) guarda la **formación** de cada cambio. Lo que ya existe es de la Escuela.
- **FR-002**: Cada formación tiene **sus tres mensajes**. El **original** de la Escuela es el de hoy (`bienvenida/mensajes.yaml`); el de las otras dos es el de su
  sección del mismo recurso (la Escuela con el nombre de la institución cambiado) y, si falta, el de la Escuela.
- **FR-003**: Lo vigente de un texto en una formación es su último cambio **de esa formación** o, si no hay (o volvió al original), el original de esa formación.
  **Un texto no hereda lo que Administración editó en otra formación.**
- **FR-004**: Cada formación tiene **su portada** (el fondo de la tarjeta). Sin portada propia, usa la **vigente de la Escuela** (la subida o la original).
- **FR-005**: Cada formación tiene **su imagen** (pieza nueva `IMAGEN_DE_LA_FORMACION`). Sin imagen propia, usa la de la Escuela; sin ninguna, no hay imagen
  de formación y los chats usan lo de la app.
- **FR-006**: La bienvenida del soporte usa la formación **de la conversación del soporte**; la del grupo, la **del chat del grupo**. Nunca se infiere del rol.
- **FR-007**: La tarjeta con nombre del soporte y la de cada integrante usan la portada de la formación **de la conversación** que se mira.
- **FR-008**: `GET /chat/conversations` y `GET /chat/conversations/{id}/foto` sirven la imagen de la formación para la **comunidad general** y para los
  **grupos sin foto propia**; la foto propia de un grupo manda. El acceso es el de siempre (404 si la conversación es de otra formación).
- **FR-009**: Rutas de Administración nuevas o ampliadas (solo ADMIN/ALCHEMIST activos): `formation` opcional en las de Bienvenida (ausente = Escuela) y
  `/admin/formaciones/{codigo}/imagen` (ver, subir, confirmar, volver, vista previa). Contrato en `contracts/api.md`.
- **FR-010**: Las imágenes subidas pasan las mismas reglas de la portada (JPEG o PNG, ≤ 5 MB, 600 a 8000 px) y se sirven como JPEG cuadrado de 512 px; las
  rutas viven bajo `formaciones/imagenes/` y ninguna otra ruta del almacenamiento se acepta.
- **FR-011**: **Aislamiento**: una persona nunca recibe la bienvenida ni ve la imagen de otra formación. Lo prueban unitarias e integración (par Escuela/Diplomado
  y Diplomado/Certificación).
- **FR-012**: Compatibilidad: sin la app nueva todo se comporta como hoy; los campos nuevos son aditivos; sin imagen subida no cambia ningún dato que salga hoy.
- **FR-013**: Los mensajes del programa («Formación Renaser» como remitente) llevan el nombre de **la formación de la conversación** en las que no son la Escuela.

### Key Entities

- **Cambio de bienvenida** (fila de `cambios_bienvenida`): pieza + **formación** + valor + quién y cuándo. Sin tabla nueva.
- **Pieza**: `SOPORTE_CON_LA_TARJETA`, `SOPORTE_FORMAL`, `GRUPO` (textos), `PORTADA`, `CARTA_CAJA` (solo Escuela), `IMAGEN_DE_LA_FORMACION` (nueva).

## Supuestos (los decidió el equipo; el dueño los revisa)

1. **Los textos de las otras dos formaciones arrancan con el original de su formación** (el de la Escuela con el nombre cambiado: «tu Diplomado Renaser», «al
   Diplomado Renaser»; «tu Certificación Renaser», «a la Certificación Renaser») y **no copian lo que Administración haya editado de la Escuela**. Cambiar el
   nombre de la formación en el catálogo no reescribe estos textos (se editan en Bienvenida).
2. **Portada e imagen sí se heredan de la Escuela en vivo**: si la Escuela sube una nueva, el Diplomado y la Certificación que no subieron la suya la siguen.
3. **Una sola imagen por formación** sirve para la comunidad general y para los grupos sin foto propia. Con imagen, la comunidad deja de mostrar el fénix y los
   grupos dejan de mostrar la tarjeta de la app; sin ella, todo como hoy.
4. La carta de la Caja Renaser (`CARTA_CAJA`) sigue siendo única (de la Escuela): la caja no es de una formación.
5. El interruptor de Soporte TI de la bienvenida (`admin.bienvenida`) y `BIENVENIDA_ACTIVA` valen para las tres formaciones a la vez.
6. La imagen se sirve recortada al centro en cuadrado a 512 px (como la foto de un grupo); si no es cuadrada, se pierde el borde.
7. La bienvenida del soporte que ya se mandó no se reescribe; un cambio rige desde la próxima persona.
8. La firma del programa en los mensajes de sistema usa el nombre de la formación en el Diplomado y la Certificación («Diplomado Renaser»); la Escuela sigue
   con «Formación Renaser».
