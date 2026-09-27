package com.renaser.os.notifications.domain.model.habito;

import com.renaser.os.notifications.domain.model.notificacion.EntregaPush;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** La regla de D-184, caso por caso. */
class EntregaDelAvisoDeHabitoTest {

    @Test
    @DisplayName("recordatorio apagado -> ningun push, ni de inicio ni de vencimiento")
    void apagadoSinPush() {
        assertThat(EntregaDelAvisoDeHabito.para("INICIO", false, null)).isEqualTo(EntregaPush.NINGUNO);
        assertThat(EntregaDelAvisoDeHabito.para("POR_VENCER", false, 30)).isEqualTo(EntregaPush.NINGUNO);
    }

    @Test
    @DisplayName("inicio con alarma local en el telefono -> push solo al navegador")
    void inicioConAlarmaLocalSoloNavegador() {
        assertThat(EntregaDelAvisoDeHabito.para("INICIO", true, 30)).isEqualTo(EntregaPush.SOLO_NAVEGADOR);
        assertThat(EntregaDelAvisoDeHabito.para("INICIO", true, 0)).isEqualTo(EntregaPush.SOLO_NAVEGADOR);
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
