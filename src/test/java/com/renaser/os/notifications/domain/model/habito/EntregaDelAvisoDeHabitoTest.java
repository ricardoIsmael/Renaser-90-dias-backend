package com.renaser.os.notifications.domain.model.habito;

import com.renaser.os.notifications.domain.model.notificacion.EntregaPush;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** La regla de D-184, caso por caso, con el respaldo de D-217. */
class EntregaDelAvisoDeHabitoTest {

    @Test
    @DisplayName("recordatorio apagado -> ningun push, ni de inicio ni de vencimiento")
    void apagadoSinPush() {
        assertThat(EntregaDelAvisoDeHabito.para("INICIO", false, null)).isEqualTo(EntregaPush.NINGUNO);
        assertThat(EntregaDelAvisoDeHabito.para("POR_VENCER", false, 30)).isEqualTo(EntregaPush.NINGUNO);
    }

    @Test
    @DisplayName("D-217: inicio con alarma local en el telefono -> respaldo (navegador + telefonos sin confirmar)")
    void inicioConAlarmaLocalEsRespaldo() {
        assertThat(EntregaDelAvisoDeHabito.para("INICIO", true, 30)).isEqualTo(EntregaPush.RESPALDO_DE_ALARMA_LOCAL);
        assertThat(EntregaDelAvisoDeHabito.para("INICIO", true, 0)).isEqualTo(EntregaPush.RESPALDO_DE_ALARMA_LOCAL);
    }

    @Test
    @DisplayName("vencimiento: el telefono no tiene alarma para eso -> push a todos")
    void vencimientoATodos() {
        assertThat(EntregaDelAvisoDeHabito.para("POR_VENCER", true, 30)).isEqualTo(EntregaPush.TODOS);
    }

    @Test
    @DisplayName("sin preferencia (nunca configurado) o sin minutos -> push a todos, como antes")
    void sinConfigurarComoAntes() {
        assertThat(EntregaDelAvisoDeHabito.para("INICIO", null, null)).isEqualTo(EntregaPush.TODOS);
        assertThat(EntregaDelAvisoDeHabito.para("INICIO", true, null)).isEqualTo(EntregaPush.TODOS);
    }
}
