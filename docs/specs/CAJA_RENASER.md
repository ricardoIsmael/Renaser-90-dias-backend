# Caja Renaser — spec (2026-09-28)

Fuente: procedimiento de Operaciones «Caja Renaser (02)» y las 29 respuestas del dueño en el cuestionario del
2026-09-28 (artifact «Cuestionario Caja Renaser»). Regla del dueño: **no se crean tablas**; se reutiliza lo que
existe. Decisión de registro: **D-219**.

## 1. Qué resuelve

Hoy la Caja se lleva a mano: se revisa quién terminó la Fase 1, se pide la dirección por WhatsApp, se arma, se
envía y se pregunta si llegó. Con esto, el Admin lo hace entero dentro de la app y el aprendiz ve en qué va su caja
y confirma que la recibió.

## 2. Estados

| Estado | Cómo se llega | Lo ve el aprendiz como |
|---|---|---|
| `NO_APLICA` | Día del programa < 8 (derivado) | nada |
| `EN_EVALUACION` | Día ≥ 8 y no cumplió el requisito (derivado) | «En evaluación» |
| `POR_REVISAR` | Día ≥ 8 y cumplió el requisito (derivado), o el Admin lo aprueba caso por caso | «En revisión» |
| `ARMANDO` | El Admin empieza a armarla | «Armando tu caja» |
| `ENVIADA` | El Admin la marca enviada con todos los datos obligatorios | «En camino» + medio y código |
| `ENTREGADA` | El aprendiz toca «Ya la recibí», o el Admin la marca | «Entregada» |
| `CON_PROBLEMA` | El Admin reporta pérdida, daño o devolución | «Estamos resolviendo tu envío» |
| `EN_PAUSA` | La cuenta está suspendida y la caja no se entregó (derivado) | — |
| `FUERA_DE_LA_APP` | País ≠ Perú (derivado; el dueño: «extranjero, fuera de la app») | — |

- **Derivar, no incrementar** (regla 02): `NO_APLICA`, `EN_EVALUACION`, `POR_REVISAR` (automático), `EN_PAUSA` y
  `FUERA_DE_LA_APP` se calculan al leer. Solo los pasos que hace una persona se guardan.
- Un reenvío después de `CON_PROBLEMA` vuelve a `ARMANDO` con número de envío 2 (3, …). El historial completo
  queda a la vista.

### Requisito de la Fase 1 (supuesto a confirmar)

El dueño eligió «la app muestra el avance y el Admin decide», con la nota «por si acaso automático también,
según el semáforo». Se implementa: **pasa sola a `POR_REVISAR` si el cumplimiento de hábitos de sus días 1 a 7 es
≥ 80 %** (el mismo umbral del verde del semáforo, D-168/D-181). Si no, queda `EN_EVALUACION` y el Admin la aprueba
caso por caso («Aprobar para la caja»). El número vive en una sola constante del dominio.

## 3. Quién hace qué

| Acción | ADMIN | ALCHEMIST | MENTOR | Aprendiz |
|---|---|---|---|---|
| Ver la lista, el detalle y descargar | ✔ | — | — | — |
| Aprobar, armar, enviar, reportar problema, marcar entregada | ✔ | — | — | — |
| Editar el contenido de la caja y el diseño de la carta | ✔ | — | — | — |
| Ver el estado de la caja | ✔ | — | solo sus aprendices (vigentes) | la suya |
| «Ya la recibí» | ✔ (por la persona) | — | — | la suya, solo si `ENVIADA` |
| Otra dirección / otro número / quién recibe | ✔ | — | — | la suya, antes de `ENVIADA` |

Solo ADMIN opera (respuesta «Solo el Admin»). Permiso nuevo `MANAGE_RENASER_BOX` + guard de rol ADMIN activo en
el servicio (MENTOR y ALCHEMIST pasan cualquier permiso en la matriz, patrón `MANAGE_WELCOME`).

## 4. Qué se guarda y dónde (sin tablas nuevas)

Hogar: módulo **`onboarding`**, que ya es el motor genérico de formularios (V41, Mapa del Día 7). Flujo nuevo
`caja_renaser`.

| Pieza | Dónde |
|---|---|
| Datos de envío | Se leen de la Ficha Inicial (`respuestas_onboarding` de `ficha_inicial`: `full_name`, `whatsapp`, `country`, `city`, `district`, `address_reference`, `identity_document`) |
| Otra dirección, otro número, quién recibe, referencias, provincia | Preguntas nuevas del flujo `caja_renaser`, sección `destino` (`caja_otra_direccion`, `caja_otro_celular`, `caja_quien_recibe`, `caja_referencias`, `caja_provincia`). Todas opcionales |
| Contenido de la caja (editable) | UNA pregunta `caja_contenido` `SELECCION_MULTIPLE`; los elementos son sus `opciones_pregunta`, sembradas con los 8 del procedimiento. Editar la lista = reescribir las opciones |
| Checklist de cada caja | `respuestas_onboarding.valor_json` de `caja_contenido` del aprendiz (array de valores marcados) |
| Pasos con fecha, autor y datos | `etapas_onboarding_completadas`, `flujo = 'caja:<n>:<ESTADO>'` (n = número de envío). **Columnas nuevas** `marcada_por uuid NULL REFERENCES usuarios ON DELETE SET NULL` y `detalle jsonb` |
| Datos del envío | `detalle` de la fila `caja:<n>:ENVIADA`: `medio` (texto libre: por dónde se envió), `courier`, `codigo`, `costo`, `comprobante_media_id` |
| Foto de la caja armada | `medias_onboarding` (clase `FOTO`, ruta `onboarding/<aprendizId>/caja/<uuid>`), id y ruta en `detalle` de `caja:<n>:FOTO` (se reemplaza mientras se arma); al enviar se copian al `detalle` de `ENVIADA` (`foto_media_id`, `foto_ruta`) |
| Comprobante | `medias_onboarding` (clase `FOTO`, misma ruta), id y ruta en `caja:<n>:COMPROBANTE`; al enviar, a `ENVIADA` (`comprobante_media_id`, `comprobante_ruta`) |
| Aprobación caso por caso | `caja:1:APROBADA` |
| Avisos ya dados por el barrido | `caja:<n>:AVISO_EN_REVISION`, `caja:<n>:AVISO_RECORDATORIO`, `caja:<n>:AVISO_SIN_CONFIRMAR` (marcas; `ON CONFLICT DO NOTHING`) |

> **Corregido 2026-09-28 (implementación, D-219).** Decía que la foto iba en `caja:<n>:ARMADA` y el comprobante
> directo en `ENVIADA`. Pero el comprobante se sube ANTES de enviar (enviar lo exige), y los dos se pueden
> cambiar mientras se arma: cada uno tiene su fila, que se reemplaza, y `ENVIADA` guarda la copia final.
| Problema | `detalle` de `caja:<n>:CON_PROBLEMA`: `motivo` (PERDIDA, DAÑADA, DEVUELTA, OTRO) y `nota` |
| Diseño de la carta | `cambios_bienvenida` con `pieza = 'CARTA_CAJA'` (bitácora append-only de D-210; se amplía el CHECK y el prefijo `caja/cartas/`) |

**Por qué dos columnas en `etapas_onboarding_completadas`:** sin `marcada_por` no queda qué Admin armó o envió
(precedente `cambios_bienvenida.cambiado_por`); sin `detalle` el historial de un reenvío pisaría el código del
envío anterior, porque `respuestas_onboarding` tiene una fila por pregunta. Una tabla nueva estaba prohibida.

**Cierre de seguridad obligatorio:** `POST /api/v1/onboarding/answers` hoy acepta cualquier pregunta del propio
actor. Las preguntas del flujo `caja_renaser` quedan **fuera** de ese endpoint; el aprendiz solo escribe las de
`destino`, por su caso de uso, y nunca `caja_contenido`.

## 5. Avisos

Por evento → `notifications` (tipo `HITO_PROGRAMA`, `origen_evento_id` determinístico para no duplicar) y →
`chat` (mensaje del programa en su soporte, `EnviarMensajeDelProgramaUseCase`). Respuesta del dueño: «En los 2».

| Momento | Aprendiz | Admin |
|---|---|---|
| Pasa a `POR_REVISAR` | «Tu caja está en revisión» | notificación «X está lista para su Caja» |
| `ARMANDO` | «Estamos armando tu caja» | — |
| `ENVIADA` | «Tu caja va en camino» + medio y código (+ foto de la caja en el chat) | — |
| `ENVIADA` + 3 días sin confirmar | recordatorio «¿Ya te llegó?» | — |
| `ENVIADA` + 5 días sin confirmar | — | aviso |
| `ENTREGADA` | invitación a compartir una foto en el Muro (opcional) | — |

Un barrido **cada hora** (`@SchedulerLock`, paginado, try/catch por persona) detecta `POR_REVISAR` nuevo y los
plazos de 3 y 5 días; como el aviso lleva `origen_evento_id` fijo, correrlo dos veces no duplica.

Implementado así (D-219):

- Los plazos son **72 y 120 horas** desde que se marcó enviada (no días del calendario de nadie).
- **El aviso automático de «en revisión» sale apagado** (`CAJA_AVISAR_POR_REVISAR=false`): el padrón de hoy ya
  pasó el Día 8, y prendido de entrada le diría «tu caja está en revisión» a quien ya la recibió antes de la app.
  Secuencia de despliegue: el Admin marca «Ya se envió antes» a esos, y después se prende. Cuando el Admin
  **aprueba** a alguien, el aviso al aprendiz sale igual (sin aviso al Admin: ya lo sabe).
- Los avisos al Admin van a **cada ADMIN activo** (no a ALCHEMIST: la caja la lleva solo el Admin).
- Ruta de la notificación: `/caja` para el aprendiz, `/admin/caja/{aprendizId}` para el Admin.
- Al reenviar después de un problema, el aprendiz vuelve a recibir «Estamos armando tu caja».
- Un problema (`CON_PROBLEMA`) no le avisa nada al aprendiz (la tabla de arriba no lo pide): lo ve en «Tu caja».

## 6. Couriers

Olva y Shalom no tienen una API pública abierta: la dan con contrato comercial o por agregadores pagos. Para esta
versión: el Admin escribe por dónde lo envió y el código (obligatorio, también en inDrive: placa o número de
pedido) y la persona confirma. La app abre la página oficial de rastreo cuando el courier es Olva o Shalom.

## 7. Pantallas

- **Administración → «Caja Renaser»** (web y teléfono): pestañas por estado con conteo, buscador, **Descargar**
  (CSV con todos los datos de envío, para no llamar a nadie). Detalle: datos de envío, checklist, foto, envío,
  historial. Botones con una sola acción cada uno.
- **Ficha del aprendiz** (Admin y mentor): el estado de la caja, un chip.
- **Yo → «Tu Caja Renaser»** (aprendiz): pasos En evaluación → En revisión → Armando → En camino → Entregada;
  «Ya la recibí» cuando está en camino; «¿Te la enviamos a otro lugar?» antes del envío. **Trazabilidad
  (D-220):** una línea por envío que salió («Envío 1 · Olva OLV-7777 · Se perdió · 28 sep») con «Ver dónde va» si
  tiene rastreo, el motivo de un problema en palabras simples y la foto de la caja que salió. Nunca el comprobante,
  el costo ni la nota interna del Admin.
- **Carta**: el Admin descarga la carta con el nombre, lista para imprimir, y puede cambiar el fondo.
- Poco texto (pedido del dueño: «no debe contener mucho texto que se maree el usuario o administrador»).

## 8. Casos del padrón actual

Quien ya pasó el día 8 aparece según el requisito. El Admin tiene «Ya se envió antes» para marcarla `ENTREGADA`
sin datos (queda `detalle.previa = true`) y no se le avisa nada al aprendiz.

## 9. API

| Método | Ruta | Quién |
|---|---|---|
| GET | `/api/v1/admin/caja?estado=&q=&page=` | ADMIN |
| GET | `/api/v1/admin/caja/export.csv` | ADMIN |
| GET | `/api/v1/admin/caja/{aprendizId}` | ADMIN |
| POST | `/api/v1/admin/caja/{aprendizId}/aprobar` | ADMIN |
| POST | `/api/v1/admin/caja/{aprendizId}/armar` | ADMIN |
| PUT | `/api/v1/admin/caja/{aprendizId}/contenido` `{marcados:[...]}` | ADMIN |
| POST | `/api/v1/admin/caja/{aprendizId}/foto/upload-url`, `/foto/confirm` | ADMIN |
| POST | `/api/v1/admin/caja/{aprendizId}/comprobante/upload-url`, `/comprobante/confirm` | ADMIN |
| POST | `/api/v1/admin/caja/{aprendizId}/enviar` `{medio,courier,codigo,costo}` | ADMIN |
| POST | `/api/v1/admin/caja/{aprendizId}/entregada` `{previa?}` | ADMIN |
| POST | `/api/v1/admin/caja/{aprendizId}/problema` `{motivo,nota}` | ADMIN |
| POST | `/api/v1/admin/caja/{aprendizId}/reenviar` | ADMIN |
| GET/PUT | `/api/v1/admin/caja/contenido` (lista editable) | ADMIN |
| GET | `/api/v1/admin/caja/{aprendizId}/carta` (PNG) · POST `/api/v1/admin/caja/carta/fondo/upload-url`, `/confirm`, DELETE (volver al original) | ADMIN |
| GET | `/api/v1/mentor/trainees/{id}/caja` | MENTOR del aprendiz |
| GET | `/api/v1/me/caja` | aprendiz |
| PUT | `/api/v1/me/caja/destino` | aprendiz, antes de `ENVIADA` |
| POST | `/api/v1/me/caja/recibida` | aprendiz, solo `ENVIADA` |

`enviar` exige: checklist completo, foto de la caja armada, comprobante, medio y código; si falta algo, 409 con
qué falta.

## 10. Fuera de esta versión

API de couriers; envíos al extranjero; cobro del envío al aprendiz.

## 11. Contrato implementado (D-219, 2026-09-28)

Esto es lo que quedó en el servidor; donde difiere del contrato que se pasó al frontend, está marcado **(cambio)**.
Los campos sin valor viajan como `null` (no se omiten). Errores: 400 `{message}` (dato inválido), 403 (rol o
cuenta), 404 (no es un aprendiz / no se encontró la foto subida), 409 `{message}` (el estado no corresponde; el
mensaje dice el estado en palabras, «La caja ya fue entregada: …», nunca su nombre de la API — E-416).

**Lista** `GET /api/v1/admin/caja?estado=&q=&page=0&size=50`
`{items:[{aprendizId,nombre,grupo,diaPrograma,estado,envio,actualizadoEn,cumplimientoFase1}], total,
conteos:{EN_EVALUACION,POR_REVISAR,ARMANDO,ENVIADA,ENTREGADA,CON_PROBLEMA,EN_PAUSA,FUERA_DE_LA_APP}}`
- `page` desde 0; `size` por defecto 50, máximo 200. Orden: por nombre (sin tildes).
- Sin `estado`: todas menos `NO_APLICA`. `estado=NO_APLICA` también se puede pedir. `q` busca en el nombre.
- `conteos`: del padrón entero, **sin** filtros (son las pestañas).
- `envio`: el número de envío en curso (1 aunque todavía no haya salido nada).
- `cumplimientoFase1`: número con un decimal (0–100), o `null` antes del Día 8 o sin hábitos programados.
- `grupo`: el grupo en que está hoy (el primero si está en varios), o `null`.
- Quién está en el padrón: toda cuenta APRENDIZ con el programa activado, en cualquier estado de cuenta.

**Detalle** `GET /api/v1/admin/caja/{aprendizId}` → como se acordó. Además:
- `nombre`: el de la cuenta; `destino.nombre`: el `full_name` de la ficha (si falta, el de la cuenta).
- `fotoArmadaUrl`/`comprobanteUrl`: URL de lectura firmada que vence a los 15 min (pedir el detalle de nuevo
  para refrescarla); `null` si no hay en el envío actual.
- `envioDatos`: del envío actual; `null` hasta que salga (y otra vez `null` después de un reenvío).
- `historial[].estado`: `POR_REVISAR` (aprobación del Admin), `ARMANDO`, `ENVIADA`, `ENTREGADA`, `CON_PROBLEMA`,
  de todos los envíos, del más viejo al más nuevo. `porNombre`: `null` si se borró la cuenta.
- **(ampliación D-220)** `historial[].motivo` (`PERDIDA`, `DANADA`, `DEVUELTA`, `OTRO`) y `historial[].nota`: solo
  en `CON_PROBLEMA`; `null` en los demás pasos y si no se escribió nota. Antes el motivo y la nota se guardaban pero
  no salían en el detalle (observación de CAJA-10).
- `faltaParaEnviar`: se calcula siempre (la app lo usa en `ARMANDO`).

**Acciones** (todas devuelven el detalle):

| Acción | Desde |
|---|---|
| `POST …/aprobar` | `EN_EVALUACION` |
| `POST …/armar` | `POR_REVISAR` |
| `PUT …/contenido` `{marcados:[valor]}` | `ARMANDO` (un valor que no está en la lista → 400) |
| `POST …/foto/upload-url`, `…/comprobante/upload-url` `{contentType?}` → `{url,ruta}` | `ARMANDO`. **(cambio)** cuerpo opcional: `image/jpeg` (por defecto) o `image/png` — el mismo que se manda en el PUT |
| `POST …/foto/confirm`, `…/comprobante/confirm` `{ruta}` | `ARMANDO`. 404 si no se subió nada a esa ruta; 409 si el servidor no guarda objetos (en local); 400 si lo subido no es un JPEG ni un PNG por dentro (E-417). Se puede confirmar otra foto encima: reemplaza |
| `POST …/enviar` `{medio,courier,codigo,costo}` | `ARMANDO`. Falta algo → **409 `{message, faltan:[…], timestamp}`**. `medio` y `codigo` obligatorios (400); `courier` y `costo` (soles, ≥ 0) opcionales |
| `POST …/entregada` `{previa?}` | sin `previa`: `ENVIADA` o `CON_PROBLEMA` (si reapareció). `previa:true`: `NO_APLICA`, `EN_EVALUACION`, `POR_REVISAR`, `ARMANDO`, `EN_PAUSA`, `FUERA_DE_LA_APP`, sin avisos |
| `POST …/problema` `{motivo,nota}` | `ENVIADA` o `ENTREGADA` (llegó dañada). `motivo`: `PERDIDA`, `DANADA`, `DEVUELTA`, `OTRO`; `nota` ≤ 500 |
| `POST …/reenviar` | `CON_PROBLEMA`: vuelve a `ARMANDO` con `envio + 1`; el checklist y las fotos empiezan de cero |

Un doble toque sobre la misma acción da 409 (la clave del paso ya existe).

**Contenido** `GET/PUT /api/v1/admin/caja/contenido` `{elementos:[{valor,etiqueta}]}`. En el PUT, `valor` puede
venir vacío: sale de la etiqueta (`"Piedra de cuarzo"` → `piedra_de_cuarzo`). Entre 1 y 30 elementos, sin claves
repetidas; `valor` solo minúsculas, números y `_`. Editar la lista no toca los checklists ya marcados.

**Descarga** `GET /api/v1/admin/caja/export.csv`: UTF-8 con BOM, `;`, CRLF, una fila por aprendiz (sin
`NO_APLICA`). Columnas: `aprendizId;nombre;grupo;diaPrograma;estado;envio;cumplimientoFase1;nombreEnvio;celular;
dni;pais;provincia;ciudad;distrito;direccion;referencias;quienRecibe;otraDireccion;otroCelular;medio;courier;
codigo;costo;actualizadoEn`. Una celda que empieza con `= + - @` lleva un `'` delante (inyección de fórmulas).

**Carta** (la dibuja `chat`):
- `GET /api/v1/admin/caja/{aprendizId}/carta` → `image/png`, vertical 1200 × 1697 (proporción de hoja A), con el
  nombre completo de la cuenta al centro. El fondo original (hasta que Operaciones suba su diseño) es papel crema
  con doble marco verde.
- **(cambio, nuevo)** `GET /api/v1/admin/caja/carta/fondo` → `{cambiado, sePuedeCambiar, cambiadoPor, cambiadoEn}`.
- `POST /api/v1/admin/caja/carta/fondo/upload-url` `{contentType}` (obligatorio: `image/jpeg` o `image/png`) →
  `{url, ruta}`; `POST …/confirm` `{ruta}` y `DELETE /api/v1/admin/caja/carta/fondo` → el mismo objeto de
  `GET …/fondo`. La imagen se revisa como la portada de la bienvenida (JPG/PNG, ≤ 5 MB, 600–8000 px, que el
  nombre se lea al centro): si no sirve, 400 con el motivo.

**Mentor** `GET /api/v1/mentor/trainees/{id}/caja` → `{estado}`: su mentor vigente (lo acompaña HOY en un grupo
operativo) o un ADMIN; cualquier otro, 403.

**Aprendiz** `GET /api/v1/me/caja` → como se acordó. Además:
- `pasos`: siempre los cinco, `EN_EVALUACION`, `POR_REVISAR`, `ARMANDO`, `ENVIADA`, `ENTREGADA`, del envío en
  curso. `en` es cuándo llegó a ese paso, o `null` si todavía no llegó o no se sabe (`EN_EVALUACION` siempre
  `null`; `POR_REVISAR` tiene fecha si el Admin la aprobó o si el barrido avisó). Qué pasos están cumplidos se
  deduce de `estado`.
- `envioDatos` no lleva `costo`.
- **(ampliación D-220, aditiva)** `envios`: los envíos que **salieron** (llegaron a `ENVIADA`), del primero al
  último: `[{envio, medio, courier, codigo, rastreoUrl, resultado, en, motivo}]`. `resultado` es el último estado de
  ese envío: `ENVIADA` (sigue en camino), `ENTREGADA` o `CON_PROBLEMA`; `en`, cuándo llegó a ese resultado;
  `motivo`, solo si terminó con problema (`null` si no). Un envío que se quedó armando o una entrega «ya se envió
  antes» (sin datos) no aparece: no hay nada que seguir. Lista vacía si nada salió. Sin `costo` ni `nota`.
- **(ampliación D-220, aditiva)** `fotoArmadaUrl`: la foto de la caja que salió en el envío **en curso** (la copia
  que guarda `ENVIADA`), URL de lectura firmada que vence a los 15 min, como la del Admin; `null` hasta que salga y
  otra vez `null` después de un reenvío. Nunca la de un armado a medias.
- **Lo que `/me/caja` no expone nunca:** el comprobante (ni su URL ni su ruta), el costo y la nota del problema.
  `CajaRenaserIT` lo comprueba sobre el cuerpo crudo de la respuesta.
- `puedeCambiarDestino`: en `EN_EVALUACION`, `POR_REVISAR`, `ARMANDO` y `CON_PROBLEMA`.
- `PUT /api/v1/me/caja/destino` (mismo objeto `destino`; un campo vacío borra la respuesta; largos máximos: dirección
  y referencias 300, celular 30, quién recibe 120, provincia 80) y `POST /api/v1/me/caja/recibida` (solo
  `ENVIADA`) devuelven el mismo objeto de `GET /me/caja`. Fuera de su estado: 409. Cuenta suspendida: 403.

**Seguridad.** `MANAGE_RENASER_BOX` en todo `/admin/caja/**`; el servicio exige ADMIN activo (ALCHEMIST,
MENTOR, MENTOR_LEAD y ADMIN suspendido → 403; TRAINEE → 403 del interceptor). `POST /api/v1/onboarding/answers`
rechaza con 403 toda pregunta del flujo `caja_renaser`.

**Supuestos a confirmar con el dueño:** el umbral del 80 % (§2); que sin país en la ficha se asume Perú; que
`INACTIVO` cuenta como `EN_PAUSA` igual que `SUSPENDIDO`; los textos de los avisos; y el fondo original de la carta.

