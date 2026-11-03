-- V165 — Quien ya aceptó los términos no vuelve a firmar el Acuerdo de transformación (decisión del dueño, 2026-11-03)
--
-- Por qué se toca la base
-- -----------------------
-- La V162 publicó el «Acuerdo de transformación» de las tres formaciones con `pide_nueva_aceptacion = true`, así que a todas las personas que ya habían
-- aceptado una versión anterior la app les pidió firmar de nuevo al entrar. El dueño (2026-11-03): «si ya firmaron los que estaban en el programa, ¿para qué
-- volver a firmar?». Se apaga esa marca SOLO en las tres versiones de la V162: quien aceptó cualquier versión anterior de su formación queda al día
-- (`ReglasDeTerminos.quePide` responde NADA), y quien nunca aceptó la de su formación la sigue firmando una vez, como siempre.
--
-- No se inventan aceptaciones: `aceptaciones_de_terminos` no se toca y la constancia de cada persona sigue diciendo qué versión firmó de verdad.
-- Las versiones que publique Administración más adelante deciden su marca al publicarse, como hasta ahora.
-- Idempotente: volver a correrla no cambia nada.
--
-- El candado de la constancia (`terminos_versiones_constancia`, spec 027) no deja editar una versión publicada. Se apaga SOLO para esta línea y se vuelve a
-- encender en la misma transacción: no se toca el texto que la gente firmó (título, cláusulas, casilla, nota, número ni fecha), solo la marca de si se pide
-- volver a aceptar, que es una decisión operativa y no parte de lo firmado.

SET search_path TO renaser, public;

ALTER TABLE terminos_versiones DISABLE TRIGGER terminos_versiones_constancia;

UPDATE terminos_versiones
SET pide_nueva_aceptacion = false
WHERE id IN ('00000000-0000-4000-8000-000000016201',
             '00000000-0000-4000-8000-000000016202',
             '00000000-0000-4000-8000-000000016203')
  AND pide_nueva_aceptacion;

ALTER TABLE terminos_versiones ENABLE TRIGGER terminos_versiones_constancia;
