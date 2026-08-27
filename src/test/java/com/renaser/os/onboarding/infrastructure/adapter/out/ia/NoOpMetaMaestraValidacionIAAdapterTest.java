package com.renaser.os.onboarding.infrastructure.adapter.out.ia;

import com.renaser.os.onboarding.application.ports.out.metamaestra.ValidacionMetaMaestraPort.ResultadoValidacionMetaMaestra;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpMetaMaestraValidacionIAAdapterTest {

    @Test
    @DisplayName("siempre responde NO_DISPONIBLE sin explotar (sin integracion de IA en este alcance)")
    void siempreDevuelveNoDisponible() {
        var adapter = new NoOpMetaMaestraValidacionIAAdapter();

        ResultadoValidacionMetaMaestra resultado = adapter.validar("cualquier texto de meta maestra");

        assertThat(resultado.estado()).isEqualTo(ResultadoValidacionMetaMaestra.Estado.NO_DISPONIBLE);
        assertThat(resultado.pesFaltantes()).isEmpty();
        assertThat(resultado.feedback()).isNull();
    }
}
