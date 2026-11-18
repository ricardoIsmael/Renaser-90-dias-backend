# Tareas: Rediseño del rol de líder de mentores

**Entrada**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/api.md](contracts/api.md), [quickstart.md](quickstart.md)

**Pruebas**: obligatorias (constitución III) y **primero**: cada prueba falla antes del código y lleva la marca `// spec 056 FR-NNN` (Jest), `# spec 056 FR-NNN` (Maestro) o `@DisplayName("spec 056 FR-NNN …")` (Java).

**Rutas**: sin prefijo = backend (worktree `wt-056-lider-de-mentores`, raíz Java `src/main/java/com/renaser/os/` abreviada `…/`). `app:` = app (worktree `.claude/worktrees/056-lider-de-mentores`).

**Formato**: `[ID] [P?] [Carril] Descripción`. Carriles (regla 11/12 del arnés, uno por agente): **BA** lecturas del servidor, **BB** acciones del servidor, **BC** documento, **A1** ficha compartida, **A2** pantallas del líder. `[P]` = en paralelo con otros carriles.

---

## Fase 1: Preparación

- [x] T001 Worktrees `056-lider-de-mentores` en los dos repos con su grafo (`worktree-nuevo.sh`)
- [x] T002 [P] Agregar `"056"` con FR-001…FR-047 y SC-001…SC-008 a `specs/trazabilidad.json` como exigidos
- [x] T003 [P] Reservar V199 para BC (ningún otro carril crea migraciones)

---

## Fase 2: Servidor

### Carril BA — lecturas (`leadership`, sin migración) [P]

- [x] T010 Prueba que falla: `GET /leadership/people/{p}/dias` devuelve los colores del día igual que el `/dias` del mentor para la misma persona y rango, recorta a [Día 1, hoy] y da 403 fuera del alcance (FR-017, FR-018, SC-004)
- [x] T011 `DiasDeLaPersonaService` sobre `points.api.DiasDelSemaforoFinder` + controlador
- [x] T012 Prueba que falla: `GET /leadership/people/{p}/semana` con la forma de la 055 (sábado a viernes, `diaUno`, `pilar`), 404 `SIN_PROGRAMA` para un mentor que no sigue su programa (FR-014, FR-021, FR-022)
- [x] T013 `SemanaDeLaPersonaService` reutilizando el armado de la 055 por puerto + controlador
- [x] T014 Prueba que falla: `GET /leadership/people/{p}/objetivos` (aprendiz y mentor; solo lectura; 403 fuera del alcance) (FR-027)
- [x] T015 `ObjetivosDeLaPersonaService` sobre `rocks.api.RocasDelAprendizFinder` + controlador
- [x] T016 Prueba que falla: padrón y ficha del mentor traen `cupo {acompanados, maximo}` (suma de cupos de sus grupos) y `porColor`; `GET /leadership/mentors/por-resolver` (FR-002, FR-003, FR-004)
- [x] T017 Implementar cupo derivado, `porColor` y `por-resolver` (umbral 60 % del reporte)

### Carril BB — acciones (`users`, `community`, `support`, `chat`, `notifications`) [P]

- [x] T020 Cambiar `UserRoleTest.mentorLeadNoEsAdministrador` y `MatrizPorRolEnTodoEndpointTest` para exigir `MANAGE_CELLS` en MENTOR_LEAD (FR-010); prueba que falla: el líder crea grupo, asigna mentor y suma/retira aprendices de su formación y recibe 403 `FUERA_DE_SU_ALCANCE` en otra (FR-008, FR-009, FR-010)
- [x] T021 `MANAGE_CELLS` en `PERMISOS_MENTOR_LEAD`; `requireAdmin` de `CelulaService`, `SumarAprendizAGrupoService`, `SumarMentorAGrupoService`, `ComposicionDeCelulaService` acepta al líder dentro de `AlcanceDeFormacionFinder`; javadoc de `UserRole` con el rastro de CL-04 → decisión del 18/11; `capacity` en `CelulaDashboardResponse`
- [x] T022 Prueba que falla: `chat.api.MensajeDirectoPort` abre o reutiliza la conversación 1 a 1 y guarda el mensaje (que dispara su push)
- [x] T023 Puerto `MensajeDirectoPort` en `chat/api` y adaptador sobre `CrearConversacionDirectaUseCase` + `EnviarMensajeUseCase`
- [x] T024 Prueba que falla: guardar una observación manda el mensaje y el aviso al mentor una sola vez (con `messageId` de una app vieja no reenvía) (FR-025, SC-005)
- [x] T025 `ObservacionDelLiderEvent` + listener push en `notifications` + mensaje por `MensajeDirectoPort` desde `ObservacionesDeMentorService`
- [x] T026 Prueba que falla: `POST /leadership/people/{p}/avisar-al-mentor` (mensaje con enlace + aviso, `SIN_MENTOR`, idempotente por `operationKey`) (FR-019)
- [x] T027 Implementar avisar al mentor
- [x] T028 Prueba que falla: el líder de la formación responde una consulta; `respondidoPor {id, nombre}`; el aprendiz recibe el aviso; `POST /leadership/tickets/{id}/recordar` (FR-029, FR-030)
- [x] T029 Relajar `TicketMentorService.responder` para el líder de la formación, exponer `respondidoPor`, endpoint `recordar`

### Carril BC — documento (después de T013) 

- [x] T030 V199 `documentos_semanales` y `descargas_de_documentos` (data-model.md)
- [x] T031 Prueba que falla (dominio): `DocumentoSemanal` arma la ruta determinista y la huella SHA-256; misma huella → se reutiliza (FR-039)
- [x] T032 Prueba que falla (plantilla): el HTML del aprendiz trae las secciones de FR-035 en orden, escapa todo texto de personas, nunca contiene notas privadas ni «90 días» escrito; semana vacía → «Sin actividad esta semana» (FR-035, FR-037, FR-038)
- [x] T033 Prueba que falla (plantilla): el HTML del mentor trae FR-036 y, si sigue su programa, su semana
- [x] T034 `PlantillaDelDocumento` (text blocks + `HtmlUtils.htmlEscape`, A4 imprimible)
- [x] T035 Prueba que falla (servicio + REST): `POST /leadership/people/{p}/documento`, `/mentors/{id}/documento`, `/report/documento`; enlace de 10 minutos; registro de cada pedido; permisos por rol (mentor solo de los que acompaña hoy) (FR-032, FR-039, FR-040, FR-041, FR-047, SC-006, SC-007)
- [x] T036 `DocumentoSemanalService` (datos → huella → reutiliza o `subir(text/html)` → `firmarLectura(10 min)` → registra) + controladores

**Punto de control servidor**: `scripts/verificar.sh` en verde (incluye `ArchitectureTest`); contratos de `contracts/api.md` cumplidos.

---

## Fase 3: App

### Carril A1 — ficha compartida [P con el servidor]

- [ ] T040 Prueba que falla: `lecturasDeLaFicha(origen)` da las rutas del mentor para `{mentor, grupoId}` y las de liderazgo con y sin grupo para `{lider, grupoId|null}` (FR-014, FR-017)
- [ ] T041 `app:src/features/mentor/utils/lecturasDeLaFicha.ts` + api del líder (`semana`, `dias`, `objetivos`)
- [ ] T042 Extraer `app:src/features/mentor/components/FichaDePersona.tsx` del cuerpo de `AlumnoScreen` (hooks con funciones `cargar`; ranuras cabecera, pie, después); `AlumnoScreen` queda como envoltorio y **todas las pruebas de la 055 pasan sin cambios**
- [ ] T043 Prueba que falla + implementación: objetivos plegables en la ficha (FR-016, FR-027)
- [ ] T044 Prueba que falla + implementación: «Descargar semana» en la ficha del mentor (055) con `useDescargarSemana` (abre el enlace; un toque, un pedido) (FR-047)

### Carril A2 — pantallas del líder (con los contratos escritos)

- [ ] T050 Componentes con sus pruebas: `EstadoConForma` (× ! ✓ · con etiqueta accesible), `AnilloDeCupo` (progressbar), `BarraDeColores`, `FilaPorResolver`, `SeccionPlegable` (FR-003, FR-043, FR-044)
- [ ] T051 Prueba que falla + `MisMentoresScreen`: sin insignia, Por resolver (filas ocultas sin datos), lista con cupo y colores, suspendido apagado, accesos (FR-001…FR-005, SC-001)
- [ ] T052 Prueba que falla + `FichaDelAprendizDelLider`: `FichaDePersona` origen líder, nota en lectura, Avisar a su mentor (sin mentor → sin botón), Descargar semana (FR-014…FR-020)
- [ ] T053 Prueba que falla + `FichaDelMentorScreen`: misma ficha con sus datos; sin programa → línea #202; suspendido; estrella; «Como mentor» (grupos con barra, su evaluación, consultas, lo que le dijiste (sin «evaluaciones que debe», E2E 18/11)); Alertar; Descargar semana (FR-021…FR-026)
- [ ] T054 Prueba que falla + `GrupoDelLiderScreen` rehecho: cabecera con forma y frase, 4 contadores, orden de la 055, + Aprendices, Cambiar mentor (FR-011…FR-013)
- [ ] T055 Prueba que falla + `GruposDelLiderScreen` (filtros, Asignar) y `CrearGrupoScreen` (3 pasos, llenos apagados, resumen, validación de nombre) sobre `adminApi` (FR-006…FR-009, SC-002, SC-003)
- [ ] T056 Prueba que falla + `ConsultasDelLiderScreen`: Sin responder/Respondidas, Recordar al mentor, Responder yo (FR-028…FR-030)
- [ ] T057 Prueba que falla + `ReporteScreen`: anillo, ranking con color, Exportar (FR-031, FR-032)
- [ ] T058 Prueba que falla + hoja «Programar sesión» con `audienciaPrevia` en `FormularioDelEvento` (FR-033, FR-034)
- [ ] T059 Navegación del panel (`PantallaDelPanelDeRol`), volver del sistema un nivel (FR-046), `pantallasConBarraAlDesplazar`, reescribir las pruebas viejas del líder (`diasDelMentor`, `avisarYEscribirle`, `delGrupoALaPersona`, `panelesDeRol`…) a lo nuevo; Administración sin cambios
- [ ] T060 Guardas de texto en verde: sin voseo, tipo oración, sin relleno (FR-045), sin «90 días»

**Punto de control app**: `npm run verificar` en 0 (tipos, Jest, trazabilidad 056 contra el backend).

---

## Fase 4: Cierre

- [ ] T070 Flujos de Maestro del líder (Historias 1–6) armados desde los de la 055; corridos en el emulador con el backend local; cabecera `# corrido: AAAA-MM-DD`
- [ ] T071 mobile-mcp: todos los estados y roles (sin mentores, suspendido, sin programa, Día 0, sin internet), capturas; logs del backend y `adb logcat` sin errores nuevos
- [ ] T072 Docs: `docs/decisiones/liderazgo-y-administracion.md` (app) con la entrada del 18/11 y su fila; registro de decisiones del backend (CL-04 → 18/11, cupo derivado, documento para siempre); docs de módulo
- [ ] T073 PR del backend (verificar + resumen) y, después de su despliegue, PR de la app; `grafo-al-dia.sh` en los dos worktrees

## Dependencias

T002–T003 → todo. BA y BB en paralelo. BC después de T013. A1 en paralelo con el servidor (prueba con respuestas simuladas). A2 después de T042 y con contratos. Fase 4 al final, de a uno en el emulador.
