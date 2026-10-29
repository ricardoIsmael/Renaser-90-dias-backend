package com.renaser.os.mentoring.application.services;

import com.renaser.os.mentoring.api.HuecosDeLaSemana;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** La forma de la respuesta es la del contrato de la spec 015. */
class ArmadoDeLosHuecosTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 26);
    private static final LocalDate PRIMERA = LocalDate.of(2026, 10, 12);

    @Test
    @DisplayName("arma el ejemplo del contrato: ventana, minutos, huecos en HH:mm, los que se repiten y lo real")
    void ejemploDelContrato() {
        HuecosDeLaSemana huecos = ArmadoDeLosHuecos.armar(SemanasDeHuecosDePrueba.delContrato(LUNES), PRIMERA, false);

        assertThat(huecos.semana()).isEqualTo(LUNES);
        assertThat(huecos.primeraSemana()).isEqualTo(PRIMERA);
        assertThat(huecos.cerrada()).isFalse();
        assertThat(huecos.dias()).hasSize(7);
        HuecosDeLaSemana.Dia lunes = huecos.dias().get(0);
        assertThat(lunes.fecha()).isEqualTo(LUNES);
        assertThat(lunes.ventana()).isEqualTo(new HuecosDeLaSemana.Ventana("05:30", "22:30"));
        assertThat(lunes.minutosOcupados()).isEqualTo(410);
        assertThat(lunes.minutosLibres()).isEqualTo(1020 - 410);
        assertThat(lunes.huecos()).containsExactly(new HuecosDeLaSemana.Hueco("15:00", "17:00", 120));
        assertThat(huecos.resumen().huecosQueSeRepiten())
                .containsExactly(new HuecosDeLaSemana.HuecoRepetido("15:00", "17:00", List.of(1, 3, 4)));
        assertThat(huecos.resumen().cumplidosATiempo()).isEqualTo(32);
        assertThat(huecos.resumen().cumplidosTarde()).isEqualTo(6);
        assertThat(huecos.resumen().noCumplidos()).isEqualTo(4);
        assertThat(huecos.resumen().minutosOcupados()).isEqualTo(410);
    }

    @Test
    @DisplayName("sin Despertar y Dormir la ventana es null y el hueco puede terminar a las 24:00")
    void ventanaNull() {
        HuecosDeLaSemana huecos = ArmadoDeLosHuecos.armar(SemanasDeHuecosDePrueba.delContrato(LUNES), null, true);

        HuecosDeLaSemana.Dia martes = huecos.dias().get(1);
        assertThat(martes.ventana()).isNull();
        assertThat(martes.huecos()).containsExactly(new HuecosDeLaSemana.Hueco("00:00", "24:00", 1440));
        assertThat(huecos.primeraSemana()).isNull();
        assertThat(huecos.cerrada()).isTrue();
    }
}
