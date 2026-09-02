package com.renaser.os.points.application.ports.out.puntaje;

import com.renaser.os.points.domain.model.puntaje.PuntajeParticipante;

public interface SavePuntajePort {

    PuntajeParticipante save(PuntajeParticipante puntaje);

    /**
     * Asegura que exista una fila para {@code inicial.participanteId()} con sus valores de
     * partida (100 puntos, 100 de coherencia, racha en 0). INSERT ... ON CONFLICT DO NOTHING:
     * idempotente y nunca lanza por PK duplicada, a diferencia de {@link #save}, que hace
     * merge() y puede competir con un INSERT concurrente de otra transaccion sobre la MISMA
     * fila inexistente (C-12, docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html)
     * — {@code PESSIMISTIC_WRITE} no protege ahi porque no hay fila que bloquear todavia.
     * Postgres resuelve la carrera de dos INSERT concurrentes a nivel de la restriccion
     * UNIQUE, serializando contra la fila en conflicto; el llamador debe releer con
     * {@link LoadPuntajePort#byParticipanteIdParaEscritura} despues de invocar esto para
     * quedar con el mismo bloqueo pesimista que el resto del flujo, sin importar quien haya
     * ganado la carrera de creacion.
     */
    void crearFilaInicialSiFalta(PuntajeParticipante inicial);
}
