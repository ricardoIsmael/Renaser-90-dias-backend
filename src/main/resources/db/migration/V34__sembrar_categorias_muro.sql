-- =====================================================================================
-- Las cinco categorias con las que la comunidad etiqueta lo que publica.
--
-- POR QUE ESTO NO ESTABA
--
-- `categorias_muro` existe desde el baseline (V1) pero se dejo VACIA a proposito: MODULO_COMMUNITY
-- S1.2 documentaba las cinco filas y aclaraba que no se convertian en SQL ejecutable todavia
-- (CM-15), dejandolo para la migracion de datos. El resultado practico fue que el Muro quedo sin
-- catalogo: el compositor del movil mostraba tres etiquetas escritas a mano que no existian en
-- ninguna tabla, y la categoria elegida ni siquiera se enviaba al publicar (E-135). Con las dos
-- mitades arregladas, lo unico que falta para que el Muro funcione son estas filas.
--
-- DE DONDE SALEN ESTOS VALORES, Y POR QUE ESTOS Y NO LOS DEL DOCUMENTO
--
-- Habia DOS fuentes y se contradecian: `MODULO_COMMUNITY.md` daba a `PRESENTACION` el emoji 👋 en
-- orden 5 y a `LOGROS` el orden 3; el panel de administracion del sistema Next.js anterior mostraba
-- 👏 en orden 4 y `LOGROS` en 5. **Manda el panel**, confirmado por el dueno del proyecto
-- (2026-09-06): es lo que la comunidad viene viendo y usando, con 29 publicaciones repartidas entre
-- las cinco. El documento describia una intencion; el panel describe la realidad.
--
-- POR QUE `PRESENTACION` LLEVA `es_sistema = true`
--
-- Es la unica que el producto asigna solo — el arranque guiado manda al aprendiz nuevo a publicar
-- su presentacion. Las otras cuatro las elige la persona. Hoy el endpoint publico NO expone
-- `es_sistema`, asi que la app la muestra como una pastilla mas y se puede elegir a mano; si eso
-- hay que cambiarlo, el cambio es del endpoint, no de este dato.
--
-- IDEMPOTENTE A PROPOSITO
--
-- `ON CONFLICT (clave) DO NOTHING` en vez de un `UPSERT`: si un entorno ya tiene estas filas —o el
-- dueno les cambio el orden o el emoji desde el panel de administracion, que es justo lo que ese
-- panel promete— esta migracion no se los pisa. Sembrar no es imponer.
-- =====================================================================================

INSERT INTO renaser.categorias_muro (clave, etiqueta, emoji, orden, activa, es_sistema)
VALUES ('REVELACIONES',    'Revelaciones',   '✨', 1, true, false),
       ('AGRADECIMIENTO',  'Agradecimiento', '🙏', 2, true, false),
       ('AYUDA',           'Ayuda',          '🤝', 3, true, false),
       ('PRESENTACION',    'Presentación',   '👏', 4, true, true),
       ('LOGROS',          'Logros',         '🏆', 5, true, false)
ON CONFLICT (clave) DO NOTHING;
