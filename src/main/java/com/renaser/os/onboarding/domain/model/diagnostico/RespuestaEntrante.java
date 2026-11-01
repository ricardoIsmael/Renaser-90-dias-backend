package com.renaser.os.onboarding.domain.model.diagnostico;

import java.math.BigDecimal;

/** Lo que manda la persona para una pregunta. Todos los valores en null = «sin respuesta» (borra la anterior). */
public record RespuestaEntrante(int preguntaId, String valorTexto, BigDecimal valorNumero, Boolean valorBooleano,
                                Short valorEscala, String valorJson) {

    public boolean estaVacia() {
        return (valorTexto == null || valorTexto.isBlank()) && valorNumero == null && !Boolean.TRUE.equals(valorBooleano)
                && valorEscala == null && (valorJson == null || valorJson.isBlank());
    }
}
