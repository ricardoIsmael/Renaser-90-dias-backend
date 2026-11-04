package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.evento.ActualizarEventoUseCase;
import com.renaser.os.calendar.application.ports.in.evento.ActualizarEventoUseCase.ActualizarEventoCommand;
import com.renaser.os.calendar.application.ports.in.evento.CancelarOcurrenciaUseCase;
import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase;
import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase.CrearEventoCommand;
import com.renaser.os.calendar.application.ports.in.evento.EliminarEventoUseCase;
import com.renaser.os.calendar.application.ports.in.evento.EventoVista;
import com.renaser.os.calendar.application.ports.in.evento.ListarEventosParaVisorUseCase;
import com.renaser.os.calendar.application.ports.in.evento.ListarEventosParaVisorUseCase.EnlaceDeLaFecha;
import com.renaser.os.calendar.application.ports.in.evento.ListarEventosParaVisorUseCase.OcurrenciaVista;
import com.renaser.os.calendar.application.ports.in.evento.ObtenerEventoUseCase;
import com.renaser.os.calendar.application.ports.in.meet.EventoConMeetVista;
import com.renaser.os.calendar.application.ports.in.meet.EventosConMeetUseCase;
import com.renaser.os.calendar.application.ports.in.meet.ResumenDeMeet;
import com.renaser.os.calendar.application.ports.out.evento.LoadEventoPort;
import com.renaser.os.calendar.application.ports.out.meet.LoadSalaMeetPort;
import com.renaser.os.calendar.application.ports.out.meet.SaveSalaMeetPort;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.calendar.domain.model.meet.EstadoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.OcurrenciasDeseadas;
import com.renaser.os.calendar.domain.model.meet.SalaMeet;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Los eventos del calendario con el enlace de Meet automático (spec 040). Envuelve los casos de uso de siempre —que guardan en su propia
 * transacción— y, <b>una vez guardado</b>, le pide a Google las salas: nunca con una transacción abierta (FR-001) y nunca con un fallo de Google que
 * rompa el guardado (SC-003). Un evento que no pide Meet pasa tal cual por los casos de uso de siempre: este servicio no le cambia nada (SC-007).
 */
@Service
class EventoConSalaDeMeetService implements EventosConMeetUseCase {

    static final String LINK_Y_ENLACE_AUTOMATICO = "Elige: crear el enlace de Meet o pegar uno.";
    static final String ENLACE_AUTOMATICO_ES_DE_MEET = "Con el enlace de Meet automático, la ubicación tiene que ser Meet.";

    private final ListarEventosParaVisorUseCase listarUseCase;
    private final ObtenerEventoUseCase obtenerUseCase;
    private final CrearEventoUseCase crearUseCase;
    private final ActualizarEventoUseCase actualizarUseCase;
    private final EliminarEventoUseCase eliminarUseCase;
    private final CancelarOcurrenciaUseCase cancelarUseCase;
    private final LoadEventoPort loadEventoPort;
    private final LoadSalaMeetPort loadSalaPort;
    private final SaveSalaMeetPort saveSalaPort;
    private final AccesoEventoService accesoEventoService;
    private final DisponibilidadDeLasSalas disponibilidad;
    private final SincronizadorDeSalas sincronizador;
    private final AuditoriaDeGoogle auditoria;
    private final Clock clock;

    EventoConSalaDeMeetService(ListarEventosParaVisorUseCase listarUseCase, ObtenerEventoUseCase obtenerUseCase,
                               CrearEventoUseCase crearUseCase, ActualizarEventoUseCase actualizarUseCase,
                               EliminarEventoUseCase eliminarUseCase, CancelarOcurrenciaUseCase cancelarUseCase,
                               LoadEventoPort loadEventoPort, LoadSalaMeetPort loadSalaPort, SaveSalaMeetPort saveSalaPort,
                               AccesoEventoService accesoEventoService, DisponibilidadDeLasSalas disponibilidad,
                               SincronizadorDeSalas sincronizador, AuditoriaDeGoogle auditoria, Clock clock) {
        this.listarUseCase = listarUseCase;
        this.obtenerUseCase = obtenerUseCase;
        this.crearUseCase = crearUseCase;
        this.actualizarUseCase = actualizarUseCase;
        this.eliminarUseCase = eliminarUseCase;
        this.cancelarUseCase = cancelarUseCase;
        this.loadEventoPort = loadEventoPort;
        this.loadSalaPort = loadSalaPort;
        this.saveSalaPort = saveSalaPort;
        this.accesoEventoService = accesoEventoService;
        this.disponibilidad = disponibilidad;
        this.sincronizador = sincronizador;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Override
    public List<OcurrenciaVista> listar(UserId actor, Instant desde, Instant hasta, Formacion formacion) {
        List<OcurrenciaVista> ocurrencias = listarUseCase.listar(actor, desde, hasta, formacion);
        Set<EventoId> conSalaPorFecha = ocurrencias.stream().map(OcurrenciaVista::evento)
                .filter(e -> e.usaSalaDeMeetAutomatica() && e.esRecurrente()).map(Evento::id).collect(Collectors.toSet());
        if (conSalaPorFecha.isEmpty()) {
            return ocurrencias;
        }
        Map<EventoId, List<SalaMeet>> salas = loadSalaPort.porEventos(conSalaPorFecha);
        return ocurrencias.stream().map(o -> conSuEnlace(o, salas)).toList();
    }

    /** Cada fecha de un evento que se repite abre SU enlace, nunca el de otra (FR-017). */
    private static OcurrenciaVista conSuEnlace(OcurrenciaVista ocurrencia, Map<EventoId, List<SalaMeet>> salas) {
        Evento evento = ocurrencia.evento();
        if (!evento.usaSalaDeMeetAutomatica() || !evento.esRecurrente()) {
            return ocurrencia;
        }
        var fecha = OcurrenciasDeseadas.fechaDe(ocurrencia.inicioOcurrencia(), evento.timezone());
        String enlace = salas.getOrDefault(evento.id(), List.of()).stream()
                .filter(s -> s.fechaOcurrencia().equals(fecha) && s.estado() != EstadoSalaMeet.BORRADO)
                .map(SalaMeet::enlace).filter(e -> e != null).findFirst().orElse(null);
        return ocurrencia.conEnlace(new EnlaceDeLaFecha(enlace));
    }

    @Override
    public EventoConMeetVista obtener(UserId actor, EventoId eventoId) {
        EventoVista vista = obtenerUseCase.obtener(actor, eventoId);
        if (!vista.evento().usaSalaDeMeetAutomatica() || !accesoEventoService.administraElCalendario(actor)) {
            return new EventoConMeetVista(vista, null);
        }
        return new EventoConMeetVista(vista, ResumenesDeMeet.de(vista.evento(), loadSalaPort.porEvento(eventoId), clock.now()));
    }

    @Override
    public EventoConMeetVista crear(CrearEventoCommand comando) {
        accesoEventoService.requireAdministraElCalendario(comando.actorId());
        boolean pide = comando.accesoSalaMeet() != null;
        if (pide) {
            exigirUnaSolicitudCoherente(comando.tipoUbicacion(), comando.valorUbicacion(), null);
            disponibilidad.exigir(comando.actorId());
        }
        EventoVista creado = crearUseCase.crear(comando);
        return pide ? sincronizado(creado, comando.actorId()) : new EventoConMeetVista(creado, null);
    }

    @Override
    public EventoConMeetVista actualizar(ActualizarEventoCommand comando) {
        accesoEventoService.requireAdministraElCalendario(comando.actorId());
        Evento previo = loadEventoPort.byId(comando.eventoId())
                .orElseThrow(() -> new NoSuchElementException("Evento no encontrado: " + comando.eventoId()));
        boolean pedia = previo.usaSalaDeMeetAutomatica();
        boolean pide = comando.accesoSalaMeet() != null;
        if (pide) {
            exigirUnaSolicitudCoherente(comando.tipoUbicacion(), comando.valorUbicacion(), previo.valorUbicacion());
            if (!pedia) {
                disponibilidad.exigir(comando.actorId());
            }
        }
        if (pedia || pide) {
            // Antes de cambiar nada: si Google falla, el cambio no se pierde, queda pendiente y el proceso de fondo lo termina (FR-008).
            saveSalaPort.marcarParaSincronizar(comando.eventoId(), clock.now());
        }
        EventoVista actualizado = actualizarUseCase.actualizar(pide ? conElEnlaceQueYaTenia(comando, previo) : comando);
        return pedia || pide ? sincronizado(actualizado, comando.actorId()) : new EventoConMeetVista(actualizado, null);
    }

    @Override
    public void eliminar(UserId actor, EventoId eventoId) {
        accesoEventoService.requireAdministraElCalendario(actor);
        saveSalaPort.marcarParaSincronizar(eventoId, clock.now());
        eliminarUseCase.eliminar(actor, eventoId);
        sincronizador.reconciliar(eventoId, actor, auditoria.nuevaCorrelacion(), false, SincronizadorDeSalas.PRESUPUESTO_DE_UNA_PETICION);
    }

    @Override
    public void cancelarOcurrencia(UserId actor, EventoId eventoId, Instant inicioOcurrencia) {
        accesoEventoService.requireAdministraElCalendario(actor);
        cancelarUseCase.cancelar(actor, eventoId, inicioOcurrencia);
        sincronizador.reconciliar(eventoId, actor, auditoria.nuevaCorrelacion(), false, SincronizadorDeSalas.PRESUPUESTO_DE_UNA_PETICION);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Pedir el enlace automático exige que sea un evento de Meet y que no traiga un link pegado a mano (FR-004, 400). Un link igual al que el evento
     * ya tiene no cuenta como pegado: la app reenvía el evento completo al editar.
     */
    private static void exigirUnaSolicitudCoherente(TipoUbicacion tipo, String valorPegado, String enlaceQueYaTenia) {
        if (tipo != TipoUbicacion.MEET) {
            throw new IllegalArgumentException(ENLACE_AUTOMATICO_ES_DE_MEET);
        }
        boolean pegoUnLink = valorPegado != null && !valorPegado.isBlank() && !valorPegado.trim().equals(enlaceQueYaTenia);
        if (pegoUnLink) {
            throw new IllegalArgumentException(LINK_Y_ENLACE_AUTOMATICO);
        }
    }

    private static ActualizarEventoCommand conElEnlaceQueYaTenia(ActualizarEventoCommand c, Evento previo) {
        return new ActualizarEventoCommand(c.actorId(), c.eventoId(), c.titulo(), c.descripcion(), c.iniciaEn(),
                c.duracionMinutos(), c.timezone(), c.tipoUbicacion(), previo.valorUbicacion(), c.tipoAudiencia(),
                c.nivelMinimoId(), c.cursoId(), c.celulaDestinoId(), c.notificarAlCrear(), c.recordarPorEmail(),
                c.recordatoriosPersonalizados(), c.recurrencia(), c.rolesDestino(), c.reglasRecordatorio(), c.accesoSalaMeet());
    }

    /** Ya guardado el evento: pide las salas (sin transacción) y devuelve el evento con su enlace y cómo quedó cada sala. */
    private EventoConMeetVista sincronizado(EventoVista guardado, UserId actor) {
        EventoId id = guardado.evento().id();
        List<SalaMeet> salas = sincronizador.reconciliar(id, actor, auditoria.nuevaCorrelacion(), false,
                SincronizadorDeSalas.PRESUPUESTO_DE_UNA_PETICION);
        Optional<Evento> reciente = loadEventoPort.byId(id);
        Evento evento = reciente.orElse(guardado.evento());
        ResumenDeMeet resumen = ResumenesDeMeet.de(evento, salas, clock.now());
        return new EventoConMeetVista(new EventoVista(evento, guardado.coverUrl()), resumen);
    }
}
