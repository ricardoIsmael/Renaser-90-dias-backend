package com.renaser.os.notifications.domain.model.whatsapp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** La unica puerta del webhook (spec 041, FR-026, SC-010). El secreto de aqui es de mentira: ninguna clave va en el codigo. */
class FirmaDeYCloudTest {

    private static final String SECRETO = "whsec_secreto-de-prueba";
    private static final Instant AHORA = Instant.parse("2026-11-04T20:00:00Z");
    private static final byte[] CUERPO = "{\"type\":\"whatsapp.message.updated\"}".getBytes(StandardCharsets.UTF_8);

    private static String cabecera(String secreto, Instant t, byte[] cuerpo) {
        String segundos = String.valueOf(t.getEpochSecond());
        return "t=" + segundos + ",s=" + FirmaDeYCloud.firmar(secreto, segundos, cuerpo);
    }

    // spec 041 FR-026
    @Test
    @DisplayName("una firma bien hecha con el secreto del endpoint es valida")
    void unaFirmaValida() {
        assertThat(FirmaDeYCloud.valida(CUERPO, cabecera(SECRETO, AHORA, CUERPO), SECRETO, AHORA)).isTrue();
    }

    // spec 041 FR-026, SC-010
    @Test
    @DisplayName("un cuerpo alterado, aunque sea en un caracter, se rechaza")
    void unCuerpoAlterado() {
        String cabecera = cabecera(SECRETO, AHORA, CUERPO);
        byte[] alterado = "{\"type\":\"whatsapp.message.updatee\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(FirmaDeYCloud.valida(alterado, cabecera, SECRETO, AHORA)).isFalse();
    }

    // spec 041 FR-026, SC-010
    @Test
    @DisplayName("una firma hecha con otro secreto se rechaza")
    void otroSecreto() {
        assertThat(FirmaDeYCloud.valida(CUERPO, cabecera("otro-secreto", AHORA, CUERPO), SECRETO, AHORA)).isFalse();
    }

    // spec 041 FR-026, SC-010
    @Test
    @DisplayName("una firma de hace mas de 5 minutos (o de mas de 5 minutos en el futuro) se rechaza; a los 5 minutos justos todavia vale")
    void elMargenDeCincoMinutos() {
        assertThat(FirmaDeYCloud.valida(CUERPO, cabecera(SECRETO, AHORA.minusSeconds(300), CUERPO), SECRETO, AHORA)).isTrue();
        assertThat(FirmaDeYCloud.valida(CUERPO, cabecera(SECRETO, AHORA.minusSeconds(301), CUERPO), SECRETO, AHORA)).isFalse();
        assertThat(FirmaDeYCloud.valida(CUERPO, cabecera(SECRETO, AHORA.plusSeconds(301), CUERPO), SECRETO, AHORA)).isFalse();
    }

    // spec 041 FR-026
    @Test
    @DisplayName("sirve cualquiera de las firmas de la cabecera (rotacion del secreto)")
    void variasFirmas() {
        String t = String.valueOf(AHORA.getEpochSecond());
        String vieja = FirmaDeYCloud.firmar("secreto-anterior", t, CUERPO);
        String nueva = FirmaDeYCloud.firmar(SECRETO, t, CUERPO);

        assertThat(FirmaDeYCloud.valida(CUERPO, "t=" + t + ",s=" + vieja + ",s=" + nueva, SECRETO, AHORA)).isTrue();
    }

    // spec 041 FR-026, SC-010
    @Test
    @DisplayName("sin secreto, sin cabecera, sin t, con firma que no es hex o con t que no es numero: nunca es valida")
    void entradasRotas() {
        String buena = cabecera(SECRETO, AHORA, CUERPO);

        assertThat(FirmaDeYCloud.valida(CUERPO, buena, "", AHORA)).as("sin secreto no se procesa nada").isFalse();
        assertThat(FirmaDeYCloud.valida(CUERPO, buena, null, AHORA)).isFalse();
        assertThat(FirmaDeYCloud.valida(CUERPO, null, SECRETO, AHORA)).isFalse();
        assertThat(FirmaDeYCloud.valida(CUERPO, "s=abc", SECRETO, AHORA)).isFalse();
        assertThat(FirmaDeYCloud.valida(CUERPO, "t=" + AHORA.getEpochSecond(), SECRETO, AHORA)).isFalse();
        assertThat(FirmaDeYCloud.valida(CUERPO, "t=hoy,s=" + "a".repeat(64), SECRETO, AHORA)).isFalse();
        assertThat(FirmaDeYCloud.valida(CUERPO, "t=" + AHORA.getEpochSecond() + ",s=zz", SECRETO, AHORA)).isFalse();
        assertThat(FirmaDeYCloud.valida(null, buena, SECRETO, AHORA)).isFalse();
    }
}
