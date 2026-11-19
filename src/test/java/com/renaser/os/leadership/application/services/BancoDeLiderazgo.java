package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarReporteDeMentoresUseCase.ConteoDeObservaciones;
import com.renaser.os.leadership.application.ports.out.GuardarObservacionPort;
import com.renaser.os.leadership.application.ports.out.LeerObservacionesPort;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor;
import com.renaser.os.chat.api.MensajeDirectoPort;
import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.CupoDeLosGruposFinder;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.mentoring.api.EvaluacionDeMentorFinder;
import com.renaser.os.mentoring.api.MedicionDeMentoresFinder;
import com.renaser.os.mentoring.api.MedicionDeMentoresFinder.GrupoMedido;
import com.renaser.os.mentoring.api.MedicionDeMentoresFinder.MedicionVigente;
import com.renaser.os.mentoring.api.MedicionDeMentoresFinder.SemaforoResumido;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.api.AtencionDeTicketsFinder;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.api.AlcanceDeFormacionPermisivo;
import com.renaser.os.users.api.CuentasDePruebaFinder;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.FichaDeMentorFinder;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    /** D-318: los mentores y guías que llevan su propio programa (activado o no); el resto no tiene fila. */
    private final Map<UserId, ParticipacionPrograma> programasPropios = new HashMap<>();
    private final Set<UserId> cuentasDePrueba = new java.util.HashSet<>();
    private final List<GrupoMedido> grupos = new ArrayList<>();
    private final Map<UserId, SemaforoResumido> semaforoPorMentor = new HashMap<>();
    private final Map<UserId, EvaluacionDeMentorFinder.EvaluacionDeMentor> evaluaciones = new HashMap<>();
    public final List<AtencionDeTicketsFinder.TicketPendiente> pendientes = new ArrayList<>();
    public final List<AtencionDeTicketsFinder.TicketRespondido> respondidos = new ArrayList<>();
    public final List<ObservacionDeMentor> observaciones = new ArrayList<>();
    public final List<YearMonth> mesesEvaluados = new ArrayList<>();
    /** Spec 056: el tope de cada grupo; el que no figura tiene el de la política por defecto (10). */
    private final Map<UUID, Integer> cupoPorGrupo = new HashMap<>();
    /** Spec 056: los grupos en curso que no tienen mentor, por formación. */
    private final List<AcompanamientoFinder.GrupoAcompanado> gruposSinMentor = new ArrayList<>();
    public boolean medicionCaida;
    public boolean ticketsCaidos;
    public boolean cuposCaidos;
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

    /** Spec 056 FR-004: las personas que NO son de la formación de quien mira; el resto sigue siendo de todas, como en el permisivo. */
    private final Set<UserId> deOtraFormacion = new HashSet<>();

    private final AlcanceDeFormacionFinder formaciones = new AlcanceDeFormacionPermisivo() {
        @Override
        public Set<UserId> dentroDelAlcance(Collection<UserId> personas, AlcanceDeFormacion alcance) {
            Set<UserId> visibles = new HashSet<>(personas);
            visibles.removeAll(deOtraFormacion);
            return visibles;
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
        public List<FichaDeMentor> mentoresSuspendidos() {
            return usuarios.values().stream()
                    .filter(u -> u.role() == UserRole.MENTOR && u.status() == UserStatus.SUSPENDED)
                    .sorted(Comparator.comparing(UserSummary::fullName))
                    .map(u -> new FichaDeMentor(u.id(), u.fullName(), null, u.status(), perfiles.get(u.id())))
                    .toList();
        }

        @Override
        public Optional<FichaDeMentor> mentor(UserId usuarioId) {
            return Optional.ofNullable(usuarios.get(usuarioId))
                    .filter(u -> UserRole.ROLES_QUE_ACTUAN_COMO_MENTOR.contains(u.role()))
                    .map(u -> new FichaDeMentor(u.id(), u.fullName(), null, u.status(), perfiles.get(u.id())));
        }
    };

    /** D-318: lo que {@code users} diría del programa propio de cada mentor o guía; quien no tiene fila no está inscrito. */
    private final ParticipacionProgramaFinder participaciones = new ParticipacionProgramaFinder() {
        @Override
        public Optional<ParticipacionPrograma> deParticipante(UserId id) {
            if (!usuarios.containsKey(id)) {
                return Optional.empty();
            }
            return Optional.of(programasPropios.getOrDefault(id, new ParticipacionPrograma(id, false, 0, null,
                    java.time.ZoneId.of("America/Lima"), FasePrograma.PHASE_3_ALCHEMIST_WARRIOR, null, null,
                    usuarios.get(id).role(), false, false)));
        }

        @Override
        public List<UserId> miembrosActivosDeCelula(UUID celulaId) {
            return List.of();
        }

        @Override
        public List<UserId> miembrosDeCelula(UUID celulaId) {
            return List.of();
        }

        @Override
        public List<UserId> usuariosActivosConRol(Set<UserRole> roles) {
            return List.of();
        }

        @Override
        public List<UsuarioConDiaPrograma> usuariosActivosConDiaPrograma(Set<UserRole> roles) {
            return List.of();
        }

        @Override
        public List<UserId> participantesInscritosActivos() {
            return List.of();
        }

        @Override
        public int contarMiembrosDeCelula(UUID celulaId) {
            return 0;
        }
    };

    private final CuentasDePruebaFinder pruebas = new CuentasDePruebaFinder() {
        @Override
        public boolean esCuentaDePrueba(UserId id) {
            return cuentasDePrueba.contains(id);
        }

        @Override
        public Set<UserId> entre(Collection<UserId> ids) {
            Set<UserId> deLaPrueba = new java.util.HashSet<>(ids);
            deLaPrueba.retainAll(cuentasDePrueba);
            return deLaPrueba;
        }

        @Override
        public Set<UserId> todas() {
            return Set.copyOf(cuentasDePrueba);
        }
    };

    public final MedicionDeMentoresFinder medicion = alcance -> {
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

    /** Quién acompaña a quién, derivado de los grupos que ya se cargan para el padrón (spec 005, R-7). */
    public final AcompanamientoFinder acompanamiento = new AcompanamientoFinder() {
        @Override
        public List<AcompanamientoFinder.GrupoAcompanado> gruposConMentorVigente(Instant instante) {
            return grupos.stream().map(g -> new AcompanamientoFinder.GrupoAcompanado(g.grupoId(), g.nombre(),
                    g.mentorId(), UUID.randomUUID(), "America/Lima", 3)).toList();
        }

        @Override
        public List<UserId> aprendicesVigentes(UUID grupoId, Instant instante) {
            return grupos.stream().filter(g -> g.grupoId().equals(grupoId)).flatMap(g -> g.aprendices().stream()).toList();
        }

        @Override
        public List<TramoDeAcompanamiento> tramosDeMentor(UserId mentorId, Instant desde, Instant hasta) {
            return List.of();
        }

        @Override
        public List<TramoDeAprendiz> tramosDeAprendices(UUID grupoId, Instant desde, Instant hasta) {
            return List.of();
        }

        @Override
        public List<UserId> integrantesVigentes(UUID grupoId, Instant instante) {
            return List.of();
        }

        @Override
        public List<UserId> acompanantesVigentes(UUID grupoId, Instant instante) {
            return List.of();
        }

        @Override
        public boolean esIntegranteVigente(UUID grupoId, UserId usuarioId, Instant instante) {
            return false;
        }

        @Override
        public boolean acompanaVigente(UserId actorId, UUID grupoId, Instant instante) {
            return false;
        }

        @Override
        public boolean grupoOperativo(UUID grupoId, Instant instante) {
            return false;
        }

        @Override
        public Optional<GrupoBasico> grupo(UUID grupoId) {
            return Optional.empty();
        }

        @Override
        public List<GrupoAcompanado> gruposRegularesEnCurso(Instant instante) {
            List<GrupoAcompanado> enCurso = new ArrayList<>(gruposConMentorVigente(instante));
            enCurso.addAll(gruposSinMentor);
            return enCurso;
        }

        @Override
        public List<GrupoAcompanado> gruposRegularesEnCursoEntre(LocalDate desde, LocalDate hasta) {
            return gruposConMentorVigente(AHORA);
        }

        @Override
        public List<GrupoConAprendices> gruposOperativos(Instant instante) {
            return List.of();
        }
    };

    /** Spec 056: el tope de cada grupo pedido; falla como lo haría {@code community} si {@link #cuposCaidos}. */
    public final CupoDeLosGruposFinder cupos = grupoIds -> {
        if (cuposCaidos) {
            throw new IllegalStateException("cupos caidos");
        }
        Map<UUID, Integer> maximos = new HashMap<>();
        grupos.stream().filter(g -> grupoIds.contains(g.grupoId()))
                .forEach(g -> maximos.put(g.grupoId(), cupoPorGrupo.getOrDefault(g.grupoId(), 10)));
        return maximos;
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

    /** El banco de las pruebas del controller es un bean compartido: cada prueba arma sus grupos desde cero. */
    public void limpiarGrupos() {
        grupos.clear();
        cupoPorGrupo.clear();
        gruposSinMentor.clear();
        cuposCaidos = false;
        usuarios.clear();
        deOtraFormacion.clear();
        programasPropios.clear();
        cuentasDePrueba.clear();
    }

    public void usuario(UserId id, String nombre, UserRole rol, UserStatus estado) {
        usuarios.put(id, new UserSummary(id, nombre, null, rol, estado));
    }

    /** Spec 056 FR-004: esta persona es de otra formación, así que el líder de las pruebas no la ve en el padrón. */
    public void deOtraFormacion(UserId persona) {
        deOtraFormacion.add(persona);
    }

    /** D-318: el mentor o la guía lleva su propio programa; {@code activado} = ya eligió su Día 1. */
    public void programaPropio(UserId id, UserRole rol, boolean activado) {
        programasPropios.put(id, new ParticipacionPrograma(id, true, activado ? 40 : 0, LocalDate.of(2026, 8, 17),
                java.time.ZoneId.of("America/Lima"), FasePrograma.PHASE_3_ALCHEMIST_WARRIOR, null, null, rol, false,
                activado));
    }

    public void cuentaDePrueba(UserId id) {
        cuentasDePrueba.add(id);
    }

    public void perfil(UserId mentor, String nivel, String estadoOperativo) {
        perfiles.put(mentor, new FichaDeMentorFinder.PerfilDeMentor(nivel, estadoOperativo,
                Instant.parse("2026-03-03T15:00:00Z")));
    }

    /** Un grupo del mentor con esos aprendices; el semáforo del mentor se fija aparte. */
    public void grupo(UUID grupoId, String nombre, UserId mentor, List<UserId> aprendices) {
        grupos.add(new GrupoMedido(grupoId, nombre, mentor, aprendices, semaforo(aprendices.size(), 0, 0, 0, "80.0")));
    }

    /** Spec 056: el grupo tiene su propio tope (de 10 a 15) en vez del de la política. */
    public void cupoDelGrupo(UUID grupoId, int maximo) {
        cupoPorGrupo.put(grupoId, maximo);
    }

    /** Spec 056: un grupo regular en curso que no tiene mentor, de esa formación. */
    public void grupoSinMentor(UUID grupoId, String nombre, Formacion formacion) {
        gruposSinMentor.add(new AcompanamientoFinder.GrupoAcompanado(grupoId, nombre, null, UUID.randomUUID(), "America/Lima", 3,
                formacion));
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
        return new AccesoDeLiderazgo(usuariosFinder, fichas, formaciones);
    }

    public IndicadoresDeMentores indicadores() {
        return new IndicadoresDeMentores(medicion, evaluacion, new AtencionDeConsultas(tickets, reloj), cupos);
    }

    public PorResolverService porResolver() {
        return new PorResolverService(acceso(), indicadores(), acompanamiento, tickets, reloj);
    }

    public PadronDeMentoresService padron() {
        return new PadronDeMentoresService(acceso(), fichas, indicadores(), reloj);
    }

    PersonasDelPrograma programa() {
        return new PersonasDelPrograma(acompanamiento, participaciones, usuariosFinder, pruebas,
                new com.renaser.os.users.api.AlcanceDeFormacionPermisivo());
    }

    public FichaDeMentorService ficha() {
        return new FichaDeMentorService(acceso(), indicadores(), almacen, programa(), reloj);
    }

    /** Spec 056: lo que el servidor le escribe a una persona por su chat directo (un mensaje por llamada, con su conversación y su id). */
    public record MensajeDirectoEscrito(UserId de, UserId para, String texto, UUID conversacionId, UUID mensajeId) {
    }

    /** Spec 056: los mensajes directos que el servidor escribió, en orden. */
    public final List<MensajeDirectoEscrito> mensajesDirectos = new ArrayList<>();
    /** Spec 056: los avisos entre módulos que se publicaron, en orden. */
    public final List<Object> avisosPublicados = new ArrayList<>();
    /** Spec 056: si no es null, el chat rechaza el mensaje con esto (los chats directos apagados, dos personas sin formación en común). */
    public RuntimeException chatRechaza;

    public final MensajeDirectoPort chatDirecto = (remitente, destinatario, texto) -> {
        if (chatRechaza != null) {
            throw chatRechaza;
        }
        UUID conversacion = UUID.nameUUIDFromBytes((remitente.value() + "|" + destinatario.value()).getBytes());
        MensajeDirectoEscrito escrito = new MensajeDirectoEscrito(remitente, destinatario, texto, conversacion, UUID.randomUUID());
        mensajesDirectos.add(escrito);
        return new MensajeDirectoPort.MensajeDirecto(escrito.conversacionId(), escrito.mensajeId());
    };

    public final ApplicationEventPublisher publicador = avisosPublicados::add;

    public ObservacionesDeMentorService observacionesService() {
        return new ObservacionesDeMentorService(acceso(), almacen, almacen, ids, reloj, usuariosFinder, acompanamiento, chatDirecto,
                new AvisosDeObservaciones(publicador));
    }

    public ReporteDeMentoresService reporte() {
        return new ReporteDeMentoresService(acceso(), fichas, indicadores(), almacen, tickets, reloj);
    }
}
