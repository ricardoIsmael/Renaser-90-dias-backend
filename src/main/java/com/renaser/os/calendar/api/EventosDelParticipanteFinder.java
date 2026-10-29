package com.renaser.os.calendar.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Contrato publico de {@code calendar} para LEER la agenda de eventos de una persona desde otro
 * modulo (2026-09-23, herramienta {@code consultar_eventos} del acompanante, {@code rag}).
 *
 * <p><b>Delega, no reimplementa.</b> La implementacion llama al MISMO caso de uso que sirve
 * {@code GET /api/v1/calendar/events} ({@code ListarEventosParaVisorUseCase}): audiencia,
 * elegibilidad, expansion de recurrencias, excepciones y RSVP se resuelven ahi. Este contrato solo
 * agrega lo que el endpoint deja al cliente: fijar el rango en DIAS LOCALES de la persona y
 * pasar los instantes a su hora local.
 *
 * <p>Propaga lo mismo que el caso de uso: {@code NoSuchElementException} si la persona no existe y
 * {@code NotAuthorizedException} si esta suspendida.
 *
 * <p>Lo que cruza la frontera es una proyeccion con tipos de Java, nunca {@code Evento}: mismo
 * criterio que {@link RecordatorioEventoDebidoEvent}.
 */
public interface EventosDelParticipanteFinder {

    /** Tope del rango, el mismo que el endpoint ({@code ListarEventosParaVisorUseCase.RANGO_MAXIMO_DIAS}). */
    int MAXIMO_DIAS = 90;

    /**
     * Las ocurrencias visibles desde el inicio de HOY (medianoche local de la persona) hasta el
     * final del dia {@code hoy + dias - 1}, ordenadas por inicio.
     *
     * @param dias entre 1 y {@link #MAXIMO_DIAS}; 1 = solo hoy
     * @throws IllegalArgumentException si {@code dias} esta fuera de ese rango
     */
    AgendaDeEventos proximosDias(UserId participanteId, int dias);

    /**
     * Las ocurrencias visibles entre dos DIAS LOCALES de la persona, pasados o por venir (spec 015: los huecos de
     * horario miran la semana entera, incluidos los dias ya vividos). Desde la medianoche local de {@code desde} hasta
     * el final de {@code hasta}, ordenadas por inicio. Vienen las ocurrencias que EMPIEZAN dentro del rango, como en
     * {@link #proximosDias}: una que empezo el dia anterior y cruza la medianoche no viene, asi que quien necesite
     * ese pedazo pide un dia antes.
     *
     * @param hasta inclusivo; el rango no pasa de {@link #MAXIMO_DIAS} dias
     * @throws IllegalArgumentException si {@code hasta} es anterior a {@code desde} o el rango pasa del tope
     */
    AgendaDeEventos entre(UserId participanteId, LocalDate desde, LocalDate hasta);

    /**
     * @param zona  la zona de la persona, con la que se calcularon las horas locales
     * @param desde primer dia del rango (hoy, en su zona)
     * @param hasta ultimo dia del rango, inclusive
     */
    record AgendaDeEventos(ZoneId zona, LocalDate desde, LocalDate hasta, List<EventoDeLaAgenda> eventos) {
    }

    /**
     * @param terminaLocal {@code null} si el evento no declara duracion
     * @param asistencia   la confirmacion de la persona para ESTA ocurrencia ({@code ASISTE},
     *                     {@code NO_ASISTE}, {@code QUIZAS}), o {@code null} si no respondio
     */
    record EventoDeLaAgenda(UUID eventoId, String titulo, LocalDateTime iniciaLocal, LocalDateTime terminaLocal,
                            String asistencia) {
    }
}
