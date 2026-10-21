-- Lo que el barrido del fin de semana deja en semaforo_semanas: la foto de las semanas ya cerradas (3-9 y 10-16 de octubre).
\set ON_ERROR_STOP on
BEGIN;
DELETE FROM renaser.semaforo_semanas WHERE participante_id::text LIKE 'e2e00500%';
INSERT INTO renaser.semaforo_semanas (participante_id, semana_hasta, semana_desde, porcentaje, dias_con_datos, dias_medidos, version_formula, cerrada_en)
SELECT pp.usuario_id, w.hasta, w.hasta - 6,
       (SELECT round(avg(round(100.0 * (d.habitos_cumplidos + d.objetivos_cumplidos) / (d.habitos_programados + d.objetivos_programados))), 1)
          FROM renaser.semaforo_dias d WHERE d.participante_id = pp.usuario_id AND d.fecha BETWEEN w.hasta - 6 AND w.hasta
           AND d.habitos_programados + d.objetivos_programados > 0),
       (SELECT count(*) FROM renaser.semaforo_dias d WHERE d.participante_id = pp.usuario_id AND d.fecha BETWEEN w.hasta - 6 AND w.hasta
           AND d.habitos_programados + d.objetivos_programados > 0),
       (SELECT count(*) FROM generate_series(w.hasta - 6, w.hasta, interval '1 day') g(f)
         WHERE g.f::date >= pp.fecha_inicio
           AND NOT EXISTS (SELECT 1 FROM renaser.semaforo_pausas sp WHERE sp.usuario_id = pp.usuario_id AND g.f::date BETWEEN sp.desde AND COALESCE(sp.hasta, g.f::date))),
       '1', (w.hasta + 1)::timestamp AT TIME ZONE 'America/Lima' + interval '2 hours'
  FROM renaser.participantes_programa pp
  CROSS JOIN (VALUES (DATE '2026-10-09'), (DATE '2026-10-16')) w(hasta)
 WHERE pp.usuario_id::text LIKE 'e2e00500%' AND pp.fecha_inicio <= w.hasta
   AND EXISTS (SELECT 1 FROM renaser.semaforo_dias d WHERE d.participante_id = pp.usuario_id AND d.fecha BETWEEN w.hasta - 6 AND w.hasta);
SELECT u.nombre_completo, s.semana_hasta, s.porcentaje, s.dias_con_datos, s.dias_medidos FROM renaser.semaforo_semanas s JOIN renaser.usuarios u ON u.id = s.participante_id WHERE s.participante_id::text LIKE 'e2e00500%' ORDER BY 1, 2;
COMMIT;
