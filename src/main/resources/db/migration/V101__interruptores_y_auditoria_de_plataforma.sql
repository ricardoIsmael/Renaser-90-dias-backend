-- V101 — Interruptores de funcionalidades y auditoría de la plataforma (spec 002, fase 1; D-267)
--
-- Por qué se toca la base:
--   El dueño pidió poder apagar cualquier funcionalidad, para todos o por rol, en menos de un minuto y sin
--   reiniciar ni publicar un APK (spec 002, US1). Hasta hoy todo lo que prende o apaga algo se lee UNA vez al
--   arrancar (propiedades de Parameter Store). El estado tiene que vivir en un lugar que compartan todas las
--   instancias y que sobreviva a un reinicio: la base. Cada instancia guarda una copia en memoria y se pone al día
--   por el canal Redis `renaser:funcionalidades` (plan.md D3).
--
-- Qué se guarda y qué no:
--   * El CATÁLOGO (claves, madres, roles, crítica, valor por defecto) vive en código
--     (`plataforma.domain.model.catalogo`), no acá: agregar una funcionalidad es una línea más un @Funcionalidad, y
--     nunca se crea desde la app (FR-001). Estas tablas guardan solo los DESVÍOS: sin fila, rige el valor por
--     defecto del catálogo. Una clave que ya no está en el catálogo se ignora al leer.
--   * `funcionalidades_estado_rol` guarda solo los roles APAGADOS (prendida = false). La columna `prendida` queda
--     por el contrato (§4) y por si una fase futura necesita prender para un rol lo apagado por defecto.
--   * `version` sale de una secuencia global: la mayor de todas las filas es la versión del estado completo (el
--     ETag de `GET /funcionalidades/mias` y el aviso por Redis), y sirve de bloqueo optimista por fila (409).
--     Ninguna fila se borra desde la app, así que la mayor nunca retrocede.
--
-- Auditoría append-only (FR-007):
--   `cambios_auditados` recibe cada cambio manual, cada intento de desbloqueo o reautenticación de la consola y cada
--   apertura o cierre del circuito de la IA. Un trigger rechaza UPDATE, DELETE y TRUNCATE: ni un error de código ni
--   un acceso con la credencial de la app pueden reescribirla (como `ajustes_dia_programa`, pero con candado en la
--   base porque acá el actor es la cuenta con más poder del sistema).
--
-- Desvío del contrato §4, a la vista: `actualizado_por` y `actor_id` NO llevan FK a `usuarios`. La auditoría no se
--   puede borrar, así que una FK impediría para siempre el borrado definitivo de la cuenta que hizo un cambio
--   (D-243, la purga de la gracia de 30 días) y, en las pruebas, la limpieza de las cuentas de prueba. El id queda
--   como dato histórico; si la cuenta ya no existe, la consola muestra el cambio sin nombre.

CREATE SEQUENCE renaser.funcionalidades_version_seq;

CREATE TABLE renaser.funcionalidades_estado (
    clave            text        PRIMARY KEY,
    prendida_global  boolean     NOT NULL,
    aviso            text        NULL CHECK (char_length(aviso) <= 200),
    version          bigint      NOT NULL CHECK (version > 0),
    actualizado_por  uuid        NOT NULL,
    actualizado_en   timestamptz NOT NULL
);

CREATE TABLE renaser.funcionalidades_estado_rol (
    clave     text                NOT NULL REFERENCES renaser.funcionalidades_estado (clave) ON DELETE CASCADE,
    rol       renaser.rol_usuario NOT NULL,
    prendida  boolean             NOT NULL,
    PRIMARY KEY (clave, rol)
);

CREATE TABLE renaser.cambios_auditados (
    id           uuid        PRIMARY KEY,
    tipo         text        NOT NULL CHECK (tipo IN ('MANUAL', 'AUTOMATICO', 'DESBLOQUEO', 'REAUTENTICACION')),
    actor_id     uuid        NULL,
    clave        text        NULL,
    antes        jsonb       NULL,
    despues      jsonb       NULL,
    motivo       text        NULL,
    dispositivo  text        NULL,
    creado_en    timestamptz NOT NULL,
    -- Solo un cambio automático (el circuito de la IA) va sin actor.
    CONSTRAINT cambios_auditados_actor_ck CHECK (tipo = 'AUTOMATICO' OR actor_id IS NOT NULL)
);

-- La consola lista del más nuevo al más viejo, con filtros opcionales por funcionalidad y por persona.
CREATE INDEX cambios_auditados_creado_en_idx ON renaser.cambios_auditados (creado_en DESC);
CREATE INDEX cambios_auditados_clave_idx ON renaser.cambios_auditados (clave, creado_en DESC);
CREATE INDEX cambios_auditados_actor_idx ON renaser.cambios_auditados (actor_id, creado_en DESC);

CREATE FUNCTION renaser.rechazar_cambio_en_auditoria() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'cambios_auditados es append-only: no se permite % (spec 002, FR-007)', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER cambios_auditados_sin_update_ni_delete
    BEFORE UPDATE OR DELETE ON renaser.cambios_auditados
    FOR EACH ROW EXECUTE FUNCTION renaser.rechazar_cambio_en_auditoria();

CREATE TRIGGER cambios_auditados_sin_truncate
    BEFORE TRUNCATE ON renaser.cambios_auditados
    FOR EACH STATEMENT EXECUTE FUNCTION renaser.rechazar_cambio_en_auditoria();
