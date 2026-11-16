# Plan — spec 051, WhatsApp confirmado en Yo

## Backend (módulo `users`, hexagonal)

- **Migración V192** `whatsapp_confirmados(usuario_id PK→usuarios CASCADE, numero UNIQUE con CHECK de forma, confirmado_en)`. Cabecera con el porqué (regla 04).
- **Puertos de entrada**: `WhatsappConfirmadoUseCase` (`estado`, `pedirCodigo`, `confirmar`). **De salida**: `CodigoDeWhatsappDeLaCuentaPort` (Redis, mecánica de `AlmacenCodigoNumericoRedis`) y `WhatsappConfirmadoPort` (JDBC).
- **Servicio** `WhatsappConfirmadoService`: normaliza con `CodigoPorWhatsApp.numero`, envía con `CodigoPorWhatsApp.enviar` (sin esperar: la persona quiere saber si salió), cuenta con `LimitarSolicitudesResetPort`.
- **Controller** `WhatsappConfirmadoController` en `/api/v1/users/me/whatsapp`: `@Funcionalidad("cuenta.whatsapp")`, `@RequiresPermission(USE_APP, "self")`. Errores: `WhatsappDeLaCuentaException` → `GlobalExceptionHandler` (409/422 con `codigo`).
- **Recuperación**: `CodigoPorWhatsAppDeAccesoService.whatsappDe` prefiere el confirmado. No se toca `paraRecuperar`.
- **Catálogo**: interruptor `cuenta.whatsapp` (raíz, TODOS). **Borrado de cuenta**: se agrega la tabla a `BorrarDatosPropiosDeCuentaJdbcAdapter`.
- Matriz de permisos (spec 050): `USE_APP` propio, como «Eliminar mi cuenta»; ruta bajo `/users/me/**` = grupo ESCRITURA/LECTURA, sin cambios en `ClasificadorDeGrupos`.

## App (tab `Yo`, autorizado el 12/11/2026)

- `src/features/perfil/…`: api (`whatsappConfirmadoApi`), hook, hoja `HojaWhatsapp` con `PhoneCountryInput`/`FormField`, `CodigoOtpInput`, `GoldButton`, `HojaDesdeAbajo`, `useSystemBackHandler`, `Alert` de `src/components/Alerta`.
- Fila en `Yo` solo si `available`. Texto de la recuperación actualizado (FR-013).
- Pruebas Jest con marca `// spec 051 FR-NNN`; flujo Maestro `.maestro/…whatsapp-en-yo.yaml`.

## Riesgos
- Merge trivial con #145 (`log-codigo-whatsapp`) en `CodigoPorWhatsAppDeAccesoService`: se tocan el constructor y `whatsappDe`, no `paraRecuperar`.
- La plantilla de AUTENTICACIÓN de Meta ya está aprobada (spec 043); sin ella `available` es `false` y la app no ofrece nada.
