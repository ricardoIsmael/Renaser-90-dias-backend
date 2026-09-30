package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.api.TratoDeLaPersonaFinder.Trato;
import com.renaser.os.onboarding.application.ports.out.respuesta.LeerRespuestasPorClavePort;
import com.renaser.os.onboarding.application.ports.out.respuesta.LeerRespuestasPorClavePort.ValorDeRespuesta;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** E-457: el trato sale de la pregunta sex de la ficha inicial ("Masculino"/"Femenino"); sin dato, neutro. */
class TratoDeLaPersonaServiceTest {

    private static final UserId PERSONA = UserId.of(UUID.randomUUID());
    private final LeerRespuestasPorClavePort respuestas = mock(LeerRespuestasPorClavePort.class);
    private final TratoDeLaPersonaService service = new TratoDeLaPersonaService(respuestas);

    private void respondio(String valor) {
        when(respuestas.deUsuario(PERSONA, Set.of("sex"))).thenReturn(valor == null ? Map.of()
                : Map.of("sex", new ValorDeRespuesta(valor, null)));
    }

    @Test
    @DisplayName("los dos valores reales de opciones_pregunta, y neutro sin dato o con otro valor")
    void valores() {
        respondio("Masculino");
        assertThat(service.de(PERSONA)).isEqualTo(Trato.MASCULINO);
        respondio("Femenino");
        assertThat(service.de(PERSONA)).isEqualTo(Trato.FEMENINO);
        respondio(null);
        assertThat(service.de(PERSONA)).isEqualTo(Trato.NEUTRO);
        respondio("Prefiero no decirlo");
        assertThat(service.de(PERSONA)).isEqualTo(Trato.NEUTRO);
    }
}
