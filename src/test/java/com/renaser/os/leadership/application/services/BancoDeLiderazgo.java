package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarReporteDeMentoresUseCase.ConteoDeObservaciones;
import com.renaser.os.leadership.application.ports.out.GuardarObservacionPort;
import com.renaser.os.leadership.application.ports.out.LeerObservacionesPort;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor;
import com.renaser.os.mentoring.api.EvaluacionDeMentorFinder;
import com.renaser.os.mentoring.api.MedicionDeMentoresFinder;
import com.renaser.os.mentoring.api.MedicionDeMentoresFinder.GrupoMedido;
import com.renaser.os.mentoring.api.MedicionDeMentoresFinder.MedicionVigente;
import com.renaser.os.mentoring.api.MedicionDeMentoresFinder.SemaforoResumido;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.api.AtencionDeTicketsFinder;
import com.renaser.os.users.api.FichaDeMentorFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Dobles de las fuentes de la gestión del Líder de Mentores y fábrica de sus servicios REALES (D-241).
 * Lo usan las pruebas de servicio y, por {@link LiderazgoDePrueba}, las del controller.
 */
public class BancoDeLiderazgo {

    /** 03:00 UTC del 1 de octubre: en Lima todavía es 30 de septiembre (regla 02). */
    public static final Instant AHORA = Instant.parse("2026-10-01T03:00:00Z");

    private final Map<UserId, UserSummary> usuarios = new LinkedHashMap<>();
    private final Map<UserId, FichaDeMentorFinder.PerfilDeMentor> perfiles = new HashMap<>();
    private final List<GrupoMedido> grupos = new ArrayList<>();
    private final Map<UserId, SemaforoResumido> semaforoPorMentor = new HashMap<>();
    private final Map<UserId, EvaluacionDeMentorFinder.EvaluacionDeMentor> evaluaciones = new HashMap<>();
    public final List<AtencionDeTicketsFinder.TicketPendiente> pendientes = new ArrayList<>();
    public final List<AtencionDeTicketsFinder.TicketRespondido> respondidos = new ArrayList<>();
    public final List<ObservacionDeMentor> observaciones = new ArrayList<>();
    public final List<YearMonth> mesesEvaluados = new ArrayList<>();
    public boolean medicionCaida;
    public boolean ticketsCaidos;
    private int siguienteId = 1;

    public final Clock reloj = new Clock() {
        @Override
        public Instant now() {
            return AHORA;
        }

        @Override
        public LocalDate today() {
            return LocalDate.of(2026, 10, 1);
        }
    };

    public final IdGenerator ids = () -> UUID.fromString(String.format("00000000-0000-0000-0000-%012d", siguienteId++));

    public final UserSummaryFinder usuariosFinder = new UserSummaryFinder() {
        @Override
        public Optional<UserSummary> findById(UserId id) {
            return Optional.ofNullable(usuarios.get(id));
        }

        @Override
        public Map<UserId, UserSummary> findByIds(Collection<UserId> idsPedidos) {
            Map<UserId, UserSummary> encontrados = new HashMap<>();
            idsPedidos.forEach(id -> findById(id).ifPresent(u -> encontrados.put(id, u)));
            return encontrados;
        }

        @Override
        public List<UserSummary> aprendicesActivos() {
            return List.of();
        }

        @Override
        public Optional<UserSummary> findByEmail(String email) {
            return Optional.empty();
        }
    };

    public final FichaDeMentorFinder fichas = new FichaDeMentorFinder() {
        @Override
        public List<FichaDeMentor> mentoresActivos() {
            return usuarios.values().stream()
                    .filter(u -> u.role() == UserRole.MENTOR && u.status() == UserStatus.ACTIVE)
                    .sorted(Comparator.comparing(UserSummary::fullName))
                    .map(u -> new FichaDeMentor(u.id(), u.fullName(), null, u.status(), perfiles.get(u.id())))
                    .toList();
        }

        @Override
        public Optional<FichaDeMentor> mentor(UserId usuarioId) {
            return Optional.ofNullable(usuarios.get(usuarioId)).filter(u -> u.role() == UserRole.MENTOR)
                    .map(u -> new FichaDeMentor(u.id(), u.fullName(), null, u.status(), perfiles.get(u.id())));
        }
    };

    public final MedicionDeMentoresFinder medicion = () -> {
        if (medicionCaida) {
            throw new IllegalStateException("semaforo caido");
        }
        return new MedicionVigente(LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 29), false, List.copyOf(grupos),
                Map.copyOf(semaforoPorMentor));
    };

    public final EvaluacionDeMentorFinder evaluacion = (mentorId, mes) -> {
        mesesEvaluados.add(mes);
        return evaluaciones.getOrDefault(mentorId, new EvaluacionDeMentorFinder.EvaluacionDeMentor(mes.toString(),
                "America/Lima", null, 0, 0, 0, "SIN_HISTORIAL", "v1", AHORA));
    };

    public final AtencionDeTicketsFinder tickets = new AtencionDeTicketsFinder() {
        @Override
        public List<TicketPendiente> pendientesDe(Collection<UserId> participantes) {
            requireArriba();
            return pendientes.stream().filter(t -> participantes.contains(t.participanteId())).toList();
        }

        @Override
        public List<TicketRespondido> respondidosPor(Collection<UserId> respondedores, Instant desde, Instant hasta) {
            requireArriba();
            return respondidos.stream()
                    .filter(t -> respondedores.contains(t.respondidoPor()))
                    .filter(t -> !t.respondidoEn().isBefore(desde) && t.respondidoEn().isBefore(hasta))
                    .toList();
        }

        @Override
        public int respondidosSinAtribucion(Instant desde, Instant hasta) {
            requireArriba();
            return 2;
        }

        private void requireArriba() {
            if (ticketsCaidos) {
                throw new IllegalStateException("tickets caidos");
            }
        }
    };

    public final InMemoryObservaciones almacen = new InMemoryObservaciones();

    public class InMemoryObservaciones implements GuardarObservacionPort, LeerObservacionesPort {
        @Override
        public ObservacionDeMentor insertar(ObservacionDeMentor observacion) {
            observaciones.add(observacion);
            return observacion;
        }

        @Override
        public Optional<ObservacionDeMentor> porAutorYClave(UserId autorId, String clave) {
            return observaciones.stream()
                    .filter(o -> o.autorId().equals(autorId) && o.claveOperacion().equals(clave)).findFirst();
        }

        @Override
        public List<ObservacionDeMentor> deMentor(UserId mentorId, Instant antesDe, int limite) {
            return observaciones.stream()
                    .filter(o -> o.mentorId().equals(mentorId))
                    .filter(o -> antesDe == null || o.creadoEn().isBefore(antesDe))
                    .sorted(Comparator.comparing(ObservacionDeMentor::creadoEn).reversed())
                    .limit(limite)
                    .toList();
        }

        @Override
        public Map<UserId, ConteoDeObservaciones> conteoPorMentor(Instant desde, Instant hasta) {
            Map<UserId, ConteoDeObservaciones> conteos = new HashMap<>();
            observaciones.stream()
                    .filter(o -> !o.creadoEn().isBefore(desde) && o.creadoEn().isBefore(hasta))
                    .forEach(o -> conteos.merge(o.mentorId(), switch (o.tipo()) {
                        case RECONOCIMIENTO -> new ConteoDeObservaciones(1, 0, 0);
                        case SUGERENCIA -> new ConteoDeObservaciones(0, 1, 0);
                        case ALERTA -> new ConteoDeObservaciones(0, 0, 1);
                    }, (a, b) -> new ConteoDeObservaciones(a.reconocimientos() + b.reconocimientos(),
                            a.sugerencias() + b.sugerencias(), a.alertas() + b.alertas())));
            return conteos;
        }
    }

    // ── Preparación ─────────────────────────────────────────────────────

    public void usuario(UserId id, String nombre, UserRole rol, UserStatus estado) {
        usuarios.put(id, new UserSummary(id, nombre, null, rol, estado));
    }

    public void perfil(UserId mentor, String nivel, String estadoOperativo) {
        perfiles.put(mentor, new FichaDeMentorFinder.PerfilDeMentor(nivel, estadoOperativo,
                Instant.parse("2026-03-03T15:00:00Z")));
    }

    /** Un grupo del mentor con esos aprendices; el semáforo del mentor se fija aparte. */
    public void grupo(UUID grupoId, String nombre, UserId mentor, List<UserId> aprendices) {
        grupos.add(new GrupoMedido(grupoId, nombre, mentor, aprendices, semaforo(aprendices.size(), 0, 0, 0, "80.0")));
    }

    public void semaforoDe(UserId mentor, SemaforoResumido semaforo) {
        semaforoPorMentor.put(mentor, semaforo);
    }

    public void evaluacion(UserId mentor, String porcentaje) {
        evaluaciones.put(mentor, new EvaluacionDeMentorFinder.EvaluacionDeMentor("2026-09", "America/Lima",
                new BigDecimal(porcentaje), 8, 10, 3, "CALCULADA", "v1", AHORA));
    }

    public static SemaforoResumido semaforo(int verde, int amarillo, int rojo, int sinDatos, String promedio) {
        return new SemaforoResumido(verde, amarillo, rojo, sinDatos, promedio == null ? null : new BigDecimal(promedio),
                "VERDE", "Al día");
    }

    // ── Servicios reales ───────────────────────────────────────────────

    public AccesoDeLiderazgo acceso() {
        return new AccesoDeLiderazgo(usuariosFinder, fichas);
    }

    public IndicadoresDeMentores indicadores() {
        return new IndicadoresDeMentores(medicion, evaluacion, new AtencionDeConsultas(tickets, reloj));
    }

    public PadronDeMentoresService padron() {
        return new PadronDeMentoresService(acceso(), fichas, indicadores(), reloj);
    }

    public FichaDeMentorService ficha() {
        return new FichaDeMentorService(acceso(), indicadores(), almacen, reloj);
    }

    public ObservacionesDeMentorService observacionesService() {
        return new ObservacionesDeMentorService(acceso(), almacen, almacen, ids, reloj);
    }

    public ReporteDeMentoresService reporte() {
        return new ReporteDeMentoresService(acceso(), fichas, indicadores(), almacen, tickets, reloj);
    }
}
