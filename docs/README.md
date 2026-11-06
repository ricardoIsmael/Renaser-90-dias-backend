# Documentos del backend

Mapa de esta carpeta (arnés propio, 2026-11-06). **Vigente** = se mantiene al día y se puede seguir. **De referencia** =
diseño o contrato de un momento: útil para entender el porqué, pero compáralo con el código antes de tomarlo como regla.
**Histórico** = foto de un día; no se actualiza. Lo que manda siempre: el código, las pruebas, `.claude/rules/` y las
specs de `../specs/`.

## Vigente

| Documento | Para qué |
|---|---|
| [`BITACORA_ERRORES.md`](BITACORA_ERRORES.md) | Cada error con su síntoma literal, causa real y cómo evitarlo (`E-NN`). Se busca con `grep`, no se lee entero. |
| [`MODULOS_A_AVANZAR.md`](MODULOS_A_AVANZAR.md) | Registro de decisiones (§8, `D-NN`) y estado por módulo. |
| [`DESPLIEGUE_Y_CI.md`](DESPLIEGUE_Y_CI.md), [`PRUEBAS_EN_CLOUD.md`](PRUEBAS_EN_CLOUD.md) | CD a EC2 + RDS, CI y Testcontainers Cloud. |
| [`ELIMINACION_DE_CUENTAS.md`](ELIMINACION_DE_CUENTAS.md) | Cómo se cierra y se borra una cuenta (D-243). |
| [`api/`](api/) | Contratos de la API que consume la app. |
| [`arquitectura/SEMAFORO_DEL_APRENDIZ.md`](arquitectura/SEMAFORO_DEL_APRENDIZ.md) | Contrato del semáforo. |

## De referencia

| Documento | Qué es |
|---|---|
| [`ARQUITECTURA.md`](ARQUITECTURA.md) | El diseño de la arquitectura (antes `CLAUDE.MD`; los comentarios «`CLAUDE.MD` §N» del código apuntan aquí). Su §0 es historia: las reglas vigentes están en `.claude/rules/`. |
| `MODULO_*.md` | El diseño inicial de cada módulo (agosto de 2026). |
| [`CUMPLIMIENTO_REQUISITOS.md`](CUMPLIMIENTO_REQUISITOS.md), [`spec/`](spec/), [`specs/`](specs/) | Requisitos del cliente y especificaciones anteriores a spec-kit. |
| [`ESTADO_DEL_PROYECTO.md`](ESTADO_DEL_PROYECTO.md), [`MAPA_ENDPOINTS.md`](MAPA_ENDPOINTS.md) | Estado y mapa con fecha; el estado al día está en `../PROGRESO.md`. |
| [`arquitectura/`](arquitectura/), [`db/`](db/), [`rag/`](rag/) | Propuestas, análisis de la base y estilo del acompañante. |

## Histórico

`AVANCES_*`, `INFORME_*`, `REPORTE_*`, `PENDIENTES_*`, `PARA_COMPROBAR_*`, `REPARTO_AGENTES_*`, `VERIFICACION_*`,
`ESTADO_FORMACIONES_*`, `AUDITORIA_*`, `PLAN_*`, `PROPUESTA_*`, `GAPS_*`, `ENDPOINTS_FALTANTES.md`, `FEATURE_POST_PROGRAM.md`,
`INFRA_S3_BUCKET.md`, `PRUEBAS_ENDPOINTS_RAG.md`, [`informes/`](informes/) y [`reporte-del-dia/`](reporte-del-dia/).
No se movieron de lugar porque el código y otros documentos los citan por su ruta.
