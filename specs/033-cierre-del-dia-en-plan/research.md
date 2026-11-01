# Research: «Cierra tu día» pasa a Plan

## D1. Qué se reutiliza y qué se escribe

- **Se reutiliza tal cual**: `HojaDelCierre` y `useHojaDelCierre` (preguntas, reglas, guardado, 409, fundido, `tacto.logro()`), `useCierreDeHoy`, `useCierresAnteriores` + `lecturaDeCierres`, `cierreApi`, los textos y las fichas «Cierres del día».
- **Se escribe**: la tarjeta de Plan (en el estilo de las tarjetas de Plan: medallón + título + subtítulo + chevron, como «Tu 80/20») y la fila de días de la misma tarjeta.
- **Alternativa descartada**: reescribir la hoja «al idioma de Plan». La spec dice «se mueve, no se rehace» y las pruebas de la 017 (SC-003) deben seguir sin tocarse.

## D2. Dónde va la tarjeta

- **Decisión**: bajo «Acciones que mueven la meta» cuando hay Mapa (la persona ve sus acciones y cierra), y bajo «Primero tu Mapa de Renacimiento» cuando no lo hay (decisión 033-3 A). Es la **misma** tarjeta, independiente del selector de eje.
- **Cómo**: `PlanDelEje` recibe una ranura (`cierre`) que dibuja después de las acciones; `PlanScreen` la dibuja sola en el caso sin Mapa. Una sola pieza, dos lugares, ninguna lógica repetida.

## D3. Qué decide que haya tarjeta

- Lo mismo que decidía la fila de Training: hay lectura (`listo`) del cierre de hoy y el interruptor `plan` de Soporte TI lo permite (`useFuncionalidad(CLAVES.plan)`, prendida y disponible). Mientras no se sabe (`sin-leer`), o ante un 400/403/404 o una respuesta que no es un cierre (servidor anterior), **no se dibuja**: nada de parpadeos. Un fallo de red no la muestra y se reintenta al volver al foco.
- Plan apagado por Soporte TI: Plan ya muestra su aviso de mantenimiento; la tarjeta no aparece (y Training tampoco tiene una).

## D4. El ícono de eje de cada pendiente (FR-003)

- `GET /rocks/day-closure` trae las acciones sin su eje. **El servidor no cambia**, así que el eje sale de la lectura de hoy que Plan ya hace (`useAccionesDeHoy` → `RocaDiariaApi.eje`), por id. Una acción que no figura ahí (por ejemplo una de otro día) se dibuja con el ícono neutro `target`: nunca se inventa un eje.
- Lo pone `utils/ejeDeLasAcciones` (puro) y `HojaDelCierre` suma una prop opcional `ejeDe?: (idDeLaAccion) => EjeObjetivo | null`; sin ella la hoja es la de siempre.

## D5. Los días anteriores en la misma tarjeta (decisión 033-1 A)

- La semana de lunes a domingo, desde el Día 1 (nunca antes) hasta ayer; hoy es la fila de arriba de la tarjeta. **Un día se lee al tocarlo**, una vez por fecha y cuenta (`useCierresAnteriores`); nada se pide al abrir Plan ni al volver al foco.
- Un día leído dice «Cerrado» (con la hora si hubo) o «Sin cerrar»; tocar un día **nunca ofrece cerrarlo** (un día que terminó no se cierra tarde, D-259) y, si estaba cerrado, muestra lo que se respondió, solo de lectura. Sin lectura (404/403/400) o con error: una línea y «Reintentar»; ningún estado inventado.
- Se reutiliza la memoria de la sesión de la spec 017 (`useCierresAnteriores`), que ya limpia al cambiar de cuenta o de semana.

## D6. «Ver mañana en Plan» (decisión 033-2 A)

- Solo mueve. Al guardar, el pie de la hoja ofrece «Ver mañana en Plan» **todos los días, también el domingo**, siempre que la persona vea las acciones (con Mapa activo). Cierra la hoja y lleva el scroll de Plan hasta «Acciones que mueven la meta», que ya muestra lo que viene. Sin Mapa no se ofrece (no hay acciones que ver).
- Ningún automatismo: «Hacerla a otra hora» y «Dejarla» siguen siendo solo un registro.

## D7. Training

- Se borran la fila «Cierra tu día», las filas «Cerrado»/«Sin cerrar» de los días anteriores, el ítem `cierre` de la lista de hoy (`ListaDelDia.cierre`) y todo lo que solo existía para ellas. Lo demás de la lista por hora no cambia (SC: sin hueco).
- `TrainingScreen` deja de pasar `cierrePrendido`. Las pruebas de Training que hablaban del cierre pasan a ser las de Plan (los casos de quien trabaja de noche se conservan).

## D8. Compatibilidad

- APK anterior (cierre en Training) + servidor actual: sigue funcionando; un cierre hecho en cualquiera se ve en el otro y en las fichas (mismas rutas y datos). APK nuevo + servidor anterior a la 017: ni tarjeta ni nada, como hoy.
