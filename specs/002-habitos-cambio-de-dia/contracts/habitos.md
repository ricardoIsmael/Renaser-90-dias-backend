# Contratos

`GET /api/v1/habit-tracks/today/snapshot`: `USE_APP`, autoservicio del actor autenticado; sin parámetro de participante.

```json
{"fechaLocal":"2026-10-16","zonaHoraria":"America/Lima","cierraEn":"2026-10-17T05:00:00Z","ahoraServidor":"2026-10-17T04:59:59Z","tracks":[]}
```

Fecha/zona salen de la misma consulta que autoriza y obtiene los registros; la generación de respaldo conserva su contexto. El controller invoca un caso de uso y mapea. `Cache-Control: no-store` para esta lectura del día. La respuesta vieja `/today` sigue siendo array.

Frontend: solo 404 del nuevo endpoint activa fallback a `/today`; red, 403 o esquema inválido no se convierten en éxito vacío. Backend anterior: contexto null, refresco periódico/foco/actividad y preflight antes de mutar.

Todos los pasos genéricos que escriben sobre un track verifican el ID en el listado actual; ausencia → error «Tu día cambió» y evento de invalidación. Falta de conexión → error claro sin escritura. El mensaje 409 «Este hábito era del …; ese día ya cerró y no se puede registrar.» provoca esa misma recuperación. Nunca se reintenta con un nuevo ID ni se emite `habitoCumplido` para el rechazo.
