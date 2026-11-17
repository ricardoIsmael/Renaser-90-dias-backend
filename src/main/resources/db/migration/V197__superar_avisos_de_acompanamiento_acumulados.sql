-- V197 — Deja como leídos los avisos de acompañamiento repetidos (bug #165)
--
-- Por qué se toca la base
-- -----------------------
-- Hasta este cambio, cada vez que el episodio de un aprendiz se movía (el ancla de «sin actividad» es la obligación más vieja de una ventana
-- que avanza cada día) nacía otro aviso ACOMPANAMIENTO_ALUMNO para el mismo mentor, aprendiz y motivo, y los anteriores quedaban sin leer con
-- cifras viejas. El servidor ya reemplaza al anterior al nacer uno nuevo (AvisoAcompanamientoNotificationListener); esta migración hace lo mismo
-- UNA vez con lo acumulado.
--
-- Qué hace, y qué no
-- ------------------
-- * Solo pone `leida_en = now()` en los avisos ACOMPANAMIENTO_ALUMNO sin leer que NO son el más reciente de su (usuario, ruta, motivo).
--   La ruta (`ruta_app`) ya lleva el grupo y el aprendiz; el motivo se lee del texto del cuerpo, que es el mismo que arma el listener.
-- * NO borra filas ni toca otro tipo de notificación ni otra columna: el aviso queda como historia y la purga a 90 días lo limpia después.
-- * Los leídos y el aviso más reciente de cada grupo quedan intactos. Es idempotente: una segunda corrida no encuentra nada que cambiar.
-- * No agrega columnas ni índices: se reutiliza lo existente en vez de sumar una columna de «motivo» solo para esto.

SET search_path TO renaser, public;

WITH avisos AS (
    SELECT id, leida_en,
           ROW_NUMBER() OVER (
               PARTITION BY usuario_id, ruta_app,
                            (cuerpo LIKE '% evidencia%pendiente%.')
               ORDER BY creado_en DESC, id DESC) AS posicion
    FROM notificaciones
    WHERE tipo = 'ACOMPANAMIENTO_ALUMNO'
)
UPDATE notificaciones n
SET leida_en = now()
FROM avisos a
WHERE n.id = a.id
  AND a.posicion > 1
  AND n.leida_en IS NULL;
