package com.renaser.os.leadership.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.habits.api.CodigoRenaserDePersonaFinder;
import com.renaser.os.habits.api.CodigoRenaserDePersonaFinder.CodigoRenaserDePersona;
import com.renaser.os.habits.api.CodigoRenaserDePersonaFinder.MiRadarDeLaPersona;
import com.renaser.os.habits.api.HorarioDelDiaFinder;
import com.renaser.os.habits.api.HorarioDelDiaFinder.HorarioResuelto;
import com.renaser.os.habits.api.HorarioDelDiaFinder.HorariosDelDia;
import com.renaser.os.leadership.application.ports.out.GuardarConsultaDeFichaPort;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha;
import com.renaser.os.leadership.domain.model.cumplimiento.VentanaDeLaFicha;
import com.renaser.os.mentoring.api.DiaDelAlumno;
import com.renaser.os.mentoring.api.HuecosDeHorarioFinder;
import com.renaser.os.mentoring.application.services.SemanasDeHuecosDePrueba;
import com.renaser.os.mentoring.api.ObligacionDelDia;
import com.renaser.os.mentoring.api.ResumenSemana;
import com.renaser.os.mentoring.api.SemanaDelAlumno;
import com.renaser.os.mentoring.api.SemanaDeSeguimientoFinder;
import com.renaser.os.mentoring.api.TablaDelSemaforoFinder;
import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DetalleDelSemaforo;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.points.api.SemaforoFinder;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.rocks.api.AccionesDelDiaFinder;
import com.renaser.os.rocks.api.CierresDelDiaFinder;
import com.renaser.os.rocks.api.AccionesDelDiaFinder.AccionDelDia;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CuentasDePruebaFinder;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.FichaDeMentorFinder;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Dobles de las fuentes de la ficha del líder (spec 005) y fábrica de sus servicios REALES. Lo usan las pruebas de
 * servicio y, por {@link CumplimientoDePrueba}, las del controller.
 *
 * <p>Se doblan solo las APIs públicas de los otros módulos: el semáforo, la semana de seguimiento, las acciones, el
 * acompañamiento y las cuentas. Lo que se prueba es lo del líder: a quién deja ver, qué ventana pide, cómo ordena y
 * qué registra.
 */
public class BancoDeCumplimiento {

    public static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** Lunes 19 de octubre de 2026, 15:00 UTC = 10:00 en Lima. */
    public static final Instant AHORA = Instant.parse("2026-10-19T15:00:00Z");
    /** La última semana sábado→viernes cerrada a {@link #AHORA}: del 10 al 16 de octubre. */
    public static final LocalDate DESDE_SEMANA_CERRADA = LocalDate.of(2026, 10, 10);
    public static final LocalDate SEMANA_CERRADA = LocalDate.of(2026, 10, 16);
    /** La ventana vigente del resumen a {@link #AHORA}: los 7 días que terminan ayer, del lunes 12 al domingo 18. */
    public static final LocalDate DESDE_VIGENTE = LocalDate.of(2026, 10, 12);
    public static final LocalDate HASTA_VIGENTE = LocalDate.of(2026, 10, 18);
    /** El viernes de la semana que corre a {@link #AHORA} (hoy es el lunes 19): todavía no llegó. */
    public static final LocalDate VIERNES_FUTURO = LocalDate.of(2026, 10, 23);

    public static final UUID FENIX = UUID.fromString("00000000-0000-0000-0000-00000000f001");
    /** Un grupo en curso sin mentor y la recepción: ninguno aparece entre los «con mentor». */
    public static final UUID SIN_MENTOR = UUID.fromString("00000000-0000-0000-0000-00000000f002");
    private static final UUID COHORTE = UUID.fromString("00000000-0000-0000-0000-0000000c0001");

    /** El «ahora» de la prueba: se puede mover para el caso de la madrugada UTC. */
    public Instant ahora = AHORA;

    final Map<UserId, UserSummary> usuarios = new LinkedHashMap<>();
    final Map<UserId, ParticipacionPrograma> participaciones = new LinkedHashMap<>();
    final Map<UUID, GrupoAcompanado> gruposConMentor = new LinkedHashMap<>();
    final Map<UUID, List<UserId>> aprendicesPorGrupo = new LinkedHashMap<>();
    /** Spec 021: grupos en curso sin mentor y la recepción; no entran entre los «con mentor». */
    final Map<UUID, OtroGrupo> otrosGrupos = new LinkedHashMap<>();
    final Set<UserId> cuentasDePrueba = new HashSet<>();

    final Map<LocalDate, Map<UserId, VentanaDelSemaforo>> ventanasPorSemana = new LinkedHashMap<>();
    /** La ventana vigente de cada persona: la que el resumen lee sin fecha. */
    final Map<UserId, VentanaDelSemaforo> ventanasVigentes = new LinkedHashMap<>();
    /**
     * Cada lectura del semáforo, en orden: «semana:2026-10-16 [quiénes]», «vigente [quiénes]», «tabla:2026-10-16» o
     * «tabla:vigente».
     */
    public final List<String> lecturasDelSemaforo = new ArrayList<>();

    final Map<UserId, Map<LocalDate, List<ObligacionDelDia>>> obligacionesPorDia = new LinkedHashMap<>();
    final Map<UserId, List<AccionDelDia>> accionesPorPersona = new LinkedHashMap<>();
    /** Spec 018: los hábitos que le tocan a cada persona hoy, con la hora que cada una eligió o no. */
    final Map<UserId, List<HorarioResuelto>> horariosDeHoy = new LinkedHashMap<>();
    /** Spec 018: cada pregunta al horario del día, como «persona fecha»: prueba que «hoy» es el de ella. */
    public final List<String> horariosPedidos = new ArrayList<>();
    /** Cada ventana que se le pidió a la semana de seguimiento: {desde, hasta}. */
    public final List<LocalDate[]> ventanasPedidasALaSemana = new ArrayList<>();

    public final List<ConsultaDeFicha> consultas = new ArrayList<>();
    private int siguienteId = 1;

    /** Lo que {@code habits} diria del Codigo Renaser de cada persona (spec 012). */
    final Map<UserId, CodigoRenaserDePersona> radares = new LinkedHashMap<>();
    /** Cada lectura del Radar que llego a {@code habits}: quien, desde que cursor y de a cuantos. */
    public final List<LecturaDelRadarPedida> lecturasDelRadar = new ArrayList<>();

    /** Un grupo en curso sin mentor, o la recepción. */
    record OtroGrupo(UUID grupoId, String nombre, boolean recepcion) {
    }

    /** @param cursor el que pidio quien llamo; null = la pagina mas reciente */
    public record LecturaDelRadarPedida(UserId persona, Instant cursor, int tamanoPagina) {
    }

    public final Clock reloj = new Clock() {
        @Override
        public Instant now() {
            return ahora;
        }

        @Override
        public LocalDate today() {
            return ahora.atZone(ZoneId.of("UTC")).toLocalDate();
        }
    };

    public final IdGenerator ids = () -> UUID.fromString(String.format("00000000-0000-0000-0000-%012d", siguienteId++));

    public final UserSummaryFinder usuariosFinder = new UserSummaryFinder() {
        @Override
        public Optional<UserSummary> findById(UserId id) {
            return Optional.ofNullable(usuarios.get(id));
        }

        @Override
        public Map<UserId, UserSummary> findByIds(Collection<UserId> pedidos) {
            Map<UserId, UserSummary> encontrados = new LinkedHashMap<>();
            pedidos.forEach(id -> findById(id).ifPresent(u -> encontrados.put(id, u)));
            return encontrados;
        }

        @Override
        public List<UserSummary> aprendicesActivos() {
            return usuarios.values().stream()
                    .filter(u -> u.role() == UserRole.TRAINEE && u.status() == UserStatus.ACTIVE).toList();
        }

        @Override
        public Optional<UserSummary> findByEmail(String email) {
            return Optional.empty();
        }
    };

    final ParticipacionProgramaFinder participacionFinder = new ParticipacionProgramaFinder() {
        @Override
        public Optional<ParticipacionPrograma> deParticipante(UserId id) {
            return Optional.ofNullable(participaciones.get(id));
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
            return participaciones.values().stream().filter(ParticipacionPrograma::inscrito)
                    .map(ParticipacionPrograma::participanteId)
                    .filter(id -> usuarios.get(id) != null && usuarios.get(id).status() == UserStatus.ACTIVE).toList();
        }

        @Override
        public int contarMiembrosDeCelula(UUID celulaId) {
            return 0;
        }
    };

    final AcompanamientoFinder acompanamiento = new AcompanamientoFinder() {
        @Override
        public List<TramoDeAcompanamiento> tramosDeMentor(UserId mentorId, Instant desde, Instant hasta) {
            return List.of();
        }

        @Override
        public List<UserId> aprendicesVigentes(UUID grupoId, Instant instante) {
            return aprendicesPorGrupo.getOrDefault(grupoId, List.of());
        }

        @Override
        public List<TramoDeAprendiz> tramosDeAprendices(UUID grupoId, Instant desde, Instant hasta) {
            return List.of();
        }

        @Override
        public List<UserId> integrantesVigentes(UUID grupoId, Instant instante) {
            return aprendicesVigentes(grupoId, instante);
        }

        @Override
        public List<UserId> acompanantesVigentes(UUID grupoId, Instant instante) {
            return List.of();
        }

        @Override
        public boolean esIntegranteVigente(UUID grupoId, UserId usuarioId, Instant instante) {
            return aprendicesVigentes(grupoId, instante).contains(usuarioId);
        }

        @Override
        public boolean acompanaVigente(UserId actorId, UUID grupoId, Instant instante) {
            return false;
        }

        @Override
        public boolean grupoOperativo(UUID grupoId, Instant instante) {
            return gruposConMentor.containsKey(grupoId);
        }

        @Override
        public Optional<GrupoBasico> grupo(UUID grupoId) {
            return Optional.ofNullable(gruposConMentor.get(grupoId))
                    .map(g -> new GrupoBasico(g.grupoId(), g.nombre(), g.cohorteId(), g.zonaHoraria()));
        }

        @Override
        public List<GrupoAcompanado> gruposConMentorVigente(Instant instante) {
            return List.copyOf(gruposConMentor.values());
        }

        @Override
        public List<GrupoAcompanado> gruposRegularesEnCurso(Instant instante) {
            return gruposConMentorVigente(instante);
        }

        @Override
        public List<GrupoAcompanado> gruposRegularesEnCursoEntre(LocalDate desde, LocalDate hasta) {
            return gruposConMentorVigente(ahora);
        }

        @Override
        public List<GrupoConAprendices> gruposOperativos(Instant instante) {
            List<GrupoConAprendices> operativos = new ArrayList<>();
            gruposConMentor.values().forEach(g -> operativos.add(new GrupoConAprendices(g.grupoId(), g.nombre(), false,
                    g.mentorId(), aprendicesPorGrupo.getOrDefault(g.grupoId(), List.of()))));
            otrosGrupos.values().forEach(g -> operativos.add(new GrupoConAprendices(g.grupoId(), g.nombre(),
                    g.recepcion(), null, aprendicesPorGrupo.getOrDefault(g.grupoId(), List.of()))));
            return operativos;
        }
    };

    final CuentasDePruebaFinder pruebas = new CuentasDePruebaFinder() {
        @Override
        public boolean esCuentaDePrueba(UserId id) {
            return cuentasDePrueba.contains(id);
        }

        @Override
        public Set<UserId> entre(Collection<UserId> ids) {
            Set<UserId> deLaPrueba = new HashSet<>(ids);
            deLaPrueba.retainAll(cuentasDePrueba);
            return deLaPrueba;
        }

        @Override
        public Set<UserId> todas() {
            return Set.copyOf(cuentasDePrueba);
        }
    };

    /** Reemplazable: {@link #compartirElSemaforoCon} lo cambia por el del resumen para comparar las tres pantallas. */
    SemaforoFinder semaforo = new SemaforoFinder() {
        @Override
        public Map<UserId, VentanaDelSemaforo> vigenteDe(Collection<UserId> participantes) {
            lecturasDelSemaforo.add("vigente " + participantes);
            return deLos(participantes, ventanasVigentes);
        }

        @Override
        public Map<UserId, VentanaDelSemaforo> semanaDe(Collection<UserId> participantes, LocalDate semanaHasta) {
            lecturasDelSemaforo.add("semana:" + semanaHasta + " " + participantes);
            return deLos(participantes, ventanasPorSemana.getOrDefault(semanaHasta, Map.of()));
        }

        @Override
        public DetalleDelSemaforo detalleDe(UserId participante, int semanas) {
            throw new UnsupportedOperationException("La ficha del lider no pide el detalle");
        }
    };

    /** Como {@code SemaforoDelGrupoService.tablaDe}: sin fecha la ventana vigente, con un viernes esa semana. */
    TablaDelSemaforoFinder tabla = (grupoId, semanaHasta) -> {
        GrupoAcompanado grupo = gruposConMentor.get(grupoId);
        if (grupo == null) {
            throw new java.util.NoSuchElementException("Grupo no encontrado");
        }
        lecturasDelSemaforo.add("tabla:" + (semanaHasta == null ? "vigente" : semanaHasta));
        VentanaDeLaFicha ventana = VentanaDeLaFicha.de(semanaHasta, ahora.atZone(LIMA).toLocalDate());
        Map<UserId, VentanaDelSemaforo> ventanas = semanaHasta == null ? ventanasVigentes
                : ventanasPorSemana.getOrDefault(semanaHasta, Map.of());
        List<TablaDelSemaforoFinder.FilaDeLaTabla> filas = aprendicesPorGrupo.getOrDefault(grupoId, List.of())
                .stream().filter(id -> usuarios.get(id) != null && usuarios.get(id).status().allowsAccess())
                .map(id -> fila(id, ventanas.get(id))).toList();
        // Como el servicio real (PeriodoDelSemaforo.de): la tabla solo dice «cerrada» si TODAS las ventanas de sus
        // aprendices lo estan, y quien no tiene foto de la semana (empezo hace poco) la deja abierta (E-629).
        boolean cerradaComoLaTabla = filas.isEmpty() ? ventana.cerrada() : filas.stream()
                .allMatch(fila -> ventanas.get(UserId.of(fila.personaId())) != null
                        && ventanas.get(UserId.of(fila.personaId())).cerrada());
        return new TablaDelSemaforoFinder.TablaDeGrupo(grupoId, grupo.nombre(), ventana.desde(), ventana.hasta(),
                cerradaComoLaTabla, filas);
    };

    final SemanaDeSeguimientoFinder seguimiento = (personaId, grupoId, desde, hasta) -> {
        ventanasPedidasALaSemana.add(new LocalDate[]{desde, hasta});
        List<DiaDelAlumno> dias = new ArrayList<>();
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            dias.add(new DiaDelAlumno(fecha, null, obligacionesPorDia.getOrDefault(personaId, Map.of())
                    .getOrDefault(fecha, List.of())));
        }
        int total = dias.stream().mapToInt(d -> d.obligaciones().size()).sum();
        return new SemanaDelAlumno(grupoId, personaId.value(), usuarios.get(personaId).fullName(), desde, hasta, 24,
                LIMA.getId(), dias, new ResumenSemana(total, total, 0, 0, 0), "COMPLETA", ahora);
    };

    final AccionesDelDiaFinder accionesFinder = (aprendiz, desde, hasta) -> accionesPorPersona
            .getOrDefault(aprendiz, List.of()).stream()
            .filter(a -> !a.fecha().isBefore(desde) && !a.fecha().isAfter(hasta)).toList();

    final CodigoRenaserDePersonaFinder radarFinder = (persona, cursor, tamanoPagina) -> {
        lecturasDelRadar.add(new LecturaDelRadarPedida(persona, cursor, tamanoPagina));
        return radares.getOrDefault(persona, new CodigoRenaserDePersona(new MiRadarDeLaPersona(false, java.time.LocalDate.of(2026, 10, 14), List.of()), List.of(), null));
    };

    /** Spec 018: el horario resuelto de un día, tal cual lo deja {@link #habitosDeHoy}. */
    final HorarioDelDiaFinder horarioFinder = (participante, fecha) -> {
        horariosPedidos.add(participante.value() + " " + fecha);
        return new HorariosDelDia(fecha, 0, horariosDeHoy.getOrDefault(participante, List.of()),
                new HorarioDelDiaFinder.CuotaCambiosHorario(0, 3, 3, false));
    };

    final GuardarConsultaDeFichaPort guardarConsulta = consulta -> {
        consultas.add(consulta);
        return consulta;
    };

    // ── Preparación ─────────────────────────────────────────────────────

    /** Vuelve al estado de recién creado: el banco del controller es un bean que comparten todas las pruebas. */
    public void limpiar() {
        ahora = AHORA;
        usuarios.clear();
        participaciones.clear();
        gruposConMentor.clear();
        aprendicesPorGrupo.clear();
        otrosGrupos.clear();
        cuentasDePrueba.clear();
        ventanasPorSemana.clear();
        ventanasVigentes.clear();
        lecturasDelSemaforo.clear();
        obligacionesPorDia.clear();
        accionesPorPersona.clear();
        horariosDeHoy.clear();
        horariosPedidos.clear();
        ventanasPedidasALaSemana.clear();
        consultas.clear();
        radares.clear();
        lecturasDelRadar.clear();
    }

    /** Un grupo con mentor vigente (que es una cuenta de mentor activa, sin programa propio hasta que se le dé). */
    public void grupoConMentor(UUID grupoId, String nombre, UserId mentor, String nombreDelMentor) {
        gruposConMentor.put(grupoId, new GrupoAcompanado(grupoId, nombre, mentor, COHORTE, LIMA.getId(), 3));
        usuario(mentor, nombreDelMentor, UserRole.MENTOR, UserStatus.ACTIVE);
    }

    /** Spec 021: un grupo en curso sin mentor. */
    public void grupoSinMentor(UUID grupoId, String nombre) {
        otrosGrupos.put(grupoId, new OtroGrupo(grupoId, nombre, false));
    }

    /** Spec 021: la recepción (grupo de bienvenida, sin mentor). */
    public void recepcion(UUID grupoId, String nombre) {
        otrosGrupos.put(grupoId, new OtroGrupo(grupoId, nombre, true));
    }

    /** Spec 021: marca la cuenta como de prueba de «ver como»: la lista y las fichas del líder no la muestran. */
    public void cuentaDePrueba(UserId id) {
        cuentasDePrueba.add(id);
    }

    /** Spec 021: un aprendiz con su programa activado que no está en ningún grupo. */
    public void aprendizSinGrupo(UserId id, String nombre) {
        usuario(id, nombre, UserRole.TRAINEE, UserStatus.ACTIVE);
        programa(id, UserRole.TRAINEE, LocalDate.of(2026, 8, 17), true);
    }

    public void usuario(UserId id, String nombre, UserRole rol, UserStatus estado) {
        usuarios.put(id, new UserSummary(id, nombre, null, rol, estado));
    }

    /** Un aprendiz del grupo con su programa activado (Día 1 el 17 de agosto) y la zona de Lima. */
    public void aprendiz(UUID grupoId, UserId id, String nombre) {
        usuario(id, nombre, UserRole.TRAINEE, UserStatus.ACTIVE);
        aprendicesPorGrupo.computeIfAbsent(grupoId, g -> new ArrayList<>()).add(id);
        programa(id, UserRole.TRAINEE, LocalDate.of(2026, 8, 17), true);
    }

    public void programa(UserId id, UserRole rol, LocalDate diaUno, boolean activado) {
        participaciones.put(id, new ParticipacionPrograma(id, true, activado ? 63 : 0, diaUno, LIMA,
                FasePrograma.PHASE_3_ALCHEMIST_WARRIOR, FENIX, null, rol, false, activado));
    }

    public void semana(LocalDate semanaHasta, UserId id, VentanaDelSemaforo ventana) {
        ventanasPorSemana.computeIfAbsent(semanaHasta, s -> new LinkedHashMap<>()).put(id, ventana);
    }

    /** Lo que el semáforo dice de la persona en la ventana vigente (los 7 días que terminan ayer). */
    public void vigente(UserId id, VentanaDelSemaforo ventana) {
        ventanasVigentes.put(id, ventana);
    }

    /**
     * Usa el semáforo y la tabla de OTRO banco (los del resumen por grupos): la lista y la ficha leen entonces
     * exactamente lo mismo que el resumen, y una prueba puede comparar las tres pantallas con los mismos datos.
     */
    public void compartirElSemaforoCon(SemaforoFinder semaforoCompartido, TablaDelSemaforoFinder tablaCompartida) {
        this.semaforo = semaforoCompartido;
        this.tabla = tablaCompartida;
    }

    public void obligacion(UserId id, LocalDate fecha, ObligacionDelDia obligacion) {
        obligacionesPorDia.computeIfAbsent(id, p -> new LinkedHashMap<>())
                .computeIfAbsent(fecha, f -> new ArrayList<>()).add(obligacion);
    }

    public void accion(UserId id, AccionDelDia accion) {
        accionesPorPersona.computeIfAbsent(id, p -> new ArrayList<>()).add(accion);
    }

    /** Lo que {@code habits} devuelve del Codigo Renaser y Mi Radar de la persona. */
    public void radar(UserId id, CodigoRenaserDePersona radar) {
        radares.put(id, radar);
    }

    /** Spec 018: un hábito que le toca a la persona hoy; {@code propia} = la hora la eligió ella, no el catálogo. */
    public void habitoDeHoy(UserId id, String titulo, boolean propia) {
        habitoDeHoy(id, titulo, propia, false, false);
    }

    public void habitoDeHoy(UserId id, String titulo, boolean propia, boolean apagado, boolean pausado) {
        horariosDeHoy.computeIfAbsent(id, p -> new ArrayList<>()).add(new HorarioResuelto(UUID.randomUUID(), titulo,
                LocalTime.of(7, 0), LocalTime.of(8, 0), propia, apagado, pausado, false, null));
    }

    /** La misma ventana con un día cambiado a otro estado, sin porcentaje (pausa, suspensión…). */
    public static VentanaDelSemaforo conDia(VentanaDelSemaforo base, LocalDate fecha, EstadoDiaSemaforo estado) {
        List<DiaDelSemaforo> dias = base.dias().stream()
                .map(d -> d.fecha().equals(fecha) ? DiaDelSemaforo.sinPorcentaje(fecha, estado) : d).toList();
        return new VentanaDelSemaforo(base.desde(), base.hasta(), base.porcentaje(), base.color(),
                base.diasConDatos(), base.cerrada(), dias);
    }

    private static Map<UserId, VentanaDelSemaforo> deLos(Collection<UserId> pedidos,
                                                         Map<UserId, VentanaDelSemaforo> ventanas) {
        Map<UserId, VentanaDelSemaforo> leidas = new LinkedHashMap<>();
        pedidos.forEach(id -> {
            if (ventanas.containsKey(id)) {
                leidas.put(id, ventanas.get(id));
            }
        });
        return leidas;
    }

    private TablaDelSemaforoFinder.FilaDeLaTabla fila(UserId id, VentanaDelSemaforo ventana) {
        UserSummary perfil = usuarios.get(id);
        if (ventana == null) {
            return new TablaDelSemaforoFinder.FilaDeLaTabla(id.value(), perfil.fullName(), perfil.avatarUrl(), null,
                    ColorSemaforo.SIN_DATOS, 0, List.of(), "NO_ACTIVADO");
        }
        return new TablaDelSemaforoFinder.FilaDeLaTabla(id.value(), perfil.fullName(), perfil.avatarUrl(),
                ventana.porcentaje(), ventana.color(), ventana.diasConDatos(), ventana.dias(),
                ventana.color() == ColorSemaforo.SIN_DATOS ? "SIN_NADA_PLANIFICADO" : null);
    }

    // ── Servicios reales ───────────────────────────────────────────────

    /** Esta ficha no lee el perfil de ningun mentor: el finder de fichas va vacio. */
    private final FichaDeMentorFinder fichas = new FichaDeMentorFinder() {
        @Override
        public List<FichaDeMentor> mentoresActivos() {
            return List.of();
        }

        @Override
        public Optional<FichaDeMentor> mentor(UserId usuarioId) {
            return Optional.empty();
        }
    };

    public AccesoDeLiderazgo acceso() {
        return new AccesoDeLiderazgo(usuariosFinder, fichas);
    }

    PersonasDelGrupo personas() {
        return new PersonasDelGrupo(acompanamiento, participacionFinder, usuariosFinder, pruebas);
    }

    SemaforoDeLaPersona semaforoDeLaPersona() {
        return new SemaforoDeLaPersona(semaforo);
    }

    PersonasDelPrograma programa() {
        return new PersonasDelPrograma(acompanamiento, participacionFinder, usuariosFinder, pruebas);
    }

    public PersonasDelProgramaService personasDelProgramaService() {
        return new PersonasDelProgramaService(acceso(), programa(), reloj);
    }

    public GrupoDelLiderService grupoService() {
        return new GrupoDelLiderService(acceso(), personas(), semaforoDeLaPersona(), tabla, usuariosFinder, reloj);
    }

    ProgramacionDeLaPersona programacionDeLaPersona() {
        return new ProgramacionDeLaPersona(horarioFinder, accionesFinder, reloj);
    }

    public SemanaDePersonaService semanaService() {
        return new SemanaDePersonaService(acceso(), personas(), programa(), new LecturaDeLaFicha(seguimiento, semaforoDeLaPersona(),
                accionesFinder, usuariosFinder, programacionDeLaPersona()), guardarConsulta, ids, reloj);
    }

    /** Los huecos de la persona sobre un cálculo de {@code mentoring} que responde siempre la misma semana de prueba. */
    public HuecosDePersonaService huecosService() {
        HuecosDeHorarioFinder calculo = (persona, lunes) -> SemanasDeHuecosDePrueba.respuestaDelContrato(
                lunes == null ? LocalDate.of(2026, 10, 19) : lunes, LocalDate.of(2026, 8, 17), false);
        return new HuecosDePersonaService(acceso(), personas(), programa(), calculo, guardarConsulta, ids, reloj);
    }

    /** Los cierres de la persona sobre una lectura de {@code rocks} que responde la semana pedida, vacía. */
    public CierresDePersonaService cierresService() {
        CierresDelDiaFinder lectura = (persona, lunes) -> new CierresDelDiaFinder.CierresDeLaSemana(
                lunes == null ? LocalDate.of(2026, 10, 19) : lunes, LocalDate.of(2026, 8, 17), List.of(),
                new CierresDelDiaFinder.ResumenDeCierres(0, 0, List.of()));
        return new CierresDePersonaService(acceso(), personas(), programa(), lectura, guardarConsulta, ids, reloj);
    }

    public RadarDePersonaService radarService() {
        return new RadarDePersonaService(acceso(), personas(), programa(), radarFinder, reloj);
    }
}
