package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.api.EventosDelParticipanteFinder;
import com.renaser.os.calendar.application.ports.in.evento.ListarEventosParaVisorUseCase;
import com.renaser.os.calendar.application.ports.in.evento.ListarEventosParaVisorUseCase.OcurrenciaVista;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort.ProgresoParticipanteCalendar;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Implementa {@link EventosDelParticipanteFinder} (2026-09-23) delegando en
 * {@link ListarEventosParaVisorUseCase}, el mismo caso de uso de {@code GET /calendar/events}. Aca
 * no se filtra ni se expande nada: quien puede ver que evento lo sigue decidiendo ese caso de uso.
 *
 * <p>Lo unico propio es el rango, y se calcula como manda la regla 02: "hoy" es
 * {@code clock.now()} en la zona de la persona, y el rango va de su medianoche local a la
 * medianoche local del dia siguiente al ultimo. Nunca la fecha del servidor, que entre las 19:00 y
 * la medianoche de Lima ya es manana.
 */
@Service
class EventosDelParticipanteService implements EventosDelParticipanteFinder {

    private final ListarEventosParaVisorUseCase listarEventosUseCase;
    private final ConsultarProgresoParticipanteCalendarPort progresoPort;
    private final Clock clock;

    EventosDelParticipanteService(ListarEventosParaVisorUseCase listarEventosUseCase,
                                  ConsultarProgresoParticipanteCalendarPort progresoPort, Clock clock) {
        this.listarEventosUseCase = listarEventosUseCase;
        this.progresoPort = progresoPort;
        this.clock = clock;
    }

    @Override
    public AgendaDeEventos proximosDias(UserId participanteId, int dias) {
        if (dias < 1 || dias > MAXIMO_DIAS) {
            throw new IllegalArgumentException("dias debe estar entre 1 y " + MAXIMO_DIAS + ": " + dias);
        }
        ZoneId zona = zonaDe(participanteId);
        LocalDate hoy = clock.now().atZone(zona).toLocalDate();
        return agendaEntre(participanteId, zona, hoy, hoy.plusDays(dias - 1L));
    }

    @Override
    public AgendaDeEventos entre(UserId participanteId, LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde) || ChronoUnit.DAYS.between(desde, hasta) + 1 > MAXIMO_DIAS) {
            throw new IllegalArgumentException("El rango debe ir de 1 a " + MAXIMO_DIAS + " dias: " + desde + " → "
                    + hasta);
        }
        return agendaEntre(participanteId, zonaDe(participanteId), desde, hasta);
    }

    /** Del inicio de {@code primero} al final de {@code ultimo}, en dias locales de la persona. */
    private AgendaDeEventos agendaEntre(UserId participanteId, ZoneId zona, LocalDate primero, LocalDate ultimo) {
        Instant desde = primero.atStartOfDay(zona).toInstant();
        Instant hasta = ultimo.plusDays(1).atStartOfDay(zona).toInstant();
        List<EventoDeLaAgenda> eventos = listarEventosUseCase.listar(participanteId, desde, hasta).stream()
                .map(ocurrencia -> aEventoDeLaAgenda(ocurrencia, zona))
                .toList();
        return new AgendaDeEventos(zona, primero, ultimo, eventos);
    }

    /** El suspendido lo rechaza despues el caso de uso; aca solo hace falta la zona. */
    private ZoneId zonaDe(UserId participanteId) {
        return progresoPort.deParticipante(participanteId)
                .map(ProgresoParticipanteCalendar::zona)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + participanteId));
    }

    private static EventoDeLaAgenda aEventoDeLaAgenda(OcurrenciaVista ocurrencia, ZoneId zona) {
        LocalDateTime inicia = LocalDateTime.ofInstant(ocurrencia.iniciaEn(), zona);
        LocalDateTime termina = ocurrencia.duracionMinutos() == null ? null
                : inicia.atZone(zona).plusMinutes(ocurrencia.duracionMinutos()).toLocalDateTime();
        String asistencia = ocurrencia.viewerRsvpStatus() == null ? null : ocurrencia.viewerRsvpStatus().name();
        return new EventoDeLaAgenda(ocurrencia.evento().id().value(), ocurrencia.titulo(), inicia, termina,
                asistencia);
    }
}
