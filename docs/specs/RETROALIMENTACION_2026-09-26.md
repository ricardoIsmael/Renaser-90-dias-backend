# Spec — Retroalimentación del 26/09/2026 (entrega: viernes 02/10/2026)

Pedido del dueño, con la retroalimentación de administradores, docentes y mentores. Este documento es
la fuente de verdad del trabajo hasta el viernes. Cada ítem dice qué se hace, cómo se acepta y cómo se
prueba de punta a punta.

## 0. Reglas de este trabajo (no se negocian)

1. **Nada que ya funciona se rompe.** Cada entrega corre `./mvnw clean verify` (backend), `tsc` + Jest
   (app) y la batería del acompañante completa (125+ preguntas) contra la ronda anterior.
2. **Pruebas e2e siempre**: emulador + base local + backend local, verificando en la pantalla y en la
   base. Un ítem no se da por hecho sin su prueba e2e.
3. **Sin tablas nuevas ni datos duplicados.** Se reusa lo que existe. Si algo del esquema es inevitable,
   se justifica acá (hoy solo aparece un valor de enum, ver E-3).
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

## 3. Administración más simple (30–60 años)

| # | Cambio | Aceptación |
|---|---|---|
| A-1 | Letra ≥ 16 px en el cuerpo de admin y mentoría; botones 48–56 px; destructivos con confirmación | Prueba que falla con `fontSize < 16` en esas carpetas |
| A-2 | Palabras simples: Cohorte→Generación, Recepción→Grupo de bienvenida, Staff→Equipo, Cumplimiento→Cuánto cumplió | Glosario aplicado |
| A-3 | Administración sin secciones repetidas; "Pendientes" + "Necesitan atención" | Menos scroll |
| A-4 | Solicitudes con "Ver más" (hoy solo carga la primera página) y aprobar en un toque | Se aprueba la solicitud 21 |
| A-5 | "Asignar mentor" con confirmación ("Asignar a X como mentor de Y") | No asigna por error |

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
| E-6 | Formulario mínimo para crear/editar/cancelar eventos (admin; mentor para su grupo) | Aprendiz recibe 403 |
| E-7 | "Voy" programa la alarma local del evento; "No voy" la cancela | Suena en el emulador |
| E-8 | "Mi agenda": lista de 7 días con eventos, hábitos con hora, acciones y color del semáforo (endpoints existentes) | — |
| E-9 | Canales de Android por tipo (Hábitos / Eventos / Mi grupo) para silenciarlos por separado | Requiere APK |
| E-10 | **Alarma con sonido propio** (Despertar, 04:50) — requiere cambio nativo y APK; **decisión del dueño** | — |
| E-11 | Acompañante: casos de eventos/semáforo/recordatorios en la batería; no confundir la semana del semáforo con la de objetivos | Batería OK |

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

## 6. Pendientes que no entran esta semana

- **Costo del acompañante:** enviarle solo las herramientas que sirven para cada pregunta (hoy 40 en
  cada llamada, ~15.000 tokens). Pendiente por decisión del dueño.
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

## 8. Plan por día

| Día | Trabajo |
|---|---|
| Sáb 26 | Spec (este documento) y arranque de los frentes sin decisiones pendientes |
| Dom 27 – Lun 28 | Velocidad V-1..V-8 · Semáforo+Admin S-1..S-5, A-1..A-5 · Grupos G-2..G-6 · Eventos backend E-1..E-3 |
| Mar 29 – Mié 30 | Eventos app E-4..E-8 · Semáforo S-6..S-9 · e2e de cada frente |
| Jue 01 | Batería completa, regresión, correcciones |
| Vie 02 | Subida a producción y verificación |

## 9. Decisiones del dueño (26/09)

1. **V-9 — Sí.** CloudFront pasó a `PriceClass_All` el 26/09 (distribución E3O4M4W7JW3TJQ, cuenta
   302277511407). Verificar que Perú use el POP de Lima.
2. **E-10 — Sí.** En **Yo** una sección "Alarmas" para personalizar: activar o no cada alarma
   (Despertar, eventos), elegir el sonido y la hora. Sin tablas nuevas (preferencias existentes y el
   teléfono). El sonido propio requiere APK con el plugin de notificaciones.
3. **E-6 — Crea eventos el Alquimista** (y el Admin, que puede todo; supuesto a confirmar). El mentor
   ya no crea eventos.
4. **E-4 — Temas:** sin interruptor de "Hábitos" (ya se configura en cada hábito); **sí** "Eventos y
   clases". Los eventos se ven sobre todo en **Comunidad**.
5. **Meet / Drive:** el evento lleva un **link** que pega quien lo crea (Meet, Zoom o Drive) y el alumno
   ve un botón grande "Unirme". No se crean Meet automáticamente (exigiría OAuth de Google Calendar:
   más complejo y frágil).
6. **Semáforo:** se mantienen los colores, umbrales (≥80 verde, ≥60 amarillo) y palabras actuales; al
   dueño le gustan. Solo cambia lo de alrededor.
7. Pendiente de confirmar: que el ex-mentor conserve el chat con el aprendiz (G, riesgo R6).
8. **Eventos:** solo el Alquimista y el Admin crean, editan y cancelan eventos; el mentor ya no (D-186).
   Sus eventos de célula ya creados se quedan.
9. **Cierre semanal del semáforo (S-8):** no se toca. La semana es de sábado a viernes y cierra como
   ya está establecido. Un hábito del viernes cuya ventana cruza la medianoche queda solo documentado
   como borde (test de caracterización), sin cambiar la regla. Palabras del dueño: «son reglas que ya
   están establecidas, no confundamos eso».

## 10. Estado al 26/09 (noche) — qué está hecho y qué falta

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
| V-5..V-7 velocidad backend y medición | ✅ | Pruebas; `Server-Timing` visible en local |
| V-8 memoria del contenedor | ✅ código · ⏳ vigilar `docker stats` después de subir | — |
| V-9 CloudFront `PriceClass_All` | ✅ aplicado · ⏳ confirmar nodo de Lima | AWS |
| S-1..S-7, S-9 semáforo mentor/admin | ✅ colores y palabras sin cambios | Pruebas + emulador + Playwright |
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
2. **Aviso con voz** («tu hábito está por empezar»). Hoy el aviso es una notificación con sonido (del teléfono o la
   campana). Opción liviana: un audio con voz grabada como sonido del canal (igual que la campana, solo APK nuevo);
   dice una frase fija y el nombre del hábito va en el texto. Que diga el nombre en voz con la app cerrada exige un
   módulo nativo (texto a voz), más pesado.
3. Si el teléfono no deja dar el permiso «Alarmas y recordatorios», la alarma igual suena, pero Android puede demorarla
   hasta ~1 h.
