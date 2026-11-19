-- V199 — Los documentos semanales del liderazgo y su registro de descargas (spec 056, FR-035 a FR-041)
--
-- Por qué se toca la base
-- -----------------------
-- El líder de mentores, Administración y el mentor de un aprendiz se llevan la semana de una persona (o el reporte del mes del cuerpo de
-- mentores) en un documento que el servidor genera, guarda en el almacenamiento privado y entrega con un enlace que vence a los 10 minutos.
-- Dos decisiones del dueño (18/11/2026) piden estado propio: los documentos se guardan PARA SIEMPRE, y queda registrado quién generó cada
-- uno y quién lo descargó, y cuándo. Ninguna tabla de hoy sirve: `consultas_de_ficha` (V109) registra que el líder miró la ficha, no qué
-- archivo se entregó, y un archivo suelto en S3 no dice de qué datos salió ni quién lo pidió.
--
-- * `documentos_semanales`: una fila por documento generado. La `huella` es el SHA-256 de los datos con que se hizo; con los mismos datos
--   sale la misma `ruta` y el documento se REUTILIZA en vez de generarse otra vez (FR-039); si los datos cambian sale otra fila con otra
--   huella y la anterior se queda. El único `(persona_id, tipo, periodo_inicio, huella)` hace que dos pedidos a la vez dejen una sola fila:
--   el segundo recibe la que ya está (`ON CONFLICT DO NOTHING`).
--   `persona_id` es el aprendiz o el mentor de quien es el documento y, en el reporte del mes (`MES`), quien lo pidió, porque ese reporte
--   cambia con las formaciones que ve esa persona. `periodo_inicio` y `periodo_fin` son el sábado y el viernes de la semana en la zona de
--   la persona, o el primer y el último día del mes: son fechas locales, no instantes.
-- * `descargas_de_documentos`: una fila por pedido —también los que reutilizan el documento—, con quién lo pidió y cuándo. Solo se agrega.
--
-- Sin clave foránea hacia `usuarios`, igual que `consultas_de_ficha`: son registros de auditoría de ids y, si la persona de un documento
-- pide borrar su cuenta, cada módulo borra lo suyo (D-243): `BorradoDeCuentaEnLeadershipAdapter` se lleva sus documentos y las descargas
-- que hizo, y avisa de las rutas de sus archivos para que `users` las borre del bucket. Una clave foránea frenaría ese borrado.
-- `huella` es `char(64)` en hexadecimal minúscula porque el SHA-256 siempre mide 64 y la base lo comprueba.

SET search_path TO renaser, public;

CREATE TABLE documentos_semanales (
    id             uuid         PRIMARY KEY,
    persona_id     uuid         NOT NULL,
    tipo           varchar(16)  NOT NULL,
    periodo_inicio date         NOT NULL,
    periodo_fin    date         NOT NULL,
    huella         char(64)     NOT NULL,
    ruta           varchar(300) NOT NULL,
    generado_en    timestamptz  NOT NULL,
    generado_por   uuid         NOT NULL,
    CONSTRAINT documentos_semanales_tipo_chk CHECK (tipo IN ('APRENDIZ', 'MENTOR', 'MES')),
    CONSTRAINT documentos_semanales_periodo_chk CHECK (periodo_fin >= periodo_inicio),
    CONSTRAINT documentos_semanales_huella_chk CHECK (huella ~ '^[0-9a-f]{64}$'),
    CONSTRAINT documentos_semanales_unico UNIQUE (persona_id, tipo, periodo_inicio, huella)
);

CREATE TABLE descargas_de_documentos (
    id           uuid        PRIMARY KEY,
    documento_id uuid        NOT NULL REFERENCES documentos_semanales (id),
    actor_id     uuid        NOT NULL,
    reutilizado  boolean     NOT NULL,
    pedido_en    timestamptz NOT NULL
);

CREATE INDEX descargas_de_documentos_documento_idx ON descargas_de_documentos (documento_id, pedido_en);
CREATE INDEX descargas_de_documentos_actor_idx ON descargas_de_documentos (actor_id);

COMMENT ON TABLE documentos_semanales IS
    'Spec 056: el documento de una semana de un aprendiz o un mentor, o el reporte del mes. Se guarda para siempre; la huella dice con qué datos se hizo.';
COMMENT ON COLUMN documentos_semanales.persona_id IS
    'Spec 056: el aprendiz o el mentor de quien es; en el tipo MES, quien pidió el reporte (depende de sus formaciones). Sin FK: ver la cabecera.';
COMMENT ON COLUMN documentos_semanales.huella IS
    'Spec 056 FR-039: SHA-256 en hexadecimal de los datos con que se generó. Los mismos datos dan la misma ruta y se reutilizan.';
COMMENT ON TABLE descargas_de_documentos IS
    'Spec 056 FR-040: cada pedido de un documento: quién, cuándo y si se reutilizó el que ya existía. Solo se agrega.';
