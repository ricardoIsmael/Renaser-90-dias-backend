package com.renaser.os.rocks.application.services;

import com.renaser.os.evidence.api.DestinoEvidencia;
import com.renaser.os.evidence.api.RegistrarEvidenciaPort;
import com.renaser.os.evidence.api.RegistrarEvidenciaPort.RegistrarEvidenciaComando;
import com.renaser.os.evidence.api.TipoEvidencia;
import com.renaser.os.points.api.AjustarPuntosPort;
import com.renaser.os.points.api.MotivoPuntos;
import com.renaser.os.rocks.api.RocaCompletadaEvent;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CompletarRocaDiariaUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.ConsultarRocasDeHoyUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.ConsultarRocasDeMananaUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CrearPlanDiarioUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.SolicitarUrlAdjuntoRocaUseCase;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.rocks.domain.model.rocadiaria.TipoEvidenciaRoca;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.application.ports.out.rocasemanal.LoadRocaSemanalPort;
import com.renaser.os.rocks.application.ports.out.rocadiaria.SaveRocaDiariaPort;
import com.renaser.os.rocks.domain.model.rocadiaria.ColorPareto;
import com.renaser.os.rocks.domain.model.rocadiaria.EscalaPuntosRoca;
import com.renaser.os.rocks.domain.model.rocadiaria.FasePremio;
import com.renaser.os.rocks.domain.model.rocadiaria.ResultadoPremio;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiariaId;
import com.renaser.os.rocks.domain.model.rocadiaria.VentanaPlanificacionDiaria;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocasemanal.EstadoPlazo;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.rocks.domain.model.rocasemanal.SemanaPrograma;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RocaDiariaService implements CrearPlanDiarioUseCase, CompletarRocaDiariaUseCase,
        SolicitarUrlAdjuntoRocaUseCase, ConsultarRocasDeHoyUseCase, ConsultarRocasDeMananaUseCase {

    /** Bucket propio de evidencia de rocas (D-34), mismo patron que `phasecontracts`/`support`. */
    static final String BUCKET_ROCAS = "renaser-files";
    private static final String PREFIJO_RUTA = "rocas";
    private static final Duration VALIDEZ_URL_SUBIDA = Duration.ofMinutes(10);
    private static final Duration MARGEN_EXIF = Duration.ofMinutes(15);
    private static final int MIN_ROCAS_POR_EJE = 1;
    private static final int MAX_ROCAS_POR_EJE = 3;

    private final LoadRocaMaestraPort loadRocaMaestraPort;
    private final LoadRocaSemanalPort loadRocaSemanalPort;
    private final LoadRocaDiariaPort loadRocaDiariaPort;
    private final SaveRocaDiariaPort saveRocaDiariaPort;
    private final RegistrarEvidenciaPort registrarEvidenciaPort;
    private final ConsultarProgresoParticipanteRocksPort progresoPort;
    private final AlmacenamientoPort almacenamientoPort;
    private final AjustarPuntosPort ajustarPuntosPort;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RocaDiariaService(LoadRocaMaestraPort loadRocaMaestraPort, LoadRocaSemanalPort loadRocaSemanalPort,
                              LoadRocaDiariaPort loadRocaDiariaPort, SaveRocaDiariaPort saveRocaDiariaPort,
                              RegistrarEvidenciaPort registrarEvidenciaPort,
                              ConsultarProgresoParticipanteRocksPort progresoPort, AlmacenamientoPort almacenamientoPort,
                              AjustarPuntosPort ajustarPuntosPort, ApplicationEventPublisher events, Clock clock) {
        this.loadRocaMaestraPort = loadRocaMaestraPort;
        this.loadRocaSemanalPort = loadRocaSemanalPort;
        this.loadRocaDiariaPort = loadRocaDiariaPort;
        this.saveRocaDiariaPort = saveRocaDiariaPort;
        this.registrarEvidenciaPort = registrarEvidenciaPort;
        this.progresoPort = progresoPort;
        this.almacenamientoPort = almacenamientoPort;
        this.ajustarPuntosPort = ajustarPuntosPort;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public List<RocaDiaria> crear(CrearPlanDiarioCommand command) {
        ProgresoParticipanteRocks progreso = requireProgreso(command.actorId());
        Map<EjeObjetivo, RocaMaestra> maestras = requireRocasMaestrasCompletas(command.actorId());
        requirePosicionesContiguasPorEje(command.rocas());

        Instant ahora = clock.now();
        ZoneId zona = progreso.zona();
        EstadoPlazo plazoAlCrear = VentanaPlanificacionDiaria.abierta(ahora, zona) ? EstadoPlazo.EN_PLAZO
                : EstadoPlazo.A_DESTIEMPO;
        LocalDate hoy = ahora.atZone(zona).toLocalDate();
        LocalDate manana = hoy.plusDays(1);
        Set<LocalDate> fechasAdmitidas = plazoAlCrear == EstadoPlazo.EN_PLAZO ? Set.of(manana) : Set.of(hoy, manana);
        if (!fechasAdmitidas.contains(command.fecha())) {
            throw new IllegalArgumentException("INVALID_DATE: la fecha de planificacion debe ser " + fechasAdmitidas);
        }

        if (loadRocaDiariaPort.contarDeParticipanteYFecha(command.actorId(), command.fecha()) > 0) {
            throw new IllegalStateException("ALREADY_PLANNED: ya existen rocas planificadas para " + command.fecha());
        }

        int numeroSemana = SemanaPrograma.numeroSemanaParaFecha(progreso.fechaInicio(), command.fecha());
        List<RocaDiaria> creadas = command.rocas().stream()
                .map(item -> planificarUna(command.actorId(), command.fecha(), item, maestras, numeroSemana))
                .toList();
        return saveRocaDiariaPort.saveAll(creadas);
    }

    @Override
    @Transactional
    public RocaDiaria completar(CompletarRocaDiariaCommand command) {
        ProgresoParticipanteRocks progreso = requireProgreso(command.actorId());
        RocaDiaria roca = requireRocaPropia(command.actorId(), command.rocaDiariaId());
        if (roca.completada()) {
            throw new IllegalStateException("ALREADY_COMPLETED: esta roca ya tiene evidencia");
        }
        requireNoBloqueadaPorPareto(roca);
        Instant ahora = clock.now();
        if (command.tipo() == TipoEvidenciaRoca.FOTO) {
            requireExifDentroDeMargen(command.timestampExif(), ahora);
        }

        registrarEvidenciaPort.registrar(new RegistrarEvidenciaComando(command.actorId(),
                new DestinoEvidencia.RocaDiaria(roca.id().value()), aEvidenceTipo(command.tipo()), command.bucket(),
                command.rutaStorage(), command.contenidoTexto(), command.timestampExif(), command.gpsLat(),
                command.gpsLng(), command.esPrincipal(), ahora));

        roca.completar(ahora, clock);
        RocaDiaria completada = saveRocaDiariaPort.save(roca);
        completada = aplicarPremio(completada, progreso.zona(), ahora);

        events.publishEvent(new RocaCompletadaEvent(completada.id().value(), command.actorId(), ahora));
        return completada;
    }

    @Override
    public UrlAdjuntoRoca solicitarUrl(SolicitarUrlAdjuntoRocaCommand command) {
        requireProgreso(command.actorId());
        RocaDiaria roca = requireRocaPropia(command.actorId(), command.rocaDiariaId());
        if (roca.completada()) {
            throw new IllegalStateException("Esta roca ya tiene evidencia registrada");
        }
        String ruta = PREFIJO_RUTA + "/" + command.actorId() + "/" + roca.id();
        URI url = almacenamientoPort.firmarSubida(ruta, command.tipoContenido(), VALIDEZ_URL_SUBIDA);
        return new UrlAdjuntoRoca(url, BUCKET_ROCAS, ruta);
    }

    @Override
    public List<RocaDiariaVista> hoy(UserId actorId) {
        ProgresoParticipanteRocks progreso = requireProgreso(actorId);
        LocalDate hoy = clock.now().atZone(progreso.zona()).toLocalDate();
        List<RocaDiaria> rocas = loadRocaDiariaPort.deParticipanteYFecha(actorId, hoy);
        return rocas.stream().map(r -> new RocaDiariaVista(r, estaBloqueada(r, rocas))).toList();
    }

    @Override
    public List<RocaDiaria> manana(UserId actorId) {
        ProgresoParticipanteRocks progreso = requireProgreso(actorId);
        LocalDate manana = clock.now().atZone(progreso.zona()).toLocalDate().plusDays(1);
        return loadRocaDiariaPort.deParticipanteYFecha(actorId, manana);
    }

    private RocaDiaria planificarUna(UserId actorId, LocalDate fecha, ItemRocaDiaria item,
                                      Map<EjeObjetivo, RocaMaestra> maestras, int numeroSemana) {
        RocaMaestra maestra = maestras.get(item.eje());
        RocaSemanal rocaSemanal = loadRocaSemanalPort.deMaestraYSemana(maestra.id(), numeroSemana)
                .orElseThrow(() -> new IllegalArgumentException(
                        "NO_WEEKLY_ROCK: no hay plan semanal activo para el eje " + item.eje()));
        return RocaDiaria.planificar(actorId, fecha, item.posicion(), item.titulo(), item.descripcion(),
                item.puntajeImpacto(), item.esDelegable(), item.eje(), rocaSemanal.id(), item.horaInicio(),
                item.horaFin(), clock);
    }

    /**
     * Paga los puntos de la roca recien completada — misma escala EXACTA que
     * hábitos (`EscalaPuntosRoca`, espejo deliberado). Sincrónico, dentro de
     * la MISMA transacción (CLAUDE.MD §9.1): a diferencia del repo viejo (que
     * lo hacia fire-and-forget, "no fatal"), acá si falla el ajuste de puntos
     * la transacción entera hace rollback — mismo criterio de atomicidad que
     * `points` ya aplico para su propio ledger (P-06, docs/MODULO_POINTS.md §2.2).
     */
    private RocaDiaria aplicarPremio(RocaDiaria roca, ZoneId zona, Instant completadaEn) {
        if (!roca.puedeOtorgarPuntos()) {
            return roca;
        }
        Instant horaFin = roca.horaFin() == null ? null : roca.fecha().atTime(roca.horaFin()).atZone(zona).toInstant();
        ResultadoPremio premio = EscalaPuntosRoca.calcular(horaFin, completadaEn);
        if (premio.puntos() <= 0) {
            return roca;
        }
        MotivoPuntos motivo = premio.fase() == FasePremio.EXTENDIDO ? MotivoPuntos.ROCK_EXTENDED
                : MotivoPuntos.ROCK_COMPLETED;
        ajustarPuntosPort.ajustar(roca.participanteId(), motivo, premio.puntos(), "Roca diaria completada: " + roca.id());
        roca.otorgarPuntos(premio.puntos());
        return saveRocaDiariaPort.save(roca);
    }

    private void requireNoBloqueadaPorPareto(RocaDiaria roca) {
        if (roca.color() == ColorPareto.VERDE) {
            return;
        }
        List<RocaDiaria> delDia = loadRocaDiariaPort.deParticipanteYFecha(roca.participanteId(), roca.fecha());
        boolean verdeCompletada = delDia.stream()
                .anyMatch(r -> r.eje() == roca.eje() && r.color() == ColorPareto.VERDE && r.completada());
        if (RocaDiaria.bloqueadaPorPareto(roca.color(), verdeCompletada)) {
            throw new NotAuthorizedException(
                    "GREEN_NOT_EVIDENCED: primero hay que completar la roca VERDE de este eje");
        }
    }

    private static boolean estaBloqueada(RocaDiaria roca, List<RocaDiaria> delDia) {
        if (roca.color() == ColorPareto.VERDE) {
            return false;
        }
        boolean verdeCompletada = delDia.stream()
                .anyMatch(r -> r.eje() == roca.eje() && r.color() == ColorPareto.VERDE && r.completada());
        return RocaDiaria.bloqueadaPorPareto(roca.color(), verdeCompletada);
    }

    /** Traduce el espejo local {@code TipoEvidenciaRoca} al contrato publico de `evidence` (RK-2). */
    private static TipoEvidencia aEvidenceTipo(TipoEvidenciaRoca tipo) {
        return switch (tipo) {
            case FOTO -> TipoEvidencia.FOTO;
            case VIDEO -> TipoEvidencia.VIDEO;
            case AUDIO -> TipoEvidencia.AUDIO;
            case TEXTO -> TipoEvidencia.TEXTO;
            case CAPTURA -> TipoEvidencia.CAPTURA;
        };
    }

    /** Ley VI: el EXIF de una FOTO no puede diferir del instante de subida por mas de 15 min. */
    private static void requireExifDentroDeMargen(Instant timestampExif, Instant ahora) {
        Duration diferencia = Duration.between(timestampExif, ahora).abs();
        if (diferencia.compareTo(MARGEN_EXIF) > 0) {
            throw new IllegalArgumentException(
                    "EXIF_MISMATCH: el timestamp de la foto difiere mas de 15 minutos del instante de subida");
        }
    }

    private void requirePosicionesContiguasPorEje(List<ItemRocaDiaria> rocas) {
        Map<EjeObjetivo, List<Integer>> porEje = rocas.stream()
                .collect(Collectors.groupingBy(ItemRocaDiaria::eje,
                        Collectors.mapping(ItemRocaDiaria::posicion, Collectors.toList())));
        for (Map.Entry<EjeObjetivo, List<Integer>> entrada : porEje.entrySet()) {
            List<Integer> posiciones = entrada.getValue().stream().sorted().toList();
            if (posiciones.size() < MIN_ROCAS_POR_EJE || posiciones.size() > MAX_ROCAS_POR_EJE) {
                throw new IllegalArgumentException("cada eje debe tener entre 1 y 3 rocas: " + entrada.getKey());
            }
            for (int i = 0; i < posiciones.size(); i++) {
                if (posiciones.get(i) != i + 1) {
                    throw new IllegalArgumentException(
                            "las posiciones de " + entrada.getKey() + " deben empezar en 1 sin huecos");
                }
            }
        }
    }

    private Map<EjeObjetivo, RocaMaestra> requireRocasMaestrasCompletas(UserId actorId) {
        List<RocaMaestra> maestras = loadRocaMaestraPort.deParticipante(actorId);
        if (maestras.size() < EjeObjetivo.values().length) {
            throw new NotAuthorizedException("ROCKS_LOCKED: completa tu onboarding antes de planificar rocas");
        }
        return maestras.stream().collect(Collectors.toMap(RocaMaestra::eje, m -> m));
    }

    private RocaDiaria requireRocaPropia(UserId actorId, RocaDiariaId id) {
        RocaDiaria roca = loadRocaDiariaPort.byId(id)
                .orElseThrow(() -> new NoSuchElementException("Roca diaria no encontrada: " + id));
        if (!roca.participanteId().equals(actorId)) {
            throw new NotAuthorizedException("Esta roca diaria no pertenece al actor");
        }
        return roca;
    }

    /** SUSPENDIDO -> 403. Rol distinto de TRAINEE -> 403 (solo el aprendiz opera sus rocas). */
    private ProgresoParticipanteRocks requireProgreso(UserId actorId) {
        ProgresoParticipanteRocks progreso = progresoPort.deParticipante(actorId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + actorId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        if (progreso.rol() != RolParticipante.TRAINEE) {
            throw new NotAuthorizedException("Solo un aprendiz opera sus propias rocas");
        }
        return progreso;
    }
}
