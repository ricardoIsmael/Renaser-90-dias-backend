package com.renaser.os.calendar.application.ports.in.audiencia;

import com.renaser.os.calendar.domain.model.evento.audiencia.ParteDeAudiencia;

import java.util.List;

/**
 * La audiencia elegida que llega al crear o editar un evento (spec 044, contrato §1). {@code null} en el comando = el pedido no la trae: al crear, el
 * evento es de los de siempre; al editar un evento {@code SELECCION}, se conserva la guardada (FR-030).
 *
 * @param recalcular solo al editar: tomar una foto nueva del semáforo («Volver a calcular», FR-011)
 */
public record PedidoDeAudiencia(List<ParteDeAudiencia> partes, boolean recalcular) {

    public PedidoDeAudiencia {
        partes = partes == null ? List.of() : List.copyOf(partes);
    }
}
