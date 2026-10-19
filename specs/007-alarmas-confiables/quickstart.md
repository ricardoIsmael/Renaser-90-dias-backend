# Quickstart: validar 007 en local

Nunca contra producción. Los recursos pesados (emulador, backend, Metro, `clean verify`) se piden por turno a las otras sesiones y se liberan al terminar.

## Preparación

- Base clonada propia: `renaser_alarmas` (ya existe; si no, `pg_dump renaser_guia | psql renaser_alarmas`).
- Backend del worktree `alarmas-confiables` en el puerto 8097 / 8098 con `DB_URL=…/renaser_alarmas`.
- App: build debug del worktree `alarmas-confiables` (el parche y el módulo cambian código nativo: APK nuevo), Metro con `EXPO_PUBLIC_API_URL=http://10.0.2.2:8097`.
- Cuenta de prueba: `e2e-libre05@renaser.test`. Su contraseña se copió en la base clonada; las credenciales viven en `~/.config/renaser-e2e/`.
- Para poner un hábito a pocos minutos: `preferencias_horario` (`hora_disparo`, `recordatorio_activo`, `minutos_recordatorio`, `antelaciones_recordatorio`) y forzar el rearmado reabriendo la app.

## Pruebas automáticas

- Backend: `TZ=UTC ./mvnw clean verify` en verde, leyendo `Tests run:` de surefire y failsafe.
- App: `node node_modules/typescript/bin/tsc --noEmit` y `node node_modules/jest/bin/jest.js` en verde.

## Escenarios en el emulador (mobile-mcp, con captura por caso)

| # | Pasos | Esperado |
|---|---|---|
| A1 | Hábito con avisos a +3 y +5 min; `adb shell dumpsys alarm` | Alarmas de Renaser como `ALARM_CLOCK` / con `AlarmClockInfo`; `adb shell dumpsys alarm \| grep -i "next alarm"` muestra Renaser |
| A2 | `adb shell dumpsys deviceidle force-idle`, esperar | Las dos suenan dentro de ±60 s, incluida la segunda a 2 min de la primera |
| A3 | Tocar la alarma | Abre el hábito en Training |
| A3b | Bajar ajustes rápidos y tocar la «próxima alarma» de Renaser | Abre Renaser (FR-003) |
| A4 | Yo → Alarmas → «Probar el sonido» | Suena y **no** aparece como próxima alarma |
| A5 | Sin permiso «Alarmas y recordatorios» (`appops set … SCHEDULE_EXACT_ALARM deny`) | Se programa como hoy y la app muestra la línea del permiso |
| A6 | Cerrar sesión | No queda «próxima alarma» de Renaser |
| B1 | `adb shell am force-stop com.renaser.app`, abrir la app | Se reprograman y aparece la hoja «el teléfono cerró Renaser…» |
| B2 | Volver a abrir el mismo día | No vuelve a aparecer |
| B3 | Quitar de recientes (Android sin capa) y abrir | No aparece la hoja |
| B4 | Tocar «Arreglarlo» en el emulador (marca Google) | Abre la lista de batería o los ajustes de Renaser sin error |
| B5 | Yo → Alarmas | Guía genérica (emulador); con `Platform.constants` simulado en Jest, la de cada marca |
| C1 | Apagar por hoy un hábito con hora a +14 min | El servidor no crea aviso en los barridos siguientes |
| C2 | Hábito con aviso de inicio ya creado; cambiar su hora a +14 min | Exactamente un aviso nuevo para la hora nueva |
| C3 | Revisar `notificaciones` | Títulos con tildes; cuerpo «…te toca a las HH:mm…» |
| C4 | Prueba de integración con transporte falso (no se manda nada a Expo con tokens inventados) | El mensaje armado lleva `tag` = `habito-{id}-inicio`; en el teléfono, la alarma de la antelación más temprana tiene `identifier` `habito-{id}-inicio` (`getAllScheduledNotificationsAsync`) |
| C5 | Detener el backend con un evento en el outbox y arrancar después de su hora | No sale el push vencido |

## Maestro

Correr la suite completa de `.maestro/` (regresión), con las cuentas por `-e`.

## Honor real (SC-002)

1. Instalar el build de prueba apuntando al backend local por la red (IP LAN), nunca producción.
2. Programar un hábito a +5 min, quitar la app de recientes y comprobar si suena. Anotar `adb logcat` del motivo de salida (`motivo` del módulo).
3. Aplicar la guía (Inicio de aplicaciones), repetir y confirmar que suena.
4. Anotar modelo, versión de MagicOS y Android y el resultado en la bitácora.

## Despliegue del backend

Antes de elegir la hora, contar los avisos que caerían en la franja (15 min el de inicio, 30 el de vencimiento) según las horas configuradas, y desplegar en la madrugada de Lima, cuando el conteo sea mínimo (research R6).
