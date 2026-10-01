package com.renaser.os.support.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Lo que {@code support} expone de los tickets de mentoria para medir la atencion de cada mentor
 * (gestion del Lider de Mentores, SDD 002 RL-09; D-241).
 *
 * <p>Solo hechos de la fila —quien abrio, quien respondio, cuando—, nunca el texto del bloqueo ni la
 * respuesta: quien lo consume cuenta y mide, no lee consultas de aprendices (RL-07).
 *
 * <p>Todo en lote: el padron pide los tickets de todos los mentores en una sola lectura (RNL-03).
 */
public interface AtencionDeTicketsFinder {

    /** Tickets ABIERTOS de esos aprendices, sin importar cuando se abrieron. */
    List<TicketPendiente> pendientesDe(Collection<UserId> participantes);

    /**
     * Tickets que esos usuarios respondieron con {@code respondido_en} en {@code [desde, hasta)}. Solo
     * los que tienen {@code respondido_por} (V88): los anteriores no se atribuyen a nadie.
     */
    List<TicketRespondido> respondidosPor(Collection<UserId> respondedores, Instant desde, Instant hasta);

    /** Cuantos se respondieron en {@code [desde, hasta)} sin registro de quien (anteriores a V88). */
    int respondidosSinAtribucion(Instant desde, Instant hasta);

    record TicketPendiente(UUID ticketId, UserId participanteId, Instant creadoEn) {
    }

    record TicketRespondido(UUID ticketId, UserId respondidoPor, Instant creadoEn, Instant respondidoEn) {
    }
}
