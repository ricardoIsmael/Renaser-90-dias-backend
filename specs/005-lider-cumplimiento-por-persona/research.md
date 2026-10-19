# Research: El líder de mentores ve el cumplimiento de cada persona por grupo

Investigación sobre master (`9f387b78`) y sobre la rama `claude/fase2-integracion` de Soporte TI, hecha el
2026-10-19. Cada decisión lleva lo que se eligió, por qué y qué se descartó.

## R-1 · Base de la rama y orden de integración

- **Decisión**: implementar sobre master y **integrar después de la fase 2 de Soporte TI**. La migración nueva es
  `V109` y la clave de funcionalidad se declara en `EntradasDelCatalogo` de la fase 2.
- **Por qué**: el catálogo de funcionalidades (`@Funcionalidad`, `EntradasDelCatalogo`, `FuncionalidadDeclaradaTest`)
  y la marca `usuarios.es_cuenta_de_prueba` (V107) existen solo en `claude/fase2-integracion`, que ya ocupa
  V98..V108. Si 005 llegara a producción antes con un número más bajo, Flyway rechazaría las migraciones de la fase 2
  por estar fuera de orden.
- **Cómo**: el código de 005 se escribe sobre master. Las tres tareas que dependen de la fase 2 (la clave
  `lider.cumplimiento`, `@Funcionalidad` en los endpoints nuevos y el filtro de cuentas de prueba) se hacen al
  rebasar sobre master una vez integrada la fase 2. Mientras tanto, el filtro de cuentas de prueba queda detrás de
  un puerto que en master devuelve «ninguna es de prueba».
- **Descartado**: basar la rama en `claude/fase2-integracion`, porque ataría 005 a una rama que todavía cambia y
  mezclaría dos funcionalidades en una revisión.

## R-2 · La semana: de sábado a viernes, no de lunes a domingo

- **Hallazgo**: el semáforo mide de sábado a viernes, pero `weekly-progress` (la ficha del mentor y la de
  administración) arma la semana de lunes a domingo en la zona de la persona (`SeguimientoService.armarSemana`).
- **Decisión**: la ficha del líder usa la **semana del semáforo**, como pidió el dueño. `armarSemana` pasa a recibir
  la ventana (`desde`, `hasta`) en lugar de calcular el lunes. Las fichas del mentor y de administración siguen
  pidiéndola de lunes a domingo y no cambian.
- **Consecuencia para SC-002**: la comparación con la ficha de administración se hace **día por día**: el mismo
  día da los mismos estados y las mismas evidencias. Las semanas no coinciden en sus bordes.
- **Descartado**: pasar todas las fichas a sábado–viernes, porque cambia lo que ven el mentor y el administrador
  sin que nadie lo haya pedido.

## R-3 · Lista del grupo: reutilizar la tabla del semáforo con nombres

- **Hallazgo**: `GET /admin/semaforo/groups/{g}` ya devuelve las personas del grupo con nombre, foto, color, %,
  días y motivo, ordenadas de rojo a verde (`SemaforoDelGrupoService.armarTabla`). Es la misma tabla que ve el
  mentor. MENTOR_LEAD recibe 403 porque el guard es `requireAdminActivo`.
- **Decisión**: un endpoint nuevo `GET /api/v1/leadership/groups/{g}/semaforo` que llama al mismo `armarTabla`
  con el guard `requireLiderazgoActivo` (MENTOR_LEAD, ADMIN o ALCHEMIST activos) y exige un grupo regular con
  mentor (sin el de recepción, FR-001). En la app se reutiliza `VistaSemaforoDelGrupo`.
- **Orden**: `armarTabla` ordena rojo, amarillo, sin datos, verde y después por nombre. FR-007 pide además menor %
  primero dentro de cada color y «no se mide» al final. El orden se ajusta solo en la respuesta del líder
  (un comparador propio sobre la misma lista), para no cambiar la tabla del mentor.
- **«No se mide»**: el padrón del semáforo (`MedicionDeGrupos.padronDe`) ya incluye al personal con programa
  propio que está como APRENDIZ en el grupo. El mentor vigente del grupo queda fuera del padrón. El mentor o la
  guía con programa propio se suman aparte: son acompañantes vigentes del grupo con `ParticipacionPrograma`
  activada. Salen con su % de la semana y `color = NO_SE_MIDE`. Su % se calcula con la misma `ReglaDelSemaforo`
  sobre sus registros (R-6).
- **Descartado**: abrir `/admin/semaforo/groups/{g}` al líder, porque ese prefijo exige `MANAGE_TRAINEES` y lo
  vigila `requireAdminActivo`.

## R-4 · Administración y Alquimista

- **Hallazgo**: ya llegan del grupo a la persona en 4 toques (Administración → Semáforo → grupo → persona,
  `SemaforoGrupoAdminScreen`, del 26/09).
- **Decisión**: para ellos no se construye nada nuevo. Los endpoints `leadership` nuevos también los aceptan
  (`requireLiderazgoActivo`), así la misma pantalla sirve si alguna vez entran por ahí. Su consulta queda
  registrada (FR-016) pero **no** entra en el resumen al mentor (FR-017). Su recorrido actual en Administración no
  se toca y no registra consultas: «desde esta vista» es la del líder.

## R-5 · Evidencias para el líder

- **Hallazgo**: `GET /evidence/{id}` y `/evidence/{id}/url` usan `requireDuenoOAdmin`: el dueño, el mentor
  asignado o ADMIN/ALCHEMIST. MENTOR_LEAD recibe 403. El URL vence a los 10 min. El texto de una evidencia viene en
  `contenidoTexto` del detalle.
- **Decisión**: dar al liderazgo **lectura** de evidencias con una regla nueva en `EvidenciaService`, solo para el
  detalle y el URL: `actor.rol == MENTOR_LEAD && activo`. No se agrega `MANAGE_EVIDENCE`, para que el líder no pueda
  revisar evidencias. Pruebas negativas: aprobar o rechazar con MENTOR_LEAD → 403.
- **Respuestas en línea**: la semana del líder trae, por obligación, `respuestaTexto` (el «¿Qué sentiste?» y el
  resumen de la Pastilla, en `registros_habito.respuesta_texto`) y la lista de evidencias del registro con su tipo.
  La Audioterapia es una evidencia de TEXTO: se lee de su `contenidoTexto`.
- **Rocas**: la semana suma, por día, las acciones del día cumplidas con su evidencia (`destino = ROCA_DIARIA`),
  leídas por un finder de `rocks.api` y de `evidence.api`, sin SQL contra tablas ajenas.
- **Descartado**: usar el listado `GET /evidence?participanteId=` desde la app, porque obliga a la app a cruzar
  registros y días; eso le corresponde al servidor.

## R-6 · Post diario como evidencia, sin migración

- **Hallazgo**: el post diario se cierra sin evidencia. La publicación existe en `publicaciones_muro`, con autor y
  fecha.
- **Decisión**: **derivar** la publicación al leer, no guardar un enlace. Para un registro `COMMUNITY_POST`
  completado, `community.api.PublicacionMuroFinder` gana una consulta: «la primera publicación de este autor en
  este día local». La semana devuelve `publicacionId`. Un dato derivable se deriva en un solo lugar (constitución
  IV). La primera del día es la que cerró el hábito, porque solo cuenta la primera.
- **Punto de partida**: el agente del bug dejó un parche sin probar (11 archivos de `community`, `habits` y `mentoring`, y
  2 pruebas en `SeguimientoServiceTest`) que deriva justamente esto: el `publicacionId` y la entrega `ENTREGADA` a la
  hora de la publicación. Está en `.claude/worktrees/post-diario-ligar-publicacion.patch`. Se aplica como primera
  tarea y se corre en verde antes de seguir.
- **Mostrarla**: `PublicacionMuroFinder.paraCompartir(id)` ya arma una vista de la publicación. Se expone para el
  líder en `GET /leadership/publications/{id}`.
- **Bonus pedido por el dueño** (la ficha de administración sale vacía): `ObligacionDelDia` gana `publicacionId`
  opcional, así que la ficha del mentor y la de administración pueden mostrar «Ver publicación» con el mismo dato.
  Es un campo opcional: una app vieja lo ignora.

## R-7 · Observación al mentor sobre una persona

- **Hallazgo**: `observaciones_mentor` (V89) no tiene persona ni período, y el mentor no las lee en la app. Llegan
  solo si el líder las envía por chat.
- **Decisión**: V109 agrega `persona_id UUID NULL` y `semana_desde DATE NULL`. Son nullable, porque las
  observaciones de antes no los tienen. El request suma `personId` y `weekStart`, ambos opcionales. El servicio
  comprueba que la persona esté hoy en un grupo que ese mentor acompaña. El texto que va por chat lo arma la app
  como hoy y empieza con el nombre de la persona.
- **Descartado**: una tabla nueva de «observaciones sobre personas», porque sería un segundo buzón (RL-17).

## R-8 · Registro de consultas y resumen diario

- **Tabla nueva** `consultas_de_ficha` (V109): `id, consultor_id, rol_consultor, persona_id, grupo_id, mentor_id
  NULL, consultada_en`. Es append-only, con un trigger que rechaza UPDATE y DELETE (patrón de `cambios_auditados`
  de la fase 2) y sin FK al actor, para que sobreviva al borrado de cuentas. La escribe la lectura de la semana del
  líder, una vez por persona por llamada.
- **Resumen**: `ResumirConsultasDelLiderScheduler`, cada hora en el minuto 10, `@SchedulerLock`, paginado por
  mentor. La regla de dominio `ReglaDelResumenDeConsultas.toca(ahora, zonaDelMentor)` es verdadera entre las 20:00 y
  las 20:59 locales. Junta las consultas del líder (`rol_consultor = MENTOR_LEAD`, sin el propio mentor) del día
  local del mentor. Clave de deduplicación `consultas-lider:<mentorId>:<fecha>`, que hace idempotente una corrida
  repetida.
- **Zona del mentor**: `participantes_programa.timezone` si tiene programa; si no, la del primer grupo que
  acompaña (`GrupoBasico.zonaHoraria`). Por defecto, America/Lima.
- **Aviso**: tipo nuevo `REVISION_DEL_LIDER` (V109 `ALTER TYPE … ADD VALUE`). Título «Tu líder revisó a tu
  grupo», cuerpo «Hoy revisó a Ana y Luis» (hasta 3 nombres y «y N más»). Ruta `/mentor/groups/{g}/semaforo` del
  grupo con más personas consultadas. Respeta `preferencias_notificacion`. En Yo → Notificaciones aparece un
  interruptor «Revisiones de tu líder», solo para quien acompaña un grupo.
- **Fase 2**: el scheduler usa `ProcesosConInterruptor.saltaEsteTurno(…, "lider.cumplimiento", …)` cuando se integre.

## R-9 · Chat 1 a 1

- **Hallazgo**: `POST /chat/conversations/direct` no filtra por rol: el líder ya puede abrir el 1 a 1 con
  cualquier persona activa.
- **Decisión**: no cambia nada en el backend. La app reutiliza `abrirConversacionDirecta` + `irAPestana`, como
  `AlumnoScreen`.

## R-10 · Compatibilidad con el backend anterior

- `ResumenPorGruposResponse` gana `puedeAbrirse` (boolean, opcional) por grupo: `true` cuando el grupo tiene
  mentor y la funcionalidad está prendida. La app abre la tarjeta solo si viene `true`. Con un backend anterior el
  campo falta y las tarjetas siguen sin abrirse (FR-021).

## R-11 · Permisos y pruebas de autorización

- MENTOR_LEAD está en modo sombra en el interceptor (no deniega). Por eso cada endpoint nuevo tiene **guard de
  servicio** además de `@RequiresPermission(VIEW_MENTOR_CORPS)`, el permiso de lectura del líder que ya usa
  `leadership`.
- Pruebas negativas por endpoint: TRAINEE → 403, MENTOR → 403, `SUSPENDED` → 403, persona fuera del grupo → 404
  (RL-08), grupo de recepción → 404. Escrituras del líder sobre la persona → 403 (SC-003): completar un registro
  ajeno, aprobar o rechazar una evidencia, reasignar y cambiar el rol.
- Rutas bajo `/api/v1/leadership/**`, que ya cubre `SecurityConfig`: no hace falta tocar `RutasCubiertasPorElFiltroTest`.
