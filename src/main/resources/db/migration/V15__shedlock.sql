-- Tabla requerida por ShedLock (net.javacrumbs.shedlock, proveedor JDBC) para dar lock
-- distribuido a los @Scheduled que lo necesitan (C-5, docs/informes/
-- auditoria-seguridad-concurrencia-2026-09-01.html; ver docs/informes/auditoria-fixes/C-5.md
-- para el analisis de cuales de los 11 @Scheduled del repo lo necesitan de verdad).
--
-- Esquema tomado literal de la documentacion oficial de ShedLock (proveedor
-- shedlock-provider-jdbc-template, JdbcTemplateLockProvider) — no se inventa a mano: es el
-- unico esquema que su SQL de UPDATE/INSERT espera encontrar.
--
-- Explicito a proposito, por el mismo motivo que V3/V11/V12/V13/V14: si esta migracion corre
-- sola en un despliegue posterior, el search_path que fija V1 no esta puesto en la sesion.
SET search_path TO renaser, public;

CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

COMMENT ON TABLE shedlock IS
    'Lock distribuido entre instancias para los @Scheduled inseguros con N instancias (C-5). '
    'Una fila por nombre de lock (net.javacrumbs.shedlock.core.SchedulerLock#name); la '
    'instancia duena la posee hasta lock_until o hasta que libera antes. Sin logica de '
    'negocio propia -- infraestructura pura, igual que event_publication (V2).';
