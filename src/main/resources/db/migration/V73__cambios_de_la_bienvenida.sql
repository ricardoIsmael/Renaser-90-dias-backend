-- La bitacora de lo que Administracion cambia de la bienvenida automatica (D-210, 2026-09-27).
--
-- Que problema resuelve
-- ---------------------
-- El duenio pidio que ADMIN y ALCHEMIST puedan cambiar desde la app la portada de la tarjeta de
-- bienvenida y sus tres mensajes (soporte: el que acompana la tarjeta y el formal; grupo). Hasta hoy
-- los textos vivian solo en `src/main/resources/bienvenida/mensajes.yaml` y la portada en
-- `bienvenida/fondo.png` (D-190, D-174): cambiarlos era editar el repo y redesplegar. Esos archivos
-- siguen siendo los ORIGINALES; esta tabla guarda lo que Administracion pone encima, quien y cuando,
-- y la vuelta al original.
--
-- Por que no se reusa otra cosa
-- -----------------------------
-- * No hay tabla de configuracion general: V45 lo decidio asi ("no hay tabla generica de
--   configuracion"), y `politicas_mentoria` son parametros POR COHORTE del modulo community (un modulo
--   no escribe tablas ajenas, D-41); la bienvenida es una sola para todo el programa.
-- * `mensajes_bienvenida` (V1) es la marca de «a esta persona ya se le dio»: PK por destinatario.
-- * Ni variables de entorno ni Parameter Store: el duenio ya descarto el entorno para estos textos
--   (D-190), y ahi tampoco queda quien cambio que.
-- * Ni un archivo en S3: sin historia, y en local el almacenamiento es de marcador (no guarda nada),
--   asi que los textos no se podrian cambiar en local.
-- * No se copian a la base los textos del YAML ni la portada original: seguirian viviendo en dos
--   lugares y se desincronizarian con el primer cambio del repo (la leccion de V22). Una pieza que
--   nunca cambio no tiene filas, y sale su original.
--
-- Como se lee
-- -----------
-- Append-only, como `ajustes_dia_programa` (V21): no hay UPDATE ni DELETE desde el codigo. Lo vigente
-- de cada pieza es su ULTIMA fila (la de `id` mas alto). Volver al original es OTRA fila, con `texto`
-- y `portada_ruta` en NULL, y las dos quedan a la vista. `id` es una identidad creciente y no un uuid
-- justamente para eso: dos cambios en el mismo instante tendrian un orden ambiguo por `cambiado_en`.
--
-- Columnas
-- --------
-- `pieza`: SOPORTE_CON_LA_TARJETA, SOPORTE_FORMAL, GRUPO o PORTADA (`PiezaDeBienvenida`). Texto con
-- CHECK y no un ENUM de Postgres: sumar una pieza es cambiar el CHECK, sin ALTER TYPE.
-- `texto`: el mensaje nuevo, de 1 a 1000 caracteres (`TextoDeBienvenida.LARGO_MAXIMO`). Que conserve
-- sus marcadores ({nombre}, {mentor}) lo exige el dominio: depende de la pieza y no se puede expresar
-- bien con un CHECK de largo fijo.
-- `portada_ruta`: la clave del objeto en el mismo almacenamiento que las fotos de evidencia, bajo
-- `bienvenida/portadas/` (las unicas que emite el servidor).
-- `cambiado_por`: ON DELETE SET NULL, como `asignaciones_celula.actor_id` (V45). Borrar la cuenta de
-- quien hizo el cambio no puede fallar por esta tabla (la purga de cuentas), ni borrar el cambio:
-- queda el que, el cuando y el texto, y se pierde solo el quien.
--
-- Por que este nombre
-- -------------------
-- `cambios_bienvenida`: cada fila es un cambio, como `historial_cambios_horario` y
-- `auditoria_cambios_rol`, y es hermana de `mensajes_bienvenida`.
SET search_path TO renaser, public;

CREATE TABLE cambios_bienvenida (
    id            bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pieza         text        NOT NULL
                  CHECK (pieza IN ('SOPORTE_CON_LA_TARJETA', 'SOPORTE_FORMAL', 'GRUPO', 'PORTADA')),
    texto         text        CHECK (texto IS NULL OR char_length(texto) BETWEEN 1 AND 1000),
    portada_ruta  text        CHECK (portada_ruta IS NULL
                                     OR (portada_ruta LIKE 'bienvenida/portadas/%' AND char_length(portada_ruta) <= 300)),
    cambiado_por  uuid        REFERENCES usuarios (id) ON DELETE SET NULL,
    cambiado_en   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT cambio_de_texto_sin_portada CHECK (pieza = 'PORTADA' OR portada_ruta IS NULL),
    CONSTRAINT cambio_de_portada_sin_texto CHECK (pieza <> 'PORTADA' OR texto IS NULL)
);

-- «El ultimo cambio de cada pieza» es la unica lectura (en cada bienvenida y en cada foto del
-- soporte): un salto por el indice, sin ordenar la tabla.
CREATE INDEX cambios_bienvenida_pieza_idx ON cambios_bienvenida (pieza, id DESC);

COMMENT ON TABLE cambios_bienvenida IS
    'Bitacora append-only de los textos y la portada de la bienvenida que cambia Administracion desde la app (D-210). Lo vigente de cada pieza es su ultima fila; texto y portada_ruta en NULL = volvio al original del repo.';
