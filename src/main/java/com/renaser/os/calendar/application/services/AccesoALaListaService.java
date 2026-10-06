package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.out.evento.LoadEventoPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadExcepcionPort;
import com.renaser.os.calendar.domain.model.asistencia.QuienLlevaLaLista;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.ExpansorOcurrencias;
import com.renaser.os.calendar.domain.model.evento.Ocurrencia;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;

/**
 * La puerta común de «Quién respondió» y «Pasar lista» (D-256): resuelve al actor (suspendido → 403), el
 * evento (404), la regla de quién puede (403) y la ocurrencia pedida (400 si no es una ocurrencia real, o si
 * se canceló). Devuelve la ocurrencia EFECTIVA (con su reprogramación, si la tuvo), que es la que fija la
 * ventana de pasar lista.
 */
@Component
class AccesoALaListaService {

    /** Mismo margen que isRealOccurrence() del repo viejo (EventoService/ConfirmacionService). */
    private static final long TOLERANCIA_OCURRENCIA_MS = 180_000;
    /** Cuánto alrededor del slot se busca: alcanza para una ocurrencia reprogramada a otro día de la semana. */
    private static final Duration BUSQUEDA = Duration.ofDays(8);
    static final String SIN_PERMISO =
            "Solo quien creó el evento, el Admin, el Alquimista o el Líder de mentores ven la asistencia";

    private final AccesoEventoService accesoEventoService;
    private final LoadEventoPort loadEventoPort;
    private final LoadExcepcionPort loadExcepcionPort;

    AccesoALaListaService(AccesoEventoService accesoEventoService, LoadEventoPort loadEventoPort,
                          LoadExcepcionPort loadExcepcionPort) {
        this.accesoEventoService = accesoEventoService;
        this.loadEventoPort = loadEventoPort;
        this.loadExcepcionPort = loadExcepcionPort;
    }

    OcurrenciaDeLaLista autorizar(UserId actor, EventoId eventoId, Instant inicioOcurrencia) {
        var progreso = accesoEventoService.requireProgreso(actor);
        Evento evento = loadEventoPort.byId(eventoId)
                .orElseThrow(() -> new NoSuchElementException("Evento no encontrado: " + eventoId));
        if (!QuienLlevaLaLista.puede(progreso.rol(), actor, evento.creadoPor())) {
            throw new NotAuthorizedException(SIN_PERMISO);
        }
        return new OcurrenciaDeLaLista(evento, ocurrenciaReal(evento, inicioOcurrencia));
    }

    private Ocurrencia ocurrenciaReal(Evento evento, Instant inicioOcurrencia) {
        return ExpansorOcurrencias.expandir(evento.iniciaEn(), evento.duracionMinutos(), evento.timezone(),
                        evento.recurrencia(), inicioOcurrencia.minus(BUSQUEDA), inicioOcurrencia.plus(BUSQUEDA),
                        loadExcepcionPort.porEvento(evento.id()))
                .stream()
                .filter(o -> Math.abs(o.inicioOcurrencia().toEpochMilli() - inicioOcurrencia.toEpochMilli())
                        <= TOLERANCIA_OCURRENCIA_MS)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "occurrenceStart no corresponde a una fecha de este evento (o esa fecha se canceló)"));
    }

    record OcurrenciaDeLaLista(Evento evento, Ocurrencia ocurrencia) {

        /** El slot de la serie tal como lo guarda la base, aunque el cliente lo haya mandado con segundos de más. */
        Instant slot() {
            return ocurrencia.inicioOcurrencia();
        }
    }
}
