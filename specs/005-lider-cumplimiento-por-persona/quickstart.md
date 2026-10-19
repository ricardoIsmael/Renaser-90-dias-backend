# Quickstart: validar el cumplimiento por persona para el líder

Todo en **local**: base clonada propia y build debug. Nunca producción. Antes de levantar un backend o un emulador,
avisar a las otras sesiones y turnarse: la máquina no aguanta dos backends con el emulador.

## Preparación

1. Base clonada `renaser_lider005`, migrada a V109, con:
   - un líder;
   - dos mentores con grupo;
   - un grupo con aprendices rojo, amarillo, verde y sin datos;
   - una guía con programa propio;
   - una cuenta de prueba en el grupo;
   - un grupo de recepción.
2. Datos de una semana para una aprendiz, con:
   - un hábito con foto y «¿Qué sentiste?»;
   - la Pastilla;
   - la Audioterapia;
   - el post diario publicado en el Muro;
   - una acción del día con foto;
   - un día en pausa.
3. Backend del worktree en un puerto libre, acordado con las otras sesiones. APK debug de la rama
   `005-lider-cumplimiento-por-persona`.

## Escenarios

| # | Pasos | Resultado esperado | Requisitos |
|---|---|---|---|
| 1 | Líder: Hoy → Semáforo por grupos → grupo | Lista ordenada de rojo a verde, «no se mide» al final; la guía aparece; la cuenta de prueba no aparece | FR-001, 002, 006, 007; SC-006 |
| 2 | Tocar a la aprendiz | Semana de sábado a viernes; el día en pausa dice «En pausa», no ✗ | FR-009, 019 |
| 3 | Abrir cada evidencia | Foto con su texto, respuestas de la Pastilla, texto de la Audioterapia, publicación del Muro y foto de la acción | FR-011, 012; US2 |
| 4 | Comparar día por día con Administración → ficha de la misma persona | Mismos estados y mismas evidencias | SC-002 |
| 5 | Semana anterior y volver | Semana histórica; no hay flecha hacia el futuro | FR-010 |
| 6 | «Avisar a su mentor», con alerta enviada por chat | La observación nombra a la persona y la semana; el mentor la recibe en el chat | FR-014 |
| 7 | «Escribirle» | Abre el 1 a 1 vacío | FR-015 |
| 8 | Con `curl` como líder: completar un registro ajeno, revisar una evidencia, reasignar, cambiar el rol | 403 en todo | FR-003; SC-003 |
| 9 | Reloj del backend a las 20:05 en la zona del mentor | Un solo aviso «Hoy revisó a …», sin repetir nombres; un mentor sin consultas no recibe nada; el aviso no se repite a la hora siguiente | FR-017; SC-004 |
| 10 | Admin abre fichas desde esta vista | Queda registrado; el mentor no recibe el aviso por eso | FR-016, 017 |
| 11 | Cortar la red al cambiar de persona o de semana | Carga o error con «Reintentar»; nunca los datos de la anterior | FR-019; SC-005 |
| 12 | APK nuevo contra el backend de master sin 005 | Las tarjetas no se abren; nada se rompe | FR-021 |
| 13 | Ficha de administración y del mentor | El post diario muestra «Ver publicación» | R-6 |

## Comandos

- Backend: `TZ=UTC ./mvnw -B clean verify`, leyendo las líneas `Tests run:` de surefire y failsafe.
- App: `node node_modules/typescript/bin/tsc --noEmit` y `node node_modules/jest/bin/jest.js`.
- E2E: la suite de Maestro y mobile-mcp en el emulador, con capturas en `specs/005-lider-cumplimiento-por-persona/e2e.md`.
