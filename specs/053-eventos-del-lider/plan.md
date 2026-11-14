# Plan — spec 053

Backend: `TipoEvento`, `TipoEventoJpa`, `ReglasPorTipoEvento`, `QuienConvoca.tiposDe`, migración `V193` (valor del enum de Postgres); `ColoresDelSemaforoAdapter` ya lee la ventana
que termina ayer (se fija con prueba). App: `TIPOS_DE_EVENTO`/`rotuloDelTipo`, tipo fijo en `FormularioDelEvento`, texto de ayer en `ParaQuienEs`, y la repetición
(`repeticionDelEvento.ts` + `RepetirPorDias.tsx`, el cuerpo en `armarCuerpo`). Sin IA: los valores por defecto (día de la fecha, 4 semanas) son la «inteligencia».
