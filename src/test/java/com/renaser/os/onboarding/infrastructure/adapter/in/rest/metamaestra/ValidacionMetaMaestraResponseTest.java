package com.renaser.os.onboarding.infrastructure.adapter.in.rest.metamaestra;

import com.renaser.os.onboarding.application.ports.in.metamaestra.ValidarMetaMaestraUseCase.ResultadoMetaMaestra;
import com.renaser.os.onboarding.application.ports.in.metamaestra.ValidarMetaMaestraUseCase.ResultadoMetaMaestra.Veredicto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** El shape de salida tiene que calzar 1:1 con {@code SixPsValidation} del frontend real. */
class ValidacionMetaMaestraResponseTest {

    @Test
    void aprobadaMapeaAAcceptedTrueSinPendingReview() {
        var resultado = new ResultadoMetaMaestra(Veredicto.APROBADA, List.of(), "Excelente.");

        var response = ValidacionMetaMaestraResponse.from(resultado);

        assertThat(response.accepted()).isTrue();
        assertThat(response.feedback()).isEqualTo("Excelente.");
        assertThat(response.pendingReview()).isFalse();
    }

    @Test
    void rechazadaMapeaAAcceptedFalseConLasPsFaltantes() {
        var resultado = new ResultadoMetaMaestra(Veredicto.RECHAZADA, List.of("CUANDO"), "Te falta profundidad.");

        var response = ValidacionMetaMaestraResponse.from(resultado);

        assertThat(response.accepted()).isFalse();
        assertThat(response.missingPs()).containsExactly("CUANDO");
        assertThat(response.feedback()).isEqualTo("Te falta profundidad.");
    }

    @Test
    void pendienteDeRevisionMapeaAAcceptedTrueConPendingReviewTrue() {
        var resultado = new ResultadoMetaMaestra(Veredicto.PENDIENTE_DE_REVISION, List.of(), "");

        var response = ValidacionMetaMaestraResponse.from(resultado);

        assertThat(response.accepted()).isTrue();
        assertThat(response.pendingReview()).isTrue();
        assertThat(response.feedback()).isEmpty();
    }
}
