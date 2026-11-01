-- V135 — Quién cambió qué en las categorías y en el catálogo de hábitos de una formación (spec 023, FR-024; D-300)
--
-- Por qué se toca la base
-- -----------------------
-- Desde la spec 023 Administración crea, renombra, ordena, activa, desactiva y borra categorías de hábito de cada formación, y copia hábitos de una
-- formación a otra. El dueño quiere saber quién hizo cada cambio y cuándo (FR-024). Ninguna tabla existente lo guarda: `categorias_habito` solo conserva el
-- último estado y `audiencia_habito` (V96) versiona únicamente los roles de un hábito; `cambios_auditados` (V101) es de la consola de Soporte TI y su
-- `tipo` es un CHECK cerrado de interruptores.
--
-- Qué se agrega y por qué es una tabla aparte
-- --------------------------------------------
-- Una tabla de solo agregar (como `consultas_de_ficha`, V109): una fila por cambio, con la formación, qué se tocó (`entidad`: CATEGORIA o HABITO), cuál
-- (`clave`: la de la categoría o el id del hábito), qué pasó (`accion`), quién y un `detalle` en JSON con el antes y el después. Un cambio ya hecho no se
-- reescribe: si se corrige, queda otra fila.

SET search_path TO renaser, public;

CREATE TABLE auditoria_catalogo_habitos (
    id         uuid        PRIMARY KEY,
    formacion  text        NOT NULL REFERENCES formaciones (codigo),
    entidad    text        NOT NULL CHECK (entidad IN ('CATEGORIA', 'HABITO')),
    clave      text        NOT NULL,
    accion     text        NOT NULL CHECK (accion IN ('CREADA', 'RENOMBRADA', 'ICONO', 'ORDENADA', 'ACTIVADA', 'DESACTIVADA', 'ELIMINADA', 'COPIADO')),
    -- Sin llave foránea a `usuarios`, como `cambios_auditados` (V101): la fila sobrevive a la cuenta y la pantalla la muestra sin nombre.
    actor_id   uuid        NOT NULL,
    detalle    jsonb       NULL,
    creado_en  timestamptz NOT NULL
);

-- La consulta típica: lo último que se tocó en una formación.
CREATE INDEX auditoria_catalogo_habitos_formacion_idx ON auditoria_catalogo_habitos (formacion, creado_en DESC);

CREATE FUNCTION rechazar_cambio_en_auditoria_del_catalogo() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'auditoria_catalogo_habitos es de solo agregar: no se permite % (spec 023, FR-024)', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER auditoria_catalogo_habitos_sin_update_ni_delete
    BEFORE UPDATE OR DELETE ON auditoria_catalogo_habitos
    FOR EACH ROW EXECUTE FUNCTION rechazar_cambio_en_auditoria_del_catalogo();
