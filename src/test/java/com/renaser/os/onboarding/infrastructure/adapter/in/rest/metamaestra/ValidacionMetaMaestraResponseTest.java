package com.renaser.os.onboarding.infrastructure.adapter.in.rest.metamaestra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El shape de salida tiene que seguir calzando 1:1 con {@code SixPsValidation} del frontend
 * real aunque ya no haya veredicto de IA — el cliente lee los cuatro campos.
 */
class ValidacionMetaMaestraResponseTest {

    @Test
    @DisplayName("la unica respuesta posible es aceptada, sin Ps faltantes")
    void siempreAceptada() {
        var response = ValidacionMetaMaestraResponse.aceptada();

        assertThat(response.accepted()).isTrue();
        assertThat(response.missingPs()).isEmpty();
        assertThat(response.feedback()).isEmpty();
    }

    @Test
    @DisplayName("pendingReview va en false: no hay revision pendiente que prometerle a nadie")
    void nuncaPrometeUnaRevision() {
        assertThat(ValidacionMetaMaestraResponse.aceptada().pendingReview()).isFalse();
    }
}
