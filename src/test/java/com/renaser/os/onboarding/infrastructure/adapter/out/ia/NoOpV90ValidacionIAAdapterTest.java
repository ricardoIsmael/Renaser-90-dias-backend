package com.renaser.os.onboarding.infrastructure.adapter.out.ia;

import com.renaser.os.onboarding.application.ports.out.grabacionv90.ValidacionIAPort.ResultadoValidacionV90;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.ValidacionIAPort.SolicitudValidacionV90;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpV90ValidacionIAAdapterTest {

    @Test
    @DisplayName("siempre responde NO_DISPONIBLE sin explotar (sin integracion de IA en este alcance)")
    void siempreDevuelveNoDisponible() {
        var adapter = new NoOpV90ValidacionIAAdapter();
        var solicitud = new SolicitudValidacionV90(UserId.of(UUID.randomUUID()), 1L, "FASE_1", "MENTE",
                "transcripcion");

        ResultadoValidacionV90 resultado = adapter.validar(solicitud);

        assertThat(resultado.estado()).isEqualTo(ResultadoValidacionV90.Estado.NO_DISPONIBLE);
        assertThat(resultado.feedbackJson()).isNull();
    }
}
