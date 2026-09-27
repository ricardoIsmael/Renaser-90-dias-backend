# Spec — Retroalimentación del 26/09/2026 (entrega: viernes 02/10/2026)

Pedido del dueño, con la retroalimentación de administradores, docentes y mentores. Este documento es
la fuente de verdad del trabajo hasta el viernes. Cada ítem dice qué se hace, cómo se acepta y cómo se
prueba de punta a punta.

> **Actualizado 2026-09-27.** El estado real de cada ítem está al pie de su sección («Estado al 27/09», en §1 a §5).
> Las decisiones nuevas del dueño están en §9 (10 a 29) y las preguntas que se le hicieron el 27/09, con lo que
> decidió, en §12. §10 y §11 quedan como la foto del 26/09 a la noche y de la madrugada del 27/09. Estados que se
> usan: **Hecho y verificado** (se dice con qué: pruebas automáticas, emulador, Playwright o la base) · **Hecho** (sin
> una verificación propia registrada) · **En curso** (un agente lo está haciendo) · **Falta** · **Pendiente del dueño**.

## 0. Reglas de este trabajo (no se negocian)

1. **Nada que ya funciona se rompe.** Cada entrega corre `./mvnw clean verify` (backend), `tsc` + Jest
   (app) y la batería del acompañante completa (125+ preguntas) contra la ronda anterior.
2. **Pruebas e2e siempre**: emulador + base local + backend local, verificando en la pantalla y en la
   base. Un ítem no se da por hecho sin su prueba e2e.
3. **Sin tablas nuevas ni datos duplicados.** Se reusa lo que existe. Si algo del esquema es inevitable,
   se justifica acá: un valor de enum (`RECORDATORIO_EVENTO`, V70, ver E-3 y D-183) y una columna
   (`asignaciones_celula.bienvenida_enviada_en`, V71, D-191). Tablas nuevas: ninguna.
   > **Corregido 2026-09-27.** Decía «(hoy solo aparece un valor de enum, ver E-3)». Después entró V71: la marca de
   > la bienvenida del grupo, una columna que eligió el dueño para no crear una tabla (§9.12).
4. **Usuarios de 30 a 60 años**: letra de 16 px o más en el cuerpo, botones de 48–56 px, una acción
   principal por pantalla, palabras simples, pocos pasos.
5. **Trabajo en agentes en paralelo**, cada uno en su rama y worktree; lo que toca los mismos archivos
   va en secuencia. Se verifica cada informe antes de integrarlo.
6. Documentar en el mismo cambio (decisiones D-nn, bitácora E-nn).

## 1. Velocidad (Muro y Training tardan ~3 s)

**Diagnóstico (26/09, mediciones reales en producción):** el servidor está ocioso (t3.small, CPU 1 %,
créditos llenos, RDS con latencia < 1 ms). La demora es: (a) la app encadena rondas de pedidos, (b) cada
pedido cruza a Miami porque CloudFront usa `PriceClass_100` (130–490 ms solo de red), (c) algunos N+1.
**Subir la instancia no quita los 3 s.** No se sube por ahora.

| # | Cambio | Aceptación |
|---|---|---|
| V-1 | Training: una sola ronda de pedidos (quitar el `await plan.recargar()` en serie y el `/habits` pedido 3 veces) | Training hace 1 ronda al abrir |
| V-2 | Training: después de completar/sellar, refresco silencioso (sin pantalla de carga) | Respuesta visible < 300 ms |
| V-3 | Muro: `/wall` primero; cursos, ranking, chat y directorio se cargan al abrir su sección | ≤ 3 pedidos iniciales |
| V-4 | Muro: lista virtualizada (`FlatList`), sin `setPostOffsets` por post | Scroll fluido con 100 posts |
| V-5 | Backend `/habit-tracks/today`: `readOnly`, progreso una vez, sin escrituras en el GET normal | p95 servidor < 150 ms |
| V-6 | Backend: autores de comentarios en lote, `default_batch_fetch_size`, caché del catálogo | p95 < 100 ms |
| V-7 | Medir: filtro `Server-Timing` + log de ms por pedido | Se puede comparar antes/después |
| V-8 | JVM: `-XX:MaxRAMPercentage=60` y `--memory` en `docker run` (hoy 1,43 GB de heap en 1,9 GB sin swap) | Sin riesgo de OOM |
| V-9 | **CloudFront `PriceClass_All`** (Perú usa el nodo de Lima) — **requiere OK del dueño** (cambio de infra; costo: centavos al mes) | Se ve el POP de Lima |

**Prueba e2e:** script `curl -w` con p50/p95 por endpoint antes y después; tiempo al primer render en la
app (emulador). Meta desde Lima: Training y Muro < 1,2 s.

**Estado al 27/09:**

| # | Estado | Detalle |
|---|---|---|
| V-1 | Hecho y verificado | Training abre con una sola ronda de pedidos (D-187). Emulador: de esqueleto a datos en ~1 s (build de desarrollo) |
| V-2 | Hecho y verificado | Al completar, refresca sin pantalla de carga (D-187). Los < 300 ms no se midieron aparte |
| V-3 | Hecho y verificado | Comunidad abre con `/wall` y `/home`; cada sección carga la primera vez que se abre (D-187). Muro en ~1 s en el emulador |
| V-4 | Hecho | Muro con `FlatList` (D-187). La prueba con 100 posts no quedó registrada |
| V-5 | Hecho y verificado con pruebas | Una sola lectura `readOnly`, sin escrituras en el GET normal (D-180). El p95 se mide en producción, después de subir |
| V-6 | Hecho, **sin la caché** | Autores de comentarios en lote y `default_batch_fetch_size` (D-180). **La caché del catálogo no se hace**: guardaría un objeto que cambia y lo compartiría entre pedidos, para ahorrar una consulta chica (D-180) |
| V-7 | Hecho y verificado | `Server-Timing` y una línea `[http] … ms` por pedido, vistos en local (D-180, E-307) |
| V-8 | Hecho en código · falta vigilar | `--memory 1400m` y `MaxRAMPercentage=60` en el despliegue (D-180). Después de subir, mirar `docker stats` |
| V-9 | Hecho · falta confirmar | `PriceClass_All` desde el 26/09 (§9.1). Falta ver que Perú use el nodo de Lima |
| Prueba e2e | Falta (al subir) | La medición «antes» es la del 26/09; el «después» con `curl -w` y el primer render desde Lima se hacen en producción, después de la subida (§7) |

## 2. Semáforo en las vistas de mentor y admin

**Diagnóstico:** el cálculo del semáforo es correcto y coherente entre aprendiz, mentor y admin. Lo que
falla es la pantalla del mentor: al lado del semáforo hay un indicador que **nunca recibe datos**
(`mentorApi.ts` pone en `null` sus campos) y siempre dice "sin avance registrado", "0 al día" y "Día por
confirmar". Además conviven 3–4 "semanas" distintas (lunes a domingo, sábado a viernes, mes).

| # | Cambio | Aceptación |
|---|---|---|
| S-1 | Quitar el indicador vacío de "Mi grupo" y de la tarjeta del mentor en Hoy; el semáforo manda: "N necesitan tu ayuda" | Ninguna pantalla dice "sin avance" si el semáforo tiene datos |
| S-2 | Ficha del alumno (mentor y admin): semáforo primero; la semana lunes-domingo plegada y renombrada | Una sola palabra de estado |
| S-3 | Tabla del admin: tocar una fila abre la ficha | Persona en ≤ 4 toques |
| S-4 | Lista "¿A quién atiendo hoy?" (rojo/amarillo) arriba en Administración, incluyendo grupos sin mentor y de bienvenida | Aparece todo activo en rojo/amarillo |
| S-5 | "Sin datos" con motivo (no arrancó / sin nada planificado / fuera del programa) — campo calculado, sin tabla | El mentor los distingue |
| S-6 | Texto fijo: "lo que completas después del cierre del sábado ya no cambia esa semana" | Visible en detalle y ficha |
| S-7 | Protecciones del cierre semanal: sin mensaje de color si la semana tiene < 3 días con datos; runbook de reproceso; checklist de encendido | Alta de viernes no recibe "en rojo" |
| S-8 | Revisar ventanas que cruzan la medianoche del viernes y los días de suspensión | Prueba con reloj fijo |
| S-9 | Mentor suspendido pasa guards (E-258) y "Al día" sin datos en Hoy (E-257) | 403 y texto correcto |

**Prueba e2e:** aprendiz, mentor y admin ven el mismo %, color y fechas; mentor con un alumno en rojo
ve "1 necesita tu ayuda" en Hoy.

**Estado al 27/09** (colores, umbrales y palabras del semáforo sin cambios, §9.6):

| # | Estado | Detalle |
|---|---|---|
| S-1 | Hecho y verificado | Hoy del mentor y Mi grupo dicen «N necesitan tu ayuda»; se quitó el indicador vacío (D-187). Emulador + Playwright |
| S-2 | Hecho y verificado | Ficha con el semáforo primero; la semana de lunes a domingo, plegada como «Detalle de hábitos» (D-187) |
| S-3 | Hecho y verificado | Tocar una fila abre la ficha (D-187). Playwright |
| S-4 | Hecho y verificado | «¿A quién atiendo hoy?» arriba en Administración, con recepción, grupos sin mentor y personas sin grupo (D-181, E-306) |
| S-5 | Hecho y verificado | «Sin datos» con su motivo, calculado y sin tabla (D-181); en mentor y admin se lee «Todavía sin actividad para medir» (D-187) |
| S-6 | Hecho y verificado | Texto fijo del cierre del sábado en el detalle y en la ficha (D-187) |
| S-7 | Hecho y verificado con pruebas | Sin color en el chat si la semana tuvo menos de 3 días con datos; checklist de encendido y reproceso en `docs/DESPLIEGUE_Y_CI.md` §6.4 (D-181) |
| S-8 | No se toca (decisión del dueño, §9.9) | El borde del viernes queda fijado con la prueba `VentanaEntregaTest.ventanaDelViernesCruzaElCierreDelSemaforo` |
| S-9 | E-258 hecho · **E-257 falta** | El mentor suspendido ya no pasa (D-181, E-258). La tarjeta «Hábitos de hoy» del aprendiz sigue diciendo «Al día» cuando no hay datos (`HoyScreen.tsx`, frontend 390e465): E-257 no se arregló |
| Prueba e2e | Hecho y verificado | Mismo %, color y fechas para aprendiz, mentor y admin: pruebas, emulador y Playwright del 26/09 (§10) |

## 3. Administración más simple (30–60 años)

| # | Cambio | Aceptación |
|---|---|---|
| A-1 | Letra ≥ 16 px en el cuerpo de admin y mentoría; botones 48–56 px; destructivos con confirmación | Prueba que falla con `fontSize < 16` en esas carpetas |
| A-2 | Palabras simples: Cohorte→Generación, Recepción→Grupo de bienvenida, Staff→Equipo, Cumplimiento→Cuánto cumplió | Glosario aplicado |
| A-3 | Administración sin secciones repetidas; "Pendientes" + "Necesitan atención" | Menos scroll |
| A-4 | Solicitudes con "Ver más" (hoy solo carga la primera página) y aprobar en un toque | Se aprueba la solicitud 21 |
| A-5 | "Asignar mentor" con confirmación ("Asignar a X como mentor de Y") | No asigna por error |

**Estado al 27/09:**

| # | Estado | Detalle |
|---|---|---|
| A-1 | Hecho y verificado | Letra de 16 px o más y botones de 52 px (`Legible.tsx`); una prueba falla si vuelve la letra chica en esas carpetas (frontend 24c9c25). Pruebas + Playwright |
| A-2 | Hecho y verificado | Generación, Grupo de bienvenida, Equipo, Cuánto cumplió (D-187) |
| A-3 | Hecho y verificado | Administración sin secciones repetidas (D-187) |
| A-4 | Hecho y verificado | Solicitudes con «Ver más» y aprobar en un toque (D-187). Playwright |
| A-5 | Hecho y verificado | «Asignar mentor» pide confirmación (D-187) |
| Agregado: «Cambiar día» | Hecho y verificado | Administración adelanta o retrocede el día de un aprendiz desde su ficha: días 1 a 89 (D-194), 409 antes del Día 1 (D-195), los hábitos del Plan que ya venía haciendo siguen (D-196), el barrido no pisa el ajuste (D-197) y los hábitos de hoy no se rehacen (D-198). Pruebas + e2e por API y en emulador (frontend 0b6fffa a a3eeac8) |
| Retroceder con hábitos propios o con horario | En curso (D-200) | El dueño decidió el 27/09 que, si ya venían corriendo, se mantienen activos (§9.20) |
| Semanas de rocas | En curso (D-203) | Con «Cambiar día» pasaron a contarse por día del programa (D-192); el dueño eligió volver a lunes a domingo para todos, sin «semana 14» (§12, pregunta 1) |

## 4. Eventos, recordatorios, notificaciones y calendario

**Diagnóstico:** los recordatorios de eventos **nunca se envían** (se marcan enviados sin que nadie los
mande, incluida la alarma de 04:50); **nadie puede crear eventos** (no hay pantalla); el recordatorio de
un hábito puede llegar **dos veces** (alarma del teléfono + push del servidor que ignora si lo apagaste);
los interruptores de "Notificaciones & Alarmas" en Yo **no guardan nada**.

| # | Cambio | Aceptación |
|---|---|---|
| E-1 | Enviar los recordatorios de eventos (listener en `notifications`) | Push y bandeja a la hora; sin duplicados |
| E-2 | El push del servidor respeta `recordatorio_activo`/`minutos_recordatorio` de cada hábito; logros por hábito solo en la bandeja | Apagado = sin push |
| E-3 | Tipo de notificación `RECORDATORIO_EVENTO` (**un valor de enum, no una tabla**) o reusar `ANUNCIO_SISTEMA` | — |
| E-4 | Yo: 4–5 interruptores reales ("Hábitos", "Eventos y clases", "Mi grupo y mensajes", "Logros", "Resumen semanal") guardados en `preferencias_notificacion` | Persisten al reiniciar |
| E-5 | Sección **Eventos en Comunidad**: próximos 30 días, detalle, botón "Voy / No voy"; la tarjeta de Hoy abre el detalle | Letra ≥ 16 px |
| E-6 | Formulario mínimo para crear/editar/cancelar eventos (Alquimista y admin; el mentor ya no, §9.3 y §9.8). *Corregido 2026-09-27: decía «(admin; mentor para su grupo)».* | Aprendiz recibe 403 |
| E-7 | "Voy" programa la alarma local del evento; "No voy" la cancela | Suena en el emulador |
| E-8 | "Mi agenda": lista de 7 días con eventos, hábitos con hora, acciones y color del semáforo (endpoints existentes) | — |
| E-9 | Canales de Android por tipo (Hábitos / Eventos / Mi grupo) para silenciarlos por separado | Requiere APK |
| E-10 | **Alarma con sonido propio** (Despertar, 04:50) — requiere cambio nativo y APK; **decisión del dueño** | — |
| E-11 | Acompañante: casos de eventos/semáforo/recordatorios en la batería; no confundir la semana del semáforo con la de objetivos | Batería OK |

**Estado al 27/09:**

| # | Estado | Detalle |
|---|---|---|
| E-1 | Hecho y verificado | Los recordatorios de eventos llegan a la bandeja y como push, sin duplicados (D-182, E-301). Pruebas + base |
| E-2 | Hecho y verificado con pruebas | El push del servidor respeta el recordatorio de cada hábito; el logro por hábito va solo a la bandeja (D-184, E-302, E-303) |
| E-3 | Hecho | Tipo `RECORDATORIO_EVENTO`, un valor de enum (V70, D-183) |
| E-4 | Hecho y verificado | «Eventos y clases», «Logros» y «Resumen semanal» se guardan. Sin «Hábitos» (§9.4); «Mi grupo y mensajes» no va porque el servidor no manda esos avisos. Emulador + base |
| E-5 | Hecho y verificado | Eventos en Comunidad con dos vistas que pidió el dueño: **calendario del mes** y **tarjetas como los cursos**; detalle y «Voy / No voy»; la lista se relee al volver (E-315). Emulador |
| E-6 | Hecho y verificado | Crean, editan y cancelan el Alquimista y el Admin, con portada opcional; el mentor recibe 403 (D-186). Web |
| E-7 | Hecho y verificado | «Voy» programa una alarma exacta si está el permiso «Alarmas y recordatorios», y se rearma al abrir la app (E-314). `dumpsys alarm` |
| E-8 | Hecho y verificado | «Mi agenda» de 7 días con el color del semáforo. Emulador |
| E-9 | Hecho en código · apagado | Canal de Android por tipo, detrás de dos propiedades apagadas (D-188): la de recordatorios se prende cuando solo quede el APK nuevo (§7); la de acompañamiento, cuando se decida si ese aviso sale como banner (§12) |
| E-10 | Hecho y verificado · requiere APK | «Alarmas» en Yo: Despertar y eventos, sonido, hora y permiso exacto (§9.2). Emulador + `dumpsys alarm` |
| E-11 | Falta · pendiente del dueño | La batería no corre sin crédito de Gemini (E-294, §6) |
| Agregado: recordatorio de las acciones de los objetivos | Hecho · requiere APK | Pedido del dueño (§10, nuevo 1): uno diario a la hora elegida y otro antes de cada acción con hora; locales, sin servidor. Se configuran en Plan y en Yo → Alarmas |
| Agregado: aviso con voz | Hecho con audios provisionales · requiere APK | Sonido «Voz» en Yo → Alarmas: una frase fija por tipo (hábito, evento, acciones). Los audios son provisionales y se reemplazan por una grabación con el mismo nombre de archivo |
| Agregado: «Voy» y los avisos del servidor | Hecho y verificado con pruebas | El servidor deja de avisar solo si la persona tiene un teléfono registrado (D-189, §9.10) |

## 5. Grupos (D-173/D-174) — verificación y riesgos

| # | Cambio | Aceptación |
|---|---|---|
| G-1 | Guion e2e con cuenta nueva (el dueño aprueba como admin; lo demás automatizado por API/Redis) | Soporte "María – Formación Renaser", chat de dos con guía y mentor |
| G-2 | Bienvenida idempotente (usar la tabla existente `mensajes_bienvenida`, hoy sin uso) | Reintento no duplica |
| G-3 | No perder chats de dos en silencio (no comerse excepciones) | Reintento o error visible |
| G-4 | No crear chat de dos con suspendidos | Prueba |
| G-5 | Con almacenamiento `noop`, no mandar una imagen que no existe; nunca usar el bucket de producción por defecto en local | Sin foto rota |
| G-6 | Crear la célula de Bienvenida local (falta) para poder probar las guías | — |
| G-7 | Registrar D-173/D-174 en la bitácora | — |

**Estado al 27/09:**

| # | Estado | Detalle |
|---|---|---|
| G-1 | Falta · espera la cuenta | El dueño manda la cuenta nueva para probar el alta real al final del 27/09 (§9.24) |
| G-2 | Hecho y verificado con pruebas | Bienvenida sin duplicados, con la tabla `mensajes_bienvenida` que ya existía (D-185, E-298, E-299) |
| G-3 | Hecho y verificado con pruebas | Un chat de dos que falla se reintenta (D-185, E-300) |
| G-4 | Hecho y verificado con pruebas | Sin chat de dos con cuentas suspendidas (D-185, E-304) |
| G-5 | Hecho y verificado con pruebas | Sin almacenamiento real sale solo el texto, sin foto rota (D-185, E-305) |
| G-6 | Hecho | «Recepción de bienvenida (prueba)» existe en la base local desde el 26/09 a la noche, con dos guías, y se usó en el e2e |
| G-7 | Hecho | D-173/D-174 registradas, con sus errores E-298 a E-305 (D-185) |
| Bienvenida en el soporte | En curso (D-199) · pendiente del dueño | Sale del **programa**, no de la cuenta de Kelin, detrás del interruptor `BIENVENIDA_ACTIVA`, apagado hasta que el dueño apruebe los textos (§9.22). Los textos de hoy son borradores del equipo técnico (D-190) |
| Bienvenida en el chat del grupo | En curso (D-204) · pendiente del dueño | Hoy la firma el mentor (D-191, V71); el dueño decidió que también salga del programa, con un texto amigable y el mismo interruptor (§12, pregunta 3) |
| Chat estilo WhatsApp a pantalla completa, e integrantes para el mentor | Hecho y verificado | Frontend 26a5db0 a b5945ff. Emulador |
| Lista de chats por el último mensaje | Hecho | Como WhatsApp (§9.21): la app ordena por actividad y el servidor ya devolvía la lista así |
| Tocar el círculo del chat abre la información del grupo | En curso | Como WhatsApp (§9.21) |
| Foto del grupo | Pendiente del dueño | Las dos imágenes que pasó («Fotos de perfil - Formación 2026.png» y su copia «(1)») son el mismo archivo y salen en blanco (blanco sobre blanco). Hasta que la re-exporte se usa el fénix de la tarjeta de Canva (frontend 224a661) |
| Mensajes en vivo | Hecho y verificado · latidos en curso | No llegaban desde el 17/09 (E-331); arreglado y probado en el emulador el 27/09 con un mensaje de otra cuenta. Los latidos para detectar conexiones muertas están en curso (D-202) |
| Volver a poner a un mentor que ya estuvo | Hecho y verificado con pruebas | `PUT …/mentor` de A a B y de vuelta a A ahora reabre la jefatura y su chat (E-316) |
| Mover a un aprendiz A → B → A a mano | Falta | E-316 dejó anotado que el alta manual de aprendiz (`claveDeAlta`) tiene la misma falla; no se arregló |
| Chat con el mentor anterior | Decidido | Queda como historial; no se cierra (D-173, §9.7) |

## 6. Pendientes que no entran esta semana

- **Costo del acompañante:** enviarle solo las herramientas que sirven para cada pregunta (hoy 40 en
  cada llamada, ~15.000 tokens). Pendiente por decisión del dueño. El 27/09 lo confirmó: queda pendiente
  porque sin crédito de Gemini no se puede verificar (§9.26).
- **Crédito de Gemini local agotado (E-294):** recargar en AI Studio; separar keys de pruebas y producción.
- **Batería ronda 5:** retomar desde la #14 cuando haya crédito.
- **Transcripciones de los audios y las 77 lecciones de Sparkie:** el dueño las carga con
  `scripts/sparkie-indexacion/` (testimonios fuera).
- **Instancia más grande:** solo si después de V-1..V-8 el p95 sigue fuera de objetivo (t4g.medium
  ~+10 USD/mes, requiere imagen arm64).

## 7. Subida (viernes 02/10)

1. `clean verify` + Jest + batería completa en verde.
2. Push a master (backend, con OK del dueño) → CD; web por Vercel.
3. Parámetros de producción si cambian (con el script, cuenta 302277511407).
4. APK nuevo (cámara, orbe, eventos, interruptores) — sin OTA, hay que reinstalar.
5. Verificación en producción: health, logs sin errores, un recorrido real.

> **Actualizado 2026-09-27.** Lo que hay que tener presente en esta subida:
> - Paso 1: la batería completa espera crédito de Gemini (E-11, §6).
> - Paso 2: entran V70 (tipo `RECORDATORIO_EVENTO`) y V71 (marca de la bienvenida del grupo). V71 marca como ya
>   bienvenidos a todos los que hoy están en un grupo, así que nadie recibe una bienvenida atrasada (D-191).
> - Paso 3: las dos bienvenidas quedan apagadas con `BIENVENIDA_ACTIVA` hasta que el dueño apruebe los textos
>   (D-199 y D-204, en curso).
> - Paso 4: el APK lleva además Eventos con calendario y portada, Alarmas con aviso con voz, los recordatorios de las
>   acciones, «Cambiar día» y el chat estilo WhatsApp con mensajes en vivo (E-331).
> - Después de subir: confirmar el nodo de Lima (V-9), medir p50/p95 (§1), mirar `docker stats` (V-8) y, cuando solo
>   quede el APK nuevo, prender los canales de Android de los recordatorios (`canales-de-recordatorios`, D-188).

## 8. Plan por día

| Día | Trabajo |
|---|---|
| Sáb 26 | Spec (este documento) y arranque de los frentes sin decisiones pendientes |
| Dom 27 – Lun 28 | Velocidad V-1..V-8 · Semáforo+Admin S-1..S-5, A-1..A-5 · Grupos G-2..G-6 · Eventos backend E-1..E-3 |
| Mar 29 – Mié 30 | Eventos app E-4..E-8 · Semáforo S-6..S-9 · e2e de cada frente |
| Jue 01 | Batería completa, regresión, correcciones |
| Vie 02 | Subida a producción y verificación |

> **Actualizado 2026-09-27.** Lo planificado del domingo 27 al miércoles 30 se adelantó y quedó hecho el 26 y la
> madrugada del 27 (ver «Estado al 27/09» en §1 a §5). Lo que queda para la semana es lo marcado «En curso», «Falta»
> y «Pendiente del dueño» en esas tablas, más la subida del viernes (§7) con el OK del dueño.

## 9. Decisiones del dueño (26/09)

1. **V-9 — Sí.** CloudFront pasó a `PriceClass_All` el 26/09 (distribución E3O4M4W7JW3TJQ, cuenta
   302277511407). Verificar que Perú use el POP de Lima.
2. **E-10 — Sí.** En **Yo** una sección "Alarmas" para personalizar: activar o no cada alarma
   (Despertar, eventos), elegir el sonido y la hora. Sin tablas nuevas (preferencias existentes y el
   teléfono). El sonido propio requiere APK con el plugin de notificaciones.
3. **E-6 — Crea eventos el Alquimista** (y el Admin, que puede todo: confirmado, ver 8 y D-186). El mentor
   ya no crea eventos. *Corregido 2026-09-27: decía «supuesto a confirmar»; el dueño lo confirmó el mismo 26/09.*
4. **E-4 — Temas:** sin interruptor de "Hábitos" (ya se configura en cada hábito); **sí** "Eventos y
   clases". Los eventos se ven sobre todo en **Comunidad**.
5. **Meet / Drive:** el evento lleva un **link** que pega quien lo crea (Meet, Zoom o Drive) y el alumno
   ve un botón grande "Unirme". No se crean Meet automáticamente (exigiría OAuth de Google Calendar:
   más complejo y frágil).
6. **Semáforo:** se mantienen los colores, umbrales (≥80 verde, ≥60 amarillo) y palabras actuales; al
   dueño le gustan. Solo cambia lo de alrededor.
7. **Chat con el ex-mentor:** queda como historial y no se cierra para el ex-mentor (D-173, `docs/MODULO_CHAT.md` §9).
   *Corregido 2026-09-27: decía «Pendiente de confirmar: que el ex-mentor conserve el chat con el aprendiz (G, riesgo
   R6)». El dueño ya lo había elegido en D-173, en vez de cerrarlo.*
8. **Eventos:** solo el Alquimista y el Admin crean, editan y cancelan eventos; el mentor ya no (D-186).
   Sus eventos de célula ya creados se quedan.
9. **Cierre semanal del semáforo (S-8):** no se toca. La semana es de sábado a viernes y cierra como
   ya está establecido. Un hábito del viernes cuya ventana cruza la medianoche queda solo documentado
   como borde (test de caracterización), sin cambiar la regla. Palabras del dueño: «son reglas que ya
   están establecidas, no confundamos eso».
10. **«Voy» y los avisos del servidor (D-189):** el servidor deja de avisar de un evento solo si la persona tiene un
    teléfono registrado, donde suena la alarma de la app; quien responde desde la web sigue recibiendo los avisos.
11. **Textos de bienvenida (D-190):** no van en una variable de entorno. Viven en un archivo del programa
    (`src/main/resources/bienvenida/mensajes.yaml`) y cada parte del ingreso lleva su mensaje, como en OPE-01-01. Los
    de hoy son borradores.
12. **Marca de la bienvenida del grupo (D-191):** una columna nueva en `asignaciones_celula` (V71), sin tabla nueva.
    Nadie que ya estaba en un grupo recibe una bienvenida atrasada.
13. **Semanas de rocas por día del programa (D-192):** la semana pasaba a ser `ceil(día/7)` (días 85 a 90 en la 13,
    nunca en la 14) y seguía al día cuando se lo ajustaba.
    > **Corregido 2026-09-27 (D-203).** El 27/09 el dueño eligió volver a semanas de lunes a domingo para todos: el
    > domingo cierra la semana de todos, como dice el documento del programa, y la «semana 14» se sigue evitando
    > sumando los últimos días a la semana 13 (§12, pregunta 1). Lo implementa D-203 (en curso); hasta que entre, el
    > código de `evidencia-foto` sigue contando por día del programa.
14. **Pacto saltado por un ajuste (D-193):** queda pendiente y se firma después. El 27/09 el dueño lo confirmó también
    para quien no lo firmó a tiempo (§12, pregunta 2).
15. **«Cambiar día» acepta solo los días 1 a 89 (D-194).** Graduar no se hace por ahí.
16. **Antes del Día 1 no se ajusta (D-195):** se responde que la persona todavía no empezó y no se guarda nada.
17. **Al retroceder, un hábito del Plan que ya estaba activo sigue (D-196).** El 27/09 se extendió a los hábitos
    propios y con horario (punto 20).
18. **El barrido del reloj no pisa un ajuste hecho mientras corre (D-197).**
19. **Los hábitos de hoy no se rehacen al ajustar el día (D-198):** desde mañana se generan con el día nuevo.

### Del 27/09

20. **Retroceder el día:** un hábito propio o con horario que ya venía corriendo se mantiene activo (D-200, en
    curso). Responde lo que D-196 había dejado abierto.
21. **Chats:** la lista se ordena por el último mensaje, como WhatsApp (hecho). Tocar el círculo del chat abre la
    información del grupo, como WhatsApp (en curso).
22. **Bienvenida en el soporte:** sale del programa, no de la cuenta de Kelin (D-199, en curso), detrás del
    interruptor `BIENVENIDA_ACTIVA`, apagado hasta que el dueño apruebe los textos.
23. **Foto del grupo:** hasta que el dueño la re-exporte (las dos imágenes que pasó son el mismo archivo y salen en
    blanco), se usa el fénix de la tarjeta de Canva.
24. **Cuenta nueva para G-1:** la manda el dueño al final del 27/09.
25. **Fecha de inicio guardada en UTC y latidos del chat en vivo:** en curso (D-201 y D-202).
26. **Costo del acompañante:** la reducción de herramientas queda pendiente; sin crédito de Gemini no se puede
    verificar.
27. **Domingo Ritual: opción A**, semanas de lunes a domingo para todos (D-203, en curso). Ver §12, pregunta 1.
28. **Pacto atrasado: queda como está** (confirma D-193). Ver §12, pregunta 2.
29. **Bienvenida del grupo: sale del programa**, con un texto amigable y el mismo interruptor (D-204, en curso). Ver
    §12, pregunta 3.

## 10. Estado al 26/09 (noche) — qué está hecho y qué falta

> **Corregido 2026-09-27.** Esta tabla es la foto del 26/09 a la noche y ya no es el estado vigente, que está en
> «Estado al 27/09», al pie de §1 a §5. Lo que acá figura en curso o pendiente y ya está hecho: el rediseño de E-5, la
> portada de E-6, el grupo de bienvenida local (G-6) y los puntos 1 y 2 de «Nuevo, por decidir» (ver §11). Dos filas
> decían más de lo que había ya ese día y se corrigieron en su celda: V-5..V-7 (la caché del catálogo de V-6 no se
> hizo) y S-9 (E-257 sigue sin arreglar).

Todo integrado en `evidencia-foto` (backend y frontend). **Nada subido a master ni a producción** (decisión del dueño:
todavía no). Verificación: backend `clean verify` 4771 unitarias + 120 integración en verde; frontend 100 suites / 841
pruebas; Playwright admin/alquimista 26 ok + 1 saltada por datos; e2e en emulador con capturas en `~/Imágenes/e2e-26-09-*`.

**Eventos ≠ recordatorios de hábito.** Son dos cosas distintas y así se tratan aquí:
- *Recordatorio de hábito*: se elige en cada hábito (Training → dimensión → Planificar → «Recordatorio»: sin aviso /
  30 min / 10 min / a la hora / otra). Suena como alarma local del teléfono.
- *Evento*: clase o encuentro que crea el Alquimista/Admin; la persona dice «Voy» y suena una alarma antes.

| Ítem | Estado | Cómo se verificó |
|---|---|---|
| V-1..V-4 velocidad app | ✅ | Emulador: Training y Muro con datos en ~1 s (build de desarrollo) |
| V-5..V-7 velocidad backend y medición | ✅ salvo la caché del catálogo de V-6, que no se hizo a propósito (D-180). *Corregido 2026-09-27: decía solo «✅».* | Pruebas; `Server-Timing` visible en local |
| V-8 memoria del contenedor | ✅ código · ⏳ vigilar `docker stats` después de subir | — |
| V-9 CloudFront `PriceClass_All` | ✅ aplicado · ⏳ confirmar nodo de Lima | AWS |
| S-1..S-7, S-9 semáforo mentor/admin | ✅ colores y palabras sin cambios, salvo E-257 (parte de S-9): la tarjeta «Hábitos de hoy» del aprendiz sigue diciendo «Al día» sin datos. *Corregido 2026-09-27: decía «✅» para todo S-9.* | Pruebas + emulador + Playwright |
| S-8 cierre semanal | ✅ **no se toca** (decisión del dueño, §9.9) | — |
| A-1..A-5 administración simple | ✅ | Pruebas + Playwright |
| E-1..E-3 avisos de eventos del servidor | ✅ | Pruebas + base |
| E-4 interruptores de Yo | ✅ «Eventos y clases», «Logros», «Resumen semanal» · sin «Hábitos» · «Mi grupo y mensajes» no (el backend no emite esos tipos) | Emulador + base |
| E-5 Eventos en Comunidad | ✅ funciona · 🔄 **rediseño en curso**: vista Calendario del mes + vista Tarjetas estilo cursos (el dueño no quiere el diseño de filas) | Emulador |
| E-6 formulario del Alquimista | ✅ · ⏳ portada del evento (en el rediseño) | Web |
| E-7 alarma del «Voy» | ✅ exacta con el permiso «Alarmas y recordatorios» | `dumpsys alarm` |
| E-8 Mi agenda (+ semáforo) | ✅ | Emulador |
| E-9 canales de Android | ✅ código · apagado por propiedad hasta que solo quede el APK nuevo (D-188) | Pruebas |
| E-10 Alarmas en Yo (Despertar, eventos, sonido, permiso exacto) | ✅ | Emulador + `dumpsys alarm` |
| Recordatorio de hábito según lo elegido | ✅ «10 min antes» del ritual de 13:00 → alarma exacta 12:50 del día siguiente (el día en curso no se reacomoda) | Emulador + `dumpsys alarm` |
| E-11 batería del acompañante (eventos/semáforo) | ⏳ sin crédito de Gemini | — |
| G-1 grupos con cuenta nueva | ⏳ espera la cuenta nueva | — |
| G-2..G-5, G-7 grupos | ✅ | Pruebas |
| G-6 grupo de bienvenida local | ⏳ | — |
| Subida (§7) y APK nuevo | ⏳ espera el OK del dueño | — |

**Nuevo, por decidir con el dueño (26/09 noche):**
1. **Recordatorio de objetivos y acciones del día (rocas).** Hoy no existe: solo los hábitos tienen recordatorio.
   *(Decidido y hecho después: ver §4, «Estado al 27/09».)*
2. **Aviso con voz** («tu hábito está por empezar»). Hoy el aviso es una notificación con sonido (del teléfono o la
   campana). Opción liviana: un audio con voz grabada como sonido del canal (igual que la campana, solo APK nuevo);
   dice una frase fija y el nombre del hábito va en el texto. Que diga el nombre en voz con la app cerrada exige un
   módulo nativo (texto a voz), más pesado. *(Se hizo la opción liviana, con audios provisionales: ver §4, «Estado al
   27/09».)*
3. Si el teléfono no deja dar el permiso «Alarmas y recordatorios», la alarma igual suena, pero Android puede demorarla
   hasta ~1 h.

## 11. Cierre del 26–27/09 (madrugada) — todo integrado y probado, sin subir

Backend `evidencia-foto` baa48665: `clean verify` 4889 unitarias + 130 integración en verde. Frontend `evidencia-foto`
(390e465 + chat): 113 suites / 972 pruebas. Playwright 27/27 contra el backend nuevo. e2e por API y en emulador con capturas
(`~/Imágenes/e2e-26-09-*`) y el log del servidor revisado (0 ERROR con configuración válida, ningún 5xx).

Agregado después de §10 (decisiones del dueño): Eventos con calendario del mes y tarjetas como cursos; aviso con voz (audios
provisionales) y recordatorios de objetivos; chat estilo WhatsApp a pantalla completa, integrantes para el mentor, foto de grupo
(fénix de la tarjeta de Canva), mensajes en vivo (E-331, roto desde el 17/09); bienvenidas con textos versionados (D-190) y
bienvenida del mentor en el grupo (D-191, V71); remitente inválido apaga con aviso (E-330); «Cambiar día» en Administración con
riesgos cerrados (D-192..D-198: semanas de rocas por día del programa —resuelve la «semana 14» que hoy existe en producción—,
pactos firmables tarde, rango 1–89, 409 antes del Día 1, hábitos activos al retroceder, bloqueo contra el barrido).

Pendiente del dueño: textos oficiales de bienvenida (hoja de Operaciones) y cuenta de Kelin como remitente; re-exportar la foto de
perfil del grupo; preguntas abiertas (Domingo Ritual con semanas por día; pacto pendiente para quien no firmó a tiempo; hábito
propio creado después del día destino al retroceder; orden de la lista de chats). Subida (§7) y APK nuevo: esperan el OK.

> **Corregido 2026-09-27.** Lo que este cierre decía y cambió después:
> - «(390e465 + chat)»: el arreglo del chat en vivo (044159f) ya está dentro de 390e465. «Sin subir» quiere decir sin
>   subir a producción: las ramas `evidencia-foto` sí están en GitHub (backend 7e63e8dd, frontend 390e465).
> - «semanas de rocas por día del programa»: el 27/09 el dueño eligió volver a semanas de lunes a domingo para todos,
>   sin «semana 14» (D-203, en curso; §12, pregunta 1).
> - «bienvenida del mentor en el grupo (D-191, V71)»: pasa a salir del programa (D-204, en curso; §12, pregunta 3).
> - «cuenta de Kelin como remitente»: ya no se pide; la bienvenida del soporte sale del programa (D-199, en curso).
>   Siguen pendientes del dueño los textos oficiales, que se aprueban antes de prender `BIENVENIDA_ACTIVA`.
> - «preguntas abiertas»: las cuatro tienen respuesta del 27/09: Domingo Ritual (§12, pregunta 1), pacto de quien no
>   firmó a tiempo (§12, pregunta 2), hábito propio al retroceder (§9.20) y orden de la lista de chats (§9.21).

## 12. Preguntas al dueño del 27/09 y lo que decidió

Estas eran las preguntas abiertas («Por decidir»). Quedan escritas como se hicieron, con la respuesta del dueño.

**1. Domingo Ritual.**

- *Se preguntó:* el documento del programa define el domingo como día sagrado de cierre y descanso («Formulario de
  cierre de domingo»). Pero desde D-192 las semanas de rocas se cuentan desde el día de inicio de cada persona, así que
  para quien no empezó un lunes su semana ya no termina el domingo. Opción A: volver a semanas de lunes a domingo para
  todos (la «semana 14» se sigue evitando sumando los últimos días a la semana 13). Opción B: mantener las semanas por
  día de inicio y mover el ritual de cada persona al último día de SU semana.
- *Decidió:* **opción A.** Semanas de lunes a domingo para todos: el domingo cierra la semana de todos, como dice el
  documento del programa, y los últimos días del programa se suman a la semana 13, así que no hay «semana 14». Corrige
  D-192; lo implementa D-203 (en curso).

**2. Pacto atrasado.**

- *Se preguntó:* el sistema no distingue «se lo salteó por un ajuste de día» de «no lo firmó a tiempo». ¿Está bien que
  a quien no firmó a tiempo también le aparezca el pacto viejo pendiente?
- *Decidió:* **sí, queda como está.** A quien no firmó a tiempo también le aparece el pacto viejo pendiente, para
  firmarlo después. Confirma D-193. Queda el riesgo técnico que anotó D-193: con dos o más pactos pendientes, un doble
  envío podría firmar el siguiente sin su firma dibujada (hoy ni la app ni la web llaman a ese endpoint).

**3. Bienvenida del grupo.**

- *Se preguntó:* hoy la bienvenida en el chat del grupo la firma automáticamente el mentor (D-191). ¿También debería
  salir del programa, como la del soporte?
- *Decidió:* **sí.** Sale del programa, no del mentor, con un texto amigable (D-204, en curso). Las dos bienvenidas, la
  del soporte y la del grupo, quedan detrás del interruptor `BIENVENIDA_ACTIVA`, apagado hasta que el dueño apruebe los
  textos.

**Sigue abierto** (todavía no se preguntó): si el aviso de acompañamiento del servidor debe salir como banner en Android.
Hasta decidirlo, su canal propio queda apagado (D-188).
