# Spec 055 — Rediseño del rol de mentor (fase 1)

**Rama**: `055-rediseno-del-mentor` (backend y app)

**Creada**: 2026-11-17

**Estado**: Borrador

**Input**: pedido del dueño (2026-11-17) tras revisar el rol en el emulador. Decisiones del grilling del 17/11 (P1–P8) y bocetos aprobados 0, A, B, B2, G y H en https://claude.ai/artifact/1NGKi8rKZxxm6BmRmU3GRz. Mentor y Guía de aprendiz son el mismo rol para esta spec.

## Por qué

Visto en el emulador el 17/11 con la app de master:

- «Mi grupo» abre con una lista de 40 avisos (dos o más por aprendiz, con cifras viejas) y el semáforo, lo que el mentor necesita para decidir, queda unas 5 pantallas más abajo.
- La ficha del aprendiz ocupa 9 pantallas seguidas; «Escribirle», la única acción del mentor, recién aparece en la tercera.
- Los hábitos se ven como una lista larga de horarios, sin saber cuáles cumplió ni con qué evidencia, y solo de la semana en curso.
- El mentor no tiene dónde anotar lo que habló con cada aprendiz.

La fase 1 ordena el recorrido del mentor (Mis grupos → Mi grupo → ficha) para que en una pantalla vea quién necesita ayuda, qué cumplió cada uno y pueda actuar en un toque.

## Aclaraciones

### Sesión 2026-11-17

- P: ¿Dónde leen las notas privadas el líder y Administración, y pueden escribir? → R: Solo leen, desde la ficha del aprendiz que ya abren en sus paneles, con el mismo lápiz; escribe solo el mentor que lo acompaña hoy.
- P: Con un solo grupo, ¿se va directo al grupo o pasa por «Mis grupos»? → R: Directo al grupo; «Mis grupos» aparece con dos o más.
- P: Al abrir la ficha, ¿qué pilar va desplegado? → R: El primer pilar con hábitos pendientes o vencidos; los demás cerrados.
- P: Dentro de un mismo color, ¿en qué orden salen los aprendices? → R: Del que menos cumplió en la semana al que más; a igualdad, por nombre.
- P: Si se elimina la cuenta de un mentor, ¿qué pasa con las notas que escribió? → R: Se quedan, con el autor «Cuenta eliminada»; las siguen viendo el mentor vigente, el liderazgo y Administración.
- P: Si se elimina la cuenta de un aprendiz, ¿qué pasa con las notas sobre él? → R: Se borran con su cuenta, con su historial.

## Escenarios de uso y pruebas *(obligatorio)*

### Historia 1 — Ver quién necesita ayuda en mi grupo (Prioridad: P1)

El mentor entra a «Mis grupos», elige un grupo y ve a todos sus aprendices ordenados del más urgente al que no tiene datos, cada uno con el color de su semáforo en el borde de su foto y el motivo en una línea. Puede escribirle a cualquiera en un toque.

**Por qué esta prioridad**: es lo primero que hace el mentor cada día y hoy lo urgente queda escondido.

**Prueba independiente**: con un mentor de prueba con un grupo de 9 aprendices de colores distintos, abrir el grupo y comprobar el orden, los colores, el motivo y que el ícono de chat abre la conversación con esa persona.

**Escenarios de aceptación**:

1. **Dado** un mentor con tres grupos activos, **cuando** abre «Mis grupos», **entonces** ve los tres con su nombre y, a la derecha, la cantidad de aprendices, y al tocar uno entra a ese grupo.
2. **Dado** un mentor con un solo grupo activo, **cuando** abre su entrada desde Yo, **entonces** entra directo a ese grupo, sin pasar por la lista.
3. **Dado** un grupo con aprendices en rojo, ámbar, verde y sin datos, **cuando** el mentor lo abre, **entonces** los ve en ese orden; el borde de cada foto lleva el color de su semáforo (gris suave si no tiene datos) y el nombre va en texto normal.
4. **Dado** un aprendiz con un aviso vigente («no registra actividad hace 12 días»), **cuando** el mentor ve su fila, **entonces** la línea gris dice ese motivo; sin aviso vigente, dice su estado («Cumplió 4 de 7 días», «Al día», «Todavía sin datos»).
5. **Dado** cualquier fila, **cuando** el mentor toca el ícono de chat, **entonces** se abre la conversación 1 a 1 con ese aprendiz (o se crea si no existía), sin enviar nada.
6. **Dado** el grupo abierto, **cuando** el mentor mira la pantalla, **entonces** no hay lista de avisos arriba, ni conteos por color, ni insignia «MENTOR», y abajo hay un enlace a «Mi evaluación».

---

### Historia 2 — Ver qué cumplió un aprendiz, día por día (Prioridad: P1)

El mentor abre la ficha de un aprendiz y en una pantalla ve su semana como 7 círculos de color. Toca un día y ve los hábitos de ese día agrupados por pilar, con lo cumplido, lo vencido y lo pendiente. Puede desplegar el mes y elegir cualquier fecha desde el día 1 del programa.

**Por qué esta prioridad**: es la información que el mentor necesita para acompañar y hoy está repartida en varias pantallas.

**Prueba independiente**: abrir la ficha de un aprendiz con datos de varias semanas, tocar días de esta semana y de semanas anteriores (desde el mes desplegado) y comprobar los hábitos y sus estados contra lo registrado.

**Escenarios de aceptación**:

1. **Dado** un aprendiz en el día 78, **cuando** el mentor abre su ficha, **entonces** arriba ve su foto con el borde de su color, su nombre, «Día 78 · <motivo>», el ícono de nota y el ícono de chat, y debajo la semana en 7 círculos con el color de cada día, con hoy elegido.
2. **Dado** la ficha abierta, **cuando** el mentor toca un día, **entonces** abajo se muestran los hábitos de ese día agrupados por pilar (Cuerpo, Mente, Espíritu, Emociones), cada pilar con su avance «N de M», y la ficha no se va a otra pantalla.
3. **Dado** un pilar, **cuando** el mentor lo toca, **entonces** se abre o se cierra; cerrado muestra su avance en puntitos y «N de M».
4. **Dado** un hábito, **entonces** se ve con su estado: cumplido (con la hora de su entrega, en la zona del aprendiz, si la hubo), vencido o pendiente.
5. **Dado** la ficha, **cuando** el mentor toca «<Mes> ⌄», **entonces** se despliega el mes en el mismo lugar, con el color de cada día, y puede pasar a meses anteriores hasta el del día 1 del programa; al elegir un día, los hábitos cambian a ese día; «Ver solo la semana» lo vuelve a plegar.
6. **Dado** que el mentor elige una fecha, **cuando** se cargan los hábitos, **entonces** se carga su semana y se adelanta la semana anterior; volver a una semana ya vista en la misma ficha no la vuelve a pedir.
7. **Dado** un día anterior al día 1 del programa o posterior a hoy, **entonces** se ve sin color y no se puede elegir.
8. **Dado** el final de la ficha, **entonces** hay accesos a Código Renaser, Diagnóstico, 80/20 de SER y Tiempo libre, que abren lo que ya existe hoy.

---

### Historia 3 — Ver la evidencia de lo que cumplió (Prioridad: P2)

Junto al estado de un hábito cumplido con evidencia hay un ojo. Al tocarlo se abre una hoja desde abajo con la evidencia.

**Por qué esta prioridad**: confirma el cumplimiento sin salir de la ficha.

**Prueba independiente**: con un aprendiz que subió foto, audio y texto a distintos hábitos, tocar el ojo de cada uno y comprobar el contenido, la hora y el paso al hábito siguiente.

**Escenarios de aceptación**:

1. **Dado** un hábito cumplido con evidencia, **entonces** se ve un ojo a la izquierda, junto al estado; sin evidencia, no hay ojo ni hueco.
2. **Dado** el ojo, **cuando** el mentor lo toca, **entonces** se abre una hoja desde abajo con la foto, el audio, el video o el texto, la hora de entrega, el pilar y lo que escribió el aprendiz; si hay varias evidencias, «1 de N».
3. **Dado** la hoja abierta, **cuando** el mentor toca «anterior» o «siguiente», **entonces** pasa a la evidencia del hábito anterior o siguiente de ese día, sin cerrar la hoja.
4. **Dado** una evidencia publicada en el Muro, **entonces** la hoja ofrece abrir esa publicación.
5. **Dado** la hoja, **cuando** el mentor desliza hacia abajo, usa el gesto de volver del sistema o toca cerrar, **entonces** se cierra y queda en la ficha, en el mismo día.

---

### Historia 4 — Anotar lo que hablé con un aprendiz (Prioridad: P2)

El ícono de lápiz junto al chat abre una hoja para escribir una nota privada sobre el aprendiz y ver las notas anteriores.

**Por qué esta prioridad**: el acompañamiento se pierde si el mentor no recuerda lo que habló, y al rotar el mentor nuevo empieza de cero.

**Prueba independiente**: escribir una nota, corregirla el mismo día, intentar corregirla al día siguiente, y comprobar quién la ve (mentor, líder, Administración, mentor nuevo) y quién no (el aprendiz).

**Escenarios de aceptación**:

1. **Dado** un aprendiz sin notas, **entonces** el lápiz no lleva puntito; con al menos una nota, lleva un puntito.
2. **Dado** el lápiz, **cuando** el mentor lo toca, **entonces** se abre una hoja desde abajo con un campo de texto, la aclaración «Solo la ven tú, tu líder y Administración» y, debajo, las notas anteriores con su fecha y su autor, de la más nueva a la más vieja.
3. **Dado** una nota escrita, **cuando** el mentor toca «Guardar nota», **entonces** queda guardada con su fecha y su autor y aparece arriba de las anteriores.
4. **Dado** una nota propia escrita hoy, **entonces** el mentor puede corregirla; de días anteriores, o escrita por otra persona, solo puede leerla. Ninguna nota se borra.
5. **Dado** el líder del mentor o Administración, **cuando** abren la ficha del aprendiz en sus paneles, **entonces** ven el lápiz y leen las notas, sin poder escribir ni corregir.
6. **Dado** que el aprendiz cambia de mentor, **cuando** el mentor nuevo abre la ficha, **entonces** ve las notas del mentor anterior (con su nombre) y no puede corregirlas.
7. **Dado** el aprendiz, **entonces** nunca ve las notas en ninguna parte de la app.
8. **Dado** una nota vacía o solo con espacios, **entonces** no se puede guardar y se dice qué falta.

---

### Casos límite

- Mentor sin grupos activos: «Mis grupos» dice que todavía no acompaña ningún grupo, sin huecos en blanco.
- Grupo sin aprendices: el grupo lo dice en una línea; no hay lista vacía.
- Aprendiz que todavía no empezó su programa (Día 0) o lo tiene suspendido: la ficha lo dice arriba y la semana se ve sin color.
- Aprendiz que dejó el grupo mientras el mentor tenía su ficha abierta, o mentor que rotó: la siguiente lectura responde que ya no lo acompaña y la app vuelve a «Mis grupos» con el motivo, sin datos viejos.
- Día con hábitos sin horario: se muestran sin hora.
- Evidencia que no se puede abrir (archivo borrado o enlace vencido): la hoja dice que no se pudo cargar y ofrece reintentar.
- Sin internet: lo ya cargado de la ficha se sigue viendo; elegir una semana no cargada dice que hace falta conexión. Guardar una nota sin internet no la pierde: avisa que no se guardó y conserva el texto.
- Toques repetidos en «Guardar nota» o en el chat: un toque, un pedido.
- Aprendiz en otra zona horaria que el mentor: los días y las horas son los del aprendiz.

## Requisitos *(obligatorio)*

### Requisitos funcionales

**Mis grupos**

- **FR-001**: El mentor tiene una pantalla «Mis grupos» con todos los grupos que acompaña hoy; cada uno muestra su nombre y, a la derecha, la cantidad de aprendices activos.
- **FR-002**: Si el mentor acompaña un solo grupo, su entrada lo lleva directo a ese grupo.
- **FR-003**: Desde Yo (y desde Comunidad → Tribu, donde hoy está) se entra a «Mis grupos».

**Mi grupo**

- **FR-004**: «Mi grupo» muestra a todos los aprendices del grupo, ordenados por el color de su semáforo de la semana: primero rojo, luego ámbar, luego verde y al final los que no tienen datos; dentro de cada color, del que menos cumplió al que más, y a igualdad, por nombre.
- **FR-005**: El color del semáforo de cada aprendiz se muestra en el borde de su foto o iniciales (rojo, ámbar, verde, gris suave sin datos); el nombre va en el color de texto normal.
- **FR-006**: Debajo del nombre, una sola línea gris con el motivo: el aviso de acompañamiento vigente más reciente de ese aprendiz si lo hay; si no, su estado de la semana («Cumplió N de 7 días», «Al día» o «Todavía sin datos»).
- **FR-007**: Cada fila tiene un ícono de chat que abre (o crea) la conversación 1 a 1 con ese aprendiz sin enviar nada; tocar el resto de la fila abre su ficha.
- **FR-008**: «Mi grupo» ya no muestra arriba la lista de avisos, ni los conteos por color, ni la insignia del rol; al pie tiene un enlace a «Mi evaluación» con su porcentaje.
- **FR-009**: Al abrir la ficha de un aprendiz, sus avisos de acompañamiento sin leer quedan como leídos.

**Ficha del aprendiz**

- **FR-010**: Arriba de la ficha: la foto o iniciales con el borde de su color, el nombre, «Día N · <motivo>», el ícono de nota (con un puntito si existe al menos una nota) y el ícono de chat.
- **FR-011**: Debajo, la semana del aprendiz (sábado a viernes, la misma del semáforo) en 7 círculos con el color del semáforo de cada día; hoy aparece elegido al abrir.
- **FR-012**: El color de cada día es el estado diario que ya calcula el semáforo (verde, ámbar, rojo); los días sin obligaciones, anteriores al día 1 del programa o futuros, van sin color. No se crea una regla de color nueva.
- **FR-013**: Al elegir un día se muestran sus hábitos agrupados por pilar (Cuerpo, Mente, Espíritu, Emociones), en secciones que se abren y se cierran; cada sección muestra su avance «N de M» y, cerrada, puntitos por hábito. Al abrir, el primer pilar con hábitos pendientes o vencidos va abierto y los demás cerrados.
- **FR-014**: Cada hábito muestra su estado del día: cumplido (con la hora de su entrega, en la zona del aprendiz, si la hubo), vencido o pendiente. La ventana horaria del pendiente queda fuera de la fase 1 (el servidor no la guarda en el día).
- **FR-015**: «<Mes> ⌄» despliega el mes en el mismo lugar de la semana, con el color de cada día; se puede ir a meses anteriores hasta el del día 1 del programa, y no a meses futuros. Elegir un día del mes cambia los hábitos a ese día; «Ver solo la semana» lo pliega.
- **FR-016**: Al elegir una fecha se cargan los hábitos de su semana y, además, se adelanta la carga de la semana anterior; lo ya cargado se reutiliza mientras la ficha esté abierta.
- **FR-017**: El mentor ve los días desde el día 1 del programa del aprendiz mientras lo acompañe hoy; si ya no lo acompaña, no ve ningún día.
- **FR-018**: Al final de la ficha hay accesos a Código Renaser, Diagnóstico, 80/20 de SER y Tiempo libre, que abren las vistas que ya existen; un acceso apagado por configuración (por ejemplo Código Renaser) no se muestra.

**Evidencia**

- **FR-019**: Un hábito cumplido con evidencia muestra un ojo a la izquierda, junto al estado; sin evidencia no hay ojo.
- **FR-020**: El ojo abre una hoja desde abajo con la evidencia (foto, audio, video o texto), la hora de entrega, el pilar, el texto del aprendiz y «1 de N» si hay varias.
- **FR-021**: Desde la hoja se pasa a la evidencia del hábito anterior o siguiente de ese mismo día sin cerrarla; si la evidencia se publicó en el Muro, la hoja ofrece abrir la publicación.
- **FR-022**: La hoja se cierra deslizando hacia abajo, con cerrar o con el gesto de volver del sistema, y deja la ficha en el mismo día y pilares.

**Nota privada**

- **FR-023**: El mentor que acompaña hoy a un aprendiz puede escribir notas privadas sobre él; cada nota guarda su texto, su autor y su fecha y hora.
- **FR-024**: Leen las notas de un aprendiz: el mentor que lo acompaña hoy (todas, también las de mentores anteriores), el liderazgo de su formación (MENTOR_LEAD con esa formación en su alcance) y Administración. El liderazgo y Administración solo leen, desde la ficha del aprendiz que ya abren en sus paneles y con el mismo ícono de lápiz; no escriben. El aprendiz y cualquier otro rol nunca las leen.
- **FR-025**: El autor puede corregir su nota solo el mismo día en que la escribió (día del autor); después, y para cualquier otra persona, es solo lectura. Ninguna nota se borra, salvo con la baja de la cuenta del aprendiz (se van con sus datos); si se da de baja la cuenta del autor, sus notas se quedan con el autor «Cuenta eliminada». Una corrección conserva el texto anterior en el historial.
- **FR-026**: Una nota no puede estar vacía ni pasar de 2000 caracteres; el campo lo dice antes de guardar.
- **FR-027**: La hoja de notas muestra la aclaración de quién la ve, el campo de texto y, debajo, las notas anteriores de la más nueva a la más vieja, con fecha y autor.

**Comunes**

- **FR-028**: Todo lo que lee o escribe el mentor en esta spec lo autoriza el servidor con la relación de acompañamiento vigente (y el líder o Administración en las notas); quien no corresponde recibe una negativa con su motivo y la app lo muestra.
- **FR-029**: Todos los textos nuevos en español de Perú, con tuteo y en tipo oración; toques de al menos 48 de alto; las hojas desde abajo respetan el gesto de volver del sistema.

### Entidades clave

- **Nota privada del mentor**: texto escrito sobre un aprendiz. Tiene autor, aprendiz, fecha y hora de creación, y el historial de sus correcciones. No pertenece a un grupo: sigue al aprendiz aunque cambie de mentor.
- **Día del aprendiz** (ya existe): fecha en su zona, color del semáforo diario y hábitos con su estado y evidencia.
- **Acompañamiento** (ya existe): relación vigente entre mentor y grupo; decide qué puede ver el mentor.

## Criterios de éxito *(obligatorio)*

### Resultados medibles

- **SC-001**: Al abrir «Mi grupo», el mentor ve en la primera pantalla, sin desplazarse, a los aprendices en rojo de un grupo de hasta 6 en rojo (en un teléfono de 1080 × 2400).
- **SC-002**: Escribirle a un aprendiz desde «Mi grupo» toma 1 toque (hoy, 4 pasos).
- **SC-003**: En la ficha, el mentor ve la semana, el día y los hábitos del primer pilar en la primera pantalla, sin desplazarse.
- **SC-004**: Volver a una semana ya vista en la misma ficha se muestra al instante, sin esperar la red; elegir la semana anterior a la vista se muestra al instante porque ya estaba adelantada.
- **SC-005**: Ver la evidencia de un hábito toma 1 toque desde la ficha y no la saca del día elegido.
- **SC-006**: Ningún aprendiz puede leer una nota escrita sobre él, y un mentor que ya no lo acompaña no puede leer ni escribir notas suyas (verificado por rol).
- **SC-007**: Los avisos sin leer de un aprendiz quedan en 0 después de que el mentor abre su ficha.

## Lo que no cubre (fase 2 y aparte)

- Fase 2: objetivos en la ficha (objetivo de 90 días y rocas de la fase), «Mi evaluación» con «Qué sigue» por reglas fijas, el historial de grupos anteriores como resumen de solo lectura y el aviso de rojo del mismo día (una vez al día, 19:00 en la hora del mentor, como mucho uno por aprendiz por semana, silenciable).
- El arreglo de los avisos acumulados (issue #165 del backend) va por el camino corto, antes de esta fase; FR-006 da por hecho que hay un solo aviso vigente por aprendiz y motivo.
- La pastilla «SER» que tapa contenido (issue #224 de la app) va aparte.
- No cambia qué datos calcula el semáforo ni sus reglas de color.

## Supuestos

- Se reutiliza lo que ya existe: la semana del aprendiz por fecha, el semáforo diario y del grupo, el enlace firmado de la evidencia, la conversación 1 a 1, las vistas de Código Renaser, Diagnóstico, 80/20 y Tiempo libre, y los componentes de hoja desde abajo de la app. Lo nuevo de servidor es solo la nota privada.
- «El mismo día» de una nota es el día del autor (su zona), porque es el autor quien corrige.
- El límite de 2000 caracteres alcanza para una nota de seguimiento; se puede ajustar sin cambiar la spec.
- Cambia el tab Yo (la fila de entrada) y la vista de Comunidad → Tribu del mentor: es un cambio pedido y aprobado por el dueño el 17/11 con los bocetos (se anota en `docs/decisiones/` de la app).
