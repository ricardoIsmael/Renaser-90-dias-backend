package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

/**
 * Cómo paga la persona que se acepta (spec 032, D-307). <b>Lo implementa {@code onboarding}</b> (dueño de los pagos, que viven en su {@code estado_onboarding} y en
 * {@code cuotas_de_pago}) y lo consume {@code users} al aceptar una solicitud: la dependencia va de {@code onboarding} hacia {@code users}, como siempre, y {@code users} no importa a
 * nadie (el mismo patrón de {@link FichaAlAceptar}).
 *
 * <p>Se invoca DENTRO de la transacción de aceptar: si el plan no es válido (sin moneda, sin cuotas, un monto que no es positivo…), la aceptación entera se deshace con un 400. Si
 * el pedido no trae plan, no se llama a nada y la persona queda «sin anotar».
 */
public interface PagosAlAceptar {

    /** Anota el pago de la persona. {@code quien} es Administración (o Alquimista), para comprobar que puede y dejar quién marcó lo que luego se marque. */
    void anotar(UserId persona, UserId quien, PlanDePagoPedido plan);
}
