package com.renaser.os.rocks.infrastructure.adapter.in.rest.cierre;

import com.renaser.os.rocks.api.CierresDelDiaFinder.AccionDelCierre;
import com.renaser.os.rocks.application.ports.in.cierre.ConsultarCierreDelDiaUseCase.AvanceDeLaSemana;
import com.renaser.os.rocks.application.ports.in.cierre.ConsultarCierreDelDiaUseCase.VistaDelCierre;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** El cierre de un día de la propia persona (contrato de la spec 017). A mano, sin mapper: un campo nuevo no se cuela solo. */
public record CierreDelDiaResponse(LocalDate fecha, String estado, boolean puedeCerrar, Instant cerradoEn, String nota,
                                   List<AccionDelCierre> acciones, SemanaResponse semana) {

    public static CierreDelDiaResponse from(VistaDelCierre vista) {
        return new CierreDelDiaResponse(vista.dia().fecha(), vista.dia().estado(), vista.puedeCerrar(),
                vista.dia().cerradoEn(), vista.dia().nota(), vista.dia().acciones(), SemanaResponse.from(vista.semana()));
    }

    public record SemanaResponse(int cumplidas, int planificadas) {

        static SemanaResponse from(AvanceDeLaSemana avance) {
            return avance == null ? null : new SemanaResponse(avance.cumplidas(), avance.planificadas());
        }
    }
}
