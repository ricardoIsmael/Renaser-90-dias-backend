package com.renaser.os.mentoring.infrastructure.adapter.in.rest.semaforo;

import com.renaser.os.mentoring.application.ports.in.ConsultarAtencionDelSemaforoUseCase.AprendizQueNecesitaAtencion;
import com.renaser.os.mentoring.application.ports.in.ConsultarAtencionDelSemaforoUseCase.AtencionDelSemaforo;
import com.renaser.os.mentoring.application.ports.in.ConsultarAtencionDelSemaforoUseCase.GrupoDelAprendiz;
import com.renaser.os.mentoring.infrastructure.adapter.in.rest.semaforo.TablaDelSemaforoResponse.AprendizResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * «¿A quién atiendo hoy?» (S-4), en castellano como el resto del semáforo
 * (docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §4.6). Cada aprendiz trae la misma forma que una fila
 * de la tabla del grupo (§4.3) más sus grupos: la app lo dibuja con el mismo componente.
 *
 * <p>Mapeo a mano (CLAUDE.MD §5.4.5): un campo nuevo del dominio no aparece solo en la respuesta.
 */
public record AtencionDelSemaforoResponse(LocalDate desde, LocalDate hasta, boolean cerrada,
                                          ResumenResponse resumen, List<AprendizConGruposResponse> aprendices) {

    public static AtencionDelSemaforoResponse from(AtencionDelSemaforo atencion) {
        return new AtencionDelSemaforoResponse(atencion.periodo().desde(), atencion.periodo().hasta(),
                atencion.periodo().cerrada(),
                new ResumenResponse(atencion.rojo(), atencion.amarillo(), atencion.rojo() + atencion.amarillo()),
                atencion.aprendices().stream().map(AprendizConGruposResponse::from).toList());
    }

    public record ResumenResponse(int rojo, int amarillo, int total) {
    }

    /**
     * Los mismos campos que una fila de la tabla del grupo ({@link AprendizResponse}), más sus grupos.
     *
     * @param grupos vacío = no está en ningún grupo que esté corriendo
     */
    public record AprendizConGruposResponse(java.util.UUID aprendizId, String nombre, String avatarUrl,
                                            java.math.BigDecimal porcentaje, String color, String etiqueta,
                                            int diasConDatos, List<TablaDelSemaforoResponse.DiaResponse> dias,
                                            String motivo, List<GrupoResponse> grupos) {

        static AprendizConGruposResponse from(AprendizQueNecesitaAtencion fila) {
            AprendizResponse comoEnLaTabla =
                    AprendizResponse.from(fila.aprendizId(), fila.nombre(), fila.avatarUrl(), fila.medicion());
            return new AprendizConGruposResponse(comoEnLaTabla.aprendizId(), comoEnLaTabla.nombre(),
                    comoEnLaTabla.avatarUrl(), comoEnLaTabla.porcentaje(), comoEnLaTabla.color(),
                    comoEnLaTabla.etiqueta(), comoEnLaTabla.diasConDatos(), comoEnLaTabla.dias(),
                    comoEnLaTabla.motivo(), fila.grupos().stream().map(GrupoResponse::from).toList());
        }
    }

    /** @param mentorNombre null si el grupo no tiene mentor vigente (la recepción nunca lo tiene) */
    public record GrupoResponse(java.util.UUID grupoId, String grupoNombre, boolean recepcion, String mentorNombre) {

        static GrupoResponse from(GrupoDelAprendiz grupo) {
            return new GrupoResponse(grupo.grupoId(), grupo.grupoNombre(), grupo.recepcion(), grupo.mentorNombre());
        }
    }
}
