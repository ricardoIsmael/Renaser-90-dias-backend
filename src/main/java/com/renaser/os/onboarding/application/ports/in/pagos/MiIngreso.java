package com.renaser.os.onboarding.application.ports.in.pagos;

import com.renaser.os.onboarding.application.ports.in.pagos.VistaDePagos.PagoAnotado;

import java.util.List;

/**
 * Yo → «Mi ingreso» (spec 032): los pasos de la entrada de la persona con su estado real y, si tiene su pago anotado, el cronograma. Sin pago anotado no hay nada que
 * mostrar: {@code pasos} va vacío y {@code pagos} nulo, y la app no dibuja la fila.
 *
 * @param pasos solo los que le aplican, en orden: Diagnóstico (si le tocó), Acuerdo y Pagos
 */
public record MiIngreso(List<Paso> pasos, PagoAnotado pagos) {

    public enum ClaveDePaso {
        DIAGNOSTICO,
        ACUERDO,
        PAGOS
    }

    /** @param hecho Diagnóstico: terminado; Acuerdo: términos aceptados; Pagos: sin cronograma, o todas las cuotas pagadas */
    public record Paso(ClaveDePaso clave, boolean hecho) {
    }

    /** Lo que la persona ve cuando no hay nada anotado. */
    public static MiIngreso sinNada() {
        return new MiIngreso(List.of(), null);
    }
}
