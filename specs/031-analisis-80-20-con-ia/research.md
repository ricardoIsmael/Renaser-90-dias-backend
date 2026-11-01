# Research: Análisis 80/20 con IA

Decisiones de diseño y por qué. Las preguntas de producto las respondió el dueño el 2026-10-31 (ver la spec); acá están las técnicas.

## 1. ¿Dónde vive el cálculo del 80/20?
**Decisión:** en `rocks` (`ArmadoDel8020`, pura, y `Pareto8020Service`), expuesto por `Pareto8020Finder`. Es ahí donde están las acciones, su impacto (`puntajeImpacto`) y su color de Pareto. `rag` solo lo pide.
**Alternativa descartada:** calcularlo en `rag` leyendo tablas de `rocks`: rompe el módulo y duplica la regla.

## 2. ¿Quién calcula las cifras: la IA o el servidor?
**Decisión:** el servidor, siempre. La IA recibe las cifras ya hechas y **solo redacta**. `ValidadorDelTexto` comprueba que todo número del texto esté entre los calculados (los de la foto, los números de los títulos de acciones y el número y los días del periodo), que no haya diagnóstico ni cuadros clínicos, que no compare con otras personas, que no cite entre comillas y que no pase de 3 emojis. Si no pasa, ese intento falla y cuenta como intento.
**Por qué:** una IA que suma o redondea inventa números (FR-009/FR-013 de la spec).

## 3. Las dos reglas del 20 % (031-1 A y B)
**Decisión:** se calculan las dos y la app dice las dos. **A:** las acciones verdes (la que más mueve en su eje); **B:** el `ceil(n/5)` de las cumplidas con más impacto, desde 5 cumplidas (con menos, `null`: no se habla de ella). Desempate de las de B: impacto desc, fecha, posición, id (determinista).
**«Hasta hoy»:** las acciones de días que todavía no llegaron cuentan como `porVenir`, no como no cumplidas.

## 4. Periodos: semanas 1–13 de Plan y qué pasa después del 90 (031-6 A, 031-8 B)
**Decisión:** la semana es la de `SemanaPrograma` (la 1 empieza el Día 1; la 13 llega al día 90 de Plan). Desde la 14 el calendario sigue **sin tope**: la 14 empieza el día siguiente al fin de la 13 y llega a su domingo; de la 15 en adelante, lunes a domingo. Los meses son bloques de 4 semanas con la 13 en el mes 3 (1–4, 5–8, 9–13) y luego 14–17, 18–21…
**No se usa la duración de la formación para cortar nada:** quien está en un Diplomado de 365 días o sigue después de su día final sigue teniendo su análisis mientras use la app (031-8 B). Lo calcula `CalendarioDelAnalisis` (puro) a partir de la fecha del Día 1 con ajuste (`ProgramaActivado.primeraFecha()`).
**Alternativa descartada:** un 90 fijo, o la duración de la formación como tope.

## 5. ¿Una tabla o recalcular al leer?
**Decisión:** una tabla (`analisis_80_20`). El texto de la IA cuesta dinero y no es reproducible: hay que guardarlo, y guardar también el estado de cada intento (PENDIENTE, LISTO, SIN_DATOS, AGOTADO, con `intentos` y `ultimo_intento_en`) para reintentar sin memoria de proceso. Las cifras que el texto cita se guardan en `cifras jsonb` (el análisis listo muestra lo que el texto dijo, no lo que dicen los datos hoy). Sin tablas auxiliares: `proximos_pasos` es `text[]` (1–3), el resto columnas de texto.
**Clave:** UNIQUE (`participante_id, periodo, inicio`). Una fila PENDIENTE se puede pisar con el upsert; una LISTO nunca (`ON CONFLICT … WHERE estado = 'PENDIENTE'`).
**FK:** `participantes_programa(usuario_id)` con `ON DELETE CASCADE`, como `registros_radar` y `rocas_diarias` (el análisis es del programa de esa persona, y se va con su cuenta: 031-9 A).

## 6. ¿Cómo se genera? (031-7 A)
**Decisión:** un barrido horario (minuto 15, `@SchedulerLock` `rag-generar-analisis-80-20`) que busca, para cada persona activa con Día 1, periodos **cerrados** sin análisis LISTO ni SIN_DATOS dentro de la ventana de puesta al día de 7 días; decide por persona con su zona. Sin llamada a la IA dentro de `@Transactional`; el evento `AnalisisListoEvent` se publica en un `@Transactional` corto (`RegistroDelAnalisis.registrar`) para el outbox de Modulith, y `notifications` lo escucha.
- **IA apagada / circuito abierto / tope global de 500 intentos en 24 h:** se **difiere** sin gastar un intento (no se pierde nada; el siguiente barrido lo retoma dentro de la ventana).
- **Sin datos mínimos** (5 acciones por semana en 3 días distintos, 15 por mes; configurables): fila SIN_DATOS y **no se llama a la IA** (FR-014).
- **Fallo del modelo o texto que no valida:** un intento más; hasta 3, espaciados 6 horas; después AGOTADO y la app dice «No pudimos generar el análisis» sin culpar a la persona.
**Alternativa descartada:** generar al abrir la hoja (031-7 B): costo impredecible y latencia en la mano de la persona.

## 7. ¿Qué sale hacia el proveedor? (privacidad, 031-3 A)
**Decisión:** cifras, títulos de acciones, números de energía del Código Renaser y una **muestra recortada** de lo escrito en el Código Renaser y en «Cierra tu día» (031-4 B), solo para que la IA vea patrones; el prompt ordena no citar, no diagnosticar, no nombrar emociones ni cuadros y tratar todo texto de la persona como dato, no como instrucción. Nunca correo, teléfono, documento, dirección, nombre ni id. El texto de la IA nunca entra al log (hay un test de escaneo de fuentes). La Política de Privacidad lo dice.

## 8. Quién ve qué (031-2 A)
**Decisión:** la persona, por `GET /analisis-80-20` (solo sobre sí misma: no hay parámetro para pedir a otra). Mentor y guía: `GET /mentor/groups/{g}/learners/{u}/analisis-80-20` (mismo guardia que su ficha). Líder, Administración y Alquimista: las rutas de `leadership` (con o sin grupo), por `AccesoDeLiderazgo`. **Las tres rutas de otra persona respetan `radar.fichas`** (404 `FUNCIONALIDAD_NO_DISPONIBLE` si está apagada para el rol: Administración y Alquimista no están gobernados) y la de liderazgo deja constancia en `consultas_de_ficha`. `AnalisisSoloSaleConLosPermisosDeLaFichaTest` (ArchUnit) frena una cuarta puerta.

## 9. Interruptor de Soporte TI
**Decisión:** `ia.analisis` (hijo de `ia`, normal, todos los roles, proceso SALTA): apagado, el barrido difiere y la app esconde el texto con el aviso de mantenimiento. Las lecturas siguen devolviendo las cifras (están en la funcionalidad `plan`), como pide FR-019.

## 10. ¿SER lo conversa? (US5)
**Decisión:** una herramienta de lectura, `consultar_analisis_80_20` (sin parámetros), que devuelve el último análisis LISTO de la semana y del mes. El modelo lo explica; **no inventa** ni cambia el plan (las escrituras de SER siguen siendo propuestas con confirmación, D-152).

## 11. Aviso (FR-024)
**Decisión:** un tipo de notificación propio (`ANALISIS_80_20`, V154) con **texto fijo** («Tu análisis de la semana está listo» / «…del mes…», «Entra a Plan y míralo.») y ruta `/plan/80-20?periodo=…&inicio=…`: el aviso nunca lleva el análisis. Dedupe por el origen del evento (id de la fila).

## 12. Aislamiento por formación
**Decisión:** la fila guarda `formacion` (de la persona al generar); las lecturas muestran solo la de su formación actual (`AlcanceDeFormacionFinder`). El mentor y el líder además pasan por `exigirQueLaVea` (404 de otra formación). Si una persona cambia de formación, sus análisis viejos quedan guardados pero no se ven en la nueva.
