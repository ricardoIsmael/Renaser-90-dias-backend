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
