package com.renaser.os.rocks.application.services;

import com.renaser.os.shared.GuardDeRol;
import com.renaser.os.rocks.application.ports.in.dashboard.ConsultarDashboardRocasUseCase.DashboardRocas;
import com.renaser.os.rocks.application.ports.in.rocadiaria.ConsultarRocasDeHoyUseCase;
import com.renaser.os.rocks.application.ports.in.rocamaestra.ConsultarRocasMaestrasUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.ConsultarRocasSemanalesUseCase;
import com.renaser.os.rocks.application.ports.out.coherencia.CargarConteoDiarioRocasPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.rocks.domain.model.coherencia.DiaRocas;
import com.renaser.os.rocks.domain.model.dashboard.EstadoRitmoRocas;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocasemanal.SemanaPrograma;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardRocasServiceTest {

    // Lunes 20:05 UTC, dia de programa >= 31 y hora >= 20 -> caso de borde de Ley II ejercitado.
    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T20:05:00Z"));
    /**
     * Coherente con el reloj a proposito: el reloj marca 2026-08-24 y los tests usan
     * diaPrograma 10, asi que el dia 1 tiene que ser 9 dias antes. Con la fecha anterior
     * (2026-01-05) el programa habia terminado el 2026-04-04 — cuatro meses antes de "hoy" —
     * y el recorte contra el fin de programa daba un rango invertido con grilla vacia.
     */
    private static final LocalDate FECHA_INICIO = LocalDate.of(2026, 8, 15);

    @Mock
    private ConsultarProgresoParticipanteRocksPort progresoPort;
    @Mock
    private ConsultarRocasMaestrasUseCase rocasMaestrasUseCase;
    @Mock
    private ConsultarRocasSemanalesUseCase rocasSemanalesUseCase;
    @Mock
    private ConsultarRocasDeHoyUseCase rocasDeHoyUseCase;
    @Mock
    private LoadRocaDiariaPort loadRocaDiariaPort;
    @Mock
    private CargarConteoDiarioRocasPort conteoPort;

    private DashboardRocasService service;
    private UserId actorId;

    @BeforeEach
    void setUp() {
        service = new DashboardRocasService(progresoPort, rocasMaestrasUseCase, rocasSemanalesUseCase,
                rocasDeHoyUseCase, loadRocaDiariaPort, conteoPort, CLOCK);
        actorId = UserId.of(UUID.randomUUID());
        lenient().when(rocasSemanalesUseCase.misRocasSemanales(eq(actorId), anyInt())).thenReturn(List.of());
        lenient().when(rocasDeHoyUseCase.hoy(actorId)).thenReturn(List.of());
        lenient().when(conteoPort.conteoDiarioPorParticipante(any(), any(), any())).thenReturn(Map.of());
    }

    /**
     * {@code fecha_inicio} coherente con el dia pedido (regla 03): la semana se ancla en el primer dia
     * efectivo (D-192, D-203), y un fixture con dia 40 y 10 dias transcurridos probaria otra cosa.
     */
    private static ProgresoParticipanteRocks progreso(int diaPrograma, RolParticipante rol, boolean suspendido) {
        return new ProgresoParticipanteRocks(diaPrograma, inicioParaDia(diaPrograma), ZoneOffset.UTC, rol,
                suspendido, false);
    }

    /** Igual que {@link #progreso} pero con el programa ANDANDO: el caso de E-169. */
    private static ProgresoParticipanteRocks progresoActivado(int diaPrograma, RolParticipante rol, boolean suspendido) {
        return new ProgresoParticipanteRocks(diaPrograma, inicioParaDia(diaPrograma), ZoneOffset.UTC, rol,
                suspendido, true);
    }

    private static LocalDate inicioParaDia(int diaPrograma) {
        return CLOCK.now().atZone(ZoneOffset.UTC).toLocalDate().minusDays(diaPrograma - 1L);
    }

    private static List<RocaMaestra> tresMaestras(UserId participante) {
        Instant ahora = CLOCK.now();
        // meta = null: el dashboard no muestra la parte medible del objetivo (V35), asi que
        // estas rocas son cualitativas a proposito — es el caso mas pobre que tiene que andar.
        return List.of(
                new RocaMaestra(RocaMaestraId.of(UUID.randomUUID()), participante, EjeObjetivo.CUERPO,
                        "objetivo cuerpo", null, ahora, ahora),
                new RocaMaestra(RocaMaestraId.of(UUID.randomUUID()), participante, EjeObjetivo.TRABAJO,
                        "objetivo trabajo", null, ahora, ahora),
                new RocaMaestra(RocaMaestraId.of(UUID.randomUUID()), participante, EjeObjetivo.RELACIONES,
                        "objetivo relaciones", null, ahora, ahora));
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: rol sin permiso (no TRAINEE) -> NotAuthorizedException")
    void rolSinPermisoRechazado() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(40, RolParticipante.MENTOR, false)));

        assertThatThrownBy(() -> service.dashboard(actorId)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: actor SUSPENDIDO -> NotAuthorizedException")
    void actorSuspendidoRechazado() {
        when(progresoPort.deParticipante(actorId))
                .thenReturn(Optional.of(progreso(40, RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service.dashboard(actorId)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("programa no iniciado: colecciones vacias, ritmo OK, sin compuertas de planificacion abiertas")
    void programaNoIniciadoDevuelveContratoVacio() {
        LocalDate fechaFutura = CLOCK.now().atZone(ZoneOffset.UTC).toLocalDate().plusDays(10);
        ProgresoParticipanteRocks progreso = new ProgresoParticipanteRocks(0, fechaFutura, ZoneOffset.UTC,
                RolParticipante.TRAINEE, false, false);
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso));
        when(rocasMaestrasUseCase.misRocasMaestras(actorId)).thenReturn(tresMaestras(actorId));

        DashboardRocas dashboard = service.dashboard(actorId);

        assertThat(dashboard.ritmo()).isEqualTo(EstadoRitmoRocas.OK);
        assertThat(dashboard.grillaSemanal()).isEmpty();
        assertThat(dashboard.rocasSemanales()).isEmpty();
        assertThat(dashboard.rocasDeHoy()).isEmpty();
        assertThat(dashboard.puedeCrearPlanDiario()).isFalse();
        assertThat(dashboard.puedeCrearPlanSemanal()).isFalse();
        assertThat(dashboard.planificacionBloqueada()).isFalse();
        assertThat(dashboard.rocasDesbloqueadas()).isTrue(); // las 3 maestras ya existen (onboarding)
        assertThat(dashboard.fechaInicioPrograma()).isEqualTo(fechaFutura);
        assertThat(dashboard.inicioSemana()).isEqualTo(fechaFutura);
    }

    /**
     * D-201: sin el programa activado, la {@code fecha_inicio} de {@code users} es la provisional del alta y el
     * adaptador ya no la manda como Dia 1. La semana 1 va sin fechas: ni rango ni {@code fechaInicioPrograma}
     * inventados. Produccion y D-192 la anclaban en esa fecha provisional (o en manana, si ya habia pasado).
     */
    @Test
    @DisplayName("D-203: sin Dia 1 elegido, la semana 1 va sin fechas y sin fecha de inicio del programa")
    void sinDiaUnoLaSemanaUnoVaSinFechas() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(new ProgresoParticipanteRocks(0, null,
                ZoneOffset.UTC, RolParticipante.TRAINEE, false, false)));
        when(rocasMaestrasUseCase.misRocasMaestras(actorId)).thenReturn(tresMaestras(actorId));

        DashboardRocas dashboard = service.dashboard(actorId);

        assertThat(dashboard.numeroSemana()).isEqualTo(1);
        assertThat(dashboard.inicioSemana()).isNull();
        assertThat(dashboard.finSemana()).isNull();
        assertThat(dashboard.fechaInicioPrograma()).isNull();
        assertThat(dashboard.grillaSemanal()).isEmpty();
        assertThat(dashboard.puedeCrearPlanDiario()).isFalse();
        assertThat(dashboard.rocasDesbloqueadas()).isTrue();
    }

    @Test
    @DisplayName("Ley II: dia >= 31, hora >= 20 y menos de 3 rocas para manana -> planificacionBloqueada")
    void leyDosBloqueaPlanificacion() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(40, RolParticipante.TRAINEE, false)));
        when(rocasMaestrasUseCase.misRocasMaestras(actorId)).thenReturn(tresMaestras(actorId));
        when(loadRocaDiariaPort.contarDeParticipanteYFecha(eq(actorId), any())).thenReturn(1);

        DashboardRocas dashboard = service.dashboard(actorId);

        assertThat(dashboard.planificacionBloqueada()).isTrue();
    }

    @Test
    @DisplayName("con las 3 rocas de manana ya planificadas, Ley II no bloquea")
    void leyDosNoBloqueaConTresRocasDeManana() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(40, RolParticipante.TRAINEE, false)));
        when(rocasMaestrasUseCase.misRocasMaestras(actorId)).thenReturn(tresMaestras(actorId));
        when(loadRocaDiariaPort.contarDeParticipanteYFecha(eq(actorId), any())).thenReturn(3);

        DashboardRocas dashboard = service.dashboard(actorId);

        assertThat(dashboard.planificacionBloqueada()).isFalse();
    }

    @Test
    @DisplayName("rocasDesbloqueadas es false con menos de 3 Rocas Maestras (onboarding incompleto)")
    void rocasBloqueadasSinLasTresMaestras() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(10, RolParticipante.TRAINEE, false)));
        when(rocasMaestrasUseCase.misRocasMaestras(actorId))
                .thenReturn(List.of(tresMaestras(actorId).get(0)));

        DashboardRocas dashboard = service.dashboard(actorId);

        assertThat(dashboard.rocasDesbloqueadas()).isFalse();
        assertThat(dashboard.puedeCrearPlanSemanal()).isFalse();
        // dia 10 < 31 -> Ley II nunca consulta el conteo de rocas de manana
        assertThat(dashboard.planificacionBloqueada()).isFalse();
    }

    @Test
    @DisplayName("ritmo: cuenta dias distintos con al menos una roca completada en los ultimos 7 dias")
    void ritmoCuentaDiasCompletadosDeLaVentanaDeSieteDias() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(10, RolParticipante.TRAINEE, false)));
        when(rocasMaestrasUseCase.misRocasMaestras(actorId)).thenReturn(tresMaestras(actorId));
        LocalDate hoy = CLOCK.now().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate desde7 = hoy.minusDays(7);
        LocalDate hasta7 = hoy.minusDays(1);
        List<DiaRocas> ultimos7 = List.of(
                new DiaRocas(hasta7, 3, 2),           // completado (>0)
                new DiaRocas(hasta7.minusDays(1), 3, 0), // planificado pero sin completar
                new DiaRocas(hasta7.minusDays(2), 2, 1)); // completado
        when(conteoPort.conteoDiarioPorParticipante(List.of(actorId), desde7, hasta7))
                .thenReturn(Map.of(actorId, ultimos7));

        DashboardRocas dashboard = service.dashboard(actorId);

        assertThat(dashboard.diasCompletadosUltimos7()).isEqualTo(2);
        assertThat(dashboard.ritmo()).isEqualTo(EstadoRitmoRocas.CRITICO); // 2 < 3
    }

    @Test
    @DisplayName("grillaSemanal: dias futuros viajan con completadas/total en null")
    void grillaSemanalOcultaDiasFuturos() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(10, RolParticipante.TRAINEE, false)));
        when(rocasMaestrasUseCase.misRocasMaestras(actorId)).thenReturn(tresMaestras(actorId));
        LocalDate hoy = CLOCK.now().atZone(ZoneOffset.UTC).toLocalDate();
        SemanaPrograma semanas = SemanaPrograma.desde(FECHA_INICIO);
        var limites = semanas.limites(semanas.numeroSemanaParaFecha(hoy));
        when(conteoPort.conteoDiarioPorParticipante(List.of(actorId), limites.inicio(), limites.fin()))
                .thenReturn(Map.of(actorId, List.of(new DiaRocas(hoy, 3, 1))));

        DashboardRocas dashboard = service.dashboard(actorId);

        var diaDeHoy = dashboard.grillaSemanal().stream().filter(d -> d.fecha().equals(hoy)).findFirst().orElseThrow();
        assertThat(diaDeHoy.completadas()).isEqualTo(1);
        assertThat(diaDeHoy.total()).isEqualTo(3);
        assertThat(diaDeHoy.esHoy()).isTrue();

        // hoy es lunes 24, primer dia de la semana 3 (lunes 24 a domingo 30) -> el resto es futuro.
        var futuro = dashboard.grillaSemanal().stream().filter(d -> d.fecha().isAfter(hoy)).findFirst()
                .orElseThrow(() -> new AssertionError("se esperaba al menos un dia futuro en la grilla"));
        assertThat(futuro.completadas()).isNull();
        assertThat(futuro.total()).isNull();
    }

    /**
     * E-169: un MENTOR que activo su seguimiento personal opera su programa como cualquiera.
     *
     * <p>Es el reverso exacto del caso de rechazo, y se construye sobre su mismo fixture para que
     * la unica diferencia sea el dato que importa. Antes de esto,
     * {@code POST /api/v1/mentor/activate-tracking} inscribia al staff y despues el guard lo
     * echaba: la inscripcion estaba construida y el uso prohibido.
     */
    @Test
    @DisplayName("E-169: un MENTOR con su programa ACTIVADO ya no lo rechaza el guard de rol")
    void staffConProgramaActivadoOperaSuPrograma() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progresoActivado(40, RolParticipante.MENTOR, false)));

        GuardDeRol.noRechaza(() -> service.dashboard(actorId), "Solo un aprendiz opera sus propias rocas");
    }

    /**
     * Hoy es el lunes 2026-08-24, dia 10 de calendario (empezo el sabado 15) ajustado al 35: el dia 1
     * efectivo es el martes 21 de julio. Produccion contaba desde {@code fecha_inicio} sin el ajuste y daba
     * la semana 3 ("dia 35, semana 3", E-320); D-192 daba la 5 (dias 29 a 35, del martes 18 a hoy).
     */
    @Test
    @DisplayName("D-203: tras adelantar el dia (10 de calendario -> 35) la semana acompana el ajuste y va de lunes a domingo")
    void trasAdelantarElDiaLaSemanaSigueAlDia() {
        LocalDate hoy = CLOCK.now().atZone(ZoneOffset.UTC).toLocalDate();
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(new ProgresoParticipanteRocks(35,
                hoy.minusDays(9), ZoneOffset.UTC, RolParticipante.TRAINEE, false, true)));
        when(rocasMaestrasUseCase.misRocasMaestras(actorId)).thenReturn(tresMaestras(actorId));

        DashboardRocas dashboard = service.dashboard(actorId);

        assertThat(dashboard.numeroSemana()).isEqualTo(6);
        assertThat(dashboard.inicioSemana()).isEqualTo(hoy);
        assertThat(dashboard.finSemana()).isEqualTo(hoy.plusDays(6));
        assertThat(dashboard.finSemana().getDayOfWeek()).isEqualTo(java.time.DayOfWeek.SUNDAY);
    }

    /**
     * Inicio el miercoles 2026-05-27: el dia 90 es hoy, lunes 24 de agosto, que produccion contaba como
     * semana 14 (E-320). La 13 va del lunes 17 a hoy: ocho dias. D-192 la hacia empezar el dia 85.
     */
    @Test
    @DisplayName("D-203: dia 90 con inicio en miercoles -> semana 13, del lunes al dia 90 (ocho dias), nunca la 14")
    void elDiaNoventaEsSemanaTrece() {
        LocalDate hoy = CLOCK.now().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate inicio = hoy.minusDays(89);
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(new ProgresoParticipanteRocks(90,
                inicio, ZoneOffset.UTC, RolParticipante.TRAINEE, false, true, hoy)));
        when(rocasMaestrasUseCase.misRocasMaestras(actorId)).thenReturn(tresMaestras(actorId));

        DashboardRocas dashboard = service.dashboard(actorId);

        assertThat(inicio.getDayOfWeek()).isEqualTo(java.time.DayOfWeek.WEDNESDAY);
        assertThat(dashboard.numeroSemana()).isEqualTo(13);
        assertThat(dashboard.inicioSemana()).isEqualTo(inicio.plusDays(82));
        assertThat(dashboard.inicioSemana().getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
        assertThat(dashboard.finSemana()).isEqualTo(hoy);
        assertThat(dashboard.grillaSemanal()).hasSize(8);
        assertThat(dashboard.puedeCrearPlanDiario()).isFalse();
    }

    /**
     * Graduado hace diez dias, sin ajuste: empezo el domingo 2026-05-17 y su dia 90 fue el viernes 14 de
     * agosto. {@code users.api} ya da el dia acotado a 90; con la fecha real del dia 90 (la trae el
     * adaptador) ve su semana 13 de verdad, del lunes 3 al viernes 14. Con el ancla de D-192
     * ({@code hoy − 89}) veia una "semana 13" que se corria un dia por dia; produccion, la 16 y la grilla
     * vacia.
     */
    @Test
    @DisplayName("D-203: un graduado ve su ultima semana real, no una que se corre con el reloj")
    void unGraduadoVeSuUltimaSemana() {
        LocalDate hoy = CLOCK.now().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate inicio = hoy.minusDays(99);
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(new ProgresoParticipanteRocks(90,
                inicio, ZoneOffset.UTC, RolParticipante.TRAINEE, false, true, inicio.plusDays(89))));
        when(rocasMaestrasUseCase.misRocasMaestras(actorId)).thenReturn(tresMaestras(actorId));

        DashboardRocas dashboard = service.dashboard(actorId);

        assertThat(dashboard.numeroSemana()).isEqualTo(13);
        assertThat(dashboard.inicioSemana()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(dashboard.finSemana()).isEqualTo(LocalDate.of(2026, 8, 14));
        assertThat(dashboard.grillaSemanal()).hasSize(12);
        assertThat(dashboard.puedeCrearPlanDiario()).isFalse();
    }

    /**
     * Regla 02: el lunes 14 a las 03:00 UTC en Lima todavia es el domingo 13 a las 22:00. Con la fecha del
     * servidor seria la semana 2 (lunes 14 a domingo 20); en Lima es el dia 6, el domingo que cierra la
     * semana 1 (martes 8 a domingo 13).
     */
    @Test
    @DisplayName("D-203: de madrugada UTC la semana es la del dia LOCAL del participante")
    void deMadrugadaUtcMandaElDiaLocal() {
        FixedClock madrugada = FixedClock.at(Instant.parse("2026-09-14T03:00:00Z"));
        DashboardRocasService enMadrugada = new DashboardRocasService(progresoPort, rocasMaestrasUseCase,
                rocasSemanalesUseCase, rocasDeHoyUseCase, loadRocaDiariaPort, conteoPort, madrugada);
        LocalDate inicio = LocalDate.of(2026, 9, 8);
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(new ProgresoParticipanteRocks(6, inicio,
                java.time.ZoneId.of("America/Lima"), RolParticipante.TRAINEE, false, true)));
        when(rocasMaestrasUseCase.misRocasMaestras(actorId)).thenReturn(tresMaestras(actorId));

        DashboardRocas dashboard = enMadrugada.dashboard(actorId);

        assertThat(dashboard.numeroSemana()).isEqualTo(1);
        assertThat(dashboard.inicioSemana()).isEqualTo(inicio);
        assertThat(dashboard.finSemana()).isEqualTo(LocalDate.of(2026, 9, 13));
    }
}
