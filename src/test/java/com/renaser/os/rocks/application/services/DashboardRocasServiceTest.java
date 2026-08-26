package com.renaser.os.rocks.application.services;

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

    private static ProgresoParticipanteRocks progreso(int diaPrograma, RolParticipante rol, boolean suspendido) {
        return new ProgresoParticipanteRocks(diaPrograma, FECHA_INICIO, ZoneOffset.UTC, rol, suspendido);
    }

    private static List<RocaMaestra> tresMaestras(UserId participante) {
        Instant ahora = CLOCK.now();
        return List.of(
                new RocaMaestra(RocaMaestraId.newId(), participante, EjeObjetivo.CUERPO, "objetivo cuerpo", ahora),
                new RocaMaestra(RocaMaestraId.newId(), participante, EjeObjetivo.TRABAJO, "objetivo trabajo", ahora),
                new RocaMaestra(RocaMaestraId.newId(), participante, EjeObjetivo.RELACIONES, "objetivo relaciones",
                        ahora));
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
                RolParticipante.TRAINEE, false);
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
        int numeroSemana = SemanaPrograma.numeroSemanaParaFecha(FECHA_INICIO, hoy);
        var limites = SemanaPrograma.limites(FECHA_INICIO, numeroSemana);
        when(conteoPort.conteoDiarioPorParticipante(List.of(actorId), limites.inicio(), limites.fin()))
                .thenReturn(Map.of(actorId, List.of(new DiaRocas(hoy, 3, 1))));

        DashboardRocas dashboard = service.dashboard(actorId);

        var diaDeHoy = dashboard.grillaSemanal().stream().filter(d -> d.fecha().equals(hoy)).findFirst().orElseThrow();
        assertThat(diaDeHoy.completadas()).isEqualTo(1);
        assertThat(diaDeHoy.total()).isEqualTo(3);
        assertThat(diaDeHoy.esHoy()).isTrue();

        // hoy es lunes (inicio de la semana calendario) -> el resto de la semana es futuro.
        var futuro = dashboard.grillaSemanal().stream().filter(d -> d.fecha().isAfter(hoy)).findFirst()
                .orElseThrow(() -> new AssertionError("se esperaba al menos un dia futuro en la grilla"));
        assertThat(futuro.completadas()).isNull();
        assertThat(futuro.total()).isNull();
    }
}
