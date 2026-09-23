-- =====================================================================================
-- Propuestas del acompanante: lo que el modelo quiere escribir, esperando el boton de la persona.
--
-- QUE PROBLEMA RESUELVE
--
-- D-132: un pedido viejo del historial se ejecuto con un "Hola" y marco un habito que nadie habia
-- pedido marcar. La causa de fondo es que el modelo podia ESCRIBIR. La fase 2 del acompanante
-- (docs/arquitectura/PROPUESTA_ACOMPANANTE_90_DIAS.md §5.2, D-153) le quita ese camino: una
-- herramienta de escritura ya no escribe, deja una PROPUESTA guardada en el servidor, y la escritura
-- real la dispara la persona con un boton (POST /api/v1/renasia/propuestas/{id}/confirmar).
--
-- La propuesta tiene que vivir en el SERVIDOR, no en el cliente ni en el texto del chat, por tres
-- cosas que solo se pueden verificar con una fila propia:
--   * quien es el dueno (solo el puede confirmarla);
--   * que se va a ejecutar EXACTAMENTE lo que se le mostro (herramienta + argumentos + su huella);
--   * que se ejecuta UNA sola vez aunque el boton se toque dos veces (estado + version).
--
-- POR QUE NO SE REUSA `mensajes_renasia`
--
-- `mensajes_renasia` es el historial conversacional: texto con rol, que se le vuelve a mandar al
-- modelo como contexto. Meter ahi una accion pendiente mezclaria dos cosas con ciclos de vida
-- distintos (un mensaje no cambia de estado; una propuesta pasa por PENDIENTE -> CONFIRMADA...) y,
-- peor, pondria la accion al alcance del modelo en el siguiente turno, que es exactamente lo que
-- D-132 prohibe. Tampoco sirve `acciones_criticas`/`acciones_diarias`: son acciones del PLAN del
-- aprendiz (rocas), no ordenes pendientes de ejecutar.
--
-- POR QUE `argumentos` ES jsonb
--
-- Los argumentos de una herramienta son un mapa nombre -> texto (InvocacionHerramienta) cuya forma
-- depende de cada herramienta; no se consultan por campo desde SQL, solo se guardan y se devuelven
-- tal cual al confirmar. Una columna por argumento obligaria a una migracion por herramienta nueva.
-- Lo que SI se verifica es su integridad, y para eso esta `argumentos_hash` (sha256 de la forma
-- canonica herramienta + argumentos ordenados, calculado en el dominio): si la fila se altera entre
-- proponer y confirmar, la confirmacion no ejecuta.
--
-- POR QUE NO HAY ESTADO `VENCIDA`
--
-- El vencimiento es funcion del reloj (vence_en vs. ahora), asi que se DERIVA en el dominio
-- (PropuestaAccion.estaVencidaEn), no se guarda (regla 02: derivar, no incrementar). Guardarlo
-- exigiria un scheduler que lo marque, y una noche con el scheduler caido dejaria propuestas
-- "pendientes" que en verdad ya vencieron. Una PENDIENTE con vence_en en el pasado ES una vencida.
--
-- POR QUE `version`
--
-- Bloqueo optimista (@Version de JPA). La transicion PENDIENTE -> CONFIRMADA se guarda ANTES de
-- ejecutar la accion; si dos toques leen la misma version, solo uno logra escribirla y el otro
-- recibe el conflicto sin ejecutar nada. No se usa un lock pesimista porque la ejecucion llama a
-- otros modulos y no debe correr dentro de una transaccion larga (C-1).
--
-- LOS CHECK
--
-- Van los que se evaluan con la fila sola (regla 04): el estado es uno de los cuatro; vence despues
-- de crearse; solo una PENDIENTE no tiene fecha de resolucion; una FALLIDA siempre dice por que.
-- Que la resolucion ocurra antes del vencimiento depende del reloj de la aplicacion y lo impone el
-- dominio.
--
-- El nombre sigue al de la tabla de conversacion del mismo modulo (`*_renasia` es el historial;
-- esto es otra cosa, por eso `propuestas_acompanante`).
-- =====================================================================================

CREATE TABLE renaser.propuestas_acompanante (
    id               uuid        PRIMARY KEY,
    participante_id  uuid        NOT NULL REFERENCES renaser.usuarios (id) ON DELETE CASCADE,
    herramienta      text        NOT NULL,
    argumentos       jsonb       NOT NULL,
    argumentos_hash  text        NOT NULL,
    resumen          text        NOT NULL,
    estado           text        NOT NULL,
    creada_en        timestamptz NOT NULL,
    vence_en         timestamptz NOT NULL,
    resuelta_en      timestamptz,
    resultado        text,
    version          bigint      NOT NULL DEFAULT 0,
    CONSTRAINT propuesta_estado_valido
        CHECK (estado IN ('PENDIENTE', 'CONFIRMADA', 'CANCELADA', 'FALLIDA')),
    CONSTRAINT propuesta_vence_despues_de_crearse
        CHECK (vence_en > creada_en),
    CONSTRAINT propuesta_solo_pendiente_sin_resolver
        CHECK ((estado = 'PENDIENTE') = (resuelta_en IS NULL)),
    CONSTRAINT propuesta_fallida_con_motivo
        CHECK (estado <> 'FALLIDA' OR resultado IS NOT NULL),
    CONSTRAINT propuesta_herramienta_no_vacia
        CHECK (btrim(herramienta) <> ''),
    CONSTRAINT propuesta_resumen_no_vacio
        CHECK (btrim(resumen) <> '')
);

CREATE INDEX propuestas_acompanante_participante_idx
    ON renaser.propuestas_acompanante (participante_id, creada_en);

COMMENT ON TABLE renaser.propuestas_acompanante IS
    'Escrituras que el acompanante propuso y la persona confirma o cancela con un boton (D-153). El vencimiento se deriva de vence_en; no hay estado VENCIDA.';
