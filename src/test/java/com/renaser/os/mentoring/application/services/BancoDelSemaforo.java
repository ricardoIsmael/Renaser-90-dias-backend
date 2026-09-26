package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.points.api.DetalleDelSemaforo;
import com.renaser.os.points.api.SemaforoFinder;
import com.renaser.os.points.api.SemanaDelSemaforo;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Dobles de las tres APIs públicas que leen las vistas del semáforo —{@code community}
 * (acompañamiento), {@code points} (semáforo) y {@code users} (nombres y roles)— más un reloj que
 * cada prueba puede mover. Los comparten las pruebas de los servicios y las de los controllers
 * ({@link VistasDelSemaforoDePrueba}); las ventanas que se cargan acá las arma {@link VentanasDePrueba}.
 *
 * <p>{@code SemaforoFinder} todavía no tiene implementación en esta rama (la escribe otro frente
 * de D-168): acá se lo dobla con lo que dice su contrato —sin clave = no se mide, una lectura para
 * toda la colección— y se registra cada llamada, para poder afirmar que nadie lo llama por persona.
 */
public class BancoDelSemaforo {

    public static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** Viernes 25 de setiembre de 2026, 15:00 UTC = 10:00 en Lima. */
    public static final Instant AHORA = Instant.parse("2026-09-25T15:00:00Z");
    /** La ventana vigente en Lima a {@link #AHORA}: los 7 días que terminan ayer (jueves 24). */
    public static final LocalDate DESDE_VIGENTE = LocalDate.of(2026, 9, 18);
    public static final LocalDate HASTA_VIGENTE = LocalDate.of(2026, 9, 24);
    /** La última semana sábado→viernes ya cerrada a {@link #AHORA}. */
    public static final LocalDate SEMANA_CERRADA = LocalDate.of(2026, 9, 18);

    private static final Instant DESDE_SIEMPRE = Instant.parse("2026-09-01T05:00:00Z");
    private static final UUID COHORTE = UUID.fromString("00000000-0000-0000-0000-0000000c0001");

    private final Map<UUID, AcompanamientoFinder.GrupoBasico> grupos = new LinkedHashMap<>();
    private final List<Tramo> acompanamientos = new ArrayList<>();
    private final List<Tramo> pertenencias = new ArrayList<>();
    private final Map<UserId, UserSummary> perfiles = new LinkedHashMap<>();
    private final Map<UserId, VentanaDelSemaforo> vigentes = new LinkedHashMap<>();
    private final Map<LocalDate, Map<UserId, VentanaDelSemaforo>> semanas = new LinkedHashMap<>();
    private final Map<UserId, DetalleDelSemaforo> detalles = new LinkedHashMap<>();
    private Instant ahora = AHORA;

    /** Cada lectura al semáforo, con los ids pedidos: una por vista, nunca una por persona. */
    public final List<List<UserId>> lecturasDelSemaforo = new ArrayList<>();
    public final List<LocalDate> semanasPedidas = new ArrayList<>();
    public final List<DetallePedido> detallesPedidos = new ArrayList<>();
    /** Cada {@code findByIds}, con los ids pedidos: el resumen del líder no puede pedir aprendices. */
    public final List<List<UserId>> nombresPedidos = new ArrayList<>();

    public record DetallePedido(UserId participante, int semanas) {
    }

    private record Tramo(UUID grupoId, UserId usuario, boolean mentor, Instant desde, Instant hasta) {

        boolean vigenteEn(Instant instante) {
            return !instante.isBefore(desde) && (hasta == null || instante.isBefore(hasta));
        }
    }

    // ── los dobles ──────────────────────────────────────────────────────────

    public final Clock reloj = new Clock() {
        @Override
        public Instant now() {
            return ahora;
        }

        @Override
        public LocalDate today() {
            return ahora.atZone(ZoneOffset.UTC).toLocalDate();
        }
    };

    public final AcompanamientoFinder acompanamiento = new AcompanamientoFinder() {
        @Override
        public List<TramoDeAcompanamiento> tramosDeMentor(UserId mentorId, Instant desde, Instant hasta) {
            return List.of(); // las vistas del semaforo no miran tramos historicos
        }

        @Override
        public List<UserId> aprendicesVigentes(UUID grupoId, Instant instante) {
            return vigentesDelGrupo(pertenencias.stream(), grupoId, instante);
        }

        @Override
        public List<TramoDeAprendiz> tramosDeAprendices(UUID grupoId, Instant desde, Instant hasta) {
            return List.of();
        }

        @Override
        public List<UserId> integrantesVigentes(UUID grupoId, Instant instante) {
            return vigentesDelGrupo(Stream.concat(pertenencias.stream(), acompanamientos.stream()), grupoId, instante)
                    .stream().distinct().toList();
        }

        @Override
        public List<UserId> acompanantesVigentes(UUID grupoId, Instant instante) {
            return vigentesDelGrupo(acompanamientos.stream(), grupoId, instante);
        }

        @Override
        public boolean esIntegranteVigente(UUID grupoId, UserId usuarioId, Instant instante) {
            return integrantesVigentes(grupoId, instante).contains(usuarioId);
        }

        @Override
        public boolean acompanaVigente(UserId actorId, UUID grupoId, Instant instante) {
            return vigentesDelGrupo(acompanamientos.stream(), grupoId, instante).contains(actorId);
        }

        @Override
        public Optional<GrupoBasico> grupo(UUID grupoId) {
            return Optional.ofNullable(grupos.get(grupoId));
        }

        @Override
        public List<GrupoAcompanado> gruposConMentorVigente(Instant instante) {
            List<GrupoAcompanado> conMentor = new ArrayList<>();
            for (GrupoBasico grupo : grupos.values()) {
                acompanamientos.stream()
                        .filter(t -> t.mentor() && t.grupoId().equals(grupo.grupoId()) && t.vigenteEn(instante))
                        .findFirst()
                        .ifPresent(t -> conMentor.add(new GrupoAcompanado(grupo.grupoId(), grupo.nombre(),
                                t.usuario(), grupo.cohorteId(), grupo.zonaHoraria(), 3)));
            }
            return conMentor;
        }
    };

    public final SemaforoFinder semaforo = new SemaforoFinder() {
        @Override
        public Map<UserId, VentanaDelSemaforo> vigenteDe(Collection<UserId> participantes) {
            lecturasDelSemaforo.add(List.copyOf(participantes));
            return soloLosPedidos(vigentes, participantes);
        }

        @Override
        public Map<UserId, VentanaDelSemaforo> semanaDe(Collection<UserId> participantes, LocalDate semanaHasta) {
            SemanaDelSemaforo.exigirCierreValido(semanaHasta);
            lecturasDelSemaforo.add(List.copyOf(participantes));
            semanasPedidas.add(semanaHasta);
            return soloLosPedidos(semanas.getOrDefault(semanaHasta, Map.of()), participantes);
        }

        @Override
        public DetalleDelSemaforo detalleDe(UserId participante, int cuantasSemanas) {
            detallesPedidos.add(new DetallePedido(participante, cuantasSemanas));
            return detalles.getOrDefault(participante, DetalleDelSemaforo.noAplica(LIMA, true));
        }
    };

    public final UserSummaryFinder usuarios = new UserSummaryFinder() {
        @Override
        public Optional<UserSummary> findById(UserId id) {
            return Optional.ofNullable(perfiles.get(id));
        }

        @Override
        public Map<UserId, UserSummary> findByIds(Collection<UserId> ids) {
            nombresPedidos.add(List.copyOf(ids));
            Map<UserId, UserSummary> encontrados = new LinkedHashMap<>();
            ids.stream().filter(perfiles::containsKey).forEach(id -> encontrados.put(id, perfiles.get(id)));
            return encontrados;
        }

        @Override
        public List<UserSummary> aprendicesActivos() {
            return List.of(); // las vistas del semaforo no usan el padron global
        }

        @Override
        public Optional<UserSummary> findByEmail(String email) {
            return Optional.empty();
        }
    };

    // ── los servicios reales, armados con los dobles ─────────────────────────

    public SemaforoDelGrupoService servicioDeTablas() {
        MedicionDeGrupos medicion = medicion();
        return new SemaforoDelGrupoService(acceso(medicion), medicion, acompanamiento, reloj);
    }

    public SemaforoDelAprendizService servicioDeDetalle() {
        return new SemaforoDelAprendizService(acceso(medicion()), semaforo, usuarios, reloj);
    }

    public SemaforoPorGruposService servicioPorGrupos() {
        MedicionDeGrupos medicion = medicion();
        return new SemaforoPorGruposService(acceso(medicion), medicion, acompanamiento, usuarios, reloj);
    }

    private MedicionDeGrupos medicion() {
        return new MedicionDeGrupos(acompanamiento, semaforo, usuarios);
    }

    private AccesoAVistasDelSemaforo acceso(MedicionDeGrupos medicion) {
        return new AccesoAVistasDelSemaforo(acompanamiento, medicion, usuarios);
    }

    // ── armado del escenario ────────────────────────────────────────────────

    /** Deja el banco vacío: el contexto de Spring de las pruebas web se reutiliza entre pruebas. */
    public void limpiar() {
        grupos.clear();
        acompanamientos.clear();
        pertenencias.clear();
        perfiles.clear();
        vigentes.clear();
        semanas.clear();
        detalles.clear();
        lecturasDelSemaforo.clear();
        semanasPedidas.clear();
        detallesPedidos.clear();
        nombresPedidos.clear();
        ahora = AHORA;
    }

    public void relojEn(Instant instante) {
        this.ahora = instante;
    }

    public void grupo(UUID grupoId, String nombre) {
        grupos.put(grupoId, new AcompanamientoFinder.GrupoBasico(grupoId, nombre, COHORTE, LIMA.getId()));
    }

    /** Mentor vigente del grupo desde antes de toda prueba. */
    public void mentor(UUID grupoId, UserId mentor) {
        acompanamientos.add(new Tramo(grupoId, mentor, true, DESDE_SIEMPRE, null));
    }

    /** Fue mentor del grupo hasta {@code hasta}: su token puede seguir vivo, la relación no. */
    public void exmentor(UUID grupoId, UserId mentor, Instant hasta) {
        acompanamientos.add(new Tramo(grupoId, mentor, true, DESDE_SIEMPRE, hasta));
    }

    /** Acompaña el grupo sin ser su mentor (guía o soporte). */
    public void guia(UUID grupoId, UserId guia) {
        acompanamientos.add(new Tramo(grupoId, guia, false, DESDE_SIEMPRE, null));
    }

    public void aprendiz(UUID grupoId, UserId aprendiz) {
        pertenencias.add(new Tramo(grupoId, aprendiz, false, DESDE_SIEMPRE, null));
    }

    /** Un aprendiz activo con nombre. */
    public void persona(UserId id, String nombre) {
        usuario(id, nombre, UserRole.TRAINEE, UserStatus.ACTIVE);
    }

    public void usuario(UserId id, String nombre, UserRole rol, UserStatus estado) {
        perfiles.put(id, new UserSummary(id, nombre, null, rol, estado));
    }

    public void vigente(UserId participante, VentanaDelSemaforo ventana) {
        vigentes.put(participante, ventana);
    }

    public void semana(LocalDate semanaHasta, UserId participante, VentanaDelSemaforo ventana) {
        semanas.computeIfAbsent(semanaHasta, s -> new LinkedHashMap<>()).put(participante, ventana);
    }

    public void detalle(UserId participante, DetalleDelSemaforo detalle) {
        detalles.put(participante, detalle);
    }

    // ── auxiliares de los dobles ────────────────────────────────────────────

    private static List<UserId> vigentesDelGrupo(Stream<Tramo> tramos, UUID grupoId, Instant instante) {
        return tramos.filter(t -> t.grupoId().equals(grupoId) && t.vigenteEn(instante)).map(Tramo::usuario).toList();
    }

    private static Map<UserId, VentanaDelSemaforo> soloLosPedidos(Map<UserId, VentanaDelSemaforo> guardadas,
                                                                  Collection<UserId> pedidos) {
        Map<UserId, VentanaDelSemaforo> encontradas = new LinkedHashMap<>();
        pedidos.stream().filter(guardadas::containsKey).forEach(id -> encontradas.put(id, guardadas.get(id)));
        return encontradas;
    }
}
