package com.renaser.os.rag.application.ports.out.semaforo;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * El semaforo de cumplimiento de la persona (D-168), tal como ya lo guardo el barrido de
 * {@code points}; para {@code consultar_desvio_de_la_semana} (D-177). El adaptador delega en
 * {@code points.api.SemaforoFinder.detalleDe}: no recalcula nada.
 *
 * <p>La semana del semaforo va de sabado a viernes y no coincide con la semana de programa (lunes a
 * domingo): quien lo muestre tiene que decir las fechas, no "esta semana".
 */
public interface ConsultarSemaforoDelAprendizPort {

    /** Vacio si la persona no se mide (sin programa activado). */
    Optional<SemaforoDelAprendiz> de(UserId participanteId);

    /**
     * @param vigente     los ultimos 7 dias cerrados
     * @param ultimaSemana la ultima semana sabado a viernes ya cerrada; {@code null} si todavia no hay
     */
    record SemaforoDelAprendiz(Tramo vigente, Tramo ultimaSemana) {
    }

    /**
     * @param porcentaje {@code null} si ningun dia tuvo datos
     * @param etiqueta   la palabra que acompana al color ("Al dia", "Requiere atencion", "Con problemas",
     *                   "Sin datos"): nunca se comunica un estado solo con el color (RL-30)
     */
    record Tramo(LocalDate desde, LocalDate hasta, BigDecimal porcentaje, String etiqueta, int diasConDatos) {
    }
}
