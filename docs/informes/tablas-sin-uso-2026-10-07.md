# Tablas de la base: cuáles se usan (2026-10-07)

Revisión de solo lectura. Leí el código de `master` del backend (copia extraída con `git archive master`, sin tocar la carpeta de trabajo) y del frontend, y la base local de desarrollo. No cambié código, datos ni esquema, y no consulté producción.

## Respuesta corta

**No todas las tablas se usan.** De las 119 tablas que crea Flyway (más `flyway_schema_history`):

- **Unas 95 se usan de verdad**: la app las lee y las escribe.
- **5 tablas no tienen ningún uso real en el código**: `grupos`, `permisos`, `rol_permiso`, `ranking_celulas` y `auditoria_cambios_rol`.
- **4 tablas guardan algo que nadie lee nunca, o están vacías porque nadie las llena**: `historial_coherencia`, `revisiones_semanales_sin_celular`, `anomalias_acompanamiento` y `asignaciones_curso`/`miembros_grupo` (esas dos van juntas).
- **Unas 10 tienen el backend completo, pero la app nunca llama a esos endpoints**, o solo llega a ellas RenasIA. Son funciones que se construyeron y nunca se conectaron a una pantalla. Están descritas más abajo.
- **5 columnas no se usan**: `participantes_programa.habitos_escalonados_en`, `usuarios.estado_cambiado_en`, `usuarios.motivo_estado`, `habitos.tipo_entrada_diario` y `habitos.grupo`.

**Mi recomendación: no borrar nada todavía.** Primero hay que correr el script de producción (`~/.cache/renaser-e2e/consulta-uso-tablas.sh`) para confirmar que de verdad están vacías allá. Las únicas tablas que se podrían eliminar sin perder nada son `permisos` y `rol_permiso`: la decisión D-21 ya las dio por superadas. También `grupos`, pero solo si se elimina junto con `miembros_grupo` y `asignaciones_curso`. Las tres tienen datos de siembra o están vacías, y una decisión anterior reemplazó a las dos primeras. Todo lo demás conviene **dejarlo o conectarlo**, según lo que se decida del producto.

## Tablas sin uso

| Tabla | Origen | Qué pasa | Filas locales | Recomendación |
|---|---|---|---|---|
| `permisos` | V1 | Ninguna línea de Java la toca. Los permisos se manejan con el enum `UserRole` en el código (D-21, reafirma D-13) | 11 (siembra) | **Eliminar** con una migración nueva, junto con `rol_permiso`. D-21 ya la dio por superada |
| `rol_permiso` | V1 | Igual que `permisos` | 22 (siembra) | **Eliminar** junto con `permisos` |
| `grupos` | V1 | Nada en Java. Solo existe porque `miembros_grupo` y `asignaciones_curso` la referencian con FK | 0 | Decidir junto con «Acceso a cursos por grupo» (ver más abajo). Si se descarta esa idea, se eliminan las tres juntas |
| `ranking_celulas` | V1, ampliada en V46 | Nada la lee ni la escribe. El ranking de grupos se calcula al momento (`RankingDeGruposService`). V46 la dejó «preparada para un snapshot» (GAP-24) | 0 | **Dejar**. Es un lugar ya previsto si el cálculo al momento deja de alcanzar. Eliminarla solo si se decide que nunca va a hacer falta |
| `auditoria_cambios_rol` | V1 | Nadie la escribe. `CUMPLIMIENTO_REQUISITOS.md` RF-04 ⚠️ dice que el cambio de rol debía quedar auditado y no se audita | 0 | **Conectar** (que `PATCH /users/{id}/role` deje una fila) o, si el dueño no quiere esa auditoría, eliminarla. Es un requisito abierto, no basura |

## Tablas a medio usar

| Tabla | Origen | Qué pasa | Filas locales | Recomendación |
|---|---|---|---|---|
| `historial_coherencia` | V1 | Solo tiene escritor (`PuntajeService` → `SaveHistorialCoherenciaPort`), y ese escritor (`RegistrarCoherenciaDiariaUseCase`) no lo llama nadie. Ya está anotado en `PARA_COMPROBAR_2026-09-14.md` y en `PROPUESTA_AJUSTE_DIAS_PROGRAMA.md`. V68 explica que el semáforo (`semaforo_dias`/`semaforo_semanas`) la reemplazó | 0 | **Eliminar más adelante**, junto con el caso de uso muerto, si el semáforo ya cubre la necesidad. Antes, confirmar en producción que tiene 0 filas |
| `revisiones_semanales_sin_celular` | V1 | Solo aparece en un `DELETE` cuando se borra una cuenta. La penalización semanal (−10) del Día sin celular nunca se construyó (RF-15 ❌) | 0 | Dejar mientras RF-15 siga vivo. Eliminar si el dueño descarta la penalización |
| `anomalias_acompanamiento` | V45 | Solo la escribió la migración V45, una vez, para dejar a la vista punteros mentor/célula contradictorios. El código solo la borra cuando se elimina una cuenta | 0 (en producción puede tener filas) | **Dejar**. Es un registro histórico de la reconciliación. No borrar sin ver antes si en producción tiene filas |
| `asignaciones_curso` + `miembros_grupo` | V1 | Las lee `AccesoCursoService` para decidir el acceso a cursos, pero nadie las escribe: no hay endpoint para asignar un curso ni para armar un grupo de academia | 0 y 0 | Decidir si existe el «acceso a cursos por asignación/grupo». Si existe, **conectar** (falta el endpoint de administración). Si no, eliminar las dos junto con `grupos` y simplificar `AccesoCursoService` |
| `niveles_membresia` | V1 | Solo se lee (para la audiencia de eventos `NIVEL_MINIMO`). Nadie la escribe y no tiene siembra (CL-2 en `MODULO_CALENDAR.md`). También sobra la columna `eventos.nivel_minimo_id` | 0 | Dejar. D-40 dice que los catálogos «llegan con la migración de datos». Si el nivel de membresía no va a existir, eliminar la tabla y la audiencia `NIVEL_MINIMO` |
| `roles_permitidos_curso` | V1 | Solo se lee. Nadie la escribe | 0 | Dejar. Vacía significa «curso abierto a todos los roles», y funciona |
| `recomendaciones_academia` | V1 | Está conectada a través de la Clase diaria, que la app sí usa, pero la IA que recomienda es `NoOpRecomendarClaseAdapter`, que siempre devuelve vacío. Por eso nunca se escribe una fila (RF-38 ⚠️) | 0 | Dejar. Se llena sola cuando se conecte una IA real |

### Funciones con backend completo que la app no llama

Lo que sigue tiene endpoint, servicio y persistencia, pero en el frontend (`master`) no aparece ninguna llamada a esas rutas:

| Tabla | Endpoint sin cliente | Otra vía de entrada | Filas locales | Recomendación |
|---|---|---|---|---|
| `eventos_verdugo` | `/api/v1/enforcer-events` | `VerdugoIgnoradoScheduler` (no hace nada si no hay filas) | 0 | Conectar o descartar el «modo Verdugo» |
| `testimonios` | `/api/v1/testimonios` | ninguna. Comunidad muestra testimonios **estáticos** | 0 | Conectar (Comunidad → Testimonios) o descartar |
| `grabaciones_v90` | `/api/v1/onboarding/v90-recordings` | ninguna | 0 | Conectar o descartar la validación V90 |
| `dias_semanales_habito` | `/api/v1/weekly-habit-days` | RenasIA (`DiaDeHabitoSemanalConfirmable`) | 0 | Dejar: RenasIA puede escribirla |
| `sesiones_bloqueo` (Santuario) | `/habit-tracks/{id}/santuario/*` | RenasIA (propuesta «Iniciar Santuario», D-156) | 0 | Dejar |
| `rachas_sin_celular` (Día sin celular) | `/habit-tracks/{id}/phone-free/*` | RenasIA (propuesta «Día sin celular», D-156) | 0 | Dejar |
| `informes_espejo_sombra` + `preguntas_confrontacion` | `/api/v1/espejo-sombra` | Barrido semanal cada hora (`GenerarInformesSemanalesScheduler`) y RenasIA, que los lee | 0 | Dejar. En local no hay informes, probablemente porque no hay IA configurada. Revisar en producción |

Hay tablas usadas y conectadas que igual tienen 0 filas en local, y eso solo significa que nadie probó esa opción en esta base: `acciones_diarias`, `observaciones_mentor`, `rocas_mensuales`, `tickets_mentor`, `tokens_push`, `reacciones_muro`, `excepciones_evento`, `reglas_recordatorio_evento`, `roles_destino_evento` y `dias_semana_recurrencia`.

### Tablas de solo escritura a propósito (no son un problema)

- `auditoria_eliminacion_cuentas` (V90): auditoría append-only de las cuentas borradas (D-243).
- `ajustes_puntos_liga`: libro de movimientos de puntos. La app no lo lee, pero lo lee la vista `verificacion_puntos_liga` (control manual: «debe devolver 0 filas»). Ninguna parte del código consulta esa vista.
- `ajustes_dia_programa`, `semaforo_semanas` y `historial_confirmaciones_evento`: append-only, y además se leen.

## Columnas sin uso

| Columna | Qué pasa | Datos locales | Recomendación |
|---|---|---|---|
| `participantes_programa.habitos_escalonados_en` | Nadie la lee ni la escribe (ya documentado en `.claude/rules/04`) | 0 con dato | Eliminar en una migración de limpieza, después de verificar producción |
| `usuarios.estado_cambiado_en` | Nadie la lee ni la escribe. El historial de suspensiones se anota al recibir `EstadoDeCuentaCambiadoEvent` (E-348) | **4 con dato** (de alguna siembra o prueba vieja) | Dejar hasta ver producción. No borrar si allá tiene datos |
| `usuarios.motivo_estado` | Igual | 0 | Eliminar junto con la anterior si producción también está vacía |
| `habitos.tipo_entrada_diario` | La entidad no la mapea (la anota como «nullable, sin uso») | 1 con dato | Dejar. Es parte del CHECK `diario_solo_journaling` |
| `habitos.grupo` | La entidad no la mapea. La llenó la siembra V4 | 7 con dato | Dejar. Es un dato de catálogo inofensivo |

`usuarios.telefono`, `ciudad` y `pais` tampoco los mapea la entidad, pero se leen por SQL en otros adaptadores, así que no están sin uso.

---

## Tabla resumen completa

Leyenda: **LE** = leída y escrita por la app · **L** = solo leída · **E** = solo escrita · **—** = ninguna referencia · **(B)** = además se borra cuando se elimina una cuenta.

| Tabla | Estado | Usada por | Filas locales | Recomendación |
|---|---|---|---|---|
| acciones_diarias | LE | rocks (`@ElementCollection` de `rocas_diarias`) | 0 | dejar |
| acciones_mapa | LE | onboarding (mapa) | 6 | dejar |
| adjuntos_guia | LE | habits (guías) | 2 | dejar |
| agenda_ocupada | LE | rocks (agenda semanal) | 5 | dejar |
| ajustes_dia_programa | LE (append-only) | users | 20 | dejar |
| ajustes_puntos_liga | E + vista | points (ledger) | 28 | dejar |
| animales_de_fase | LE | phasecontracts (V94) | n/d (local en V89) | dejar |
| anomalias_acompanamiento | escrita solo por V45 (B) | nadie | 0 | dejar (histórico) |
| asignaciones_celula | LE | community/mentoring | 116 | dejar |
| asignaciones_curso | L | academy (`AccesoCursoService`) | 0 | decidir: conectar o eliminar |
| asistencias_evento | LE | calendar (V93, D-256) | n/d | dejar |
| audios_espiritu | LE | habits/espíritu | 45 | dejar |
| audioterapias | LE | habits | 13 | dejar |
| auditoria_cambios_rol | — | nadie | 0 | conectar (RF-04) o eliminar |
| auditoria_eliminacion_cuentas | E (auditoría) | users (V90) | n/d | dejar |
| base_conocimiento | LE | rag (pgvector) | 35 | dejar |
| cambios_bienvenida | LE | chat | 2 | dejar |
| cambios_horario_pendientes | LE | habits | 15 | dejar |
| categorias_habito | catálogo vía FK | habits (`habitos.categoria_clave`) | 4 | dejar |
| categorias_muro | LE | community | 5 | dejar |
| celulas | LE | community/mentoring | 18 | dejar |
| cohortes | LE | community | 1 | dejar |
| comentarios_muro | LE | community | 2 | dejar |
| confirmaciones_evento | LE | calendar | 6 | dejar |
| contratos_fase | LE | phasecontracts | 4 | dejar |
| conversaciones | LE | chat | 153 | dejar |
| conversaciones_renasia | LE | rag | 53 | dejar |
| cursos | LE | academy | 23 | dejar |
| desbloqueos_habito | LE | habits | 10 | dejar |
| dias_accion_mapa | LE | onboarding (`@ElementCollection`) | 27 | dejar |
| dias_semana_recurrencia | LE | calendar | 0 | dejar |
| dias_semanales_habito | LE, sin cliente (solo RenasIA) | habits | 0 | dejar |
| entradas_diario | LE | habits | 1 | dejar |
| estado_onboarding | LE | onboarding | 39 | dejar |
| etapas_onboarding_completadas | LE | onboarding/caja | 87 | dejar |
| event_publication (public) | LE | Spring Modulith (outbox) | 138 | dejar (infraestructura) |
| eventos | LE | calendar | 12 | dejar |
| eventos_verdugo | LE, sin cliente | habits (Verdugo) | 0 | conectar o descartar |
| evidencias | LE | habits | 20 | dejar |
| excepciones_evento | LE | calendar (cancelar ocurrencia) | 0 | dejar |
| flyway_schema_history (public) | Flyway | — | 80 | dejar (infraestructura) |
| fuentes_mensaje_renasia | LE | rag | 125 | dejar |
| grabaciones_v90 | LE, sin cliente | onboarding | 0 | conectar o descartar |
| grupos | — | solo FK | 0 | decidir junto con asignaciones_curso |
| guias_habito | LE | habits | 17 | dejar |
| habitos | LE | habits | 47 | dejar |
| historial_cambios_horario | LE | habits | 29 | dejar |
| historial_coherencia | E (escritor sin llamador) | nadie en la práctica | 0 | eliminar más adelante |
| historial_confirmaciones_evento | LE | calendar (V93) | n/d | dejar |
| horario_semanal_habito | LE | habits | 64 | dejar |
| horarios_habito | LE | habits | 43 | dejar |
| horarios_habito_por_fecha | LE | habits | 2 | dejar |
| iconos_habito | catálogo vía FK | habits (`habitos.icono_clave`) | 20 | dejar |
| identidades_externas | LE | users (login externo) | 1 | dejar |
| informes_espejo_sombra | LE (barrido + RenasIA), sin cliente | rag | 0 | dejar |
| lecciones | LE | academy | 473 | dejar |
| listas_asistencia_evento | LE | calendar (V93) | n/d | dejar |
| medias_onboarding | LE | onboarding | 32 | dejar |
| medias_publicacion | LE | community | 6 | dejar |
| memorias_renasia | LE | rag | 3 | dejar |
| mensajes | LE | chat | 83 | dejar |
| mensajes_bienvenida | LE | chat | 2 | dejar |
| mensajes_renasia | LE | rag | 1362 | dejar |
| miembros_grupo | L | academy | 0 | decidir junto con asignaciones_curso |
| niveles_membresia | L | calendar (audiencia) | 0 | dejar o eliminar con `NIVEL_MINIMO` |
| notificaciones | LE | notifications | 1165 | dejar |
| observaciones_mentor | LE | mentoring (líder) | 0 | dejar |
| opciones_pregunta | LE | onboarding | 63 | dejar |
| participantes_conversacion | LE | chat | 778 | dejar |
| participantes_programa | LE | users | 73 | dejar |
| perfiles_mentor | LE | mentoring | 6 | dejar |
| permisos | — | nadie (D-21) | 11 | **eliminar** |
| politicas_mentoria | LE | mentoring | 1 | dejar |
| preferencias_horario | LE | habits | 20 | dejar |
| preferencias_notificacion | LE | notifications | 3 | dejar |
| preguntas_confrontacion | LE | rag (hija de espejo) | 0 | dejar |
| preguntas_onboarding | LE | onboarding | 145 | dejar |
| progreso_lecciones | LE | academy | 6 | dejar |
| propuestas_acompanante | LE | rag | 103 | dejar |
| protocolos_reemplazo_mapa | LE | onboarding | 2 | dejar |
| publicaciones_muro | LE | community | 6 | dejar |
| puntajes_participante | LE | points | 7 | dejar |
| rachas_sin_celular | LE, sin cliente (RenasIA) | habits | 0 | dejar |
| ranking_aprendices | LE | points | 331 | dejar |
| ranking_celulas | — | nadie | 0 | dejar (reservada) o eliminar |
| reacciones_muro | LE | community | 0 | dejar |
| recomendaciones_academia | LE, IA NoOp | academy | 0 | dejar |
| recordatorios_evento | LE | calendar | 628 | dejar |
| recuerdos_renasia | LE | rag | 10 | dejar |
| recurrencias_evento | LE | calendar | 1 | dejar |
| recursos_leccion | LE | academy | 18 | dejar |
| registros_espiritu | LE | habits | 12 | dejar |
| registros_habito | LE | habits | 1974 | dejar |
| registros_radar | LE | habits/radar | 4 | dejar |
| reglas_recordatorio_evento | LE | calendar | 0 | dejar |
| renombres_habito | LE | habits | 1 | dejar |
| respuestas_onboarding | LE | onboarding | 148 | dejar |
| revisiones_semanales_sin_celular | solo (B) | nadie | 0 | dejar mientras RF-15 siga vivo |
| rocas_diarias | LE | rocks | 14 | dejar |
| rocas_maestras | LE | rocks | 40 | dejar |
| rocas_mensuales | LE | rocks | 0 | dejar |
| rocas_semanales | LE | rocks | 28 | dejar |
| rol_permiso | — | nadie (D-21) | 22 | **eliminar** |
| roles | L (catálogo) | calendar/academy (`RolesCatalogoCache`) | 5 | dejar |
| roles_destino_evento | LE | calendar | 0 | dejar |
| roles_permitidos_curso | L | academy | 0 | dejar |
| secciones_curso | LE | academy | 171 | dejar |
| secciones_onboarding | LE | onboarding | 29 | dejar |
| semaforo_dias | LE | points (semáforo) | 1682 | dejar |
| semaforo_pausas | LE | points | 2 | dejar |
| semaforo_semanas | LE (append-only) | points | 229 | dejar |
| sesiones_bloqueo | LE, sin cliente (RenasIA) | habits (Santuario) | 0 | dejar |
| shedlock | LE | ShedLock (`@SchedulerLock`) | 20 | dejar (infraestructura) |
| solicitudes_cuenta | LE | users | 68 | dejar |
| solicitudes_emergencia | LE | users (V91, D-244) | n/d | dejar |
| testimonios | LE, sin cliente | community | 0 | conectar o descartar |
| tickets_mentor | LE | mentoring (bandeja del líder) | 0 | dejar |
| tickets_soporte | LE | support | 1 | dejar |
| tokens_push | LE | notifications | 0 | dejar |
| usuarios | LE | users | 101 | dejar |
| *vista* verificacion_puntos_liga | sin uso desde el código | control manual | — | dejar |

`acciones_criticas` (V1) ya no existe: V62 la borró vacía.

---

## Detalles técnicos

**Esquema esperado contra esquema local.**
- En `master` hay 85 migraciones (V1 a V94). Crean 120 tablas, V62 borra `acciones_criticas` y queda en 119. Hay que sumar `public.flyway_schema_history` y una vista, `verificacion_puntos_liga`. Ninguna migración renombra tablas.
- `event_publication` (V2) y `flyway_schema_history` viven en `public`. El resto, en `renaser`.
- La base local (`renaser-db`) está en **V89**: tiene 112 tablas en `renaser` y 2 en `public`. Le faltan exactamente las 6 tablas de V90 a V94 (`auditoria_eliminacion_cuentas`, `solicitudes_emergencia`, `asistencias_evento`, `listas_asistencia_evento`, `historial_confirmaciones_evento` y `animales_de_fase`). No hay ninguna tabla de más ni ninguna diferencia rara. Las filas de esas 6 figuran como «n/d».

**Método.**
1. Para cada tabla busqué `@Table(name=…)` y `@CollectionTable` en `src/main/java` de master. Para cada entidad seguí la cadena repositorio Spring Data → adaptador → puerto `out` → servicio → caso de uso → controller, scheduler o listener.
2. Las tablas sin entidad las busqué como SQL (`renaser.<tabla>` en `JdbcClient`/`JdbcTemplate`/`@Query` nativas) y separé SELECT de INSERT/UPDATE/DELETE. Un DELETE en `BorradoDeCuenta*Adapter` no lo conté como uso.
3. Para las tablas con backend pero sin datos, busqué las rutas en el frontend (`git grep` sobre `master` de `Renaser-90-dias-frontend-`): `enforcer-events`, `testimonios`, `v90-recordings`, `weekly-habit-days`, `santuario`, `phone-free` y `espejo-sombra` no aparecen. `tickets`, `rocks/monthly`, `push-tokens`, `leadership/mentors/…/observations`, `cancel-occurrence` y `attendance` sí aparecen.
4. Columnas: crucé las 940 columnas locales con el cuerpo de cada entidad (sin comentarios) y con el SQL de otros adaptadores. Solo quedaron las 5 de arriba.
5. En local conté las filas con `count(*)` exacto y miré `pg_stat_user_tables` (n_tup_ins/upd/del). Todas las tablas candidatas tienen 0 inserciones desde el último reinicio de estadísticas.

**Hallazgos de código que conviene registrar** (no los toqué):
- `RegistrarCoherenciaDiariaUseCase` / `PuntajeService.registrarCoherencia…` no tiene ningún llamador. Es código muerto que sostiene a `historial_coherencia`.
- `AccesoCursoService` evalúa `asignaciones_curso` y `miembros_grupo`, que nadie puede llenar.
- `NoOpRecomendarClaseAdapter` siempre devuelve `Optional.empty()`, así que `recomendaciones_academia` nunca se escribe.
- D-156 dice que los tickets al mentor «se retiraron de la app el 2026-09-07», pero el frontend `master` todavía monta la bandeja de tickets del líder en `HoyScreen`. Habría que confirmar cuál de los dos tiene razón.
- Regla vigente: `D-40` («ningún módulo crea migraciones») quedó en los hechos superada por V2 a V94. No la corregí en la documentación, porque no es parte de este pedido.

**Script de producción (preparado, no ejecutado).** `~/.cache/renaser-e2e/consulta-uso-tablas.sh`:
- Usa el mismo mecanismo que `consulta-olga-estados.sh`: verifica la cuenta AWS 302277511407 y llega por SSM a i-0ea00f555c5fe8028. Abre la sesión con `default_transaction_read_only=on` y todo va dentro de `BEGIN READ ONLY … ROLLBACK`.
- Por cada tabla devuelve: filas, última fecha de `creado_en`/`actualizado_en`, lecturas (`seq_scan + idx_scan`) e inserts/updates/deletes desde que se reiniciaron las estadísticas. Además, cuántas filas tienen dato en las 5 columnas sin uso, la versión de Flyway y la fecha de reinicio de estadísticas. No devuelve ningún dato personal.
- Lo pasé por `bash -n` sin errores, y su SQL lo corrí en la base local en modo solo lectura: funciona.
- Ojo al interpretar los escaneos: un `seq_scan` también puede venir de una verificación de FK o de un borrado de cuenta, así que no prueba que la app la lea. Las inserciones (`n_tup_ins`) sí son una señal confiable de escritura.

**Antes de escribir cualquier migración de borrado**: correr ese script, confirmar 0 filas en producción y registrar la decisión en `docs/MODULOS_A_AVANZAR.md` §8 (verificando antes el último D-nn usado).

## Resultado en producción (2026-10-07, mismo día)

> **Agregado 2026-10-07.** Arriba dice «no consulté producción» y «script preparado, no ejecutado». Después el dueño
> autorizó correrlo y se corrió (`consulta-uso-tablas.sh`, solo lectura: `BEGIN READ ONLY … ROLLBACK`). Se dejó el
> texto original a la vista; esto es lo que dio. El script tenía `public.flyway_schema_history` y se corrigió a
> `renaser.flyway_schema_history`.

- **Solo datos de siembra:** `permisos`, `rol_permiso` (D-21 ya las dio por superadas).
- **Vacías en producción:** `grupos`, `miembros_grupo`, `asignaciones_curso`, `auditoria_cambios_rol`,
  `historial_coherencia`, `ranking_celulas`, `eventos_verdugo`, `testimonios`, `grabaciones_v90`,
  `recomendaciones_academia`.
- **Vacías pero leídas a cada rato** (más de 31 000 lecturas): `niveles_membresia` y `roles_permitidos_curso`. **No
  se borran**: el código las consulta aunque estén vacías.
- **Columnas sin dato en producción:** `usuarios.estado_cambiado_en`, `usuarios.motivo_estado`,
  `participantes_programa.habitos_escalonados_en`.

**Nada se borró.** Las decisiones quedan para el dueño (ver `pendientes-2026-10-07.md` §A.1). Si se borra algo, va en
una migración nueva con cabecera justificada y la decisión en `MODULOS_A_AVANZAR.md` §8.
