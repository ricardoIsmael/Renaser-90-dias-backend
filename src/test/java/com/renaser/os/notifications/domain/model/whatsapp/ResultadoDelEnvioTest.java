package com.renaser.os.notifications.domain.model.whatsapp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResultadoDelEnvioTest {

    // spec 041 FR-022, FR-028
    @Test
    @DisplayName("el motivo que se guarda es corto y nunca lleva un numero de telefono")
    void elMotivoNoLlevaElNumero() {
        var resultado = ResultadoDelEnvio.fallido(CausaDeFalla.OTRA, "El número +51 987 654 321 no es válido en WhatsApp");

        assertThat(resultado.motivo()).doesNotContain("987").doesNotContain("654").contains("[número]");
    }

    // spec 041 FR-022
    @Test
    @DisplayName("un motivo largo se recorta a 200 caracteres")
    void elMotivoSeRecorta() {
        assertThat(ResultadoDelEnvio.noSeSupo("x".repeat(500)).motivo()).hasSize(ResultadoDelEnvio.LARGO_MAXIMO_DEL_MOTIVO);
    }

    // spec 041 FR-024
    @Test
    @DisplayName("la causa se reconoce por el estado HTTP y por palabras claras; lo demas es «otra» y no le llega a Administracion")
    void lasCausas() {
        assertThat(CausaDeFalla.segun(401, null)).isEqualTo(CausaDeFalla.CLAVE);
        assertThat(CausaDeFalla.segun(403, "forbidden")).isEqualTo(CausaDeFalla.CLAVE);
        assertThat(CausaDeFalla.segun(400, "Template renaser_evento_recordatorio_v1 is not approved")).isEqualTo(CausaDeFalla.PLANTILLA);
        assertThat(CausaDeFalla.segun(null, "Insufficient balance")).isEqualTo(CausaDeFalla.SALDO);
        assertThat(CausaDeFalla.segun(429, "slow down")).isEqualTo(CausaDeFalla.LIMITE);
        assertThat(CausaDeFalla.segun(400, "bad request")).isEqualTo(CausaDeFalla.OTRA);
        assertThat(CausaDeFalla.CLAVE.leTocaAAdministracion()).isTrue();
        assertThat(CausaDeFalla.PLANTILLA.leTocaAAdministracion()).isTrue();
        assertThat(CausaDeFalla.SALDO.leTocaAAdministracion()).isTrue();
        assertThat(CausaDeFalla.LIMITE.leTocaAAdministracion()).isFalse();
        assertThat(CausaDeFalla.OTRA.leTocaAAdministracion()).isFalse();
    }
}
