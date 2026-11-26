# Spec 058 — Plan como ruta de niveles (R1)

**Rama**: `058-plan-como-ruta` (app y backend)

**Creada**: 2026-11-25

**Estado**: En curso

**Input**: el dueño eligió el 25/11 la propuesta **R1 · Zigzag crema** de https://claude.ai/artifact/G4kZvuKmreDjTFxNx2Jkrh («vamos en el 1»), con estos ajustes suyos del mismo día: sin la fila de cifras (día · de 7 días · % semana), sin colores por eje (todo en el dorado de la app, cada eje con su ícono de siempre), sin el subtítulo «Tu dirección en 90 días», y la planificación de los objetivos detrás de un engranaje «igual que hábitos». Pidió implementarla («tú mismo procede con este cambio») y tenerla en su teléfono para hacer él mismo el E2E y ver si se entiende y si así la gente usa los objetivos.

## Por qué

En el podio de la semana casi nadie suma puntos por objetivos. El E2E del 25/11 (`.maestro/plan-por-dia/`, 20 flujos) mostró que Plan funciona, pero apila cuatro pisos (90 días, mes, semana y acciones) y lo de hoy aparece recién al bajar. La R1 ordena las mismas piezas como un camino de niveles: lo que toca se ve primero y cada paso tiene su lugar.

## Regla de esta spec: mismo comportamiento, otra pantalla

| Pieza de Plan hoy | En la R1 | Qué se abre (se reutiliza tal cual) |
|---|---|---|
| Selector Cuerpo · Negocio · Relaciones | Se queda arriba; una ruta por eje | — |
| Tarjeta «Tu objetivo de 90 días» | Una línea con la meta y su barra de avance | El mismo editor de los tres puntos |
| Tarjeta «Revisa tus objetivos de 90 días» (por confirmar) | Igual, arriba de la ruta | `HojaConfirmarObjetivos` |
| «Planificar mi mes» | Un separador «Mes N · <objetivo del mes>» | `PlanDelMesModal` |
| Tarjeta «Esta semana» | Franja dorada con el objetivo de la semana y «N de M» | `PlanSemanalModal` |
| Semana sin armar | El primer nivel es «Arma tu semana» | El mismo flujo de «Organizar mi semana» |
| «Acciones que mueven la meta» | Un nivel por acción, en orden de día y hora, con su título y «Día HH:MM» al costado | La misma hoja de la acción y su evidencia |
| «Agendar otro día» | Un nivel «+» al final de la semana | `AgendarAccionesModal` |
| «Cerrar este objetivo con mi revisión» | El cofre al final de la semana | `RevisionSemanalModal` |
| «Recordatorios de tus acciones» | Dentro del engranaje (ver FR-010) | `RecordatorioDeAcciones` |
| «Cierra tu día», «Tu 80/20», «Organizar mi semana» | Debajo de la ruta, en ese orden | Lo mismo de hoy |

**Ningún endpoint, tabla ni regla de negocio nueva.** El único cambio del backend es una entrada en el catálogo de funcionalidades (FR-001).

## Escenarios de aceptación

### Historia 1 — La ruta solo para quien la tiene (P1)

1. **Dado** `plan.ruta` apagada o no disponible para la persona, **entonces** Plan se ve exactamente como hoy.
2. **Dado** `plan.ruta` disponible (por ejemplo, despliegue «Solo probadores» con el dueño como probador), **entonces** Plan se ve como ruta.

### Historia 2 — Ver y recorrer la semana (P1)

1. **Dado** una semana armada con acciones agendadas, **cuando** la persona abre Plan en un eje, **entonces** ve, de arriba abajo: el título «Plan» con el engranaje, el selector de ejes, la línea de la meta de 90 días, el separador del mes, la franja de la semana y los niveles de esa semana en orden de día y hora; el «+» y el cofre al final; debajo, «Cierra tu día», «Tu 80/20» y «Organizar mi semana»; y al final «Lo que viene» (semanas siguientes bloqueadas, los meses que faltan y la bandera de la meta).
2. Un nivel cumplido se ve lleno con un check; el siguiente por hacer es más grande y lleva al fénix al costado; los demás de la semana se ven por hacer.
3. Por día van hasta 3 acciones por eje: la 2.ª y la 3.ª aparecen con candado hasta cumplir la 1.ª (la misma regla de hoy, Ley IV del servidor).
4. Tocar un nivel por hacer de hoy abre la misma hoja de la acción y se completa con evidencia (+10 puntos, como hoy). Al volver, el nivel queda cumplido y la franja suma uno.

### Historia 3 — Armar y cerrar (P1)

1. **Dado** una semana sin objetivo, **entonces** el primer nivel es «Arma tu semana» y los demás tienen candado; tocarlo abre «Plan de la semana».
2. **Dado** el día 1 sin objetivos de 90 días, **entonces** el primer nivel es «Tus metas de 90 días» y abre el editor de hoy.
3. Tocar el cofre abre la revisión semanal de hoy (nota del 1 al 10, qué te frenó, qué harás distinto). Revisado, el cofre se ve abierto.

### Historia 4 — Planificar tus objetivos, igual que hábitos (P1)

1. **Cuando** la persona toca el engranaje, **entonces** se abre «Planifica tus objetivos» con: «Tu semana» (el objetivo de la semana por eje y «Editar mi semana»), «Tu agenda, día por día» (los días de la semana con un punto en los que tienen acciones y «Agendar mis acciones»), «Planificar mi mes» y «Recordatorios de tus acciones».
2. Cada fila abre la hoja que ya existe, sin cambiar sus campos.

## Requisitos funcionales

- **FR-001** (backend): el catálogo de funcionalidades tiene la clave `plan.ruta` («Plan como ruta»), hija de `plan`, que **nace apagada**; se prende y se despliega por porcentaje o a probadores desde la consola de Soporte TI, como cualquier otra.
- **FR-002**: con `plan.ruta` no disponible, `PlanScreen` se ve como hoy (sin ningún cambio visual).
- **FR-003**: con `plan.ruta` disponible, Plan muestra la ruta del eje elegido con las piezas y el orden de la Historia 2.1. Sin la fila de cifras y sin subtítulo bajo «Plan».
- **FR-004**: cada nivel muestra el título de la acción y su día y hora («Mar 07:00», «Hoy 14:00»; sin hora, solo el día); cumplido, lleno con check; el siguiente por hacer, grande y con el fénix; bloqueado por la Ley IV, con candado.
- **FR-005**: tocar un nivel abre la misma hoja de la acción que hoy; si esa hoja solo puede abrirse en Entrenar sin duplicar código, se navega como hoy y se vuelve a Plan (se documenta en el PR).
- **FR-006**: semana sin objetivo → primer nivel «Arma tu semana»; día 1 sin objetivos → primer nivel «Tus metas de 90 días».
- **FR-007**: el «+» al final de la semana abre «Agendar mis acciones» en el primer día que queda.
- **FR-008**: el cofre abre la revisión semanal; se ve abierto cuando la semana del eje está revisada.
- **FR-009**: «Cierra tu día», «Tu 80/20» y «Organizar mi semana» se ven debajo de la ruta, con su comportamiento de hoy.
- **FR-010**: el engranaje abre «Planifica tus objetivos» (Historia 4). La fila «Recordatorios de tus acciones» sale del fondo de Plan y vive aquí (Yo → Alarmas sigue igual).
- **FR-011**: todo en el dorado de la app; los ejes se distinguen por su ícono de siempre, no por color.
- **FR-012**: textos en español de Perú con tuteo, tipo oración; el plazo es el de la formación de la persona (nunca «90» escrito a mano); toques de 48–52 px; un solo scroll; `useSystemBackHandler` en la hoja nueva.
- **FR-013**: sin señal se comporta como Plan hoy (`planSinRed`): la ruta se dibuja con lo que hay guardado.
- **FR-014** (pedido del dueño el 25/11: «la ruta ponlo con efectos… para que las personas vean que lo hacen»): al cumplir una acción el nivel hace un «pop» con su check y un toque háptico; el fénix salta al siguiente; la franja de la semana sube su cifra y su barra; el cofre se abre con un rebote cuando la semana queda revisada; los niveles entran en cascada (menos de 300 ms) la primera vez de la sesión; el siguiente por hacer «respira» suave. Con «Reducir movimiento» activado no se anima nada (el háptico sí). Cada efecto se dispara una vez por cambio real.
- **FR-015** (ajuste aprobado por el dueño el 26/11, «Define tu objetivo» en tres pasos): el formulario del objetivo se hace en tres pasos: (1) «¿Qué quieres lograr en <eje>?» con ideas de un toque por eje (texto fijo del cliente) y el campo libre; (2) «¿Cómo vas a medirlo?» con unidades de un toque por eje, «Otra unidad» y «No se mide con un número» (guarda sin los tres campos); (3) «¿Desde dónde partes y a dónde quieres llegar?» con dos números grandes, una flecha y la frase armada («Tu objetivo: bajar de 82 a 76 kg en <plazo de su formación>. Son unos 2 kg por mes.»). Al crear, «Vas en» = «Partiste de». Se guarda con el mismo `PUT /api/v1/rocks/master/{eje}` y los mismos campos; el objetivo queda fijo (409 `ROCA_MAESTRA_FIJA`). Con el objetivo ya fijo, la hoja es «Anota tu avance»: un número («¿En cuánto vas hoy?») con su barra y «Llevas X de Y <unidad> · Z %».
- **FR-016** («Tu 80/20» con el fénix): la hoja mantiene su mensaje de inicio y recomienda la acción diaria que genera ese 100 % con reglas fijas del cliente y los datos que ya llegan. Hoy: dona con el peso de cada color y «cumplidas/total» al centro, lista por orden de impacto y tarjeta del fénix («Lo que más te acerca ahora: <acción> a las <hora>»; devolución corta si todo está cumplido; «Reagéndala» —abre la hoja de agendar— si la verde de ayer no se cumplió). Semana: mapa de calor por día y eje (Todo, La verde, A medias, Nada; hoy resaltado) y una recomendación (eje en blanco, la verde que más se repite o el eje más atrás). Mes: cifra de hoy, meta del mes y una proyección lineal calculada en el cliente (inicio, actual, meta del mes, días transcurridos), o el avance de acciones si el objetivo no es numérico. Sin servidor nuevo: lo que el servidor no manda por día (los días ya pasados salvo las cinco acciones que más movieron y las cinco que se fueron) se muestra «sin dato», no se inventa.
- **FR-017** («Planifica tus objetivos» con el mismo diseño): tarjetas limpias, cifras grandes («3 de 3 ejes con semana armada», «5 de 7 días con acciones») y una recomendación del fénix (semana por armar, días sin acciones o cierre). No cambia qué hace ni a dónde lleva cada fila.

## Lo que no cubre

- Colores por eje, cifras arriba, puntos o premios nuevos al abrir el cofre (descartados por el dueño el 25/11 o fuera de esta versión).
- Los issues #261 y #262 (se arreglan aparte).
- La inconsistencia de los dos «mes en curso» entre los días 25 y 30: espera decisión del dueño.

## Cómo se prueba

- Jest de la lógica pura de la ruta (orden de niveles, estados, candados, primer nivel especial) con marcas `// spec 058 FR-NNN`.
- Maestro: `.maestro/plan-ruta/` con la cuenta y los datos de `.maestro/plan-por-dia/datos.sh`, más los 20 flujos de `plan-por-dia` con `plan.ruta` apagada (Plan de hoy intacto).
- El dueño hace el E2E final en su teléfono.
