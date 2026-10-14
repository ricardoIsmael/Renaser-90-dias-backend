-- V106 — La auditoría de la plataforma registra los cambios de roles personalizados (spec 002, fase 2; D-269)
--
-- Por qué se toca la base:
--   Crear, editar, desactivar, borrar o reemplazar en bloque un rol personalizado cambia lo que pueden usar personas
--   concretas: tiene que quedar en la misma auditoría append-only que los interruptores (FR-007, US7 escenario 6). El
--   `CHECK` de `cambios_auditados.tipo` (V101) solo acepta los cuatro tipos de la fase 1; sin un tipo propio habría
--   que disfrazarlo de MANUAL, y la consola no podría distinguir «apagó una funcionalidad» de «cambió un rol».
--
-- Por qué así:
--   * `tipo` es `text` con un CHECK (V101), no un enum: se reemplaza el CHECK. El nombre es el que Postgres le puso al
--     CHECK de la columna en V101 (`<tabla>_<columna>_check`).
--   * El trigger append-only rechaza UPDATE, DELETE y TRUNCATE de filas, no un ALTER de la tabla: las filas ya
--     guardadas no se tocan, y el CHECK nuevo las valida igual (todas tienen uno de los cuatro tipos viejos).
--   * Migración aparte de V105, como pide el contrato de la fase 2 (§2): «ver como» (B3) suma `VER_COMO` en V108 y
--     tiene que conservar `ROL_PERSONALIZADO` en su lista.

ALTER TABLE renaser.cambios_auditados DROP CONSTRAINT cambios_auditados_tipo_check;

ALTER TABLE renaser.cambios_auditados ADD CONSTRAINT cambios_auditados_tipo_check
    CHECK (tipo IN ('MANUAL', 'AUTOMATICO', 'DESBLOQUEO', 'REAUTENTICACION', 'ROL_PERSONALIZADO'));
