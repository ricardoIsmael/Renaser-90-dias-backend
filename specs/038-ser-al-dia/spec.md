# Feature Specification: SER al día con la lógica nueva (y para las tres formaciones)

**Feature Branch**: `038-ser-al-dia` (backend y app; app en `038-ser-al-dia` del frontend)
**Created**: 2026-11-02
**Status**: Implementada (pruebas unitarias corridas a mano; `mvnw clean verify`, `tsc` y Jest encolados). Falta el E2E en el emulador con SER (la IA real no se llamó).
**Input**: Pedido del dueño (2026-11-02), textual: «Lo de cumplir hábito, su lógica, y SER también puede hacerlo: ¿sabe de estos cambios? Ahora no pide foto, pero SER debe ayudar con lo que tenemos. Cada cambio debe ser de acuerdo a lo de ahora: no ayudará a Plan como antes, ya que no podrá, o saldrá error. Ten cuidado con esta parte.» Y antes: «el Mapa ya no es necesario, ahora es con diagnóstico o sin diagnóstico; de ahí salen los objetivos.» Ampliación del mismo día: «SER estará para las 3», es decir, Escuela, Diplomado y Certificación.

## Qué pasa hoy (verificado en el código, 2026-11-02)

SER (un solo prompt, `renasia-sistema.st`, más ~35 herramientas) se escribió cuando el programa era la Escuela de 90 días con Mapa de Renacimiento. Desde entonces la app cambió: se retiró el Mapa (spec 026), los hábitos y las categorías son por formación (023), Plan se rehízo (028, 031, 033), los rituales se escriben (31/10), «Última comida del día» lleva círculo, un día cerrado no se registra (D-259) y cumplir un hábito apaga sus alarmas (issue #33). SER no sabía nada de eso:

| # | Qué hacía SER | Por qué estaba mal |
|---|---|---|
| 1 | Mandaba a «completar su Mapa» (en el prompt, en `consultar_mi_mapa` y pegado a cada propuesta de Plan). | El servidor rechaza escribir el Mapa (409 `MAPA_RETIRADO`): un camino que no existe. |
| 2 | Decía «90 días» fijo, «Trabajo», «los hábitos están en Plan», «sube la evidencia desde Hoy», y nunca nombraba «Cierra tu día». | Duraciones 90 días / 3 meses / 1 año; el eje se llama Negocio; los hábitos y la evidencia están en **Training**; «Cierra tu día» está en **Plan**. |
| 3 | `consultar_rocas` (noventa) decía «no definió objetivos» sin distinguir «sin diagnóstico», «del diagnóstico, por confirmar» y «ya confirmado». | La persona puede tener objetivos del diagnóstico que confirma una vez en Plan. |
| 4 | Dejaba la tarjeta de «Plan del día» para un día que el servidor iba a rechazar. | Tras las 18:00 el día en curso ya no se arma; solo se planifica hasta el fin de la semana. Salía «No se pudo». |
| 5 | Pedía foto + «¿Qué sentiste?» en los tres rituales. | Los rituales se registran **escribiendo** «¿Cómo te sientes después de realizar el ritual?», sin cámara. |
| 6 | «Última comida del día» seguía con evidencia OBLIGATORIA (seed V4), así que pedía foto. | El dueño decidió que es opcional y se marca con el círculo. |
| 7 | Cuando SER marcaba un hábito por una propuesta, la app solo recargaba. | No apagaba las alarmas que le quedaban a ese día (issue #33), no celebraba, no refrescaba el semáforo. |
| 8 | Trataba un registro `EXPIRADO` como «igual puede hacerlo» y le dejaba botón. | `EXPIRADO` es un **día cerrado** (D-259): el servidor responde 409. |
| 9 | Solo conocía las cuatro categorías de la Escuela. | Un hábito del Diplomado o la Certificación salía «Sin dimension»; podía proponer crear un hábito en una categoría que su formación no tiene (el alta lo rechaza). |
| 10 | La memoria guardaba cualquier dato, el diagnóstico completo (Q8-B) llega al modelo y la política de privacidad no lo decía. | Ingresos, deudas y cuotas no deben guardarse; el uso hay que declararlo. |

## Clarifications

### Decisiones del dueño (ya tomadas)

- **El Mapa se retira** (spec 026): los objetivos nacen del diagnóstico («con diagnóstico o sin diagnóstico») o se escriben en Plan. SER nunca manda al Mapa.
- **Q8-B** (spec 026): SER lee el diagnóstico **completo**. Esta spec lo acompaña con tacto, sin dinero en la memoria y declarándolo en la política de privacidad.
- **Los rituales piden solo texto** y **«Última comida del día» lleva círculo** (31/10, puntos 7 y 8 del rediseño de Training).
- **«SER estará para las 3»** (02/11): Escuela, Diplomado (3 meses o 1 año) y Certificación (90 días).
- **Reglas generales del lote**: reutilizar, no crear tablas innecesarias, hexagonal / Modulith / SOLID.

### Decisiones de quien implementa (a revisar con el dueño)

1. **Principio rector: SER nunca propone algo que el servidor va a rechazar.** Donde el servidor decide con datos que SER ya puede leer, SER comprueba antes de dejar la tarjeta (fecha del plan del día, día cerrado, categoría de la formación, hábito que pide escribir).
2. **Lo que SER no puede escribir se lleva a Plan** con las palabras de la app: el objetivo grande (se confirma una vez; luego queda fijo), el objetivo del mes y «Cierra tu día». SER **no gana herramientas de escritura** para eso (se mantiene la decisión de las specs 026, 028 y 033).
3. **El ritual por texto reutiliza la tarjeta de evidencia** (`conPregunta = true` en el cable). La app lee «ritual: se escribe» y abre el mismo registro por texto de Training (`RegistroDeRitualConTexto` → `registrarConTexto`). Sin endpoint ni tabla nuevos, y las tarjetas ya dejadas en un chat (antes pedían cámara) pasan a escribir.
4. **Cumplir con SER también apaga las alarmas.** `POST /renasia/propuestas/{id}/confirmar` devuelve (aditivo) `habitoCumplido {registroId, habitoId, fecha, puntos}` cuando la propuesta cumplió un hábito, y la app emite el mismo aviso `habitoCumplido` que `completarRegistro` (alarmas, celebración, semáforo, fénix). Se cubre `marcar_habito_completado` (Despertar, Dormir, opcionales, Última comida). **La Pastilla, la Audioterapia y la Clase** cierran por su propio camino y siguen sin avisar (límite aceptado, ver abajo).
5. **V160** deja «Última comida del día» (`3a8a82a0-787d-46bc-a69e-b29c110a37bc`) con evidencia **OPCIONAL**, de forma idempotente. «Primera comida» y el resto del catálogo no se tocan (el dueño nombró solo la última).
6. **Categorías por formación sin refactor grande**: `habits.api.CategoriasDeLaFormacionFinder` (nuevo, solo lectura, la misma lectura de Training) y `rag` reemplaza el enum `DimensionDelHabito` por `CategoriaDelHabito` (clave + nombre). El enum queda solo para entender cómo se dice en la Escuela («emociones» → `CONSCIENCIA`).
7. **El eje `TRABAJO` se dice «Negocio»** en lo que ve la persona (tarjetas, textos). El argumento interno de las herramientas sigue siendo `TRABAJO`.

## User Scenarios & Testing

### Historia 1 — SER habla de lo que existe hoy (P1)

**Prueba**: pedirle a SER, en una cuenta nueva (sin Mapa) de cada formación, «¿cuáles son mis objetivos?», «arma mi día» y «arma mi semana».
1. **Given** una persona sin Mapa, **When** pregunta por sus objetivos, **Then** SER usa el diagnóstico y/o `consultar_rocas noventa`, dice el plazo de **su** formación («objetivo de 3 meses», «de 1 año», «de 90 días») y nunca menciona el Mapa.
2. **Given** objetivos que nacieron del diagnóstico y no se abrió Plan, **When** SER los lee, **Then** salen marcados **POR CONFIRMAR** y SER la lleva a Plan; no dice «no definiste objetivos».
3. **Given** que pide cambiar su objetivo grande, armar el objetivo del mes o cerrar su día, **When** SER responde, **Then** dice dónde se hace («en Plan, en el eje que quieras: ahí escribes tu objetivo y lo confirmas una vez; luego queda fijo») y **no deja ninguna tarjeta**.
4. **Given** que pide armar el plan de un día que ya no se puede (hoy pasadas las 18:00, un día que pasó, o más allá del domingo), **When** SER responde, **Then** no deja la tarjeta y dice desde cuándo sí se puede.

### Historia 2 — SER registra los hábitos como la app (P1)

1. **Given** un ritual, **When** la persona pide marcarlo, **Then** SER deja el botón «Escribir cómo me siento», sin cámara; la tarjeta abre el registro por texto de Training.
2. **Given** «Última comida del día», Despertar, Dormir o un hábito de evidencia opcional, **When** pide marcarlo, **Then** SER deja la propuesta con «Confirmar» (sin foto), y al confirmar la app apaga las alarmas de ese hábito que le quedaban a ese día y celebra.
3. **Given** un hábito de un día que ya cerró (`EXPIRADO`), **When** pide registrarlo, **Then** SER dice que ese día ya cerró y no deja botón. Un hábito `PENDIENTE` al que se le pasó la hora **sí** se registra (con menos puntos).
4. **Given** el jugo verde u otro hábito de evidencia obligatoria, **When** pide marcarlo, **Then** SER deja el botón de la cámara, como siempre.

### Historia 3 — SER es de las tres formaciones (P1)

Con una persona de la Certificación de 90 días, del Diplomado de 3 meses y del Diplomado de 1 año (una sola prueba parametrizada): la situación dice la duración correcta; el objetivo grande se nombra con su plazo; los hábitos llevan las categorías **de su formación**; crear un hábito propio solo ofrece y acepta esas categorías; el prompt no nombra otra formación ni una duración fija; y orienta a Training (hábitos) y Plan (objetivos y «Cierra tu día»).

### Historia 4 — Lo sensible se trata con tacto (P2)

El diagnóstico completo llega al modelo (Q8-B). SER lo usa con sus palabras, no nombra montos, y la memoria **no guarda** ingresos, deudas, cuotas ni pagos. La política de privacidad lo dice. SER no lee los pagos (spec 032) ni los usa para presionar.

## Requirements

- **FR-001** SER MUST NOT invitar a hacer el Mapa de Renacimiento ni dejar un vínculo «completa tu Mapa» (prompt, `consultar_mi_mapa`, `MapaParaProponer`).
- **FR-002** `consultar_rocas noventa` MUST distinguir objetivos **por confirmar** (nacieron del diagnóstico) de los confirmados, y sin objetivos MUST mandar al diagnóstico o a Plan.
- **FR-003** Ningún texto de SER MUST fijar «90 días», el nombre de una formación ni «Trabajo»: el plazo sale de la situación de la persona y el eje se dice Negocio.
- **FR-004** SER MUST orientar con los nombres de hoy: hábitos y evidencia en **Training**; objetivos, 80/20 y «Cierra tu día» en **Plan**.
- **FR-005** `proponer_plan_del_dia` MUST comprobar antes de proponer que la fecha cae entre el primer día planificable ahora y el fin de la semana (el domingo, hasta el lunes), con los datos de `consultar_rocas`; si no, no deja la propuesta y dice desde cuándo sí. Si no puede leerlos, no bloquea.
- **FR-006** Los rechazos que la app muestra tal cual MUST estar en segunda persona y tuteo.
- **FR-007** Los tres rituales MUST registrarse escribiendo: `proponer_registrar_con_foto` sobre un ritual deja la tarjeta con `conPregunta = true`, sin pedir foto ni «¿Qué sentiste?»; la app abre `RegistroDeRitualConTexto`.
- **FR-008** La migración **V160** MUST dejar «Última comida del día» con `exigencia_evidencia = 'OPCIONAL'`, idempotente y sin tocar otro hábito.
- **FR-009** `confirmar` MUST devolver `habitoCumplido` cuando la propuesta cumplió un hábito, y la app MUST emitir `habitoCumplido` con ese dato. Vale también para las tres entregas que cierran un hábito por su propio camino: la Clase diaria (resumen), la Audioterapia (respuestas) y la Pastilla Renacer (resumen de Espíritu) (issue #99 de la app, 2026-11-09). Una entrega repetida que no cambia el estado del hábito no lo devuelve.
- **FR-010** Un registro `EXPIRADO` MUST tratarse como **día cerrado**: no cuenta entre lo que falta, no se le deja botón y SER lo dice.
- **FR-011** Los hábitos y la creación de hábitos propios MUST usar las categorías de la formación de la persona; nunca se ofrece o acepta una categoría de otra formación.
- **FR-012** La memoria de SER MUST NOT guardar dinero personal (ingresos, deudas, cuotas, pagos); la política de privacidad MUST declarar que el diagnóstico llega al modelo de IA.
- **FR-013** SER MUST NOT leer los pagos ni hablar de ellos más que para ubicar «Yo → Mi ingreso».

## Qué ve y qué dice SER en cada formación

| | Escuela (90 días) | Diplomado, 3 meses | Diplomado, 1 año | Certificación (90 días) |
|---|---|---|---|---|
| Situación | «su dia N de 90, fase F de 4» | «su dia N de 90» | «su dia N de 365» | «su dia N de 90» |
| Objetivo grande | «objetivo de 90 días» (no se aclara) | «objetivo de 3 meses» (y «nunca de 90 dias») | «objetivo de 1 año» | «objetivo de 90 días» |
| Semanas / meses | 13 / 3 | 13 / 3 | 53 / 13 | 13 / 3 |
| Categorías de hábitos | las 4 de la Escuela | las del Diplomado (las crea Administración) | las del Diplomado | las de la Certificación |
| Hábitos de hoy | los de su catálogo (ya filtrado) | los del Diplomado | los del Diplomado | los de la Certificación |
| Cursos y lecciones | los de la Escuela (spec 024) | los del Diplomado | los del Diplomado | los de la Certificación |
| «Sin objetivos» | «Objetivos de 90 días: todavía no…» | «Objetivos de 3 meses…» | «Objetivos de 1 año…» | «Objetivos de 90 días…» |
| Dónde manda | Training (hábitos), Plan (objetivos, 80/20, Cierra tu día), Cursos (Comunidad), Yo (pagos) | igual | igual | igual |
| Nunca | nombra otra formación, supone 90 días, ofrece el Mapa, ofrece una categoría ajena | igual | igual | igual |

## Material de conocimiento (enseñanzas, YouTube, cursos)

- **Lo que está ligado a una lección** (`base_conocimiento.leccion_id`) **sí se separa por formación**: la búsqueda solo trae fragmentos de lecciones visibles para la persona, y `LeccionesVisiblesAcademyService` ya filtra los cursos por su formación (spec 024).
- **Lo que no está ligado a una lección** (`leccion_id IS NULL`: enseñanzas generales, transcripciones de YouTube) **no se separa**: `PgVectorNativoAdapter` deja pasar ese material a todas las personas. Hoy toda esa base es del contenido de la Escuela, así que una persona del Diplomado o de la Certificación podría ver citado, en el material de fondo, algo que solo es de la Escuela.
- **Forma más simple de separarlo, sin tablas nuevas** (no se implementa en esta spec; necesita decisión del dueño): una clave `formacion` en `base_conocimiento.metadatos` (el `jsonb` ya existe y tiene índice GIN). La ingesta (`ConocimientoService.indexar`, que ya recibe `metadatos`) la escribe cuando el material es de una sola formación. La búsqueda agrega al `WHERE` de lo no ligado a lecciones `(metadatos->>'formacion' IS NULL OR metadatos->>'formacion' = ANY(:formaciones))`, con las formaciones que `AlcanceDeFormacionFinder` da para la persona. Lo que no tiene la clave sigue valiendo para todas (compatible hacia atrás). Un `UPDATE` de una sola vez marca como `ESCUELA` lo que el dueño confirme que lo es.

## Integrado el 2026-11-02 (tareas T020 y T021): 036 y 037 en SER

Lo de abajo se hizo en la consolidación; queda como la descripción de lo que se hizo (ver `tasks.md`, §8).

## Fuera de alcance / para cuando se integren 036 y 037

- **036 (el programa empieza al día siguiente):** hoy la situación dice «su dia 0 de 90» y el primer encuentro con SER cae justo ahí. Al integrarla: en `GoogleGenAiRenasiaChatAdapter.formatearSituacion`, si `diaPrograma < 1`, decir «Su programa de N dias empieza mañana (el martes 3 de noviembre). No hables de dia ni de fase.» (con el plazo de su formación); y `proponer_plan_del_dia` debe comprobar que ya eligió su Día 1 (sin él `semanasParaPlanificarUnDia` rechaza con `INVALID_DATE`).
- **037 (pasos y km):** al integrarla, `consultar_habitos_del_dia` suma `mide=km, meta=N, lleva=X` al hábito medible; en el prompt, «los km se miden solos si conectó sus pasos; si no, foto o galería»; SER **no pide foto** si hay pasos de Health Connect y manda al «Registrar» de la fila de Training (la tarjeta de SER para km abre ese flujo); y `PropuestaDeRegistrarConFoto` deja de ofrecer cámara para KILÓMETROS DIARIOS con pasos conectados.
- **Pastilla, Audioterapia y Clase por SER:** siguen sin avisar a la app del hábito cumplido (cierran por su propio camino). Para apagar sus alarmas habría que sumar el `HabitoCumplido` a sus tres confirmables.
- **Reintento de `confirmar`:** una respuesta repetida (doble toque, reintento tras una caída de red) trae solo el texto que quedó guardado, sin `habitoCumplido`; las alarmas de ese hábito se apagarán en la siguiente puesta al día solo si se cumplió desde Training. Es un límite aceptado: guardar el dato exigiría una columna nueva.

## Success Criteria

- **SC-001** Ninguna respuesta de SER, en las tres formaciones, manda al Mapa ni deja una tarjeta que el servidor rechace por fecha o por día cerrado.
- **SC-002** Un ritual pedido a SER se registra escribiendo en el mismo registro de Training (misma evidencia TEXTO, mismo cierre, mismos puntos).
- **SC-003** Cumplir Despertar con SER a las 06:00 apaga la alarma de las 06:30 de ese día, igual que desde Training.
- **SC-004** La prueba parametrizada de las tres formaciones pasa; el prompt es el mismo para las tres y ninguna prueba depende de una formación concreta.
