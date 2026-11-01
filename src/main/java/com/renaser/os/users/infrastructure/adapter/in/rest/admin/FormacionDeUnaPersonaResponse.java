package com.renaser.os.users.infrastructure.adapter.in.rest.admin;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.users.application.ports.in.formacion.ConsultarFormacionDeUnaPersonaUseCase.FormacionDeUnaPersona;
import com.renaser.os.users.domain.model.formacion.ParticipacionAnterior;

import java.util.List;

/**
 * Las formaciones de una persona en su ficha de Administración (spec 022). {@code historial}: lo que cursó antes, lo más reciente primero; el
 * motivo solo viene si Administración la dio por terminada.
 */
public record FormacionDeUnaPersonaResponse(String formacion, int duracionDias, boolean programaActivado,
                                            List<AsignadaResponse> formacionesDelPersonal, List<AnteriorResponse> historial) {

    public static FormacionDeUnaPersonaResponse from(FormacionDeUnaPersona f) {
        return new FormacionDeUnaPersonaResponse(f.formacion() == null ? null : f.formacion().codigo(), f.duracionDias(),
                f.programaActivado(),
                f.delPersonal().stream().map(a -> new AsignadaResponse(a.formacion().codigo(), a.activa())).toList(),
                f.historial().stream().map(AnteriorResponse::from).toList());
    }

    public record AsignadaResponse(String formacion, boolean activa) {
    }

    public record AnteriorResponse(String formacion, int duracionDias, String fechaInicio, String fechaFin, int diaFinal,
                                   String resultado, String motivo, Integer puntosLiga, String trasladadoA) {

        static AnteriorResponse from(ParticipacionAnterior a) {
            Formacion hacia = a.trasladadoA();
            return new AnteriorResponse(a.formacion().codigo(), a.duracionDias(),
                    a.fechaInicio() == null ? null : a.fechaInicio().toString(), a.fechaFin().toString(), a.diaFinal(),
                    a.resultado().name(), a.motivo(), a.puntosLiga(), hacia.codigo());
        }
    }
}
