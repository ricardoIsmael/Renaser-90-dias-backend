-- ============================================================================
-- Borra TODO lo sembrado para el recorrido del rol Mentor.
-- Local / desarrollo. Revisar antes de correr.
--
-- El orden importa: las evidencias cuelgan de los registros, y los registros de
-- los participantes. Se borra de adentro hacia afuera.
-- ============================================================================
SET search_path TO renaser, public;
BEGIN;

-- 1. Evidencias y registros de habito sembrados (marcados con "(PRUEBA)")
DELETE FROM evidencias e
USING registros_habito r, usuarios u
WHERE e.registro_habito_id = r.id AND u.id = r.participante_id
  AND u.email LIKE 'prueba.%@ejemplo.test';

DELETE FROM registros_habito r
USING usuarios u
WHERE u.id = r.participante_id AND u.email LIKE 'prueba.%@ejemplo.test';

-- 2. Historial de acompanamiento de la celula de prueba
DELETE FROM asignaciones_celula a
USING celulas c
WHERE c.id = a.celula_id AND c.nombre LIKE '%(PRUEBA)%';

-- 3. Avisos que el job haya emitido para esos alumnos
DELETE FROM notificaciones WHERE tipo = 'ACOMPANAMIENTO_ALUMNO';

-- 4. Participacion, perfil de mentor de prueba y usuarios
DELETE FROM participantes_programa p
USING usuarios u
WHERE u.id = p.usuario_id AND u.email LIKE 'prueba.%@ejemplo.test';

UPDATE celulas SET mentor_id = NULL WHERE nombre LIKE '%(PRUEBA)%';
DELETE FROM celulas  WHERE nombre LIKE '%(PRUEBA)%';
DELETE FROM cohortes WHERE nombre LIKE '%(PRUEBA)%';
DELETE FROM usuarios WHERE email LIKE 'prueba.%@ejemplo.test';

-- 5. Devolver la cuenta propia a APRENDIZ (opcional: solo si ya no se prueba el rol)
-- UPDATE usuarios SET rol = 'APRENDIZ' WHERE email = 'ricardoismael777@gmail.com';
-- DELETE FROM perfiles_mentor WHERE usuario_id =
--     (SELECT id FROM usuarios WHERE email = 'ricardoismael777@gmail.com');

COMMIT;
