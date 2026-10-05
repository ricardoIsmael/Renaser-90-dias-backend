# Eliminación de cuentas (D-243, 2026-10-02)

Motivo: Google Play exige que la persona pueda eliminar su cuenta **desde la app** y **desde un enlace
web**. Las decisiones son del dueño (2026-10-02) y se aplicaron tal cual; lo que se asumió está marcado.

## 1. Los tres caminos

| Camino | Quién | Qué pasa | Endpoint |
|---|---|---|---|
| **Yo → «Eliminar mi cuenta»** | La propia persona, con su contraseña (o con un código al correo si entra con Google/Apple) | La cuenta se **cierra al instante**; a los **30 días** se borra para siempre | `GET/POST /api/v1/users/me/account-deletion`, `POST …/code` |
| **Página web pública** | Cualquiera que tenga el correo (sin sesión) | Igual que la anterior: se confirma con un código que llega al correo | `POST /api/v1/account-deletion/request-code`, `POST /api/v1/account-deletion/confirm` |
| **Administración → persona → «Eliminar cuenta»** | ADMIN y ALQUIMISTA (a un ADMIN/ALQUIMISTA solo lo elimina un ADMIN; nadie a sí mismo) | Se **borra en el acto y para siempre**, sin gracia. Hay que escribir el correo de la persona | `POST /api/v1/admin/users/{id}/account-deletion` |
| **Administración → «Recuperar cuenta»** | ADMIN y ALQUIMISTA | Dentro de los 30 días, devuelve la cuenta (la persona escribió a soporte) | `POST /api/v1/admin/users/{id}/account-deletion/recover` |

URL de la página web: **https://renaser-90-dias-frontend-livid.vercel.app/eliminar-cuenta** (HTML
estático en el frontend, `public/eliminar-cuenta/index.html`; el texto se lee sin JavaScript).

### Qué es «cerrar» (los 30 días de gracia)

- `usuarios.baja_solicitada_en = ahora` y la cuenta pasa a `SUSPENDED`: no puede iniciar sesión.
- Se cierran **todas** sus sesiones (Redis) y la sesión de la propia petición.
- Se publica `EstadoDeCuentaCambiadoEvent` (ACTIVE → SUSPENDED), el mismo de una suspensión:
  `notifications` borra sus tokens push y `points` pausa su semáforo.
- Sale de lo que ven los demás: cada módulo filtra sus listados con `users.api.CuentasCerradasFinder`
  (ranking, integrantes del grupo, lista del mentor, Muro, testimonios, chats directos). Administración
  sigue viéndola, con «Se borra el …» y «Recuperar cuenta».
- Queda en `auditoria_eliminacion_cuentas` (V90) como `CERRADA`, con la vía (`APP` o `WEB`).

Una suspensión a secas **no** cambió de comportamiento: solo las cuentas cerradas para eliminar se
ocultan.

### El borrado definitivo

- **Barrido cada hora** (`PurgarCuentasBajaScheduler`, minuto 30 UTC, `@SchedulerLock`): borra las
  cuentas cuyo `baja_solicitada_en + 30 días` ya pasó. La gracia se mide en instantes (30 × 24 h), no en
  días de ninguna zona. Pagina de a 50 por id; cada cuenta en su transacción y con su `try/catch`;
  una corrida perdida la recupera la siguiente (regla 02).
- **Cada módulo borra lo suyo** (`users.api.BorradoDeDatosDeCuenta`, una implementación por módulo,
  ordenadas con `@Order`). `users` orquesta: junta las claves de archivo de todos, pregunta cuáles
  siguen en uso por filas que quedan, borra del bucket las exclusivas, y después, en una
  transacción, llama a cada módulo y borra sus propias tablas. Las FK con `CASCADE` quedan como red.
- Los archivos se borran **antes** que las filas (si se corta, la cuenta sigue y se reintenta).
- Queda en la auditoría como `ELIMINADA_AL_VENCER` o `ELIMINADA_POR_ADMIN` (con quién la eliminó).

### El correo vuelve a servir

Tras el borrado definitivo el correo se puede usar para una cuenta nueva (decisión 3). Unicidades
revisadas:

| Dónde | Cómo queda libre |
|---|---|
| `usuarios.email` (UNIQUE) | Se borra la fila |
| `solicitudes_cuenta.email` (UNIQUE) y `existsByEmail` del alta | Se borran las solicitudes por `usuario_id`, `usuario_creado_id` **y por correo** |
| `identidades_externas` (Google/Apple) | Se borran (por `usuario_id`) |
| Redis: códigos de alta, de reset y de eliminación, topes | Van por correo pero vencen solos (minutos/1 h) y no bloquean un alta nueva; los tokens de reset apuntan al id viejo, que ya no existe |
| Sesiones (Spring Session en Redis) | Se borran al cerrar y otra vez al borrar |

Probado de punta a punta en `EliminacionDeCuentaIT`: alta real → cerrar → barrido → ninguna fila →
alta nueva con el mismo correo.

## 2. Inventario de datos de una persona

Se recorrió el esquema completo (`information_schema`: todas las columnas con FK a `usuarios(id)` o a
`participantes_programa(usuario_id)`, más las columnas de persona sin FK). La prueba de integración
compara contra el esquema, no contra esta tabla: una tabla nueva entra sola.

**B** = se borra · **N** = la columna queda en NULL (la fila es de otra persona y se conserva) ·
**A** = se borra el archivo del bucket.

| Módulo | Tabla | Qué se hace | Por qué |
|---|---|---|---|
| users | `usuarios` | B | La cuenta |
| users | `participantes_programa`, `perfiles_mentor`, `identidades_externas` | B | Datos de la persona |
| users | `solicitudes_cuenta` | B (por id **y** por correo) | Correo, nombre, teléfono, ciudad, IP; y libera el correo |
| users | `ajustes_dia_programa` (como aprendiz) | B | Su historia |
| users | `ajustes_dia_programa.ajustado_por` (lo que un Admin le ajustó a OTRO) | N (V90) | Es historia del otro aprendiz; antes era `RESTRICT` y no dejaba borrar al Admin |
| users | `auditoria_cambios_rol.usuario_id/actor_id`, `solicitudes_cuenta.revisada_por` | N (FK) | Auditoría de otros |
| users | avatar (`avatares/<id>`) | A | Su foto |
| chat | conversaciones SOPORTE suyas y DIRECTAS donde participa | B enteras (mensajes de los dos lados) | Sin la otra persona un 1 a 1 no tiene sentido |
| chat | sus mensajes en chats de grupo y el global, su fila de participante | B | Ver §3 |
| chat | `mensajes_bienvenida` suyos | B | Marca de su bienvenida |
| chat | `cambios_bienvenida.cambiado_por` | N | Historia del staff |
| chat | fotos y audios de los mensajes que se borran (`chat/<conversación>/…`) | A (si ningún mensaje que queda las usa) | |
| community | sus publicaciones, comentarios y reacciones; las medias de sus publicaciones | B + A | Su contenido; lo de otros sobre su publicación cae con ella |
| community | `testimonios` suyos | B | Son su nombre, foto y texto |
| community | `asignaciones_celula` (suyas) | B; `actor_id` → N | |
| community | `celulas.mentor_id` | N | El grupo queda sin mentor, como hoy con una suspensión |
| community | `anomalias_acompanamiento` | B | Tabla sin dueño en Java (V45); asignada a community |
| calendar | `confirmaciones_evento`, `recordatorios_evento` | B | |
| calendar | `eventos.creado_por` | N | El evento es de todos |
| notifications | `notificaciones`, `preferencias_notificacion`, `tokens_push` | B | |
| support | `tickets_soporte` (y su adjunto), `tickets_mentor` como aprendiz | B + A | |
| support | `tickets_mentor.respondido_por` | N | Respuesta a otro aprendiz |
| leadership | `observaciones_mentor` sobre ella o escritas por ella | B | Es su texto; `autor_id` era `RESTRICT` |
| academy | `asignaciones_curso`, `progreso_lecciones`, `miembros_grupo`, `recomendaciones_academia`; `asignada_por` → N | B / N | |
| rag | conversación y mensajes con SER, memoria, recuerdos, agenda ocupada, informes espejo-sombra y sus preguntas, propuestas del acompañante | B | |
| onboarding | respuestas, grabaciones V90, medias (archivo), estado, etapas (`marcada_por` → N), acciones y protocolos del mapa | B + A | |
| phasecontracts | `contratos_fase` (firma) | B + A | |
| evidence | `evidencias` | B + A | |
| rocks | objetivos maestros/mensuales/semanales, rocas diarias y sus acciones, eventos del verdugo | B | |
| points | ranking, puntajes, ajustes de puntos, historial de coherencia, días/semanas/pausas del semáforo | B | |
| habits | registros (y sus sesiones de bloqueo y rachas, con archivos), diario (audio), espíritu, radar, desbloqueos, horarios, preferencias, renombres, historial, y sus hábitos personales con sus horarios y guías | B + A | |
| — | `auditoria_eliminacion_cuentas` (V90) | **Se conserva** | Pedido del dueño: quién eliminó a quién y cuándo. Solo ids opacos y el rol; ni correo ni nombre |

**Datos que se conservan por ley o contabilidad:** ninguno identificado. El sistema no guarda pagos ni
facturación propia (el enum `categoria_soporte` tiene `FACTURACION` como tema de ticket, no como
registro contable). Si el dueño tiene una obligación legal de conservar algo, hay que decírnoslo: no se
inventó ninguna.

## 3. Decisión sobre los mensajes en grupos

Pedido: «elige lo que respete la conversación de los demás y explícalo». Se **borran** sus mensajes
de los grupos, no se anonimizan:

- Anonimizar («Cuenta eliminada», contenido vacío) obliga a que `mensajes.emisor_id` sea NULL. La app
  instalada espera siempre un emisor; como no se actualiza por aire, un mensaje sin emisor podía
  romperle el chat a quien no reinstaló.
- Lo que escribieron los demás **se conserva entero**. Si alguien había respondido a un mensaje
  borrado, su respuesta sigue siendo una respuesta y la app muestra la cita como «Mensaje eliminado»
  (`respuesta_a_id` queda guardado: V92 quitó la FK, D-251); el texto, el autor y el archivo del
  citado se van con su fila.
  > **Corregido 2026-10-05 (D-251).** Decía «su respuesta queda sin la cita (`respuesta_a_id` pasa a
  > NULL por la FK)»: así la respuesta se veía como un mensaje suelto, sin saber a qué contestaba.
- Borrar es además lo que la persona pidió: que sus datos desaparezcan.

## 4. Supuestos a confirmar con el dueño

- **La última cuenta ADMIN activa no puede eliminarse a sí misma** (409): si no, nadie podría
  recuperar ni administrar. Supuesto técnico.
- **Un MENTOR que se elimina** deja sus grupos sin mentor (igual que una suspensión).
- **Recuperar** devuelve la cuenta a ACTIVE aunque antes de cerrarse estuviera suspendida por un Admin.
- **Canal de soporte para quien ya no puede entrar**: los textos dicen «escríbele a soporte» sin un
  correo o teléfono, porque no hay uno confirmado. Falta que el dueño diga cuál publicar.
- **Bienvenidas de grupo**: el texto de bienvenida de un grupo lleva el nombre de la persona. Ver el
  informe del módulo chat sobre si se pudieron identificar y borrar.

## 5. Qué hace falta para que llegue a la gente

- **Backend**: desplegar (trae la migración V90).
- **Web**: desplegar en Vercel (la página es estática).
- **App**: **hace falta un APK nuevo** para «Eliminar mi cuenta» en Yo y para «Eliminar cuenta» /
  «Recuperar cuenta» en Administración.
