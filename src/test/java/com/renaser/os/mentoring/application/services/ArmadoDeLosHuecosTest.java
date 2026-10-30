package com.renaser.os.mentoring.application.services;

import com.renaser.os.mentoring.api.HuecosDeLaSemana;
import com.renaser.os.mentoring.domain.model.huecos.DiaDeHuecos;
import com.renaser.os.mentoring.domain.model.huecos.HabitoConHora;
import com.renaser.os.mentoring.domain.model.huecos.ItemPlanificado;
import com.renaser.os.mentoring.domain.model.huecos.LoReal;
import com.renaser.os.mentoring.domain.model.huecos.SemanaDeHuecos;
import com.renaser.os.shared.domain.TramoDelDia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** La forma de la respuesta es la del contrato de la spec 015. */
class ArmadoDeLosHuecosTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 26);
    private static final LocalDate PRIMERA = LocalDate.of(2026, 10, 12);
    private static final LocalDate HOY = LocalDate.of(2026, 10, 29);

    @Test
    @DisplayName("arma el ejemplo del contrato: ventana, minutos, huecos en HH:mm, los que se repiten y lo real")
    void ejemploDelContrato() {
        HuecosDeLaSemana huecos = ArmadoDeLosHuecos.armar(SemanasDeHuecosDePrueba.delContrato(LUNES),
                new ArmadoDeLosHuecos.Lectura(PRIMERA, false, HOY));

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
        HuecosDeLaSemana huecos = ArmadoDeLosHuecos.armar(SemanasDeHuecosDePrueba.delContrato(LUNES),
                new ArmadoDeLosHuecos.Lectura(null, true, HOY));

        HuecosDeLaSemana.Dia martes = huecos.dias().get(1);
        assertThat(martes.ventana()).isNull();
        assertThat(martes.huecos()).containsExactly(new HuecosDeLaSemana.Hueco("00:00", "24:00", 1440));
        assertThat(huecos.primeraSemana()).isNull();
        assertThat(huecos.cerrada()).isTrue();
    }

    @Test
    @DisplayName("un dia calculado trae sus bloques ocupados con tipo y nombre, lo cumplido y el motivo de no tener ventana; y hoy")
    void diaConDetalle() {
        DiaDeHuecos calculado = DiaDeHuecos.calcular(LUNES,
                List.of(new HabitoConHora(HabitoConHora.DESPERTAR, LocalTime.of(5, 30), "Despertar"),
                        new HabitoConHora(HabitoConHora.DORMIR, LocalTime.of(22, 30), "Dormir")),
                List.of(ItemPlanificado.deEvento(LUNES.atTime(9, 0), LUNES.atTime(10, 30), "Reunion")),
                List.of(new TramoDelDia(840, 900))).conLoReal(new LoReal(5, 2, 1));
        DiaDeHuecos sinVentana = DiaDeHuecos.calcular(LUNES.plusDays(1),
                List.of(new HabitoConHora(HabitoConHora.DORMIR, LocalTime.of(22, 30), "Dormir")), List.of(), List.of());
        SemanaDeHuecos semana = SemanaDeHuecos.de(LUNES, List.of(calculado, sinVentana,
                sinVentana, sinVentana, sinVentana, sinVentana, sinVentana), LoReal.NADA);

        HuecosDeLaSemana huecos = ArmadoDeLosHuecos.armar(semana, new ArmadoDeLosHuecos.Lectura(PRIMERA, false, HOY));

        assertThat(huecos.hoy()).isEqualTo(HOY);
        HuecosDeLaSemana.Dia lunes = huecos.dias().get(0);
        assertThat(lunes.ocupados()).containsExactly(
                new HuecosDeLaSemana.Ocupado("05:30", "06:00", HuecosDeLaSemana.Tipo.HABITO, "Despertar"),
                new HuecosDeLaSemana.Ocupado("09:00", "10:30", HuecosDeLaSemana.Tipo.EVENTO, "Reunion"),
                new HuecosDeLaSemana.Ocupado("14:00", "15:00", HuecosDeLaSemana.Tipo.AGENDA, null));
        assertThat(lunes.cumplidosATiempo()).isEqualTo(5);
        assertThat(lunes.cumplidosTarde()).isEqualTo(2);
        assertThat(lunes.noCumplidos()).isEqualTo(1);
        assertThat(lunes.sinVentanaPor()).isNull();
        HuecosDeLaSemana.Dia martes = huecos.dias().get(1);
        assertThat(martes.ventana()).isNull();
        assertThat(martes.sinVentanaPor()).isEqualTo(HuecosDeLaSemana.SinVentanaPor.SIN_DESPERTAR);
    }

    @Test
    @DisplayName("un dia que sale de la foto no trae bloques ni lo cumplido: salen en null, no en cero ni en una lista vacia")
    void diaDeLaFotoSinDetalle() {
        HuecosDeLaSemana huecos = ArmadoDeLosHuecos.armar(SemanasDeHuecosDePrueba.delContrato(LUNES),
                new ArmadoDeLosHuecos.Lectura(PRIMERA, true, HOY));

        for (HuecosDeLaSemana.Dia dia : huecos.dias()) {
            assertThat(dia.ocupados()).isNull();
            assertThat(dia.cumplidosATiempo()).isNull();
            assertThat(dia.cumplidosTarde()).isNull();
            assertThat(dia.noCumplidos()).isNull();
            assertThat(dia.sinVentanaPor()).isNull();
        }
        // Lo de antes no cambia: el resumen sigue trayendo lo real de la semana.
        assertThat(huecos.resumen().cumplidosATiempo()).isEqualTo(32);
    }
}
