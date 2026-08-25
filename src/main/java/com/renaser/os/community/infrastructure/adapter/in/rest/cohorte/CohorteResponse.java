package com.renaser.os.community.infrastructure.adapter.in.rest.cohorte;

import com.renaser.os.community.application.ports.in.cohorte.ConsultarCohortesUseCase.CohorteResumen;
import com.renaser.os.community.domain.model.cohorte.EstadoCohorte;

/** {@code status} en ingles (PLANNED/ACTIVE/COMPLETED) — la app publicada nunca vio
 * `estado_cohorte` en espanol; la traduccion (D-36) vive solo aca. */
public record CohorteResponse(String id, String name, String startDate, String endDate, String status,
                               int cellCount) {

    public static CohorteResponse from(CohorteResumen resumen) {
        var c = resumen.cohorte();
        return new CohorteResponse(c.id().toString(), c.nombre(), c.fechaInicio().toString(),
                c.fechaFin() != null ? c.fechaFin().toString() : null, toWireEstado(c.estado()),
                resumen.cantidadCelulas());
    }

    private static String toWireEstado(EstadoCohorte estado) {
        return switch (estado) {
            case PLANIFICADA -> "PLANNED";
            case ACTIVA -> "ACTIVE";
            case COMPLETADA -> "COMPLETED";
        };
    }
}
