package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.out.respuesta.SembrarRespuestaPorClavePort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** El WhatsApp del alta pasa a la Ficha Inicial (spec 043, FR-006). */
class WhatsappDelRegistroServiceTest {

    private final List<String> escritas = new ArrayList<>();
    private final SembrarRespuestaPorClavePort puerto = (persona, clave, texto, cuando) -> escritas.add(clave + "=" + texto + "@" + cuando);
    private final WhatsappDelRegistroService service = new WhatsappDelRegistroService(puerto,
            FixedClock.at(Instant.parse("2026-11-06T03:00:00Z")));

    // spec 043 FR-006
    @Test
    @DisplayName("se guarda en la respuesta «whatsapp» tal como lo arma la app, con la hora del reloj")
    void seGuardaEnLaRespuestaWhatsapp() {
        service.llevarALaFicha(UserId.of(UUID.randomUUID()), " +51 987654321 ");

        assertThat(escritas).containsExactly("whatsapp=+51 987654321@2026-11-06T03:00:00Z");
    }

    // spec 043 FR-006
    @Test
    @DisplayName("lo que no tiene forma de numero no se lleva a la ficha")
    void loQueNoEsNumeroNoSeLleva() {
        service.llevarALaFicha(UserId.of(UUID.randomUUID()), "+51 98");
        service.llevarALaFicha(UserId.of(UUID.randomUUID()), "llamame");
        service.llevarALaFicha(UserId.of(UUID.randomUUID()), null);

        assertThat(escritas).isEmpty();
    }
}
