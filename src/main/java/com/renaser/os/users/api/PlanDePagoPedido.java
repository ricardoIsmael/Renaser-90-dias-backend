package com.renaser.os.users.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Lo que Administración decide del pago de una persona al aceptar su solicitud (spec 032): «Sin cronograma (pagó completo)» o «Con cronograma», con su moneda y sus cuotas. Los códigos
 * son los de la API ({@code PEN}, {@code USD}); {@code onboarding}, que es quien guarda los pagos, los valida.
 *
 * @param moneda {@code PEN} o {@code USD}; solo con cronograma
 * @param cuotas fecha fija del calendario y monto de cada una; vacío sin cronograma
 */
public record PlanDePagoPedido(boolean conCronograma, String moneda, List<Cuota> cuotas) {

    public PlanDePagoPedido {
        cuotas = cuotas == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(cuotas));
    }

    /** «Sin cronograma (pagó completo)». */
    public static PlanDePagoPedido sinCronograma() {
        return new PlanDePagoPedido(false, null, List.of());
    }

    public record Cuota(LocalDate venceEl, BigDecimal monto) {
    }
}
