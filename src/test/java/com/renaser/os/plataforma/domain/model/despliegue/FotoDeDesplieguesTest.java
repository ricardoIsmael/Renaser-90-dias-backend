package com.renaser.os.plataforma.domain.model.despliegue;

import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** La copia en memoria y la cadena de madres (contrato fase 2 §3.2). Sin Spring. */
class FotoDeDesplieguesTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T04:30:00Z");
    private static final UserId SOPORTE = UserId.of(UUID.randomUUID());
    private final UserId probadora = UserId.of(UUID.randomUUID());
    private final UserId otra = UserId.of(UUID.randomUUID());

    @Test
    @DisplayName("sin despliegues no deja fuera a nadie")
    void sinDespliegues() {
        assertThat(FotoDeDespliegues.vacia().sinDespliegues()).isTrue();
        assertThat(FotoDeDespliegues.vacia().fueraEn(List.of("chat", "chat.grupos"), otra)).isEmpty();
    }

    @Test
    @DisplayName("una madre en despliegue esconde a sus hijas aunque la hija no tenga despliegue propio")
    void laMadreEscondeALasHijas() {
        FotoDeDespliegues foto = FotoDeDespliegues.de(List.of(
                DespliegueDeFuncionalidad.empezar("chat", 0, 1, SOPORTE, AHORA).conProbadora(probadora)));

        assertThat(foto.fueraEn(List.of("chat", "chat.grupos"), otra)).contains("chat");
        assertThat(foto.fueraEn(List.of("chat", "chat.grupos"), probadora)).isEmpty();
        assertThat(foto.fueraEn(List.of("muro"), otra)).as("otra rama no se toca").isEmpty();
    }

    @Test
    @DisplayName("devuelve el PRIMER nivel que deja fuera, de la raíz a la clave")
    void elPrimerNivelQueDejaFuera() {
        FotoDeDespliegues foto = FotoDeDespliegues.de(List.of(
                DespliegueDeFuncionalidad.empezar("chat", 0, 1, SOPORTE, AHORA).conProbadora(probadora),
                DespliegueDeFuncionalidad.empezar("chat.audio", 0, 2, SOPORTE, AHORA)));

        assertThat(foto.fueraEn(List.of("chat", "chat.audio"), probadora))
                .as("está dentro de la madre pero no de la hija").contains("chat.audio");
        assertThat(foto.fueraEn(List.of("chat", "chat.audio"), otra)).contains("chat");
        assertThat(foto.cantidad()).isEqualTo(2);
    }
}
