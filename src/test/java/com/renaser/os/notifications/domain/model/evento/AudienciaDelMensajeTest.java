package com.renaser.os.notifications.domain.model.evento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AudienciaDelMensajeTest {

    // spec 041 FR-011, SC-016
    @Test
    @DisplayName("VOY admite solo a quien dijo «Voy»; TODOS admite a toda la audiencia")
    void quienAdmiteCadaValor() {
        assertThat(AudienciaDelMensaje.VOY.admite(true)).isTrue();
        assertThat(AudienciaDelMensaje.VOY.admite(false)).isFalse();
        assertThat(AudienciaDelMensaje.TODOS.admite(true)).isTrue();
        assertThat(AudienciaDelMensaje.TODOS.admite(false)).isTrue();
    }

    // spec 041 FR-011
    @Test
    @DisplayName("la configuracion se lee con VOY como valor si falta o no se entiende: nunca se abre de mas")
    void sinValorEsVoy() {
        assertThat(AudienciaDelMensaje.desde(null)).isEqualTo(AudienciaDelMensaje.VOY);
        assertThat(AudienciaDelMensaje.desde("  ")).isEqualTo(AudienciaDelMensaje.VOY);
        assertThat(AudienciaDelMensaje.desde("todos los de la sala")).isEqualTo(AudienciaDelMensaje.VOY);
        assertThat(AudienciaDelMensaje.desde(" todos ")).isEqualTo(AudienciaDelMensaje.TODOS);
        assertThat(AudienciaDelMensaje.desde("voy")).isEqualTo(AudienciaDelMensaje.VOY);
    }
}
