# Informe E2E — Hábito GIMNASIO con su ranking (D-266)

Fecha: 2026-10-13. Pedido del dueño: «tenemos que activar el hábito de gimnasio y debe salir como ranking también,
igual que los demás: una foto y queda nada más». **Nunca contra producción** (regla del dueño): todo local.

## Entorno

| Pieza | Valor |
|---|---|
| Backend | worktree `claude/gimnasio`, puerto 8092, variables de la configuración de IntelliJ del dueño; V98 y V99 aplicadas al arrancar |
| Base | `renaser_guia` (clon local en `renaser-db`, 5433), que venía en V97 |
| App | APK debug ya instalado (mismo nativo; el cambio es solo JavaScript) con Metro del worktree `claude/gimnasio` del frontend y `EXPO_PUBLIC_API_URL=http://10.0.2.2:8092` |
| Dispositivo | emulador `Pixel_6`, manejado con `mobile-mcp` |
| Cuenta | E2E APRENDIZ1 (`~/.config/renaser-e2e/habitos-por-rol.json`, fuera del repo) |

Capturas: `~/Imágenes/e2e-2026-10-13/gimnasio/`.

## Resultado por caso

| Caso | Resultado | Evidencia |
|---|---|---|
| Migraciones | OK | `flyway_schema_history`: 98 y 99 con éxito; hábito `GIMNASIO`, `GYM`, activo, opcional, foto `OBLIGATORIA` |
| Barrido del día | OK | 13:02 Lima: «80 participante(s) con el dia recien armado»; 80 registros de GIMNASIO `PENDIENTE` y opcionales, incluidos mentores y admins con programa |
| Tarjeta en Training → Cuerpo | OK | «Opcional», mancuerna, 07:00, «Subir». `GYM2-tarjeta-gimnasio.png` |
| «Subir» abre la cámara directo | OK | Pide el permiso de cámara la primera vez y abre la cámara sin pasar por el selector de cuatro formas |
| Subir la foto | NO SE PUDO (entorno) | «No se pudo registrar tu foto.» `upload-url` respondió 200; el `PUT` a S3 falló porque el bucket local `s3-renaser90dias` está deshabilitado (`AllAccessDisabled`). Afecta a toda foto en local, no al gimnasio. `GYM3-pantalla-partida.png` |
| Completar | OK (por API) | `POST /complete` como APRENDIZ1 → `COMPLETADO`, 10 puntos. La tarjeta pasa a cumplida, tachada, «1 día» de racha y «Ver». `GYM8-gimnasio-cumplido.png`. Ver E-608 |
| Corte del ranking | OK | `POST /admin/ranking/snapshots` → tipos `LEAGUE, CELL, GENERAL, KILOMETROS, GIMNASIO`, sin fallas; `GET /ranking` trae `gimnasio` con E2E APRENDIZ1 = 1 |
| Pestaña «Gimnasio» en Comunidad → Ranking | OK | Las cuatro pestañas entran en una línea; «Días de gimnasio desde tu Día 1», podio con «1 día», «Tu posición #1 · 1 día de gimnasio»; quien tiene 0 no aparece. `GYM6-ranking-gimnasio.png` |
| Barra de pestañas tras la cámara | OK | Al cancelar la cámara y volver, la barra está. Una vez quedó escondida tras la subida fallida + «Cancelar»; no se reprodujo |

## Hallazgos

- **E-608 (abierto):** el servidor completa un hábito de foto obligatoria sin foto; la regla solo la cumple la app.
- **Al desplegar:** quien ya tenía armado su día antes de V99 ve GIMNASIO desde el día siguiente (el barrido arma el
  día una sola vez). Es lo mismo que pasa con un hábito nuevo del catálogo.
- **Entorno local:** sin un bucket de S3 válido en la configuración de IntelliJ no se puede probar ninguna foto.

## Segunda pasada (13/10, tarde): fotos reales, E-608 y la suite de Maestro completa

Sobre la integración local de las tres ramas que suben juntas (`claude/gimnasio`, `claude/foto-obligatoria` con E-608 y
`claude/s3-local`), con `./mvnw clean verify` en verde (6279 unitarias, 409 de integración) y las fotos en el S3 local
(`fotos-local`, `AWS_S3_ENDPOINT=http://localhost:9002`, `adb reverse tcp:9002`).

| Caso | Resultado | Evidencia |
|---|---|---|
| GIMNASIO con foto real | OK | La cámara se abre directo, la foto se sube al S3 local, se confirma la evidencia y recién después se completa: `COMPLETADO`, 10 puntos, 1 evidencia en la base. La app registra sola al aceptar la foto (sin pregunta) |
| KILÓMETROS con foto y 3,5 km | OK | `COMPLETADO`, `valor_medido = 3.50`, 1 evidencia |
| E-608 con la app | OK | El orden de la app (subir → confirmar → completar) pasa la regla nueva; sin foto, `POST /complete` responde 400 (prueba `KilometrosDiariosIT.sinCapturaNoSeCompleta`) |
| Suite de Maestro (`.maestro/regresion/`, 11 flujos) | OK | Kilómetros con foto, publicar en el Muro, chats del admin y del aprendiz, Yo y Ajustes, Planificar DESPERTAR a las 22:00, gimnasio con foto, ranking con la pestaña Gimnasio, y Administración (entrar y volver, Personas y vuelta, crear grupo con el teclado). Corrida completa: 9 de 11; los dos restantes (ajustes del flujo, no de la app) en verde al repetirlos. Capturas: `~/Imágenes/e2e-2026-10-13/gimnasio/maestro/` |

Notas de la suite: Maestro 2.11 no podía escribir en este emulador (animación de bienvenida trabada, Maestro #3667);
la sesión la abre `correr.sh` por adb. Datos de prueba ajustados en la base clonada: las cuentas `habrol-*` se sumaron al
chat global (se habían creado por SQL, sin el alta) y el límite de intentos de login se reinicia solo en el Redis local.
