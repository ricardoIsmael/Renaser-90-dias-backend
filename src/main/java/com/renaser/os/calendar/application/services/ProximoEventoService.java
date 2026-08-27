package com.renaser.os.calendar.application.services;

import com.renaser.os.points.api.ProximoEventoFinder;
import com.renaser.os.calendar.application.ports.in.evento.ListarEventosParaVisorUseCase;
import com.renaser.os.calendar.application.ports.out.evento.LoadEventoPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadExcepcionPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort.ProgresoParticipanteCalendar;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.Excepcion;
import com.renaser.os.calendar.domain.model.evento.ExpansorOcurrencias;
import com.renaser.os.calendar.domain.model.evento.Ocurrencia;
import com.renaser.os.calendar.domain.model.evento.ResolverAudiencia.VisorContexto;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementa {@link ProximoEventoFinder} reutilizando la misma resolucion de acceso que
 * {@link EventoService#listar} ({@link AccesoEventoService}) — misma ventana maxima
 * ({@link ListarEventosParaVisorUseCase#RANGO_MAXIMO_DIAS} dias) pero acotada a la primera
 * ocurrencia futura, sin materializar toda la agenda del rango.
 */
@Service
class ProximoEventoService implements ProximoEventoFinder {

    private final LoadEventoPort loadEventoPort;
    private final LoadExcepcionPort loadExcepcionPort;
    private final AccesoEventoService accesoEventoService;
    private final Clock clock;

    ProximoEventoService(LoadEventoPort loadEventoPort, LoadExcepcionPort loadExcepcionPort,
                          AccesoEventoService accesoEventoService, Clock clock) {
        this.loadEventoPort = loadEventoPort;
        this.loadExcepcionPort = loadExcepcionPort;
        this.accesoEventoService = accesoEventoService;
        this.clock = clock;
    }

    @Override
    public Optional<ProximoEvento> proximoEventoDe(UserId participanteId) {
        ProgresoParticipanteCalendar progreso = accesoEventoService.requireProgreso(participanteId);
        VisorContexto visor = accesoEventoService.buildVisor(progreso);

        Instant ahora = clock.now();
        Instant hasta = ahora.plus(ListarEventosParaVisorUseCase.RANGO_MAXIMO_DIAS, ChronoUnit.DAYS);

        List<Evento> visibles = loadEventoPort.candidatosParaVisor(ahora, hasta).stream()
                .filter(e -> accesoEventoService.puedeAcceder(participanteId, progreso, visor, e))
                .toList();
        if (visibles.isEmpty()) {
            return Optional.empty();
        }

        Set<EventoId> visibleIds = visibles.stream().map(Evento::id).collect(Collectors.toSet());
        Map<EventoId, List<Excepcion>> excepcionesPorEvento = loadExcepcionPort.porEventos(visibleIds);

        return visibles.stream()
                .flatMap(evento -> proximaOcurrenciaDe(evento, ahora, hasta, excepcionesPorEvento).stream())
                .min((a, b) -> a.iniciaEn().compareTo(b.iniciaEn()));
    }

    /** Como {@link Evento} es dueno de multiples ocurrencias, se busca solo la mas cercana a
     * "ahora" DENTRO de este evento — el minimo global se resuelve luego, entre eventos. */
    private Optional<ProximoEvento> proximaOcurrenciaDe(Evento evento, Instant ahora, Instant hasta,
                                                          Map<EventoId, List<Excepcion>> excepcionesPorEvento) {
        List<Excepcion> excepciones = excepcionesPorEvento.getOrDefault(evento.id(), List.of());
        List<Ocurrencia> ocurrencias = ExpansorOcurrencias.expandir(evento.iniciaEn(), evento.duracionMinutos(),
                evento.timezone(), evento.recurrencia(), ahora, hasta, excepciones);
        return ocurrencias.stream()
                .filter(occ -> !occ.iniciaEn().isBefore(ahora))
                .min((a, b) -> a.iniciaEn().compareTo(b.iniciaEn()))
                .map(occ -> new ProximoEvento(evento.id().value(),
                        occ.titulo() != null ? occ.titulo() : evento.titulo(), occ.iniciaEn()));
    }
}
