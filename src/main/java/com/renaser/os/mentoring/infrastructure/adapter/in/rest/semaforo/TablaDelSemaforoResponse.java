package com.renaser.os.mentoring.infrastructure.adapter.in.rest.semaforo;

import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoDelGrupoUseCase.FilaDelSemaforo;
import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoDelGrupoUseCase.TablaDelSemaforo;
import com.renaser.os.mentoring.domain.model.semaforo.MedicionDelAprendiz;
import com.renaser.os.points.api.DiaDelSemaforo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * La tabla del semáforo de un grupo, en el JSON exacto de docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md
 * §4.3. La misma forma para el mentor y para el administrador.
 *
 * <p>Mapeo a mano y no automático (CLAUDE.MD §5.4.5): un campo nuevo del dominio no puede aparecer
 * solo en la respuesta.
 */
public record TablaDelSemaforoResponse(UUID grupoId, String grupoNombre, LocalDate desde, LocalDate hasta,
                                       boolean cerrada, ConteoPorColorResponse resumen,
                                       List<AprendizResponse> aprendices) {

    public static TablaDelSemaforoResponse from(TablaDelSemaforo tabla) {
        return new TablaDelSemaforoResponse(tabla.grupoId(), tabla.grupoNombre(), tabla.periodo().desde(),
                tabla.periodo().hasta(), tabla.periodo().cerrada(), ConteoPorColorResponse.from(tabla.resumen()),
                tabla.aprendices().stream().map(AprendizResponse::from).toList());
    }

    /** Un aprendiz que no se mide llega con {@code color: SIN_DATOS}, {@code porcentaje: null} y sin días. */
    public record AprendizResponse(UUID aprendizId, String nombre, String avatarUrl, BigDecimal porcentaje,
                                   String color, String etiqueta, int diasConDatos, List<DiaResponse> dias) {

        static AprendizResponse from(FilaDelSemaforo fila) {
            MedicionDelAprendiz medicion = fila.medicion();
            return new AprendizResponse(fila.aprendizId(), fila.nombre(), fila.avatarUrl(), medicion.porcentaje(),
                    medicion.color().name(), medicion.color().etiqueta(), medicion.diasConDatos(),
                    medicion.dias().stream().map(DiaResponse::from).toList());
        }
    }

    /**
     * El día resumido de la tabla: sin los conteos de hábitos y objetivos, que van en el detalle. La
     * palabra acompaña al color también en cada día (RL-30: nunca un estado solo con color).
     */
    public record DiaResponse(LocalDate fecha, String estado, Integer porcentaje, String color, String etiqueta) {

        static DiaResponse from(DiaDelSemaforo dia) {
            return new DiaResponse(dia.fecha(), dia.estado().name(), dia.porcentaje(), dia.color().name(),
                    dia.color().etiqueta());
        }
    }
}
