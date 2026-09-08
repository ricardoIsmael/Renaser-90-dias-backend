package com.renaser.os.onboarding.domain.model.mapa;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtocolosDelMapaTest {

    private static final FixedClock RELOJ = FixedClock.at(Instant.parse("2026-09-08T02:00:00Z"));
    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());

    private static ProtocoloReemplazoMapa protocolo(String id) {
        return ProtocoloReemplazoMapa.crear(UUID.randomUUID(), APRENDIZ, id, "scroll",
                "termino de almorzar", "abrir Instagram 30 minutos", "caminar 10 minutos", RELOJ);
    }

    @Test
    void tresEsElTecho() {
        assertThat(new ProtocolosDelMapa(List.of(protocolo("p1"), protocolo("p2"), protocolo("p3"))).protocolos())
                .hasSize(3);
    }

    @Test
    void unCuartoProtocoloSeRechaza() {
        var cuatro = List.of(protocolo("p1"), protocolo("p2"), protocolo("p3"), protocolo("p4"));

        assertThatThrownBy(() -> new ProtocolosDelMapa(cuatro))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("3");
    }

    /** Guardar el paso a medias vale; lo que no vale es ACTIVAR sin ningun protocolo. */
    @Test
    void laListaVaciaSeGuardaPeroNoAlcanzaParaActivar() {
        assertThat(ProtocolosDelMapa.vacio().completoParaActivar()).isFalse();
        assertThat(new ProtocolosDelMapa(List.of(protocolo("p1"))).completoParaActivar()).isTrue();
    }

    @Test
    void laFraseSeArmaComoEnElManual() {
        assertThat(protocolo("p1").frase())
                .isEqualTo("Cuando termino de almorzar, en lugar de abrir Instagram 30 minutos, "
                        + "hare caminar 10 minutos.");
    }

    @Test
    void unCampoVacioSeRechaza() {
        assertThatThrownBy(() -> ProtocoloReemplazoMapa.crear(UUID.randomUUID(), APRENDIZ, "p1", "scroll",
                "  ", "abrir Instagram", "caminar", RELOJ))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("disparador");
    }
}
