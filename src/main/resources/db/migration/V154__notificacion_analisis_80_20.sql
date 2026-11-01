-- V154 — Tipo de aviso propio para «tu análisis 80/20 está listo» (spec 031; D-306)
--
-- Por qué se toca la base: el aviso del teléfono cuando un análisis queda listo (FR-024) se puede apagar desde Yo → Notificaciones. La
-- llave de `preferencias_notificacion` es el tipo: con otro tipo (por ejemplo `RESUMEN_SEMANAL`, que es el del semáforo), silenciar este
-- aviso silenciaría también el del semáforo. Mismo criterio que V46, V59, V70, V87 y V109: un valor más en el enum, no una tabla.
--
-- Por qué ese nombre: nombra lo que la persona recibe. Va en su propia migración porque ALTER TYPE ... ADD VALUE no se puede usar en la
-- misma transacción que lo estrena (igual que V109). La app instalada valida `type` como texto abierto: un valor desconocido no la rompe.

ALTER TYPE renaser.tipo_notificacion ADD VALUE IF NOT EXISTS 'ANALISIS_80_20';
