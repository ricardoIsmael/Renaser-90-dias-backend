-- V100 — Rol «Soporte TI» (spec 002, fase 1; D-267)
--
-- Por qué se toca la base:
--   El dueño pidió (2026-10-13) un rol propio para operar la plataforma sin código: apagar y prender
--   funcionalidades por rol, ver la auditoría y el estado del circuito de la IA. Es «para mí como
--   programador y nada más»: lo tiene solo su cuenta y se asigna desde la base
--   (`scripts/soporte-ti/asignar.sql`), nunca desde la app.
--
-- Por qué así:
--   * `usuarios.rol` es `rol_usuario`: un rol nuevo es un valor nuevo del enum, igual que GUIA_APRENDIZ
--     en V97. No se reusa ADMIN con una marca aparte: Soporte TI tiene la consola, que ADMIN y
--     ALQUIMISTA NO deben ver (FR-025), y una marca aparte obligaría a cada guard a mirarla.
--   * El nombre va en español como los demás valores del tipo (D-21); la API lo llama IT_SUPPORT y la
--     traducción vive en `UserPersistenceMapper`.
--   * Va primero, igual que en `UserRole` (de más a menos alcance: la lista de roles de hábitos por rol
--     los muestra al revés, Aprendiz primero y Soporte TI al final). Nadie compara roles por su orden.
--   * Sola en su migración (E-187): `ADD VALUE` no puede usarse en la misma transacción que lo agrega.
--   * Sin fila en `roles`: esa tabla está SUPERADA (D-21), como en V97.

ALTER TYPE renaser.rol_usuario ADD VALUE IF NOT EXISTS 'SOPORTE_TI' BEFORE 'ALQUIMISTA';
