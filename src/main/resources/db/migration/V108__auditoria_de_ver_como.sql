-- V108 — La auditoría de la plataforma registra cuándo Soporte TI entra y sale de «ver como rol» (spec 002, fase 2,
-- US5; contrato fase-2.md §5; D-273)
--
-- Por qué se toca la base:
--   «Ver como» abre una sesión de otra cuenta (la de prueba del rol elegido). Aunque sea de solo lectura, deja ver lo que
--   ve un administrador o un mentor, así que quién la abrió, con qué rol y cuándo la cerró tiene que quedar en la misma
--   auditoría append-only que los interruptores (FR-007). El `CHECK` de `cambios_auditados.tipo` (V101, reemplazado por
--   V106) no acepta un tipo para esto; disfrazarlo de MANUAL mezclaría «cambió algo» con «miró la app».
--
-- Por qué así:
--   * `tipo` es `text` con un CHECK (V101), no un enum: se reemplaza el CHECK, igual que V106.
--   * **Se conserva `ROL_PERSONALIZADO`**, que agregó V106: este CHECK es la lista completa.
--   * El trigger append-only rechaza UPDATE, DELETE y TRUNCATE de filas, no un ALTER de la tabla: las filas ya
--     guardadas no se tocan y todas cumplen el CHECK nuevo.

ALTER TABLE renaser.cambios_auditados DROP CONSTRAINT cambios_auditados_tipo_check;

ALTER TABLE renaser.cambios_auditados ADD CONSTRAINT cambios_auditados_tipo_check
    CHECK (tipo IN ('MANUAL', 'AUTOMATICO', 'DESBLOQUEO', 'REAUTENTICACION', 'ROL_PERSONALIZADO', 'VER_COMO'));
