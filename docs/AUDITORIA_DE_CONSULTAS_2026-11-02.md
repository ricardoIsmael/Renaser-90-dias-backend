# Auditoría de consultas, índices y base de datos — formaciones (V125 a V162)

Fecha: 2026-11-02. La corrió un agente de solo lectura sobre una base descartable con ≈ 1.000 aprendices y 1,1 millones de registros de hábito. Aquí queda lo que se
**aplicó en la consolidación**, lo que se **dejó pendiente** y por qué. El informe completo (planes de ejecución, volumen sintético y límites de la medición) no vive en el
repositorio; las cifras de abajo son las suyas.

**Respuesta corta a «¿confiaste exclusivamente en el ORM?»**: no. Lo que importa para el rendimiento ya iba en SQL a mano (`JdbcClient`, consultas nativas, `LATERAL`,
`ON CONFLICT`, paginación por cursor, filtro de formación en `WHERE`) y los índices nuevos de las formaciones sí los usa el planificador. **No faltaba ningún índice en un
camino caliente.** Con 300 aprendices por formación la base no es el cuello de botella (todas las consultas medidas salen en menos de 40 ms en caliente); lo que escalaba
mal era la **cantidad de viajes** a la base.

## Aplicado

| # | Qué era | Qué se hizo | Prueba |
|---|---|---|---|
| H-01 (ALTA) | «Hábitos de todos → personas de un hábito» hacía, por cada persona de la página, el programa (`deParticipante`) y todo el caso de uso de horarios (`deFecha`, ≈ 20 consultas): ≈ 630 por página de 30 y ≈ 2.100 por una de 100. | `ParticipacionProgramaFinder.deParticipantes` (una consulta) y `HorarioDelDiaFinder.horaDeUnHabito` (`HoraDeUnHabitoEnLote`): la hora de UN hábito para toda la página con una consulta por capa (progreso, hábito, horarios del catálogo, preferencia general, cambio general, día de la semana, fecha). La precedencia no se reescribió: las capas las resuelven las mismas funciones que la lectura de una persona y la hora del catálogo sale de `ConsultaPreferenciasHorarioService.vigenteDeCatalogo`. | `HabitosDeTodosIT.personasDelHabitoNoHacenUnaConsultaPorFila` (3 y 15 personas dan las mismas sentencias), `HoraDeUnHabitoEnLoteTest`, `HabitosDeTodosServiceTest`, `ParticipacionesEnLoteIT` |
| H-02 (MEDIA) | Corte del ranking: `saveAllAndFlush` de entidades con `@IdClass` sin `@Version` hace `merge()` = un SELECT y un INSERT por fila (≈ 10.800 viajes por noche) y la formación de cada aprendiz salía de 3 consultas con ~1.000 ids. | `RankingPersistenceAdapter.reemplazar` escribe con `JdbcTemplate.batchUpdate … ON CONFLICT (fecha, tipo, participante_id) DO UPDATE` en lotes de 500; la formación sale de `deParticipantes` (una consulta) y se quita `AlcanceDeFormacionFinder` del adaptador. | `RankingPersistenceAdapterTest` (IT, idempotencia y formación), `RankingPersistenceAdapterCacheTest` |
| H-03 (MEDIA) | Sin `batch_size`, `order_inserts`, `order_updates` ni `reWriteBatchedInserts`. | `spring.jpa.properties.hibernate.jdbc.batch_size: 50`, `order_inserts`, `order_updates` y `data-source-properties.reWriteBatchedInserts` (propiedad del driver en Hikari y no en la URL, para que valga con el `DB_URL` de Parameter Store). Espejado en `src/test/resources/application.yaml`. | La suite completa de ITs corre con las propiedades puestas. |
| H-04 a, c (MEDIA) | El catálogo se leía entero (3 formaciones) para quedarse con una; `gruposOperativos` hacía 1 + C consultas de asignaciones. | `LoadHabitoPort.catalogoActivoDeFormacion` (SQL con `habitos_formacion_catalogo_idx`, V134) que usa `CatalogoAplicable.globalesPara` (la vista de quien acompaña sigue leyendo todo: necesita los de otras formaciones); `LoadAsignacionesPort.porCelulas` (una consulta) en `gruposOperativos`. | `CatalogoAplicableTest`, `AcompanamientoServiceTest.gruposOperativosLeenLasAsignacionesEnUnaSolaLectura` |
| H-05 (MEDIA) | Barrido de avisos cada 5 minutos: recorría a todos los inscritos, uno por transacción. | Prefiltro por conjuntos: `DespacharAvisosHabitoUseCase.conAlgoPorAvisar` (una consulta con `registros_estado_idx`, ayer a mañana en UTC); el scheduler solo visita a quien tiene un registro sin terminar. | `AvisoHabitoServiceTest` |
| H-06 (MEDIA) | Planificar y repetir una semana: por fila un `findById` + `saveAndFlush` (merge = SELECT) y por cada borrado un `existsById` + `deleteById` + `flush` (≈ 300–420 viajes). | `guardarFechasPropias`: una consulta trae lo existente (de ahí sale `creado_en`), lo nuevo va con `persist` (sin SELECT) y lo existente se actualiza en su sitio, un flush; `borrarParaFechas`: un DELETE por hábito. | Las ITs de la spec 028 (planificar y repetir la semana) |
| H-07 (parcial) | «Todos los aprendices» pedía el programa de cada fila de la página. | `PersonasDelPrograma.armar` usa `deParticipantes`. | `LiderVeATodos*` |
| H-11 (BAJA) | Cuatro claves foráneas de auditoría `ON DELETE SET NULL` sin índice: borrar una cuenta recorría esas tablas enteras. | **V163**: cuatro índices parciales (`WHERE col IS NOT NULL`), sin `CONCURRENTLY` (Flyway corre cada script en una transacción; son tablas chicas). | `IndicesDeLaAuditoriaIT` |

## Pendiente (no se aplicó)

- **V163 no borra los tres índices que parecen sobrar** (`ranking_apr_lectura_idx`, `analisis_80_20_formacion_idx`, `celulas_formacion_idx`, hallazgo H-12). Antes hay que
  confirmar en producción, tras unas semanas de uso, `SELECT indexrelname, idx_scan FROM pg_stat_user_indexes WHERE indexrelname IN (…)` = 0. Entonces: `DROP INDEX IF EXISTS …`
  en la V16x siguiente.
- **H-04 b** — el padrón del líder (`PersonasDelPrograma.padron`) carga `aprendicesActivos()` (todos) y `participantesInscritosActivos()` (todos) y filtra después con un `IN`
  de ~1.000 ids. El arreglo (`usuarios JOIN participantes_programa WHERE formacion = ANY(?)` detrás de un método nuevo de `UserSummaryFinder`) obliga a rehacer los dobles
  de `leadership` que hoy siembran `aprendicesActivos()`; no se hizo en la consolidación.
- **H-07 (resto)** — con el buscador, cada tecla relee el padrón entero; pasarlo a `ILIKE` + `LIMIT` en la base depende de H-04 b.
- **Entidades con id asignado que siguen haciendo `merge`** (un SELECT por fila al guardar muchas): `PagosDeLaPersonaService` (`cronograma.cuotas().forEach(guardar)`, 3 a 12
  `saveAndFlush`), `CopiarHabitoService` (copiar un hábito a otra formación inserta horarios y guías de a uno), `DiasDelSemaforoJdbcAdapter.guardar` (un `update` por día).
  Son operaciones de Administración o de pocas filas; con `Persistable<UUID>`/`isNew()` o `batchUpdate` como en el ranking.
- **`despacharDe` con `readOnly`** salvo cuando publica (H-05): publica eventos que otros módulos atienden dentro de la misma transacción; cambiarlo exige revisar a los oyentes.

## Pendiente — BAJA

| # | Dónde | Problema | Arreglo |
|---|---|---|---|
| H-08 | `SpringDataParticipanteConversacionRepository.contarNoLeidos` | Cuenta todos los mensajes posteriores a `ultimo_leido_en`; con `NULL` cuenta todo el historial de la comunidad global (6,7 ms con 15.216). | `SELECT count(*) FROM (SELECT 1 … LIMIT 100)` y mostrar «99+». |
| H-09 | `ConversacionSoporteService` (`esParticipante` dentro de un `for` sobre `deSoporte()`) | Al entrar o salir un integrante del staff recorre todos los soportes (≈ 900 consultas). Evento raro. | Un `INSERT … SELECT … ON CONFLICT DO NOTHING` / un `DELETE … WHERE conversacion_id IN (SELECT id FROM conversaciones WHERE tipo = 'SOPORTE')`. |
| H-10 | `CifrasDeFormacionJdbcAdapter`, `IntegrantesDeFormacionJdbcAdapter`, `PertenenciaJdbcAdapter.DENTRO_DEL_ALCANCE`, `SpringDataAccountRequestRepository.cola` | `u.rol::text = 'APRENDIZ'`, `u.estado::text`, `s.estado::text = …` anulan `usuarios_rol_estado_idx` y `solicitudes_cola_idx` (Seq Scan; hoy < 1 ms). | Comparar contra el literal del enum o `CAST(:p AS renaser.estado_solicitud)`. |
| H-13 | `ranking_aprendices`, `registros_habito` | Sin retención ni particionado: ≈ 2 millones de filas de ranking por año y ≈ 5,4 millones de registros de hábito por año con 900 personas. | Retención del ranking (≈ 120 días, salvo los cortes semanales del podio) antes de fin de año; revisar particionado por mes de `registros_habito` pasados ~20 millones. |
| H-14 | `CierreDelSemaforoService.ConteosEnLote` | El conteo del lote usa el rango unión de todos los del lote: una persona con atraso fuerza leer ese rango para las 200 (35 ms hoy). | Agrupar el lote por rango parecido. |
| H-15 | `VisibilidadPorFormacion`, `AlcanceDeFormacionService` | `prendidosDe(formacion)` y `formaciones.de(actor)` se consultan varias veces dentro de un pedido (lecturas por PK, ≈ 0,1 ms). | Memorizar por pedido o cachear `formaciones` (3 filas) con invalidación al editarlas. |

### Lo de la sección 7 del informe (funciones, triggers y vistas en la base)

La base **no** tiene procedimientos ni lógica de negocio en funciones: 6 funciones de trigger (todas «solo se agrega» o «lo publicado no se edita»), 9 triggers, 1 vista y 2
restricciones `EXCLUDE`. Nada de eso se movió. Huecos de prueba y de duplicación que quedan abiertos:

- `fichas_publicadas_solo_se_agrega()` (V145) no tiene ninguna prueba que ejecute `UPDATE`/`DELETE` contra la tabla.
- `participaciones_anteriores` y `cambios_auditados` no prueban `TRUNCATE`.
- Las listas de columnas de los triggers de V127, V145 y V148 están escritas a mano: una columna nueva en `terminos_versiones`, `fichas_publicadas` o `participaciones_anteriores`
  no queda protegida. Prueba de integración que lea `information_schema.columns`, o comparar `to_jsonb(NEW) - 'publicada_por' IS NOT DISTINCT FROM to_jsonb(OLD) - 'publicada_por'`.
- La vista `verificacion_puntos_liga` lleva escrito el saldo inicial de 100 puntos, que también está en Java.
- SQL de pertenencia que repite la regla de Java con literales de rol (`PertenenciaJdbcAdapter.DENTRO_DEL_ALCANCE`, `CifrasDeFormacionJdbcAdapter.SIN_FORMACION`,
  `ConsultarResumenParticipacionPersistenceAdapter.FILTRO_PADRON`): pasar los roles como parámetro construido desde el dominio y una prueba de arquitectura que falle si
  aparece `'APRENDIZ'` entre comillas en un adaptador `Jdbc`.

## Qué no se movió a la base, y por qué

Ninguna agregación pesada justifica hoy una función almacenada ni una vista materializada: el ranking se calcula en 2 ms en SQL pero duplicaría las reglas (coherencia,
kilómetros, gimnasio, exclusiones) que viven en el dominio; el semáforo ya es incremental (`semaforo_dias`/`semaforo_semanas`); «Hábitos de todos» devuelve 7 mil a 19 mil filas
que Java agrupa (una consulta agregada de ≈ 20 filas es el paso siguiente, sin función SQL ni vista materializada porque el estado cambia con cada hábito marcado).
