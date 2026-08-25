package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

/** {@code videoCallUrl} siempre se aplica, igual criterio simplificado que
 * {@code ActualizarCohorteRequest} (CM-14). */
public record ActualizarCelulaRequest(String name, String videoCallUrl) {
}
