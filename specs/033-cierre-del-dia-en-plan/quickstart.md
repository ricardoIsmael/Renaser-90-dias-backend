# Quickstart: comprobar «Cierra tu día» en Plan

Siempre en local, con base clonada y build debug; nunca contra producción.

1. `node node_modules/typescript/bin/tsc --noEmit` y `node node_modules/jest/bin/jest.js src/features/cierre src/features/training src/screens` en la app.
2. En el teléfono (con el backend de la spec 017): Plan → bajar hasta «Acciones que mueven la meta» → «Cierra tu día · 2 minutos» → responder lo pendiente → «Guardar mi cierre» → «Día cerrado» → «Ver mañana en Plan».
3. Training: la lista de hoy termina en «N hechos hoy» (o donde termine), sin «Cierra tu día», y un día anterior no tiene «Cerrado»/«Sin cerrar».
4. Plan → fila de la semana → tocar un día pasado: «Cerrado a las …» con lo respondido, o «Sin cerrar» sin botón.
5. Flujos de Maestro: `.maestro/cierre-en-plan/` (ver `tasks.md`).
