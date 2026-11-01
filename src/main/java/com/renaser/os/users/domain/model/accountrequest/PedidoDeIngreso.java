package com.renaser.os.users.domain.model.accountrequest;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.users.api.PlanDePagoPedido;

import java.util.Set;

/**
 * Lo que Administración pide al aceptar una solicitud (spec 025): a qué formaciones entra, con qué duración y si su ficha es con diagnóstico. El rol va
 * aparte (D-265). Cualquier campo puede faltar: una app anterior a las formaciones solo manda el rol, y eso se acepta como siempre —Escuela, su
 * duración única y sin diagnóstico (FR-013)—.
 *
 * @param formaciones  las formaciones elegidas; {@code null} o vacío = no se eligió (se asume la Escuela, la de siempre)
 * @param duracionDias la duración elegida para un aprendiz; {@code null} = no se eligió
 * @param pago         spec 032: «sin cronograma» o «con cronograma» con sus cuotas; {@code null} = no se decidió y la persona queda «sin anotar» (lo que manda una app anterior)
 */
public record PedidoDeIngreso(Set<Formacion> formaciones, Integer duracionDias, boolean conDiagnostico, PlanDePagoPedido pago) {

    public PedidoDeIngreso {
        formaciones = formaciones == null ? Set.of() : Set.copyOf(formaciones);
    }

    /** El pedido de la spec 025, sin decisión de pago. */
    public PedidoDeIngreso(Set<Formacion> formaciones, Integer duracionDias, boolean conDiagnostico) {
        this(formaciones, duracionDias, conDiagnostico, null);
    }

    /** Lo que manda una app anterior: solo el rol. */
    public static PedidoDeIngreso deSiempre() {
        return new PedidoDeIngreso(Set.of(), null, false, null);
    }

    /** Si no eligió formación: es el camino de siempre y se acepta en la Escuela. */
    public boolean sinFormacionElegida() {
        return formaciones.isEmpty();
    }
}
