package com.renaser.os.rocks.infrastructure.adapter.in.rest.dashboard;

import com.renaser.os.rocks.application.ports.in.dashboard.ConsultarDashboardRocasUseCase.DashboardRocas;
import com.renaser.os.rocks.application.ports.in.dashboard.ConsultarDashboardRocasUseCase.RocaSemanalVista;
import com.renaser.os.rocks.domain.model.dashboard.DiaGrillaSemanal;
import com.renaser.os.rocks.infrastructure.adapter.in.rest.rocadiaria.RocaDiariaResponse;
import com.renaser.os.rocks.infrastructure.adapter.in.rest.rocamaestra.RocaMaestraResponse;
import com.renaser.os.rocks.infrastructure.adapter.in.rest.rocasemanal.RocaSemanalResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Proyección de salida del dashboard agregado (Hueco #15, {@code GET /api/v1/rocks}).
 * {@code coherenceScore} del repo viejo no viaja acá — ver javadoc de
 * {@code ConsultarDashboardRocasUseCase.DashboardRocas}.
 */
public record DashboardRocasResponse(int diaPrograma, int numeroSemana, LocalDate inicioSemana, LocalDate finSemana,
                                      List<RocaMaestraResponse> rocasMaestras, boolean rocasDesbloqueadas,
                                      boolean tieneRocaSemanal, List<RocaSemanalVistaResponse> rocasSemanales,
                                      List<DiaGrillaResponse> grillaSemanal, String ritmo,
                                      int diasCompletadosUltimos7, int progresoSemanalPct,
                                      boolean planificacionBloqueada, boolean puedeCrearPlanDiario,
                                      boolean puedeCrearPlanSemanal, boolean planificacionSemanalTardia,
                                      List<RocaDiariaResponse> rocasDeHoy, LocalDate fechaInicioPrograma) {

    public static DashboardRocasResponse from(DashboardRocas d) {
        return new DashboardRocasResponse(d.diaPrograma(), d.numeroSemana(), d.inicioSemana(), d.finSemana(),
                d.rocasMaestras().stream().map(RocaMaestraResponse::from).toList(), d.rocasDesbloqueadas(),
                d.tieneRocaSemanal(), d.rocasSemanales().stream().map(RocaSemanalVistaResponse::from).toList(),
                d.grillaSemanal().stream().map(DiaGrillaResponse::from).toList(), d.ritmo().name(),
                d.diasCompletadosUltimos7(), d.progresoSemanalPct(), d.planificacionBloqueada(),
                d.puedeCrearPlanDiario(), d.puedeCrearPlanSemanal(), d.planificacionSemanalTardia(),
                d.rocasDeHoy().stream().map(RocaDiariaResponse::from).toList(), d.fechaInicioPrograma());
    }

    public record RocaSemanalVistaResponse(RocaSemanalResponse roca, boolean editable) {

        static RocaSemanalVistaResponse from(RocaSemanalVista v) {
            return new RocaSemanalVistaResponse(RocaSemanalResponse.from(v.roca()), v.editable());
        }
    }

    public record DiaGrillaResponse(LocalDate fecha, String diaSemana, Integer completadas, Integer total,
                                     boolean esHoy) {

        static DiaGrillaResponse from(DiaGrillaSemanal d) {
            return new DiaGrillaResponse(d.fecha(), d.diaSemana().name(), d.completadas(), d.total(), d.esHoy());
        }
    }
}
