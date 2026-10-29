package com.renaser.os.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La aritmética compartida de «juntar lo que se cruza y sacar lo libre» (spec 015; antes vivía solo en
 * {@code rag.AgendaOcupada}). Sin Spring.
 */
class TramosUnidosTest {

    private static TramoDelDia tramo(String desde, String hasta) {
        return new TramoDelDia(minutos(desde), minutos(hasta));
    }

    private static int minutos(String hora) {
        return "24:00".equals(hora) ? TramoDelDia.FIN_DEL_DIA : TramoDelDia.minutosDe(LocalTime.parse(hora));
    }

    @Test
    @DisplayName("junta los que se pisan, los contenidos, los iguales y los que se tocan justo")
    void juntaLosQueSeCruzan() {
        TramosUnidos unidos = new TramosUnidos(List.of(
                tramo("11:00", "13:00"), tramo("09:00", "12:00"),   // se pisan
                tramo("09:30", "10:00"),                            // uno dentro de otro
                tramo("14:00", "15:00"), tramo("14:00", "15:00"),   // iguales
                tramo("15:00", "16:00")));                          // se tocan justo

        assertThat(unidos.tramos()).containsExactly(tramo("09:00", "13:00"), tramo("14:00", "16:00"));
    }

    @Test
    @DisplayName("sacar lo libre descuenta lo ocupado, en orden, y no deja huecos de cero minutos")
    void libresEntre() {
        TramosUnidos unidos = new TramosUnidos(List.of(tramo("09:00", "13:00"), tramo("13:00", "14:00"),
                tramo("18:00", "19:00")));

        assertThat(unidos.libresEntre(minutos("08:00"), minutos("20:00")))
                .containsExactly(tramo("08:00", "09:00"), tramo("14:00", "18:00"), tramo("19:00", "20:00"));
        assertThat(unidos.libresEntre(minutos("10:00"), minutos("12:00"))).isEmpty();
        assertThat(new TramosUnidos(List.of()).libresEntre(0, TramoDelDia.FIN_DEL_DIA))
                .containsExactly(new TramoDelDia(0, TramoDelDia.FIN_DEL_DIA));
    }

    @Test
    @DisplayName("lo ocupado se recorta a la franja pedida")
    void dentroDe() {
        TramosUnidos unidos = new TramosUnidos(List.of(tramo("05:00", "07:00"), tramo("10:00", "11:00"),
                tramo("22:00", "23:30")));

        assertThat(unidos.dentroDe(minutos("06:00"), minutos("22:30")))
                .containsExactly(tramo("06:00", "07:00"), tramo("10:00", "11:00"), tramo("22:00", "22:30"));
        assertThat(unidos.dentroDe(minutos("12:00"), minutos("13:00"))).isEmpty();
    }

    @Test
    @DisplayName("el resultado no depende del orden en que llegan y ocupado + libre es siempre la ventana")
    void independienteDelOrdenYCierraLaCuenta() {
        Random azar = new Random(15);
        for (int vuelta = 0; vuelta < 200; vuelta++) {
            List<TramoDelDia> sueltos = new ArrayList<>();
            for (int i = 0; i < 1 + azar.nextInt(20); i++) {
                int desde = azar.nextInt(TramoDelDia.FIN_DEL_DIA - 1);
                sueltos.add(new TramoDelDia(desde, desde + 1 + azar.nextInt(Math.min(240,
                        TramoDelDia.FIN_DEL_DIA - desde))));
            }
            TramosUnidos unidos = new TramosUnidos(sueltos);
            List<TramoDelDia> mezclados = new ArrayList<>(sueltos);
            Collections.shuffle(mezclados, azar);
            assertThat(new TramosUnidos(mezclados)).isEqualTo(unidos);

            int ocupado = unidos.dentroDe(0, TramoDelDia.FIN_DEL_DIA).stream().mapToInt(TramoDelDia::minutos).sum();
            int libre = unidos.libresEntre(0, TramoDelDia.FIN_DEL_DIA).stream().mapToInt(TramoDelDia::minutos).sum();
            assertThat(ocupado + libre).isEqualTo(TramoDelDia.FIN_DEL_DIA);
            unidos.libresEntre(0, TramoDelDia.FIN_DEL_DIA).forEach(hueco -> assertThat(unidos.tramos())
                    .noneMatch(ocupadoTramo -> ocupadoTramo.desde() < hueco.hasta() && hueco.desde() < ocupadoTramo.hasta()));
        }
    }

    @Test
    @DisplayName("un tramo invalido se rechaza")
    void tramoInvalido() {
        assertThatThrownBy(() -> new TramoDelDia(60, 60)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TramoDelDia(-1, 60)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TramoDelDia(0, TramoDelDia.FIN_DEL_DIA + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("la hora se escribe HH:mm y el fin del dia es 24:00")
    void hora() {
        assertThat(TramoDelDia.hora(330)).isEqualTo("05:30");
        assertThat(TramoDelDia.hora(TramoDelDia.FIN_DEL_DIA)).isEqualTo("24:00");
    }
}
