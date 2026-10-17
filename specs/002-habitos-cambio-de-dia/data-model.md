# Modelo de datos

Sin persistencia nueva ni migraciones.

`DiaDeHabitos`: fechaLocal (YYYY-MM-DD), zonaHoraria (IANA), cierraEn (ISO Instant de la siguiente medianoche en esa zona), ahoraServidor (ISO Instant), tracks (array actual). Todos los tracks pertenecen a fechaLocal; un snapshot puede haber vencido durante la respuesta y debe releerse. La lista vacía conserva contexto.

Frontend: `ContextoDelDia` conserva fecha y cierre del snapshot más una referencia local de reloj para medir el tiempo transcurrido; no redefine las reglas. Estado: carga inicial → vigente → vencido/actualizando → vigente o error con reintento. Una respuesta de generación anterior no puede restablecer vigencia. Un formulario mantiene su ID original incluso después de la actualización.
