package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.evento.EventoVista;
import com.renaser.os.calendar.application.ports.in.evento.ObtenerEventoUseCase;
import com.renaser.os.calendar.application.ports.in.meet.EventoConMeetVista;
import com.renaser.os.calendar.application.ports.in.meet.ReintentarSalaDeMeetUseCase;
import com.renaser.os.calendar.application.ports.in.meet.ResumenDeMeet;
import com.renaser.os.calendar.application.ports.out.evento.LoadEventoPort;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.meet.AccionDeAuditoriaGoogle;
import com.renaser.os.calendar.domain.model.meet.SalaMeet;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * «Reintentar» a mano (spec 040, FR-008): vuelve a pedirle a Google lo que quedó sin hacer en un evento, sin esperar el turno del reintento
 * automático. No duplica nada aunque Google ya hubiera creado la sala sin que Renaser lo supiera (FR-007, SC-011): el id del evento de Google
 * sale del de Renaser, y pedirlo dos veces da 409, de donde se toma la sala que ya existe.
 */
@Service
class ReintentoDeSalaDeMeetService implements ReintentarSalaDeMeetUseCase {

    static final String EVENTO_SIN_MEET = "Este evento no pide el enlace de Meet automático.";

    private final AccesoEventoService accesoEventoService;
    private final DisponibilidadDeLasSalas disponibilidad;
    private final SincronizadorDeSalas sincronizador;
    private final LoadEventoPort loadEventoPort;
    private final ObtenerEventoUseCase obtenerUseCase;
    private final AuditoriaDeGoogle auditoria;
    private final Clock clock;

    ReintentoDeSalaDeMeetService(AccesoEventoService accesoEventoService, DisponibilidadDeLasSalas disponibilidad,
                                 SincronizadorDeSalas sincronizador, LoadEventoPort loadEventoPort,
                                 ObtenerEventoUseCase obtenerUseCase, AuditoriaDeGoogle auditoria, Clock clock) {
        this.accesoEventoService = accesoEventoService;
        this.disponibilidad = disponibilidad;
        this.sincronizador = sincronizador;
        this.loadEventoPort = loadEventoPort;
        this.obtenerUseCase = obtenerUseCase;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Override
    public EventoConMeetVista reintentar(UserId actor, EventoId eventoId) {
        Evento evento = loadEventoPort.byId(eventoId)
                .orElseThrow(() -> new NoSuchElementException("Evento no encontrado: " + eventoId));
        // Spec 044 (FR-006): reintenta quien gestiona el evento, también el mentor que lo creó.
        accesoEventoService.requireGestiona(actor, evento);
        if (!evento.usaSalaDeMeetAutomatica()) {
            throw new IllegalStateException(EVENTO_SIN_MEET);
        }
        disponibilidad.exigir(actor);

        String correlacion = auditoria.nuevaCorrelacion();
        List<SalaMeet> salas = sincronizador.reconciliar(eventoId, actor, correlacion, true,
                SincronizadorDeSalas.PRESUPUESTO_DE_UNA_PETICION);
        EventoVista vista = obtenerUseCase.obtener(actor, eventoId);
        ResumenDeMeet resumen = ResumenesDeMeet.de(vista.evento(), salas, clock.now());
        auditoria.registrar(AccionDeAuditoriaGoogle.REINTENTAR, actor, eventoId.value(),
                resumen != null && resumen.fechasConProblema() == 0, null, correlacion);
        return new EventoConMeetVista(vista, resumen, ResumenesDeMeet.seGrabaOTranscribe(salas));
    }
}
