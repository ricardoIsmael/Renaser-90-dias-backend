# Quickstart: validar 008 en local

Nunca contra producción. Turno de máquina avisado a las otras sesiones. **Sin APK nuevo**: build debug ya instalado (el de la 007) y Metro del worktree.

## Preparación

- Base clonada `renaser_alarmas`, cuenta `e2e-libre05@renaser.test` (credenciales en `~/.config/renaser-e2e/`).
- Backend del worktree en 8097/8098 con V111 aplicada. Metro con `EXPO_PUBLIC_API_URL=http://10.0.2.2:8097`.

## Pruebas automáticas

- Backend: `TZ=UTC ./mvnw clean verify` en verde (dominio, adaptador por capas con la fila de solo aviso, endpoint con 403, migración).
- App: `tsc --noEmit` y Jest completo en verde (estado del editor, qué guardar, esquema diario o por día, identificadores, medianoche, rearmado `weekly`, agrupado).

## Escenarios (emulador con mobile-mcp, captura por caso; servidor en la base)

| # | Pasos | Esperado |
|---|---|---|
| E1 | Gimnasio 07:00 con «10 min antes». Marcar S y D, elegir «30 min antes» sin mover la rueda | Botón «Guardar aviso · S, D». S y D muestran la campana «30′»; L–V sin campana |
| E2 | Reabrir el editor y marcar S | Recordatorio muestra «30 min antes» |
| E3 | Marcar V y S (avisos distintos) | Ninguno marcado y «Estos días tienen avisos distintos» |
| E4 | `dumpsys alarm` tras E1 | `WEEKLY` sábado y domingo 06:30, L–V 06:50; ninguna a 06:50 los fines de semana. Tipo despertador (`flags=0x3`) |
| E5 | J a las 09:00 «a la hora» | El jueves una sola alarma a las 09:00 (corrige el bug de la hora general) |
| E6 | D con «Sin aviso» | El domingo sin alarma; base: sin aviso de inicio del servidor ese día |
| E7 | Quitar S («como los demás») | S vuelve a 07:00 y 10 min; alarmas del sábado reprogramadas, sin duplicados |
| E8 | Cambiar el general a «a la hora» sin días marcados | Días sin aviso propio pasan a 07:00; S y D conservan el suyo |
| E9 | `force-stop` y abrir la app | Vuelven todas las alarmas por día (007) |
| E10 | Cerrar sesión | 0 alarmas de Renaser |
| S1 | Base: S con 30 min; barrido de un sábado (reloj de prueba o fecha cercana) | Aviso de inicio a 30 min de la hora del sábado |
| S2 | `PUT …/weekdays/SATURDAY` con hora (como el APK viejo) | El aviso propio del sábado sigue en la base |
| S3 | Hoy con candado | No se puede cambiar el aviso de hoy |

## Maestro

Por la regla al juntar ramas: solo los flujos que pasan por el editor de Planificar (`08-planificar-despertar`) y Yo → Alarmas (`07-yo-y-ajustes`), más uno nuevo para el aviso por día si hace falta.
