package com.renaser.os.mentoring.domain.model.huecos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VentanaDelDiaTest {

    private static HabitoConHora despertar(int hora, int minuto) {
        return new HabitoConHora(HabitoConHora.DESPERTAR, LocalTime.of(hora, minuto));
    }

    private static HabitoConHora dormir(int hora, int minuto) {
        return new HabitoConHora(HabitoConHora.DORMIR, LocalTime.of(hora, minuto));
    }

    @Test
    @DisplayName("va de la hora de Despertar a la de Dormir")
    void deDespertarADormir() {
        VentanaDelDia ventana = VentanaDelDia.de(List.of(dormir(22, 30), despertar(5, 30),
                new HabitoConHora(null, LocalTime.of(9, 0))));

        assertThat(ventana).isEqualTo(new VentanaDelDia(330, 1350));
        assertThat(ventana.vieneDeLaPersona()).isTrue();
        assertThat(ventana.minutos()).isEqualTo(1020);
    }

    @Test
    @DisplayName("sin Despertar, sin Dormir o sin ninguno, es el dia entero")
    void sinAlguno() {
        assertThat(VentanaDelDia.de(List.of(dormir(22, 30)))).isEqualTo(VentanaDelDia.diaEntero());
        assertThat(VentanaDelDia.de(List.of(despertar(6, 0)))).isEqualTo(VentanaDelDia.diaEntero());
        assertThat(VentanaDelDia.de(List.of())).isEqualTo(VentanaDelDia.diaEntero());
        assertThat(VentanaDelDia.diaEntero().vieneDeLaPersona()).isFalse();
    }

    @Test
    @DisplayName("con Dormir antes que Despertar (quien trabaja de noche) o a la misma hora, es el dia entero")
    void noCoherentes() {
        assertThat(VentanaDelDia.de(List.of(despertar(18, 0), dormir(8, 0)))).isEqualTo(VentanaDelDia.diaEntero());
        assertThat(VentanaDelDia.de(List.of(despertar(6, 0), dormir(6, 0)))).isEqualTo(VentanaDelDia.diaEntero());
    }
}
