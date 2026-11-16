package com.renaser.os.shared.application.ports.out;

import com.renaser.os.shared.domain.GrupoDeLimite;
import com.renaser.os.shared.domain.ResultadoDelLimite;

/**
 * Las métricas del límite por cuenta (spec 050, FR-019): cuántos pedidos pasaron, habrían sido bloqueados o se bloquearon, y qué
 * parte del tope se usó, que es lo que decide los topes definitivos. Medir nunca rompe un pedido.
 */
public interface RegistrarMetricaDeLimitesPort {

    void pedido(GrupoDeLimite grupo, ResultadoDelLimite resultado);

    /** {@code proporcion}: lo contado dividido por el tope de esa ventana; 1,0 es justo el tope y más de 1, que se pasó. */
    void uso(GrupoDeLimite grupo, String ventana, double proporcion);
}
