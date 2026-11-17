# Tareas: Rediseño del rol de mentor (fase 1)

**Entrada**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/api.md](contracts/api.md), [quickstart.md](quickstart.md)

**Pruebas**: obligatorias (constitución III) y **primero**: cada prueba falla antes del código y lleva la marca `// spec 055 FR-NNN` (Jest) o `# spec 055 FR-NNN` (Maestro); en Java, `@DisplayName("spec 055 FR-NNN …")`.

**Rutas**: sin prefijo = backend (`Renaser-90-dias-backend`, worktree `wt-055-rediseno-del-mentor`, raíz Java `src/main/java/com/renaser/os/`, abreviada `…/`). `app:` = app (`Renaser-90-dias-frontend-`, worktree `.claude/worktrees/055-rediseno-del-mentor`).

**Formato**: `[ID] [P?] [Historia] Descripción`. `[P]` = puede ir en paralelo (otro archivo, sin dependencias pendientes).

---

## Fase 1: Preparación

- [x] T001 Crear el worktree de la app con `.arnes/scripts/worktree-nuevo.sh 055-rediseno-del-mentor` desde `Renaser-90-dias-frontend-` y comprobar `graphify query "ficha del aprendiz mentor"` en los dos worktrees
- [x] T002 [P] Agregar `"055"` con FR-001…FR-029 y SC-001…SC-007 a `specs/trazabilidad.json`, marcando como exigidos los FR de la fase 1

---

## Fase 2: Base (bloquea todas las historias)

- [x] T003 [P] Prueba que falla: `ObligacionDelDia.pilar` trae la `categoria_clave` del hábito (`CUERPO|MENTE|ESPIRITU|EMOCIONES|null`) en `src/test/java/com/renaser/os/mentoring/application/services/PilarEnLaObligacionTest.java` (spec 055 FR-013)
- [x] T004 Agregar `pilar` a `…/habits/api/ObligacionHabito.java` (leído de `habitos.categoria_clave` en su adaptador de lectura) y a `…/mentoring/api/ObligacionDelDia.java` (campo nuevo al final; constructores anteriores conservados), y pasarlo en `ArmadoDeLaSemana` / `ArmadoDeLaFicha`
- [x] T005 [P] app: agregar `pilar?: 'CUERPO'|'MENTE'|'ESPIRITU'|'EMOCIONES'|null` al esquema de la obligación en `app:src/features/mentor/api/mentorSchemas.ts`, tolerando que falte (apps y servidores viejos)

**Punto de control**: `scripts/verificar.sh` y `npm run verificar -- --rapido` en verde con el campo nuevo.

---

## Fase 3: Historia 1 — Ver quién necesita ayuda en mi grupo (P1) 🎯 MVP

**Meta**: Mis grupos → Mi grupo ordenado con el color en el borde, la línea del motivo y el chat en un toque.
**Prueba independiente**: mentor de prueba con 3 grupos y 9 aprendices de colores distintos (quickstart, escenarios 1 y 2).

### Pruebas (primero)

- [x] T006 [P] [US1] Prueba de `ordenDeAprendices` (rojo → ámbar → verde → sin datos; dentro del color, menor % primero; a igualdad, por nombre) en `app:src/features/mentor/utils/__tests__/ordenDeAprendices.test.ts` (spec 055 FR-004)
- [x] T007 [P] [US1] Prueba de `motivoDeLaFila` (aviso vigente más reciente por `route` `/mentor/groups/{g}/learners/{u}`; si no hay, «Cumplió N de 7 días» / «Al día» / «Todavía sin datos») en `app:src/features/mentor/utils/__tests__/motivoDeLaFila.test.ts` (spec 055 FR-006)
- [x] T008 [P] [US1] Prueba de pantalla de `MisGruposScreen`: lista con la cantidad a la derecha; con un solo grupo entra directo; sin grupos, el mensaje sin huecos, en `app:src/features/mentor/screens/__tests__/misGrupos.test.ts` (spec 055 FR-001, FR-002)
- [x] T009 [P] [US1] Prueba de pantalla de `MiCelulaScreen`: borde del avatar con el color (gris suave sin datos), nombre en texto normal, sin lista de avisos, sin conteos ni insignia, enlace a «Mi evaluación», el chat llama a `abrirConversacionDirecta` una sola vez (un toque, un pedido), tocar la fila abre la ficha y cada toque mide al menos 48 de alto, en `app:src/features/mentor/screens/__tests__/miGrupoRediseno.test.ts` (spec 055 FR-005, FR-007, FR-008)
- [x] T010 [P] [US1] Prueba de las vistas: `misGrupos` en `vistasDeLosPaneles` y en `vistasDelMentor`, volver de Mi grupo lleva a Mis grupos (o a Yo/Tribu si hay un solo grupo), en `app:src/features/mentor/utils/__tests__/vistasDelMentor.test.ts` y `app:src/features/paneles-de-rol/utils/__tests__/vistasDeLosPaneles.test.ts` (spec 055 FR-003)

### Implementación

- [x] T011 [P] [US1] `ordenDeAprendices` y `motivoDeLaFila` (puras) en `app:src/features/mentor/utils/ordenDeAprendices.ts` y `app:src/features/mentor/utils/motivoDeLaFila.ts`
- [x] T012 [P] [US1] `AvatarConSemaforo` (reutiliza `AvatarPersona` de `app:src/components/ui.tsx` y `coloresDelSemaforo`; borde de 2,5 px con el color, gris suave `c.divider` sin datos) en `app:src/features/mentor/components/AvatarConSemaforo.tsx`
- [x] T013 [US1] `FilaDeAprendiz` (avatar, nombre, línea gris del motivo, ícono `chat` de 48 px de toque con `Presionable`) en `app:src/features/mentor/components/FilaDeAprendiz.tsx`
- [x] T014 [US1] `MisGruposScreen` (grupos de `gruposQueAcompana`; cantidad del semáforo del grupo; entra directo con uno) en `app:src/features/mentor/screens/MisGruposScreen.tsx`
- [x] T015 [US1] Rehacer `MiCelulaScreen` con `useSemaforoDelGrupo` + `useAvisosDeAcompanamiento`, `ordenDeAprendices` y `FilaDeAprendiz`; quitar la lista de avisos de arriba, los conteos y la insignia; pie con «Mi evaluación · N %» en `app:src/features/mentor/screens/MiCelulaScreen.tsx`
- [x] T016 [US1] Vista `misGrupos` en `app:src/features/paneles-de-rol/utils/vistasDeLosPaneles.ts`, `app:src/features/paneles-de-rol/screens/PantallaDelPanelDeRol.tsx`, `app:src/features/mentor/utils/vistasDelMentor.ts` y `app:src/screens/ComunidadScreen.tsx`; la fila de Yo pasa a «Mis grupos» (o «Mi grupo» con uno) en `app:src/screens/YoScreen.tsx`
- [x] T017 [US1] Flujo de Maestro `app:.maestro/rediseno-del-mentor/01-mis-grupos-y-mi-grupo.yaml` (cuenta del mentor; orden, colores, chat) con su siembra `siembra.sql` (spec 055 FR-001…FR-008)

**Punto de control**: Historia 1 funciona sola en el emulador.

---

## Fase 4: Historia 2 — Ver qué cumplió un aprendiz, día por día (P1)

**Meta**: ficha con la semana de sábado a viernes, el mes desplegable y los hábitos por pilar.
**Prueba independiente**: E2E Aprendiz (día 78) con varias semanas de datos (quickstart, escenarios 3 y 4).

### Pruebas del backend (primero)

- [x] T018 [P] [US2] Prueba de integración (Testcontainers) de `GET /api/v1/mentor/groups/{g}/learners/{u}/semana?semanaHasta=`: semana sábado–viernes, `diaUno`, `primeraSemana`, `pilar`; 400 `SEMANA_INVALIDA` si no es viernes; 400 `FUERA_DEL_PROGRAMA` antes del Día 1 o futura; 403 `NO_ACOMPANA` con otro mentor, en `src/test/java/com/renaser/os/mentoring/infrastructure/adapter/in/rest/SemanaDelAprendizIT.java` (spec 055 FR-011, FR-015, FR-017)
- [x] T019 [P] [US2] Prueba de integración de `GET …/dias?desde&hasta`: color y % por día con la regla de `ColorSemaforo` leída de `semaforo_dias`; estados `PAUSADO`/`CUENTA_SUSPENDIDA`/`SIN_DATOS`; máx. 62 días (400 `RANGO_INVALIDO`); recorta a `[diaUno, hoy]`; 403 a otro mentor, en `src/test/java/com/renaser/os/mentoring/infrastructure/adapter/in/rest/DiasDelAprendizIT.java` (spec 055 FR-012, FR-017)

### Implementación del backend

- [x] T020 [US2] Puerto `ConsultarSemanaDelAprendizUseCase` y servicio `SemanaDelAprendizService` (guard `acompanaVigente` + cuenta activa + aprendiz del grupo; reutiliza el armado de la ficha del líder vía su puerto de dominio, no su adaptador; agrega `diaUno`) en `…/mentoring/application/ports/in/` y `…/mentoring/application/services/SemanaDelAprendizService.java`
- [x] T021 [US2] Puerto y servicio `DiasDelAprendizService` (lee `semaforo_dias` por el puerto de salida del semáforo; color con `ColorSemaforo`) en `…/mentoring/application/services/DiasDelAprendizService.java`
- [x] T022 [US2] `SemanaDelAprendizController` y `DiasDelAprendizController` con `@Funcionalidad("mentor.mi_grupo")` y `@RequiresPermission(value = Permission.USE_APP, scope = "…: acompañante vigente del grupo Y el aprendiz del grupo")` en `…/mentoring/infrastructure/adapter/in/rest/`

### Pruebas de la app (primero)

- [x] T023 [P] [US2] Prueba de `estadoDelHabito` (COMPLETADO → cumplido con `entregadaEn` en la zona del aprendiz; FALLIDO/EXPIRADO → vencido; PENDIENTE/EN_CURSO → pendiente) y `agruparPorPilar` (orden Cuerpo, Mente, Espíritu, Emociones; «N de M»; primer pilar con pendientes o vencidos abierto) en `app:src/features/mentor/utils/__tests__/habitosDelDia.test.ts` (spec 055 FR-013, FR-014)
- [x] T024 [P] [US2] Prueba de `useSemanasDelAprendiz`: al elegir una fecha pide su semana y adelanta la anterior; volver a una semana ya vista no pide de nuevo; desmontar descarta la caché; sin internet, una semana no cargada dice que hace falta conexión y lo cargado se sigue viendo, en `app:src/features/mentor/hooks/__tests__/useSemanasDelAprendiz.test.ts` (spec 055 FR-016, SC-004)
- [x] T025 [P] [US2] Prueba de pantalla de la ficha: cabecera (borde de color, «Día N · motivo», lápiz, chat), 7 círculos con hoy elegido, tocar un día cambia los pilares sin salir, «Mes ⌄» despliega el mes y no deja ir antes del Día 1 ni a futuros, accesos del final (sin Código Renaser si está apagado), toques de al menos 48 de alto, y si el servidor responde `NO_ACOMPANA` vuelve a «Mis grupos» con el motivo y sin datos viejos, en `app:src/features/mentor/screens/__tests__/fichaRediseno.test.ts` (spec 055 FR-010…FR-018)

### Implementación de la app

- [x] T026 [P] [US2] API y esquemas `obtenerSemanaDelAprendiz(g,u,semanaHasta)` y `obtenerDiasDelAprendiz(g,u,desde,hasta)` en `app:src/features/mentor/api/mentorApi.ts` y `mentorSchemas.ts`
- [x] T027 [P] [US2] `estadoDelHabito` y `agruparPorPilar` (puras) en `app:src/features/mentor/utils/habitosDelDia.ts`
- [x] T028 [US2] Hooks `useSemanasDelAprendiz` (Map por `semanaHasta` + adelanto de la anterior) y `useDiasDelMes` en `app:src/features/mentor/hooks/`
- [x] T029 [P] [US2] `SemanaDeCirculos` (sábado a viernes, círculos de 24 px con el color, día elegido con anillo dorado; toques de 48) en `app:src/features/mentor/components/SemanaDeCirculos.tsx`
- [x] T030 [P] [US2] `MesDeColores`: generalizar la grilla de `app:src/features/eventos/components/CalendarioDelMes.tsx` / `utils/calendarioDelMes.ts` para que no dependa de `Ocurrencia` (prop de render por día) y usarla con colores; sin días antes del Día 1 ni futuros, en `app:src/features/mentor/components/MesDeColores.tsx`
- [x] T031 [P] [US2] `PilarDeHabitos` (reutiliza `SeccionPlegable` de `app:src/components/Legible.tsx`; puntitos y «N de M») y `HabitoDelDia` (estado ✓/✕/punteado + hora) en `app:src/features/mentor/components/`
- [x] T032 [US2] Rehacer `AlumnoScreen`: cabecera, semana/mes, pilares del día elegido, accesos al final (Código Renaser, Diagnóstico, 80/20, Tiempo libre, que abren lo existente); quitar el detalle día por día y la lista larga de hábitos con horarios; un solo scroll, en `app:src/features/mentor/screens/AlumnoScreen.tsx`
- [x] T033 [US2] Al abrir la ficha, marcar leídos los avisos sin leer de ese aprendiz (por `route`) con `PUT /api/v1/notifications/{id}/read` en `app:src/features/mentor/hooks/useAvisosDelAprendiz.ts` (prueba en `__tests__`) (spec 055 FR-009, SC-007)
- [x] T034 [US2] Flujo de Maestro `app:.maestro/rediseno-del-mentor/02-ficha-semana-y-mes.yaml` (spec 055 FR-010…FR-017)

---

## Fase 5: Historia 3 — Ver la evidencia de lo que cumplió (P2)

**Meta**: el ojo abre la evidencia en una hoja, con anterior y siguiente.
**Prueba independiente**: aprendiz con foto, audio y texto (quickstart, escenario 5).

- [x] T035 [P] [US3] Prueba: el ojo aparece solo si el hábito cumplido tiene evidencia; la hoja muestra tipo, hora, pilar, texto y «1 de N»; anterior/siguiente dentro del día sin cerrar; enlace al Muro con `publicacionId`; cerrar con volver deja el día, en `app:src/features/mentor/components/__tests__/evidenciaDelHabito.test.ts` (spec 055 FR-019…FR-022)
- [x] T036 [US3] Extender `EvidenciaDeObligacion` (`app:src/features/lider-mentores/components/EvidenciaDeObligacion.tsx`) con navegación anterior/siguiente y enlace al Muro sin romper sus usos actuales (líder y Administración), y conectar el ojo en `HabitoDelDia`
- [x] T037 [US3] Flujo de Maestro `app:.maestro/rediseno-del-mentor/03-evidencia.yaml` (spec 055 FR-019…FR-022)

---

## Fase 6: Historia 4 — Anotar lo que hablé con un aprendiz (P2)

**Meta**: nota privada que escribe el acompañante vigente y leen el liderazgo y Administración.
**Prueba independiente**: quickstart, escenario 6 y por rol (escenario 7).

### Pruebas del backend (primero)

- [x] T038 [P] [US4] Prueba de dominio de `NotaDelMentor`: «texto NOT NULL, 1–2000 caracteres sin contar espacios de los extremos»; corrige solo el autor y solo si `hoy en su zona == dia_del_autor`; cada corrección guarda el texto anterior; no hay borrado, en `src/test/java/com/renaser/os/mentoring/domain/model/nota/NotaDelMentorTest.java` (spec 055 FR-023, FR-025, FR-026)
- [x] T039 [P] [US4] Prueba de integración por rol de `/notas`: el acompañante vigente crea, lista (también las de mentores anteriores) y corrige hoy; 409 `YA_NO_SE_PUEDE_CORREGIR` otro día; 403 `NO_ES_EL_AUTOR`; 403 `NO_ACOMPANA` a un mentor de otro grupo o rotado; el aprendiz no tiene ninguna ruta que las devuelva; el liderazgo de su formación y Administración leen con `puedeCorregir=false`, en `src/test/java/com/renaser/os/mentoring/infrastructure/adapter/in/rest/NotasDelAprendizIT.java` (spec 055 FR-023…FR-026, FR-028, SC-006)

### Implementación del backend

- [x] T040 [US4] Migración `src/main/resources/db/migration/V198__notas_del_mentor.sql` (o el siguiente número libre al fusionar): `notas_del_mentor (id uuid PK, aprendiz_id uuid NOT NULL FK participantes_programa(usuario_id), autor_id uuid NOT NULL FK usuarios(id) RESTRICT, texto text NOT NULL CHECK 1–2000 sin espacios de los extremos, dia_del_autor date NOT NULL, creado_en timestamptz NOT NULL, corregido_en timestamptz NULL)` con índice `(aprendiz_id, creado_en DESC)` y `notas_del_mentor_versiones (nota_id uuid FK, texto_anterior text NOT NULL, reemplazado_en timestamptz NOT NULL, PK (nota_id, reemplazado_en))`; `SET search_path` como exige la prueba de migraciones
- [x] T041 [US4] Dominio `NotaDelMentor` y sus reglas en `…/mentoring/domain/model/nota/`
- [x] T042 [US4] Puertos y `NotasDelMentorService` (guard del acompañante vigente con `acompanaVigente` + aprendiz del grupo; lectura del liderazgo con `AccesoDeLiderazgo` + `AlcanceDeFormacionFinder`; Administración ADMIN/ALCHEMIST activos; día del autor en su zona) en `…/mentoring/application/`
- [x] T043 [US4] Adaptador JPA de notas y versiones en `…/mentoring/infrastructure/adapter/out/persistence/nota/`
- [x] T044 [US4] `NotasDelAprendizController` (`GET`/`POST /notas`, `PATCH /notas/{notaId}`; `USE_APP` + `@Funcionalidad("mentor.mi_grupo")`) en `…/mentoring/infrastructure/adapter/in/rest/`; `GET /api/v1/leadership/people/{personId}/notas` (`VIEW_MENTOR_CORPS`) en `…/leadership/infrastructure/adapter/in/rest/PersonasDelProgramaController.java`; `GET /api/v1/admin/trainees/{traineeId}/notas` (`MANAGE_TRAINEES`) en `…/mentoring/infrastructure/adapter/in/rest/`

### Pruebas de la app (primero)

- [x] T045 [P] [US4] Prueba de `HojaDeNotas` y del lápiz: puntito solo con notas; aclaración «Solo la ven tú, tu líder y Administración»; guardar agrega arriba; vacía o > 2000 no se guarda y dice qué falta; un toque, un pedido; sin internet conserva el texto y avisa; corregir solo las propias de hoy (`puedeCorregir`); el líder y Administración solo leen; toques de al menos 48 de alto; `NO_ACOMPANA` muestra el motivo, en `app:src/features/mentor/components/__tests__/notasDelAprendiz.test.ts` (spec 055 FR-024…FR-027)

### Implementación de la app

- [x] T046 [P] [US4] API, esquema y hook `useNotasDelAprendiz` (mentor, líder y Administración según el origen) en `app:src/features/mentor/api/mentorApi.ts` y `app:src/features/mentor/hooks/useNotasDelAprendiz.ts`
- [x] T047 [US4] `HojaDeNotas` (reutiliza `HojaDesdeAbajo`, `FormField`, `BotonPrincipal`) en `app:src/features/mentor/components/HojaDeNotas.tsx`; lápiz en la cabecera de `AlumnoScreen`
- [x] T048 [US4] Lápiz de solo lectura en `app:src/features/lider-mentores/screens/PersonaDelLiderScreen.tsx` y `app:src/features/admin/screens/FichaAprendizScreen.tsx`
- [x] T049 [US4] Flujo de Maestro `app:.maestro/rediseno-del-mentor/04-notas.yaml` (mentor escribe y corrige; líder lee) (spec 055 FR-023…FR-027)

---

## Fase 7: Terminación

- [x] T050 [P] Decisión del dueño en `app:docs/decisiones/liderazgo-y-administracion.md` (cambio de Yo y de Comunidad → Tribu del mentor, autorizado el 17/11 con los bocetos) y su fila en `app:docs/decisiones/README.md`; corregir con `> **Corregido 2026-11-17.**` las entradas de Mi grupo que queden desmentidas
- [x] T051 [P] Documentar las notas y las lecturas nuevas en el doc del módulo `docs/MODULO_MENTORING.md` (o el que exista) y en `docs/MODULOS_A_AVANZAR.md`
- [x] T052 Quitar lo que queda sin uso tras el rediseño (la rejilla y el detalle día por día de la ficha, `tituloDeAvisos`, `reglas.ts` sin uso, esquemas `/admin/cohorts` y `/admin/cells` de `mentorSchemas.ts`) solo si `graphify query` y `tsc` confirman que nadie más los usa
- [x] T053 `speckit-converge`: comparar el código con la spec y agregar aquí lo que falte — hecho 17/11: todos los FR con tarea marcada y prueba (trazabilidad 0 sin prueba); lo único fuera es #167, bug previo
- [x] T054 `scripts/verificar.sh` (backend) y `npm run verificar` (app) en 0, con la trazabilidad de la 055 completa — hecho 17/11: backend 0 (11182 pruebas), app 0
- [x] T055 E2E: `npm run verificar -- --e2e .maestro/rediseno-del-mentor/` en el emulador + mobile-mcp por los escenarios del quickstart, con capturas (solo el emulador de Android: el dueño pidió el 17/11 no usar el simulador de iPhone); revisar roles (mentor, guía, líder, admin, aprendiz) y estados (Día 0, suspendido, sin grupos); comprobar con captura de la primera pantalla, sin desplazarse, que se ven los rojos de Mi grupo (SC-001) y la semana, el día y el primer pilar en la ficha (SC-003); contar los toques: escribir = 1 desde Mi grupo (SC-002) y ver una evidencia = 1 desde la ficha (SC-005) — hecho 17/11: los 4 flujos en verde en el emulador Android
- [x] T056 Logs: backend local sin ERROR ni 5xx y `adb logcat` sin errores de JavaScript durante el E2E (`sin-senal.sh logs`) — hecho 17/11: ERROR=0, 5xx=0; el 403 de /evidence ya existía → #167
- [ ] T057 PR del backend y, cuando esté desplegado, PR de la app, con la verificación, el E2E, los logs y lo que no se pudo verificar

---

## Dependencias y orden

- Fase 1 → Fase 2 → historias. La Historia 1 (app) puede empezar apenas termina la Fase 2, porque usa endpoints que ya existen.
- Historia 2: su backend (T018–T022) antes de T026; su app depende de la Fase 2.
- Historia 3 depende de `HabitoDelDia` (T031) de la Historia 2.
- Historia 4: el backend (T038–T044) es independiente de las historias 1–3; su app necesita la cabecera de la ficha (T032).
- Fase 7 al final. El PR de la app espera al despliegue del backend (la app instalada no se rompe: todo lo del servidor es aditivo).
- **Antes de todo**: #165 (avisos acumulados) fusionado, porque FR-006 y FR-009 dan por hecho un aviso vigente por aprendiz y motivo.

## En paralelo

- Backend y app en paralelo desde el principio: un agente para el backend (T003–T004, T018–T022, T038–T044) y otro para la app (T005–T017), cada uno en su worktree.
- Dentro de cada historia, las pruebas `[P]` se escriben a la vez; los componentes `[P]` (T029, T030, T031) también.

## Estrategia

1. **MVP**: Fases 1–3 (Historia 1) → probar en el emulador.
2. Historia 2 (la ficha) → probar → Historia 3 (evidencia) → Historia 4 (notas).
3. Terminación: converge, verificar, E2E en el emulador de Android, logs, docs y los dos PR.
