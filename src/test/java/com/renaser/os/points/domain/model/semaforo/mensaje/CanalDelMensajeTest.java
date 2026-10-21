package com.renaser.os.points.domain.model.semaforo.mensaje;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** D-281: por qué canal llega el mensaje decide qué datos puede nombrar. */
class CanalDelMensajeTest {

    private static MensajeDelSemaforo mensaje(String texto) {
        return new MensajeDelSemaforo("a001", GrupoDelMensaje.AMARILLO, texto, "azul-reloj", true);
    }

    @Test
    @DisplayName("en Hoy el mensaje puede nombrar cualquier dato, también cuánto falta para subir de color")
    void hoyAdmiteTodasLasVariables() {
        assertThat(CanalDelMensaje.HOY.variablesPermitidas()).containsExactlyInAnyOrder(Variable.values());
        assertThat(CanalDelMensaje.HOY.admite(mensaje("Te faltan {faltan} para subir."))).isTrue();
    }

    @Test
    @DisplayName("en el chat de las 23:50 no existe «cuánto falta»: el día ya casi cerró y sería una mentira")
    void elChatNoAdmiteFaltan() {
        assertThat(CanalDelMensaje.CHAT.variablesPermitidas()).doesNotContain(Variable.FALTAN)
                .containsExactlyInAnyOrder(Variable.NOMBRE, Variable.DIA, Variable.FASE, Variable.HABITO_FLOJO,
                        Variable.HABITO_FUERTE);
        assertThat(CanalDelMensaje.CHAT.admite(mensaje("{nombre}, te faltan {faltan} para subir."))).isFalse();
    }

    @Test
    @DisplayName("en el chat sí se admiten los mensajes con nombre, día, fase y hábitos, y los que no piden nada")
    void elChatAdmiteElResto() {
        assertThat(CanalDelMensaje.CHAT.admite(mensaje("{nombre}, día {dia} en {fase}."))).isTrue();
        assertThat(CanalDelMensaje.CHAT.admite(mensaje("Dedica tiempo a {habitoFlojo}, como {habitoFuerte}."))).isTrue();
        assertThat(CanalDelMensaje.CHAT.admite(mensaje("Todavía puedes levantar el ritmo."))).isTrue();
    }

    @Test
    @DisplayName("cada canal tiene su nombre en la base: HOY es el de siempre y CHAT es el nuevo")
    void losNombresDeLaBase() {
        assertThat(CanalDelMensaje.values()).extracting(Enum::name).containsExactly("HOY", "CHAT");
    }
}
