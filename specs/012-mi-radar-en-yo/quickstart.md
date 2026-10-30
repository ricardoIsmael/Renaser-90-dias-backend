# Quickstart: probar Mi Radar en Yo

**Siempre en local, con base clonada y build debug. Nunca contra producción.** Antes de levantar el backend o el
emulador, pedir el turno de la máquina a las otras sesiones (ListAgents + SendMessage) y avisar al terminar.

## Prerrequisitos

- Backend del worktree `.claude/worktrees/mi-radar` con una base clonada propia (por ejemplo, `renaser_mi_radar`
  en `DB_URL` del `.env` del worktree), para que V119 no toque la base de master.
- App: build debug ya instalado en `Pixel_6` y Metro desde el worktree de la app (solo cambia JavaScript).

## Verificación automática

- Backend: `./mvnw clean verify`, leyendo las líneas `Tests run:` de surefire y failsafe.
- App: `node node_modules/typescript/bin/tsc --noEmit` y `node node_modules/jest/bin/jest.js`, o `npm run verificar`
  si el harness ya está en master.

## Escenarios E2E (Maestro y mobile-mcp)

| # | Cuenta y día | Paso | Esperado |
|---|---|---|---|
| 1 | Aprendiz, día 3 | Abrir la app a una hora entre 08 y 19 | El Código Renaser se abre solo, como hoy. Yo no tiene la fila «Mi Radar» |
| 2 | Aprendiz, día 10 | Yo → Mi Radar | Interruptor apagado, sin «Registrar» ni horarios |
| 3 | Ídem | Encender, dejar 09/14/20 y guardar | Fila «Encendido · 09:00, 14:00 y 20:00». `dumpsys alarm` / notificaciones programadas: solo esas 3 horas |
| 4 | Ídem | Marcar 12 horas e intentar la 13.ª | No deja, y dice «Puedes elegir hasta 12» |
| 5 | Ídem, a una hora elegida | Tocar el aviso | Abre Mi Radar con el formulario; al registrar: «Prevista HH:00 · respondida HH:MM» |
| 6 | Ídem | Abrir la app a una hora elegida | Hoy sin tarjeta del Radar y sin formulario automático |
| 7 | Ídem | Apagar | Sin avisos programados; el historial sigue |
| 8 | Ídem, apagado | `POST /api/v1/radar` con curl | 409 |
| 9 | Ídem | `PUT /mi-radar` con la hora 2 o 24, o 13 horas | 400 |
| 10 | Mentor del grupo | Ficha del aprendiz | Estado de Mi Radar y registros del día 10 |
| 11 | Líder / Admin / Alquimista | Ficha de la persona | Mismo bloque |
| 12 | Mentor de otro grupo, Soporte TI | Endpoint de liderazgo | 403 |
| 13 | Aprendiz, día 7 | Tras abrir la app | Notificación de invitación programada para el día 8 a las 09:00 |
| 14 | Puntos, racha y semáforo | Antes y después de los registros del día 10 | Iguales |
| 15 | App nueva contra backend de master | Yo, día 10 | Sin fila «Mi Radar»; días 1 a 7 iguales |
| 16 | Aprendiz, día 10 | Encender con 09/14 para todos los días, el martes solo la 11 y el domingo «Sin Radar»; guardar | `GET /radar/mi-radar` devuelve `dias` con el martes (2) y el domingo (7); el teléfono avisa el martes a las 11 y el domingo no avisa |
| 17 | Ídem, un domingo | `POST /api/v1/radar` con curl | 409 «Hoy no tienes Mi Radar»; el martes a las 11:00 registra con `horaPrevista` 11 |
| 18 | Ídem, domingo 22:00 de Lima (lunes 03:00 UTC) | `POST` con el domingo con horas propias | El servidor usa el domingo (día de la persona), no el lunes de UTC |
