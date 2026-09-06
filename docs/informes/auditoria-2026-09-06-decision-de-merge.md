# Rama `auditoria` — por qué conviene mergearla, qué mejora y qué puede romper

**Fecha:** 2026-09-06
**Ramas:** `auditoria` en los dos repos (backend `ricardoIsmael/Renaser-90-dias-backend`, frontend `RENASER-LAB/Renaser-90-dias-frontend-`).
**Estado del merge:** `master` **no se movió** desde que se abrieron las ramas; `auditoria` va estrictamente por delante (base común = punta de `master`). Un merge a `master` sería **fast-forward, sin conflictos** — verificado con `git merge-tree` en los dos repos, sin tocar `master`.
**Este documento NO mergea nada.** Los usuarios están usando producción; mergear lo decide el dueño, con esto en la mano.

> **Mergear el backend = desplegar.** El CD sale solo de `master`: en cuanto el merge llegue, se construye y se reemplaza el contenedor (~10 min, con rollback automático si `/actuator/health` no responde). El frontend lo despliega Vercel al instante. Conviene mergear **primero el backend, comprobar, y después el frontend** (§4).

---

## 1. Qué hay en la rama y qué logra cada cosa

### Backend (`9c360ee` + este documento)

| Cambio | Qué logra o mejora | Evidencia |
|---|---|---|
| `SecurityConfig`: `/api/v1/habits/**` y `/api/v1/users/**` con sesión obligatoria | Cierra que cualquiera renombre o quite hábitos ajenos, **invite usuarios o cambie roles** con solo el UUID de un admin en un header | `HabitRenameControllerAutenticacionTest` (4) |
| WebSocket del chat: actor desde Spring Session (`X-Auth-Token` / `?token=`), orígenes = CORS, `SEND` a `/topic` rechazado | Cierra S-2, S-4 y S-6 de la auditoría del 1/9: nadie se conecta "como" otro ni publica en conversaciones ajenas | `ActorHandshakeInterceptorTest` (5), `SubscripcionAutorizadaInterceptorEnvioTest` (2); E-148 |
| `server.forward-headers-strategy=framework` | Los límites por IP (login 50/h, alta 60/h, reset) pasan de ser **un contador compartido por todos** a uno por cliente real | `AccountRequestControllerIpRealTest` (1); E-149 |
| Cliente Google: timeout 60 s, 2 intentos solo en 5xx/408, errores → `ProveedorIaNoDisponibleException` → **503 + `Retry-After`** | Con la cuota agotada el chat falla en milisegundos con un mensaje honesto, en vez de ~15 s de reintentos ocultos del SDK y un 500 que el móvil reintentaba | 12 pruebas; E-147 |
| `spring.mvc.async.request-timeout=120s` | Una respuesta larga del chat ya no se corta a los 30 s (default de Tomcat) sin aviso | config |
| Redis `timeout 3s` / `connect-timeout 2s` | Redis caído = fallo rápido, no un minuto colgado por request (incluido el health check del CD) | config |
| `question` ≤ 4.000 caracteres | Un cliente no puede mandar un prompt de megabytes a cargo de la cuota compartida | `RenasiaControllerLargoPreguntaTest` (2) |
| `consultar_habitos_del_dia` devuelve el total en juego | Un viaje menos a Gemini cuando la pregunta mezcla "qué falta" y "cuánto vale" | `HerramientasAgenteServiceTest` |
| `CLAUDE.md` §7, bitácora E-142…E-149, D-117/D-118, informe NFR | La documentación deja de contradecir al código; quien venga después no "arregla" lo que ya estaba bien | docs |

**Verificación de la rama entera:** `./mvnw clean verify` → **2.465 unitarias + 25 de integración, 0 fallos** (cifras de los XML de surefire/failsafe).

### Frontend (`auditoria`, último commit con el interruptor del Día 7)

| Cambio | Qué logra o mejora |
|---|---|
| `Row` / `RowBetween` en `components/ui.tsx` + 16 filas de `PlanScreen` convertidas | La fila repetida 16 veces a mano pasa a un solo lugar; es el patrón para el resto (informe de estilos) |
| Módulo `features/mapa-renacimiento` (Día 7, once vistas) | Implementa el manual del Día 7 del lado del cliente, con reglas de calidad sin IA y borrador local |
| **Interruptor `EXPO_PUBLIC_MAPA_DIA7`** | En producción el Día 7 **no aparece** salvo que la variable esté en `on` al compilar. Mergear no es lanzar |
| `docs/AUDITORIA_ESTILOS_2026-09-06.md`, `docs/MAPA_RENACIMIENTO_DIA7.md` | Qué se hizo, qué se asumió, qué falta |

**Verificación:** `tsc --noEmit` en cero; V01–V10 del Día 7 recorridas en el navegador.

### Ya en producción, fuera del repo (no dependen del merge)

Cabeceras de seguridad en CloudFront (HSTS, `nosniff`, `X-Frame-Options`, `Referrer-Policy`); topic SNS `renaser-alertas` + alarmas `StatusCheckFailed`; política IAM `bootstrap-primer-admin` retirada del rol del EC2.

---

## 2. Qué puede romper, cómo se nota y cómo se revierte

Esto es lo que importa antes de mergear. Ordenado de mayor a menor riesgo real.

| # | Cambio | Qué podría romper | Probabilidad | Cómo se detecta | Cómo se revierte |
|---|---|---|---|---|---|
| 1 | `/users/**` con sesión | **El panel Lambda de solicitudes**, si llama a `POST /users/invite` o `PATCH /users/{id}/role` con solo `X-Actor-Id`. Esta sesión no tiene su código. La app móvil no se ve afectada: usa sesión para todo desde el 5/9 | Baja–media | Un 403 al invitar/aprobar desde el panel; `grep 403` en los logs del contenedor | Quitar `"/api/v1/users/**"` de esa línea de `SecurityConfig` (una línea) — o, mejor, hacer que el panel mande `X-Auth-Token` |
| 2 | `forward-headers-strategy=framework` | Spring pasa a **confiar en `X-Forwarded-For`**. Quien alcance el origen **sin pasar por CloudFront** puede falsear su IP y esquivar los límites de tasa. Hoy el security group solo deja llegar a CloudFront y a la IP del dueño; el pendiente de la cabecera secreta de origen (informe §2.3) es lo que cierra esto del todo. Efecto colateral bueno: Spring ve `X-Forwarded-Proto: https` y emite HSTS también desde el origen (duplicado con CloudFront, inofensivo) | Baja | Límites que no se disparan cuando deberían; `X-Forwarded-For` con valores raros en logs | `FORWARD_HEADERS_STRATEGY=none` en Parameter Store + reinicio |
| 3 | Cliente Google con timeout 60 s y 1 reintento | Una respuesta del modelo que tarde **más de 60 s** falla (503). Con `flash-lite` y dos herramientas es raro; con `busqueda-web` activada podría acercarse | Baja | Errores "saturado" sin cuota agotada; `503` en logs con `Retry-After: 10` | `RENASER_IA_GOOGLE_TIMEOUT_MS=120000` (propiedad `renaser.ia.google.timeout-ms`) sin tocar código |
| 4 | `question` ≤ 4.000 | Un mensaje más largo recibe **400** con mensaje de validación (antes entraba) | Muy baja (600 palabras) | 400 en `POST /renasia/mensajes` | subir el tope en `PreguntarRenasiaRequest` |
| 5 | WebSocket por sesión | Cualquier cliente que abriera `/ws` con `X-Actor-Id`. **No existe ninguno en el repo** (el móvil conversa por REST) | Muy baja | 403 en el handshake de `/ws` | no conviene revertir: era el agujero |
| 6 | Redis `timeout 3s` | Si Redis tardara >3 s (misma máquina, responde en <1 ms) las requests con sesión fallarían en vez de esperar | Muy baja | errores de Lettuce `Command timed out` | `REDIS_TIMEOUT=60s` |
| 7 | 503 en vez de 500 ante cuota de Google | Ningún cliente actual distingue 500 de 503; el móvil muestra el mismo error genérico. **Mejora** el mensaje en el stream ("saturado… en unos minutos") | — | — | — |
| 8 | Total en `consultar_habitos_del_dia` | El modelo ve una línea más de contexto; podría dejar de llamar a `consultar_puntos_en_juego` (esa era la idea). Sin cambio de API | Muy baja | respuestas del acompañante sobre puntos | quitar la línea |
| 9 | `PlanScreen` con `Row`/`RowBetween` | Regresión **visual** en las 16 filas convertidas. El estilo resultante es idéntico por construcción, pero no se verificó con sesión | Baja | abrir Plan y mirar las filas de los hábitos | revertir el commit `522c1ff` del frontend |
| 10 | Día 7 en producción | **Apagado por defecto.** Si se enciende antes de tener backend: el mapa vive solo en el dispositivo (se pierde al cambiar de teléfono o limpiar el navegador), los hábitos que crea son diarios (el endpoint no acepta días), y no hay panel del mentor | Cero mientras esté apagado | — | no poner `EXPO_PUBLIC_MAPA_DIA7=on` hasta cerrar `docs/MAPA_RENACIMIENTO_DIA7.md` §4 |

**Lo que NO cambia para los usuarios el día del merge:** ninguna pantalla nueva visible; ningún contrato de API modificado (solo nuevas exigencias de sesión en rutas que la app ya usa con sesión, y un tope de tamaño que nadie alcanza); el chat responde igual salvo cuando el proveedor falla, que ahora avisa mejor.

---

## 3. Cómo mergear (cuando se decida)

Los dos repos: `auditoria` ya contiene `master` (`git merge origin/master` en `auditoria` devolvió *Already up to date*), así que el merge es fast-forward:

```bash
git checkout master && git pull
git merge --ff-only auditoria      # si dijera "not possible", alguien movió master: git merge auditoria y resolver
git push origin master             # backend: dispara el CD; frontend: Vercel despliega
```

Primero el backend. Cuando `/actuator/health` esté `UP` con la imagen nueva (el CD la etiqueta con el SHA del commit), el frontend.

## 4. Comprobaciones después de desplegar el backend (10 minutos)

1. `curl -s https://djbooeq09skac.cloudfront.net/actuator/health` → `UP`.
2. Login desde la app web (`-livid`) y abrir Plan: las rutas con sesión siguen respondiendo.
3. **El panel Lambda:** aprobar o rechazar una solicitud de prueba. Si devuelve 403, es el punto 1 de §2.
4. Preflight CORS: `OPTIONS /api/v1/habits` con `Origin` del `-livid` → 200 con `Access-Control-Allow-Origin`.
5. Un login fallido a propósito con un correo inventado → 401 (no 429): confirma que el contador por IP ya no es global.
6. Una pregunta al acompañante → responde, o devuelve el mensaje de "saturado" si la cuota sigue agotada (no un error genérico).

Después del frontend: abrir Plan → Hábitos y mirar las filas (punto 9); confirmar que en Home **no** aparece la tarjeta del Día 7.

## 5. Cómo volver atrás, entero

- **Backend:** `git revert -m 1 <commit del merge>` en `master` y push → el CD despliega la versión anterior. O, más rápido, redesplegar la imagen anterior: `workflow_dispatch` del CD sobre el commit `c8b18a6` no existe como opción; lo que sí: `git reset --hard c8b18a6 && git push --force-with-lease` **solo** si nadie más empujó a `master` (peligroso; preferir el revert).
- **Frontend:** `git revert` del merge y push; Vercel redespliega en ~1 minuto.
- **Por partes:** cada riesgo de §2 tiene su reversión puntual, casi siempre una variable de entorno sin tocar código.

---

**Resumen para decidir:** lo que se gana son cuatro agujeros de seguridad cerrados (dos de ellos críticos), límites de tasa que por primera vez son por persona, y un chat que falla rápido y honesto. Lo que se arriesga es un panel administrativo que hay que confirmar (§2.1) y una pantalla del Plan que hay que mirar (§2.9). El Día 7 viaja apagado.
