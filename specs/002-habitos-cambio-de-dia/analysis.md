# Análisis previo a implementar — 2026-10-17

Revisión de spec, plan, tareas y constitución: 0 hallazgos críticos, 0 requisitos sin tareas, 0 marcadores pendientes. FR-001→T003–T005; FR-002/008→T006/T007; FR-003/004/006→T008/T009; FR-005/007→T005/T010; validación transversal T011/T012.

El cambio técnico no necesita decisiones de negocio nuevas: aplica el cierre ya confirmado. El modo compatible es deliberadamente eventual y está diferenciado del cierre preciso con contrato nuevo. El snapshot nace de una única proyección y detecta una respuesta que ya venció. No cambia la aceptación de sesiones especiales ni el cálculo de puntos.

La ruta nueva conserva permiso USE_APP y actor propio; tests negativos y de suspendido incluidos. No hay migraciones. La spec se guarda una sola vez en backend y se enlaza desde app.
