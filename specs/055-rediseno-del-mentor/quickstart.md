# Guía de prueba — Spec 055 fase 1

## Antes
- Backend local con la base clonada `renaser_e2e_048` (`sin-senal.sh levantar` con `WORKTREE=<worktree del backend>`), build debug y emulador. Nunca producción.
- Cuentas: `e2e-mentor@renaser.test` (3 grupos; «Cupo [e2e-piso-1314]» con 9 aprendices de colores distintos), un líder (`e2e-lider@renaser.test`), un admin y un aprendiz (`e2e-aprendiz@renaser.test`, día 78, con evidencias).

## Escenarios
1. **Mis grupos** (FR-001–003): el mentor abre Yo → Mis grupos y ve 3 grupos con su cantidad a la derecha; con un mentor de un solo grupo entra directo.
2. **Mi grupo** (FR-004–009): orden rojo → ámbar → verde → sin datos; borde del avatar con el color; línea del motivo; el chat abre el 1 a 1; no hay lista de avisos.
3. **Ficha y semana** (FR-010–018): «Día 78 · …», 7 círculos (sábado a viernes) con color; tocar un día cambia los pilares; el primer pilar con pendientes va abierto.
4. **Mes** (FR-015–017): «Noviembre ⌄» despliega el mes; se puede ir hasta el mes del Día 1, no a futuros; elegir el 12 cambia los hábitos; volver a una semana vista no hace un pedido nuevo (se ve en el log del backend).
5. **Evidencia** (FR-019–022): el ojo abre la hoja con la foto, «1 de N», anterior/siguiente, cerrar con el gesto de volver.
6. **Notas** (FR-023–027): escribir, corregir hoy, intentar corregir una de ayer (en la base: `update notas_del_mentor set dia_del_autor = current_date - 1`), leer como líder y como admin, y comprobar que el aprendiz no las ve en ninguna parte.
7. **Por rol** (SC-006): un mentor de otro grupo recibe 403 en `/notas`, `/semana` y `/dias`.

## Comandos
- Backend: `scripts/verificar.sh` (con `pesado.sh`).
- App: `npm run verificar` y `npm run verificar -- --e2e .maestro/rediseno-del-mentor/`.
- Logs: `sin-senal.sh logs` (backend sin ERROR ni 5xx; logcat sin errores de JavaScript).
