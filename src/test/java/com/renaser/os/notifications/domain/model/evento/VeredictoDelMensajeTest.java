package com.renaser.os.notifications.domain.model.evento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** El tope de atraso y el evento ya empezado (spec 041, FR-016, SC-007). Sin horario: sale tambien de madrugada. */
class VeredictoDelMensajeTest {

    /** 03:00 de Lima = 08:00 UTC: un horario que cualquier "horario de oficina" habria dejado afuera. */
    private static final Instant INICIO_DE_MADRUGADA = Instant.parse("2026-11-07T08:30:00Z");
    private static final Duration QUINCE = VeredictoDelMensaje.ATRASO_MAXIMO;

    // spec 041 FR-016, SC-007
    @Test
    @DisplayName("un aviso a las 03:00 sale: no hay horario")
    void aLasTresDeLaMadrugadaSale() {
        Instant suHora = INICIO_DE_MADRUGADA.minus(Duration.ofMinutes(30));

        assertThat(VeredictoDelMensaje.de(INICIO_DE_MADRUGADA, 30, suHora, QUINCE)).isEqualTo(VeredictoDelMensaje.ENVIAR);
    }

    // spec 041 FR-016, SC-007
    @Test
    @DisplayName("hasta 15 minutos despues de su hora todavia sale; a los 15 minutos y un segundo, no")
    void elTopeDeQuinceMinutos() {
        Instant inicio = Instant.parse("2026-11-07T00:00:00Z");
        Instant suHora = inicio.minus(Duration.ofHours(1));

        assertThat(VeredictoDelMensaje.de(inicio, 60, suHora.plus(QUINCE), QUINCE)).isEqualTo(VeredictoDelMensaje.ENVIAR);
        assertThat(VeredictoDelMensaje.de(inicio, 60, suHora.plus(QUINCE).plusSeconds(1), QUINCE)).isEqualTo(VeredictoDelMensaje.LLEGO_TARDE);
        assertThat(VeredictoDelMensaje.LLEGO_TARDE.motivo()).isEqualTo("Llegó tarde");
    }

    // spec 041 FR-016
    @Test
    @DisplayName("el tope se cuenta desde la hora del aviso tambien al cruzar la medianoche")
    void alCruzarLaMedianoche() {
        Instant inicio = Instant.parse("2026-11-07T00:05:00Z");
        Instant suHora = inicio.minus(Duration.ofDays(1));

        assertThat(VeredictoDelMensaje.de(inicio, 1440, suHora.plus(Duration.ofMinutes(14)), QUINCE)).isEqualTo(VeredictoDelMensaje.ENVIAR);
        assertThat(VeredictoDelMensaje.de(inicio, 1440, suHora.plus(Duration.ofMinutes(16)), QUINCE)).isEqualTo(VeredictoDelMensaje.LLEGO_TARDE);
    }

    // spec 041 FR-016, SC-007
    @Test
    @DisplayName("una ocurrencia que ya empezo no recibe su aviso, aunque este dentro de los 15 minutos")
    void elEventoYaEmpezo() {
        Instant inicio = Instant.parse("2026-11-07T00:00:00Z");

        assertThat(VeredictoDelMensaje.de(inicio, 10, inicio, QUINCE)).isEqualTo(VeredictoDelMensaje.EL_EVENTO_YA_EMPEZO);
        assertThat(VeredictoDelMensaje.de(inicio, 10, inicio.plusSeconds(60), QUINCE)).isEqualTo(VeredictoDelMensaje.EL_EVENTO_YA_EMPEZO);
        assertThat(VeredictoDelMensaje.EL_EVENTO_YA_EMPEZO.motivo()).isEqualTo("El evento ya empezó");
    }

    // spec 041 FR-016
    @Test
    @DisplayName("un aviso que llega antes de su hora (reloj adelantado unos segundos) sale")
    void llegarUnPocoAntesSale() {
        Instant inicio = Instant.parse("2026-11-07T00:00:00Z");

        assertThat(VeredictoDelMensaje.de(inicio, 10, inicio.minus(Duration.ofMinutes(10)).minusSeconds(3), QUINCE)).isEqualTo(VeredictoDelMensaje.ENVIAR);
    }
}
