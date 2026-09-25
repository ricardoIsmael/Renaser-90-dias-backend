package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.api.RocasDelAprendizFinder;
import com.renaser.os.rocks.application.ports.in.dashboard.ConsultarDashboardRocasUseCase;
import com.renaser.os.rocks.application.ports.in.dashboard.ConsultarDashboardRocasUseCase.DashboardRocas;
import com.renaser.os.rocks.application.ports.in.rocadiaria.ConsultarRocasDeMananaUseCase;
import com.renaser.os.rocks.application.ports.in.rocamensual.ConsultarObjetivoDelMesUseCase;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocadiaria.VentanaPlanificacionDiaria;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Implementa {@link RocasDelAprendizFinder} (2026-09-23) componiendo los casos de uso que ya sirven
 * a la app: el dashboard (rocas de hoy con Pareto, objetivos semanales, compuerta del plan diario),
 * las rocas de manana y el objetivo del mes. Aca no vive ninguna regla nueva: solo se traduce a
 * las proyecciones de {@code rocks.api}.
 *
 * <p>La unica cuenta propia es "que fecha es hoy" para ponerle fecha a la respuesta, y se hace
 * como manda la regla 02: {@code clock.now()} en la zona del participante, nunca la del servidor.
 */
@Service
class RocasDelAprendizService implements RocasDelAprendizFinder {

    private static final Comparator<RocaDelDia> POR_EJE_Y_POSICION = Comparator
            .comparing(RocaDelDia::eje).thenComparingInt(RocaDelDia::posicion);

    private final ConsultarDashboardRocasUseCase dashboardUseCase;
    private final ConsultarRocasDeMananaUseCase rocasDeMananaUseCase;
    private final ConsultarObjetivoDelMesUseCase objetivoDelMesUseCase;
    private final ConsultarProgresoParticipanteRocksPort progresoPort;
    private final Clock clock;

    RocasDelAprendizService(ConsultarDashboardRocasUseCase dashboardUseCase,
                            ConsultarRocasDeMananaUseCase rocasDeMananaUseCase,
                            ConsultarObjetivoDelMesUseCase objetivoDelMesUseCase,
                            ConsultarProgresoParticipanteRocksPort progresoPort, Clock clock) {
        this.dashboardUseCase = dashboardUseCase;
        this.rocasDeMananaUseCase = rocasDeMananaUseCase;
        this.objetivoDelMesUseCase = objetivoDelMesUseCase;
        this.progresoPort = progresoPort;
        this.clock = clock;
    }

    @Override
    public RocasDelDia deHoy(UserId aprendizId) {
        DashboardRocas tablero = dashboardUseCase.dashboard(aprendizId);
        List<RocaDiaria> deManana = rocasDeMananaUseCase.manana(aprendizId);
        Instant ahora = clock.now();
        ZoneId zona = zonaDe(aprendizId);
        List<RocaDelDia> rocas = tablero.rocasDeHoy().stream()
                .map(vista -> aRocaDelDia(vista.roca(), vista.bloqueada()))
                .sorted(POR_EJE_Y_POSICION).toList();
        return new RocasDelDia(ahora.atZone(zona).toLocalDate(), rocas,
                planificacion(tablero, deManana, ahora, zona));
    }

    @Override
    public RocasDelDia deManana(UserId aprendizId) {
        DashboardRocas tablero = dashboardUseCase.dashboard(aprendizId);
        List<RocaDiaria> deManana = rocasDeMananaUseCase.manana(aprendizId);
        Instant ahora = clock.now();
        ZoneId zona = zonaDe(aprendizId);
        List<RocaDelDia> rocas = deManana.stream().map(roca -> aRocaDelDia(roca, false))
                .sorted(POR_EJE_Y_POSICION).toList();
        return new RocasDelDia(ahora.atZone(zona).toLocalDate().plusDays(1), rocas,
                planificacion(tablero, deManana, ahora, zona));
    }

    @Override
    public RocasDeLaSemana deLaSemana(UserId aprendizId) {
        DashboardRocas tablero = dashboardUseCase.dashboard(aprendizId);
        Map<RocaMaestraId, EjeObjetivo> ejePorMaestra = tablero.rocasMaestras().stream()
                .collect(Collectors.toMap(RocaMaestra::id, RocaMaestra::eje, (a, b) -> a));
        List<RocaDeLaSemana> rocas = tablero.rocasSemanales().stream()
                .map(vista -> new RocaDeLaSemana(nombreDelEje(ejePorMaestra.get(vista.roca().rocaMaestraId())),
                        vista.roca().titulo(), vista.roca().obstaculo(), vista.roca().contingencia(),
                        vista.editable(), vista.roca().autoevaluacionFin() != null))
                .sorted(Comparator.comparing(RocaDeLaSemana::eje)).toList();
        return new RocasDeLaSemana(tablero.numeroSemana(), tablero.inicioSemana(), tablero.finSemana(), rocas);
    }

    @Override
    public List<ObjetivoDelMesDelEje> delMes(UserId aprendizId) {
        return objetivoDelMesUseCase.misObjetivosMensuales(aprendizId).stream()
                .flatMap(plan -> ProyeccionObjetivoDelMes.delMesEnCurso(plan).stream())
                .toList();
    }

    private PlanificacionDeManana planificacion(DashboardRocas tablero, List<RocaDiaria> deManana, Instant ahora,
                                                ZoneId zona) {
        return new PlanificacionDeManana(!deManana.isEmpty(), deManana.size(),
                VentanaPlanificacionDiaria.abierta(ahora, zona),
                LocalTime.of(VentanaPlanificacionDiaria.ABRE_HORA, 0), tablero.puedeCrearPlanDiario());
    }

    /** Se llama DESPUES del caso de uso, que ya rechazo al inexistente y al suspendido. */
    private ZoneId zonaDe(UserId aprendizId) {
        return progresoPort.deParticipante(aprendizId)
                .map(ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks::zona)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + aprendizId));
    }

    private static RocaDelDia aRocaDelDia(RocaDiaria roca, boolean bloqueada) {
        return new RocaDelDia(nombreDelEje(roca.eje()), roca.posicion(), roca.color().name(), roca.titulo(),
                roca.horaInicio(), roca.horaFin(), roca.completada(), bloqueada);
    }

    private static String nombreDelEje(EjeObjetivo eje) {
        return eje == null ? "SIN_EJE" : eje.name();
    }
}
