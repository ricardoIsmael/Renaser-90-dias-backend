-- Bitacora de ajustes manuales del dia del programa (D-82, Fase 1 de
-- docs/PROPUESTA_AJUSTE_DIAS_PROGRAMA.md).
--
-- El problema que cierra: `PUT /api/v1/admin/trainees/{id}/program-day` existe desde el
-- gap #7 y permite a un ADMIN/ALCHEMIST mover a un aprendiz del dia 40 al 34 -- pero NO
-- quedaba registro de quien lo hizo ni por que. La pregunta que el cliente va a hacer
-- ("cuantas veces le movimos el dia a este chico, y por que") hoy no tiene respuesta.
--
-- Por que una TABLA y no columnas en `participantes_programa`: un aprendiz puede pedir
-- esto mas de una vez en 90 dias. Un par de columnas solo guarda el ultimo ajuste y
-- pierde la historia, que es justamente lo que se quiere auditar.
--
-- Append-only por diseno: no hay UPDATE ni DELETE sobre esta tabla desde el codigo. Un
-- ajuste equivocado se corrige con OTRO ajuste, y los dos quedan a la vista -- misma
-- filosofia que `ajustes_puntos` en `points`.
--
-- `dias_ajuste_anterior`/`dias_ajuste_nuevo` (y no solo los dias) porque el offset es lo
-- que de verdad manda el reloj desde V20: guardar ambos deja revertir un ajuste sin tener
-- que recalcular nada, y deja ver el corrimiento acumulado a lo largo del programa.
--
-- `motivo` es NOT NULL a nivel de tabla pero el comando de aplicacion lo acepta vacio
-- durante la ventana de transicion del panel admin (escribe '(sin motivo registrado)').
-- Se prefiere un texto explicito y feo antes que un NULL que obligue a ramificar en cada
-- lectura.
--
-- Cada migracion corre en su propia conexion: si el contenedor arranco en un despliegue
-- posterior, el search_path que fija V1 no esta puesto en la sesion (mismo preambulo que
-- V13, V18 y V20).
SET search_path TO renaser, public;

CREATE TABLE ajustes_dia_programa (
    id                    uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    participante_id       uuid        NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    dia_anterior          smallint    NOT NULL CHECK (dia_anterior BETWEEN 0 AND 90),
    dia_nuevo             smallint    NOT NULL CHECK (dia_nuevo BETWEEN 0 AND 90),
    dias_ajuste_anterior  smallint    NOT NULL,
    dias_ajuste_nuevo     smallint    NOT NULL,
    motivo                text        NOT NULL CHECK (length(motivo) BETWEEN 1 AND 280),
    ajustado_por          uuid        NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
    ajustado_en           timestamptz NOT NULL DEFAULT now()
);

-- "el ultimo ajuste de este aprendiz" es la lectura del panel admin (una por detalle):
-- indice descendente por fecha para que sea un solo salto, no un sort.
CREATE INDEX ajustes_dia_programa_participante_idx
    ON ajustes_dia_programa (participante_id, ajustado_en DESC);

-- ON DELETE RESTRICT en `ajustado_por` a proposito: borrar al admin que hizo el ajuste
-- destruiria la trazabilidad. Si algun dia hay que dar de baja a un admin con ajustes
-- hechos, se decide ahi que hacer -- no se resuelve por defecto perdiendo el dato.

COMMENT ON TABLE ajustes_dia_programa IS
    'Bitacora append-only de PUT /api/v1/admin/trainees/{id}/program-day. Ver V21 y D-82.';
