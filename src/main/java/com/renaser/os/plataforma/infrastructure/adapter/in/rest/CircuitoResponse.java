package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import com.renaser.os.shared.application.EstadoAutomaticoDeFuncionalidades.CircuitoAutomatico;

import java.time.Instant;

/** El estado de un circuito de la IA (contrato §6, {@code GET /soporte-ti/ia/circuito}). */
record CircuitoResponse(String parte, String estado, int fallasEnVentana, Instant abiertoDesde,
                        Instant proximaPrueba) {

    static CircuitoResponse from(CircuitoAutomatico circuito) {
        return new CircuitoResponse(circuito.parte(), circuito.estado(), circuito.fallasEnVentana(),
                circuito.abiertoDesde(), circuito.proximaPrueba());
    }
}
