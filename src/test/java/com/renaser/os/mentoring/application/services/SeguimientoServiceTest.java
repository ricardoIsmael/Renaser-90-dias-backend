package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.evidence.api.EntregaDeEvidencia;
import com.renaser.os.evidence.api.EntregasPorRegistroFinder;
import com.renaser.os.evidence.api.EstadoValidacion;
import com.renaser.os.habits.api.EstadoObligacion;
import com.renaser.os.habits.api.ObligacionHabito;
import com.renaser.os.habits.api.ObligacionesHistoricasFinder;
import com.renaser.os.mentoring.application.ports.in.ConsultarEvaluacionPropiaUseCase.EvaluacionPropia;
import com.renaser.os.mentoring.application.ports.in.ConsultarSeguimientoSemanalUseCase.ConsultaSemana;
import com.renaser.os.mentoring.application.ports.in.ConsultarSeguimientoSemanalUseCase.ObligacionDelDia;
import com.renaser.os.mentoring.application.ports.in.ConsultarSeguimientoSemanalUseCase.SemanaDelAlumno;
import com.renaser.os.points.api.CalculoCumplimientoPort;
import com.renaser.os.points.api.EvaluacionCumplimiento;
import com.renaser.os.points.api.ObligacionEvidencia;
import com.renaser.os.points.api.VentanaEvaluacion;
import com.renaser.os.points.domain.model.cumplimiento.CalculoCumplimiento;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Seguimiento semanal y evaluación mensual, con dobles de las cinco APIs públicas que consume.
 *
 * <p>El calculo NO se dobla: se usa el motor real de {@code points}. Doblarlo dejaría sin probar
 * justamente lo que puede salir mal acá, que es cómo se arman sus entradas —qué obligación entra,
 * con qué ventana y con qué fecha de vencimiento.
 */
class SeguimientoServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** Miércoles 9 de setiembre de 2026, 15:00 UTC = 10:00 en Lima. */
    private static final Instant AHORA = Instant.parse("2026-09-09T15:00:00Z");

    private static final UUID MI_GRUPO = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID GRUPO_AJENO = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UserId MENTOR = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UserId ANA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000b1"));
    private static final UserId LUIS = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000b2"));
    private static final UserId AJENO = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000b9"));

    private final List<AcompanamientoFinder.TramoDeAcompanamiento> tramosDelMentor = new ArrayList<>();
    private final Map<UUID, List<AcompanamientoFinder.TramoDeAprendiz>> tramosPorGrupo = new LinkedHashMap<>();
    private final List<ObligacionHabito> obligaciones = new ArrayList<>();
    private final Map<UUID, EntregaDeEvidencia> entregas = new LinkedHashMap<>();

    private SeguimientoService servicio;

    @BeforeEach
    void preparar() {
        AcompanamientoFinder acompanamiento = new AcompanamientoFinder() {
            @Override
            public List<TramoDeAcompanamiento> tramosDeMentor(UserId mentorId, Instant desde, Instant hasta) {
                return mentorId.equals(MENTOR) ? tramosDelMentor : List.of();
            }

            @Override
            public List<UserId> aprendicesVigentes(UUID grupoId, Instant instante) {
                return tramosPorGrupo.getOrDefault(grupoId, List.of()).stream()
                        .filter(t -> !instante.isBefore(t.desde()) && (t.hasta() == null || instante.isBefore(t.hasta())))
                        .map(TramoDeAprendiz::aprendizId).toList();
            }

            @Override
            public List<TramoDeAprendiz> tramosDeAprendices(UUID grupoId, Instant desde, Instant hasta) {
                return tramosPorGrupo.getOrDefault(grupoId, List.of()).stream()
                        .filter(t -> t.hasta() == null || t.hasta().isAfter(desde))
                        .filter(t -> hasta == null || t.desde().isBefore(hasta))
                        .map(t -> new TramoDeAprendiz(t.aprendizId(),
                                t.desde().isAfter(desde) ? t.desde() : desde,
                                t.hasta() == null || (hasta != null && hasta.isBefore(t.hasta())) ? hasta : t.hasta()))
                        .toList();
            }
            @Override
            public List<UserId> integrantesVigentes(UUID grupoId, Instant instante) {
                // Aprendices + acompanantes. Estas pruebas solo pueblan aprendices.
                return aprendicesVigentes(grupoId, instante);
            }

            @Override
            public boolean esIntegranteVigente(UUID grupoId, UserId usuarioId, Instant instante) {
                return aprendicesVigentes(grupoId, instante).contains(usuarioId)
                        || acompanaVigente(usuarioId, grupoId, instante);
            }


            @Override
            public boolean acompanaVigente(UserId actorId, UUID grupoId, Instant instante) {
                return actorId.equals(MENTOR) && tramosDelMentor.stream().anyMatch(t -> t.grupoId().equals(grupoId));
            }

            @Override
            public Optional<GrupoBasico> grupo(UUID grupoId) {
                return Optional.of(new GrupoBasico(grupoId, "Grupo Amanecer", UUID.randomUUID(), "America/Lima"));
            }

            @Override
            public List<GrupoAcompanado> gruposConMentorVigente(Instant instante) {
                // Esta prueba no barre grupos: el seguimiento siempre parte de uno pedido.
                return List.of();
            }
        };

        ObligacionesHistoricasFinder obligacionesFinder = (participantes, desde, hasta) -> obligaciones.stream()
                .filter(o -> participantes.contains(o.participanteId()))
                .filter(o -> !o.fecha().isBefore(desde) && !o.fecha().isAfter(hasta))
                .toList();

        EntregasPorRegistroFinder entregasFinder = ids -> {
            Map<UUID, EntregaDeEvidencia> encontradas = new LinkedHashMap<>();
            ids.forEach(id -> {
                EntregaDeEvidencia e = entregas.get(id);
                if (e != null) {
                    encontradas.put(id, e);
                }
            });
            return encontradas;
        };

        // El motor real, no un doble.
        CalculoCumplimientoPort calculo = CalculoCumplimiento::evaluar;

        ParticipacionProgramaFinder participaciones = new ParticipacionProgramaFinder() {
            @Override
            public Optional<ParticipacionPrograma> deParticipante(UserId id) {
                return Optional.of(new ParticipacionPrograma(id, true, 24, LocalDate.of(2026, 8, 17), LIMA,
                        FasePrograma.PHASE_1_REBIRTH, MI_GRUPO, MENTOR, UserRole.TRAINEE, false, true));
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

        UserSummaryFinder usuarios = new UserSummaryFinder() {
            @Override
            public Optional<UserSummary> findById(UserId id) {
                return Optional.of(new UserSummary(id, "Ana Perez", null, UserRole.TRAINEE, UserStatus.ACTIVE));
            }

            @Override
            public Map<UserId, UserSummary> findByIds(java.util.Collection<UserId> ids) {
                return Map.of();
            }
            @Override
            public java.util.Optional<UserSummary> findByEmail(String email) {
                return java.util.Optional.empty();
            }

        };

        servicio = new SeguimientoService(acompanamiento, obligacionesFinder, entregasFinder, calculo,
                participaciones, usuarios, FixedClock.at(AHORA));
    }

    // ── armado ──────────────────────────────────────────────────────────────
    private void acompanaTodoElMes() {
        tramosDelMentor.add(new AcompanamientoFinder.TramoDeAcompanamiento(MI_GRUPO, "Grupo Amanecer",
                Instant.parse("2026-09-01T05:00:00Z"), null));
    }

    private void alumnoEnGrupo(UserId alumno, Instant desde, Instant hasta) {
        tramosPorGrupo.computeIfAbsent(MI_GRUPO, g -> new ArrayList<>())
                .add(new AcompanamientoFinder.TramoDeAprendiz(alumno, desde, hasta));
    }

    private ObligacionHabito obligacion(UserId alumno, LocalDate fecha, String titulo, EstadoObligacion estado,
                                         boolean requiereEvidencia) {
        ObligacionHabito o = new ObligacionHabito(UUID.randomUUID(), alumno, fecha, 24, titulo, estado,
                requiereEvidencia, false);
        obligaciones.add(o);
        return o;
    }

    private void entregada(ObligacionHabito o, LocalDate cuando, EstadoValidacion revision) {
        entregas.put(o.registroId(), new EntregaDeEvidencia(o.registroId(), UUID.randomUUID(),
                cuando.atTime(12, 0).atZone(LIMA).toInstant(), revision, 1));
    }

    // ── autorización ────────────────────────────────────────────────────────

    @Test
    @DisplayName("un grupo que no acompaña no devuelve nada")
    void grupoAjenoProhibido() {
        acompanaTodoElMes();

        assertThatThrownBy(() -> servicio.semanaDe(
                new ConsultaSemana(MENTOR, GRUPO_AJENO, ANA.value(), LocalDate.of(2026, 9, 7))))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("un alumno que no es de ese grupo tampoco, aunque el grupo si sea suyo (V12)")
    void alumnoAjenoProhibido() {
        acompanaTodoElMes();
        alumnoEnGrupo(ANA, Instant.parse("2026-09-01T05:00:00Z"), null);

        assertThatThrownBy(() -> servicio.semanaDe(
                new ConsultaSemana(MENTOR, MI_GRUPO, AJENO.value(), LocalDate.of(2026, 9, 7))))
                .isInstanceOf(NotAuthorizedException.class);
    }

    // ── semana ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("la semana trae los siete dias, aunque alguno venga vacio")
    void sieteDiasSiempre() {
        acompanaTodoElMes();
        alumnoEnGrupo(ANA, Instant.parse("2026-09-01T05:00:00Z"), null);
        obligacion(ANA, LocalDate.of(2026, 9, 9), "Caminar", EstadoObligacion.COMPLETADO, true);

        SemanaDelAlumno semana = servicio.semanaDe(
                new ConsultaSemana(MENTOR, MI_GRUPO, ANA.value(), LocalDate.of(2026, 9, 9)));

        assertThat(semana.dias()).hasSize(7);
        assertThat(semana.inicioDeSemana()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(semana.finDeSemana()).isEqualTo(LocalDate.of(2026, 9, 13));
    }

    @Test
    @DisplayName("distingue cumplido, pendiente, no cumplido y sin evidencia requerida")
    void estadosDiferenciados() {
        acompanaTodoElMes();
        alumnoEnGrupo(ANA, Instant.parse("2026-09-01T05:00:00Z"), null);
        ObligacionHabito conEntrega = obligacion(ANA, LocalDate.of(2026, 9, 8), "Caminar",
                EstadoObligacion.COMPLETADO, true);
        entregada(conEntrega, LocalDate.of(2026, 9, 8), EstadoValidacion.PENDIENTE);
        obligacion(ANA, LocalDate.of(2026, 9, 8), "Lectura", EstadoObligacion.PENDIENTE, false);
        obligacion(ANA, LocalDate.of(2026, 9, 7), "Ayuno", EstadoObligacion.FALLIDO, true);

        SemanaDelAlumno semana = servicio.semanaDe(
                new ConsultaSemana(MENTOR, MI_GRUPO, ANA.value(), LocalDate.of(2026, 9, 9)));

        List<ObligacionDelDia> martes = semana.dias().stream()
                .filter(d -> d.fecha().equals(LocalDate.of(2026, 9, 8))).findFirst().orElseThrow().obligaciones();

        assertThat(martes).extracting(ObligacionDelDia::titulo).containsExactlyInAnyOrder("Caminar", "Lectura");
        assertThat(martes).filteredOn(o -> o.titulo().equals("Caminar"))
                .allSatisfy(o -> {
                    assertThat(o.entrega()).isEqualTo("ENTREGADA");
                    assertThat(o.revision()).isEqualTo("PENDIENTE");
                });
        // Un habito que no pide archivo no aparece como "sin entregar": nunca debio uno.
        assertThat(martes).filteredOn(o -> o.titulo().equals("Lectura"))
                .allSatisfy(o -> {
                    assertThat(o.entrega()).isEqualTo("NO_REQUERIDA");
                    assertThat(o.revision()).isNull();
                });

        assertThat(semana.resumen().obligaciones()).isEqualTo(3);
        assertThat(semana.resumen().cumplidas()).isEqualTo(1);
        assertThat(semana.resumen().sinCumplir()).isEqualTo(1);
        assertThat(semana.resumen().pendientes()).isEqualTo(1);
    }

    @Test
    @DisplayName("una semana sin ninguna obligacion es SIN_DATOS, no 'no cumplio nada'")
    void semanaVaciaEsSinDatos() {
        acompanaTodoElMes();
        alumnoEnGrupo(ANA, Instant.parse("2026-09-01T05:00:00Z"), null);

        SemanaDelAlumno semana = servicio.semanaDe(
                new ConsultaSemana(MENTOR, MI_GRUPO, ANA.value(), LocalDate.of(2026, 9, 9)));

        assertThat(semana.cobertura()).isEqualTo("SIN_DATOS");
        assertThat(semana.resumen().obligaciones()).isZero();
    }

    @Test
    @DisplayName("sin weekStart se usa la semana en curso en la zona del alumno")
    void semanaEnCursoPorDefecto() {
        acompanaTodoElMes();
        alumnoEnGrupo(ANA, Instant.parse("2026-09-01T05:00:00Z"), null);

        SemanaDelAlumno semana = servicio.semanaDe(new ConsultaSemana(MENTOR, MI_GRUPO, ANA.value(), null));

        assertThat(semana.inicioDeSemana()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(semana.zona()).isEqualTo("America/Lima");
    }

    // ── evaluación ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("el ejemplo del plan de punta a punta: Ana 2/4 y Luis 3/3 dan 75 %")
    void evaluacionEjemploDelPlan() {
        acompanaTodoElMes();
        alumnoEnGrupo(ANA, Instant.parse("2026-09-01T05:00:00Z"), null);
        alumnoEnGrupo(LUIS, Instant.parse("2026-09-01T05:00:00Z"), null);

        for (int dia : new int[] {3, 5}) {
            entregada(obligacion(ANA, LocalDate.of(2026, 9, dia), "H", EstadoObligacion.COMPLETADO, true),
                    LocalDate.of(2026, 9, dia), EstadoValidacion.PENDIENTE);
        }
        obligacion(ANA, LocalDate.of(2026, 9, 7), "H", EstadoObligacion.FALLIDO, true);
        obligacion(ANA, LocalDate.of(2026, 9, 8), "H", EstadoObligacion.FALLIDO, true);
        for (int dia : new int[] {3, 5, 7}) {
            entregada(obligacion(LUIS, LocalDate.of(2026, 9, dia), "H", EstadoObligacion.COMPLETADO, true),
                    LocalDate.of(2026, 9, dia), EstadoValidacion.PENDIENTE);
        }

        EvaluacionPropia evaluacion = servicio.evaluacionDe(MENTOR, YearMonth.of(2026, 9));

        assertThat(evaluacion.porcentaje()).isEqualByComparingTo("75");
        assertThat(evaluacion.alumnosEvaluados()).isEqualTo(2);
        assertThat(evaluacion.estado()).isEqualTo("CALCULADA");
        assertThat(evaluacion.zona()).isEqualTo("America/Lima");
    }

    @Test
    @DisplayName("un alumno que entra a mitad de mes solo aporta lo suyo desde que entro")
    void alumnoQueEntraDespues() {
        acompanaTodoElMes();
        alumnoEnGrupo(ANA, Instant.parse("2026-09-01T05:00:00Z"), null);
        // Luis entra el 5. Las fechas van todas ANTES del reloj (9 de setiembre) para que las
        // obligaciones hayan vencido de verdad: una que todavia corre no es un incumplimiento.
        alumnoEnGrupo(LUIS, Instant.parse("2026-09-05T05:00:00Z"), null);

        entregada(obligacion(ANA, LocalDate.of(2026, 9, 3), "H", EstadoObligacion.COMPLETADO, true),
                LocalDate.of(2026, 9, 3), EstadoValidacion.PENDIENTE);
        // De Luis, una ANTES de entrar (no es del mentor) y una despues.
        obligacion(LUIS, LocalDate.of(2026, 9, 2), "H", EstadoObligacion.FALLIDO, true);
        entregada(obligacion(LUIS, LocalDate.of(2026, 9, 7), "H", EstadoObligacion.COMPLETADO, true),
                LocalDate.of(2026, 9, 7), EstadoValidacion.PENDIENTE);

        EvaluacionPropia evaluacion = servicio.evaluacionDe(MENTOR, YearMonth.of(2026, 9));

        // Ana 1/1 y Luis 1/1: la del 2 de setiembre no entra en la ventana de Luis.
        assertThat(evaluacion.esperadas()).isEqualTo(2);
        assertThat(evaluacion.porcentaje()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("una obligacion que TODAVIA no vencio no baja la nota")
    void obligacionDeHoyNoCuentaAun() {
        acompanaTodoElMes();
        alumnoEnGrupo(ANA, Instant.parse("2026-09-01T05:00:00Z"), null);
        // Cumplida y entregada el 3.
        entregada(obligacion(ANA, LocalDate.of(2026, 9, 3), "H", EstadoObligacion.COMPLETADO, true),
                LocalDate.of(2026, 9, 3), EstadoValidacion.PENDIENTE);
        // La de HOY (el reloj esta en el 9) todavia esta corriendo: no es un incumplimiento.
        obligacion(ANA, LocalDate.of(2026, 9, 9), "H", EstadoObligacion.PENDIENTE, true);
        // Y la de mañana ni siquiera existe todavia.
        obligacion(ANA, LocalDate.of(2026, 9, 20), "H", EstadoObligacion.PENDIENTE, true);

        EvaluacionPropia evaluacion = servicio.evaluacionDe(MENTOR, YearMonth.of(2026, 9));

        // Sin el corte daba 1/3 = 33 %: le descontaba dias que la persona todavia tiene por delante.
        assertThat(evaluacion.esperadas()).isEqualTo(1);
        assertThat(evaluacion.porcentaje()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("sin tramos en ese mes la evaluacion es SIN_HISTORIAL, no 0 %")
    void mesSinTramos() {
        EvaluacionPropia evaluacion = servicio.evaluacionDe(MENTOR, YearMonth.of(2026, 7));

        assertThat(evaluacion.estado()).isEqualTo("SIN_HISTORIAL");
        assertThat(evaluacion.porcentaje()).isNull();
    }

    @Test
    @DisplayName("los habitos que no piden evidencia no entran al denominador")
    void habitosSinEvidenciaNoCuentan() {
        acompanaTodoElMes();
        alumnoEnGrupo(ANA, Instant.parse("2026-09-01T05:00:00Z"), null);
        entregada(obligacion(ANA, LocalDate.of(2026, 9, 3), "Con archivo", EstadoObligacion.COMPLETADO, true),
                LocalDate.of(2026, 9, 3), EstadoValidacion.PENDIENTE);
        obligacion(ANA, LocalDate.of(2026, 9, 4), "Sin archivo", EstadoObligacion.FALLIDO, false);

        EvaluacionPropia evaluacion = servicio.evaluacionDe(MENTOR, YearMonth.of(2026, 9));

        assertThat(evaluacion.esperadas()).isEqualTo(1);
        assertThat(evaluacion.porcentaje()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("la verificacion se informa aparte y no cambia el porcentaje")
    void verificacionAparte() {
        acompanaTodoElMes();
        alumnoEnGrupo(ANA, Instant.parse("2026-09-01T05:00:00Z"), null);
        entregada(obligacion(ANA, LocalDate.of(2026, 9, 3), "H", EstadoObligacion.COMPLETADO, true),
                LocalDate.of(2026, 9, 3), EstadoValidacion.VALIDA);
        entregada(obligacion(ANA, LocalDate.of(2026, 9, 4), "H", EstadoObligacion.COMPLETADO, true),
                LocalDate.of(2026, 9, 4), EstadoValidacion.RECHAZADA);

        EvaluacionPropia evaluacion = servicio.evaluacionDe(MENTOR, YearMonth.of(2026, 9));

        // Rechazada sigue siendo entregada: la nota mide entrega, no aprobacion (D-03).
        assertThat(evaluacion.porcentaje()).isEqualByComparingTo("100");
        assertThat(evaluacion.verificadas()).isEqualTo(1);
    }

    @Test
    @DisplayName("la evaluacion propia no lleva nombres de alumnos (P-07)")
    void evaluacionSinNombres() {
        acompanaTodoElMes();
        alumnoEnGrupo(ANA, Instant.parse("2026-09-01T05:00:00Z"), null);
        entregada(obligacion(ANA, LocalDate.of(2026, 9, 3), "H", EstadoObligacion.COMPLETADO, true),
                LocalDate.of(2026, 9, 3), EstadoValidacion.PENDIENTE);

        EvaluacionPropia evaluacion = servicio.evaluacionDe(MENTOR, YearMonth.of(2026, 9));

        assertThat(evaluacion.toString()).doesNotContain("Ana", ANA.value().toString());
        assertThat(evaluacion.tramos()).extracting("grupoNombre").containsOnly("Grupo Amanecer");
    }
}
