package com.renaser.os.users.infrastructure.adapter.in.rest.formacion;

import com.renaser.os.users.application.ports.in.formacion.ConsultarFormacionDeLaSesionUseCase.FaseDelPrograma;
import com.renaser.os.users.application.ports.in.formacion.ConsultarFormacionDeLaSesionUseCase.FormacionDeLaSesion;
import com.renaser.os.users.application.ports.in.formacion.ConsultarFormacionDeLaSesionUseCase.OpcionDeFormacion;

import java.util.List;

/**
 * La formación de la sesión (spec 022, contrato {@code formaciones-api.md}). Va dentro de {@code UserResponse.formacion} y sola en
 * {@code GET /me/formacion}. Todo es aditivo: un cliente anterior lo ignora.
 */
public record FormacionDeLaSesionResponse(OpcionResponse activa, List<OpcionResponse> disponibles, boolean puedeElegir, boolean veTodas,
                                          boolean sinFormacion, String delPrograma, int duracionDias, List<FaseResponse> fases) {

    public static FormacionDeLaSesionResponse from(FormacionDeLaSesion sesion) {
        return new FormacionDeLaSesionResponse(sesion.activa() == null ? null : OpcionResponse.from(sesion.activa()),
                sesion.disponibles().stream().map(OpcionResponse::from).toList(), sesion.puedeElegir(), sesion.veTodas(),
                sesion.sinFormacion(), sesion.delPrograma(), sesion.duracionDias(),
                sesion.fases().stream().map(FaseResponse::from).toList());
    }

    public record OpcionResponse(String codigo, String nombre) {

        static OpcionResponse from(OpcionDeFormacion opcion) {
            return new OpcionResponse(opcion.codigo(), opcion.nombre());
        }
    }

    public record FaseResponse(int numero, int primerDia, int ultimoDia) {

        static FaseResponse from(FaseDelPrograma fase) {
            return new FaseResponse(fase.numero(), fase.primerDia(), fase.ultimoDia());
        }
    }
}
