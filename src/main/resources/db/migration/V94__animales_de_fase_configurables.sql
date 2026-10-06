-- Animales de fase configurables: imagen y nombre del animal de cada fase del programa (D-258, pedido del
-- dueño del 2026-10-06: «un CRUD en administrador para cambiar la imagen»).
--
-- Problema: Yo muestra el animal de la fase actual (Mono, Gorila, Caballo, Águila) con imágenes incluidas
-- en la app. Cambiarlas hoy exige un APK nuevo, y la app no se actualiza por aire. Administración y
-- Alquimista tienen que poder cambiar la imagen y el nombre sin publicar una versión.
--
-- Por qué una tabla nueva: ninguna existente guarda algo por fase del programa (`contratos_fase` es por
-- participante y fase, y `FasePrograma` es un enum de dominio, no una fila). Una fila por fase (PK
-- `fase` 1..4, el mismo número que `FasePrograma.numero()`), con las dos piezas opcionales:
--   · `nombre_animal` NULL = usa el nombre incluido en la app.
--   · `imagen_ruta` NULL = usa la imagen incluida en la app. Es la ruta del objeto en el almacenamiento de
--     archivos (mismo bucket que las evidencias, prefijo `fases/animales/`), nunca una URL: la URL se firma
--     al leer.
-- Sin fila = nada personalizado. «Restaurar» pone las dos columnas en NULL; no hay historial (no lo pidió el
-- dueño; `actualizado_por` y `actualizado_en` dicen solo quién tocó por última vez).
--
-- El CHECK de `fase` evalúa solo la fila. `actualizado_por` es ON DELETE SET NULL, el trato de las demás
-- columnas de «quién lo hizo» (V93, D-243): borrar la cuenta del administrador no borra la configuración.
--
-- Nombre: `animales_de_fase`, en español como el resto del esquema y con la palabra del dueño («animal»).
SET search_path TO renaser, public;

CREATE TABLE animales_de_fase (
    fase            smallint    PRIMARY KEY CHECK (fase BETWEEN 1 AND 4),
    nombre_animal   text,
    imagen_ruta     text,
    actualizado_por uuid        REFERENCES usuarios (id) ON DELETE SET NULL,
    actualizado_en  timestamptz NOT NULL DEFAULT now()
);
