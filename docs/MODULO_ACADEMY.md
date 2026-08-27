# Módulo `academy` — Cursos, Secciones, Lecciones, Clase Diaria, Academia Adaptativa

## 0. Estado

Construido: dominio completo (4 agregados), casos de uso de lectura + progreso + `AccesoCursoFinder`,
persistencia JPA para las 9 tablas propias del módulo, controllers REST, tests unitarios de dominio y
de `application/services` con Mockito. **No corrí `./mvnw clean test`** (prohibido para este agente —
lo corre el supervisor). Compilación revisada a mano, import por import; puede haber algún error de
tipeo que solo Maven detecta.

**Corrección del supervisor durante la construcción (prioridad sobre el encargo original):** la
migración Flyway `V3__academy_roles_permitidos_curso.sql` y la decisión **D-40** que reemplazaba
`roles_permitidos_curso.rol_id smallint` por una columna enum quedaron **canceladas**. El baseline
(`V1__baseline_renaser.sql`, 90 tablas) es inmutable en esta fase: no se creó ninguna migración, no
se tocó ninguna columna, no hay datos semilla. Ver decisión **AC-05** (§5).

**2026-08-24 — Cierre de brechas D-41 (la app deja de hablar con Supabase directo).** El dueño del
proyecto decidió que la app solo hable con la API de Spring — ver `CLAUDE.MD`. Se auditaron las 3
brechas de `academy` reportadas contra `src/services/cursos.ts` del repo RN y se cerraron las 3: la
RPC `catalogo_cursos_bloqueados` (nuevo endpoint, AC-15), la RPC `progreso_cursos` (ya cubierta por
`GET /api/v1/cursos`, sin endpoint nuevo, AC-14) y el DELETE que faltaba para "descompletar" una
lección (AC-16). Tests unitarios (dominio + Mockito) y de integración con Testcontainers para las
dos consultas que reemplazan las RPC. Detalle en §1.8, §3.2 y §5.

**2026-08-24 — Cierre D-43 (progreso de cursos EN LOTE para el Ranking General).** Nuevo contrato
público `PorcentajeCursosFinder` (`academy/api/`), implementado por `PorcentajeCursosService`, para
que `points` calcule el componente "cursos" (15%) del Ranking General sin reproducir el incidente real
de producción documentado en `prisma/migrations/general_ranking_scores_function.sql` del repo viejo
("Too many database connections opened" con ~30 cuentas activas, causado por 1 consulta por aprendiz
en `sumarProgresoCursos`). Cálculo en dominio puro (`PorcentajeCursos`), 4 consultas EN TOTAL sin
importar cuántos participantes se pidan (nunca una por cabeza) — incluye un método nuevo,
`LoadProgresoLeccionPort.completadasPorCursoEnLote`, y su test de integración verifica con
estadísticas de Hibernate que dispara exactamente 1 consulta. **Corregido el mismo día:** el contrato
devuelve `Map<UserId, BigDecimal>` con escala 1 (no `Integer`) — replica bit a bit
`round(completadas/total*1000)/10` de `cursos_pct`, porque el score final pondera ese decimal antes de
redondear de nuevo. Detalle en §4.1bis y §5 (AC-17).

---

## 1. Paso 0 — reglas extraídas del código viejo

Repo viejo: `C:\Users\Usuario\Documents\Backend90dias\RenaserBack`. Todo lo de abajo se verificó
leyendo el código completo, no en diagonal, y contra sus propios tests unitarios cuando existían.

### 1.1 Qué se leyó

- `src/features/cursos/{repository,service,schema,adminService}.ts` completos + los 5 archivos de
  `__tests__/` (en particular `puedeVerCurso.test.ts`, la fuente de verdad de la regla de gate).
- `src/features/clase-diaria/{repository,service,schema}.ts` completos + `service.test.ts`.
- `src/features/academia-adaptativa/{repository,service}.ts` + `ai/recommendClass.ts` (solo para
  entender el contrato, no se porta — Ola 5).
- `src/features/post-program/{repository,service}.ts` — **no pertenece a `academy`**, ver §1.6.
- `docs/FEATURE_ACADEMY.md` del repo viejo — es un documento de **diseño previo, nunca implementado**
  (habla de `ClassCatalog`/`PointsLog`, modelos que no existen en el código real). Se descartó como
  fuente y se usó el código real (`cursos/`, que sí corre en producción) como única fuente de verdad.
- App RN: `src/types/cursos.ts`, `src/services/cursos.ts`, `src/services/claseDiaria.ts`,
  `src/services/academia.ts`, `app/(app)/curso/[id].tsx`, `app/(app)/leccion/[id].tsx` — para el
  formato EXACTO del wire que la app ya consume (ver AC-03, §5).

### 1.2 El gate de catálogo — `puedeVerCurso` (`cursos/repository.ts:731-747`)

```
puedeVerCurso(curso, rol, programDay, _asignadoHistoricamente):
  si roles_permitidos no está vacío y rol no está en la lista → false
  si dia_desbloqueo != null y rol == TRAINEE:
      si (programDay ?? 0) < dia_desbloqueo → false
  devolver curso.publicado && curso.acceso === 'abierto'
```

Verificado contra `__tests__/puedeVerCurso.test.ts` línea por línea (17 casos, todos portados a
`CursoTest.java`). Dos hallazgos no obvios, ambos ya vigentes en producción, no introducidos por esta
migración:

- **El parámetro `_asignadoHistoricamente` no altera el resultado.** El propio código lo dice:
  *"Compatibilidad temporal con llamadas antiguas; ya no altera el acceso"* (repository.ts:736-737).
  El test `'restringido CON asignación explícita → visible'` en realidad **espera `false`** — el
  título del test quedó desactualizado, la aserción no. → decisión **AC-01**.
- **Con la regla de arriba, un curso `acceso = 'restringido'` NUNCA es visible por catálogo**, tenga o
  no asignaciones. No quedó claro si es intencional o un bug latente heredado. → decisión
  **AC-02**, con pregunta abierta en §6.

### 1.3 El gate de sección — `puedeVerSeccion` (`cursos/repository.ts:751-757`)

```
puedeVerSeccion(diaDesbloqueo, rol, programDay):
  devolver diaDesbloqueo == null || rol != TRAINEE || (programDay ?? 0) >= diaDesbloqueo
```

Las lecciones heredan el `dia_desbloqueo` de su **sección** (nunca tienen uno propio); las lecciones
sueltas (sin sección) no tienen gate adicional. Verificado en `aLite`/`aplicarProgresionDeSecciones`
(`repository.ts:236-238`, `service.ts:19-50`) y portado a `SeccionCursoTest.java`.

### 1.4 Progreso — `listarMisCursosAlumno` (`repository.ts:759-802`)

Por cada curso accesible: `total_lecciones` (todas las lecciones del curso, sin gate — el catálogo
completo del curso) y `completadas` (filas en `leccion_progreso` del usuario que pertenecen a ese
curso). `ultima_leccion_id` está **fijo en `null`** en el código viejo — no hay tracking de "última
vista". Se conserva el campo en el wire por fidelidad, nunca se completa (ver
`ConsultarMisCursosUseCase.ProgresoCurso`).

### 1.5 Clase Diaria — `findClaseDiaria` (`clase-diaria/repository.ts:168-233`)

1. Si `programDay < 1 || programDay > 90` → sin clase (rango de programa).
2. Cursos publicados con `dia_desbloqueo <= programDay`.
3. De sus secciones, las que tienen `dia_desbloqueo <= programDay`.
4. Se elige la de **`dia_desbloqueo` más reciente** (desempate: `dia_desbloqueo` del curso más
   reciente) — porque a partir del día 15 las secciones representan RANGOS
   ("CICLO 2 (DÍA 17-25)"), no un día puntual (comentario original, `repository.ts:154-166`,
   con la cifra de verificación real del equipo: *"62 de 90 días"* fallaban con match exacto).
5. Dentro de esa sección, la lección cuyo título matchea `/\bclase\b/i`; si ninguna, la primera por
   `orden`.
6. Después de encontrarla, se vuelve a correr el gate completo del curso (`verificarAccesoCurso`) — si
   falla, es un 403 real (`clase-diaria/service.ts:30-33`), no un "coming soon".

Portado 1:1 a `ClaseDiariaService.buscarClaseDiaria`, con tests en `ClaseDiariaServiceTest.java`.

### 1.6 Lo que NO pertenece a `academy` (aunque el código viejo lo tenga cerca)

- **`post-program`** (`isProgramCompleted`, `postProgramDay`, `programCompletedAt`): son columnas de
  `participantes_programa` (dueño: `users`), no tocan `cursos`/`lecciones` en ningún punto. Fuera de
  alcance total de este módulo.
- **`academia-adaptativa`**: usa `RadarEntry` (energía/ánimo del aprendiz — tabla que no pertenece a
  `academy`) + Gemini. Ola 5. Ver §5 (AC-12) y §6.

### 1.7 Escritura directa desde la app — `leccion_progreso`

`src/services/cursos.ts: marcarLeccionCompletada`/`desmarcarLeccion` escriben directo contra Supabase
(RLS), sin pasar por REST. Reemplazado por `POST /api/v1/lecciones/{id}/complete`
(`CompletarLeccionUseCase`) — **cambio de release coordinado**: hasta que la app apunte a este
endpoint, sigue escribiendo directo. `desmarcarLeccion` (borrar progreso) **se portó el 2026-08-24**
(`DescompletarLeccionUseCase`, AC-16, ver §1.8) — dejó de estar fuera de alcance.

### 1.8 Cierre D-41 — las dos RPC de Supabase y el DELETE que faltaban

Repetí el mismo método del §1 (código real, no doc de diseño) para las 3 brechas reportadas contra
`src/services/cursos.ts` del repo RN.

- **Busqué el cuerpo SQL de las RPC `catalogo_cursos_bloqueados` y `progreso_cursos` y no existe en
  ningún repo local.** Grep exhaustivo (`catalogo_cursos_bloqueados`, `progreso_cursos`,
  case-insensitive) contra `C:\Users\Usuario\Documents\Backend90dias\RenaserBack` completo
  (`prisma/migrations/**`, `src/**`, `docs/**`, `scripts/**`) y contra `C:\Users\Usuario\Documents`
  entero (excluyendo `node_modules`): cero resultados. Las únicas migraciones SQL con funciones reales
  en ese repo son `is_conversation_participant`, `match_knowledge`, `norm_title`,
  `general_ranking_scores` — ninguna es de cursos. Estas dos RPC se crearon directo en Supabase (SQL
  Editor / consola), nunca se versionaron en un repo. **No inventé su lógica**: la reconstruí desde el
  comportamiento ya verificado en §1.2–§1.4 (mismo gate `puedeVerCurso`, mismo cálculo de progreso) y
  desde el contrato de tipos que la app ya consume (`src/types/cursos.ts: CursoBloqueado`,
  `ProgresoCurso`), citado línea por línea abajo.
- **`progreso_cursos` (`cursos.ts:152`, fallback de `listarMisCursos`) no necesitó endpoint nuevo —
  decisión AC-14.** Es un fallback que la app solo usa si la llamada primaria a `GET /api/v1/cursos`
  falla; ese endpoint YA devuelve `progreso` embebido por curso (`MiCursoResponse.progreso`,
  `CatalogoAcademyService.misCursos`, construido antes de este cierre) con exactamente la forma de
  `ProgresoCurso` (`curso_id`, `total_lecciones`, `completadas`, `ultima_leccion_id`). Agregar un
  segundo endpoint solo para esa RPC hubiera duplicado la misma consulta sin necesidad.
- **`catalogo_cursos_bloqueados` (`cursos.ts:180`, `listarCursosBloqueados`) SÍ era una brecha real** —
  a diferencia de `progreso_cursos`, la app la llama SIEMPRE (no como fallback), directo contra
  Supabase, en cada carga de `useMisCursos.ts`. Nuevo endpoint `GET /api/v1/cursos/bloqueados`
  (AC-15). Verifiqué el shape exacto contra `src/types/cursos.ts:89-98` (`CursoBloqueado`) y contra
  `src/features/cursos/lib/lista.ts: fusionarCatalogo` (cómo la app fusiona esta lista con la de
  `misCursos`): `portada_url` viaja SIN firmar — la app firma la escalera completa (disponibles +
  bloqueados) en una sola llamada de cliente (`resolverMediaUrls`, `useMisCursos.ts:71`), así que
  firmarla en el backend hubiera sido trabajo descartado.
- **DELETE para "descompletar"** (`cursos.ts:463-472`, usado en `app/(app)/leccion/[id].tsx:279`
  dentro de `alternarCompletada` al destildar una lección ya vista). Nuevo
  `DELETE /api/v1/lecciones/{id}/complete` (AC-16), inverso simétrico de
  `POST /api/v1/lecciones/{id}/complete` con la misma exigencia de acceso vigente (AC-07).

---

## 2. Progreso del participante

Copia propia del patrón ya usado por `rocks`/`phasecontracts`
(`ConsultarProgresoParticipanteAcademyPort`), con una diferencia deliberada — **AC-04**: el `JOIN`
contra `participantes_programa` es **LEFT**, no INNER. `rocks`/`phasecontracts` son exclusivos de
TRAINEE (sin fila de programa no hay nada que hacer, INNER está bien). `academy` es accesible para
los 5 roles (`requireRole(... ['TRAINEE','MENTOR','MENTOR_LEAD','ADMIN','ALCHEMIST'])` en el repo
viejo, ver rutas `cursos/route.ts`), y la fila de `participantes_programa` es **opcional** para todo
rol que no sea TRAINEE (comentario de la tabla en el baseline). Con INNER, un MENTOR sin inscripción
en el programa habría recibido 404 al pedir el catálogo — comportamiento incorrecto.

`diaPrograma`/`zona` son `Integer`/`ZoneId` **nullable** por la misma razón.

---

## 3. Qué se construyó

### 3.1 Agregados de dominio

| Paquete | Clases | Nota |
|---|---|---|
| `domain/model/curso/` | `Curso`, `SeccionCurso`, `Leccion`, `RecursoLeccion` | Un solo agregado — **AC-06**: las 4 clases cuentan una sola historia (el árbol de un curso), ninguna tiene sentido sin `Curso`, mismo criterio que `buckpal` (CLAUDE.MD §5.1.2). `Curso`/`SeccionCurso` llevan la regla de gate como método (`visibleEnCatalogoPara`) |
| `domain/model/asignacion/` | `AsignacionCurso`, `Grupo`, `MiembroGrupo` | `AsignacionCurso.vigente(Instant)` + arco exclusivo usuario⊕grupo validado en el constructor |
| `domain/model/progreso/` | `ProgresoLeccion` | Clave natural (usuarioId, leccionId) |
| `domain/model/recomendacion/` | `RecomendacionAcademia` | Cache de Academia Adaptativa (Ola 5) |

### 3.2 Casos de uso

| Caso de uso | Endpoint | Servicio |
|---|---|---|
| `ConsultarMisCursosUseCase` | `GET /api/v1/cursos` | `CatalogoAcademyService` |
| `ConsultarCursosBloqueadosUseCase` | `GET /api/v1/cursos/bloqueados` | `CatalogoAcademyService` |
| `ConsultarCursoDetalleUseCase` | `GET /api/v1/cursos/{id}` | `CatalogoAcademyService` |
| `ConsultarSeccionesCursoUseCase` | `GET /api/v1/cursos/{id}/secciones` | `CatalogoAcademyService` |
| `ConsultarMotivoBloqueoCursoUseCase` | `GET /api/v1/cursos/{id}/preview` | `CatalogoAcademyService` |
| `ConsultarLeccionUseCase` | `GET /api/v1/lecciones/{id}` | `CatalogoAcademyService` |
| `ConsultarMotivoBloqueoLeccionUseCase` | `GET /api/v1/lecciones/{id}/preview` | `CatalogoAcademyService` |
| `CompletarLeccionUseCase` | `POST /api/v1/lecciones/{id}/complete` | `CatalogoAcademyService` |
| `DescompletarLeccionUseCase` | `DELETE /api/v1/lecciones/{id}/complete` | `CatalogoAcademyService` |
| `ConsultarClaseDiariaUseCase` | `GET /api/v1/classroom/clase-diaria` | `ClaseDiariaService` |
| `ConsultarRecomendacionDiariaUseCase` | `GET /api/v1/academia/recomendacion` | `RecomendacionService` |
| `AccesoCursoFinder` (api) | (sin REST — consumido por `calendar`) | `AccesoCursoService` |
| `PorcentajeCursosFinder` (api) | (sin REST — consumido por `points`) | `PorcentajeCursosService` |

Todos los endpoints reciben el actor por header `X-Actor-Id` (no hay filtro JWT todavía, bloqueante
del usuario) y devuelven 403 vía `NotAuthorizedException` → `GlobalExceptionHandler` cuando el actor
está `SUSPENDIDO` o no tiene acceso al curso/lección.

### 3.3 Persistencia

Las 9 tablas propias de `academy` (`cursos`, `roles_permitidos_curso`, `secciones_curso`, `lecciones`,
`recursos_leccion`, `grupos`\*, `miembros_grupo`, `asignaciones_curso`, `progreso_lecciones`,
`recomendaciones_academia`) tienen su JpaEntity + mapper. `grupos` (\*) no tiene adapter propio — ver
§6, AC-10.

---

## 4. Integraciones

### 4.1 `AccesoCursoFinder` (api pública de `academy`)

Ya existía el contrato (`academy/api/AccesoCursoFinder.java`, escrito por el supervisor); se
implementó en `AccesoCursoService`: unión de asignaciones directas vigentes + miembros de grupos con
asignación vigente, filtrando por `AsignacionCurso.vigente(Instant.now())`. **Esto es una pregunta
DISTINTA de "puede este actor ver el curso en su catálogo"** (§1.2/AC-01) — `calendar` la usa para
resolver la audiencia `CURSO` de un evento, un concepto administrativo de asignación, no el gate de
catálogo del aprendiz. Documentado en el javadoc de `AccesoCursoService` para que no se confundan.

### 4.1bis `PorcentajeCursosFinder` (api pública de `academy`) — cierre D-43

Contrato nuevo (`academy/api/PorcentajeCursosFinder.java`), implementado en `PorcentajeCursosService`:
`Map<UserId, BigDecimal> porcentajePorParticipante(Collection<UserId> participantes)`, el componente
"cursos" (15%) del Ranking General de Comunidad (50% hábitos + 35% rocas + 15% cursos). Ver decisión
**AC-17** (§5) para el detalle completo — acá el resumen de por qué existe: el backend viejo resolvía
esto con `sumarProgresoCursos` (`cursos/repository.ts:824-849`), **1 consulta a Supabase por
aprendiz**, y con las ~30 cuentas activas del programa ya devolvía *"Too many database connections
opened"* (incidente real, documentado en la cabecera de
`prisma/migrations/general_ranking_scores_function.sql` del repo viejo, que terminó resolviendo el
cálculo completo — hábitos + rocas + cursos — con un procedimiento almacenado en producción).

**Corrección 2026-08-24 (mismo día, tras revisión del supervisor):** la primera versión de este
contrato devolvía `Map<UserId, Integer>` — perdía el decimal que el propio `cursos_pct` de
`general_ranking_scores_function.sql` calcula (`round(completadas/total*1000)/10`, escala 1) y que el
score final pondera ANTES de redondear de nuevo. Corregido a `BigDecimal` escala 1 — ver AC-17,
punto 2. `rocks` llegó por su cuenta a la misma conclusión para su propio componente, así que los tres
contratos del Ranking General (`habits`/`rocks`/`academy`) quedan consistentes entre sí.

**El culpable específico era el progreso de cursos** (`sumarProgresoCursos`), que vive en este
módulo — de ahí el encargo. La solución acá **no baja a SQL nativo ni a procedimientos almacenados**
(la BD es inmutable, AC-05): el N+1 se resuelve en Java, con **4 consultas en total, nunca una por
participante**:

1. `ParticipacionProgramaFinder.usuariosActivosConDiaPrograma(Set.of(TRAINEE))` — día de programa de
   TODOS los TRAINEE activos (ya existía, `users.api`, `LEFT JOIN`; se filtra en memoria a los
   `participantes` pedidos).
2. `LoadCursoPort.listarTodos()` — catálogo completo (ya existía).
3. `LoadLeccionPort.contarTotalPorCurso()` — total de lecciones por curso (ya existía).
4. `LoadProgresoLeccionPort.completadasPorCursoEnLote(Collection<UserId>)` — **método nuevo**, espejo
   en lote de `completadasPorCurso(UserId)`: una sola query nativa con `usuario_id IN (?1)` agrupando
   por `(usuario_id, curso_id)`, en vez de una consulta por usuario.

El cálculo del porcentaje en sí es **dominio puro**: `PorcentajeCursos.calcular(total, completadas)`
en `academy/domain/model/progreso/PorcentajeCursos.java` — sin Spring, sin SQL, incluida la regla "sin
cursos accesibles → 100". `PorcentajeCursosService` solo trae los crudos (reusando
`Curso.visibleEnCatalogoPara`, el MISMO gate del catálogo, AC-01) y arma el conjunto de cursos
accesibles por participante antes de sumar.

### 4.2 `users.api.UserRole`/`UserStatus`

Importados directo en `Curso`/`SeccionCurso` (gate por rol) y en `RolesCatalogo` (traducción
`rol_id` → `UserRole`) — permitido explícitamente por el encargo.

### 4.3 `shared.application.ports.out.AlmacenamientoPort`

Usado para firmar `portada_url` (`AC-08`, ver §5) cuando no es una URL http(s) externa (portadas
de Skool). Con el adapter NoOp actual (faltan credenciales AWS, D-34) devuelve un placeholder —
inofensivo hoy porque la app **firma la portada del lado cliente** contra el bucket viejo de Supabase
(`src/services/cursos.ts: resolverMediaUrl`), sin usar el campo que devuelve este backend.

---

## 5. Decisiones propias (`AC-`)

- **AC-01** — Las asignaciones (`asignaciones_curso`) NO alteran la visibilidad de catálogo. Fiel al
  comportamiento real de `puedeVerCurso` (§1.2). `AccesoCursoFinder` (§4.1) resuelve una pregunta
  distinta.
- **AC-02** — Con las reglas actuales, un curso `RESTRINGIDO` nunca es visible por catálogo, para
  ningún rol. Se replicó tal cual (es el comportamiento en producción hoy), con pregunta abierta en
  §6: ¿es intencional, o el campo `acceso` quedó a medio terminar?
- **AC-03** — El wire de `academy` (`CursoResponse`, `LeccionResponse`, `SeccionConLeccionesResponse`,
  etc.) usa **español, `snake_case`**, no inglés — a diferencia del criterio general de D-36
  ("hablar el idioma viejo, que es inglés"). Motivo: `cursos`/`lecciones` **nunca vivieron en
  Prisma** — siempre hablaron PostgREST/Supabase directo, con nombres de columna en español
  (verificado en `src/types/cursos.ts` y `src/services/cursos.ts` de la app RN, que leen
  `dia_desbloqueo`, `portada_url`, `acceso: "abierto"|"restringido"` tal cual). D-36 describe
  correctamente el caso de `support`/`rocks` (que sí vivían en Prisma, en inglés) pero no aplica
  literal acá — la regla de fondo (traducir en la frontera web, nunca en dominio) sí se respeta:
  domain/persistencia usan los enums nuevos (`AccesoCurso.ABIERTO`, Postgres `'ABIERTO'`), solo el DTO
  REST traduce a minúscula. Dos excepciones puntuales dentro del mismo wire, preservadas tal cual
  porque así las devolvía el backend viejo: `CursoDetalleResponse.portadaFirmada` y
  `MotivoBloqueoResponse`/`ClaseDiariaResponse`/`RecomendacionResponse` son **camelCase** (nunca
  fueron una fila de tabla, siempre fueron un estado calculado).
- **AC-04** — `ConsultarProgresoParticipanteAcademyPort` usa `LEFT JOIN` contra
  `participantes_programa` (§2), a diferencia del `INNER JOIN` de `rocks`/`phasecontracts`.
- **AC-05** — **La base de datos es inmutable en esta fase** (corrección del supervisor, prioridad
  sobre el encargo original — reemplaza la D-40 cancelada). `roles_permitidos_curso` se usa TAL CUAL
  el baseline: `curso_id text` + `rol_id smallint REFERENCES roles(id)`. La traducción `rol_id` ↔
  `UserRole` vive en `RolesCatalogo` (`infrastructure/adapter/out/persistence/curso`): lee las 5 filas
  de `renaser.roles` (ya sembradas en el baseline) con una query nativa y las cachea en memoria
  (`Map<Short,UserRole>`, carga perezosa con doble-check `synchronized` — son datos de sistema, no
  cambian en caliente). No se creó ninguna migración Flyway ni se sembró ningún dato nuevo.
- **AC-06** — `domain/model/curso/` agrupa `Curso`+`SeccionCurso`+`Leccion`+`RecursoLeccion` en un
  solo paquete (un agregado), no cuatro subcarpetas — cuentan una sola historia (CLAUDE.MD §5.1.2,
  regla de agregado, mismo criterio que `buckpal`).
- **AC-07** — `CompletarLeccionUseCase` (endpoint nuevo) exige acceso VIGENTE al curso/sección antes
  de marcar la lección completada. El repo viejo no lo exigía en código (confiaba en que RLS ya había
  filtrado lo que el cliente podía ver) — con un endpoint REST propio, la validación se hace
  explícita en el caso de uso.
- **AC-08** — `portada_url`/`portada_firmada` se resuelven vía `AlmacenamientoPort.firmarLectura`
  cuando la ruta no es http(s) externa. Con el adapter NoOp (D-34) el valor es un placeholder; no
  rompe nada porque la app hoy firma del lado cliente contra Supabase Storage directo (§4.3).
- **AC-09** — `SaveProgresoLeccionPort.marcarCompletada` es "buscar, si existe devolver, si no
  guardar" (no un `INSERT ... ON CONFLICT` nativo). Correcto en el camino feliz e idempotente en uso
  normal; bajo una carrera real (dos requests concurrentes completando la misma lección por primera
  vez) podría lanzar una violación de constraint en vez de resolver silenciosamente — no probado bajo
  concurrencia, ver §7.
- **AC-10** — `Grupo` (dominio) está modelado (para reflejar fielmente `grupos`) pero **sin
  persistencia propia**: solo se construyó lo que `AccesoCursoFinder` necesita leer
  (`miembros_grupo` vía `LoadMiembroGrupoPort`, `asignaciones_curso` vía `LoadAsignacionCursoPort`).
  Alta/edición de grupos es un flujo de administración (Alchemist), fuera de alcance — ver §6.
- **AC-11** — El panel de administración de cursos (`/admin/cursos/**` del repo viejo: crear/editar
  curso, subir portada, gestionar secciones/lecciones/recursos, asignar usuarios/grupos) **no se
  construyó**. El encargo pedía las reglas de gate/progreso del lado aprendiz y el contrato
  `AccesoCursoFinder`; CRUD completo de catálogo es superficie de otra magnitud — ver §6.
- **AC-12** — Academia Adaptativa: solo lectura de cache (`recomendaciones_academia`) +
  `RecomendarClasePort` con adapter `NoOp`. La generación real (radar de energía/ánimo + Gemini) es
  Ola 5, y `RadarEntry` no pertenece a `academy` — ver §6.
- **AC-13** — `ConsultarClaseDiariaUseCase` es de solo lectura. Completar la Clase Diaria en el repo
  viejo además cierra el hábito diario correspondiente y otorga puntos
  (`clase-diaria/service.ts: completeClaseDiaria` → `habitService.completeTodayDailyClassWithSummary`)
  — eso pertenece a `habits`, que todavía no existe con ese hook. Ver §6.
- **AC-14** — La RPC `progreso_cursos` (`cursos.ts:152`) **no generó un endpoint nuevo**: `GET
  /api/v1/cursos` ya devuelve `progreso` embebido por curso desde antes de este cierre
  (`MiCursoResponse`), con la misma forma exacta que `ProgresoCurso` (`src/types/cursos.ts:77-82`).
  Esa RPC es solo el fallback de la app cuando la llamada REST primaria falla — una vez que la app dejó
  de necesitar Supabase (D-41), el fallback queda muerto, no reemplazado uno-a-uno. Ver §1.8.
- **AC-15** — `GET /api/v1/cursos/bloqueados` (`ConsultarCursosBloqueadosUseCase`) reemplaza la RPC
  `catalogo_cursos_bloqueados` (0018), que SÍ era una brecha real (la app la llama siempre, no como
  fallback — §1.8). Domain method nuevo `Curso.bloqueadoPorDiaPara(rol, programDay)`: la inversa exacta
  de `visibleEnCatalogoPara` pero SOLO para el motivo "día de progreso" — un curso restringido por rol,
  sin publicar o con `acceso=RESTRINGIDO` nunca aparece acá, mismo criterio de "no revelar de más" que
  `MotivoBloqueoCurso`/AC-01. Lista vacía (nunca error) para roles distintos de TRAINEE. `portada_url`
  viaja sin firmar — la app firma la escalera completa del lado cliente (§1.8).
- **AC-16** — `DELETE /api/v1/lecciones/{id}/complete` (`DescompletarLeccionUseCase`) reemplaza la
  escritura directa `leccion_progreso.delete()` (`cursos.ts: desmarcarLeccion`). Inverso simétrico de
  `CompletarLeccionUseCase`: misma exigencia de acceso vigente al curso/sección (AC-07) antes de
  borrar. Idempotente — desmarcar una lección que no estaba completada no falla, igual que el DELETE de
  PostgREST que reemplaza (`SaveProgresoLeccionPort.desmarcarCompletada`, guarda con `existsById` antes
  de `deleteById` porque Spring Data lanza excepción si la fila no existe).
- **AC-17** — `PorcentajeCursosFinder`/`PorcentajeCursosService` (cierre D-43, ver §4.1bis). Tres
  decisiones puntuales:
    1. **Solo TRAINEE activo, con rol TRAINEE fijo** — espejo exacto de `sumarProgresoCursos`
       (`cursos/repository.ts:824-849`, que llama `findProgramDay(userId, 'TRAINEE')` literal, sin
       importar el rol real del usuario) y del CTE `active_trainees`/`cursos_pct` de
       `general_ranking_scores_function.sql`, que solo itera `trainee_profiles` de usuarios `ACTIVE`.
       Un participante pedido que no sea TRAINEE activo (otro rol, TRAINEE suspendido, o TRAINEE sin
       fila en `participantes_programa` — este último caso en particular SÍ aparece, con
       `diaPrograma=null`, porque `usuariosActivosConDiaPrograma` usa `LEFT JOIN`, AC-04) queda
       simplemente **ausente** del mapa devuelto — no se le inventa un `100.0` por defecto.
    2. **`Map<UserId, BigDecimal>` con escala 1, fiel al `numeric` a 1 decimal de `cursos_pct` (SQL)**
       — corregido el mismo día: la primera versión devolvía `Integer` (redondeo estándar al entero
       más cercano), y el supervisor marcó que eso pierde precisión real, porque el score final de
       `general_ranking_scores_function.sql` pondera el `pct` de cada componente **con su decimal**
       antes de volver a redondear (`round((0.5·hp.pct + 0.35·rp.pct + 0.15·cp.pct) * 10) / 10`) —
       redondear a entero un componente y recién después ponderar da un score distinto del que hoy ve
       el aprendiz en producción. `PorcentajeCursos.calcular` replica la fórmula SQL bit a bit:
       `round(completadas / total * 1000) / 10` en `BigDecimal`, con `RoundingMode.HALF_UP`
       (equivalente al `round()` de Postgres para valores no negativos). Ya no hay diferencia de
       precisión contra la función SQL — coinciden. `rocks` expone su propio componente del mismo modo
       (`BigDecimal` escala 1), así que los tres contratos del Ranking General quedan consistentes.
    3. **Nunca filtra por día de desbloqueo de lección/sección** — verificado explícitamente contra
       `sumarProgresoCursos` (§1, no lo hace) y contra el comentario de la función SQL (líneas 28-30:
       *"sumarProgresoCursos nunca filtró por día de desbloqueo de la lección/sección, así que esta
       función tampoco lo hace, a propósito, para no cambiar de comportamiento"*) — mismo criterio que
       ya regía `listarMisCursosAlumno`/`GET /api/v1/cursos` (§1.4). El comentario SQL y el código
       coinciden en este punto, no hubo contradicción que resolver.

---

## 6. Qué NO se construyó / preguntas abiertas

- **Admin CRUD de catálogo** (AC-11): crear/editar/borrar curso, secciones, lecciones, recursos,
  subir portada, asignar usuarios/grupos uno a uno o en lote, revocar/restaurar asignación. Todo
  `adminService.ts` del repo viejo queda sin portar.
- **Completar Clase Diaria de verdad** (AC-13): ✅ **Cerrada (2026-08-26).** Investigado contra el
  repo viejo (`clase-diaria/service.ts:60-90` + `habits/service.ts:1747-1811`): completar la Clase
  Diaria son **dos escrituras relacionadas, no un solo concepto**. La cita exacta del repo viejo:
  > `// Se guarda antes de completar el hábito. Ambos pasos son idempotentes: si una red se corta
  > entre ellos, repetir la acción termina el segundo sin duplicar progreso ni puntos.`
  > `const completed = await habitService.completeTodayDailyClassWithSummary(userId, resumen)`
  > `if (!completed.success) return completed`
  > `await repo.markLeccionCompleted(userId, clase.leccionId)`

  (1) cierra el registro de HOY del hábito de catálogo `DAILY_CLASS` (`claveSistema` — sí existe en
  el catálogo, confirmado por `isDailyClassHabit`/`DAILY_CLASS_SYSTEM_KEY` en `habits/service.ts:1542-1546`
  y por las referencias ya presentes en este backend en `SelectorHabito`/`PoliticaHabito`), dominio
  exclusivo de `habits`: puntos, ventana de entrega y evento de dominio; (2) marca
  `leccion_progreso`, dominio propio de `academy` ya cubierto por `CompletarLeccionUseCase`.

  Construido en consecuencia, sin tocar BD (D-40): `habits.api.CompletarClaseDiariaHabitoUseCase`
  (nuevo puerto público de `habits`, deliberadamente específico a `DAILY_CLASS` — no un "completar
  cualquier hábito por clave" genérico, para no reabrir desde otro módulo el bypass de evidencia que
  el repo viejo cierra a este único hábito) localiza el registro de HOY sin exponer su
  `RegistroHabitoId` a `academy`, y delega el cálculo de puntos/ventana en el
  `CompletarRegistroUseCase` ya existente (no se duplica esa lógica). `academy` agrega
  `CompletarClaseDiariaUseCase`, implementado en `ClaseDiariaService`: revalida en servidor que la
  lección pedida es la Clase Diaria real de hoy (nunca confía en lo que mande el cliente), llama al
  puerto de `habits` y recién después a `CompletarLeccionUseCase` — mismo orden que el repo viejo,
  ahora envuelto en una única transacción local (`@Transactional`, CLAUDE.MD §9.1) ya que ambos
  pasos son idempotentes de por sí (`EstadoRegistro.COMPLETADO` es terminal en `habits`,
  `marcarCompletada` es upsert-ignore en `academy`) y viven en el mismo Postgres. Nuevo endpoint:
  `POST /api/v1/classroom/clase-diaria` (antes solo existía el `GET`). Tests en
  `ClaseDiariaServiceTest`/`ClaseDiariaHabitoServiceTest` (unitarios, Mockito): camino feliz,
  idempotencia, lección que no coincide con la de hoy (403), sin clase disponible (409), y actor
  suspendido en ambos módulos (403).
- **Academia Adaptativa completa** (AC-12): generación real vía Gemini, necesita `RadarEntry` (dueño:
  otro módulo, probablemente `onboarding` u otro futuro) + ranking de lecciones disponibles. Ola 5.
- **Pregunta abierta AC-02**: ¿`acceso = RESTRINGIDO` debería, en algún escenario, ser visible (p.ej.
  vía asignación explícita), o es correcto que hoy nunca lo sea? El código viejo sugiere que en algún
  momento SÍ importaban las asignaciones (de ahí el parámetro `_asignadoHistoricamente`, hoy inerte) —
  puede ser una regresión no detectada, o una decisión de producto deliberada. Se replicó fiel al
  comportamiento actual; no se inventó una regla nueva.
- **Grupo — alta/gestión** (AC-10): sin persistencia de escritura ni lectura de `grupos` en sí
  (nombre, listado). Solo lo que `AccesoCursoFinder` necesita.
- **Concurrencia de `CompletarLeccionUseCase`** (AC-09): no probada. El camino feliz e idempotente en
  uso normal está cubierto; una carrera real necesitaría un test de integración con dos hilos o
  `INSERT ... ON CONFLICT DO NOTHING` nativo si se detecta un problema real en producción.
- **`roles_permitidos_curso` con datos reales**: hoy la tabla puede estar vacía (sin migración de
  datos desde producción, per instrucción del supervisor) — el catálogo completo se comporta como "sin
  restricción de rol" hasta que llegue esa migración. Es el comportamiento esperado, no un bug.

---

## 7. Pruebas

**Hecho (unitario, sin Spring/Postgres):**

- `CursoTest` — 17 casos de `puedeVerCurso.test.ts` + 8 casos nuevos (2026-08-24) de
  `bloqueadoPorDiaPara` (AC-15): bloqueado solo por día, nunca por rol/borrador/restringido/sin-día,
  `programDay` null tratado como día 0.
- `SeccionCursoTest` — espejo de `puedeVerSeccion`.
- `AsignacionCursoTest` — arco exclusivo (constructor) + `vigente()` (desde/hasta/revocada).
- `CatalogoAcademyServiceTest` (Mockito) — filtro de acceso en `misCursos`, 403 por suspensión, 404 vs
  403 en `detalle`, motivo de bloqueo (revela/no revela), `completar`/`descompletar` con y sin acceso,
  `cursosBloqueados` (solo por día, vacío para no-TRAINEE, 403 por suspensión), firma de portada
  externa vs interna.
- `AccesoCursoServiceTest` (Mockito) — unión directa+grupo, filtrado de vigencia, conjunto vacío sin
  tocar el puerto de grupos.
- `ClaseDiariaServiceTest` (Mockito) — no iniciado, sin contenido (próximamente), elección de sección
  más reciente + lección "clase".
- `RecomendacionServiceTest` (Mockito) — cache-hit sin llamar a IA, cache-miss con IA vacía (NoOp) →
  no disponible.
- `PorcentajeCursosTest` — dominio puro (AC-17): sin cursos accesibles → `100.0`, nada completado →
  `0.0`, todo completado → `100.0`, división exacta con un decimal (`5/8 → 62.5`), y sobre todo el caso
  que motivó la corrección — **decimal significativo que un `Integer` perdía**: `1/3 → 33.3`,
  `2/3 → 66.7` (`round()` HALF_UP de `666.66...` → `667`), y una prueba explícita de que el resultado
  siempre viaja con `scale() == 1`.
- `PorcentajeCursosServiceTest` (Mockito) — sin participantes → mapa vacío sin tocar ningún puerto;
  participante que no es TRAINEE activo → ausente del mapa, sin consultar catálogo/progreso; sin
  cursos accesibles → `100.0`; dos participantes con distinto día de programa → gate aplicado por
  separado y **cada puerto llamado exactamente una vez** (`verify(..., times(1))`, el equivalente a
  nivel de puertos del criterio de aceptación "en lote de verdad"); un curso completado que ya no es
  accesible no cuenta en el porcentaje; y `1/3 → 33.3` de punta a punta (orquestación completa, no
  solo el cálculo puro).

**Hecho (integración, Testcontainers — agregado 2026-08-24 al cerrar D-41):**

- `CursoPersistenceAdapterTest` — `listarTodos()` ordenado por `orden`, traducción de
  `roles_permitidos_curso` vía `RolesCatalogo` contra las 5 filas reales de `renaser.roles`
  (mapper de enum nativo `acceso_curso` incluido), `byId()` sin roles y curso inexistente, y un caso
  end-to-end que reproduce `catalogo_cursos_bloqueados`: `listarTodos()` + `Curso.bloqueadoPorDiaPara`
  contra una fila real de Postgres. Cierra el pendiente "mappers de enum nativo contra Postgres real"
  de la corrida anterior.
- `ProgresoLeccionPersistenceAdapterTest` — `completadasPorCurso` (el JOIN nativo que reemplaza la
  mitad de agregación de `progreso_cursos`, AC-14) agrupando correctamente por curso y usuario,
  `marcarCompletada` idempotente, `estaCompletada`/`leccionesCompletadas`, y `desmarcarCompletada`
  (AC-16) incluyendo el camino idempotente (desmarcar algo no completado no falla).
  **Agregado al cerrar D-43:** `completadasPorCursoEnLote` con 3 usuarios — agrupa correcto por
  `(usuario, curso)` y, con estadísticas de Hibernate habilitadas (`Statistics.getQueryExecutionCount()`),
  verifica que dispara **exactamente 1 consulta**, no una por usuario — es el criterio de aceptación
  literal del encargo, probado contra Postgres real y no solo revisando el resultado.

**Pendiente (integración, Testcontainers — no me corresponde a mí, CLAUDE.MD):**

- `LEFT JOIN` de `ConsultarProgresoParticipanteAcademyPersistenceAdapter` con un usuario SIN fila de
  `participantes_programa` (caso MENTOR/ADMIN sin inscripción) — es el caso que motivó AC-04 y no se
  puede probar con mocks.
- `ProgresoLeccionPersistenceAdapter.marcarCompletada` bajo concurrencia real (AC-09).
- Los 9 endpoints REST completos (`CursoController`, `LeccionController`, `ClaseDiariaController`,
  `RecomendacionController`) con `@SpringBootTest` + Testcontainers a nivel HTTP (`MockMvc`/
  `TestRestTemplate`), incluyendo el formato exacto del wire (AC-03) — es lo más importante de
  verificar contra Postgres real, ya que el 403 vs 404 y el contenido JSON exacto son la superficie de
  contrato con la app RN. Lo que se agregó el 2026-08-24 prueba los adaptadores de persistencia contra
  Postgres real, no la capa web.
- Tests de autorización negativa (CLAUDE.MD §0.3): rol sin permiso → 403, `SUSPENDIDO` → 403 aunque el
  token sea válido, test de reflexión de `@RequiresPermission`/`@PublicEndpoint` — no aplican todavía
  porque no hay filtro JWT ni sistema de permisos anotado (bloqueante general del proyecto, no de este
  módulo).
