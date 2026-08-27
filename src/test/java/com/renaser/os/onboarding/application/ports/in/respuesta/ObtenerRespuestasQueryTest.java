package com.renaser.os.onboarding.application.ports.in.respuesta;

import com.renaser.os.onboarding.application.ports.in.respuesta.ObtenerRespuestasUseCase.ObtenerRespuestasQuery;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObtenerRespuestasQueryTest {

    @Test
    void construyeUnaQueryValidaSinExplotar() {
        UserId actorId = UserId.of(UUID.randomUUID());

        var query = new ObtenerRespuestasQuery(actorId, "diseno_destino");

        assertThat(query.actorId()).isEqualTo(actorId);
        assertThat(query.flujo()).isEqualTo("diseno_destino");
    }

    @Test
    void rechazaActorIdNulo() {
        assertThatThrownBy(() -> new ObtenerRespuestasQuery(null, "diseno_destino"))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rechazaFlujoVacio() {
        UserId actorId = UserId.of(UUID.randomUUID());
        assertThatThrownBy(() -> new ObtenerRespuestasQuery(actorId, " "))
                .isInstanceOf(ConstraintViolationException.class);
    }
}
