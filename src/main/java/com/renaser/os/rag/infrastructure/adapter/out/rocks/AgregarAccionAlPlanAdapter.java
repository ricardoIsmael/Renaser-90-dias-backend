package com.renaser.os.rag.infrastructure.adapter.out.rocks;

import com.renaser.os.rag.application.ports.out.rocas.AgregarAccionAlPlanPort;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort.AccionDelPlan;
import com.renaser.os.rocks.api.AgregarAccionAlDiaPort;
import com.renaser.os.rocks.api.AgregarAccionAlDiaPort.AccionNueva;
import com.renaser.os.rocks.api.AgregarAccionAlDiaPort.ResultadoAgregado;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Implementa {@link AgregarAccionAlPlanPort} delegando en {@code rocks.api.AgregarAccionAlDiaPort}
 * (D-41, D-177). El puntaje de impacto y "delegable" van como los manda la app y como los manda
 * {@link PlanificarRocasAdapter}: el punto medio de la escala y {@code false}. La posicion no se
 * manda: la decide {@code rocks}.
 */
@Component
class AgregarAccionAlPlanAdapter implements AgregarAccionAlPlanPort {

    private final AgregarAccionAlDiaPort agregarAccion;

    AgregarAccionAlPlanAdapter(AgregarAccionAlDiaPort agregarAccion) {
        this.agregarAccion = agregarAccion;
    }

    @Override
    public Resultado agregar(UserId aprendizId, LocalDate fecha, AccionDelPlan accion) {
        AccionNueva nueva = new AccionNueva(accion.eje(), accion.titulo(),
                PlanificarRocasAdapter.PUNTAJE_IMPACTO_COMO_LA_APP, false, accion.inicio(), accion.fin());
        return switch (agregarAccion.agregar(aprendizId, fecha, nueva)) {
            case ResultadoAgregado.Agregada agregada ->
                    new Resultado.Agregada(agregada.eje(), agregada.posicion(), agregada.color());
            case ResultadoAgregado.Rechazado rechazado ->
                    new Resultado.Rechazado(Motivo.valueOf(rechazado.motivo().name()));
        };
    }
}
