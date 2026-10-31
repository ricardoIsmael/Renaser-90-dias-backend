package com.renaser.os.rocks.infrastructure.adapter.in.rest.cierre;

import com.renaser.os.rocks.api.CierresDelDiaFinder.AccionDelCierre;
import com.renaser.os.rocks.api.CierresDelDiaFinder.RespuestaDelCierre;
import com.renaser.os.rocks.application.ports.in.cierre.ConsultarCierreDelDiaUseCase.AvanceDeLaSemana;
import com.renaser.os.rocks.application.ports.in.cierre.ConsultarCierreDelDiaUseCase.VistaDelCierre;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** El cierre de un día de la propia persona (contrato de la spec 017). A mano, sin mapper: un campo nuevo no se cuela solo. */
public record CierreDelDiaResponse(LocalDate fecha, String estado, boolean puedeCerrar, Instant cerradoEn, String nota,
                                   List<AccionResponse> acciones, SemanaResponse semana) {

    public static CierreDelDiaResponse from(VistaDelCierre vista) {
        return new CierreDelDiaResponse(vista.dia().fecha(), vista.dia().estado(), vista.puedeCerrar(),
                vista.dia().cerradoEn(), vista.dia().nota(), acciones(vista), SemanaResponse.from(vista.semana()));
    }

    private static List<AccionResponse> acciones(VistaDelCierre vista) {
        return vista.dia().acciones().stream().map(a -> AccionResponse.from(a, vista.exigidas())).toList();
    }

    /**
     * Una acción del día. {@code exigeRespuesta} dice si al cerrar hoy se pregunta por ella: pendiente cuya hora ya llegó en la zona
     * de la persona, o sin hora. Es propio de esta ruta; la ficha del acompañamiento no lo lleva.
     */
    public record AccionResponse(UUID id, String titulo, LocalTime horaInicio, boolean cumplida, Instant cumplidaEn,
                                 RespuestaDelCierre cierre, boolean exigeRespuesta) {

        static AccionResponse from(AccionDelCierre accion, Set<UUID> exigidas) {
            return new AccionResponse(accion.id(), accion.titulo(), accion.horaInicio(), accion.cumplida(), accion.cumplidaEn(),
                    accion.cierre(), exigidas.contains(accion.id()));
        }
    }

    public record SemanaResponse(int cumplidas, int planificadas) {

        static SemanaResponse from(AvanceDeLaSemana avance) {
            return avance == null ? null : new SemanaResponse(avance.cumplidas(), avance.planificadas());
        }
    }
}
