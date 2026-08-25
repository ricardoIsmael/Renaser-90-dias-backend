package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.api.RecordatorioEventoDebidoEvent;
import com.renaser.os.calendar.application.ports.in.recordatorio.DespacharRecordatoriosUseCase;
import com.renaser.os.calendar.application.ports.in.recordatorio.GenerarRecordatoriosUseCase;
import com.renaser.os.calendar.application.ports.out.celula.ConsultarMiembrosCelulaPort;
import com.renaser.os.calendar.application.ports.out.confirmacion.LoadConfirmacionPort;
import com.renaser.os.calendar.application.ports.out.curso.ResolverAudienciaCursoPort;
import com.renaser.os.calendar.application.ports.out.elegibilidad.ConsultarElegibilidadEventoPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadEventoPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadExcepcionPort;
import com.renaser.os.calendar.application.ports.out.nivelmembresia.LoadNivelMembresiaPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort;
import com.renaser.os.calendar.application.ports.out.participante.ResolverAudienciaMasivaPort;
import com.renaser.os.calendar.application.ports.out.recordatorio.LoadRecordatorioPort;
import com.renaser.os.calendar.application.ports.out.recordatorio.SaveRecordatorioPort;
import com.renaser.os.calendar.domain.model.evento.EstadoEvento;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.Excepcion;
import com.renaser.os.calendar.domain.model.evento.ExpansorOcurrencias;
import com.renaser.os.calendar.domain.model.evento.Ocurrencia;
import com.renaser.os.calendar.domain.model.evento.ReglaRecordatorio;
import com.renaser.os.calendar.domain.model.evento.ReglasPorTipoEvento;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.nivelmembresia.NivelMembresia;
import com.renaser.os.calendar.domain.model.nivelmembresia.ProgresoNivel;
import com.renaser.os.calendar.domain.model.recordatorio.CalculadoraRecordatorios;
import com.renaser.os.calendar.domain.model.recordatorio.InstanteRecordatorio;
import com.renaser.os.calendar.domain.model.recordatorio.RecordatorioEvento;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Puerto directo de las dos operaciones de {@code reminderService.ts} (repo viejo):
 * {@code generar()} deja en la cola los avisos que faltan; {@code despachar()} toma los
 * vencidos y PUBLICA {@link RecordatorioEventoDebidoEvent} (en vez de mandar push directo
 * — `notifications` decide el canal, fuera de este modulo).
 */
@Service
public class RecordatorioService implements GenerarRecordatoriosUseCase, DespacharRecordatoriosUseCase {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioService.class);

    /** VENTANA_DIAS del repo viejo: suelo de la ventana de generacion. */
    private static final int VENTANA_DIAS_DEFECTO = 3;
    /** ANUNCIO_VALIDEZ_MS del repo viejo: 24h desde creado el evento. */
    private static final long ANUNCIO_VALIDEZ_HORAS = 24;
    private static final int LIMITE_DESPACHO = 500;

    private final LoadEventoPort loadEventoPort;
    private final LoadExcepcionPort loadExcepcionPort;
    private final LoadConfirmacionPort loadConfirmacionPort;
    private final LoadRecordatorioPort loadRecordatorioPort;
    private final SaveRecordatorioPort saveRecordatorioPort;
    private final LoadNivelMembresiaPort nivelPort;
    private final ConsultarProgresoParticipanteCalendarPort progresoPort;
    private final ResolverAudienciaMasivaPort audienciaMasivaPort;
    private final ConsultarMiembrosCelulaPort celulaPort;
    private final ResolverAudienciaCursoPort cursoPort;
    private final ConsultarElegibilidadEventoPort elegibilidadPort;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RecordatorioService(LoadEventoPort loadEventoPort, LoadExcepcionPort loadExcepcionPort,
                                LoadConfirmacionPort loadConfirmacionPort, LoadRecordatorioPort loadRecordatorioPort,
                                SaveRecordatorioPort saveRecordatorioPort, LoadNivelMembresiaPort nivelPort,
                                ConsultarProgresoParticipanteCalendarPort progresoPort,
                                ResolverAudienciaMasivaPort audienciaMasivaPort, ConsultarMiembrosCelulaPort celulaPort,
                                ResolverAudienciaCursoPort cursoPort, ConsultarElegibilidadEventoPort elegibilidadPort,
                                ApplicationEventPublisher events, Clock clock) {
        this.loadEventoPort = loadEventoPort;
        this.loadExcepcionPort = loadExcepcionPort;
        this.loadConfirmacionPort = loadConfirmacionPort;
        this.loadRecordatorioPort = loadRecordatorioPort;
        this.saveRecordatorioPort = saveRecordatorioPort;
        this.nivelPort = nivelPort;
        this.progresoPort = progresoPort;
        this.audienciaMasivaPort = audienciaMasivaPort;
        this.celulaPort = celulaPort;
        this.cursoPort = cursoPort;
        this.elegibilidadPort = elegibilidadPort;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int generar(Instant ahora) {
        Instant hastaMax = ahora.plusSeconds((ReglaRecordatorio.MAX_DIAS_ANTES + 1) * 86_400L);
        Instant desdeAnuncio = ahora.minusSeconds(ANUNCIO_VALIDEZ_HORAS * 3600);
        List<Evento> eventos = loadEventoPort.candidatosParaRecordatorios(ahora, hastaMax, desdeAnuncio);

        int creados = 0;
        for (Evento evento : eventos) {
            creados += generarParaEvento(evento, ahora);
        }
        return creados;
    }

    private int generarParaEvento(Evento evento, Instant ahora) {
        List<ReglaRecordatorio> reglas = evento.reglasRecordatorioEfectivas();
        List<Excepcion> excepciones = loadExcepcionPort.porEvento(evento.id());
        int diasVentana = CalculadoraRecordatorios.diasDeVentana(reglas, VENTANA_DIAS_DEFECTO);
        Instant hasta = ahora.plusSeconds(diasVentana * 86_400L);
        List<Ocurrencia> ocurrencias = reglas.isEmpty() ? List.of()
                : ExpansorOcurrencias.expandir(evento.iniciaEn(), evento.duracionMinutos(), evento.timezone(),
                        evento.recurrencia(), ahora, hasta, excepciones);

        if (ocurrencias.isEmpty() && !evento.notificarAlCrear()) {
            return 0;
        }
        List<UserId> usuarios = resolveRecipients(evento);
        if (usuarios.isEmpty()) {
            return 0;
        }

        int creados = anunciar(evento, usuarios, ahora);
        if (ocurrencias.isEmpty()) {
            return creados;
        }

        Set<String> confirmados = loadConfirmacionPort.confirmadosAsistencia(evento.id(),
                ocurrencias.stream().map(Ocurrencia::inicioOcurrencia).toList());

        for (Ocurrencia occ : ocurrencias) {
            List<InstanteRecordatorio> instantes = CalculadoraRecordatorios.instantesPara(occ.inicioOcurrencia(),
                    reglas, evento.timezone(), ahora);
            if (instantes.isEmpty()) {
                continue;
            }
            String claveOcurrencia = occ.inicioOcurrencia().toString();
            List<UserId> pendientes = usuarios.stream()
                    .filter(u -> !confirmados.contains(claveOcurrencia + "|" + u))
                    .toList();
            if (pendientes.isEmpty()) {
                continue;
            }
            List<RecordatorioEvento> filas = new ArrayList<>();
            for (UserId u : pendientes) {
                for (InstanteRecordatorio instante : instantes) {
                    filas.add(RecordatorioEvento.programar(evento.id(), instante.inicioOcurrencia(), u,
                            instante.enviarEn(), clock));
                }
            }
            creados += saveRecordatorioPort.encolarSiFalta(filas);
        }
        return creados;
    }

    /** anunciar() del repo viejo: clave FIJA (enviarEn = inicioOcurrencia = creadoEn del
     * evento) — dos pasadas del cron no duplican el anuncio. */
    private int anunciar(Evento evento, List<UserId> usuarios, Instant ahora) {
        if (!evento.notificarAlCrear()) {
            return 0;
        }
        if (ahora.toEpochMilli() - evento.creadoEn().toEpochMilli() > ANUNCIO_VALIDEZ_HORAS * 3_600_000L) {
            return 0;
        }
        if (evento.recurrencia() == null && !evento.iniciaEn().isAfter(ahora)) {
            return 0;
        }
        List<RecordatorioEvento> filas = usuarios.stream()
                .map(u -> RecordatorioEvento.programar(evento.id(), evento.creadoEn(), u, evento.creadoEn(), clock))
                .toList();
        return saveRecordatorioPort.encolarSiFalta(filas);
    }

    /** resolveRecipients() del repo viejo: primero audiencia (barata, en lote), despues
     * elegibilidad (cara, por persona — solo si el TIPO de evento la exige). */
    private List<UserId> resolveRecipients(Evento evento) {
        List<UserId> candidatos = resolveAudience(evento);
        if (candidatos.isEmpty() || !ReglasPorTipoEvento.requiereElegibilidad(evento.tipoEvento())) {
            return candidatos;
        }
        List<UserId> elegibles = new ArrayList<>();
        for (UserId candidato : candidatos) {
            var progreso = progresoPort.deParticipante(candidato);
            if (progreso.isEmpty()) {
                continue;
            }
            // rol_privilegiado del repo viejo: ADMIN/ALCHEMIST/MENTOR siempre elegibles.
            if (progreso.get().rol() != RolUsuario.TRAINEE
                    || elegibilidadPort.esElegible(candidato, evento.tipoEvento())) {
                elegibles.add(candidato);
            }
        }
        return elegibles;
    }

    private List<UserId> resolveAudience(Evento evento) {
        return switch (evento.tipoAudiencia()) {
            case TODOS -> audienciaMasivaPort.traineesActivos();
            case ROLES -> audienciaMasivaPort.activosConRoles(evento.rolesDestino());
            case CELULA -> evento.celulaDestinoId() == null ? List.of() : celulaPort.miembrosActivos(evento.celulaDestinoId());
            case NIVEL_MINIMO -> resolveAudienceNivelMinimo(evento);
            case CURSO -> resolveAudienceCurso(evento);
        };
    }

    private List<UserId> resolveAudienceNivelMinimo(Evento evento) {
        if (evento.nivelMinimoId() == null) {
            return List.of();
        }
        List<NivelMembresia> niveles = nivelPort.listar();
        Integer minRango = niveles.stream().filter(n -> n.id() == evento.nivelMinimoId())
                .findFirst().map(NivelMembresia::rango).orElse(null);
        if (minRango == null) {
            return List.of();
        }
        return audienciaMasivaPort.traineesActivosConDiaPrograma().stream()
                .filter(c -> c.diaPrograma() != null
                        && ProgresoNivel.resolverRango(ProgresoNivel.porcentajeDeProgreso(c.diaPrograma()), niveles) >= minRango)
                .map(ResolverAudienciaMasivaPort.ParticipanteConDia::id)
                .toList();
    }

    private List<UserId> resolveAudienceCurso(Evento evento) {
        if (evento.cursoId() == null) {
            return List.of();
        }
        Set<UserId> candidatos = audienciaMasivaPort.traineesActivosConDiaPrograma().stream()
                .map(ResolverAudienciaMasivaPort.ParticipanteConDia::id)
                .collect(Collectors.toSet());
        return List.copyOf(cursoPort.filtrarConAcceso(evento.cursoId(), candidatos));
    }

    @Override
    @Transactional
    public int despachar(Instant ahora) {
        List<RecordatorioEvento> pendientes = loadRecordatorioPort.vencidosPendientes(ahora, LIMITE_DESPACHO);
        if (pendientes.isEmpty()) {
            return 0;
        }

        List<Long> despachadosIds = new ArrayList<>();
        for (RecordatorioEvento recordatorio : pendientes) {
            var eventoOpt = loadEventoPort.byId(recordatorio.eventoId());
            if (eventoOpt.isEmpty()) {
                // FK ON DELETE CASCADE deberia impedir esto; guard defensivo.
                continue;
            }
            Evento evento = eventoOpt.get();
            if (evento.estado() == EstadoEvento.CANCELADO) {
                saveRecordatorioPort.cancelarPorIds(List.of(recordatorio.id()), RecordatorioEvento.MOTIVO_EVENTO_CANCELADO);
                continue;
            }

            boolean esAnuncio = recordatorio.esAnuncio(evento.creadoEn());
            events.publishEvent(new RecordatorioEventoDebidoEvent(recordatorio.id(), evento.id().value(),
                    recordatorio.usuarioId(), recordatorio.inicioOcurrencia(), evento.titulo(), esAnuncio, ahora));
            despachadosIds.add(recordatorio.id());
        }

        if (!despachadosIds.isEmpty()) {
            saveRecordatorioPort.marcarEnviados(despachadosIds, ahora);
        }
        log.debug("[RecordatorioService.despachar] {} recordatorio(s) despachado(s) de {} pendiente(s)",
                despachadosIds.size(), pendientes.size());
        return despachadosIds.size();
    }
}
