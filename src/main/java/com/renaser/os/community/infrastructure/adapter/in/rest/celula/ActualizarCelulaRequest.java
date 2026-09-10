package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import java.time.LocalDate;

/**
 * {@code videoCallUrl} siempre se aplica, igual criterio simplificado que
 * {@code ActualizarCohorteRequest} (CM-14).
 *
 * <p>El periodo NO sigue ese criterio, y es a proposito. Aplicarlo siempre significaria que un
 * PATCH que solo renombra el grupo le borra las fechas y el grupo deja de cerrarse: un dano
 * silencioso que nadie pidio. Aca la regla es <b>vienen las dos y se fijan, o no viene ninguna y
 * el periodo queda como estaba</b>. {@code clearPeriod} es la unica via para borrarlo, y hay que
 * escribirla: borrar el periodo de un grupo se pide, no se cae de un descuido.
 */
public record ActualizarCelulaRequest(String name, String videoCallUrl, LocalDate periodStart, LocalDate periodEnd,
                                       Boolean clearPeriod) {

    /** Si este PATCH tiene algo que decir sobre el periodo. Si no, el grupo se queda con el suyo. */
    public boolean tocaPeriodo() {
        return periodStart != null || periodEnd != null || borraPeriodo();
    }

    /** Un body que manda {@code clearPeriod} Y fechas se contradice; gana el borrado, que es lo
     * unico que se pudo haber escrito a mano. */
    public LocalDate periodStartAplicado() {
        return borraPeriodo() ? null : periodStart;
    }

    public LocalDate periodEndAplicado() {
        return borraPeriodo() ? null : periodEnd;
    }

    private boolean borraPeriodo() {
        return Boolean.TRUE.equals(clearPeriod);
    }
}
