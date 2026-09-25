package com.renaser.os.rag.domain.model.agenda;

import com.renaser.os.rag.domain.model.agenda.AgendaOcupada.Tramo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgendaOcupadaTest {

    private static Tramo tramo(String desde, String hasta) {
        return new Tramo(minutos(desde), minutos(hasta));
    }

    private static int minutos(String hora) {
        return "24:00".equals(hora) ? AgendaOcupada.FIN_DEL_DIA
                : AgendaOcupada.minutos(java.time.LocalTime.parse(hora));
    }

    @Test
    @DisplayName("lee varios tramos, los ordena y junta los que se pisan o se tocan")
    void leeOrdenaYJunta() {
        AgendaOcupada agenda = AgendaOcupada.leer("14:00-18:00, 9:00-12:00; 11:30-13:00, 13:00-13:30");

        assertThat(agenda.ocupados()).containsExactly(tramo("09:00", "13:30"), tramo("14:00", "18:00"));
    }

    @Test
    @DisplayName("un tramo que cruza la medianoche se parte en el final y el principio del mismo dia")
    void tramoNocturno() {
        AgendaOcupada agenda = AgendaOcupada.leer("23:00-06:00");

        assertThat(agenda.ocupados()).containsExactly(tramo("00:00", "06:00"), tramo("23:00", "24:00"));
        assertThat(agenda.libresEntre(0, AgendaOcupada.FIN_DEL_DIA)).containsExactly(tramo("06:00", "23:00"));
    }

    @Test
    @DisplayName("lo libre dentro de una franja descuenta lo ocupado, en orden")
    void libresEntre() {
        AgendaOcupada agenda = AgendaOcupada.leer("09:00-13:00, 14:00-18:00");

        assertThat(agenda.libresEntre(minutos("12:00"), minutos("20:00")))
                .containsExactly(tramo("13:00", "14:00"), tramo("18:00", "20:00"));
        assertThat(agenda.libresEntre(minutos("10:00"), minutos("12:00"))).isEmpty();
        assertThat(agenda.libresEntre(minutos("06:00"), minutos("08:00"))).containsExactly(tramo("06:00", "08:00"));
    }

    @Test
    @DisplayName("lo que no se entiende es IllegalArgumentException con un mensaje para el modelo")
    void entradaInvalida() {
        assertThatThrownBy(() -> AgendaOcupada.leer(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgendaOcupada.leer("de 9 a 6")).hasMessageContaining("HH:mm-HH:mm");
        assertThatThrownBy(() -> AgendaOcupada.leer("25:00-26:00")).hasMessageContaining("HH:mm");
        assertThatThrownBy(() -> AgendaOcupada.leer("09:00-09:00")).hasMessageContaining("misma hora");
        assertThatThrownBy(() -> AgendaOcupada.leer("01:00-02:00,".repeat(AgendaOcupada.MAXIMO_TRAMOS + 1)))
                .hasMessageContaining("demasiados");
    }

    @Test
    @DisplayName("leida para la semana, la madrugada de un tramo nocturno queda aparte, para el dia siguiente")
    void madrugadaAparte() {
        AgendaOcupada.Lectura lectura = AgendaOcupada.leerConMedianoche("23:00-06:00, 09:00-18:00");

        assertThat(lectura.delDia().ocupados()).containsExactly(tramo("09:00", "18:00"), tramo("23:00", "24:00"));
        assertThat(lectura.delDiaSiguiente().ocupados()).containsExactly(tramo("00:00", "06:00"));
    }
}
