-- Spec 053 (D-355): el líder de mentores ve un solo tipo al crear un evento, «Sesión personalizada» (decisión del dueño, 2026-11-14).
--
-- Por qué se toca la base: `tipo_evento_calendario` es un enum de Postgres y el servidor lo escribe con el nombre del tipo; sin el valor, guardar el
-- evento del líder fallaría. No se reutiliza `SESION_ESPECIAL` porque Administración y Alquimista siguen creando «Sesión especial» y el rótulo de
-- cada tarjeta (y lo que cuenta el reporte por tipo) debe distinguir una de la otra. Los eventos ya guardados no cambian de tipo.
--
-- Por qué el nombre: el dueño la llamó «Sesión personalizada»; el literal es el mismo en dominio, base y wire (ver `TipoEvento`).
--
-- ALTER TYPE ... ADD VALUE va primero y sin BEGIN/COMMIT, igual que V46 y V109: el valor nuevo no se usa en esta migración.
ALTER TYPE renaser.tipo_evento_calendario ADD VALUE IF NOT EXISTS 'SESION_PERSONALIZADA';
