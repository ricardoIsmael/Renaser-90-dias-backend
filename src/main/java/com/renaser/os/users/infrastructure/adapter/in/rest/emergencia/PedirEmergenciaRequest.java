package com.renaser.os.users.infrastructure.adapter.in.rest.emergencia;

import com.renaser.os.users.domain.model.emergencia.SolicitudDeEmergencia;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param queOcurrio qué pasó, en pocas palabras (el dominio lo exige no vacío)
 * @param diaPedido  a qué día del programa quiere volver
 */
public record PedirEmergenciaRequest(
        @Size(max = SolicitudDeEmergencia.LARGO_MAXIMO, message = "Escríbelo en 280 caracteres o menos.") String queOcurrio,
        @NotNull(message = "Elige a qué día quieres volver.") Integer diaPedido) {
}
