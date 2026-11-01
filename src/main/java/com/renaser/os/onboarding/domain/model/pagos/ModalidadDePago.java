package com.renaser.os.onboarding.domain.model.pagos;

/**
 * Cómo paga una persona que ya tiene su pago anotado (spec 032). Quien no tiene ninguna de las dos está «sin anotar» (sin valor): las cuentas de antes y el personal
 * que entra con «Sin cronograma» por defecto; para ellas la app no muestra «Mi ingreso».
 */
public enum ModalidadDePago {

    /** Pagó completo: Yo dice «Tu pago fue cancelado» (en el Perú, «cancelado» es «pagado por completo»). */
    SIN_CRONOGRAMA,

    /** Paga en cuotas con fecha fija. */
    CON_CRONOGRAMA
}
