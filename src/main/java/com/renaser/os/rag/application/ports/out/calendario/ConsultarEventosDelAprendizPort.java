package com.renaser.os.rag.application.ports.out.calendario;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Puerto propio de {@code rag} para leer los eventos del calendario que ve el aprendiz
 * (2026-09-23, herramienta {@code consultar_eventos}). El adaptador delega en
 * {@code calendar.api.EventosDelParticipanteFinder}, que usa el mismo caso de uso que
 * {@code GET /calendar/events}: la audiencia de cada evento la sigue decidiendo {@code calendar}.
 *
 * <p>Propaga lo que propaga {@code calendar}: {@code NoSuchElementException} si la persona no
 * existe y {@code NotAuthorizedException} si esta suspendida.
 */
public interface ConsultarEventosDelAprendizPort {

    /**
     * @param dias cuantos dias locales mirar desde hoy (1 = solo hoy), entre 1 y 90
     */
    AgendaDeEventos proximosDias(UserId aprendizId, int dias);

    /** {@code desde}/{@code hasta}: dias locales de la persona, ambos inclusive. */
    record AgendaDeEventos(LocalDate desde, LocalDate hasta, List<EventoDeLaAgenda> eventos) {
    }

    /**
     * @param terminaLocal {@code null} si el evento no declara duracion
     * @param asistencia   {@code ASISTE}, {@code NO_ASISTE}, {@code QUIZAS}, o {@code null} si no respondio
     */
    record EventoDeLaAgenda(String titulo, LocalDateTime iniciaLocal, LocalDateTime terminaLocal,
                            String asistencia) {
    }
}
