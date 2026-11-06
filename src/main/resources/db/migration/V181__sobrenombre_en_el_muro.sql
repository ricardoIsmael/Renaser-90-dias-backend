-- Spec 046 (D-331): el sobrenombre que reemplaza el nombre de la persona en el Muro.
--
-- `sobrenombres`: una fila por persona que eligió uno o que dijo «Ahora no» a la invitación del día 7 (D2: la invitación no vuelve).
-- `normalizado` es el sobrenombre sin tildes, en minúsculas y con los espacios juntos: con él se decide que dos son el mismo
-- («Fénix» = «fenix»). La unicidad es DENTRO de la formación (D6), así que no puede ser un índice único: la revisa el servicio
-- con un candado por sobrenombre (`pg_advisory_xact_lock`). `cambiado_por_la_persona_en` cuenta los 30 días entre cambios (D5);
-- un cambio de Administración no lo toca.
-- `sobrenombres_cambios`: la constancia de cada cambio (quién, cuándo, antes, después y, si fue Administración, el motivo).
-- `palabras_prohibidas_sobrenombre`: las que agrega Administración, además de la lista base que vive en el código.
SET search_path TO renaser, public;

CREATE TABLE sobrenombres (
    usuario_id                  uuid PRIMARY KEY REFERENCES usuarios (id) ON DELETE CASCADE,
    sobrenombre                 varchar(20),
    normalizado                 varchar(20),
    elegido_en                  timestamptz,
    cambiado_por_la_persona_en  timestamptz,
    invitacion_descartada_en    timestamptz,
    actualizado_en              timestamptz NOT NULL DEFAULT now(),
    CHECK ((sobrenombre IS NULL) = (normalizado IS NULL))
);

CREATE INDEX sobrenombres_normalizado ON sobrenombres (normalizado) WHERE normalizado IS NOT NULL;

CREATE TABLE sobrenombres_cambios (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id  uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    antes       varchar(20),
    despues     varchar(20),
    por         uuid REFERENCES usuarios (id) ON DELETE SET NULL,
    motivo      varchar(200),
    en          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX sobrenombres_cambios_usuario ON sobrenombres_cambios (usuario_id, en DESC);

CREATE TABLE palabras_prohibidas_sobrenombre (
    palabra      varchar(40) PRIMARY KEY,
    agregada_por uuid REFERENCES usuarios (id) ON DELETE SET NULL,
    agregada_en  timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE sobrenombres IS 'Spec 046: el sobrenombre de cada persona en el Muro y si descartó la invitación del día 7.';
COMMENT ON TABLE sobrenombres_cambios IS 'Spec 046: constancia de cada cambio de sobrenombre (de la persona o de Administración, con motivo).';
COMMENT ON TABLE palabras_prohibidas_sobrenombre IS 'Spec 046: palabras que Administración prohibió en los sobrenombres (además de la lista base del código).';
